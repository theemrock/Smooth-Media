package com.theemrock.smoothmedia.catalog.model

data class HashAlgorithmVersion(
    val name: String,
    val version: Int,
) {
    init {
        require(name.isNotBlank())
        require(version > 0)
    }
}

data class FileHash(
    val algorithm: HashAlgorithmVersion,
    val digest: String,
) {
    init {
        require(digest.isNotBlank())
    }
}
