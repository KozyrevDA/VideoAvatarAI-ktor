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

@Serializable
data class CaptionRequest(
    val topic: String,
    val platform: String = "instagram",
    val tone: String = "friendly",     // friendly | expert | funny
    val language: String = "ru",
)

@Serializable
data class CaptionResult(
    val text: String,
    val hashtags: List<String>,
    val platform: String,
)

@Serializable
data class IdeasRequest(
    val niche: String,
    val platform: String = "instagram",
    val count: Int = 30,
)

@Serializable
data class IdeasResult(
    val ideas: List<String>,
)

@Serializable
private data class AnthropicRequest(
    val model: String = "claude-haiku-4-5-20251001",
    val max_tokens: Int = 1024,
    val messages: List<AnthropicMessage>,
    val system: String,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContent>,
)

@Serializable
private data class AnthropicContent(
    val type: String,
    val text: String = "",
)

class CaptionAiService(private val anthropicApiKey: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine { requestTimeout = 60_000 }
    }

    suspend fun generateCaption(request: CaptionRequest): CaptionResult {
        val platformLabel = when (request.platform) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "vk" -> "ВКонтакте"
            "youtube" -> "YouTube Shorts"
            else -> request.platform
        }
        val toneLabel = when (request.tone) {
            "expert" -> "экспертный, профессиональный"
            "funny" -> "с юмором, легкий"
            else -> "дружелюбный, тёплый"
        }

        val systemPrompt = """
Ты — опытный SMM-специалист. Пишешь тексты для постов в соцсетях.
Отвечай ТОЛЬКО JSON без markdown, без преамбулы.
Формат: {"text": "текст поста", "hashtags": ["хэштег1", "хэштег2"]}
""".trimIndent()

        val userPrompt = """
Напиши текст поста для $platformLabel.
Тема: ${request.topic}
Тон: $toneLabel
Язык: ${if (request.language == "ru") "русский" else request.language}
Длина: 2-4 предложения + призыв к действию.
Хэштеги: 5-7 релевантных без #.
""".trimIndent()

        val responseText = callClaude(systemPrompt, userPrompt)

        return try {
            val clean = responseText.trim().removePrefix("```json").removeSuffix("```").trim()
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(clean)
            val text = parsed["text"]?.toString()?.trim('"') ?: responseText
            val hashtags = parsed["hashtags"]?.let {
                json.decodeFromString<List<String>>(it.toString())
            } ?: emptyList()
            CaptionResult(text = text, hashtags = hashtags, platform = request.platform)
        } catch (e: Exception) {
            CaptionResult(text = responseText, hashtags = emptyList(), platform = request.platform)
        }
    }

    suspend fun generateIdeas(request: IdeasRequest): IdeasResult {
        val platformLabel = when (request.platform) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "vk" -> "ВКонтакте"
            else -> request.platform
        }

        val systemPrompt = """
Ты — контент-стратег. Генерируешь идеи для постов в соцсетях.
Отвечай ТОЛЬКО JSON без markdown.
Формат: {"ideas": ["идея 1", "идея 2", ...]}
""".trimIndent()

        val userPrompt = """
Придумай ${request.count} идей для постов в $platformLabel.
Ниша: ${request.niche}
Требования:
- Каждая идея — одно конкретное предложение
- Разные форматы: списки, истории, обзоры, советы, разоблачения
- Кликабельные заголовки
- На русском языке
""".trimIndent()

        val responseText = callClaude(systemPrompt, userPrompt)

        return try {
            val clean = responseText.trim().removePrefix("```json").removeSuffix("```").trim()
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(clean)
            val ideas = parsed["ideas"]?.let {
                json.decodeFromString<List<String>>(it.toString())
            } ?: emptyList()
            IdeasResult(ideas = ideas)
        } catch (e: Exception) {
            IdeasResult(ideas = listOf(responseText))
        }
    }

    private suspend fun callClaude(systemPrompt: String, userPrompt: String): String {
        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", anthropicApiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(
                AnthropicRequest(
                    system = systemPrompt,
                    messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
                )
            )
        }.body<AnthropicResponse>()
        return response.content.firstOrNull { it.type == "text" }?.text ?: ""
    }

    fun close() = client.close()
}
