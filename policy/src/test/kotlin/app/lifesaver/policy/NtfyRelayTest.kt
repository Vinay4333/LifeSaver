package app.lifesaver.policy

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Runs the real HTTP client against a tiny local stand-in for ntfy. */
class NtfyRelayTest {
    private lateinit var server: HttpServer
    private val posts = ArrayList<Triple<String, String, Map<String, String>>>()
    private val topics = HashMap<String, MutableList<String>>()
    private var status = 200

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { handle(it) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path.trim('/')
        val reply: String
        if (exchange.requestMethod == "POST") {
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            posts.add(Triple(path, body, headers))
            topics.getOrPut(path) { ArrayList() }.add(body)
            reply = "{}"
        } else {
            val topic = path.removeSuffix("/json")
            val lines =
                listOf("""{"id":"o","event":"open","topic":"$topic"}""") +
                    topics[topic].orEmpty().mapIndexed { i, m ->
                        """{"id":"m$i","event":"message","topic":"$topic","message":"$m"}"""
                    }
            reply = lines.joinToString("\n")
        }
        val bytes = reply.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun sendEmailPostsToAFreshTopicWithEmailHeaders() {
        val relay = NtfyRelay(base)
        assertTrue(relay.sendEmail("p@x.test", "Title", "Body text", "https://click.test/#a"))
        assertTrue(relay.sendEmail("p@x.test", "Title", "Body text", "https://click.test/#a"))
        val (first, second) = posts
        assertEquals("Body text", first.second)
        assertEquals("p@x.test", first.third["email"])
        assertEquals("Title", first.third["title"])
        assertEquals("https://click.test/#a", first.third["click"])
        assertTrue("topics must differ per email", first.first != second.first)
    }

    @Test
    fun publishThenPollRoundTrips() {
        val relay = NtfyRelay(base)
        assertTrue(relay.publish("approval1", "unlock.abc"))
        assertEquals(listOf("unlock.abc"), relay.poll("approval1"))
        assertTrue(relay.poll("other").isEmpty())
    }

    @Test
    fun failuresReturnFalseOrEmpty() {
        status = 500
        val relay = NtfyRelay(base)
        assertFalse(relay.publish("t", "m"))
        assertTrue(relay.poll("t").isEmpty())
        server.stop(0)
        assertFalse(relay.publish("t", "m"))
        assertTrue(relay.poll("t").isEmpty())
    }

    @Test
    fun fullFlowOverHttpOpensTheWindow() {
        val store = MemoryStore()
        val clock = FakeClock()
        val relay = NtfyRelay(base)
        val engine =
            UnlockEngine(store, clock, relay, UnlockConfig(approveBase = "https://x.test/a/", relayBase = base))
        assertTrue(engine.setupPartner("partner@x.test"))
        assertEquals(RequestResult.Sent, engine.requestUnlock())

        val emailed = posts.single { it.third.containsKey("email") }
        val approval = ApproveLink.parse(emailed.third.getValue("click"))!!
        assertFalse(engine.poll())
        assertTrue(ApproveLink.approve(approval, NtfyRelay(approval.relay)))
        assertTrue(engine.poll())
        assertTrue(engine.isOpen())
    }
}
