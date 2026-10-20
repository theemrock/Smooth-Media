package com.theemrock.smoothmedia.source

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.theemrock.smoothmedia.BuildConfig

object TreePickerIntentFactory {
    fun create(initialUri: Uri? = null): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (BuildConfig.BUILD_TYPE == "p09test") {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
    }
}
