package com.theemrock.smoothmedia.metadata

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class TagTextDecoderTest {
    @Test fun utf8BomIsDecodedWithoutReplacement() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Çağrı Özer".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Çağrı Özer", TagTextDecoder.decode(bytes))
    }

    @Test fun id3Utf16LeBomIsDecoded() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Şarkı".toByteArray(Charsets.UTF_16LE)
        assertEquals("Şarkı", TagTextDecoder.decode(bytes))
    }
}
