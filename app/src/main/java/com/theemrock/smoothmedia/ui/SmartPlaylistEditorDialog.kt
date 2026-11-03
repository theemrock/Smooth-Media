package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.theemrock.smoothmedia.library.SmartRuleDraft
import com.theemrock.smoothmedia.library.SmartRuleDrafts
import com.theemrock.smoothmedia.library.SmartRuleKind

/** Editor state held by the activity: [id] null means a new playlist. */
data class SmartEditorState(
    val id: String?,
    val name: String,
    val drafts: List<SmartRuleDraft>,
    val notEditable: Boolean,
)

/**
 * Smart playlist editor. [notEditable] means the saved rule has a shape this editor cannot show;
 * saving then replaces that rule with the rows shown here.
 */
@Composable
fun SmartPlaylistEditorDialog(
    initialName: String,
    initialDrafts: List<SmartRuleDraft>,
    notEditable: Boolean,
    canDelete: Boolean,
    onSave: (name: String, drafts: List<SmartRuleDraft>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var drafts by remember { mutableStateOf(initialDrafts.ifEmpty { listOf(SmartRuleDraft(SmartRuleKind.GENRE, "")) }) }
    val canSave = name.isNotBlank() && SmartRuleDrafts.toRule(drafts) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Akıllı liste") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (notEditable) {
                    Text(
                        "Bu listenin kuralı düzenleyicide gösterilemiyor. Kaydederseniz aşağıdaki satırlarla değişir.",
                        color = Color(0xFFE0A030),
                    )
                }
                drafts.forEachIndexed { index, draft ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            drafts = drafts.toMutableList().also { it[index] = draft.copy(kind = nextKind(draft.kind)) }
                        }) { Text(kindLabel(draft.kind)) }
                        OutlinedTextField(
                            value = draft.value,
                            onValueChange = { text ->
                                drafts = drafts.toMutableList().also { it[index] = draft.copy(value = text) }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                        TextButton(onClick = { drafts = drafts.toMutableList().also { it.removeAt(index) } }) { Text("Sil") }
                    }
                }
                TextButton(onClick = { drafts = drafts + SmartRuleDraft(SmartRuleKind.ARTIST, "") }) {
                    Text("Kural ekle (hepsi birlikte sağlanmalı)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), drafts) }, enabled = canSave) { Text("Kaydet") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canDelete) TextButton(onClick = onDelete) { Text("Listeyi sil") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

private fun kindLabel(kind: SmartRuleKind): String = when (kind) {
    SmartRuleKind.GENRE -> "Tür"
    SmartRuleKind.ARTIST -> "Sanatçı"
    SmartRuleKind.ALBUM -> "Albüm"
    SmartRuleKind.TITLE -> "Başlık içerir"
}

private fun nextKind(kind: SmartRuleKind): SmartRuleKind =
    SmartRuleKind.entries[(kind.ordinal + 1) % SmartRuleKind.entries.size]
