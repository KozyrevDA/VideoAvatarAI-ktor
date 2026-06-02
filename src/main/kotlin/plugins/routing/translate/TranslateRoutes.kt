package plugins.routing.translate

import data.remote.ai.TranslateAiService
import data.remote.ai.TranslateRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.translateRoutes(translateService: TranslateAiService) {

    post("/translate/generate") {
        val request = call.receive<TranslateRequest>()
        val result = translateService.translateVideo(request)
        call.respond(HttpStatusCode.OK, result)
    }
}
