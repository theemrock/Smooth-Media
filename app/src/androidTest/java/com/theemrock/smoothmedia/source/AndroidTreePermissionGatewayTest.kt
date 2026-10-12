package com.theemrock.smoothmedia.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun selectedTree(grantedFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION) = SelectedTree(
        rootUri = "content://com.android.externalstorage.documents/tree/primary%3ASmoothMediaMissingTestRoot",
        authority = "com.android.externalstorage.documents",
        treeDocumentId = "primary:SmoothMediaMissingTestRoot",
        grantedFlags = grantedFlags,
    )
}
