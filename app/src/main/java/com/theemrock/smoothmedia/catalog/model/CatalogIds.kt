package com.theemrock.smoothmedia.catalog.model

@JvmInline
value class TrackId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class TrackLocationId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class FileLocator(val value: String) {
    init {
        require(value.isNotBlank())
    }
}
