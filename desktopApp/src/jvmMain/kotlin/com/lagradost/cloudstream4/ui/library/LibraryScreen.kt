package com.lagradost.cloudstream4.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.engine.storage.BookmarkStatus
import com.lagradost.cloudstream4.engine.storage.DesktopBookmarkManager
import com.lagradost.cloudstream4.navigation.Screen
import com.lagradost.cloudstream4.ui.components.MediaCard

@Composable
fun LibraryScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmarks by DesktopBookmarkManager.bookmarksFlow.collectAsState()
    val tabs = listOf("All") + BookmarkStatus.entries.map { it.label }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val filteredBookmarks = remember(bookmarks, selectedTabIndex) {
        if (selectedTabIndex == 0) {
            bookmarks
        } else {
            val status = BookmarkStatus.entries[selectedTabIndex - 1]
            bookmarks.filter { it.status == status }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Library",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your saved movies, shows and anime watchlists",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (filteredBookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved titles in this section. Browse or search media and click 'Add to Watchlist' to save them here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredBookmarks) { item ->
                    MediaCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        year = item.year,
                        type = item.status.label,
                        onClick = {
                            onNavigate(
                                Screen.Details(
                                    url = item.url,
                                    apiName = item.apiName
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
