package com.brett.xai.data

import com.brett.xai.ai.Prompts
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class BackendMode { AUTO, SUBSCRIPTION, API }

@Serializable
data class ChatMessage(
    val id: String = newId(),
    val role: String,              // "user" | "assistant"
    val text: String,
    val via: String = "",          // which backend produced it
    val note: String = "",         // e.g. "Claude Code unavailable — answered via API"
    val error: Boolean = false,
    val research: Boolean = false, // user asked for live web research on this message
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String = newId(),
    val title: String = "New chat",
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    /** Claude Code session id on the server, so follow-ups use --resume. */
    val claudeSessionId: String? = null,
)

@Serializable
data class KnowledgeItem(
    val id: String = newId(),
    val text: String,
    val source: String = "you",    // "you" | "learned"
    val enabled: Boolean = true,
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
data class Note(
    val id: String = newId(),
    val title: String,
    val body: String,
    val ts: Long = System.currentTimeMillis(),
)

/** Everything configurable in Settings except secrets. */
@Serializable
data class AppSettings(
    // ---- routing
    val mode: BackendMode = BackendMode.AUTO,
    val fallbackToApi: Boolean = true,

    // ---- Claude Code subscription (runs on your server over SSH)
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "root",
    val sshUseKey: Boolean = false,
    val sshTimeoutSec: Int = 20,
    val pinnedHostKey: String = "",
    val remoteWorkDir: String = "~",
    val claudePath: String = "claude",
    val ccModel: String = "",                 // blank = Claude Code default
    val ccPermissionMode: String = "default", // default | plan | acceptEdits | bypassPermissions
    val ccAllowedTools: String = "",
    val ccExtraArgs: String = "",
    val ccResume: Boolean = true,
    val ccPartial: Boolean = true,

    // ---- Anthropic API fallback
    val apiModel: String = "claude-sonnet-5-5",
    val apiBaseUrl: String = "https://api.anthropic.com",
    val maxTokens: Int = 8192,
    val temperature: Float = 1.0f,
    val webSearch: Boolean = true,
    val historyLimit: Int = 40,

    // ---- research (live web search + reading pages)
    val webResearch: Boolean = true,
    val fetchPages: Boolean = true,
    val researchDepth: Int = 6,
    val preferredSources: String = Prompts.DEFAULT_SOURCES,

    // ---- learning suite
    val openLinksInApp: Boolean = true,
    val libraryUrl: String = "https://raw.githubusercontent.com/MossesX/xAI/main/app/src/main/assets/curriculum.json",

    // ---- assistant brain
    val systemPrompt: String = Prompts.DEFAULT_SYSTEM,
    val useKnowledge: Boolean = true,
    val autoLearn: Boolean = true,

    // ---- appearance
    val theme: String = "dark",               // system | light | dark
    val showBackendBadge: Boolean = true,
    val fontScale: Float = 1.0f,
)

/** Stored encrypted with an Android Keystore key, never backed up. */
@Serializable
data class Secrets(
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val ccOauthToken: String = "",
    val apiKey: String = "",
    val youtubeApiKey: String = "",
)
