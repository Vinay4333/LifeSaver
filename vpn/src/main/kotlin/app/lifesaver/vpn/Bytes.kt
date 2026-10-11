package app.lifesaver.vpn

internal object Bytes {
    fun getU16(
        buf: ByteArray,
        offset: Int,
    ): Int = ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    fun putU16(
        buf: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }
}
