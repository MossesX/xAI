package com.brett.xai.ai

import android.text.Html
import com.brett.xai.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Video(val id: String, val title: String, val channel: String, val published: String, val thumb: String)

data class VideoPage(val videos: List<Video>, val next: String?)

/** YouTube Data API v3 search (optional — needs a free API key from Google Cloud). */
object YouTube {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun search(key: String, query: String, pageToken: String? = null, max: Int = 20): VideoPage = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video")
            .addQueryParameter("maxResults", max.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("relevanceLanguage", "en")
            .addQueryParameter("safeSearch", "moderate")
            .addQueryParameter("key", key)
            .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val root = AppJson.parseToJsonElement(body).jsonObject
            if (!r.isSuccessful) {
                val msg = runCatching { root["error"]!!.jsonObject["message"]!!.jsonPrimitive.contentOrNull }.getOrNull()
                throw IllegalStateException("YouTube API ${r.code}: ${msg ?: body.take(200)}")
            }
            val items = root["items"]?.jsonArray.orEmpty().mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    val id = o["id"]!!.jsonObject["videoId"]!!.jsonPrimitive.content
                    val sn = o["snippet"]!!.jsonObject
                    val thumbs = sn["thumbnails"]?.jsonObject
                    val thumb = (thumbs?.get("high") ?: thumbs?.get("medium") ?: thumbs?.get("default"))
                        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                    Video(
                        id = id,
                        title = decode(sn["title"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        channel = decode(sn["channelTitle"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        published = sn["publishedAt"]?.jsonPrimitive?.contentOrNull.orEmpty().take(10),
                        thumb = thumb,
                    )
                }.getOrNull()
            }
            VideoPage(items, root["nextPageToken"]?.jsonPrimitive?.contentOrNull)
        }
    }

    private fun decode(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
}
