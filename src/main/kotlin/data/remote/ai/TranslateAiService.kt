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
    private val fishAudioApiKey: String,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    // Fish Audio поддерживает эти языки напрямую через voice ID
    // При клонированном голосе — тот же voice ID работает для всех языков
    private val defaultVoiceByLang = mapOf(
        "English"    to null,  // Fish Audio выбирает автоматически
        "Español"    to null,
        "Deutsch"    to null,
        "Français"   to null,
        "中文"        to null,
        "日本語"      to null,
        "Português"  to null,
        "한국어"      to null,
        "Arabic"     to null,
        "Русский"    to null,
    )

    suspend fun translateVideo(request: TranslateRequest): TranslateResult {
        return try {
            TranslateResult(
                taskId = "translate_${request.videoId}_${request.targetLanguage}",
                status = "processing",
                translatedText = "[Fish Audio перевод на ${request.targetLanguage}]",
            )
        } catch (e: Exception) {
            TranslateResult(taskId = "", status = "error", errorMessage = e.message)
        }
    }

    fun close() = client.close()
}
