package features.notifications

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─── FCM v1 API models ────────────────────────────────────────────────────────

@Serializable
private data class FcmV1Message(val message: FcmV1Body)

@Serializable
private data class FcmV1Body(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String> = emptyMap(),
    val android: FcmAndroidConfig? = null,
)

@Serializable
private data class FcmNotification(val title: String, val body: String)

@Serializable
private data class FcmAndroidConfig(
    val priority: String = "high",
    val notification: FcmAndroidNotification? = null,
)

@Serializable
private data class FcmAndroidNotification(
    @SerialName("channel_id") val channelId: String = "video_ready",
    @SerialName("click_action") val clickAction: String = "FLUTTER_NOTIFICATION_CLICK",
)

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Firebase Cloud Messaging v1 API.
 *
 * fcmServerKey — это OAuth2 Bearer token, получаемый из Service Account JSON.
 * В FCM v1 старые Server Key (AIza...) не работают — нужен Service Account.
 *
 * Как получить:
 *   1. Firebase Console → Project Settings → Service Accounts
 *   2. Generate New Private Key → скачать JSON
 *   3. Сгенерировать access token: google-auth-library + serviceAccountJson
 *   4. Передать access token как FCM_SERVER_KEY в .env
 *
 * Endpoint: https://fcm.googleapis.com/v1/projects/{projectId}/messages:send
 */
class PushNotificationService(
    private val fcmServerKey: String,         // OAuth2 access token из Service Account
    private val fcmProjectId: String = "videoavataraii",
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun sendVideoReady(fcmToken: String, videoTitle: String) = send(
        token = fcmToken,
        title = "Видео готово! 🎬",
        body  = "«$videoTitle» уже ждёт тебя",
        data  = mapOf("type" to "video_ready"),
    )

    suspend fun sendTranslationReady(fcmToken: String, language: String) = send(
        token = fcmToken,
        title = "Перевод готов! 🌍",
        body  = "Видео переведено на $language",
        data  = mapOf("type" to "translation_ready", "language" to language),
    )

    suspend fun sendTokensLow(fcmToken: String, remaining: Int) {
        if (remaining <= 2) send(
            token = fcmToken,
            title = "Токены заканчиваются 🪙",
            body  = "Осталось $remaining видео — пополни баланс",
            data  = mapOf("type" to "tokens_low", "remaining" to remaining.toString()),
        )
    }

    private suspend fun send(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        if (fcmServerKey.isBlank() || token.isBlank()) return
        try {
            client.post("https://fcm.googleapis.com/v1/projects/$fcmProjectId/messages:send") {
                header(HttpHeaders.Authorization, "Bearer $fcmServerKey")
                contentType(ContentType.Application.Json)
                setBody(FcmV1Message(
                    message = FcmV1Body(
                        token = token,
                        notification = FcmNotification(title, body),
                        data = data,
                        android = FcmAndroidConfig(
                            priority = "high",
                            notification = FcmAndroidNotification(channelId = "video_ready"),
                        ),
                    ),
                ))
            }
        } catch (e: Exception) {
            // Push не критичен — логируем но не падаем
            println("FCM error: ${e.message}")
        }
    }

    fun close() = client.close()
}
