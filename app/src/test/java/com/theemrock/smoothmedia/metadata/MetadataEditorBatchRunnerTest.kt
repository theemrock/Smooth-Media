package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataEditorBatchRunnerTest {
    @Test
    fun cancellationStopsBeforeTheNextLocation() {
        val items = listOf(item("one"), item("two"), item("three"))
        val written = mutableListOf<String>()
        var cancelled = false

        val result = MetadataEditorBatchRunner().run(
            items = items,
            edit = FileTagEdit(artist = "CODEX"),
            isCancelled = { cancelled },
            write = { item, _ ->
                written += item.id
                cancelled = true
                MetadataEditorItemResult.SUCCESS
            },
        )

        assertEquals(listOf("one"), written)
        assertEquals(1, result.completed)
        assertEquals(true, result.cancelled)
    }

    @Test
    fun failedItemIsolatedAndLaterItemsRemainUnwritten() {
        val written = mutableListOf<String>()
        val result = MetadataEditorBatchRunner().run(
            items = listOf(item("one"), item("two"), item("three")),
            edit = FileTagEdit(title = "Yeni"),
            isCancelled = { false },
            write = { item, _ ->
                written += item.id
                if (item.id == "two") MetadataEditorItemResult.FAILURE else MetadataEditorItemResult.SUCCESS
            },
        )

        assertEquals(listOf("one", "two", "three"), written)
        assertEquals(2, result.completed)
        assertEquals(1, result.failed)
    }

    private fun item(id: String) = MetadataEditorBatchItem(
        id = id,
        target = null,
        snapshot = FileTagSnapshot(emptyMap(), emptyList()),
    )
}
