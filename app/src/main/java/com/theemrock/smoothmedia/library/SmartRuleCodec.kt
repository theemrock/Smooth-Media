package com.theemrock.smoothmedia.library

/**
 * Text form of a [SmartRule] for storage, e.g. `all(genre(rock\,metal;1),not(artist(Yol)))`.
 * Text arguments escape `\`, `,`, `(` and `)` with a backslash, so the form stays unambiguous.
 * shortcut: hand-written codec because org.json is a stub in JVM unit tests; swap for kotlinx.serialization if a second format appears.
 */
object SmartRuleCodec {
    fun encode(rule: SmartRule): String = when (rule) {
        is SmartRule.All -> rule.children.joinToString(",", prefix = "all(", postfix = ")") { encode(it) }
        is SmartRule.Any -> rule.children.joinToString(",", prefix = "any(", postfix = ")") { encode(it) }
        is SmartRule.Not -> "not(${encode(rule.child)})"
        is SmartRule.Genre -> "genre(${escape(rule.genreId)},${if (rule.includeDescendants) "1" else "0"})"
        SmartRule.NoGenre -> "noGenre()"
        is SmartRule.ArtistIs -> "artist(${escape(rule.artist)})"
        is SmartRule.AlbumIs -> "album(${escape(rule.album)})"
        is SmartRule.TitleContains -> "title(${escape(rule.text)})"
    }

    /** Throws [IllegalArgumentException] on malformed text; callers treat that as "no saved rule". */
    fun decode(text: String): SmartRule {
        val reader = Reader(text)
        val rule = reader.node()
        require(reader.atEnd()) { "trailing text at ${reader.pos}" }
        return rule
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ',' || c == '(' || c == ')') append('\\')
            append(c)
        }
    }

    private class Reader(private val s: String) {
        var pos = 0

        fun atEnd() = pos == s.length

        fun node(): SmartRule {
            val tag = s.substring(pos).takeWhile { it.isLetter() }
            pos += tag.length
            expect('(')
            val rule = when (tag) {
                "all" -> SmartRule.All(list())
                "any" -> SmartRule.Any(list())
                "not" -> SmartRule.Not(node()).also { expect(')') }
                "noGenre" -> SmartRule.NoGenre.also { expect(')') }
                "genre" -> {
                    val id = text()
                    expect(',')
                    val flag = text()
                    require(flag == "1" || flag == "0") { "bad genre flag: $flag" }
                    SmartRule.Genre(id, includeDescendants = flag == "1").also { expect(')') }
                }
                "artist" -> SmartRule.ArtistIs(text()).also { expect(')') }
                "album" -> SmartRule.AlbumIs(text()).also { expect(')') }
                "title" -> SmartRule.TitleContains(text()).also { expect(')') }
                else -> throw IllegalArgumentException("unknown rule: $tag")
            }
            return rule
        }

        /** Children up to and including the closing paren; empty list when the paren comes first. */
        private fun list(): List<SmartRule> {
            val children = mutableListOf<SmartRule>()
            if (peek() == ')') {
                pos++
                return children
            }
            children += node()
            while (peek() == ',') {
                pos++
                children += node()
            }
            expect(')')
            return children
        }

        /** Reads an escaped text argument up to the next unescaped `,` or `)`. */
        private fun text(): String {
            val out = StringBuilder()
            while (pos < s.length) {
                val c = s[pos]
                when {
                    c == '\\' -> {
                        require(pos + 1 < s.length) { "dangling escape" }
                        out.append(s[pos + 1])
                        pos += 2
                    }
                    c == ',' || c == ')' -> break
                    c == '(' -> throw IllegalArgumentException("unescaped ( at $pos")
                    else -> {
                        out.append(c)
                        pos++
                    }
                }
            }
            return out.toString()
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        private fun expect(c: Char) {
            require(peek() == c) { "expected $c at $pos" }
            pos++
        }
    }
}
