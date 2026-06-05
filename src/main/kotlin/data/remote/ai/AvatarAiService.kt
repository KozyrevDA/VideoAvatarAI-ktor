package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─── Models ───────────────────────────────────────────────────────────────────

@Serializable
data class AvatarRequest(
    val photoBase64: String,
    val text: String,
    val language: String = "ru",
    val voiceId: String? = null,
    val style: String = "business",
    val platform: String = "instagram",
)

@Serializable
data class AvatarResult(
    val taskId: String,
    val status: String = "processing",
    val videoUrl: String? = null,
    val progress: Int = 0,
    val errorMessage: String? = null,
)

// Fish Audio TTS request
@Serializable
private data class FishTtsRequest(
    val text: String,
    val reference_id: String? = null,    // ID клонированного голоса
    val format: String = "mp3",
    val mp3_bitrate: Int = 128,
    val chunk_length: Int = 200,
    val normalize: Boolean = true,
    val latency: String = "normal",      // "normal" | "balanced"
)

// Hedra lip-sync models
@Serializable
private data class HedraJobResponse(
    val jobId: String? = null,
    val job_id: String? = null,
) {
    val id: String get() = jobId ?: job_id ?: ""
}

@Serializable
private data class HedraStatusResponse(
    val status: String = "pending",
    val url: String? = null,
    val progress: Int = 0,
    val error: String? = null,
)

// ─── Service ──────────────────────────────────────────────────────────────────

class AvatarAiService(
    private val hedraApiKey: String,
    private val fishAudioApiKey: String,      // Fish Audio (заменяет ElevenLabs)
    private val elevenlabsApiKey: String = "", // оставляем для совместимости
    private val veo3ApiKey: String = "",
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine { requestTimeout = 120_000 }
    }

    private val FISH_BASE = "https://api.fish.audio"
    private val HEDRA_BASE = "https://mercury.dev.dream-ai.com/api/v1"

    /**
     * Полный пайплайн:
     * текст → Fish Audio TTS → аудио → Hedra lip-sync → видео
     */
    suspend fun generateAvatar(request: AvatarRequest): AvatarResult {
        return try {
            // Шаг 1: Fish Audio TTS → аудио байты
            val audioBytes = generateAudioFishAudio(
                text = request.text,
                voiceId = request.voiceId,
                language = request.language,
            )

            // Шаг 2: Загружаем аудио в Hedra
            val audioUrl = uploadAudioToHedra(audioBytes)

            // Шаг 3: Создаём Hedra lip-sync job
            val aspectRatio = when (request.platform) {
                "youtube", "vk" -> "16:9"
                else -> "9:16"   // instagram, tiktok → вертикальное
            }
            val jobId = submitHedraJob(
                photoBase64 = request.photoBase64,
                audioUrl = audioUrl,
                aspectRatio = aspectRatio,
            )

            AvatarResult(taskId = jobId, status = "processing", progress = 0)
        } catch (e: Exception) {
            AvatarResult(taskId = "", status = "error", errorMessage = e.message ?: "Unknown error")
        }
    }

    /**
     * Клонирование голоса через Fish Audio.
     * Загружаем запись голоса пользователя → получаем reference_id → сохраняем в профиль.
     */
    suspend fun cloneVoice(audioBytes: ByteArray, title: String): String {
        val response = client.post("$FISH_BASE/v1/model") {
            header("Authorization", "Bearer $fishAudioApiKey")
            setBody(MultiPartFormDataContent(formData {
                appendInput("title", headers = Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"title\"")
                }) { buildPacket { writeText(title) } }
                appendInput("voices", headers = Headers.build {
                    append(HttpHeaders.ContentType, "audio/mpeg")
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"voices\"; filename=\"voice.mp3\"")
                }, size = audioBytes.size.toLong()) {
                    buildPacket { writeFully(audioBytes) }
                }
            }))
        }
        val json = response.body<String>()
        // Парсим _id из ответа
        val idRegex = "\"_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return idRegex.find(json)?.groupValues?.getOrNull(1)
            ?: throw RuntimeException("Voice ID not found in response: $json")
    }

    /**
     * Поллинг статуса Hedra job.
     */
    suspend fun checkStatus(taskId: String): AvatarResult {
        return try {
            val response = client.get("$HEDRA_BASE/projects/$taskId") {
                header("X-API-Key", hedraApiKey)
            }.body<HedraStatusResponse>()

            AvatarResult(
                taskId = taskId,
                status = response.status,
                videoUrl = response.url,
                progress = response.progress,
                errorMessage = response.error,
            )
        } catch (e: Exception) {
            AvatarResult(taskId = taskId, status = "error", errorMessage = e.message)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun generateAudioFishAudio(
        text: String,
        voiceId: String?,
        language: String,
    ): ByteArray {
        // Fish Audio TTS — streaming endpoint возвращает аудио байты напрямую
        val response = client.post("$FISH_BASE/v1/tts") {
            header("Authorization", "Bearer $fishAudioApiKey")
            contentType(ContentType.Application.Json)
            setBody(FishTtsRequest(
                text = text,
                reference_id = voiceId,  // null → стандартный голос
                format = "mp3",
                latency = "normal",
            ))
        }
        return response.body<ByteArray>()
    }

    private suspend fun uploadAudioToHedra(audioBytes: ByteArray): String {
        val response = client.post("$HEDRA_BASE/audio") {
            header("X-API-Key", hedraApiKey)
            setBody(MultiPartFormDataContent(formData {
                appendInput("file", headers = Headers.build {
                    append(HttpHeaders.ContentType, "audio/mpeg")
                    append(HttpHeaders.ContentDisposition, "filename=voice.mp3")
                }, size = audioBytes.size.toLong()) {
                    buildPacket { writeFully(audioBytes) }
                }
            }))
        }
        val json = response.body<String>()
        val urlRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return urlRegex.find(json)?.groupValues?.getOrNull(1)
            ?: throw RuntimeException("Audio URL not found")
    }

    private suspend fun submitHedraJob(
        photoBase64: String,
        audioUrl: String,
        aspectRatio: String,
    ): String {
        val photoBytes = java.util.Base64.getDecoder().decode(photoBase64)

        // Загружаем фото в Hedra
        val photoResp = client.post("$HEDRA_BASE/portrait") {
            header("X-API-Key", hedraApiKey)
            setBody(MultiPartFormDataContent(formData {
                appendInput("file", headers = Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                }, size = photoBytes.size.toLong()) {
                    buildPacket { writeFully(photoBytes) }
                }
            }))
        }
        val photoJson = photoResp.body<String>()
        val photoUrl = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            .find(photoJson)?.groupValues?.getOrNull(1)
            ?: throw RuntimeException("Photo URL not found")

        // Создаём lip-sync job
        val jobResp = client.post("$HEDRA_BASE/characters") {
            header("X-API-Key", hedraApiKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "text" to "",
                "voice_url" to audioUrl,
                "avatar_image_input" to mapOf("url" to photoUrl, "type" to "url"),
                "aspect_ratio" to aspectRatio,
            ))
        }.body<HedraJobResponse>()

        return jobResp.id.ifBlank { throw RuntimeException("Job ID empty from Hedra") }
    }

    fun close() = client.close()
}
