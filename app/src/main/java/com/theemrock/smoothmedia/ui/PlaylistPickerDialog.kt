package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntity

@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistEntity) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playliste ekle") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(playlists, key = { it.playlistId }) { playlist ->
                        OutlinedButton(onClick = { onSelect(playlist) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(playlist.name) }
                    }
                }
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Yeni playlist") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = newName.trim().isNotEmpty(), onClick = { onCreate(newName) }) { Text("Oluştur ve ekle") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("İptal") } },
    )
}
