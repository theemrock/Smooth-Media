package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryDashboardData
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState

private val DashboardInk = Color(0xFF080B0D)
private val DashboardPanel = Color(0xFF10161A)
private val DashboardLine = Color(0xFF26343B)
private val DashboardMuted = Color(0xFFAAB9C2)
private val DashboardCyan = Color(0xFF19C3E6)

@Composable
fun LibraryDashboardScreen(
    data: LibraryDashboardData,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onCollectionSelected: (LibraryCollectionType, LibraryCollectionRow) -> Unit,
    onSearch: () -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit = {},
    onSong: (LibrarySong) -> Unit,
    onAddToQueue: (LibrarySong) -> Unit,
    onPlayNext: (LibrarySong) -> Unit,
    playbackState: PlaybackEngineState? = null,
    miniPlayerSong: LibrarySong? = null,
    onMiniPlayerOpen: () -> Unit = {},
    onMiniPlayerPause: () -> Unit = {},
    onMiniPlayerResume: () -> Unit = {},
    onMiniPlayerNext: () -> Unit = {},
    artworkResolver: AndroidArtworkResolver? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = DashboardInk,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                DashboardTopBar(
                    menuExpanded = menuExpanded,
                    onMenuChange = { menuExpanded = it },
                    onSearch = onSearch,
                    onSongs = onSongs,
                    onCollection = onCollection,
                    onSources = onSources,
                    onSettings = onSettings,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item {
                        CategoryStrip(data, onSongs, onCollection)
                    }
                    item {
                        DashboardSection(
                            title = "Şarkılar",
                            action = "Tümünü gör",
                            onAction = onSongs,
                        ) {
                            DashboardSongRow(
                                songs = data.songs,
                                artworkResolver = artworkResolver,
                                onSong = onSong,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                            )
                        }
                    }
                    item {
                        DashboardCollectionSection(
                            title = "Albümler",
                            action = "Tümünü gör",
                            rows = data.albums,
                            type = LibraryCollectionType.ALBUMS,
                            onAction = { onCollection(LibraryCollectionType.ALBUMS) },
                            onSelected = onCollectionSelected,
                            artworkResolver = artworkResolver,
                        )
                    }
                    item {
                        DashboardCollectionSection(
                            title = "Sanatçılar",
                            action = "Tümünü gör",
                            rows = data.artists,
                            type = LibraryCollectionType.ARTISTS,
                            onAction = { onCollection(LibraryCollectionType.ARTISTS) },
                            onSelected = onCollectionSelected,
                            artworkResolver = artworkResolver,
                        )
                    }
                    item {
                        DashboardCollectionSection(
                            title = "Türlere göz at",
                            action = "Tümünü gör",
                            rows = data.genres,
                            type = LibraryCollectionType.GENRES,
                            onAction = { onCollection(LibraryCollectionType.GENRES) },
                            onSelected = onCollectionSelected,
                            artworkResolver = artworkResolver,
                        )
                    }
                }
                if (playbackState != null && miniPlayerSong != null) {
                    MiniPlayer(
                        song = miniPlayerSong,
                        state = playbackState,
                        onOpen = onMiniPlayerOpen,
                        onPause = onMiniPlayerPause,
                        onResume = onMiniPlayerResume,
                        onNext = onMiniPlayerNext,
                        artworkResolver = artworkResolver,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            IconButton(onClick = { onMenuChange(!menuExpanded) }) {
                Text("☰", color = Color.White, fontSize = 25.sp)
            }
            if (menuExpanded) {
                DashboardMenu(
                    onDismiss = { onMenuChange(false) },
                    onSongs = onSongs,
                    onCollection = onCollection,
                    onSearch = onSearch,
                    onSources = onSources,
                    onSettings = onSettings,
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(30.dp))
                .border(1.dp, DashboardLine, RoundedCornerShape(30.dp))
                .clickable(onClick = onSearch),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", color = Color.White, fontSize = 30.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Kütüphanede ara…", color = DashboardMuted, fontSize = 16.sp)
            }
        }
        Text("⋮", color = Color.White, fontSize = 28.sp)
    }
}

@Composable
private fun DashboardMenu(
    onDismiss: () -> Unit,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onSearch: () -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(220.dp).background(DashboardPanel),
    ) {
        DashboardMenuItem(CategoryIconKind.LIBRARY, "Kütüphane", selected = true, onClick = onDismiss)
        DashboardMenuItem(CategoryIconKind.SONGS, "Tüm şarkılar", onClick = { onDismiss(); onSongs() })
        DashboardMenuItem(CategoryIconKind.ALBUMS, "Albümler", onClick = { onDismiss(); onCollection(LibraryCollectionType.ALBUMS) })
        DashboardMenuItem(CategoryIconKind.ARTISTS, "Sanatçılar", onClick = { onDismiss(); onCollection(LibraryCollectionType.ARTISTS) })
        DashboardMenuItem(CategoryIconKind.GENRES, "Türler", onClick = { onDismiss(); onCollection(LibraryCollectionType.GENRES) })
        DashboardMenuItem(CategoryIconKind.FOLDERS, "Klasörler", onClick = { onDismiss(); onCollection(LibraryCollectionType.FOLDERS) })
        HorizontalDivider(color = DashboardLine)
        DashboardMenuItem(CategoryIconKind.SETTINGS, "Ayarlar", onClick = { onDismiss(); onSettings() })
    }
}

@Composable
private fun DashboardMenuItem(icon: CategoryIconKind, label: String, selected: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { CategoryIcon(icon, color = if (selected) DashboardCyan else Color.White) },
        text = { Text(label, color = if (selected) DashboardCyan else Color.White, fontSize = 15.sp) },
        modifier = Modifier.padding(horizontal = 4.dp),
        onClick = onClick,
    )
}

@Composable
private fun CategoryStrip(
    data: LibraryDashboardData,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = dashboardCategoryWidthDp(maxWidth.value).dp
        Row(
            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DashboardCategory(CategoryIconKind.SONGS, "Şarkılar", data.totalTracks, onSongs, cardWidth)
            DashboardCategory(CategoryIconKind.ALBUMS, "Albümler", data.albumCount, { onCollection(LibraryCollectionType.ALBUMS) }, cardWidth)
            DashboardCategory(CategoryIconKind.ARTISTS, "Sanatçılar", data.artistCount, { onCollection(LibraryCollectionType.ARTISTS) }, cardWidth)
            DashboardCategory(CategoryIconKind.GENRES, "Türler", data.genreCount, { onCollection(LibraryCollectionType.GENRES) }, cardWidth)
            DashboardCategory(CategoryIconKind.FOLDERS, "Klasörler", data.folderCount, { onCollection(LibraryCollectionType.FOLDERS) }, cardWidth)
        }
    }
}

private enum class CategoryIconKind { LIBRARY, SONGS, ALBUMS, ARTISTS, GENRES, FOLDERS, SETTINGS }

@Composable
private fun DashboardCategory(
    iconKind: CategoryIconKind,
    label: String,
    count: Int?,
    onClick: (() -> Unit)?,
    cardWidth: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (onClick != null) DashboardCyan else DashboardLine, RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(DashboardPanel, DashboardInk)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CategoryIcon(iconKind, color = if (onClick != null) DashboardCyan else DashboardMuted)
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(count?.toString() ?: "—", color = DashboardMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CategoryIcon(kind: CategoryIconKind, color: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val ink = DashboardInk
        when (kind) {
            CategoryIconKind.LIBRARY -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 15.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(10.dp.toPx(), 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 17.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(17.dp.toPx(), 7.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 14.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
            CategoryIconKind.SONGS -> {
                drawCircle(color, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(7.dp.toPx(), 19.dp.toPx()))
                drawLine(color, androidx.compose.ui.geometry.Offset(10.dp.toPx(), 19.dp.toPx()), androidx.compose.ui.geometry.Offset(10.dp.toPx(), 5.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawLine(color, androidx.compose.ui.geometry.Offset(10.dp.toPx(), 5.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx(), 8.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawCircle(color, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(17.dp.toPx(), 21.dp.toPx()))
                drawLine(color, androidx.compose.ui.geometry.Offset(20.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx(), 21.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            CategoryIconKind.ALBUMS -> {
                drawCircle(color, radius = 10.dp.toPx(), center = androidx.compose.ui.geometry.Offset(13.dp.toPx(), 13.dp.toPx()))
                drawCircle(ink, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(13.dp.toPx(), 13.dp.toPx()))
            }
            CategoryIconKind.ARTISTS -> {
                drawCircle(color, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(9.dp.toPx(), 8.dp.toPx()))
                drawCircle(color, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(18.dp.toPx(), 8.dp.toPx()))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 13.dp.toPx()), size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 9.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(12.dp.toPx(), 13.dp.toPx()), size = androidx.compose.ui.geometry.Size(11.dp.toPx(), 9.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
            CategoryIconKind.GENRES -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 7.dp.toPx()), size = androidx.compose.ui.geometry.Size(13.dp.toPx(), 10.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(10.dp.toPx(), 10.dp.toPx()), size = androidx.compose.ui.geometry.Size(13.dp.toPx(), 10.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
            }
            CategoryIconKind.FOLDERS -> {
                val path = Path().apply {
                    moveTo(3.dp.toPx(), 7.dp.toPx())
                    lineTo(10.dp.toPx(), 7.dp.toPx())
                    lineTo(12.dp.toPx(), 10.dp.toPx())
                    lineTo(23.dp.toPx(), 10.dp.toPx())
                    lineTo(21.dp.toPx(), 20.dp.toPx())
                    lineTo(4.dp.toPx(), 20.dp.toPx())
                    close()
                }
                drawPath(path, color)
            }
            CategoryIconKind.SETTINGS -> {
                val center = androidx.compose.ui.geometry.Offset(13.dp.toPx(), 13.dp.toPx())
                drawCircle(color, radius = 8.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
                drawCircle(color, radius = 2.5.dp.toPx(), center = center)
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val start = androidx.compose.ui.geometry.Offset(
                        (13f + kotlin.math.cos(angle).toFloat() * 9f).dp.toPx(),
                        (13f + kotlin.math.sin(angle).toFloat() * 9f).dp.toPx(),
                    )
                    val end = androidx.compose.ui.geometry.Offset(
                        (13f + kotlin.math.cos(angle).toFloat() * 12f).dp.toPx(),
                        (13f + kotlin.math.sin(angle).toFloat() * 12f).dp.toPx(),
                    )
                    drawLine(color, start, end, strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(title: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(action, color = DashboardCyan, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction))
            Text("›", color = DashboardCyan, fontSize = 22.sp)
        }
        content()
    }
}

@Composable
private fun DashboardSongRow(
    songs: List<LibrarySong>,
    artworkResolver: AndroidArtworkResolver?,
    onSong: (LibrarySong) -> Unit,
    onPlayNext: (LibrarySong) -> Unit,
    onAddToQueue: (LibrarySong) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = dashboardMediaCardWidthDp(maxWidth.value).dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(songs, key = { it.trackId }) { song ->
                DashboardSongCard(
                    song = song,
                    cardWidth = cardWidth,
                    artworkResolver = artworkResolver,
                    onClick = { onSong(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                )
            }
        }
    }
}

@Composable
private fun DashboardSongCard(
    song: LibrarySong,
    cardWidth: androidx.compose.ui.unit.Dp,
    artworkResolver: AndroidArtworkResolver?,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(cardWidth).clickable(enabled = song.isAccessible, onClick = onClick)) {
        Box(modifier = Modifier.size(cardWidth).clip(RoundedCornerShape(8.dp))) {
            if (artworkResolver != null) {
                LibraryArtwork(
                    request = ArtworkRequest.Track(song.trackId),
                    resolver = artworkResolver,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = "Artwork: ${song.title}",
                    targetPx = 240,
                )
            } else {
                Text("♫", color = DashboardCyan, fontSize = 42.sp, modifier = Modifier.align(Alignment.Center))
            }
            Text("▶", color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                Text(song.artist, color = DashboardMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                Text("⋮", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { actionsExpanded = true }.padding(horizontal = 3.dp))
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }, modifier = Modifier.width(180.dp)) {
                    DropdownMenuItem(text = { Text("Sonraki çal") }, onClick = { actionsExpanded = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Kuyruğa ekle") }, onClick = { actionsExpanded = false; onAddToQueue() })
                }
            }
        }
    }
}

@Composable
private fun DashboardCollectionSection(
    title: String,
    action: String,
    rows: List<LibraryCollectionRow>,
    type: LibraryCollectionType,
    onAction: () -> Unit,
    onSelected: (LibraryCollectionType, LibraryCollectionRow) -> Unit,
    artworkResolver: AndroidArtworkResolver?,
) {
    DashboardSection(title, action, onAction) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = dashboardMediaCardWidthDp(maxWidth.value).dp
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    Column(modifier = Modifier.width(cardWidth).clickable { onSelected(type, row) }) {
                    if (type == LibraryCollectionType.ALBUMS && artworkResolver != null) {
                        LibraryArtwork(
                            request = ArtworkRequest.Album(row.title, row.subtitle.orEmpty()),
                            resolver = artworkResolver,
                            modifier = Modifier.size(cardWidth).clip(RoundedCornerShape(8.dp)),
                            contentDescription = "Artwork: ${row.title}",
                            targetPx = 240,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(cardWidth).clip(RoundedCornerShape(8.dp)).background(DashboardPanel),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (type == LibraryCollectionType.ARTISTS) "●" else "◇", color = DashboardCyan, fontSize = 42.sp) }
                    }
                        Text(row.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        Text("${row.trackCount} şarkı", color = DashboardMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
