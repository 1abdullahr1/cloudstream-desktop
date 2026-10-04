package com.lagradost.cloudstream4.engine.download

import com.lagradost.cloudstream4.FilePreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable
enum class DownloadState {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed
}

@Serializable
data class DesktopDownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val episodeName: String? = null,
    val url: String,
    val posterUrl: String? = null,
    val filePath: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadState = DownloadState.Queued,
    val downloadSpeedBytesPerSec: Long = 0L,
    val addedTime: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

object DesktopDownloadManager {
    val downloadsFolder: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val folder = File(userHome, "Videos/CloudStream")
        if (!folder.exists()) folder.mkdirs()
        folder
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val recordsFile = File(FilePreferenceStore.appDirectory, "downloads.json")
    private val _downloadsFlow = MutableStateFlow<List<DesktopDownloadItem>>(emptyList())
    val downloadsFlow = _downloadsFlow.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val downloadScope = CoroutineScope(Dispatchers.IO)

    init {
        loadRecords()
    }

    private fun loadRecords() {
        try {
            if (recordsFile.exists()) {
                val list = json.decodeFromString<List<DesktopDownloadItem>>(recordsFile.readText())
                _downloadsFlow.value = list.map {
                    // Reset interrupted downloads to Paused
                    if (it.status == DownloadState.Downloading) it.copy(status = DownloadState.Paused) else it
                }
            }
        } catch (_: Throwable) {
            _downloadsFlow.value = emptyList()
        }
    }

    private fun saveRecords() {
        try {
            recordsFile.writeText(json.encodeToString(_downloadsFlow.value))
        } catch (_: Throwable) {
            // Ignore
        }
    }

    fun startDownload(title: String, episodeName: String?, streamUrl: String, posterUrl: String?, headers: Map<String, String> = emptyMap()) {
        val safeFileName = sanitizeFileName("${title}_${episodeName ?: "Movie"}.mp4")
        val destFile = File(downloadsFolder, safeFileName)

        val item = DesktopDownloadItem(
            title = title,
            episodeName = episodeName,
            url = streamUrl,
            posterUrl = posterUrl,
            filePath = destFile.absolutePath,
            status = DownloadState.Queued
        )

        val current = _downloadsFlow.value.toMutableList()
        current.add(0, item)
        _downloadsFlow.value = current
        saveRecords()

        processDownload(item, headers)
    }

    private fun processDownload(item: DesktopDownloadItem, headers: Map<String, String>) {
        val job = downloadScope.launch {
            updateStatus(item.id, DownloadState.Downloading)
            val destFile = File(item.filePath)

            try {
                val urlObj = URL(item.url)
                val conn = urlObj.openConnection() as HttpURLConnection
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                val totalLength = conn.contentLengthLong
                updateItem(item.id) { it.copy(totalBytes = totalLength) }

                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastTime = System.currentTimeMillis()
                        var bytesSinceLast = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            bytesSinceLast += read

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 1000) {
                                val speed = (bytesSinceLast * 1000) / (now - lastTime)
                                updateItem(item.id) {
                                    it.copy(
                                        downloadedBytes = downloaded,
                                        downloadSpeedBytesPerSec = speed
                                    )
                                }
                                lastTime = now
                                bytesSinceLast = 0L
                            }
                        }
                    }
                }

                updateStatus(item.id, DownloadState.Completed)
            } catch (t: Throwable) {
                updateStatus(item.id, DownloadState.Failed)
            } finally {
                downloadJobs.remove(item.id)
                saveRecords()
            }
        }
        downloadJobs[item.id] = job
    }

    fun pauseDownload(id: String) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        updateStatus(id, DownloadState.Paused)
        saveRecords()
    }

    fun resumeDownload(id: String) {
        val item = _downloadsFlow.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadState.Downloading) {
            processDownload(item, emptyMap())
        }
    }

    fun cancelDownload(id: String) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        val item = _downloadsFlow.value.firstOrNull { it.id == id }
        if (item != null) {
            try {
                File(item.filePath).delete()
            } catch (_: Throwable) {}
        }
        _downloadsFlow.value = _downloadsFlow.value.filterNot { it.id == id }
        saveRecords()
    }

    private fun updateStatus(id: String, state: DownloadState) {
        updateItem(id) { it.copy(status = state, downloadSpeedBytesPerSec = 0L) }
    }

    private fun updateItem(id: String, transform: (DesktopDownloadItem) -> DesktopDownloadItem) {
        _downloadsFlow.value = _downloadsFlow.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
