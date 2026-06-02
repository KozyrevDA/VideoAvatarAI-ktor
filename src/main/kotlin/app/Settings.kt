package app

import io.ktor.server.application.*

data class Settings(
    val hedraApiKey: String,
    val elevenlabsApiKey: String,
    val veo3ApiKey: String,
    val anthropicApiKey: String,
    val jwtSecret: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val rustoreApiKey: String,
    val googleServiceAccountJson: String,
)

fun Application.loadSettings() = Settings(
    hedraApiKey = environment.config.propertyOrNull("ktor.hedra.apiKey")?.getString() ?: System.getenv("HEDRA_API_KEY") ?: "",
    elevenlabsApiKey = environment.config.propertyOrNull("ktor.elevenlabs.apiKey")?.getString() ?: System.getenv("ELEVENLABS_API_KEY") ?: "",
    veo3ApiKey = environment.config.propertyOrNull("ktor.veo3.apiKey")?.getString() ?: System.getenv("VEO3_API_KEY") ?: "",
    anthropicApiKey = environment.config.propertyOrNull("ktor.anthropic.apiKey")?.getString() ?: System.getenv("ANTHROPIC_API_KEY") ?: "",
    jwtSecret = environment.config.propertyOrNull("ktor.jwt.secret")?.getString() ?: System.getenv("JWT_SECRET") ?: "default_secret",
    dbUrl = environment.config.propertyOrNull("ktor.database.url")?.getString() ?: System.getenv("DATABASE_URL") ?: "",
    dbUser = environment.config.propertyOrNull("ktor.database.user")?.getString() ?: System.getenv("DATABASE_USER") ?: "",
    dbPassword = environment.config.propertyOrNull("ktor.database.password")?.getString() ?: System.getenv("DATABASE_PASSWORD") ?: "",
    rustoreApiKey = environment.config.propertyOrNull("ktor.rustore.apiKey")?.getString() ?: System.getenv("RUSTORE_API_KEY") ?: "",
    googleServiceAccountJson = environment.config.propertyOrNull("ktor.google.serviceAccount")?.getString() ?: System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON") ?: "",
)
