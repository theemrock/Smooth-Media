package com.theemrock.smoothmedia.metadata

import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun id3Utf16BeBomIsDecoded() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "İstanbul".toByteArray(Charsets.UTF_16BE)
        assertEquals("İstanbul", TagTextDecoder.decode(bytes))
    }

    @Test fun flacVorbisUtf8WithoutBomIsDecodedWithDeclaredCharset() {
        val bytes = "Çağrı Özer".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Çağrı Özer", TagTextDecoder.decode(bytes, StandardCharsets.UTF_8))
    }

    @Test fun id3v1SingleByteTextUsesDeclaredCharset() {
        val bytes = "Çığ".toByteArray(Charset.forName("windows-1254"))
        assertEquals("Çığ", TagTextDecoder.decode(bytes, Charset.forName("windows-1254")))
    }

    @Test fun mojibakeRepairHandlesCommonTurkishUtf8Examples() {
        val examples = mapOf(
            "ÅžarkÄ±" to "Şarkı",
            "GÃ¶zlerin" to "Gözlerin",
            "KÃ¼Ã§Ã¼k" to "Küçük",
            "AÅŸk" to "Aşk",
            "Ä°stanbul" to "İstanbul",
        )

        examples.forEach { (broken, expected) ->
            assertEquals(expected, TagTextDecoder.normalizeText(broken))
        }
    }

    @Test fun mojibakeRepairDoesNotChangeValidUnicodeOrAmbiguousText() {
        assertEquals("Şarkı — Björk — 東京", TagTextDecoder.normalizeText("Şarkı — Björk — 東京"))
        assertEquals("Åland", TagTextDecoder.normalizeText("Åland"))
    }

    @Test fun mojibakeRepairIsIdempotent() {
        val once = TagTextDecoder.normalizeText("GÃ¶zlerin")

        assertEquals("Gözlerin", once)
        assertEquals(once, TagTextDecoder.normalizeText(once))
    }

    @Test fun repairSuggestionReturnsOnlyHighConfidenceCandidate() {
        assertEquals("Gözlerin", TagTextDecoder.suggestRepair("GÃ¶zlerin")?.repaired)
        assertEquals("GÃ¶zlerin", TagTextDecoder.suggestRepair("GÃ¶zlerin")?.original)
    }

    @Test fun repairSuggestionDoesNotChangeValidAsciiTurkishOrAmbiguousText() {
        assertNull(TagTextDecoder.suggestRepair("Album"))
        assertNull(TagTextDecoder.suggestRepair("Şarkı"))
        assertNull(TagTextDecoder.suggestRepair("Åland"))
        assertNull(TagTextDecoder.suggestRepair("Ã"))
    }
}
