package com.theemrock.smoothmedia.metadata

import java.util.concurrent.atomic.AtomicBoolean


/**
 * Runs explicitly requested metadata pages until the missing set is exhausted or the caller stops.
 * Cursor and progress are intentionally held only for the lifetime of this call.
 */
class MetadataContinuousSessionCoordinator(
    private val page: MetadataMissingTrackPageCoordinator,
    private val countMissing: () -> Int,
) {
    fun runSession(
        cancellationRequested: () -> Boolean = { false },
        onPage: (MetadataPageResult) -> Unit = {},
        onProgress: (MetadataSessionProgress) -> Unit = {},
    ): MetadataContinuousSessionResult {
        var cursor: String? = null
        var progress = MetadataSessionProgress.empty()
        val cursors = mutableSetOf<String?>()

        while (true) {
            if (cancellationRequested()) {
                return MetadataContinuousSessionResult(MetadataSessionStatus.STOPPED, progress)
            }

            val pageResult = try {
                page.runPage(
                    afterTrackId = cursor,
                    cancellationRequested = cancellationRequested,
                )
            } catch (error: Throwable) {
                return MetadataContinuousSessionResult(
                    status = MetadataSessionStatus.ERROR,
                    progress = progress,
                    errorMessage = error.message ?: error::class.simpleName,
                )
            }

            val batchProgress = pageResult.batch.progress
            val missingCount = if (batchProgress.total == 0) {
                progress.missingCount
            } else {
                try {
                    countMissingAfterPage()
                } catch (error: Throwable) {
                    return MetadataContinuousSessionResult(
                        status = MetadataSessionStatus.ERROR,
                        progress = progress.plus(batchProgress, progress.missingCount),
                        errorMessage = error.message ?: error::class.simpleName,
                    )
                }
            }
            progress = progress.plus(batchProgress, missingCount)
            onPage(pageResult)
            onProgress(progress)

            if (batchProgress.cancelled || cancellationRequested()) {
                return MetadataContinuousSessionResult(MetadataSessionStatus.STOPPED, progress)
            }
            if (batchProgress.total == 0) {
                return MetadataContinuousSessionResult(MetadataSessionStatus.COMPLETED, progress)
            }

            val nextCursor = pageResult.nextCursor
            if (nextCursor == null || !cursors.add(nextCursor)) {
                return MetadataContinuousSessionResult(
                    status = MetadataSessionStatus.ERROR,
                    progress = progress,
                    errorMessage = "Metadata page cursor did not advance",
                )
            }
            cursor = nextCursor
        }
    }

    private fun countMissingAfterPage(): Int = countMissing()
}

enum class MetadataSessionStatus {
    COMPLETED,
    STOPPED,
    ERROR,
}

data class MetadataSessionProgress(
    val pages: Int,
    val processed: Int,
    val succeeded: Int,
    val skipped: Int,
    val failed: Int,
    val missingCount: Int,
) {
    companion object {
        fun empty() = MetadataSessionProgress(0, 0, 0, 0, 0, 0)
    }

    fun plus(page: MetadataBatchProgress, missingCount: Int) = copy(
        pages = pages + if (page.total == 0) 0 else 1,
        processed = processed + page.processed,
        succeeded = succeeded + page.succeeded,
        skipped = skipped + page.skipped,
        failed = failed + page.failed,
        missingCount = missingCount,
    )
}

data class MetadataContinuousSessionResult(
    val status: MetadataSessionStatus,
    val progress: MetadataSessionProgress,
    val errorMessage: String? = null,
)

class MetadataSessionLaunchGate {
    private val acquired = AtomicBoolean(false)

    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)

    fun release() {
        acquired.set(false)
    }
}
