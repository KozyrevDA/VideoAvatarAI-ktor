package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─── Models ──────────────────────────────────────────────────────────────────

@Serializable
data class GenerateTextPostRequest(
    val topic: String,
    val platform: String = "instagram",
    val tone: TextTone = TextTone.FRIENDLY,
)

@Serializable
data class GenerateIdeasRequest(
    val niche: String,
    val platform: String = "instagram",
    val count: Int = 30,
)

@Serializable
data class TextPostResult(
    val text: String,
    val hashtags: List<String> = emptyList(),
    val characterCount: Int = 0,
)

@Serializable
data class IdeasResult(val ideas: List<String>)

@Serializable
enum class TextTone { FRIENDLY, EXPERT, FUNNY, MOTIVATIONAL }

// OpenAI-compatible request/response (laozhang.ai)
@Serializable
private data class OaiMessage(val role: String, val content: String)

@Serializable
private data class OaiRequest(
    val model: String,
    val messages: List<OaiMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.8,
)

@Serializable
private data class OaiChoice(val message: OaiMessage)

@Serializable
private data class OaiResponse(val choices: List<OaiChoice>)

// ─── Service ─────────────────────────────────────────────────────────────────

class CaptionAiService(
    private val laozhangApiKey: String,          // laozhang.ai ключ
    private val anthropicApiKey: String = "",    // резерв (если нужен Claude)
    private val model: String = "chatgpt-5.2",   // модель на laozhang.ai
) {
    private val BASE_URL = "https://api.laozhang.ai/v1"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine { requestTimeout = 30_000 }
    }

    // ── Текст для поста ───────────────────────────────────────────────────────

    suspend fun generateTextPost(request: GenerateTextPostRequest): TextPostResult {
        val platformGuide = when (request.platform) {
            "instagram" -> "Instagram: 150–300 символов, 5–10 хэштегов, эмодзи"
            "tiktok"    -> "TikTok: 100–150 символов, 3–5 хэштегов, динамично"
            "vk"        -> "ВКонтакте: 300–600 символов, без хэштегов в тексте"
            "youtube"   -> "YouTube: описание 200–400 символов, 3–5 хэштегов"
            else        -> "универсальный формат, 200–300 символов"
        }

        val toneGuide = when (request.tone) {
            TextTone.FRIENDLY    -> "дружелюбный, разговорный, тёплый"
            TextTone.EXPERT      -> "экспертный, уверенный, с фактами"
            TextTone.FUNNY       -> "с юмором, лёгкий, самоирония"
            TextTone.MOTIVATIONAL -> "мотивирующий, вдохновляющий, призыв к действию"
        }

        val prompt = """
            Напиши текст для поста в $platformGuide.
            Тема: ${request.topic}
            Тон: $toneGuide
            
            Верни JSON (без markdown-блоков):
            {
              "text": "текст поста",
              "hashtags": ["хэштег1", "хэштег2"]
            }
        """.trimIndent()

        return try {
            val raw = chat(prompt)
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(clean)
            val text = parsed["text"]?.toString()?.trim('"') ?: raw
            val hashtags = parsed["hashtags"]?.let {
                Json.decodeFromString<List<String>>(it.toString())
            } ?: emptyList()
            TextPostResult(text = text, hashtags = hashtags, characterCount = text.length)
        } catch (e: Exception) {
            TextPostResult(text = "Ошибка генерации: ${e.message}")
        }
    }

    // ── 30 идей для контента ─────────────────────────────────────────────────

    suspend fun generateIdeas(request: GenerateIdeasRequest): IdeasResult {
        val platformGuide = when (request.platform) {
            "instagram" -> "Instagram Reels и посты"
            "tiktok"    -> "TikTok видео"
            "vk"        -> "ВКонтакте клипы и посты"
            "youtube"   -> "YouTube Shorts и видео"
            else        -> "социальные сети"
        }

        val prompt = """
            Придумай ровно ${request.count} идей для контента в ${platformGuide}.
            Ниша: ${request.niche}
            
            Требования:
            - Каждая идея — конкретная тема для видео или поста (1–2 предложения)
            - Разнообразие форматов: обучение, личное, юмор, лайфхак, кейс
            - Ориентация на русскую аудиторию
            
            Верни JSON (без markdown-блоков):
            {"ideas": ["идея 1", "идея 2", ...]}
        """.trimIndent()

        return try {
            val raw = chat(prompt, maxTokens = 2000)
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            Json.decodeFromString<IdeasResult>(clean)
        } catch (e: Exception) {
            IdeasResult(ideas = listOf("Ошибка генерации идей: ${e.message}"))
        }
    }

    // ── Перевод текста ────────────────────────────────────────────────────────

    suspend fun translateText(text: String, targetLanguage: String): String {
        val prompt = """
            Переведи текст на $targetLanguage.
            Сохрани стиль, тон и структуру оригинала.
            Верни только перевод, без пояснений.
            
            Текст: $text
        """.trimIndent()
        return try {
            chat(prompt)
        } catch (e: Exception) {
            "Ошибка перевода: ${e.message}"
        }
    }

    // ─── Приватный helper ─────────────────────────────────────────────────────

    private suspend fun chat(prompt: String, maxTokens: Int = 1000): String {
        val response = client.post("$BASE_URL/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $laozhangApiKey")
            contentType(ContentType.Application.Json)
            setBody(OaiRequest(
                model = model,
                messages = listOf(OaiMessage(role = "user", content = prompt)),
                max_tokens = maxTokens,
                temperature = 0.8,
            ))
        }.body<OaiResponse>()

        return response.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("Empty response from $model")
    }

    fun close() = client.close()
}
