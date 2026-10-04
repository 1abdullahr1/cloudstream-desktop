package com.lagradost.cloudstream4.ui.extensions

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import com.lagradost.cloudstream4.ui.components.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.engine.plugins.DesktopPluginInfo
import com.lagradost.cloudstream4.engine.plugins.DesktopPluginManager
import com.lagradost.cloudstream4.engine.plugins.DesktopRepository
import com.lagradost.cloudstream4.engine.plugins.DesktopRepositoryManager
import kotlinx.coroutines.launch

@Composable
fun ExtensionsScreen(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val installedPlugins by DesktopPluginManager.installedPluginsFlow.collectAsState()
    val repositories by DesktopRepositoryManager.repositoriesFlow.collectAsState()

    var onlinePlugins by remember { mutableStateOf<List<DesktopPluginInfo>>(emptyList()) }
    var isLoadingOnline by remember { mutableStateOf(false) }
    var installingPluginName by remember { mutableStateOf<String?>(null) }

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }

    fun refreshOnlinePlugins() {
        scope.launch {
            isLoadingOnline = true
            onlinePlugins = DesktopRepositoryManager.fetchAllOnlinePlugins()
            isLoadingOnline = false
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && onlinePlugins.isEmpty()) {
            refreshOnlinePlugins()
        }
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
                    text = "Extensions",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manage and add streaming repositories and scrapers",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showAddRepoDialog = true },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Repo")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Repository")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Installed (${installedPlugins.size + 1})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Browse Repositories") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Built-in plugin card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Extension,
                                    contentDescription = "Extension",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CloudStream Featured (Built-in)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v1.0.0 • Open Source Media & Demos",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                items(installedPlugins) { plugin ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E3036)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Extension,
                                    contentDescription = "Extension",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${plugin.version} • ${plugin.internalName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { DesktopPluginManager.uninstallPlugin(plugin.internalName) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Uninstall",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Browse Repositories Tab
            if (isLoadingOnline) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available Extensions (${onlinePlugins.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(onClick = ::refreshOnlinePlugins) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(onlinePlugins) { item ->
                        val isInstalled = installedPlugins.any { it.internalName == item.internalName }
                        val isCurrentlyInstalling = installingPluginName == item.internalName

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "v${item.version} ${if (!item.language.isNullOrBlank()) "• [${item.language}]" else ""}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!item.description.isNullOrBlank()) {
                                        Text(
                                            text = item.description!!,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (isCurrentlyInstalling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (isInstalled) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Installed",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Installed",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                installingPluginName = item.internalName
                                                DesktopPluginManager.installPlugin(item)
                                                installingPluginName = null
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(AppIcons.Download, contentDescription = "Install")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Repository Dialog
        if (showAddRepoDialog) {
            AlertDialog(
                onDismissRequest = { showAddRepoDialog = false },
                title = { Text("Add Plugin Repository", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Paste a CloudStream repository plugins.json URL or raw repository link:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newRepoName,
                            onValueChange = { newRepoName = it },
                            label = { Text("Repository Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newRepoUrl,
                            onValueChange = { newRepoUrl = it },
                            label = { Text("Repository URL") },
                            placeholder = { Text("https://raw.githubusercontent.com/.../plugins.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newRepoUrl.isNotBlank()) {
                                val name = if (newRepoName.isNotBlank()) newRepoName else "Custom Repo"
                                DesktopRepositoryManager.addRepository(
                                    DesktopRepository(name = name, url = newRepoUrl.trim())
                                )
                                showAddRepoDialog = false
                                newRepoUrl = ""
                                newRepoName = ""
                                refreshOnlinePlugins()
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddRepoDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
