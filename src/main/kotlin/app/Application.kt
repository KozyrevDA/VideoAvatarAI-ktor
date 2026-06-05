package app

import data.remote.ai.AvatarAiService
import data.remote.ai.CaptionAiService
import data.remote.ai.TranslateAiService
import data.repository.postgres.PostgresDatabase
import data.repository.postgres.PostgresUserRepository
import data.repository.postgres.PostgresVideoRepository
import features.jobs.AvatarPollingJob
import features.notifications.PushNotificationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import plugins.*
import plugins.routing.configureRouting

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val settings = loadSettings()

    configureSerialization()
    configureHTTP()
    configureAuthentication(settings)

    if (settings.dbUrl.isNotBlank()) {
        PostgresDatabase.init(settings.dbUrl, settings.dbUser, settings.dbPassword)
    }

    val avatarService = AvatarAiService(
        hedraApiKey     = settings.hedraApiKey,
        fishAudioApiKey = settings.fishAudioApiKey,  // ← Fish Audio вместо ElevenLabs
        veo3ApiKey      = settings.veo3ApiKey,
    )
    val captionService  = CaptionAiService(anthropicApiKey = settings.anthropicApiKey)
    val translateService = TranslateAiService(
        hedraApiKey     = settings.hedraApiKey,
        fishAudioApiKey = settings.fishAudioApiKey,
    )
    val pushService  = PushNotificationService(fcmServerKey = settings.fcmServerKey)
    val userRepo     = PostgresUserRepository()
    val videoRepo    = PostgresVideoRepository()
    val pollingJob   = AvatarPollingJob(avatarService, videoRepo, userRepo, pushService)

    configureRouting(
        avatarService    = avatarService,
        captionService   = captionService,
        translateService = translateService,
        pushService      = pushService,
        userRepo         = userRepo,
        videoRepo        = videoRepo,
        pollingJob       = pollingJob,
        settings         = settings,
    )
}
