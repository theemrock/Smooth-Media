package com.theemrock.smoothmedia.metadata

enum class MetadataEditorWriteDecision {
    ALLOWED,
    BLOCKED,
}

/** Production physical-file writes remain disabled until real SAF artwork verification is complete. */
class MetadataEditorWriteGate(
    private val physicalWritesEnabled: Boolean = false,
) {
    companion object {
        /** The application composition root opts into physical ID3 writes explicitly. */
        fun production(): MetadataEditorWriteGate = MetadataEditorWriteGate(physicalWritesEnabled = true)
    }

    fun decision(): MetadataEditorWriteDecision =
        if (physicalWritesEnabled) MetadataEditorWriteDecision.ALLOWED
        else MetadataEditorWriteDecision.BLOCKED
}
