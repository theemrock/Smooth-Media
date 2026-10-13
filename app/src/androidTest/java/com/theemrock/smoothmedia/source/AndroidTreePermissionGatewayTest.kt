package com.theemrock.smoothmedia.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTreePermissionGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = AndroidTreePermissionGateway(context.contentResolver)

    @Test
    fun selectedTreeParser_decodesProviderTreeIdentity() {
        val selected = AndroidSelectedTreeParser.fromResult(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )

        assertEquals("com.android.externalstorage.documents", selected!!.authority)
        assertEquals("primary:Music", selected.treeDocumentId)
    }

    @Test
    fun selectedTreeParser_rejectsNonContentAndNonTreeUris() {
        assertNull(AndroidSelectedTreeParser.fromResult(Uri.parse("file:///storage/emulated/0/Music"), 1))
        assertNull(AndroidSelectedTreeParser.fromResult(Uri.parse("content://provider/document/file"), 1))
    }

    @Test
    fun takeWithoutReturnedReadGrant_isPermissionDenied() {
        val selected = selectedTree(grantedFlags = 0)

        assertEquals(TreePermissionTakeResult.PermissionDenied, gateway.takeAndVerify(selected))
    }

    @Test
    fun takeReadGrantThatWasNotOfferedByPlatform_isPermissionDenied() {
        val selected = selectedTree(grantedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION)

        assertEquals(TreePermissionTakeResult.PermissionDenied, gateway.takeAndVerify(selected))
    }

    @Test
    fun inspectTreeWithoutPersistedGrant_isPermissionLost() {
        assertEquals(TreePermissionInspection.PermissionLost, gateway.inspect(selectedTree().rootUri))
    }

    @Test
    fun releaseTreeWithoutPersistedGrant_isIdempotent() {
        assertEquals(
            TreePermissionReleaseResult.AlreadyReleased,
            gateway.release(selectedTree().rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    @Test
    fun grantedLifecycle_verifiesTake_tracksRecovery_andReleasesEquivalentUris() {
        val operations = FakeOperations()
        val lifecycleGateway = AndroidTreePermissionGateway(operations)
        val upper = selectedTree()
        val lower = upper.copy(rootUri = upper.rootUri.replace("%3A", "%3a"))

        operations.probeResult = TreeProbeResult.UNAVAILABLE
        assertTrue(lifecycleGateway.takeAndVerify(upper) is TreePermissionTakeResult.Unavailable)
        assertEquals(TreePermissionInspection.Unavailable(Intent.FLAG_GRANT_READ_URI_PERMISSION), lifecycleGateway.inspect(upper.rootUri))

        operations.probeResult = TreeProbeResult.AVAILABLE
        assertEquals(TreePermissionTakeResult.Granted(Intent.FLAG_GRANT_READ_URI_PERMISSION), lifecycleGateway.takeAndVerify(lower))
        assertEquals(TreePermissionInspection.Available(Intent.FLAG_GRANT_READ_URI_PERMISSION), lifecycleGateway.inspect(lower.rootUri))
        assertEquals(2, operations.heldPermissions.size)

        assertEquals(
            TreePermissionReleaseResult.Released,
            lifecycleGateway.release(lower.rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        assertTrue(operations.heldPermissions.isEmpty())
        assertEquals(2, operations.releasedUris.size)
    }

    @Test
    fun permissionEnumerationFailures_areControlledForTakeInspectAndRelease() {
        val operations = FakeOperations().apply { failEnumeration = true }
        val failingGateway = AndroidTreePermissionGateway(operations)

        assertEquals(TreePermissionTakeResult.Failed, failingGateway.takeAndVerify(selectedTree()))
        assertEquals(TreePermissionInspection.Failed, failingGateway.inspect(selectedTree().rootUri))
        assertEquals(TreePermissionReleaseResult.Failed, failingGateway.release(selectedTree().rootUri, 1))
    }

    private fun selectedTree(grantedFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION) = SelectedTree(
        rootUri = "content://com.android.externalstorage.documents/tree/primary%3ASmoothMediaMissingTestRoot",
        authority = "com.android.externalstorage.documents",
        treeDocumentId = "primary:SmoothMediaMissingTestRoot",
        grantedFlags = grantedFlags,
    )

    private class FakeOperations : AndroidTreePermissionOperations {
        val heldPermissions = mutableListOf<PersistedTreePermission>()
        val releasedUris = mutableListOf<Uri>()
        var probeResult = TreeProbeResult.AVAILABLE
        var failEnumeration = false

        override fun take(uri: Uri, flags: Int) {
            heldPermissions.removeAll { it.uri == uri }
            heldPermissions += PersistedTreePermission(uri, isReadPermission = true)
        }

        override fun persistedPermissions(): List<PersistedTreePermission> {
            if (failEnumeration) error("enumeration failed")
            return heldPermissions.toList()
        }

        override fun release(uri: Uri, flags: Int) {
            releasedUris += uri
            heldPermissions.removeAll { it.uri == uri }
        }

        override fun probe(treeUri: Uri): TreeProbeResult = probeResult
    }
}
