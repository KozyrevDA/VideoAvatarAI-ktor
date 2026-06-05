package plugins.routing.translate

import data.remote.ai.TranslateAiService
import data.remote.ai.TranslateRequest
import data.repository.postgres.PostgresVideoRepository
import features.jobs.AvatarPollingJob
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.translateRoutes(
    translateService: TranslateAiService,
    videoRepo: PostgresVideoRepository,
    pollingJob: AvatarPollingJob,
) {
    post("/translate/generate") {
        val request = call.receive<TranslateRequest>()
        val userId   = call.request.headers["X-User-Id"] ?: "demo"
        val fcmToken = call.request.headers["X-FCM-Token"]

        val result = translateService.translateVideo(request)

        if (result.taskId.isNotBlank() && result.status != "error") {
            val video = videoRepo.create(
                userId   = userId,
                type     = "translation",
                title    = "Перевод → ${request.targetLanguage}",
                taskId   = result.taskId,
                language = request.targetLanguage,
                platform = "instagram",
            )
            pollingJob.startPolling(result.taskId, video.id, userId, fcmToken)
            call.respond(HttpStatusCode.OK, result.copy(taskId = video.id))
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
