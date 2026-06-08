package plugins.routing.billing

import app.Settings
import data.repository.postgres.PostgresUserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class ConfirmPurchaseRequest(
    val productId: String,
    val purchaseToken: String,
    val store: String,
    val userId: String,
)

fun Route.billingRoutes(settings: Settings, userRepo: PostgresUserRepository) {

    // ── Подтверждение покупки от мобильного клиента ───────────────────────────
    post("/billing/confirm") {
        val req = call.receive<ConfirmPurchaseRequest>()

        if (req.userId.isBlank() || req.purchaseToken.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "userId и purchaseToken обязательны")
            return@post
        }

        val tokensToAdd = tokensFor(req.productId)
        val isSubscription = req.productId.startsWith("sub_")

        when (req.store) {
            "rustore" -> {
                // В продакшене: верифицируем через RuStore API
                // POST https://public-api.rustore.ru/public/v1/purchase/{purchaseToken}
                // Сейчас — доверяем клиенту (добавить верификацию при росте)
                applyPurchase(userRepo, req.userId, tokensToAdd, isSubscription, req.productId)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            "google_play" -> {
                // В продакшене: верифицируем через Google Play Developer API
                // GET https://androidpublisher.googleapis.com/androidpublisher/v3/...
                applyPurchase(userRepo, req.userId, tokensToAdd, isSubscription, req.productId)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            else -> call.respond(HttpStatusCode.BadRequest, "Неизвестный магазин: ${req.store}")
        }
    }

    // ── RuStore Webhook (server-to-server, подпись RSA-SHA256) ───────────────
    post("/billing/webhook/rustore") {
        val body     = call.receiveText()
        val signature = call.request.headers["X-Signature"] ?: ""

        if (!verifyRuStoreSignature(body, signature, settings.rustoreApiKey)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
            return@post
        }

        try {
            val event = Json.parseToJsonElement(body).jsonObject
            val type  = event["type"]?.jsonPrimitive?.contentOrNull ?: ""
            when {
                type == "purchase.completed" -> {
                    val data      = event["data"]?.jsonObject ?: return@post
                    val userId    = data["userId"]?.jsonPrimitive?.contentOrNull ?: ""
                    val productId = data["productId"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (userId.isNotBlank() && productId.isNotBlank()) {
                        val tokens = tokensFor(productId)
                        applyPurchase(userRepo, userId, tokens, productId.startsWith("sub_"), productId)
                    }
                }
                type.contains("subscription") && type.contains("cancel") -> {
                    val userId = event["data"]?.jsonObject?.get("userId")?.jsonPrimitive?.contentOrNull ?: ""
                    if (userId.isNotBlank()) userRepo.setPro(userId, "", java.time.LocalDateTime.now())
                }
            }
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    // ── Google Play Webhook (Pub/Sub, подпись HMAC-SHA256) ───────────────────
    post("/billing/webhook/google") {
        val body      = call.receiveText()
        val signature = call.request.headers["X-Goog-Signature"] ?: ""

        // Верифицируем HMAC если ключ задан
        if (settings.googleServiceAccountJson.isNotBlank() && signature.isNotBlank()) {
            if (!verifyGoogleSignature(body, signature, settings.googleServiceAccountJson)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
                return@post
            }
        }

        try {
            // Google Pub/Sub обёртывает в base64
            val decoded = Json.parseToJsonElement(body).jsonObject
            val dataB64 = decoded["message"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
            val eventJson = if (dataB64 != null)
                String(Base64.getDecoder().decode(dataB64))
            else body

            val event = Json.parseToJsonElement(eventJson).jsonObject
            val notificationType = event["subscriptionNotification"]?.jsonObject
                ?.get("notificationType")?.jsonPrimitive?.intOrNull

            when (notificationType) {
                1  -> { /* SUBSCRIPTION_RECOVERED */ }
                2  -> { /* SUBSCRIPTION_RENEWED */ }
                3, 12, 13 -> { /* Cancelled/Revoked — деактивировать */ }
                4  -> { /* SUBSCRIPTION_PURCHASED */ }
            }
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun tokensFor(productId: String): Int = when (productId) {
    "tokens_1_80"     -> 1
    "tokens_5_400"    -> 5
    "tokens_10_800"   -> 10
    "sub_monthly_499" -> 5
    "sub_yearly_2490" -> 5
    else              -> 0
}

private fun applyPurchase(
    userRepo: PostgresUserRepository,
    userId: String,
    tokens: Int,
    isSubscription: Boolean,
    productId: String,
) {
    if (tokens > 0) userRepo.addTokens(userId, tokens)
    if (isSubscription) {
        val subType  = if (productId.contains("yearly")) "yearly" else "monthly"
        val expireAt = if (subType == "yearly")
            java.time.LocalDateTime.now().plusYears(1)
        else
            java.time.LocalDateTime.now().plusMonths(1)
        userRepo.setPro(userId, subType, expireAt)
    }
}

private fun verifyRuStoreSignature(body: String, signature: String, secretKey: String): Boolean {
    if (secretKey.isBlank() || signature.isBlank()) return true  // пропускаем если ключ не задан
    return try {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(body.toByteArray()))
        MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    } catch (e: Exception) { false }
}

private fun verifyGoogleSignature(body: String, signature: String, secret: String): Boolean {
    if (secret.isBlank() || signature.isBlank()) return true
    return try {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.take(32).toByteArray(), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(body.toByteArray()))
        MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    } catch (e: Exception) { false }
}
