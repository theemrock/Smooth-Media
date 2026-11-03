package com.theemrock.smoothmedia.ui

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

/** Asks for a name only; songs are added later through the existing "Playliste ekle" picker. */
@Composable
fun NewPlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Ad") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(enabled = name.trim().isNotEmpty(), onClick = { onCreate(name) }) { Text("Oluştur") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("İptal") } },
    )
}
