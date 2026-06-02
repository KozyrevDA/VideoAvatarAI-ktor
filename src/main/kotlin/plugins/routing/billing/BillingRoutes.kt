package plugins.routing.billing

import app.Settings
import data.repository.postgres.PostgresUserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmPurchaseRequest(val productId: String, val purchaseToken: String, val store: String, val userId: String)

fun Route.billingRoutes(settings: Settings, userRepo: PostgresUserRepository) {

    post("/billing/confirm") {
        val req = call.receive<ConfirmPurchaseRequest>()
        val tokensAdded = when {
            req.productId.contains("tokens_10") -> 10
            req.productId.contains("tokens_5")  -> 5
            req.productId.contains("tokens_1")  -> 1
            req.productId.contains("sub_")      -> 5
            else -> 0
        }
        if (tokensAdded > 0) userRepo.addTokens(req.userId, tokensAdded)
        call.respond(HttpStatusCode.OK, mapOf("success" to true, "tokensAdded" to tokensAdded))
    }

    post("/billing/rustore/webhook") {
        call.receiveText() // TODO: verify signature
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/billing/google/webhook") {
        call.receiveText() // TODO: verify Pub/Sub message
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}
