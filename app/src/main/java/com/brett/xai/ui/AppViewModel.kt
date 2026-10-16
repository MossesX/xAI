package com.brett.xai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brett.xai.ai.AssistantEngine
import com.brett.xai.ai.BackendException
import com.brett.xai.ai.Ev
import com.brett.xai.ai.Prompts
import com.brett.xai.data.BackendMode
import com.brett.xai.data.ChatMessage
import com.brett.xai.data.Conversation
import com.brett.xai.data.Note
import com.brett.xai.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUi(
    val currentId: String? = null,
    val busy: Boolean = false,
    val status: String = "",
    val streamingId: String? = null,
    val toast: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val engine = AssistantEngine(store)
    val learn = com.brett.xai.data.LearnRepo(app)

    private val _ui = MutableStateFlow(ChatUi(currentId = store.chats.value.firstOrNull()?.id))
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    /** Text shared into the app from another app, pre-filled into the input box. */
    val pendingInput = MutableStateFlow("")

    private var job: Job? = null

    fun toast(msg: String?) = _ui.update { it.copy(toast = msg) }

    fun openChat(id: String?) { if (!_ui.value.busy) _ui.update { it.copy(currentId = id) } }
    fun newChat() { if (!_ui.value.busy) _ui.update { it.copy(currentId = null) } }

    fun deleteChat(id: String) {
        store.deleteChat(id)
        if (_ui.value.currentId == id) _ui.update { it.copy(currentId = store.chats.value.firstOrNull()?.id) }
    }

    fun send(text: String, force: BackendMode? = null, research: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || _ui.value.busy) return
        val existing = store.chat(_ui.value.currentId)
        val base = existing ?: Conversation(title = t.lineSequence().first().take(48))
        val conv = base.copy(messages = base.messages + ChatMessage(role = "user", text = t, research = research), updated = System.currentTimeMillis())
        store.upsertChat(conv)
        _ui.update { it.copy(currentId = conv.id) }
        run(conv, force)
    }

    /** Re-answer the last user message (optionally forcing a backend). */
    fun regenerate(force: BackendMode? = null) {
        if (_ui.value.busy) return
        val c = store.chat(_ui.value.currentId) ?: return
        val idx = c.messages.indexOfLast { it.role == "user" }
        if (idx < 0) return
        val trimmed = c.copy(messages = c.messages.take(idx + 1))
        store.upsertChat(trimmed)
        run(trimmed, force)
    }

    fun stop() {
        job?.cancel()
    }

    private fun run(convWithUser: Conversation, force: BackendMode?) {
        val reply = ChatMessage(role = "assistant", text = "")
        var conv = convWithUser.copy(messages = convWithUser.messages + reply)
        store.upsertChat(conv, persist = false)
        _ui.update { it.copy(busy = true, status = "Starting…", streamingId = reply.id) }

        fun patch(persist: Boolean = false, f: (ChatMessage) -> ChatMessage) {
            conv = conv.copy(
                messages = conv.messages.map { if (it.id == reply.id) f(it) else it },
                updated = System.currentTimeMillis(),
            )
            store.upsertChat(conv, persist)
        }

        job = viewModelScope.launch {
            val sb = StringBuilder()
            var lastPush = 0L
            try {
                engine.respond(convWithUser, force).collect { ev ->
                    when (ev) {
                        is Ev.Text -> {
                            sb.append(ev.delta)
                            val now = System.currentTimeMillis()
                            if (now - lastPush > 50) {
                                lastPush = now
                                val shown = sb.toString()
                                patch { it.copy(text = shown) }
                            }
                            if (_ui.value.status.isNotEmpty()) _ui.update { it.copy(status = "") }
                        }
                        is Ev.Status -> _ui.update { it.copy(status = ev.text) }
                        is Ev.Backend -> patch { it.copy(via = ev.label) }
                        is Ev.Fallback -> {
                            _ui.update { it.copy(status = "Falling back to API…") }
                            patch { it.copy(note = "Claude Code unavailable (${ev.reason}) — answered via API") }
                        }
                        is Ev.SessionId -> conv = conv.copy(claudeSessionId = ev.id)
                    }
                }
                val full = sb.toString()
                val s = store.settings.value
                if (s.autoLearn) Prompts.extractFacts(full).forEach { store.learn(it) }
                patch(persist = true) { it.copy(text = Prompts.clean(full)) }
                val learned = Prompts.extractFacts(full).size
                if (s.autoLearn && learned > 0) toast("Learned $learned new fact${if (learned > 1) "s" else ""} about your setup")
            } catch (e: CancellationException) {
                val partial = Prompts.clean(sb.toString())
                patch(persist = true) { it.copy(text = if (partial.isBlank()) "_Stopped._" else "$partial\n\n_(stopped)_") }
            } catch (e: Exception) {
                val msg = (e as? BackendException)?.message ?: "${e::class.simpleName}: ${e.message}"
                val partial = Prompts.clean(sb.toString())
                patch(persist = true) {
                    it.copy(text = if (partial.isBlank()) msg else "$partial\n\n---\n⚠ $msg", error = partial.isBlank())
                }
            } finally {
                _ui.update { it.copy(busy = false, status = "", streamingId = null) }
            }
        }
    }

    /** From the built-in browser: start a new chat about the page the user is looking at. */
    fun askAboutPage(title: String, url: String, pageText: String) {
        if (_ui.value.busy) { toast("Wait for the current reply to finish"); return }
        _ui.update { it.copy(currentId = null) }
        val excerpt = pageText.trim().take(10000)
        val prompt = buildString {
            append("I'm reading this page: ").append(title.ifBlank { url }).append("\n").append(url).append("\n\n")
            append("Explain what it covers, the key steps or commands, and how I'd apply it to my setup. Point out anything outdated or risky.")
            if (excerpt.isNotBlank()) append("\n\n--- Page text (excerpt) ---\n").append(excerpt)
        }
        send(prompt)
    }

    fun saveToNotes(text: String, title: String? = null) {
        val t = title ?: text.lineSequence().map { it.trim().trimStart('#', ' ') }.firstOrNull { it.isNotBlank() }?.take(60) ?: "Note"
        store.upsertNote(Note(title = t, body = text))
        toast("Saved to Notes")
    }

    fun rememberFact(text: String) {
        store.learn(text)
        toast("Added to Knowledge")
    }

    fun conversationMarkdown(c: Conversation): String = buildString {
        append("# ").append(c.title).append("\n\n")
        c.messages.forEach { m ->
            append(if (m.role == "user") "## You\n\n" else "## xAI${if (m.via.isNotBlank()) " (${m.via})" else ""}\n\n")
            append(m.text).append("\n\n")
        }
    }
}
