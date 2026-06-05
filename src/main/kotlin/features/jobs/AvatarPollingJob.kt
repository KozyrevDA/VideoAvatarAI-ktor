package features.jobs

import data.remote.ai.AvatarAiService
import data.repository.postgres.PostgresUserRepository
import data.repository.postgres.PostgresVideoRepository
import features.notifications.PushNotificationService
import kotlinx.coroutines.*

class AvatarPollingJob(
    private val avatarService: AvatarAiService,
    private val videoRepo: PostgresVideoRepository,
    private val userRepo: PostgresUserRepository,
    private val pushService: PushNotificationService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startPolling(taskId: String, videoId: String, userId: String, fcmToken: String?) {
        scope.launch {
            var attempts = 0
            while (attempts < 60) {
                delay(5_000)
                attempts++

                val result = avatarService.checkStatus(taskId)

                // checkStatus() возвращает "ready" | "processing" | "error"
                when (result.status) {
                    "ready" -> {
                        videoRepo.updateStatus(
                            videoId  = videoId,
                            status   = "ready",
                            videoUrl = result.videoUrl,
                        )
                        fcmToken?.let { token ->
                            val video = videoRepo.getHistory(userId, 1).firstOrNull()
                            pushService.sendVideoReady(token, video?.title ?: "Видео")
                        }
                        // Уведомляем если токенов мало
                        userRepo.findById(userId)?.let { user ->
                            if (user.tokensCount <= 2) {
                                fcmToken?.let { token ->
                                    pushService.sendTokensLow(token, user.tokensCount)
                                }
                            }
                        }
                        return@launch
                    }
                    "error" -> {
                        videoRepo.updateStatus(
                            videoId  = videoId,
                            status   = "error",
                            errorMsg = result.errorMessage,
                        )
                        return@launch
                    }
                    // "processing" → продолжаем ждать
                }
            }
            videoRepo.updateStatus(videoId = videoId, status = "error", errorMsg = "Timeout после 5 минут")
        }
    }
}
