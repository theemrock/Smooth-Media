package com.theemrock.smoothmedia

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.scan.ManualScanController
import com.theemrock.smoothmedia.catalog.scan.ManualScanProgress
import com.theemrock.smoothmedia.catalog.scan.ManualScanResult
import com.theemrock.smoothmedia.catalog.scan.ScanSessionOrchestrator
import com.theemrock.smoothmedia.source.AndroidSelectedTreeParser
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import com.theemrock.smoothmedia.source.SourceRegistryRuntime
import com.theemrock.smoothmedia.source.TreePickerIntentFactory
import com.theemrock.smoothmedia.source.selectRefreshSummary
import com.theemrock.smoothmedia.ui.DeviceOrientationPolicy
import com.theemrock.smoothmedia.ui.SourceManagementScreen
import com.theemrock.smoothmedia.ui.sourceRefreshMessage
import com.theemrock.smoothmedia.ui.sourceRegistrationMessage
import com.theemrock.smoothmedia.ui.sourceRemovalMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFF090B0D),
    surface = Color(0xFF111418),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFFF4F6F8),
    onSurface = Color(0xFFF4F6F8),
    outline = Color(0xFFD8DEE3),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFFF5F7F8),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFF111418),
    onSurface = Color(0xFF111418),
    outline = Color(0xFFD8DEE3),
)

class MainActivity : ComponentActivity() {
    private lateinit var runtime: SourceRegistryRuntime
    private lateinit var executor: ExecutorService
    private lateinit var scanController: ManualScanController
    private var sources by mutableStateOf<List<LibrarySourceEntity>>(emptyList())
    private var statusMessage by mutableStateOf<String?>(null)
    private var scanMessage by mutableStateOf<String?>(null)
    private var scanningSourceId by mutableStateOf<String?>(null)

    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val selected = AndroidSelectedTreeParser.fromResult(
            uri = result.data?.data,
            grantedFlags = result.data?.flags ?: 0,
        )
        if (selected == null) {
            statusMessage = getString(R.string.source_invalid_uri)
            return@registerForActivityResult
        }
        submit {
            sourceRegistrationMessage(runtime.registry.register(selected, System.currentTimeMillis()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = DeviceOrientationPolicy.requestedOrientation(
            resources.configuration.smallestScreenWidthDp,
        )
        enableEdgeToEdge()
        runtime = SourceRegistryFactory.create(this)
        executor = Executors.newSingleThreadExecutor()
        scanController = ManualScanController(runtime.database, contentResolver)
        setContent {
            SmoothMediaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SourceManagementScreen(
                        sources = sources,
                        statusMessage = listOfNotNull(statusMessage, scanMessage).joinToString("\n").ifBlank { null },
                        scanningSourceId = scanningSourceId,
                        onAdd = { treePicker.launch(TreePickerIntentFactory.create()) },
                        onRefresh = { refreshSources() },
                        onRemove = { sourceId -> removeSource(sourceId) },
                        onScan = { sourceId -> startScan(sourceId) },
                    )
                }
            }
        }
        loadAndRefreshSources()
    }

    override fun onDestroy() {
        if (::executor.isInitialized && ::runtime.isInitialized) {
            executor.execute { runtime.close() }
            executor.shutdown()
        }
        super.onDestroy()
    }

    private fun loadAndRefreshSources() {
        executor.execute {
            val stored = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(emptyList(), R.string.source_storage_failed)
                return@execute
            }
            val results = stored.filter { it.selectionState == SourceSelectionState.ACTIVE }.map { source ->
                runtime.registry.refresh(source.sourceId, System.currentTimeMillis())
            }
            val refreshed = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(stored, R.string.source_storage_failed)
                return@execute
            }
            postState(refreshed, selectRefreshSummary(results)?.let(::sourceRefreshMessage))
        }
    }

    private fun refreshSources() {
        executor.execute {
            val activeSources = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(sources, R.string.source_storage_failed)
                return@execute
            }.filter { it.selectionState == SourceSelectionState.ACTIVE }
            val results = activeSources.map { source ->
                runtime.registry.refresh(source.sourceId, System.currentTimeMillis())
            }
            val refreshed = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(activeSources, R.string.source_storage_failed)
                return@execute
            }
            postState(refreshed, selectRefreshSummary(results)?.let(::sourceRefreshMessage))
        }
    }

    private fun removeSource(sourceId: String) {
        submit {
            sourceRemovalMessage(runtime.registry.remove(sourceId, System.currentTimeMillis()))
        }
    }

    private fun startScan(sourceId: String) {
        if (scanningSourceId != null) return
        scanningSourceId = sourceId
        scanMessage = getString(R.string.scan_starting)
        executor.execute {
            val source = runCatching { runtime.repository.find(sourceId) }.getOrNull()
            val result = if (source == null) {
                ManualScanResult.Rejected("", ScanSessionOrchestrator.RejectionReason.SOURCE_NOT_SELECTED)
            } else {
                scanController.start(source, ::postScanProgress)
            }
            runOnUiThread {
                scanningSourceId = null
                scanMessage = scanResultMessage(result)
            }
        }
    }

    private fun postScanProgress(progress: ManualScanProgress) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            scanMessage = getString(
                R.string.scan_progress,
                progress.processedCount,
                progress.discoveredCount,
                progress.errorCount,
            )
        }
    }

    private fun scanResultMessage(result: ManualScanResult): String = when (result) {
        is ManualScanResult.Completed -> getString(
            R.string.scan_completed,
            result.processedCount,
            result.successfulCount,
            result.skippedUnreadableCount,
        )
        is ManualScanResult.Paused -> getString(R.string.scan_paused)
        is ManualScanResult.Cancelled -> getString(R.string.scan_cancelled)
        is ManualScanResult.AlreadyActive -> getString(R.string.scan_already_active)
        is ManualScanResult.DiscoveryFailed -> getString(R.string.scan_discovery_failed)
        is ManualScanResult.CandidateFailed -> getString(R.string.scan_candidate_failed, result.reason.name)
        is ManualScanResult.Rejected -> getString(R.string.scan_rejected)
    }

    private fun submit(operation: () -> Int) {
        executor.execute {
            val message = runCatching(operation).getOrElse { R.string.source_storage_failed }
            val updatedSources = runCatching { runtime.repository.listAll() }.getOrElse { sources }
            postState(updatedSources, message)
        }
    }

    private fun postState(updatedSources: List<LibrarySourceEntity>, messageRes: Int?) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            sources = updatedSources
            statusMessage = messageRes?.let(::getString)
        }
    }
}

@Composable
private fun SmoothMediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
