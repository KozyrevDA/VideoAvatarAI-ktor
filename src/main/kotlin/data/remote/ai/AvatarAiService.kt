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

// ─── Request/Response models ───────────────────────────────────────────────

@Serializable
data class AvatarRequest(
    val photoBase64: String,
    val text: String,
    val language: String = "ru",
    val voiceId: String? = null,           // ElevenLabs voice id
    val style: String = "business",
    val platform: String = "instagram",    // instagram → 9:16, youtube → 16:9
)

@Serializable
data class AvatarResult(
    val taskId: String,
    val status: String = "processing",
    val videoUrl: String? = null,
    val progress: Int = 0,
    val errorMessage: String? = null,
)

@Serializable
private data class ElevenLabsTtsRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",
    val voice_settings: VoiceSettings = VoiceSettings(),
)

@Serializable
private data class VoiceSettings(
    val stability: Float = 0.5f,
    val similarity_boost: Float = 0.75f,
)

@Serializable
private data class HedraCharacterRequest(
    val text: String,
    val voice_url: String,
    val avatar_image_input: AvatarImageInput,
    val aspect_ratio: String = "9:16",
)

@Serializable
private data class AvatarImageInput(
    val url: String? = null,
    val type: String = "url",
)

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

// ─── Service ──────────────────────────────────────────────────────────────

class AvatarAiService(
    private val hedraApiKey: String,
    private val elevenlabsApiKey: String,
    private val veo3ApiKey: String,       // fallback for simple photo animation
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine { requestTimeout = 120_000 }
    }

    // Default Russian voice on ElevenLabs (Rachel — multilingual)
    private val defaultVoiceId = "21m00Tcm4TlvDq8ikWAM"

    /**
     * Full pipeline: text → ElevenLabs audio → Hedra lip-sync video
     */
    suspend fun generateAvatar(request: AvatarRequest): AvatarResult {
        return try {
            // Step 1: Generate audio via ElevenLabs TTS
            val voiceId = request.voiceId ?: defaultVoiceId
            val audioBytes = generateAudio(
                text = request.text,
                voiceId = voiceId,
                language = request.language,
            )

            // Step 2: Upload audio to Hedra and get audio URL
            val audioUrl = uploadAudioToHedra(audioBytes)

            // Step 3: Submit Hedra character animation job
            val aspectRatio = when (request.platform) {
                "youtube" -> "16:9"
                "vk" -> "16:9"
                else -> "9:16" // instagram, tiktok → vertical
            }

            val jobId = submitHedraJob(
                photoBase64 = request.photoBase64,
                audioUrl = audioUrl,
                aspectRatio = aspectRatio,
            )

            AvatarResult(taskId = jobId, status = "processing", progress = 0)
        } catch (e: Exception) {
            AvatarResult(
                taskId = "",
                status = "error",
                errorMessage = e.message ?: "Unknown error",
            )
        }
    }

    /**
     * Poll job status from Hedra
     */
    suspend fun checkStatus(taskId: String): AvatarResult {
        return try {
            val response = client.get("https://mercury.dev.dream-ai.com/api/v1/projects/$taskId") {
                bearerAuth(hedraApiKey)
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

    // ─── Private helpers ────────────────────────────────────────────────────

    private suspend fun generateAudio(text: String, voiceId: String, language: String): ByteArray {
        val response = client.post("https://api.elevenlabs.io/v1/text-to-speech/$voiceId") {
            header("xi-api-key", elevenlabsApiKey)
            contentType(ContentType.Application.Json)
            setBody(ElevenLabsTtsRequest(text = text))
        }
        return response.body<ByteArray>()
    }

    private suspend fun uploadAudioToHedra(audioBytes: ByteArray): String {
        val response = client.post("https://mercury.dev.dream-ai.com/api/v1/audio") {
            bearerAuth(hedraApiKey)
            setBody(
                MultiPartFormDataContent(formData {
                    appendInput(
                        key = "file",
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, "audio/mpeg")
                            append(HttpHeaders.ContentDisposition, "filename=voice.mp3")
                        },
                        size = audioBytes.size.toLong(),
                    ) { buildPacket { writeFully(audioBytes) } }
                })
            )
        }
        val json = response.body<String>()
        // Parse URL from response
        val urlRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return urlRegex.find(json)?.groupValues?.getOrNull(1) ?: throw RuntimeException("Audio URL not found in response")
    }

    private suspend fun submitHedraJob(
        photoBase64: String,
        audioUrl: String,
        aspectRatio: String,
    ): String {
        // Upload photo first
        val photoBytes = java.util.Base64.getDecoder().decode(photoBase64)
        val photoUploadResponse = client.post("https://mercury.dev.dream-ai.com/api/v1/portrait") {
            bearerAuth(hedraApiKey)
            setBody(
                MultiPartFormDataContent(formData {
                    appendInput(
                        key = "file",
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                        },
                        size = photoBytes.size.toLong(),
                    ) { buildPacket { writeFully(photoBytes) } }
                })
            )
        }
        val photoJson = photoUploadResponse.body<String>()
        val photoUrlRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val photoUrl = photoUrlRegex.find(photoJson)?.groupValues?.getOrNull(1)
            ?: throw RuntimeException("Photo URL not found")

        // Submit character animation job
        val jobResponse = client.post("https://mercury.dev.dream-ai.com/api/v1/characters") {
            bearerAuth(hedraApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                HedraCharacterRequest(
                    text = "",  // text already baked into audio
                    voice_url = audioUrl,
                    avatar_image_input = AvatarImageInput(url = photoUrl),
                    aspect_ratio = aspectRatio,
                )
            )
        }.body<HedraJobResponse>()

        return jobResponse.id.ifBlank { throw RuntimeException("Job ID not returned from Hedra") }
    }

    fun close() = client.close()
}
