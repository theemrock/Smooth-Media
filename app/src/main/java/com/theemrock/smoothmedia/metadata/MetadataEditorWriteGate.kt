package com.theemrock.smoothmedia.metadata

enum class MetadataEditorWriteDecision {
    ALLOWED,
    BLOCKED,
}

/** Production physical-file writes remain disabled until real SAF artwork verification is complete. */
class MetadataEditorWriteGate(
    private val physicalWritesEnabled: Boolean = false,
) {
    fun decision(): MetadataEditorWriteDecision =
        if (physicalWritesEnabled) MetadataEditorWriteDecision.ALLOWED
        else MetadataEditorWriteDecision.BLOCKED
}
