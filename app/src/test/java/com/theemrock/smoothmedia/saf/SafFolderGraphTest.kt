package com.theemrock.smoothmedia.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SafFolderGraphTest {
    @Test
    fun `opaque document ids preserve nested and same named folders under different roots`() {
        val graph = SafFolderGraph(
            listOf(
                SafDocument("root-a", null, "Music", true),
                SafDocument("root-b", null, "Music", true),
                SafDocument("folder-a", "root-a", "Live", true),
                SafDocument("folder-b", "root-b", "Live", true),
                SafDocument("song-a", "folder-a", "track.mp3", false),
            ),
        )
        assertEquals(listOf("folder-a"), graph.childrenOf("root-a").map { it.documentId })
        assertEquals("folder-a", graph.parentOf("song-a")?.documentId)
        assertNotEquals(graph.stableKeyOf("folder-a"), graph.stableKeyOf("folder-b"))
        assertEquals("Music/Live/track.mp3", graph.pathOf("song-a"))
    }

    @Test
    fun `roots exclude nested documents but keep documents whose provider parent is not persisted`() {
        val graph = SafFolderGraph(
            listOf(
                SafDocument("root", null, "Music", true),
                SafDocument("nested", "root", "Albums", true),
                SafDocument("provider-root-child", "opaque-provider-root", "Singles", true),
            ),
        )

        assertEquals(listOf("root", "provider-root-child"), graph.rootDocuments().map { it.documentId })
    }
}
