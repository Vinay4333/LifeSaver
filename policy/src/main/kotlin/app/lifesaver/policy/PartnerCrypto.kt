package app.lifesaver.policy

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class PartnerKeyPair(
    /** 65-byte uncompressed point, base64url. This is what the user pastes into the phone. */
    val publicKey: String,
    /** PKCS#8, base64url. Stays on the partner's device. */
    val privateKey: String,
)

/**
 * ECDSA P-256 / SHA-256, wire-compatible with WebCrypto: public key is the raw uncompressed
 * point and signatures are `r || s` (64 bytes), both base64url without padding.
 */
object PartnerCrypto {
    private const val COORD = 32
    private const val POINT_LEN = 1 + 2 * COORD
    private const val SIG_LEN = 2 * COORD
    private const val UNCOMPRESSED: Byte = 4
    private const val FINGERPRINT_BYTES = 8
    private const val FINGERPRINT_GROUP = 4
    private const val DER_SEQUENCE = 0x30
    private const val DER_INTEGER = 0x02
    private const val DER_LONG_FORM = 0x80
    private const val BYTE_MASK = 0xFF

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    private val curve: ECParameterSpec by lazy {
        AlgorithmParameters
            .getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray? =
        try {
            decoder.decode(text.trim())
        } catch (e: IllegalArgumentException) {
            null.also { e.message }
        }

    fun message(
        purpose: Purpose,
        requestId: String,
        extra: String = "",
    ): String = "lifesaver/v1|${purpose.wire}|$requestId|$extra"

    fun generate(): PartnerKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val point = (pair.public as ECPublicKey).w
        return PartnerKeyPair(encode(rawPoint(point)), encode(pair.private.encoded))
    }

    /** `A1B2-C3D4-E5F6-0718`, for humans to compare. Null if [publicKey] is not a valid key. */
    fun fingerprint(publicKey: String): String? {
        val raw = decode(publicKey)?.takeIf { it.size == POINT_LEN } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest
            .take(FINGERPRINT_BYTES)
            .joinToString("") { "%02X".format(it) }
            .chunked(FINGERPRINT_GROUP)
            .joinToString("-")
    }

    fun isValidPublicKey(publicKey: String): Boolean = parsePublic(publicKey) != null

    fun sign(
        privateKey: String,
        message: String,
    ): String? {
        val pkcs8 = decode(privateKey) ?: return null
        return try {
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(key)
            signer.update(message.toByteArray(Charsets.UTF_8))
            encode(derToRaw(signer.sign()))
        } catch (e: GeneralSecurityException) {
            null.also { e.message }
        }
    }

    fun verify(
        publicKey: String,
        message: String,
        signature: String,
    ): Boolean {
        val key = parsePublic(publicKey) ?: return false
        val raw = decode(signature)?.takeIf { it.size == SIG_LEN } ?: return false
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(message.toByteArray(Charsets.UTF_8))
            verifier.verify(rawToDer(raw))
        } catch (e: GeneralSecurityException) {
            false.also { e.message }
        }
    }

    private fun parsePublic(publicKey: String): ECPublicKey? {
        val raw = decode(publicKey)?.takeIf { it.size == POINT_LEN && it[0] == UNCOMPRESSED } ?: return null
        val x = BigInteger(1, raw.copyOfRange(1, 1 + COORD))
        val y = BigInteger(1, raw.copyOfRange(1 + COORD, POINT_LEN))
        if (!onCurve(x, y)) {
            return null
        }
        return try {
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), curve)) as ECPublicKey
        } catch (e: GeneralSecurityException) {
            null.also { e.message }
        }
    }

    /** Java does not check this when building a key, and a point off the curve could never sign. */
    private fun onCurve(
        x: BigInteger,
        y: BigInteger,
    ): Boolean {
        val p = (curve.curve.field as ECFieldFp).p
        if (x >= p || y >= p) {
            return false
        }
        val left = y.multiply(y).mod(p)
        val right =
            x
                .pow(3)
                .add(curve.curve.a.multiply(x))
                .add(curve.curve.b)
                .mod(p)
        return left == right
    }

    private fun rawPoint(point: ECPoint): ByteArray =
        byteArrayOf(UNCOMPRESSED) + fixed(point.affineX) + fixed(point.affineY)

    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(COORD - bytes.size) + bytes
    }

    /** `r || s` to ASN.1 DER, which is what [Signature] expects. */
    internal fun rawToDer(raw: ByteArray): ByteArray {
        val r = derInteger(raw.copyOfRange(0, COORD))
        val s = derInteger(raw.copyOfRange(COORD, SIG_LEN))
        val body = r + s
        return byteArrayOf(DER_SEQUENCE.toByte()) + derLength(body.size) + body
    }

    private fun derInteger(unsigned: ByteArray): ByteArray {
        var bytes = unsigned.dropWhile { it == 0.toByte() }.toByteArray()
        if (bytes.isEmpty()) bytes = byteArrayOf(0)
        if (bytes[0] < 0) bytes = byteArrayOf(0) + bytes
        return byteArrayOf(DER_INTEGER.toByte()) + derLength(bytes.size) + bytes
    }

    private fun derLength(length: Int): ByteArray =
        if (length < DER_LONG_FORM) {
            byteArrayOf(length.toByte())
        } else {
            byteArrayOf((DER_LONG_FORM or 1).toByte(), length.toByte())
        }

    /** ASN.1 DER to `r || s`. */
    internal fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        if (der[1].toInt() and BYTE_MASK >= DER_LONG_FORM) i += 1
        val out = ByteArray(SIG_LEN)
        for (part in 0..1) {
            val length = der[i + 1].toInt() and BYTE_MASK
            val value = der.copyOfRange(i + 2, i + 2 + length).dropWhile { it == 0.toByte() }.toByteArray()
            value.copyInto(out, part * COORD + (COORD - value.size))
            i += 2 + length
        }
        return out
    }
}
