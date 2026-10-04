package com.lagradost.cloudstream4.ui.search

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.lagradost.cloudstream4.ui.components.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.navigation.Screen
import com.lagradost.cloudstream4.ui.components.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf(initialQuery) }
    var searchResults by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    var selectedProviderName by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf<TvType?>(null) }

    val providers = remember { APIHolder.allProviders.toList() }

    suspend fun executeSearch(searchTerm: String) {
        val trimmed = searchTerm.trim()
        if (trimmed.isBlank()) {
            searchResults = emptyList()
            hasSearched = false
            return
        }

        isSearching = true
        hasSearched = true
        try {
            val results = withContext(Dispatchers.IO) {
                if (selectedProviderName == "All") {
                    val deferreds = providers.map { prov ->
                        async {
                            try {
                                prov.search(trimmed)
                            } catch (_: Throwable) {
                                emptyList()
                            }
                        }
                    }
                    deferreds.awaitAll().filterNotNull().flatten().distinctBy { it.url }
                } else {
                    val prov = providers.firstOrNull { it.name == selectedProviderName }
                    prov?.search(trimmed) ?: emptyList()
                }
            }
            searchResults = results
        } catch (_: Throwable) {
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    LaunchedEffect(query, selectedProviderName) {
        if (query.isNotBlank()) {
            executeSearch(query)
        }
    }

    val filteredResults = remember(searchResults, selectedType) {
        if (selectedType == null) {
            searchResults
        } else {
            searchResults.filter { it.type == selectedType }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies, TV series, anime across providers...") },
            leadingIcon = {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; searchResults = emptyList(); hasSearched = false }) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Provider:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilterChip(
                selected = selectedProviderName == "All",
                onClick = { selectedProviderName = "All" },
                label = { Text("All (${providers.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )

            for (prov in providers.take(4)) {
                FilterChip(
                    selected = selectedProviderName == prov.name,
                    onClick = { selectedProviderName = prov.name },
                    label = { Text(prov.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Type filters
            FilterChip(
                selected = selectedType == null,
                onClick = { selectedType = null },
                label = { Text("All Types") }
            )

            FilterChip(
                selected = selectedType == TvType.Movie,
                onClick = { selectedType = if (selectedType == TvType.Movie) null else TvType.Movie },
                label = { Text("Movies") }
            )

            FilterChip(
                selected = selectedType == TvType.TvSeries,
                onClick = { selectedType = if (selectedType == TvType.TvSeries) null else TvType.TvSeries },
                label = { Text("Series") }
            )

            FilterChip(
                selected = selectedType == TvType.Anime,
                onClick = { selectedType = if (selectedType == TvType.Anime) null else TvType.Anime },
                label = { Text("Anime") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Results Section
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching across streaming providers...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (hasSearched && filteredResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for \"$query\". Try another search term or enable more extensions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        } else if (!hasSearched) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type in the search bar above to find media across all your extensions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
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
                items(filteredResults) { media ->
                    MediaCard(
                        title = media.name,
                        posterUrl = media.posterUrl,
                        quality = media.quality,
                        type = media.type?.name ?: media.apiName,
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
}
