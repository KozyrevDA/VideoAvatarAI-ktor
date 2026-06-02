package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TranslateRequest(
    val videoId: String,
    val sourceText: String,
    val targetLanguage: String,
    val voiceId: String? = null,
    val useClonedVoice: Boolean = true,
)

@Serializable
data class TranslateResult(
    val taskId: String,
    val status: String = "processing",
    val videoUrl: String? = null,
    val translatedText: String? = null,
    val errorMessage: String? = null,
)

class TranslateAiService(
    private val hedraApiKey: String,
    private val elevenlabsApiKey: String,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine { requestTimeout = 120_000 }
    }

    // ElevenLabs voices per language
    private val voicesByLanguage = mapOf(
        "English" to "21m00Tcm4TlvDq8ikWAM",
        "Español" to "AZnzlk1XvdvUeBnXmlld",
        "Deutsch" to "IKne3meq5aSn9XLyUdCD",
        "Français" to "MF3mGyEYCl7XYWbV9V6O",
        "中文" to "jBpfuIE2acCo8z3wKNLl",
        "日本語" to "Yko7PKHZNXotIFUBG7I9",
        "Português" to "g5CIjZEefAph4nQFvHAz",
        "العربية" to "ThT5KcBeYPX3keUQqHPh",
        "한국어" to "D38z5RcWu1voky8WS1ja",
        "Hindi" to "SOYHLrjzK2X1ezoPC6cr",
        "Turkish" to "wViXBPUzp2ZZixB1xQuM",
        "Italian" to "zcAOhNBS3c14rBihAFp1",
        "Polish" to "E3u0zBxfKQrHUxJGxfmV",
        "Dutch" to "Xb7hH8MSUJpSbSDYk0k2",
    )

    suspend fun translateVideo(request: TranslateRequest): TranslateResult {
        return try {
            // Step 1: Translate text via simple translation
            // TODO: integrate DeepL or Claude for translation
            val translatedText = translateText(request.sourceText, request.targetLanguage)

            // Step 2: Generate audio in target language via ElevenLabs
            val voiceId = request.voiceId ?: voicesByLanguage[request.targetLanguage] ?: "21m00Tcm4TlvDq8ikWAM"
            val avatarService = AvatarAiService(
                hedraApiKey = hedraApiKey,
                elevenlabsApiKey = elevenlabsApiKey,
                veo3ApiKey = "",
            )

            // Step 3: Re-generate avatar with translated audio
            // (same photo but new voice in target language)
            // In production: fetch original photo from storage by videoId
            // For now return placeholder
            TranslateResult(
                taskId = "translate_${request.videoId}_${request.targetLanguage}",
                status = "processing",
                translatedText = translatedText,
            )
        } catch (e: Exception) {
            TranslateResult(
                taskId = "",
                status = "error",
                errorMessage = e.message,
            )
        }
    }

    // Simple translation using Claude (same anthropic endpoint)
    private suspend fun translateText(text: String, targetLang: String): String {
        // TODO: call Claude API to translate text
        // For now return original with language tag
        return "[Перевод на $targetLang]: $text"
    }

    fun close() = client.close()
}
