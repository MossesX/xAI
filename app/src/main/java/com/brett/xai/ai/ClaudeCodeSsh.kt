package com.brett.xai.ai

import com.brett.xai.data.AppJson
import com.brett.xai.data.AppSettings
import com.brett.xai.data.Secrets
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties
import kotlin.concurrent.thread

/**
 * "Subscription" backend: runs the real Claude Code CLI on your own server over SSH, in headless
 * print mode. Claude Code is logged in to your Claude plan on that server, so usage counts against
 * your subscription — the phone never handles subscription credentials itself.
 *
 *   phone ──SSH──▶ VPS ──▶ claude -p --output-format stream-json ──▶ streamed back here
 */
class ClaudeCodeSsh(private val onHostKeyPinned: (String) -> Unit) {

    private fun connect(s: AppSettings, sec: Secrets): Session {
        if (s.sshHost.isBlank()) throw BackendException("No server set (Settings → Claude Code subscription).", retryable = false)
        val jsch = JSch()
        if (s.sshUseKey) {
            if (sec.sshPrivateKey.isBlank()) throw BackendException("SSH key auth selected but no private key pasted.", retryable = false)
            val pass = sec.sshKeyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray()
            jsch.addIdentity("xai-key", sec.sshPrivateKey.trim().toByteArray() + '\n'.code.toByte(), null, pass)
        }
        val session = jsch.getSession(s.sshUser.ifBlank { "root" }, s.sshHost.trim(), s.sshPort)
        if (!s.sshUseKey) session.setPassword(sec.sshPassword)
        val cfg = Properties()
        cfg["StrictHostKeyChecking"] = "no" // we do our own trust-on-first-use pinning below
        cfg["PreferredAuthentications"] = if (s.sshUseKey) "publickey" else "password,keyboard-interactive"
        session.setConfig(cfg)
        session.setServerAliveInterval(15_000)
        try {
            session.connect(s.sshTimeoutSec * 1000)
        } catch (e: Exception) {
            throw BackendException("SSH connect to ${s.sshHost}:${s.sshPort} failed: ${e.message}")
        }
        val fp = session.hostKey.getFingerPrint(jsch)
        if (s.pinnedHostKey.isBlank()) onHostKeyPinned(fp)
        else if (s.pinnedHostKey != fp) {
            session.disconnect()
            throw BackendException(
                "Server host key changed! Expected ${s.pinnedHostKey}, got $fp. If you rebuilt the server, clear the pinned key in Settings.",
                retryable = false,
            )
        }
        return session
    }

    private fun cdExpr(dir: String): String {
        val d = dir.trim()
        return when {
            d.isEmpty() || d == "~" -> "cd ~"
            d.startsWith("~/") -> "cd ~/" + shellQuote(d.removePrefix("~/"))
            else -> "cd " + shellQuote(d)
        }
    }

    fun buildCommand(s: AppSettings, sec: Secrets, systemAppend: String, resumeId: String?): String {
        val p = mutableListOf(s.claudePath.ifBlank { "claude" }, "-p", "--output-format", "stream-json", "--verbose")
        if (s.ccPartial) p += "--include-partial-messages"
        if (s.ccModel.isNotBlank()) p += listOf("--model", shellQuote(s.ccModel.trim()))
        if (s.ccPermissionMode.isNotBlank()) p += listOf("--permission-mode", shellQuote(s.ccPermissionMode))
        val tools = buildList {
            if (s.ccAllowedTools.isNotBlank()) add(s.ccAllowedTools.trim())
            if (s.webResearch) { add("WebSearch"); if (s.fetchPages) add("WebFetch") }
        }.joinToString(" ")
        if (tools.isNotBlank()) p += listOf("--allowedTools", shellQuote(tools))
        if (!resumeId.isNullOrBlank()) p += listOf("--resume", shellQuote(resumeId))
        p += listOf("--append-system-prompt", shellQuote(systemAppend))
        if (s.ccExtraArgs.isNotBlank()) p += s.ccExtraArgs.trim()
        val token = if (sec.ccOauthToken.isNotBlank()) "export CLAUDE_CODE_OAUTH_TOKEN=${shellQuote(sec.ccOauthToken.trim())}; " else ""
        val inner = "${cdExpr(s.remoteWorkDir)} && ${token}exec " + p.joinToString(" ")
        // login shell so PATH from ~/.profile / nvm / ~/.local/bin is loaded
        return "bash -lc " + shellQuote(inner)
    }

    fun run(s: AppSettings, sec: Secrets, prompt: String, systemAppend: String, resumeId: String?): Flow<Ev> = flow {
        emit(Ev.Status("Connecting to ${s.sshHost}…"))
        val session = connect(s, sec)
        val ch = session.openChannel("exec") as ChannelExec
        try {
            ch.setCommand(buildCommand(s, sec, systemAppend, resumeId))
            val stdout = ch.inputStream
            val stderrStream = ch.errStream
            val stdin = ch.outputStream
            ch.connect(s.sshTimeoutSec * 1000)
            emit(Ev.Backend("Claude Code · ${s.sshHost}"))
            emit(Ev.Status("Claude Code is thinking…"))

            val stderr = StringBuilder()
            val errThread = thread(isDaemon = true) {
                runCatching {
                    BufferedReader(InputStreamReader(stderrStream)).forEachLine { synchronized(stderr) { if (stderr.length < 4000) stderr.appendLine(it) } }
                }
            }
            stdin.write(prompt.toByteArray(Charsets.UTF_8))
            stdin.flush()
            stdin.close()

            var sawPartial = false
            var emittedText = false
            var resultError: String? = null
            val raw = StringBuilder()
            val reader = BufferedReader(InputStreamReader(stdout, Charsets.UTF_8))
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                val obj = runCatching { AppJson.parseToJsonElement(line).jsonObject }.getOrNull()
                if (obj == null) { if (raw.length < 2000) raw.appendLine(line); continue }
                when (obj.str("type")) {
                    "system" -> {
                        obj.str("session_id")?.let { emit(Ev.SessionId(it)) }
                        obj.str("model")?.let { emit(Ev.Backend("Claude Code · ${s.sshHost} · $it")) }
                    }
                    "stream_event" -> {
                        val e = obj["event"]?.jsonObject ?: continue
                        when (e.str("type")) {
                            "message_start" -> if (emittedText) emit(Ev.Text("\n\n"))
                            "content_block_start" -> {
                                val b = e["content_block"]?.jsonObject
                                if (b?.str("type") == "tool_use") emit(Ev.Status("Using ${b?.str("name")}…"))
                            }
                            "content_block_delta" -> {
                                val d = e["delta"]?.jsonObject
                                if (d?.str("type") == "text_delta") {
                                    sawPartial = true
                                    val t = d?.str("text").orEmpty()
                                    if (t.isNotEmpty()) { emittedText = true; emit(Ev.Text(t)) }
                                }
                            }
                        }
                    }
                    "assistant" -> {
                        val content = obj["message"]?.jsonObject?.get("content")?.jsonArray ?: continue
                        for (el in content) {
                            val b = el.jsonObject
                            when (b.str("type")) {
                                "text" -> if (!sawPartial) {
                                    val t = b.str("text").orEmpty()
                                    if (t.isNotBlank()) { if (emittedText) emit(Ev.Text("\n\n")); emittedText = true; emit(Ev.Text(t)) }
                                }
                                "tool_use" -> emit(Ev.Status(toolSummary(b)))
                            }
                        }
                    }
                    "user" -> emit(Ev.Status("Claude Code is working…"))
                    "result" -> {
                        obj.str("session_id")?.let { emit(Ev.SessionId(it)) }
                        val res = obj.str("result").orEmpty()
                        val isErr = obj["is_error"]?.jsonPrimitive?.booleanOrNull == true
                        if (isErr || (!emittedText && looksLikeAuthOrLimit(res))) resultError = res.ifBlank { obj.str("subtype") ?: "error" }
                        else if (!emittedText && res.isNotBlank()) { emittedText = true; emit(Ev.Text(res)) }
                    }
                }
            }
            // wait for exit status
            var waited = 0
            while (!ch.isClosed && waited < 3000 && currentCoroutineContext().isActive) { Thread.sleep(50); waited += 50 }
            errThread.join(500)
            val exit = ch.exitStatus
            val err = synchronized(stderr) { stderr.toString().trim() }
            if (resultError != null && !emittedText) throw BackendException("Claude Code: ${resultError!!.take(400)}")
            if (!emittedText) {
                val detail = listOf(err, raw.toString().trim()).filter { it.isNotBlank() }.joinToString("\n").take(600)
                val hint = when {
                    exit == 127 || detail.contains("not found") -> " — is Claude Code installed? Set the full path in Settings (try `which claude` on the server)."
                    looksLikeAuthOrLimit(detail) -> ""
                    else -> ""
                }
                throw BackendException("Claude Code exited ($exit) with no answer$hint\n$detail")
            }
        } finally {
            runCatching { ch.disconnect() }
            runCatching { session.disconnect() }
        }
    }.flowOn(Dispatchers.IO)

    /** Run an arbitrary command (used by "Test connection"). */
    suspend fun exec(s: AppSettings, sec: Secrets, command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val session = connect(s, sec)
        try {
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand(command)
            val out = ch.inputStream
            val err = ch.errStream
            ch.connect(s.sshTimeoutSec * 1000)
            val o = out.bufferedReader().readText()
            val e = err.bufferedReader().readText()
            var waited = 0
            while (!ch.isClosed && waited < 3000) { Thread.sleep(50); waited += 50 }
            val code = ch.exitStatus
            ch.disconnect()
            code to (o + e).trim()
        } finally {
            session.disconnect()
        }
    }

    suspend fun test(s: AppSettings, sec: Secrets): String = runCatching {
        val cmd = "bash -lc " + shellQuote("${cdExpr(s.remoteWorkDir)} && echo \"dir: \$(pwd)\" && ${s.claudePath.ifBlank { "claude" }} --version")
        val (code, out) = exec(s, sec, cmd)
        if (code == 0) "✓ Connected. $out\nTip: send a message to confirm Claude Code is logged in."
        else "✗ Connected over SSH, but Claude Code check failed (exit $code):\n$out"
    }.getOrElse { "✗ ${it.message}" }

    private fun toolSummary(b: JsonObject): String {
        val name = b.str("name") ?: "tool"
        val input = b["input"]?.jsonObject
        val detail = input?.let { it.str("command") ?: it.str("file_path") ?: it.str("pattern") ?: it.str("url") ?: it.str("query") }
        val label = when (name) { "WebSearch" -> "Searching the web"; "WebFetch" -> "Reading"; else -> name }
        return if (detail != null) "$label: ${detail.take(80)}" else "$label…"
    }

    private fun JsonObject.str(k: String): String? = runCatching { this[k]?.jsonPrimitive?.contentOrNull }.getOrNull()

    companion object {
        fun looksLikeAuthOrLimit(t: String): Boolean {
            val l = t.lowercase()
            return listOf("usage limit", "rate limit", "/login", "not logged in", "invalid api key", "oauth", "authentication", "credit balance")
                .any { l.contains(it) }
        }
    }
}
