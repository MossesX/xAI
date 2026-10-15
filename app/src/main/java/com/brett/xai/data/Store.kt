package com.brett.xai.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}

/**
 * Simple file-backed store. Everything lives in app-private storage:
 *  settings.json, secrets.enc (Keystore-encrypted), knowledge.json, notes.json, chats/<id>.json
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val chatsDir = File(dir, "chats").apply { mkdirs() }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(load("settings.json", AppSettings.serializer()) ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _secrets = MutableStateFlow(loadSecrets())
    val secrets: StateFlow<Secrets> = _secrets.asStateFlow()

    private val _knowledge = MutableStateFlow(
        load("knowledge.json", ListSerializer(KnowledgeItem.serializer())) ?: defaultKnowledge()
    )
    val knowledge: StateFlow<List<KnowledgeItem>> = _knowledge.asStateFlow()

    private val _notes = MutableStateFlow(load("notes.json", ListSerializer(Note.serializer())) ?: emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _chats = MutableStateFlow(loadChats())
    val chats: StateFlow<List<Conversation>> = _chats.asStateFlow()

    // ------------------------------------------------------------------ settings
    private var settingsJob: Job? = null
    fun updateSettings(f: (AppSettings) -> AppSettings) {
        _settings.update(f)
        settingsJob?.cancel()
        settingsJob = io.launch {
            delay(300)
            save("settings.json", AppJson.encodeToString(AppSettings.serializer(), _settings.value))
        }
    }

    private var secretsJob: Job? = null
    fun updateSecrets(f: (Secrets) -> Secrets) {
        _secrets.update(f)
        secretsJob?.cancel()
        secretsJob = io.launch {
            delay(300)
            runCatching {
                val enc = SecretBox.encrypt(AppJson.encodeToString(Secrets.serializer(), _secrets.value))
                save("secrets.enc", enc)
            }
        }
    }

    private fun loadSecrets(): Secrets = runCatching {
        val f = File(dir, "secrets.enc")
        if (!f.exists()) Secrets() else AppJson.decodeFromString(Secrets.serializer(), SecretBox.decrypt(f.readText()))
    }.getOrDefault(Secrets())

    // ------------------------------------------------------------------ knowledge
    fun updateKnowledge(f: (List<KnowledgeItem>) -> List<KnowledgeItem>) {
        _knowledge.update(f)
        val snapshot = _knowledge.value
        io.launch { save("knowledge.json", AppJson.encodeToString(ListSerializer(KnowledgeItem.serializer()), snapshot)) }
    }

    fun learn(fact: String) {
        val t = fact.trim().removeSuffix(".")
        if (t.length < 4) return
        val exists = _knowledge.value.any { it.text.equals(t, ignoreCase = true) }
        if (!exists) updateKnowledge { it + KnowledgeItem(text = t, source = "learned") }
    }

    private fun defaultKnowledge() = listOf(
        KnowledgeItem(text = "Phone: Samsung Galaxy S26 Ultra (Android) — I work mostly from my phone"),
        KnowledgeItem(text = "Hosting: Hostinger VPS with root SSH access"),
        KnowledgeItem(text = "GitHub account: MossesX; Android apps are Kotlin + Jetpack Compose, built to APK by GitHub Actions"),
        KnowledgeItem(text = "I have a Claude subscription and use Claude Code"),
    )

    // ------------------------------------------------------------------ notes
    fun upsertNote(n: Note) {
        _notes.update { list -> (listOf(n) + list.filterNot { it.id == n.id }).sortedByDescending { it.ts } }
        saveNotes()
    }

    fun deleteNote(id: String) {
        _notes.update { list -> list.filterNot { it.id == id } }
        saveNotes()
    }

    private fun saveNotes() {
        val snapshot = _notes.value
        io.launch { save("notes.json", AppJson.encodeToString(ListSerializer(Note.serializer()), snapshot)) }
    }

    // ------------------------------------------------------------------ chats
    fun chat(id: String?): Conversation? = id?.let { i -> _chats.value.firstOrNull { it.id == i } }

    /** Update in memory; pass persist=false while streaming to avoid a disk write per token. */
    fun upsertChat(c: Conversation, persist: Boolean = true) {
        _chats.update { list -> (listOf(c) + list.filterNot { it.id == c.id }).sortedByDescending { it.updated } }
        if (persist) io.launch {
            File(chatsDir, "${c.id}.json").writeText(AppJson.encodeToString(Conversation.serializer(), c))
        }
    }

    fun deleteChat(id: String) {
        _chats.update { list -> list.filterNot { it.id == id } }
        io.launch { File(chatsDir, "$id.json").delete() }
    }

    fun deleteAllChats() {
        _chats.value = emptyList()
        io.launch { chatsDir.listFiles()?.forEach { it.delete() } }
    }

    private fun loadChats(): List<Conversation> =
        (chatsDir.listFiles() ?: emptyArray())
            .mapNotNull { f -> runCatching { AppJson.decodeFromString(Conversation.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.updated }

    // ------------------------------------------------------------------ io helpers
    private fun <T> load(name: String, ser: kotlinx.serialization.KSerializer<T>): T? = runCatching {
        val f = File(dir, name)
        if (f.exists()) AppJson.decodeFromString(ser, f.readText()) else null
    }.getOrNull()

    private fun save(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}
