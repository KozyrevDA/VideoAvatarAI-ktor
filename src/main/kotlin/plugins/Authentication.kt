package plugins

import app.Settings
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

fun Application.configureAuthentication(settings: Settings) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "VideoAvatarAI"
            verifier(
                JWT.require(Algorithm.HMAC256(settings.jwtSecret))
                    .withIssuer("videoavataraii")
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
