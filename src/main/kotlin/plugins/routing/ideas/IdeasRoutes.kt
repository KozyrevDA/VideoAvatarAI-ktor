package plugins.routing.ideas

import data.remote.ai.CaptionAiService
import data.remote.ai.IdeasRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.ideasRoutes(captionService: CaptionAiService) {

    post("/ideas/generate") {
        val request = call.receive<IdeasRequest>()
        val result = captionService.generateIdeas(request)
        call.respond(HttpStatusCode.OK, result)
    }
}
