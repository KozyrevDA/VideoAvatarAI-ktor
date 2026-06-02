package plugins.routing.users

import app.Settings
import data.repository.postgres.PostgresUserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

@Serializable
data class RegisterRequest(val email: String? = null, val provider: String = "email", val providerId: String? = null)

@Serializable
data class AuthResponse(val userId: String, val accessToken: String, val tokensCount: Int = 5, val isPro: Boolean = false)

fun Route.usersRoutes(settings: Settings, userRepo: PostgresUserRepository) {

    post("/users/register") {
        val req = call.receive<RegisterRequest>()
        val existing = req.providerId?.let { userRepo.findByProviderId(req.provider, it) }
        val user = existing ?: userRepo.createUser(req.email, req.provider, req.providerId)
        val token = generateJwt(user.id, settings.jwtSecret)
        call.respond(HttpStatusCode.OK, AuthResponse(user.id, token, user.tokensCount, user.isPro))
    }

    get("/users/profile") {
        val userId = call.request.headers["X-User-Id"] ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val user = userRepo.findById(userId) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(HttpStatusCode.OK, mapOf(
            "userId" to user.id,
            "email" to (user.email ?: ""),
            "tokensCount" to user.tokensCount,
            "isPro" to user.isPro,
            "subType" to (user.subType ?: ""),
            "voiceCloned" to (user.voiceId != null),
        ))
    }

    get("/history") {
        val userId = call.request.headers["X-User-Id"] ?: return@get call.respond(HttpStatusCode.Unauthorized)
        // PostgresVideoRepository is injected via closure in real impl
        call.respond(HttpStatusCode.OK, emptyList<Any>())
    }
}

private fun generateJwt(userId: String, secret: String): String =
    JWT.create()
        .withIssuer("videoavataraii")
        .withSubject(userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
        .sign(Algorithm.HMAC256(secret))
