package data.repository.postgres

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

// ─── Tables ──────────────────────────────────────────────────────────────────

object Users : UUIDTable("users") {
    val email        = varchar("email", 255).nullable()
    val provider     = varchar("provider", 32).default("email") // email | vk | google | apple
    val providerId   = varchar("provider_id", 255).nullable()
    val tokensCount  = integer("tokens_count").default(5)
    val isPro        = bool("is_pro").default(false)
    val subType      = varchar("sub_type", 32).nullable()        // monthly | yearly
    val subExpiresAt = datetime("sub_expires_at").nullable()
    val voiceId      = varchar("voice_id", 128).nullable()       // ElevenLabs cloned voice id
    val createdAt    = datetime("created_at").default(LocalDateTime.now())
    val updatedAt    = datetime("updated_at").default(LocalDateTime.now())
}

object Videos : UUIDTable("videos") {
    val userId      = reference("user_id", Users)
    val type        = varchar("type", 32)           // avatar | translation | text_post | ideas
    val title       = varchar("title", 512).default("")
    val status      = varchar("status", 32).default("processing") // processing | ready | error
    val videoUrl    = varchar("video_url", 2048).nullable()
    val thumbnailUrl = varchar("thumbnail_url", 2048).nullable()
    val language    = varchar("language", 32).default("ru")
    val platform    = varchar("platform", 32).default("instagram")
    val taskId      = varchar("task_id", 256).nullable()  // Hedra/ElevenLabs task id
    val errorMsg    = text("error_msg").nullable()
    val createdAt   = datetime("created_at").default(LocalDateTime.now())
    val updatedAt   = datetime("updated_at").default(LocalDateTime.now())
}

object Purchases : UUIDTable("purchases") {
    val userId       = reference("user_id", Users)
    val productId    = varchar("product_id", 128)
    val purchaseToken = varchar("purchase_token", 512)
    val store        = varchar("store", 32)          // rustore | google | apple
    val status       = varchar("status", 32).default("pending") // pending | confirmed | refunded
    val tokensAdded  = integer("tokens_added").default(0)
    val createdAt    = datetime("created_at").default(LocalDateTime.now())
}

// ─── Database init ────────────────────────────────────────────────────────────

object PostgresDatabase {

    fun init(url: String, user: String, password: String) {
        Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password,
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Videos, Purchases)
        }
    }
}
