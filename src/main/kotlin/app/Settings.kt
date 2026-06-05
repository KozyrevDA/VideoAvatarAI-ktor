package app

import io.ktor.server.application.*

data class Settings(
    val hedraApiKey: String,
    val fishAudioApiKey: String,       // Fish Audio TTS + клонирование голоса
    val elevenlabsApiKey: String = "", // deprecated — заменён Fish Audio
    val veo3ApiKey: String,
    val anthropicApiKey: String,
    val jwtSecret: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val rustoreApiKey: String,
    val googleServiceAccountJson: String,
    val fcmServerKey: String,
)

fun Application.loadSettings() = Settings(
    hedraApiKey            = env("HEDRA_API_KEY"),
    fishAudioApiKey        = env("FISH_AUDIO_API_KEY"),
    elevenlabsApiKey       = env("ELEVENLABS_API_KEY"),
    veo3ApiKey             = env("VEO3_API_KEY"),
    anthropicApiKey        = env("ANTHROPIC_API_KEY"),
    jwtSecret              = env("JWT_SECRET", "change_me_in_prod"),
    dbUrl                  = env("DATABASE_URL"),
    dbUser                 = env("DATABASE_USER"),
    dbPassword             = env("DATABASE_PASSWORD"),
    rustoreApiKey          = env("RUSTORE_API_KEY"),
    googleServiceAccountJson = env("GOOGLE_SERVICE_ACCOUNT_JSON"),
    fcmServerKey           = env("FCM_SERVER_KEY"),
)

private fun Application.env(key: String, default: String = "") =
    environment.config.propertyOrNull("ktor.$key")?.getString()
        ?: System.getenv(key)
        ?: default
