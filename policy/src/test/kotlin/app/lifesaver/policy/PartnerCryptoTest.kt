package app.lifesaver.policy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Signature

class PartnerCryptoTest {
    // Produced by docs/approve/crypto.js (the approve page's own code) under Node's WebCrypto.
    // Proves the page and the app agree on key format, signature format, fingerprint and message.
    private val webPublic =
        "BEFoVJvZ79vClsw2lt0egGoWfhgubgHzX2WhSuriSuABmIH4wvjdsdjVtIaze_y0q7FpnEjzGnQvv29nnW8WScM"
    private val webMessage = "lifesaver/v1|unlock|REQID123|"
    private val webSignature =
        "I02Koikkk0vGWwT6E2JL6dnQ0VboTYNtbqIVzAa4UB8bci_sTMdAN3EF6cIWPYHRazg4PophLQC_8vG0LWyQ8Q"
    private val webFingerprint = "143A-E7C4-7C2B-697C"
    private val webPartnerMessage = "lifesaver/v1|partner|REQ9|$webPublic"
    private val webPartnerSignature =
        "thSpNd2TKIGUniU9ym6QucBnQUwAS6V47GcEQejrlUxIdtfYjTHZe4V_8tYqjX9qBwxEcmiRTf89QTSsF3rSLw"

    @Test
    fun verifiesSignaturesMadeByThePageCode() {
        assertTrue(PartnerCrypto.verify(webPublic, webMessage, webSignature))
        assertEquals(webFingerprint, PartnerCrypto.fingerprint(webPublic))
        assertEquals(webPartnerMessage, PartnerCrypto.message(Purpose.ChangePartner, "REQ9", webPublic))
        assertTrue(PartnerCrypto.verify(webPublic, webPartnerMessage, webPartnerSignature))
    }

    @Test
    fun webSignatureFailsForAnotherMessageOrKey() {
        assertFalse(PartnerCrypto.verify(webPublic, "lifesaver/v1|unlock|OTHER|", webSignature))
        assertFalse(PartnerCrypto.verify(PartnerCrypto.generate().publicKey, webMessage, webSignature))
    }

    @Test
    fun signAndVerifyRoundTrip() {
        val pair = PartnerCrypto.generate()
        val message = PartnerCrypto.message(Purpose.Unlock, "abc")
        val signature = PartnerCrypto.sign(pair.privateKey, message)!!
        assertTrue(PartnerCrypto.verify(pair.publicKey, message, signature))
        assertFalse(PartnerCrypto.verify(pair.publicKey, message + "x", signature))
    }

    @Test
    fun manySignaturesSurviveTheDerConversion() {
        val pair = PartnerCrypto.generate()
        repeat(300) { i ->
            val message = "m$i"
            val signature = PartnerCrypto.sign(pair.privateKey, message)!!
            assertEquals(86, signature.length)
            assertTrue("signature $i", PartnerCrypto.verify(pair.publicKey, message, signature))
        }
    }

    @Test
    fun derAndRawConvertBothWays() {
        val pair = PartnerCrypto.generate()
        val key =
            java.security.KeyFactory
                .getInstance("EC")
                .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(PartnerCrypto.decode(pair.privateKey)))
        repeat(100) { i ->
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(key)
            signer.update("x$i".toByteArray())
            val der = signer.sign()
            val raw = PartnerCrypto.derToRaw(der)
            assertEquals(64, raw.size)
            assertArrayEquals(der, PartnerCrypto.rawToDer(raw))
        }
    }

    @Test
    fun fingerprintLooksRightAndIsStable() {
        val pair = PartnerCrypto.generate()
        val fp = PartnerCrypto.fingerprint(pair.publicKey)!!
        assertTrue(Regex("^([0-9A-F]{4}-){3}[0-9A-F]{4}$").matches(fp))
        assertEquals(fp, PartnerCrypto.fingerprint(pair.publicKey))
        assertNull(PartnerCrypto.fingerprint("short"))
    }

    @Test
    fun rejectsMalformedPublicKeys() {
        assertFalse(PartnerCrypto.isValidPublicKey(""))
        assertFalse(PartnerCrypto.isValidPublicKey("%%%"))
        assertFalse(PartnerCrypto.isValidPublicKey(PartnerCrypto.encode(ByteArray(65) { 4 })))
        assertFalse(PartnerCrypto.isValidPublicKey(PartnerCrypto.encode(ByteArray(33))))
        assertTrue(PartnerCrypto.isValidPublicKey(PartnerCrypto.generate().publicKey))
        assertNotNull(PartnerCrypto.decode("AAAA"))
    }

    @Test
    fun verifyRejectsBadSignatureShapes() {
        val pair = PartnerCrypto.generate()
        assertFalse(PartnerCrypto.verify(pair.publicKey, "m", ""))
        assertFalse(PartnerCrypto.verify(pair.publicKey, "m", "%%%"))
        assertFalse(PartnerCrypto.verify(pair.publicKey, "m", PartnerCrypto.encode(ByteArray(64))))
        assertFalse(PartnerCrypto.verify("bad", "m", PartnerCrypto.encode(ByteArray(64))))
    }

    @Test
    fun messageBindsPurposeIdAndExtra() {
        assertEquals("lifesaver/v1|unlock|id|", PartnerCrypto.message(Purpose.Unlock, "id"))
        assertEquals("lifesaver/v1|partner|id|KEY", PartnerCrypto.message(Purpose.ChangePartner, "id", "KEY"))
    }
}
