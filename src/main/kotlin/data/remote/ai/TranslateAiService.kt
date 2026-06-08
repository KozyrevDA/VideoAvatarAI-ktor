package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ─── Models ──────────────────────────────────────────────────────────────────

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

// OpenAI-compatible (laozhang.ai)
@Serializable
private data class OaiMsg(val role: String, val content: String)

@Serializable
private data class OaiReq(
    val model: String,
    val messages: List<OaiMsg>,
    @SerialName("max_tokens") val maxTokens: Int = 600,
    val temperature: Double = 0.3,
)

@Serializable
private data class OaiResp(val choices: List<OaiChoice>)

@Serializable
private data class OaiChoice(val message: OaiMsg)

@Serializable
private data class HedraUploadResp(val url: String = "")

@Serializable
private data class FishTtsReq(
    val text: String,
    val reference_id: String? = null,
    val format: String = "mp3",
    val latency: String = "normal",
)

@Serializable
private data class HedraJobResp(
    val id: String = "",
    val jobId: String? = null,
) { val taskId get() = id.ifBlank { jobId ?: "" } }

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Пайплайн перевода видео:
 *   1. GPT-5.2 (laozhang.ai) — переводит sourceText на targetLanguage
 *   2. Fish Audio TTS         — озвучивает перевод (клонированным голосом или стандартным)
 *   3. Hedra Kling v2 Std     — новый lip-sync с переведённым аудио
 *   4. Polling → готово       — через AvatarPollingJob
 */
class TranslateAiService(
    private val hedraApiKey: String,
    private val fishAudioApiKey: String,
    private val laozhangApiKey: String = "",
    private val laozhangModel: String = "chatgpt-5.2",
    private val avatarModel: String = "kling_ai_avatar_v2_standard",
) {
    private val HEDRA = "https://api.hedra.com/web-app/public"
    private val FISH  = "https://api.fish.audio"
    private val LZ    = "https://api.laozhang.ai/v1"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 120_000 }
    }

    // ── Главный метод ─────────────────────────────────────────────────────────

    suspend fun translateVideo(request: TranslateRequest): TranslateResult {
        return try {
            // 1. Переводим текст через GPT-5.2
            val translatedText = translateText(
                text           = request.sourceText,
                targetLanguage = request.targetLanguage,
            )

            // 2. Fish Audio TTS — переведённый текст → MP3
            val audioBytes = fishTts(
                text    = translatedText,
                voiceId = if (request.useClonedVoice) request.voiceId else null,
            )

            // 3. Загружаем аудио в Hedra
            val audioUrl = hedraUpload(audioBytes, "audio/mpeg", "translation.mp3")

            // 4. Нам нужно фото из оригинального видео — запрашиваем thumbnail
            // Если нет thumbnail — создаём job без фото (avatar-only режим)
            val photoUrl = getOriginalVideoThumbnail(request.videoId)

            // 5. Запускаем Kling v2 Standard с переведённым аудио
            val jobResp = client.post("$HEDRA/characters") {
                header("X-API-Key", hedraApiKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "model"        to avatarModel,
                    "image_url"    to (photoUrl ?: ""),
                    "audio_url"    to audioUrl,
                    "aspect_ratio" to "9:16",
                ))
            }.body<HedraJobResp>()

            val taskId = jobResp.taskId.ifBlank {
                throw RuntimeException("Hedra вернул пустой job ID")
            }

            TranslateResult(
                taskId         = taskId,
                status         = "processing",
                translatedText = translatedText,
            )
        } catch (e: Exception) {
            TranslateResult(taskId = "", status = "error", errorMessage = e.message)
        }
    }

    // ── Перевод текста через GPT-5.2 ─────────────────────────────────────────

    suspend fun translateText(text: String, targetLanguage: String): String {
        if (laozhangApiKey.isBlank()) {
            // Fallback: возвращаем оригинал если нет API ключа
            return text
        }
        val resp = client.post("$LZ/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $laozhangApiKey")
            contentType(ContentType.Application.Json)
            setBody(OaiReq(
                model    = laozhangModel,
                messages = listOf(
                    OaiMsg("system",
                        "Ты профессиональный переводчик. Переводи точно и естественно, " +
                        "сохраняй разговорный стиль, эмодзи и пунктуацию оригинала. " +
                        "Возвращай ТОЛЬКО перевод, без пояснений."),
                    OaiMsg("user", "Переведи на $targetLanguage:\n\n$text"),
                ),
                maxTokens   = 600,
                temperature = 0.3,
            ))
        }.body<OaiResp>()

        return resp.choices.firstOrNull()?.message?.content?.trim()
            ?: text
    }

    // ── Fish Audio TTS ────────────────────────────────────────────────────────

    private suspend fun fishTts(text: String, voiceId: String?): ByteArray =
        client.post("$FISH/v1/tts") {
            header("Authorization", "Bearer $fishAudioApiKey")
            contentType(ContentType.Application.Json)
            setBody(FishTtsReq(
                text         = text,
                reference_id = voiceId,
                format       = "mp3",
                latency      = "normal",
            ))
        }.body<ByteArray>()

    // ── Hedra upload ──────────────────────────────────────────────────────────

    private suspend fun hedraUpload(
        bytes: ByteArray,
        mimeType: String,
        filename: String,
    ): String = client.post("$HEDRA/assets") {
        header("X-API-Key", hedraApiKey)
        setBody(MultiPartFormDataContent(formData {
            appendInput("file", headers = Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
            }, size = bytes.size.toLong()) {
                buildPacket { writeFully(bytes) }
            }
        }))
    }.body<HedraUploadResp>().url.ifBlank {
        throw RuntimeException("Hedra: пустой URL после загрузки $filename")
    }

    // ── Получаем thumbnail оригинального видео ────────────────────────────────

    private suspend fun getOriginalVideoThumbnail(videoId: String): String? {
        // Пробуем получить статус оригинального видео из Hedra
        return try {
            val statusResp = client.get("$HEDRA/generations/$videoId") {
                header("X-API-Key", hedraApiKey)
            }.body<JsonObject>()
            statusResp["thumbnail_url"]?.jsonPrimitive?.contentOrNull
                ?: statusResp["image_url"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null  // Нет thumbnail — Hedra создаст аватар без исходного фото
        }
    }

    fun close() = client.close()
}
