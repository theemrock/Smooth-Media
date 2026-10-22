package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.metadata.MetadataEditorField
import com.theemrock.smoothmedia.metadata.MetadataEditorState

@Composable
fun MetadataEditorScreen(
    state: MetadataEditorState,
    affectedFileCount: Int,
    saving: Boolean,
    progress: String? = null,
    error: String? = null,
    writingEnabled: Boolean = false,
    onChange: (MetadataEditorState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val fields = listOf(
        MetadataEditorField.TITLE to "Title",
        MetadataEditorField.ARTIST to "Artist",
        MetadataEditorField.ALBUM to "Album",
        MetadataEditorField.ALBUM_ARTIST to "Album Artist",
        MetadataEditorField.GENRE to "Genre (virgülle çoklu)",
        MetadataEditorField.TRACK_NUMBER to "Track",
        MetadataEditorField.DISC_NUMBER to "Disc",
        MetadataEditorField.YEAR to "Date",
        MetadataEditorField.COMMENT to "Comment",
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Metadata Editor", color = Color.White)
        Text("${state.selectedTrackIds.size} kayıt · $affectedFileCount dosya", color = Color(0xFFAAB9C2))
        Text("Boş bırakılan alanlar korunur. Silme ayrı bir eylemdir.", color = Color(0xFFAAB9C2))
        fields.forEach { (field, label) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = state.values[field].orEmpty(),
                    onValueChange = { onChange(state.set(field, it)) },
                    label = { Text(label) },
                    placeholder = {
                        Text(
                            when {
                                field in state.mixed -> "Farklı değerler"
                                field in state.missing -> "Eksik metadata"
                                else -> "Karma/değiştirme yok"
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                )
                TextButton(
                    onClick = { onChange(state.clear(field)) },
                    enabled = !saving,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Sil") }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8F8F)) }
        progress?.let { Text(it, color = Color(0xFF19C3E6)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(if (saving) "İptal" else "Vazgeç") }
            Button(onClick = { confirm = true }, enabled = !saving && writingEnabled && state.isDirty) {
                Text("Kaydet")
            }
            if (saving) CircularProgressIndicator(color = Color(0xFF19C3E6))
        }
        if (!writingEnabled) {
            Text("Fiziksel metadata yazımı şu anda kilitli.", color = Color(0xFFFFC107))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Dosyalara yazılsın mı?") },
            text = { Text("${affectedFileCount} dosyada seçilen alanlar değiştirilecek. Yazma sonrası read-back ve hash doğrulaması yapılır.") },
            confirmButton = { TextButton(onClick = { confirm = false; onSave() }) { Text("Onayla") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("İptal") } },
        )
    }
}
