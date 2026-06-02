package plugins.routing.avatar

import data.remote.ai.AvatarAiService
import data.remote.ai.AvatarRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.avatarRoutes(avatarService: AvatarAiService) {

    post("/avatar/generate") {
        val request = call.receive<AvatarRequest>()
        val result = avatarService.generateAvatar(request)
        call.respond(HttpStatusCode.OK, result)
    }

    get("/avatar/status/{taskId}") {
        val taskId = call.parameters["taskId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing taskId")
        val result = avatarService.checkStatus(taskId)
        call.respond(HttpStatusCode.OK, result)
    }

    get("/generation/{id}/status") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
        val result = avatarService.checkStatus(id)
        call.respond(HttpStatusCode.OK, result)
    }
}
