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
                when (result.status) {
                    "completed", "ready" -> {
                        videoRepo.updateStatus(
                            videoId = videoId,
                            status = "ready",
                            videoUrl = result.videoUrl,
                        )
                        fcmToken?.let {
                            val video = videoRepo.getHistory(userId, 1).firstOrNull()
                            pushService.sendVideoReady(it, video?.title ?: "Видео")
                        }
                        val user = userRepo.findById(userId)
                        user?.let {
                            if (it.tokensCount <= 2) {
                                fcmToken?.let { token -> pushService.sendTokensLow(token, it.tokensCount) }
                            }
                        }
                        return@launch
                    }
                    "error", "failed" -> {
                        videoRepo.updateStatus(
                            videoId = videoId,
                            status = "error",
                            errorMsg = result.errorMessage,
                        )
                        return@launch
                    }
                }
            }
            // Timeout
            videoRepo.updateStatus(videoId = videoId, status = "error", errorMsg = "Timeout")
        }
    }
}
