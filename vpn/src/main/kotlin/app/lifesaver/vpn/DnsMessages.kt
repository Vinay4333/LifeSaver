package app.lifesaver.vpn

internal object DnsMessages {
    private const val HEADER_LEN = 12
    private const val QR = 0x8000
    private const val RA = 0x0080
    private const val RCODE_MASK = 0x000F
    private const val NXDOMAIN = 3
    private const val POINTER_MASK = 0xC0
    private const val MAX_LABEL = 63
    private const val MAX_HOPS = 16

    fun isResponse(payload: ByteArray): Boolean {
        if (payload.size < HEADER_LEN) {
            return false
        }
        return (Bytes.getU16(payload, 2) and QR) != 0
    }

    fun rcode(payload: ByteArray): Int {
        if (payload.size < HEADER_LEN) {
            return -1
        }
        return Bytes.getU16(payload, 2) and RCODE_MASK
    }

    fun questionNames(payload: ByteArray): List<String>? {
        if (payload.size < HEADER_LEN || isResponse(payload)) {
            return null
        }
        val count = Bytes.getU16(payload, 4)
        val names = ArrayList<String>(count)
        var offset = HEADER_LEN
        repeat(count) {
            val read = readName(payload, offset) ?: return null
            names += read.first
            offset = read.second
            if (offset + TYPE_AND_CLASS > payload.size) {
                return null
            }
            offset += TYPE_AND_CLASS
        }
        return names
    }

    fun nxDomain(query: ByteArray): ByteArray? {
        if (query.size < HEADER_LEN || isResponse(query)) {
            return null
        }
        val questionEnd = endOfQuestions(query) ?: return null
        val out = query.copyOf(questionEnd)
        val flags = (Bytes.getU16(query, 2) and RCODE_MASK.inv()) or QR or RA or NXDOMAIN
        Bytes.putU16(out, 2, flags)
        Bytes.putU16(out, 6, 0)
        Bytes.putU16(out, 8, 0)
        Bytes.putU16(out, 10, 0)
        return out
    }

    private fun endOfQuestions(payload: ByteArray): Int? {
        val count = Bytes.getU16(payload, 4)
        if (count < 1) {
            return null
        }
        var offset = HEADER_LEN
        repeat(count) {
            offset = skipName(payload, offset) ?: return null
            if (offset + TYPE_AND_CLASS > payload.size) {
                return null
            }
            offset += TYPE_AND_CLASS
        }
        return offset
    }

    private fun readName(
        payload: ByteArray,
        start: Int,
    ): Pair<String, Int>? {
        val labels = ArrayList<String>(4)
        var index = start
        var end = -1
        var hops = 0
        while (hops < MAX_HOPS) {
            if (index >= payload.size) {
                return null
            }
            val length = payload[index].toInt() and 0xFF
            when {
                length == 0 -> {
                    val consumed = if (end == -1) index + 1 else end
                    return labels.joinToString(".") to consumed
                }
                length and POINTER_MASK == POINTER_MASK -> {
                    if (index + 1 >= payload.size) {
                        return null
                    }
                    if (end == -1) {
                        end = index + 2
                    }
                    index = ((length and 0x3F) shl 8) or (payload[index + 1].toInt() and 0xFF)
                    hops++
                }
                else -> {
                    if (length > MAX_LABEL || index + 1 + length > payload.size) {
                        return null
                    }
                    labels += payload.decodeToString(index + 1, index + 1 + length)
                    index += 1 + length
                }
            }
        }
        return null
    }

    private fun skipName(
        payload: ByteArray,
        start: Int,
    ): Int? {
        var index = start
        var hops = 0
        while (index < payload.size && hops < MAX_HOPS) {
            val length = payload[index].toInt() and 0xFF
            when {
                length == 0 -> return index + 1
                length and POINTER_MASK == POINTER_MASK -> {
                    if (index + 1 >= payload.size) {
                        return null
                    }
                    return index + 2
                }
                else -> {
                    if (length > MAX_LABEL || index + 1 + length > payload.size) {
                        return null
                    }
                    index += 1 + length
                    hops++
                }
            }
        }
        return null
    }

    private const val TYPE_AND_CLASS = 4
}
