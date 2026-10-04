package com.lagradost.cloudstream4.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lagradost.cloudstream4.ui.components.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream4.engine.download.DesktopDownloadManager
import com.lagradost.cloudstream4.engine.storage.BookmarkItem
import com.lagradost.cloudstream4.engine.storage.BookmarkStatus
import com.lagradost.cloudstream4.engine.storage.DesktopBookmarkManager
import com.lagradost.cloudstream4.engine.storage.DesktopHistoryManager
import com.lagradost.cloudstream4.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailsScreen(
    url: String,
    apiName: String,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val provider = remember(apiName) { APIHolder.getApiFromNameNull(apiName) ?: APIHolder.apis.firstOrNull() }

    var loadResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Bookmark state
    val bookmarks by DesktopBookmarkManager.bookmarksFlow.collectAsState()
    val currentBookmark = remember(bookmarks, url) { DesktopBookmarkManager.getBookmark(url) }
    var bookmarkMenuExpanded by remember { mutableStateOf(false) }

    // Season tab selection
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }

    // Link scraping modal state
    var isScrapingLinks by remember { mutableStateOf(false) }
    var foundLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var foundSubtitles by remember { mutableStateOf<List<SubtitleFile>>(emptyList()) }
    var scrapingEpisode by remember { mutableStateOf<Episode?>(null) }
    var showLinksDialog by remember { mutableStateOf(false) }

    // Load data from provider
    LaunchedEffect(url, apiName) {
        val prov = provider ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val response = withContext(Dispatchers.IO) {
                prov.load(url)
            }
            loadResponse = response
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Failed to load content details"
        } finally {
            isLoading = false
        }
    }

    fun startScraping(episode: Episode?, targetData: String) {
        scrapingEpisode = episode
        isScrapingLinks = true
        foundLinks = emptyList()
        foundSubtitles = emptyList()
        showLinksDialog = true

        val prov = provider ?: return
        scope.launch(Dispatchers.IO) {
            try {
                prov.loadLinks(
                    data = targetData,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        foundSubtitles = (foundSubtitles + sub).distinctBy { it.url }
                    },
                    callback = { link ->
                        foundLinks = (foundLinks + link).distinctBy { it.url }
                    }
                )
            } catch (_: Throwable) {
            } finally {
                isScrapingLinks = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }

        if (errorMessage != null || loadResponse == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "Content not found",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
            return
        }

        val details = loadResponse!!
        val isSeries = details is TvSeriesLoadResponse
        val episodes = if (isSeries) (details as TvSeriesLoadResponse).episodes else emptyList()
        val seasons = remember(episodes) {
            episodes.mapNotNull { it.season ?: 1 }.distinct().sorted()
        }
        val currentSeasonEpisodes = remember(episodes, selectedSeasonIndex, seasons) {
            val seasonNum = seasons.getOrNull(selectedSeasonIndex) ?: 1
            episodes.filter { (it.season ?: 1) == seasonNum }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Hero Header with Backdrop
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Backdrop Image
                    AsyncImage(
                        model = details.posterUrl,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlays
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    // Back & Action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Bookmark Button
                        Box {
                            OutlinedButton(
                                onClick = { bookmarkMenuExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentBookmark != null) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentBookmark?.status?.label ?: "Add to Watchlist",
                                    color = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = bookmarkMenuExpanded,
                                onDismissRequest = { bookmarkMenuExpanded = false }
                            ) {
                                for (status in BookmarkStatus.entries) {
                                    DropdownMenuItem(
                                        text = { Text(status.label) },
                                        onClick = {
                                            DesktopBookmarkManager.setBookmark(
                                                BookmarkItem(
                                                    url = details.url,
                                                    apiName = details.apiName,
                                                    title = details.name,
                                                    posterUrl = details.posterUrl,
                                                    year = details.year,
                                                    status = status
                                                )
                                            )
                                            bookmarkMenuExpanded = false
                                        }
                                    )
                                }
                                if (currentBookmark != null) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Watchlist", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            DesktopBookmarkManager.removeBookmark(details.url)
                                            bookmarkMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Content Metadata Overlay
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Poster Card
                        Surface(
                            modifier = Modifier
                                .width(130.dp)
                                .aspectRatio(2f / 3f)
                                .shadow(12.dp, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            AsyncImage(
                                model = details.posterUrl,
                                contentDescription = details.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Title & Meta Info
                        Column {
                            Text(
                                text = details.name,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (details.year != null) {
                                    Text(
                                        text = details.year.toString(),
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("•", color = Color.White.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Text(
                                    text = if (isSeries) "TV Series" else "Movie",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val score = details.score
                                if (score != null) {
                                    val scoreVal = score.toFloat(10)
                                    if (scoreVal > 0f) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("•", color = Color.White.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = AppIcons.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFB800),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format("%.1f", scoreVal),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Play Button
                            Button(
                                onClick = {
                                    if (isSeries) {
                                        val firstEp = episodes.firstOrNull()
                                        if (firstEp != null) startScraping(firstEp, firstEp.data)
                                    } else {
                                        val movieData = (details as? MovieLoadResponse)?.dataUrl ?: details.url
                                        startScraping(null, movieData)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(AppIcons.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSeries) "Watch Episode 1" else "Play Movie", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Synopsis Plot
            if (!details.plot.isNullOrBlank()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
                        Text(
                            text = "Synopsis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = details.plot!!,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Episodes Section (for TV Series / Anime)
            if (isSeries && seasons.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)) {
                        Text(
                            text = "Episodes",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Season Tabs
                        if (seasons.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIndex,
                                edgePadding = 0.dp,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                seasons.forEachIndexed { index, s ->
                                    Tab(
                                        selected = selectedSeasonIndex == index,
                                        onClick = { selectedSeasonIndex = index },
                                        text = { Text("Season $s") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                items(currentSeasonEpisodes) { ep ->
                    val epProgress = DesktopHistoryManager.getProgress(details.url, ep.data)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                startScraping(ep, ep.data)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Episode Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF222226))
                            ) {
                                if (!ep.posterUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ep.posterUrl,
                                        contentDescription = ep.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                if (epProgress != null && epProgress.progressFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.name ?: "Episode ${ep.episode ?: ""}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!ep.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ep.description!!,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Links / Server Selection Modal Dialog
        if (showLinksDialog) {
            AlertDialog(
                onDismissRequest = { showLinksDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isScrapingLinks) "Scraping Stream Sources..." else "Select Stream Source",
                            fontWeight = FontWeight.Bold
                        )
                        if (isScrapingLinks) {
                            Spacer(modifier = Modifier.width(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (foundLinks.isEmpty()) {
                            Text(
                                text = if (isScrapingLinks) "Searching mirrors and resolving video host links..." else "No streams found for this selection.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "Found ${foundLinks.size} streaming link(s). Select a quality or server:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            foundLinks.forEach { link ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showLinksDialog = false
                                            onNavigate(
                                                Screen.Player(
                                                    mediaUrl = link.url,
                                                    title = details.name,
                                                    episodeTitle = scrapingEpisode?.name ?: "Movie",
                                                    apiName = details.apiName,
                                                    parentUrl = details.url,
                                                    episodeData = scrapingEpisode?.data,
                                                    headers = link.headers,
                                                    subtitles = foundSubtitles
                                                )
                                            )
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = link.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${link.source} • Quality: ${link.quality}p",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Download button for this stream
                                        IconButton(
                                            onClick = {
                                                DesktopDownloadManager.startDownload(
                                                    title = details.name,
                                                    episodeName = scrapingEpisode?.name ?: "Movie",
                                                    streamUrl = link.url,
                                                    posterUrl = details.posterUrl,
                                                    headers = link.headers
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Download,
                                                contentDescription = "Download",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (foundLinks.isNotEmpty()) {
                        Button(
                            onClick = {
                                val bestLink = foundLinks.maxByOrNull { it.quality } ?: foundLinks[0]
                                showLinksDialog = false
                                onNavigate(
                                    Screen.Player(
                                        mediaUrl = bestLink.url,
                                        title = details.name,
                                        episodeTitle = scrapingEpisode?.name ?: "Movie",
                                        apiName = details.apiName,
                                        parentUrl = details.url,
                                        episodeData = scrapingEpisode?.data,
                                        headers = bestLink.headers,
                                        subtitles = foundSubtitles
                                    )
                                )
                            }
                        ) {
                            Text("Play Best Quality")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLinksDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
