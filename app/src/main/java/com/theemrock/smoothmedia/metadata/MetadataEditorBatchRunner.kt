package com.theemrock.smoothmedia.metadata

data class MetadataEditorBatchItem(
    val id: String,
    val target: MetadataFileWriteTarget?,
    val snapshot: FileTagSnapshot,
)

enum class MetadataEditorItemResult { SUCCESS, FAILURE }

data class MetadataEditorBatchResult(
    val completed: Int,
    val failed: Int,
    val cancelled: Boolean,
    val blocked: Boolean = false,
)

class MetadataEditorBatchRunner(
    private val maxItems: Int = 100,
) {
    init { require(maxItems > 0) }

    fun run(
        items: List<MetadataEditorBatchItem>,
        edit: FileTagEdit,
        isCancelled: () -> Boolean,
        write: (MetadataEditorBatchItem, FileTagEdit) -> MetadataEditorItemResult,
    ): MetadataEditorBatchResult {
        var completed = 0
        var failed = 0
        var cancelled = false
        items.take(maxItems).forEach { item ->
            if (isCancelled()) {
                cancelled = true
                return@forEach
            }
            when (write(item, edit)) {
                MetadataEditorItemResult.SUCCESS -> completed++
                MetadataEditorItemResult.FAILURE -> failed++
            }
        }
        if (isCancelled()) cancelled = true
        return MetadataEditorBatchResult(completed, failed, cancelled)
    }
}
