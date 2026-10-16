package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.playback.PlaybackQueueState
import com.theemrock.smoothmedia.playback.QueueMode

@Composable
fun QueueScreen(
    state: PlaybackQueueState,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", color = Color.White) }
                Text("QUEUE", color = Color(0xFF19C3E6))
                IconButton(onClick = onPrevious, enabled = state.currentIndex > 0) { Text("|‹") }
                IconButton(onClick = onNext, enabled = state.items.isNotEmpty()) { Text("›|") }
                IconButton(onClick = onShuffle) { Text(if (state.shuffle) "SH" else "sh") }
                IconButton(onClick = onRepeat) { Text(state.repeat.name.take(1)) }
                IconButton(onClick = onClear, enabled = state.items.isNotEmpty()) { Text("CLR") }
            }
            Text(if (state.mode == QueueMode.GHOST) "GHOST CONTEXT · NATURAL ORDER" else "USER QUEUE", color = Color(0xFFAAB9C2))
        }
        if (state.items.isEmpty()) {
            item { Text("Queue is empty", color = Color(0xFFAAB9C2), modifier = Modifier.padding(16.dp)) }
        } else {
            itemsIndexed(state.items, key = { _, item -> item.entryId }) { index, item ->
                Row(Modifier.fillMaxWidth().padding(8.dp).clickable { onSelect(index) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (index == state.currentIndex) "▶" else "·", color = Color(0xFF19C3E6))
                    Text("${if (index == state.currentIndex) "CURRENT · " else "UP NEXT · "}${item.title.ifBlank { item.trackId }} · ${item.artist}", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) { Text("↑") }
                    IconButton(onClick = { onMoveDown(index) }, enabled = index < state.items.lastIndex) { Text("↓") }
                    IconButton(onClick = { onRemove(index) }) { Text("×") }
                }
            }
        }
    }
}
