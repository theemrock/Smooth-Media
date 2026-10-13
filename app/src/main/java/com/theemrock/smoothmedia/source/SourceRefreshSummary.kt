package com.theemrock.smoothmedia.source

/** Keeps a meaningful problem visible when several source refreshes complete together. */
fun selectRefreshSummary(results: List<SourceRefreshResult>): SourceRefreshResult? =
    results.firstOrNull {
        it == SourceRefreshResult.StorageFailure ||
            it == SourceRefreshResult.PermissionCheckFailed ||
            it == SourceRefreshResult.InvalidUri ||
            it == SourceRefreshResult.NotFound
    }
        ?: results.firstOrNull { it is SourceRefreshResult.PermissionLost }
        ?: results.firstOrNull { it is SourceRefreshResult.Unavailable }
        ?: results.lastOrNull()
