package app.lifesaver.policy

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Moves messages between the phone and the partner. Network calls: never on the main thread. */
interface Relay {
    /**
     * Emails [body] to [email]. The message goes to a fresh random topic that is not kept.
     * Returns null on success, otherwise a short reason such as `HTTP 429` or `network error`.
     */
    fun sendEmail(
        email: String,
        title: String,
        body: String,
        clickUrl: String,
    ): String?

    /** Posts [message] to [topic]. Used by the partner's approve action. */
    fun publish(
        topic: String,
        message: String,
    ): Boolean

    /** Message bodies currently cached for [topic]. Empty on any failure. */
    fun poll(topic: String): List<String>
}

/** ntfy.sh (or a compatible server). HTTP only through [HttpURLConnection]; no extra dependencies. */
class NtfyRelay(
    base: String,
) : Relay {
    private val base = base.trimEnd('/')

    override fun sendEmail(
        email: String,
        title: String,
        body: String,
        clickUrl: String,
    ): String? =
        post(
            "$base/${UnlockTokens.random()}",
            body,
            mapOf("Email" to email, "Title" to title, "Click" to clickUrl, "Priority" to "high"),
        )

    override fun publish(
        topic: String,
        message: String,
    ): Boolean = post("$base/$topic", message, emptyMap()) == null

    @Suppress("SwallowedException") // A network failure is an expected "no answer", not an error.
    override fun poll(topic: String): List<String> =
        try {
            val connection = open("$base/$topic/json?poll=1&since=all", "GET")
            try {
                if (connection.responseCode in HTTP_OK_RANGE) {
                    NtfyJson.messages(connection.inputStream.bufferedReader().readText())
                } else {
                    emptyList()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            emptyList()
        }

    /** Returns null on success, otherwise a short reason that is safe to show the user. */
    @Suppress("SwallowedException")
    private fun post(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): String? =
        try {
            val connection = open(url, "POST")
            try {
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code in HTTP_OK_RANGE) null else "HTTP $code${errorText(connection)}"
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            "network error (${e.javaClass.simpleName})"
        }

    /** ntfy explains refusals in a small JSON body, for example the email rate limit. */
    @Suppress("SwallowedException") // The reason is a nicety; the status code alone is still reported.
    private fun errorText(connection: HttpURLConnection): String {
        val raw =
            try {
                connection.errorStream
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            } catch (e: IOException) {
                ""
            }
        val reason = errorField.find(raw)?.groupValues?.get(1)
        return if (reason.isNullOrBlank()) "" else ": ${reason.take(MAX_REASON)}"
    }

    private fun open(
        url: String,
        method: String,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_REASON = 120
        val errorField = Regex("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val HTTP_OK_RANGE = 200..299
    }
}

/** Pulls `message` fields out of ntfy's JSON-lines output. Only `event":"message"` lines count. */
object NtfyJson {
    private val messageField = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun messages(body: String): List<String> =
        body
            .lineSequence()
            .filter { "\"event\":\"message\"" in it.replace(" ", "") }
            .mapNotNull { line ->
                messageField
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.let(::unescape)
            }.toList()

    private fun unescape(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                out.append(c)
                i++
                continue
            }
            val next = raw[i + 1]
            if (next == 'u' && i + UNICODE_END <= raw.length) {
                out.append(raw.substring(i + 2, i + UNICODE_END).toInt(HEX).toChar())
                i += UNICODE_END
            } else {
                out.append(
                    when (next) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        else -> next
                    },
                )
                i += 2
            }
        }
        return out.toString()
    }

    private const val UNICODE_END = 6
    private const val HEX = 16
}
