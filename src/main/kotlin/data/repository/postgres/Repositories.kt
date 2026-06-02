package data.repository.postgres

import data.repository.postgres.Users
import data.repository.postgres.Videos
import data.repository.postgres.Purchases
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

data class UserRow(
    val id: String,
    val email: String?,
    val provider: String,
    val tokensCount: Int,
    val isPro: Boolean,
    val subType: String?,
    val voiceId: String?,
)

data class VideoRow(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val status: String,
    val videoUrl: String?,
    val language: String,
    val platform: String,
    val taskId: String?,
    val createdAt: String,
)

class PostgresUserRepository {

    fun createUser(email: String?, provider: String, providerId: String?): UserRow = transaction {
        val id = Users.insertAndGetId {
            it[Users.email] = email
            it[Users.provider] = provider
            it[Users.providerId] = providerId
            it[tokensCount] = 5  // free onboarding tokens
        }
        UserRow(id.value.toString(), email, provider, 5, false, null, null)
    }

    fun findByProviderId(provider: String, providerId: String): UserRow? = transaction {
        Users.select { (Users.provider eq provider) and (Users.providerId eq providerId) }
            .singleOrNull()?.toUserRow()
    }

    fun findById(userId: String): UserRow? = transaction {
        Users.select { Users.id eq UUID.fromString(userId) }
            .singleOrNull()?.toUserRow()
    }

    fun spendToken(userId: String): Boolean = transaction {
        val user = Users.select { Users.id eq UUID.fromString(userId) }.singleOrNull() ?: return@transaction false
        val current = user[Users.tokensCount]
        if (current <= 0) return@transaction false
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            it[tokensCount] = current - 1
            it[updatedAt] = LocalDateTime.now()
        }
        true
    }

    fun addTokens(userId: String, count: Int) = transaction {
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            with(SqlExpressionBuilder) {
                it.update(tokensCount, tokensCount + count)
            }
            it[updatedAt] = LocalDateTime.now()
        }
    }

    fun setPro(userId: String, subType: String, expiresAt: LocalDateTime) = transaction {
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            it[isPro] = true
            it[Users.subType] = subType
            it[subExpiresAt] = expiresAt
            it[updatedAt] = LocalDateTime.now()
        }
    }

    fun setVoiceId(userId: String, voiceId: String) = transaction {
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            it[Users.voiceId] = voiceId
            it[updatedAt] = LocalDateTime.now()
        }
    }

    private fun ResultRow.toUserRow() = UserRow(
        id = this[Users.id].value.toString(),
        email = this[Users.email],
        provider = this[Users.provider],
        tokensCount = this[Users.tokensCount],
        isPro = this[Users.isPro],
        subType = this[Users.subType],
        voiceId = this[Users.voiceId],
    )
}

class PostgresVideoRepository {

    fun create(userId: String, type: String, title: String, taskId: String?, language: String, platform: String): VideoRow = transaction {
        val id = Videos.insertAndGetId {
            it[Videos.userId] = UUID.fromString(userId)
            it[Videos.type] = type
            it[Videos.title] = title
            it[Videos.taskId] = taskId
            it[Videos.language] = language
            it[Videos.platform] = platform
            it[status] = "processing"
        }
        VideoRow(id.value.toString(), userId, type, title, "processing", null, language, platform, taskId, LocalDateTime.now().toString())
    }

    fun updateStatus(videoId: String, status: String, videoUrl: String? = null, errorMsg: String? = null) = transaction {
        Videos.update({ Videos.id eq UUID.fromString(videoId) }) {
            it[Videos.status] = status
            if (videoUrl != null) it[Videos.videoUrl] = videoUrl
            if (errorMsg != null) it[Videos.errorMsg] = errorMsg
            it[updatedAt] = LocalDateTime.now()
        }
    }

    fun findByTaskId(taskId: String): VideoRow? = transaction {
        Videos.select { Videos.taskId eq taskId }.singleOrNull()?.toVideoRow()
    }

    fun getHistory(userId: String, limit: Int = 50): List<VideoRow> = transaction {
        Videos
            .select { Videos.userId eq UUID.fromString(userId) }
            .orderBy(Videos.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toVideoRow() }
    }

    private fun ResultRow.toVideoRow() = VideoRow(
        id = this[Videos.id].value.toString(),
        userId = this[Videos.userId].value.toString(),
        type = this[Videos.type],
        title = this[Videos.title],
        status = this[Videos.status],
        videoUrl = this[Videos.videoUrl],
        language = this[Videos.language],
        platform = this[Videos.platform],
        taskId = this[Videos.taskId],
        createdAt = this[Videos.createdAt].toString(),
    )
}
