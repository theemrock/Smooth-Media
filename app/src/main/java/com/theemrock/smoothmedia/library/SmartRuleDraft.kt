package com.theemrock.smoothmedia.library

enum class SmartRuleKind { GENRE, ARTIST, ALBUM, TITLE }

/** One editable row in the smart playlist editor. */
data class SmartRuleDraft(val kind: SmartRuleKind, val value: String)

/**
 * Editor rows <-> SmartRule. The editor only builds a flat AND of single conditions;
 * nested rules created elsewhere are reported as not editable instead of being flattened.
 */
object SmartRuleDrafts {
    /** Blank rows are ignored. Null when no row is left, so the playlist would match nothing useful. */
    fun toRule(drafts: List<SmartRuleDraft>): SmartRule? {
        val children = drafts.mapNotNull { draft ->
            val value = draft.value.trim()
            if (value.isEmpty()) return@mapNotNull null
            when (draft.kind) {
                // Same id rule as genre sync: display name lowercased.
                SmartRuleKind.GENRE -> SmartRule.Genre(value.lowercase())
                SmartRuleKind.ARTIST -> SmartRule.ArtistIs(value)
                SmartRuleKind.ALBUM -> SmartRule.AlbumIs(value)
                SmartRuleKind.TITLE -> SmartRule.TitleContains(value)
            }
        }
        return if (children.isEmpty()) null else SmartRule.All(children)
    }

    /** Null when the rule is not a flat AND of single conditions (the editor cannot show it). */
    fun fromRule(rule: SmartRule): List<SmartRuleDraft>? {
        val leaves = when (rule) {
            is SmartRule.All -> rule.children
            else -> listOf(rule)
        }
        return leaves.map { leaf ->
            when (leaf) {
                is SmartRule.Genre -> if (leaf.includeDescendants) SmartRuleDraft(SmartRuleKind.GENRE, leaf.genreId) else return null
                is SmartRule.ArtistIs -> SmartRuleDraft(SmartRuleKind.ARTIST, leaf.artist)
                is SmartRule.AlbumIs -> SmartRuleDraft(SmartRuleKind.ALBUM, leaf.album)
                is SmartRule.TitleContains -> SmartRuleDraft(SmartRuleKind.TITLE, leaf.text)
                else -> return null
            }
        }
    }
}
