package plugins.routing.textpost

import data.remote.ai.CaptionAiService
import data.remote.ai.GenerateTextPostRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.textPostRoutes(captionService: CaptionAiService) {
    post("/text/generate") {
        val request = call.receive<GenerateTextPostRequest>()
        val result = captionService.generateTextPost(request)
        call.respond(HttpStatusCode.OK, result)
    }
}
