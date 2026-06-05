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
    val s = loadSettings()

    configureSerialization()
    configureHTTP()
    configureAuthentication(s)

    if (s.dbUrl.isNotBlank()) {
        PostgresDatabase.init(s.dbUrl, s.dbUser, s.dbPassword)
    }

    val avatarService = AvatarAiService(
        hedraApiKey   = s.hedraApiKey,
        fishAudioApiKey = s.fishAudioApiKey,
        veo3ApiKey    = s.veo3ApiKey,
        avatarModel   = s.hedraAvatarModel,   // kling_ai_avatar_v2_standard
    )
    val captionService = CaptionAiService(
        laozhangApiKey = s.laozhangApiKey,
        model          = s.laozhangModel,
    )
    val translateService = TranslateAiService(
        hedraApiKey     = s.hedraApiKey,
        fishAudioApiKey = s.fishAudioApiKey,
    )
    val pushService  = PushNotificationService(fcmServerKey = s.fcmServerKey)
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
        settings         = s,
    )
}
