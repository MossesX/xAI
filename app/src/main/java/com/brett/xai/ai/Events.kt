package com.brett.xai.ai

/** Everything a backend can stream back to the chat. */
sealed interface Ev {
    data class Text(val delta: String) : Ev
    data class Status(val text: String) : Ev
    data class Backend(val label: String) : Ev
    data class Fallback(val reason: String) : Ev
    data class SessionId(val id: String) : Ev
}

class BackendException(message: String, val retryable: Boolean = true) : Exception(message)

fun shellQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"
