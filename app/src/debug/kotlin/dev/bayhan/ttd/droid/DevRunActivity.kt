package dev.bayhan.ttd.droid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import dev.bayhan.ttd.droid.config.AppConfig
import dev.bayhan.ttd.droid.store.TaskStore
import java.io.File

class DevRunActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (!isExpectedTree(uri) || !hasRequiredFlags(intent.flags)) {
            finish()
            return
        }

        TaskStore(this).setRoot(uri!!)
        val persisted = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!persisted) {
            finish()
            return
        }

        AppConfig.setTaskDirUri(this, uri)
        File(filesDir, READY_FILE).writeText(uri.toString())
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isExpectedTree(uri: Uri?): Boolean {
        return uri?.scheme == "content" &&
            uri.authority == "com.android.externalstorage.documents" &&
            DocumentsContract.isTreeUri(uri) &&
            DocumentsContract.getTreeDocumentId(uri) == EXPECTED_DOCUMENT_ID
    }

    private fun hasRequiredFlags(flags: Int): Boolean =
        flags and REQUIRED_FLAGS == REQUIRED_FLAGS

    companion object {
        private const val EXPECTED_DOCUMENT_ID = "primary:Documents/ttd-dev/todo.txt.d"
        private const val READY_FILE = "dev-run-ready"
        private const val REQUIRED_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
