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
    fun publishThenPollRoundTrips() {
        val relay = NtfyRelay(base)
        assertTrue(relay.publish("topic1", "unlock.id.sig"))
        assertEquals(listOf("unlock.id.sig"), relay.poll("topic1"))
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
    fun fullFlowOverHttpPairsAndUnlocks() {
        val engine =
            UnlockEngine(
                MemoryStore(),
                FakeClock(),
                NtfyRelay(base),
                UnlockConfig(approveBase = "https://x.test/a/", relayBase = base),
            )
        val partner = PartnerKeys(MemoryStore())
        val publicKey = partner.create()

        assertEquals(RequestResult.Created, engine.beginPairing(publicKey, null))
        assertFalse(engine.poll())
        assertEquals(ApproveResult.Sent, partner.approve(ApproveLink.parse(engine.share()!!.webLink)!!))
        assertTrue(engine.poll())

        engine.requestUnlock()
        val approval = ApproveLink.parse(engine.share()!!.webLink)!!
        assertEquals(ApproveResult.Sent, partner.approve(approval))
        assertTrue(engine.poll())
        assertTrue(engine.isOpen())
    }
}
