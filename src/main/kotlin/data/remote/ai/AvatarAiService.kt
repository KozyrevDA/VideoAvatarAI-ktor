package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ─── Public models ────────────────────────────────────────────────────────────

@Serializable
data class AvatarRequest(
    val photoBase64: String,
    val text: String,
    val language: String = "ru",
    val voiceId: String? = null,       // Fish Audio voice reference_id
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

// ─── Hedra Platform API wire models ──────────────────────────────────────────

@Serializable
private data class HedraGenerateRequest(
    val model: String,                   // "kling_ai_avatar_v2_standard"
    val image_url: String,
    val audio_url: String,
    val aspect_ratio: String = "9:16",   // 9:16 | 16:9 | 1:1
)

@Serializable
private data class HedraGenerateResponse(
    val id: String = "",
    val jobId: String? = null,
    val status: String = "pending",
    val error: String? = null,
) {
    val taskId: String get() = id.ifBlank { jobId ?: "" }
}

@Serializable
private data class HedraStatusResponse(
    val id: String = "",
    val status: String = "pending",     // pending | processing | complete | error
    val video_url: String? = null,
    val url: String? = null,
    val progress: Int? = null,
    val error_message: String? = null,
    val error: String? = null,
) {
    val videoUrl: String? get() = video_url ?: url
    val errorMsg: String? get() = error_message ?: error
    val progressPct: Int get() = progress ?: when (status) {
        "pending" -> 5
        "processing" -> 50
        "complete" -> 100
        else -> 0
    }
    val isDone: Boolean get() = status in listOf("complete", "completed", "ready")
    val isFailed: Boolean get() = status in listOf("error", "failed")
}

// Hedra media upload response
@Serializable
private data class HedraUploadResponse(
    val url: String = "",
    val id: String? = null,
)

// Fish Audio TTS request
@Serializable
private data class FishTtsRequest(
    val text: String,
    val reference_id: String? = null,
    val format: String = "mp3",
    val mp3_bitrate: Int = 128,
    val chunk_length: Int = 200,
    val normalize: Boolean = true,
    val latency: String = "normal",
)

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Пайплайн создания говорящего аватара:
 *   1. Fish Audio TTS  — текст → MP3 (клонированный голос пользователя)
 *   2. Hedra upload    — загружаем фото и аудио → получаем URL
 *   3. Hedra generate  — модель kling_ai_avatar_v2_standard → job ID
 *   4. Polling         — ждём завершения через checkStatus()
 *
 * Модель: Kling AI Avatar v2 Standard (8 кредитов/сек через Hedra)
 * То же что Hedra Character 3 по цене, лучше по качеству (плавнее, без over-smiling).
 */
class AvatarAiService(
    private val hedraApiKey: String,
    private val fishAudioApiKey: String,
    private val veo3ApiKey: String = "",
    // Модель можно переключить через env HEDRA_AVATAR_MODEL
    private val avatarModel: String = "kling_ai_avatar_v2_standard",
) {
    private val HEDRA = "https://api.hedra.com/web-app/public"
    private val FISH  = "https://api.fish.audio"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 120_000 }
    }

    // ── Главный метод ─────────────────────────────────────────────────────────

    suspend fun generateAvatar(request: AvatarRequest): AvatarResult {
        return try {
            // 1. Fish Audio TTS → MP3 bytes
            val audioBytes = fishTts(
                text    = request.text,
                voiceId = request.voiceId,
            )

            // 2. Загружаем фото и аудио в Hedra
            val aspectRatio = when (request.platform) {
                "youtube" -> "16:9"
                "vk"      -> "16:9"
                else      -> "9:16"   // instagram, tiktok, shorts
            }
            val photoBytes = java.util.Base64.getDecoder().decode(request.photoBase64)
            val photoUrl   = hedraUploadImage(photoBytes)
            val audioUrl   = hedraUploadAudio(audioBytes)

            // 3. Запускаем Kling v2 Standard через Hedra Platform API
            val response = client.post("$HEDRA/characters") {
                header("X-API-Key", hedraApiKey)
                contentType(ContentType.Application.Json)
                setBody(HedraGenerateRequest(
                    model        = avatarModel,   // kling_ai_avatar_v2_standard
                    image_url    = photoUrl,
                    audio_url    = audioUrl,
                    aspect_ratio = aspectRatio,
                ))
            }.body<HedraGenerateResponse>()

            val taskId = response.taskId.ifBlank {
                throw RuntimeException("Hedra вернул пустой job ID")
            }

            AvatarResult(taskId = taskId, status = "processing", progress = 0)
        } catch (e: Exception) {
            AvatarResult(taskId = "", status = "error", errorMessage = e.message ?: "Неизвестная ошибка")
        }
    }

    // ── Поллинг статуса ───────────────────────────────────────────────────────

    suspend fun checkStatus(taskId: String): AvatarResult {
        return try {
            val resp = client.get("$HEDRA/generations/$taskId") {
                header("X-API-Key", hedraApiKey)
            }.body<HedraStatusResponse>()

            AvatarResult(
                taskId       = taskId,
                status       = when {
                    resp.isDone   -> "ready"
                    resp.isFailed -> "error"
                    else          -> "processing"
                },
                videoUrl     = resp.videoUrl,
                progress     = resp.progressPct,
                errorMessage = resp.errorMsg,
            )
        } catch (e: Exception) {
            AvatarResult(taskId = taskId, status = "error", errorMessage = e.message)
        }
    }

    // ── Клонирование голоса через Fish Audio ─────────────────────────────────

    suspend fun cloneVoice(audioBytes: ByteArray, title: String): String {
        val response = client.post("$FISH/v1/model") {
            header("Authorization", "Bearer $fishAudioApiKey")
            setBody(MultiPartFormDataContent(formData {
                appendInput("title", headers = Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"title\"")
                }) { buildPacket { writeText(title) } }
                appendInput("voices", headers = Headers.build {
                    append(HttpHeaders.ContentType, "audio/mpeg")
                    append(HttpHeaders.ContentDisposition,
                        "form-data; name=\"voices\"; filename=\"voice.mp3\"")
                }, size = audioBytes.size.toLong()) {
                    buildPacket { writeFully(audioBytes) }
                }
            }))
        }
        val raw = response.body<String>()
        return "\"_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            .find(raw)?.groupValues?.getOrNull(1)
            ?: throw RuntimeException("Voice ID не найден: $raw")
    }

    // ─── Приватные helpers ────────────────────────────────────────────────────

    private suspend fun fishTts(text: String, voiceId: String?): ByteArray =
        client.post("$FISH/v1/tts") {
            header("Authorization", "Bearer $fishAudioApiKey")
            contentType(ContentType.Application.Json)
            setBody(FishTtsRequest(
                text         = text,
                reference_id = voiceId,
                format       = "mp3",
                latency      = "normal",
            ))
        }.body<ByteArray>()

    private suspend fun hedraUploadImage(imageBytes: ByteArray): String =
        client.post("$HEDRA/assets") {
            header("X-API-Key", hedraApiKey)
            setBody(MultiPartFormDataContent(formData {
                appendInput("file", headers = Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"photo.jpg\"")
                }, size = imageBytes.size.toLong()) {
                    buildPacket { writeFully(imageBytes) }
                }
            }))
        }.body<HedraUploadResponse>().url.ifBlank {
            throw RuntimeException("Hedra: пустой URL после загрузки фото")
        }

    private suspend fun hedraUploadAudio(audioBytes: ByteArray): String =
        client.post("$HEDRA/assets") {
            header("X-API-Key", hedraApiKey)
            setBody(MultiPartFormDataContent(formData {
                appendInput("file", headers = Headers.build {
                    append(HttpHeaders.ContentType, "audio/mpeg")
                    append(HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"voice.mp3\"")
                }, size = audioBytes.size.toLong()) {
                    buildPacket { writeFully(audioBytes) }
                }
            }))
        }.body<HedraUploadResponse>().url.ifBlank {
            throw RuntimeException("Hedra: пустой URL после загрузки аудио")
        }

    fun close() = client.close()
}
