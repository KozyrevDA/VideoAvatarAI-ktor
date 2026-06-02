package app

import data.remote.ai.AvatarAiService
import data.remote.ai.CaptionAiService
import data.remote.ai.TranslateAiService
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

    val avatarService = AvatarAiService(
        hedraApiKey = settings.hedraApiKey,
        elevenlabsApiKey = settings.elevenlabsApiKey,
        veo3ApiKey = settings.veo3ApiKey,
    )
    val captionService = CaptionAiService(
        anthropicApiKey = settings.anthropicApiKey,
    )
    val translateService = TranslateAiService(
        hedraApiKey = settings.hedraApiKey,
        elevenlabsApiKey = settings.elevenlabsApiKey,
    )

    configureRouting(
        avatarService = avatarService,
        captionService = captionService,
        translateService = translateService,
        settings = settings,
    )
}
