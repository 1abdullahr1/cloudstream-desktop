package com.lagradost.cloudstream4.engine.storage

import com.lagradost.cloudstream4.FilePreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryItem(
    val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeName: String? = null,
    val episodeData: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

object DesktopHistoryManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(FilePreferenceStore.appDirectory, "watch_history.json")
    private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyFlow = _historyFlow.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        try {
            if (file.exists()) {
                val list = json.decodeFromString<List<HistoryItem>>(file.readText())
                _historyFlow.value = list.sortedByDescending { it.lastWatchedTimestamp }
            }
        } catch (_: Throwable) {
            _historyFlow.value = emptyList()
        }
    }

    private fun saveHistory() {
        try {
            file.writeText(json.encodeToString(_historyFlow.value))
        } catch (_: Throwable) {
            // Ignore
        }
    }

    fun saveProgress(item: HistoryItem) {
        val current = _historyFlow.value.toMutableList()
        current.removeAll { it.url == item.url && (it.episodeData == null || it.episodeData == item.episodeData) }
        current.add(0, item.copy(lastWatchedTimestamp = System.currentTimeMillis()))
        _historyFlow.value = current
        saveHistory()
    }

    fun getProgress(url: String, episodeData: String? = null): HistoryItem? {
        return if (episodeData != null) {
            _historyFlow.value.firstOrNull { it.url == url && it.episodeData == episodeData }
        } else {
            _historyFlow.value.firstOrNull { it.url == url }
        }
    }

    fun remove(url: String) {
        _historyFlow.value = _historyFlow.value.filterNot { it.url == url }
        saveHistory()
    }

    fun clearAll() {
        _historyFlow.value = emptyList()
        saveHistory()
    }
}
