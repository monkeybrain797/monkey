package com.claudevoice.api

import com.claudevoice.model.VoiceSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClaudeApiClient {

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()
    private val API_URL = "https://api.anthropic.com/v1/messages"
    private val MODEL = "claude-sonnet-4-6"

    private val VOICE_PARSE_SYSTEM = """
        You are a voice settings parser. Given a natural-language description of how a voice should sound,
        respond ONLY with a JSON object containing exactly these fields:
        - "speechRate": float between 0.5 and 2.0 (1.0 = normal speed)
        - "pitch": float between 0.5 and 2.0 (1.0 = normal pitch)
        - "volume": float between 0.1 and 1.0 (1.0 = max volume)
        - "voiceLocale": BCP-47 locale tag such as "en-US", "en-GB", "es-ES"
        - "voiceGender": one of "male", "female", or "default"
        Output only valid JSON with no explanation or markdown.
    """.trimIndent()

    suspend fun parseVoiceGuidelines(apiKey: String, guidelines: String): Result<VoiceSettings> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildRequestBody(MODEL, VOICE_PARSE_SYSTEM, guidelines, maxTokens = 256)
                val responseJson = executeBlocking(apiKey, body)
                val text = extractTextContent(responseJson)
                gson.fromJson(text, VoiceSettings::class.java)
            }
        }

    suspend fun sendMessage(
        apiKey: String,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildRequestBody(MODEL, null, userMessage, maxTokens = 1024, stream = true)
            val request = Request.Builder()
                .url(API_URL)
                .headers(anthropicHeaders(apiKey))
                .post(body.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body?.source()?.let { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ")
                        if (data == "[DONE]") break
                        runCatching {
                            val json = gson.fromJson(data, JsonObject::class.java)
                            if (json.get("type")?.asString == "content_block_delta") {
                                json.getAsJsonObject("delta")
                                    ?.get("text")?.asString
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(onChunk)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildRequestBody(
        model: String,
        system: String?,
        userMessage: String,
        maxTokens: Int,
        stream: Boolean = false
    ): JsonObject = JsonObject().apply {
        addProperty("model", model)
        addProperty("max_tokens", maxTokens)
        if (stream) addProperty("stream", true)
        system?.let { add("system", gson.toJsonTree(it)) }
        add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to userMessage))))
    }

    private fun executeBlocking(apiKey: String, body: JsonObject): String {
        val request = Request.Builder()
            .url(API_URL)
            .headers(anthropicHeaders(apiKey))
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun extractTextContent(responseJson: String): String =
        gson.fromJson(responseJson, JsonObject::class.java)
            .getAsJsonArray("content")
            .get(0).asJsonObject
            .get("text").asString

    private fun anthropicHeaders(apiKey: String) = okhttp3.Headers.Builder()
        .add("x-api-key", apiKey)
        .add("anthropic-version", "2023-06-01")
        .add("content-type", "application/json")
        .build()
}
