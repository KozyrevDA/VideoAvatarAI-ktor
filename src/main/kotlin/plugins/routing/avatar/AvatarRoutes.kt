package plugins.routing.avatar

import data.remote.ai.AvatarAiService
import data.remote.ai.AvatarRequest
import data.repository.postgres.PostgresVideoRepository
import features.jobs.AvatarPollingJob
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.avatarRoutes(
    avatarService: AvatarAiService,
    videoRepo: PostgresVideoRepository,
    pollingJob: AvatarPollingJob,
) {
    post("/avatar/generate") {
        val request = call.receive<AvatarRequest>()
        val userId = call.request.headers["X-User-Id"] ?: "demo"
        val fcmToken = call.request.headers["X-FCM-Token"]

        val result = avatarService.generateAvatar(request)
        if (result.taskId.isBlank()) {
            call.respond(HttpStatusCode.InternalServerError, result)
            return@post
        }

        // Save to DB and start background polling
        val video = videoRepo.create(
            userId = userId,
            type = "avatar",
            title = request.text.take(80),
            taskId = result.taskId,
            language = request.language,
            platform = request.platform,
        )
        pollingJob.startPolling(result.taskId, video.id, userId, fcmToken)

        call.respond(HttpStatusCode.OK, result.copy(taskId = video.id))
    }

    get("/generation/{id}/status") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        // Try DB first
        val video = videoRepo.findByTaskId(id)
        if (video != null) {
            call.respond(HttpStatusCode.OK, mapOf(
                "id" to video.id,
                "status" to video.status,
                "videoUrl" to video.videoUrl,
                "progress" to if (video.status == "ready") 100 else 50,
            ))
        } else {
            // Fallback: poll Hedra directly
            val result = avatarService.checkStatus(id)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
