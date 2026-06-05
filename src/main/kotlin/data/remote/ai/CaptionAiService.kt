package data.remote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ─── Public models ────────────────────────────────────────────────────────────

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

// ─── OpenAI-compatible wire models ───────────────────────────────────────────

@Serializable
private data class Msg(val role: String, val content: String)

@Serializable
private data class ResponseFormat(@SerialName("type") val type: String)

@Serializable
private data class ChatReq(
    val model: String,
    val messages: List<Msg>,
    @SerialName("max_tokens")     val maxTokens: Int,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
private data class Choice(val message: Msg)

@Serializable
private data class ChatResp(val choices: List<Choice>)

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Генерация текстов для постов и идей контента через GPT-5.2 на laozhang.ai.
 *
 * laozhang.ai — OpenAI-совместимый прокси:
 *   base_url : https://api.laozhang.ai/v1
 *   auth     : Bearer {LAOZHANG_API_KEY}
 *   модели   : chatgpt-5.2, gpt-4o, gpt-4.1-mini, claude-3-5-haiku и др.
 *
 * Ключ передаётся через .env: LAOZHANG_API_KEY=...
 */
class CaptionAiService(
    private val laozhangApiKey: String,
    private val model: String = "chatgpt-5.2",
) {
    private val BASE = "https://api.laozhang.ai/v1"

    // System prompt — заставляет GPT-5.2 всегда возвращать чистый JSON
    private val SYSTEM = """
        Ты — эксперт по контент-маркетингу для русскоязычных соцсетей.
        ВАЖНО: отвечай ТОЛЬКО валидным JSON без markdown-обёрток (без ```json, без пояснений).
        Первый символ ответа — открывающая скобка {, последний — закрывающая }.
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 45_000 }
    }

    // ── Текст поста ───────────────────────────────────────────────────────────

    suspend fun generateTextPost(request: GenerateTextPostRequest): TextPostResult {
        val platform = platformSpec(request.platform)
        val tone     = toneSpec(request.tone)

        val prompt = """
            Напиши текст для поста.
            
            Платформа: ${request.platform.uppercase()} — $platform
            Тема: ${request.topic}
            Тон: $tone
            
            Правила:
            - текст живой, без канцелярита, на «ты»
            - эмодзи уместно, не перегружай
            - призыв к действию в конце (сохрани, поделись, напиши в комментах)
            - хэштеги без # в поле hashtags
            
            JSON-ответ строго по схеме:
            {"text":"...","hashtags":["слово1","слово2"]}
        """.trimIndent()

        return try {
            val raw = chatJson(prompt, maxTokens = 600, temperature = 0.85)
            val obj = json.parseToJsonElement(raw).jsonObject
            val text = obj["text"]!!.jsonPrimitive.content
            val tags = obj["hashtags"]?.jsonArray
                ?.map { it.jsonPrimitive.content.trimStart('#') }
                ?: emptyList()
            TextPostResult(text = text, hashtags = tags, characterCount = text.length)
        } catch (e: Exception) {
            // fallback: возвращаем сырой текст если JSON не распарсился
            val raw = chatPlain("Напиши короткий текст для поста на тему «${request.topic}».")
            TextPostResult(text = raw, characterCount = raw.length)
        }
    }

    // ── 30 идей контента ─────────────────────────────────────────────────────

    suspend fun generateIdeas(request: GenerateIdeasRequest): IdeasResult {
        val platform = when (request.platform) {
            "instagram" -> "Instagram Reels и посты"
            "tiktok"    -> "TikTok видео"
            "vk"        -> "ВКонтакте клипы и посты"
            "youtube"   -> "YouTube Shorts"
            else        -> "соцсети"
        }

        val prompt = """
            Придумай ровно ${request.count} идей для контента.
            
            Ниша: ${request.niche}
            Платформа: $platform
            Аудитория: русскоязычная
            
            Требования к каждой идее:
            - конкретная тема (не общая фраза вроде «советы»)
            - 1 предложение, до 100 символов
            - микс форматов: туториал, личная история, разбор ошибок, лайфхак, чек-лист, провокация, тренд
            
            JSON строго:
            {"ideas":["идея 1","идея 2",...,"идея ${request.count}"]}
        """.trimIndent()

        return try {
            val raw = chatJson(prompt, maxTokens = 2500, temperature = 0.92)
            val obj = json.parseToJsonElement(raw).jsonObject
            val ideas = obj["ideas"]!!.jsonArray.map { it.jsonPrimitive.content }
            IdeasResult(ideas = ideas)
        } catch (e: Exception) {
            // fallback: просим ещё раз попроще
            val raw = chatJson(
                "Напиши ${request.count} идей постов для ниши «${request.niche}» JSON: {\"ideas\":[...]}",
                maxTokens = 2500,
                temperature = 0.9,
            )
            try {
                json.decodeFromString<IdeasResult>(raw)
            } catch (_: Exception) {
                IdeasResult(ideas = listOf("Ошибка генерации: ${e.message}"))
            }
        }
    }

    // ── Перевод текста ────────────────────────────────────────────────────────

    suspend fun translateText(text: String, targetLanguage: String): String {
        val prompt = """
            Переведи на $targetLanguage. Сохрани стиль, эмодзи и структуру.
            Верни JSON: {"translation":"..."}
            
            Текст: $text
        """.trimIndent()
        return try {
            val raw = chatJson(prompt, maxTokens = 800, temperature = 0.3)
            json.parseToJsonElement(raw).jsonObject["translation"]!!.jsonPrimitive.content
        } catch (e: Exception) {
            chatPlain("Переведи на $targetLanguage (только перевод, без пояснений): $text")
        }
    }

    // ─── Приватные helpers ────────────────────────────────────────────────────

    /**
     * Чат с принудительным JSON-ответом.
     * GPT-5.2 поддерживает response_format: json_object — не возвращает лишний текст.
     */
    private suspend fun chatJson(
        userPrompt: String,
        maxTokens: Int = 1000,
        temperature: Double = 0.8,
    ): String {
        val raw = doRequest(
            messages = listOf(
                Msg("system", SYSTEM),
                Msg("user", userPrompt),
            ),
            maxTokens = maxTokens,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object"),
        )
        // Убираем возможные markdown-обёртки (на случай старой версии модели)
        return raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /** Обычный текстовый ответ (для fallback) */
    private suspend fun chatPlain(userPrompt: String, maxTokens: Int = 600): String =
        doRequest(
            messages = listOf(Msg("user", userPrompt)),
            maxTokens = maxTokens,
            temperature = 0.8,
        )

    private suspend fun doRequest(
        messages: List<Msg>,
        maxTokens: Int,
        temperature: Double,
        responseFormat: ResponseFormat? = null,
    ): String {
        val resp = client.post("$BASE/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $laozhangApiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatReq(
                model         = model,
                messages      = messages,
                maxTokens     = maxTokens,
                temperature   = temperature,
                responseFormat = responseFormat,
            ))
        }.body<ChatResp>()

        return resp.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("Пустой ответ от $model")
    }

    // ─── Вспомогательные данные ───────────────────────────────────────────────

    private fun platformSpec(platform: String) = when (platform) {
        "instagram" -> "150–300 символов, 5–10 хэштегов, эмодзи приветствуются"
        "tiktok"    -> "80–150 символов, 3–5 хэштегов, разговорный стиль"
        "vk"        -> "300–800 символов, хэштеги в конце, можно без эмодзи"
        "youtube"   -> "200–500 символов в описании, 3–5 хэштегов"
        else        -> "200–400 символов, универсальный формат"
    }

    private fun toneSpec(tone: TextTone) = when (tone) {
        TextTone.FRIENDLY     -> "дружелюбный, тёплый, как разговор с другом"
        TextTone.EXPERT       -> "экспертный, уверенный, с конкретными фактами и цифрами"
        TextTone.FUNNY        -> "с юмором, самоирония, лёгкий — заставь улыбнуться"
        TextTone.MOTIVATIONAL -> "мотивирующий, энергичный, призывает к действию"
    }

    fun close() = client.close()
}
