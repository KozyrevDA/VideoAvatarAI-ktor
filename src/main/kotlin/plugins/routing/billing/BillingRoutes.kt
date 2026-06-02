package plugins.routing.billing

import app.Settings
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmPurchaseRequest(
    val productId: String,
    val purchaseToken: String,
    val store: String, // "rustore" | "google"
    val userId: String,
)

@Serializable
data class ConfirmPurchaseResponse(
    val success: Boolean,
    val tokensAdded: Int = 0,
    val message: String = "",
)

fun Route.billingRoutes(settings: Settings) {

    post("/billing/confirm") {
        val request = call.receive<ConfirmPurchaseRequest>()

        // TODO: verify purchase with store API
        // For now — trust the client and return success
        val tokensAdded = when {
            request.productId.contains("tokens_10") -> 10
            request.productId.contains("tokens_5") -> 5
            request.productId.contains("tokens_1") -> 1
            request.productId.contains("sub_") -> 5 // monthly allocation
            else -> 0
        }

        call.respond(
            HttpStatusCode.OK,
            ConfirmPurchaseResponse(
                success = true,
                tokensAdded = tokensAdded,
                message = "Purchase confirmed",
            )
        )
    }

    // RuStore webhook
    post("/billing/rustore/webhook") {
        val body = call.receiveText()
        // TODO: verify RuStore signature and process webhook
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    // Google Play webhook
    post("/billing/google/webhook") {
        val body = call.receiveText()
        // TODO: verify Google Pub/Sub message and process
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}
