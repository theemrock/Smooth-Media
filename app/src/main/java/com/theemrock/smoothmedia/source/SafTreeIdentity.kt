package com.theemrock.smoothmedia.source

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class SafTreeIdentity(
    val sourceId: String,
    val authority: String,
    val treeDocumentId: String,
    val canonicalKey: String,
)

sealed interface SafTreeIdentityResult {
    data class Valid(val identity: SafTreeIdentity) : SafTreeIdentityResult

    data object Invalid : SafTreeIdentityResult
}

object SafTreeIdentityFactory {
    private const val VERSION = "saf-tree-v1"

    fun from(authority: String?, treeDocumentId: String?): SafTreeIdentityResult {
        val validAuthority = authority?.takeIf { it.isNotBlank() } ?: return SafTreeIdentityResult.Invalid
        val validDocumentId = treeDocumentId?.takeIf { it.isNotBlank() } ?: return SafTreeIdentityResult.Invalid
        val canonicalKey = "$VERSION\u0000$validAuthority\u0000$validDocumentId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        return SafTreeIdentityResult.Valid(
            SafTreeIdentity(
                sourceId = digest,
                authority = validAuthority,
                treeDocumentId = validDocumentId,
                canonicalKey = canonicalKey,
            ),
        )
    }
}
