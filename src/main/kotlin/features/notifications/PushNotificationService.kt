package features.notifications

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FcmMessage(
    val message: FcmMessageBody,
)

@Serializable
data class FcmMessageBody(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class FcmNotification(
    val title: String,
    val body: String,
)

class PushNotificationService(
    private val fcmServerKey: String,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun sendVideoReady(
        fcmToken: String,
        videoTitle: String,
    ) {
        sendPush(
            token = fcmToken,
            title = "Видео готово! 🎬",
            body = "«$videoTitle» уже ждёт тебя",
            data = mapOf("type" to "video_ready"),
        )
    }

    suspend fun sendTranslationReady(
        fcmToken: String,
        language: String,
    ) {
        sendPush(
            token = fcmToken,
            title = "Перевод готов! 🌍",
            body = "Видео переведено на $language",
            data = mapOf("type" to "translation_ready", "language" to language),
        )
    }

    suspend fun sendTokensLow(fcmToken: String, remaining: Int) {
        if (remaining <= 2) {
            sendPush(
                token = fcmToken,
                title = "Токены заканчиваются 🪙",
                body = "Осталось $remaining видео. Пополни чтобы продолжить",
                data = mapOf("type" to "tokens_low"),
            )
        }
    }

    private suspend fun sendPush(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        try {
            client.post("https://fcm.googleapis.com/v1/projects/videoavataraii/messages:send") {
                header(HttpHeaders.Authorization, "Bearer $fcmServerKey")
                contentType(ContentType.Application.Json)
                setBody(
                    FcmMessage(
                        message = FcmMessageBody(
                            token = token,
                            notification = FcmNotification(title = title, body = body),
                            data = data,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            // Log but don't fail the main flow
        }
    }

    fun close() = client.close()
}
