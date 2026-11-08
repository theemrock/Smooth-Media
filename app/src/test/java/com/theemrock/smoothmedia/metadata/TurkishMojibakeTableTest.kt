package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishMojibakeTableTest {

    private fun misread(text: String, charset: String): String =
        String(text.toByteArray(Charsets.UTF_8), charset(charset))

    private fun charset(name: String) = java.nio.charset.Charset.forName(name)

    @Test
    fun latin2MisreadTurkishLettersAreRestored() {
        assertEquals("Yalnız", TurkishMojibakeTable.repair(misread("Yalnız", "ISO-8859-2")))
        assertEquals("Fasıl", TurkishMojibakeTable.repair(misread("Fasıl", "ISO-8859-2")))
        assertEquals("Hürmüz", TurkishMojibakeTable.repair(misread("Hürmüz", "ISO-8859-2")))
    }

    @Test
    fun gbkMisreadTurkishLettersAreRestored() {
        assertEquals("Göksel", TurkishMojibakeTable.repair(misread("Göksel", "GBK")))
        assertEquals("Hümeyra", TurkishMojibakeTable.repair(misread("Hümeyra", "GBK")))
    }

    @Test
    fun latin1MisreadTurkishLettersAreRestored() {
        assertEquals("Aşk Yangını", TurkishMojibakeTable.repair(misread("Aşk Yangını", "ISO-8859-1")))
        assertEquals("Doğuş Çağrı", TurkishMojibakeTable.repair(misread("Doğuş Çağrı", "ISO-8859-1")))
    }

    @Test
    fun plainTurkishTextIsUnchanged() {
        assertEquals("Barış Manço", TurkishMojibakeTable.repair("Barış Manço"))
        assertEquals("Ezginin Günlüğü", TurkishMojibakeTable.repair("Ezginin Günlüğü"))
    }
}
