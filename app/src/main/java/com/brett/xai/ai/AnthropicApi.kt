package com.brett.xai.ai

import com.brett.xai.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.brett.xai.data.AppJson
import java.util.concurrent.TimeUnit

/** Direct Anthropic Messages API with SSE streaming. Used as the fallback (or primary in API mode). */
class AnthropicApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun request(s: AppSettings, apiKey: String, body: JsonObject, fetch: Boolean = false): Request =
        Request.Builder()
            .apply { if (fetch) header("anthropic-beta", WEB_FETCH_BETA) }
            .url(s.apiBaseUrl.trimEnd('/') + "/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

    /** history: (role, text) pairs, oldest first, must end with a user turn. */
    fun stream(s: AppSettings, apiKey: String, system: String, history: List<Pair<String, String>>, allowFetch: Boolean = true): Flow<Ev> = flow {
        val useFetch = allowFetch && s.webSearch && s.webResearch && s.fetchPages
        if (apiKey.isBlank()) throw BackendException("No Anthropic API key set (Settings → Anthropic API).", retryable = false)
        val body = buildJsonObject {
            put("model", s.apiModel.trim())
            put("max_tokens", s.maxTokens)
            put("temperature", s.temperature.toDouble())
            put("stream", true)
            put("system", system)
            putJsonArray("messages") {
                normalize(history).forEach { (role, text) ->
                    addJsonObject { put("role", role); put("content", text) }
                }
            }
            if (s.webSearch) putJsonArray("tools") {
                addJsonObject {
                    put("type", "web_search_20250305")
                    put("name", "web_search")
                    put("max_uses", s.researchDepth.coerceIn(1, 20))
                }
                if (useFetch) addJsonObject {
                    put("type", WEB_FETCH_TOOL)
                    put("name", "web_fetch")
                    put("max_uses", s.researchDepth.coerceIn(1, 20))
                }
            }
        }
        emit(Ev.Backend("API · ${s.apiModel}"))
        val call = client.newCall(request(s, apiKey, body, useFetch))
        val resp = call.execute()
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                // If this account/model doesn't support the page-reading tool, retry once with search only.
                if (useFetch && resp.code == 400 && (err.contains("web_fetch") || err.contains("beta") || err.contains("tool"))) {
                    resp.close()
                    emit(Ev.Status("Retrying without page fetch…"))
                    emitAll(stream(s, apiKey, system, history, allowFetch = false))
                    return@flow
                }
                val msg = runCatching {
                    AppJson.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: err.take(300)
                throw BackendException("API ${resp.code}: $msg", retryable = resp.code >= 500 || resp.code == 429)
            }
            val src = resp.body!!.source()
            var blocksWithText = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val ev = runCatching { AppJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                when (ev["type"]?.jsonPrimitive?.contentOrNull) {
                    "content_block_start" -> {
                        val block = ev["content_block"]?.jsonObject
                        when (block?.get("type")?.jsonPrimitive?.contentOrNull) {
                            "server_tool_use" -> {
                                val name = block?.get("name")?.jsonPrimitive?.contentOrNull
                                emit(Ev.Status(if (name == "web_fetch") "Reading a page…" else "Searching the web…"))
                            }
                            "web_search_tool_result" -> emit(Ev.Status("Reading search results…"))
                            "web_fetch_tool_result" -> emit(Ev.Status("Reading page…"))
                            "text" -> blocksWithText++
                        }
                    }
                    "content_block_delta" -> {
                        val d = ev["delta"]?.jsonObject
                        if (d?.get("type")?.jsonPrimitive?.contentOrNull == "text_delta") {
                            emit(Ev.Text(d?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()))
                        }
                    }
                    "message_delta" -> {
                        val stop = ev["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                        if (stop == "max_tokens") emit(Ev.Text("\n\n_(Reply cut off at the max-tokens limit — raise it in Settings.)_"))
                    }
                    "error" -> {
                        val m = ev["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: data
                        throw BackendException("API error: $m")
                    }
                }
            }
        } finally {
            resp.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun test(s: AppSettings, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "✗ No API key set"
        val body = buildJsonObject {
            put("model", s.apiModel.trim())
            put("max_tokens", 20)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "Reply with just: pong") } }
        }
        runCatching {
            client.newCall(request(s, apiKey, body)).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (r.isSuccessful) "✓ API OK — model ${s.apiModel} replied"
                else "✗ HTTP ${r.code}: " + (runCatching {
                    AppJson.parseToJsonElement(txt).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: txt.take(200))
            }
        }.getOrElse { "✗ ${it.message}" }
    }

    companion object {
        const val WEB_FETCH_TOOL = "web_fetch_20250910"
        const val WEB_FETCH_BETA = "web-fetch-2025-09-10"

        /** API requires alternating roles starting with user. Merge/clean as needed. */
        fun normalize(history: List<Pair<String, String>>): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            for ((role, text) in history) {
                if (text.isBlank()) continue
                if (out.isNotEmpty() && out.last().first == role) {
                    out[out.size - 1] = role to (out.last().second + "\n\n" + text)
                } else out += role to text
            }
            while (out.isNotEmpty() && out.first().first != "user") out.removeAt(0)
            return out
        }
    }
}
