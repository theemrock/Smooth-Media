package com.theemrock.smoothmedia.discovery

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSafDocumentTreeReaderTest {
    @Test
    fun selectedTreeRootResolvesToTreeDocumentId() {
        assertEquals(ROOT_DOCUMENT_ID, directoryDocumentId(treeUri()))
    }

    @Test
    fun nestedTreeDocumentResolvesToItsOwnDocumentId() {
        val nestedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri(), NESTED_DOCUMENT_ID)

        assertEquals(NESTED_DOCUMENT_ID, directoryDocumentId(nestedUri))
    }

    @Test
    fun deeplyNestedTreeDocumentResolvesToItsOwnDocumentId() {
        val nestedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri(), DEEPLY_NESTED_DOCUMENT_ID)

        assertEquals(DEEPLY_NESTED_DOCUMENT_ID, directoryDocumentId(nestedUri))
    }

    private fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_DOCUMENT_ID)

    private companion object {
        const val AUTHORITY = "test.provider"
        const val ROOT_DOCUMENT_ID = "primary:Music/Selected"
        const val NESTED_DOCUMENT_ID = "primary:Music/Selected/album"
        const val DEEPLY_NESTED_DOCUMENT_ID = "primary:Music/Selected/album/disc-1/tracks"
    }
}
