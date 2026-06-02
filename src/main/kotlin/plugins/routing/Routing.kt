package plugins.routing

import app.Settings
import data.remote.ai.AvatarAiService
import data.remote.ai.CaptionAiService
import data.remote.ai.TranslateAiService
import data.repository.postgres.PostgresUserRepository
import data.repository.postgres.PostgresVideoRepository
import features.jobs.AvatarPollingJob
import features.notifications.PushNotificationService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import plugins.routing.avatar.avatarRoutes
import plugins.routing.billing.billingRoutes
import plugins.routing.ideas.ideasRoutes
import plugins.routing.textpost.textPostRoutes
import plugins.routing.translate.translateRoutes
import plugins.routing.users.usersRoutes

fun Application.configureRouting(
    avatarService: AvatarAiService,
    captionService: CaptionAiService,
    translateService: TranslateAiService,
    pushService: PushNotificationService,
    userRepo: PostgresUserRepository,
    videoRepo: PostgresVideoRepository,
    pollingJob: AvatarPollingJob,
    settings: Settings,
) {
    routing {
        route("/api") {
            avatarRoutes(avatarService, videoRepo, pollingJob)
            textPostRoutes(captionService)
            ideasRoutes(captionService)
            translateRoutes(translateService, videoRepo, pollingJob)
            billingRoutes(settings, userRepo)
            usersRoutes(settings, userRepo)
        }
    }
}
