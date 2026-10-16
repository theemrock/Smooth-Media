package com.theemrock.smoothmedia.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArtworkCyan = Color(0xFF19C3E6)

@Composable
fun LibraryArtwork(
    request: ArtworkRequest,
    resolver: AndroidArtworkResolver,
    modifier: Modifier,
    contentDescription: String,
    targetPx: Int,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, request.cacheKey, targetPx) {
        value = withContext(Dispatchers.IO) { resolver.load(request, targetPx) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF172A31), Color(0xFF071014))))
            .border(1.dp, ArtworkCyan.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("♪", color = ArtworkCyan, fontSize = 28.sp, style = TextStyle())
        }
    }
}
