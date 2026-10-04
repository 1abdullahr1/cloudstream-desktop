package com.lagradost.cloudstream4.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.engine.download.DesktopDownloadManager
import com.lagradost.cloudstream4.engine.download.DownloadState
import com.lagradost.cloudstream4.navigation.Screen
import java.awt.Desktop
import java.io.File

@Composable
fun DownloadsScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by DesktopDownloadManager.downloadsFlow.collectAsState()

    fun openDownloadsFolder() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(DesktopDownloadManager.downloadsFolder)
            }
        } catch (_: Throwable) {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Downloads",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Saved media for offline viewing in ${DesktopDownloadManager.downloadsFolder.name}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = ::openDownloadsFolder,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Folder")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Folder")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No downloads yet. Click the download icon on any stream to download it for offline watching.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(downloads) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.episodeName != null) {
                                        Text(
                                            text = item.episodeName,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (item.status) {
                                        DownloadState.Completed -> {
                                            Button(
                                                onClick = {
                                                    val localFile = File(item.filePath)
                                                    if (localFile.exists()) {
                                                        onNavigate(
                                                            Screen.Player(
                                                                mediaUrl = localFile.toURI().toString(),
                                                                title = item.title,
                                                                episodeTitle = item.episodeName
                                                            )
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play")
                                            }
                                        }
                                        DownloadState.Downloading -> {
                                            IconButton(onClick = { DesktopDownloadManager.pauseDownload(item.id) }) {
                                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        DownloadState.Paused -> {
                                            IconButton(onClick = { DesktopDownloadManager.resumeDownload(item.id) }) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        else -> {}
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(onClick = { DesktopDownloadManager.cancelDownload(item.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            if (item.status == DownloadState.Downloading || item.status == DownloadState.Paused) {
                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { item.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    val downloadedMB = String.format("%.1f", item.downloadedBytes / (1024.0 * 1024.0))
                                    val totalMB = if (item.totalBytes > 0) String.format("%.1f MB", item.totalBytes / (1024.0 * 1024.0)) else "Unknown"
                                    val speedMB = String.format("%.2f MB/s", item.downloadSpeedBytesPerSec / (1024.0 * 1024.0))

                                    Text(
                                        text = "$downloadedMB MB / $totalMB (${item.progressPercent}%)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    if (item.status == DownloadState.Downloading) {
                                        Text(
                                            text = speedMB,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Paused",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFFB800),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
