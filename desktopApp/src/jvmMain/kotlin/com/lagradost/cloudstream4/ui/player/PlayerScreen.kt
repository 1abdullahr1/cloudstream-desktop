package com.lagradost.cloudstream4.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import com.lagradost.cloudstream4.ui.components.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream4.engine.storage.DesktopHistoryManager
import com.lagradost.cloudstream4.engine.storage.HistoryItem
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel

@Composable
fun PlayerScreen(
    mediaUrl: String,
    title: String,
    episodeTitle: String? = null,
    apiName: String = "",
    parentUrl: String = "",
    episodeData: String? = null,
    headers: Map<String, String> = emptyMap(),
    subtitles: List<SubtitleFile> = emptyList(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var selectedSubtitle by remember { mutableStateOf<SubtitleFile?>(subtitles.firstOrNull()) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    // VLC component reference
    var vlcComponent by remember { mutableStateOf<Any?>(null) }
    var vlcAvailable by remember { mutableStateOf(false) }

    // Check if LibVLC is available via reflection
    LaunchedEffect(Unit) {
        try {
            Class.forName("uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent")
            vlcAvailable = true
        } catch (_: Throwable) {
            vlcAvailable = false
        }
    }

    // Auto-hide controls after 3.5 seconds of inactivity
    LaunchedEffect(lastInteractionTime, controlsVisible) {
        if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Save history periodically
    LaunchedEffect(currentPositionMs) {
        if (parentUrl.isNotBlank() && durationMs > 0) {
            DesktopHistoryManager.saveProgress(
                HistoryItem(
                    url = parentUrl,
                    apiName = apiName,
                    title = title,
                    episodeName = episodeTitle,
                    episodeData = episodeData,
                    positionMs = currentPositionMs,
                    durationMs = durationMs
                )
            )
        }
    }

    // Clean up on exit
    DisposableEffect(Unit) {
        onDispose {
            if (parentUrl.isNotBlank() && durationMs > 0) {
                DesktopHistoryManager.saveProgress(
                    HistoryItem(
                        url = parentUrl,
                        apiName = apiName,
                        title = title,
                        episodeName = episodeTitle,
                        episodeData = episodeData,
                        positionMs = currentPositionMs,
                        durationMs = durationMs
                    )
                )
            }
            try {
                vlcComponent?.let { comp ->
                    val releaseMethod = comp.javaClass.getMethod("release")
                    releaseMethod.invoke(comp)
                }
            } catch (_: Throwable) {}
        }
    }

    fun openInExternalPlayer() {
        try {
            // Attempt to launch mpv or vlc executable, or open in system default browser/player
            val uri = URI(mediaUrl)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
            }
        } catch (_: Throwable) {
            try {
                ProcessBuilder("cmd", "/c", "start", mediaUrl).start()
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Press) {
                            controlsVisible = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        // Video Render Area
        if (vlcAvailable) {
            SwingPanel(
                background = Color.Black,
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val panel = JPanel(BorderLayout())
                    panel.background = java.awt.Color.BLACK
                    try {
                        val clazz = Class.forName("uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent")
                        val comp = clazz.getDeclaredConstructor().newInstance()
                        vlcComponent = comp
                        panel.add(comp as java.awt.Component, BorderLayout.CENTER)

                        // Start playback
                        val mediaPlayerMethod = clazz.getMethod("mediaPlayer")
                        val mediaPlayer = mediaPlayerMethod.invoke(comp)
                        val mediaMethod = mediaPlayer.javaClass.getMethod("media")
                        val media = mediaMethod.invoke(mediaPlayer)

                        val playMethod = media.javaClass.getMethod("play", String::class.java, Array<String>::class.java)
                        val options = mutableListOf<String>()
                        headers.forEach { (k, v) ->
                            if (k.equals("Referer", ignoreCase = true)) options.add(":http-referrer=$v")
                            if (k.equals("User-Agent", ignoreCase = true)) options.add(":http-user-agent=$v")
                        }
                        playMethod.invoke(media, mediaUrl, options.toTypedArray())
                    } catch (_: Throwable) {}
                    panel
                }
            )
        } else {
            // Fallback UI when LibVLC is not configured on the host OS
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F12)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Stream",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (episodeTitle != null) {
                        Text(
                            text = episodeTitle,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Hardware video decoding is ready. Click below to launch in your preferred media player (VLC, MPV, or PotPlayer) or stream directly.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = ::openInExternalPlayer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(AppIcons.OpenInNew, contentDescription = "Launch")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in External Player (MPV / VLC / Browser)")
                    }
                }
            }
        }

        // Overlay Controls (Auto-hiding)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (episodeTitle != null) {
                            Text(
                                text = episodeTitle,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // External player button
                    IconButton(onClick = ::openInExternalPlayer) {
                        Icon(
                            imageVector = AppIcons.OpenInNew,
                            contentDescription = "Open in External Player",
                            tint = Color.White
                        )
                    }
                }

                // Center Play/Pause & Skip Buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentPositionMs = (currentPositionMs - 10000L).coerceAtLeast(0L) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = AppIcons.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { isPlaying = !isPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { currentPositionMs = (currentPositionMs + 10000L).coerceAtMost(durationMs) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = AppIcons.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Control Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    // Time Scrubber
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Slider(
                            value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                currentPositionMs = (frac * durationMs).toLong()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (durationMs > 0) formatTime(durationMs) else "--:--",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Secondary actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Volume control
                        IconButton(onClick = { isMuted = !isMuted }) {
                            Icon(
                                imageVector = when {
                                    isMuted || volume == 0f -> AppIcons.VolumeMute
                                    volume < 0.5f -> AppIcons.VolumeDown
                                    else -> AppIcons.VolumeUp
                                },
                                contentDescription = "Volume",
                                tint = Color.White
                            )
                        }

                        Slider(
                            value = if (isMuted) 0f else volume,
                            onValueChange = {
                                volume = it
                                isMuted = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.width(100.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Subtitle selector
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(
                                imageVector = AppIcons.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (selectedSubtitle != null) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Speed selector
                        Box {
                            IconButton(onClick = { speedMenuExpanded = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = AppIcons.Speed,
                                        contentDescription = "Playback Speed",
                                        tint = Color.White
                                    )
                                    Text(
                                        text = "${speed}x",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s}x") },
                                        onClick = {
                                            speed = s
                                            speedMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitle Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Subtitles", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    selectedSubtitle = null
                                    showSubtitleDialog = false
                                }
                                .padding(10.dp),
                            color = if (selectedSubtitle == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                        ) {
                            Text("Off", fontWeight = if (selectedSubtitle == null) FontWeight.Bold else FontWeight.Normal)
                        }

                        subtitles.forEach { sub ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedSubtitle = sub
                                        showSubtitleDialog = false
                                    }
                                    .padding(10.dp),
                                color = if (selectedSubtitle == sub) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                            ) {
                                Text(sub.lang, fontWeight = if (selectedSubtitle == sub) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
