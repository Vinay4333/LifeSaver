package app.lifesaver.policy

import java.security.SecureRandom
import java.util.Base64

object UnlockTokens {
    private val random = SecureRandom()

    /** URL-safe random id. 16 bytes is 128 bits. Used for request ids and relay topics. */
    fun random(bytes: Int = 16): String {
        val raw = ByteArray(bytes)
        random.nextBytes(raw)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }
}
