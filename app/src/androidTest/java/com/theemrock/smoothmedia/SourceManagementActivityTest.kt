package com.theemrock.smoothmedia

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.metadata.MetadataSessionProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionStatus
import com.theemrock.smoothmedia.source.SourceRegistrationResult
import com.theemrock.smoothmedia.ui.DeviceOrientationPolicy
import com.theemrock.smoothmedia.ui.SourceManagementScreen
import com.theemrock.smoothmedia.ui.sourceRegistrationMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceManagementActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun phoneActivity_appliesPortraitPolicyWhileTabletPolicyIsUnspecified() {
        val smallestWidth = composeRule.activity.resources.configuration.smallestScreenWidthDp

        assertEquals(
            DeviceOrientationPolicy.requestedOrientation(smallestWidth),
            composeRule.activity.requestedOrientation,
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, DeviceOrientationPolicy.requestedOrientation(600))
    }

    @Test
    fun addFolder_launchesOpenDocumentTree() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
            true,
        )
        try {
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.add_folder)).performClick()

            composeRule.waitUntil(timeoutMillis = 3_000) { monitor.hits > 0 }
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun sourceScreen_distinguishesRemovedAndPermissionLostRecords() {
        val removed = source(
            "removed",
            SourceSelectionState.REMOVED,
            SourceAvailability.UNAVAILABLE,
            persistedPermissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val permissionLost = source("lost", SourceSelectionState.ACTIVE, SourceAvailability.PERMISSION_LOST)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = listOf(removed, permissionLost),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.source_removed)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.source_permission_lost)).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.retry_permission_release),
        ).assertIsDisplayed()
    }

    @Test
    fun controlledFailure_mapsToNonSuccessMessage() {
        assertEquals(
            R.string.source_permission_persisted_storage_failed,
            sourceRegistrationMessage(SourceRegistrationResult.PermissionPersistedButStorageFailed),
        )
    }

    @Test
    fun metadataSection_exposesSinglePageControlsWithoutStartingWork() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = emptyList(),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                        metadataMissingCount = 12,
                    )
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.metadata_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.metadata_process_page))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.metadata_missing_count, 12),
        ).assertIsDisplayed()
    }

    @Test
    fun metadataSession_requiresExplicitStart_andShowsTerminalState() {
        var starts = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = emptyList(),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                        metadataMissingCount = 12,
                        onMetadataSessionStart = { starts++ },
                    )
                }
            }
        }

        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.metadata_session_running))
            .assertCountEquals(0)
        assertEquals(0, starts)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.metadata_session_start))
            .performClick()
        assertEquals(1, starts)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = emptyList(),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                        metadataMissingCount = 0,
                        metadataSessionStatus = MetadataSessionStatus.COMPLETED,
                        metadataSessionProgress = MetadataSessionProgress(
                            pages = 2,
                            processed = 12,
                            succeeded = 10,
                            skipped = 1,
                            failed = 1,
                            missingCount = 0,
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.metadata_session_completed))
            .assertIsDisplayed()
    }

    @Test
    fun sourceScreen_exposesSongsAction_withoutStartingScan() {
        var opened = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = emptyList(),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                        onLibrary = { opened++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.open_library))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, opened)
    }

    private fun source(
        id: String,
        selectionState: SourceSelectionState,
        availability: SourceAvailability,
        persistedPermissionFlags: Int = 0,
    ) = LibrarySourceEntity(
        sourceId = id,
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://provider/tree/$id",
        volumeName = null,
        persistedPermissionFlags = persistedPermissionFlags,
        permissionState = if (availability == SourceAvailability.PERMISSION_LOST) {
            SourcePermissionState.LOST
        } else {
            SourcePermissionState.GRANTED
        },
        availability = availability,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
        selectionState = selectionState,
    )
}
