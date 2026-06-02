package plugins.routing.users

import app.Settings
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val provider: String = "email", // email | google | vk
    val providerId: String? = null,
)

@Serializable
data class AuthResponse(
    val userId: String,
    val accessToken: String,
    val tokensCount: Int = 0,
    val isPro: Boolean = false,
)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val email: String? = null,
    val tokensCount: Int,
    val isPro: Boolean,
    val subscriptionType: String = "",
    val nextPaymentDate: String = "",
    val voiceCloned: Boolean = false,
)

fun Route.usersRoutes(settings: Settings) {

    post("/users/register") {
        val request = call.receive<RegisterRequest>()
        // TODO: create user in DB, generate JWT
        val userId = "user_${System.currentTimeMillis()}"
        call.respond(
            HttpStatusCode.OK,
            AuthResponse(
                userId = userId,
                accessToken = "jwt_${userId}",
                tokensCount = 5, // free onboarding tokens
                isPro = false,
            )
        )
    }

    get("/users/profile") {
        // TODO: extract userId from JWT
        call.respond(
            HttpStatusCode.OK,
            UserProfileResponse(
                userId = "demo_user",
                tokensCount = 27,
                isPro = true,
                subscriptionType = "yearly",
                nextPaymentDate = "2027-06-15",
                voiceCloned = true,
            )
        )
    }

    post("/users/voice/clone") {
        // TODO: receive voice sample, send to ElevenLabs voice cloning
        call.respond(HttpStatusCode.OK, mapOf("voiceId" to "cloned_voice_id"))
    }

    get("/history") {
        // TODO: return user's generation history from DB
        call.respond(HttpStatusCode.OK, emptyList<Any>())
    }

    get("/video/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        // TODO: return video by ID from DB
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
    }
}
