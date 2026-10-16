package com.brett.xai.ai

import com.brett.xai.data.BackendMode
import com.brett.xai.data.Conversation
import com.brett.xai.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Routes a message to Claude Code (subscription, over SSH) first and falls back to the
 * Anthropic API when the subscription path is unavailable — all driven by Settings.
 */
class AssistantEngine(private val store: Store) {

    val api = AnthropicApi()
    val ssh = ClaudeCodeSsh { fp -> store.updateSettings { it.copy(pinnedHostKey = fp) } }

    /**
     * @param conv conversation INCLUDING the new user message as its last message.
     * @param force overrides the configured mode for this one reply (e.g. "Regenerate via API").
     */
    fun respond(conv: Conversation, force: BackendMode? = null): Flow<Ev> = flow {
        val s = store.settings.value
        val sec = store.secrets.value
        val mode = force ?: s.mode
        val subReady = s.sshHost.isNotBlank()
        val apiReady = sec.apiKey.isNotBlank()

        when (mode) {
            BackendMode.API -> emitAll(viaApi(conv))
            BackendMode.SUBSCRIPTION -> emitAll(viaClaudeCode(conv))
            BackendMode.AUTO -> {
                if (!subReady && !apiReady) throw BackendException(
                    "Nothing is configured yet. Open Settings and add your server (Claude Code subscription) and/or an Anthropic API key.",
                    retryable = false,
                )
                if (!subReady) {
                    emit(Ev.Fallback("Claude Code server not configured"))
                    emitAll(viaApi(conv))
                    return@flow
                }
                var gotText = false
                try {
                    viaClaudeCode(conv).collect { ev ->
                        if (ev is Ev.Text && ev.delta.isNotBlank()) gotText = true
                        emit(ev)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Only fall back if nothing was shown yet; a half answer + an API answer would be confusing.
                    if (gotText || !apiReady || !s.fallbackToApi) throw e
                    emit(Ev.Fallback(e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "Claude Code unavailable"))
                    emitAll(viaApi(conv))
                }
            }
        }
    }

    private fun viaApi(conv: Conversation): Flow<Ev> {
        val s = store.settings.value
        val system = Prompts.build(s, store.knowledge.value, "the Anthropic API (${s.apiModel}) inside the xAI Android app")
        val history = conv.messages
            .filter { !it.error && it.text.isNotBlank() }
            .takeLast(s.historyLimit.coerceAtLeast(1))
            .map { it.role to (Prompts.clean(it.text) + if (it.research && it.role == "user") Prompts.RESEARCH_DIRECTIVE else "") }
        return api.stream(s, store.secrets.value.apiKey, system, history)
    }

    private fun viaClaudeCode(conv: Conversation): Flow<Ev> {
        val s = store.settings.value
        val sec = store.secrets.value
        val system = Prompts.build(
            s, store.knowledge.value,
            "Claude Code running on the user's server (${s.sshHost}), in headless mode, relayed to the xAI Android app. " +
                "You can use your tools (read files, run commands, search the web) on that server within the configured permission mode.",
        )
        val lastMsg = conv.messages.last()
        val last = lastMsg.text + if (lastMsg.research) Prompts.RESEARCH_DIRECTIVE else ""
        val resume = conv.claudeSessionId.takeIf { s.ccResume && !it.isNullOrBlank() }
        val prior = conv.messages.dropLast(1).filter { !it.error && it.text.isNotBlank() }
        // If we can't resume a server session but there's history (e.g. earlier turns came from the API),
        // replay a compact transcript so Claude Code has the context.
        val prompt = if (resume == null && prior.isNotEmpty()) buildString {
            append("Conversation so far (for context):\n\n")
            prior.takeLast(s.historyLimit).forEach { m ->
                append(if (m.role == "user") "USER: " else "ASSISTANT: ")
                append(Prompts.clean(m.text).take(4000)).append("\n\n")
            }
            append("---\nNew message from the user:\n").append(last)
        } else last
        return ssh.run(s, sec, prompt, system, resume)
    }
}
