package app.lifesaver.policy

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object UnlockTokens {
    private val random = SecureRandom()

    /** URL-safe random token. 16 bytes is 128 bits. */
    fun random(bytes: Int = 16): String {
        val raw = ByteArray(bytes)
        random.nextBytes(raw)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    fun hash(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun matches(
        token: String,
        expectedHash: String,
    ): Boolean =
        MessageDigest.isEqual(
            hash(token).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )
}
