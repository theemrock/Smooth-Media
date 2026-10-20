package com.theemrock.smoothmedia.source

import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreePickerIntentFactoryTest {
    @Test
    fun create_usesOpenDocumentTreeWithReadWritePersistableAndPrefixFlagsForP09test() {
        val intent = TreePickerIntentFactory.create()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
    }

    @Test
    fun create_withInitialTreeCarriesInitialUriExtra() {
        val initial = android.net.Uri.parse("content://provider/tree/primary%3AMusic")

        val intent = TreePickerIntentFactory.create(initial)

        assertEquals(initial, intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI, android.net.Uri::class.java))
    }
}
