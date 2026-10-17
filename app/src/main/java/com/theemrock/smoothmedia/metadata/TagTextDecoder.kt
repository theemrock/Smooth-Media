package com.theemrock.smoothmedia.metadata

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Byte-level decoding used by format adapters; no heuristic mojibake replacement. */
object TagTextDecoder {
    fun decode(bytes: ByteArray, declaredCharset: Charset? = null): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        return (declaredCharset ?: StandardCharsets.ISO_8859_1).decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun ByteArray.toString(charset: Charset): String = charset.decode(ByteBuffer.wrap(this)).toString()
}
