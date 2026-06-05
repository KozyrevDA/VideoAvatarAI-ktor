package plugins.routing.users

import app.Settings
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.repository.postgres.PostgresUserRepository
import data.repository.postgres.PostgresVideoRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val provider: String = "email",
    val providerId: String? = null,
)

@Serializable
data class AuthResponse(
    val userId: String,
    val accessToken: String,
    val tokensCount: Int = 5,
    val isPro: Boolean = false,
)

fun Route.usersRoutes(
    settings: Settings,
    userRepo: PostgresUserRepository,
    videoRepo: PostgresVideoRepository,
) {
    // Регистрация / вход
    post("/users/register") {
        val req = call.receive<RegisterRequest>()
        val existing = req.providerId?.let { userRepo.findByProviderId(req.provider, it) }
        val user = existing ?: userRepo.createUser(req.email, req.provider, req.providerId)
        val token = generateJwt(user.id, settings.jwtSecret)
        call.respond(HttpStatusCode.OK, AuthResponse(user.id, token, user.tokensCount, user.isPro))
    }

    // Профиль пользователя
    get("/users/profile") {
        val userId = call.request.headers["X-User-Id"]
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "X-User-Id header required")
        val user = userRepo.findById(userId)
            ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        call.respond(HttpStatusCode.OK, mapOf(
            "userId"       to user.id,
            "email"        to (user.email ?: ""),
            "tokensCount"  to user.tokensCount,
            "isPro"        to user.isPro,
            "subType"      to (user.subType ?: ""),
            "voiceCloned"  to (user.voiceId != null),
        ))
    }

    // История генераций (теперь использует videoRepo)
    get("/history") {
        val userId = call.request.headers["X-User-Id"]
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "X-User-Id header required")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
        val items = videoRepo.getHistory(userId, limit)
        call.respond(HttpStatusCode.OK, items.map { video ->
            mapOf(
                "id"        to video.id,
                "type"      to video.type,
                "title"     to video.title,
                "status"    to video.status,
                "videoUrl"  to (video.videoUrl ?: ""),
                "language"  to video.language,
                "platform"  to video.platform,
                "createdAt" to video.createdAt,
            )
        })
    }
}

private fun generateJwt(userId: String, secret: String): String =
    JWT.create()
        .withIssuer("videoavataraii")
        .withSubject(userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
        .sign(Algorithm.HMAC256(secret))
