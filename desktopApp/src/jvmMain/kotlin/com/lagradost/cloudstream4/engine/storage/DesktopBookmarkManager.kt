package com.lagradost.cloudstream4.engine.storage

import com.lagradost.cloudstream4.FilePreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class BookmarkStatus(val label: String) {
    Watching("Watching"),
    PlanToWatch("Plan to Watch"),
    Completed("Completed"),
    OnHold("On Hold"),
    Dropped("Dropped")
}

@Serializable
data class BookmarkItem(
    val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val status: BookmarkStatus = BookmarkStatus.Watching,
    val addedTimestamp: Long = System.currentTimeMillis()
)

object DesktopBookmarkManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(FilePreferenceStore.appDirectory, "bookmarks.json")
    private val _bookmarksFlow = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarksFlow = _bookmarksFlow.asStateFlow()

    init {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        try {
            if (file.exists()) {
                val list = json.decodeFromString<List<BookmarkItem>>(file.readText())
                _bookmarksFlow.value = list.sortedByDescending { it.addedTimestamp }
            }
        } catch (_: Throwable) {
            _bookmarksFlow.value = emptyList()
        }
    }

    private fun saveBookmarks() {
        try {
            file.writeText(json.encodeToString(_bookmarksFlow.value))
        } catch (_: Throwable) {
            // Ignore
        }
    }

    fun setBookmark(item: BookmarkItem) {
        val current = _bookmarksFlow.value.toMutableList()
        current.removeAll { it.url == item.url }
        current.add(0, item.copy(addedTimestamp = System.currentTimeMillis()))
        _bookmarksFlow.value = current
        saveBookmarks()
    }

    fun removeBookmark(url: String) {
        _bookmarksFlow.value = _bookmarksFlow.value.filterNot { it.url == url }
        saveBookmarks()
    }

    fun getBookmark(url: String): BookmarkItem? {
        return _bookmarksFlow.value.firstOrNull { it.url == url }
    }

    fun isBookmarked(url: String): Boolean {
        return getBookmark(url) != null
    }

    fun getBookmarksByStatus(status: BookmarkStatus?): List<BookmarkItem> {
        return if (status == null) {
            _bookmarksFlow.value
        } else {
            _bookmarksFlow.value.filter { it.status == status }
        }
    }
}
