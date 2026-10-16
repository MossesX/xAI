package com.brett.xai.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable data class DocLink(val title: String, val url: String)

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val level: String = "",
    val summary: String = "",
    val videos: List<String> = emptyList(),   // YouTube search queries (live results → thousands of videos)
    val docs: List<DocLink> = emptyList(),
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val icon: String = "",
    val color: String = "#5EEAD4",
    val summary: String = "",
    val topics: List<Topic> = emptyList(),
)

@Serializable data class Channel(val name: String, val handle: String, val about: String = "", val tag: String = "")
@Serializable data class Course(val title: String, val provider: String = "", val url: String, val about: String = "")

@Serializable
data class Curriculum(
    val version: Int = 1,
    val tracks: List<Track> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val courses: List<Course> = emptyList(),
)

@Serializable
data class SavedItem(
    val id: String = newId(),
    val title: String,
    val url: String,
    val kind: String = "page",   // "video" | "page"
    val thumb: String = "",
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
data class LearnState(
    val completed: Set<String> = emptySet(),
    val saved: List<SavedItem> = emptyList(),
    val history: List<SavedItem> = emptyList(),
)

object Links {
    fun ytSearch(q: String) = "https://m.youtube.com/results?search_query=" + android.net.Uri.encode(q)
    fun ytWatch(id: String) = "https://m.youtube.com/watch?v=$id"
    fun ytChannel(handle: String) = "https://m.youtube.com/@$handle/videos"
    fun webSearch(q: String) = "https://duckduckgo.com/?q=" + android.net.Uri.encode(q)
    fun isVideo(url: String) = url.contains("youtube.com/watch") || url.contains("youtu.be/") || url.contains("youtube.com/shorts/")
}

class LearnRepo(private val context: Context) {
    private val dir = context.filesDir
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheFile = File(dir, "curriculum.json")
    private val stateFile = File(dir, "learn_state.json")

    private val _curriculum = MutableStateFlow(loadCurriculum())
    val curriculum: StateFlow<Curriculum> = _curriculum.asStateFlow()

    private val _state = MutableStateFlow(
        runCatching { AppJson.decodeFromString(LearnState.serializer(), stateFile.readText()) }.getOrDefault(LearnState())
    )
    val state: StateFlow<LearnState> = _state.asStateFlow()

    private fun bundled(): Curriculum = runCatching {
        context.assets.open("curriculum.json").bufferedReader().use { AppJson.decodeFromString(Curriculum.serializer(), it.readText()) }
    }.getOrDefault(Curriculum())

    private fun loadCurriculum(): Curriculum {
        val b = bundled()
        val cached = runCatching { AppJson.decodeFromString(Curriculum.serializer(), cacheFile.readText()) }.getOrNull()
        return if (cached != null && cached.version >= b.version && cached.tracks.isNotEmpty()) cached else b
    }

    fun topic(id: String): Pair<Track, Topic>? {
        for (t in _curriculum.value.tracks) t.topics.firstOrNull { it.id == id }?.let { return t to it }
        return null
    }

    fun track(id: String): Track? = _curriculum.value.tracks.firstOrNull { it.id == id }

    fun search(q: String): List<Pair<Track, Topic>> {
        val terms = q.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return _curriculum.value.tracks.flatMap { tr -> tr.topics.map { tr to it } }.filter { (tr, t) ->
            val hay = (t.title + " " + t.summary + " " + tr.title + " " + t.videos.joinToString(" ")).lowercase()
            terms.all { hay.contains(it) }
        }
    }

    fun isDone(id: String) = id in _state.value.completed

    fun toggleDone(id: String) = mutate { s -> s.copy(completed = if (id in s.completed) s.completed - id else s.completed + id) }

    fun isSaved(url: String) = _state.value.saved.any { it.url == url }

    fun toggleSaved(item: SavedItem) = mutate { s ->
        if (s.saved.any { it.url == item.url }) s.copy(saved = s.saved.filterNot { it.url == item.url })
        else s.copy(saved = listOf(item) + s.saved)
    }

    fun addHistory(item: SavedItem) = mutate { s ->
        if (item.url.isBlank() || item.url.startsWith("about:")) s
        else s.copy(history = (listOf(item) + s.history.filterNot { it.url == item.url }).take(200))
    }

    fun clearHistory() = mutate { it.copy(history = emptyList()) }

    private fun mutate(f: (LearnState) -> LearnState) {
        _state.update(f)
        val snap = _state.value
        io.launch { runCatching { stateFile.writeText(AppJson.encodeToString(LearnState.serializer(), snap)) } }
    }

    /** Pull a newer library (e.g. from the GitHub repo) so new topics arrive without an app update. */
    suspend fun updateFrom(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body!!.string()
            }
            val c = AppJson.decodeFromString(Curriculum.serializer(), body)
            if (c.tracks.isEmpty()) error("library was empty")
            cacheFile.writeText(body)
            _curriculum.value = c
            "Library updated: ${c.tracks.size} tracks, ${c.tracks.sumOf { it.topics.size }} topics"
        }.getOrElse { "Update failed: ${it.message}" }
    }
}
