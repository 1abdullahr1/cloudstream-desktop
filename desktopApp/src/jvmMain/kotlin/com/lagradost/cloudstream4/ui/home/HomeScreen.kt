package com.lagradost.cloudstream4.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.lagradost.cloudstream4.ui.components.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream4.engine.storage.DesktopBookmarkManager
import com.lagradost.cloudstream4.engine.storage.DesktopHistoryManager
import com.lagradost.cloudstream4.navigation.Screen
import com.lagradost.cloudstream4.ui.components.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val historyItems by DesktopHistoryManager.historyFlow.collectAsState()
    val bookmarks by DesktopBookmarkManager.bookmarksFlow.collectAsState()

    var providers by remember { mutableStateOf(APIHolder.allProviders.toList()) }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull()) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    var homeLists by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Update providers list when refreshed
    LaunchedEffect(refreshTrigger) {
        providers = APIHolder.allProviders.toList()
        if (selectedProvider == null || !providers.contains(selectedProvider)) {
            selectedProvider = providers.firstOrNull()
        }
    }

    // Load home page lists from selected provider
    LaunchedEffect(selectedProvider, refreshTrigger) {
        val prov = selectedProvider ?: return@LaunchedEffect
        isLoading = true
        homeLists = emptyList()
        try {
            val lists = mutableListOf<HomePageList>()
            withContext(Dispatchers.IO) {
                if (prov.hasMainPage && prov.mainPage.isNotEmpty()) {
                    for (pageData in prov.mainPage) {
                        try {
                            val req = MainPageRequest(
                                name = pageData.name,
                                data = pageData.data,
                                horizontalImages = pageData.horizontalImages
                            )
                            val res = prov.getMainPage(1, req)
                            lists.addAll(res.items)
                        } catch (_: Throwable) {
                            // Skip failed section
                        }
                    }
                } else {
                    // Fallback search popular
                    try {
                        val popular = prov.search("")
                        if (popular.isNotEmpty()) {
                            lists.add(HomePageList("Popular Titles", popular))
                        }
                    } catch (_: Throwable) {}
                }
            }
            homeLists = lists
        } catch (_: Throwable) {
            homeLists = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Discover",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Stream movies, TV shows and anime",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Provider Selector Dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { providerDropdownExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedProvider?.name ?: "No Provider Selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = AppIcons.ArrowDropDown,
                            contentDescription = "Select Provider",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false }
                ) {
                    for (prov in providers) {
                        DropdownMenuItem(
                            text = { Text(prov.name) },
                            onClick = {
                                selectedProvider = prov
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Refresh Button
            IconButton(
                onClick = { refreshTrigger++ }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Main Scrollable Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Continue Watching Section
            if (historyItems.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "Continue Watching",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyItems.take(10)) { item ->
                                MediaCard(
                                    title = item.title,
                                    posterUrl = item.posterUrl,
                                    type = item.episodeName ?: "Resume",
                                    progressFraction = item.progressFraction,
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

            // 2. Watchlist Section
            if (bookmarks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "My Watchlist",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(bookmarks.take(12)) { item ->
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

            // 3. Provider Dynamic Lists
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (homeLists.isNotEmpty()) {
                items(homeLists) { section ->
                    Column(modifier = Modifier.padding(vertical = 14.dp)) {
                        Text(
                            text = section.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(section.list) { media ->
                                MediaCard(
                                    title = media.name,
                                    posterUrl = media.posterUrl,
                                    quality = media.quality,
                                    type = media.type?.name,
                                    onClick = {
                                        onNavigate(
                                            Screen.Details(
                                                url = media.url,
                                                apiName = media.apiName
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No content available. Select or add extensions from the Extensions tab.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
