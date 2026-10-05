package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.engine.plugins.DesktopPluginManager
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.navigation.Screen
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import com.lagradost.cloudstream4.ui.components.DesktopNavigationRail
import com.lagradost.cloudstream4.ui.details.DetailsScreen
import com.lagradost.cloudstream4.engine.SingleInstanceManager
import com.lagradost.cloudstream4.ui.downloads.DownloadsScreen
import com.lagradost.cloudstream4.ui.extensions.ExtensionsScreen
import com.lagradost.cloudstream4.ui.home.HomeScreen
import com.lagradost.cloudstream4.ui.library.LibraryScreen
import com.lagradost.cloudstream4.ui.player.PlayerScreen
import com.lagradost.cloudstream4.ui.search.SearchScreen
import com.lagradost.cloudstream4.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.system.exitProcess

fun main() {
    if (!SingleInstanceManager.acquireLock()) {
        System.err.println("CloudStream is already running.")
        exitProcess(0)
    }

    try {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(FilePreferenceStore.appDirectory, "image_cache").toOkioPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    } catch (_: Throwable) {}

    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 780.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.default_icon),
        state = windowState
    ) {
        // Initialize plugins and providers
        LaunchedEffect(Unit) {
            DesktopPluginManager.init()
        }

        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        val backStack = remember { mutableStateListOf<Screen>() }

        fun navigateTo(screen: Screen) {
            if (screen != currentScreen) {
                backStack.add(currentScreen)
                currentScreen = screen
            }
        }

        fun navigateBack() {
            if (backStack.isNotEmpty()) {
                currentScreen = backStack.removeAt(backStack.lastIndex)
            } else {
                currentScreen = Screen.Home
            }
        }

        CloudStreamTheme(mode = CloudStreamThemeMode.Dark) {
            CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (val screen = currentScreen) {
                        is Screen.Player -> {
                            // Full-window dedicated player experience
                            PlayerScreen(
                                mediaUrl = screen.mediaUrl,
                                title = screen.title,
                                episodeTitle = screen.episodeTitle,
                                apiName = screen.apiName,
                                parentUrl = screen.parentUrl,
                                episodeData = screen.episodeData,
                                headers = screen.headers,
                                subtitles = screen.subtitles,
                                onClose = ::navigateBack
                            )
                        }
                        else -> {
                            // Main Shell with Navigation Sidebar
                            Row(modifier = Modifier.fillMaxSize()) {
                                DesktopNavigationRail(
                                    currentScreen = currentScreen,
                                    onNavigate = { dest ->
                                        if (dest != currentScreen) {
                                            backStack.clear()
                                            currentScreen = dest
                                        }
                                    }
                                )

                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    when (screen) {
                                        is Screen.Home -> {
                                            HomeScreen(onNavigate = ::navigateTo)
                                        }
                                        is Screen.Search -> {
                                            SearchScreen(
                                                initialQuery = screen.initialQuery,
                                                onNavigate = ::navigateTo
                                            )
                                        }
                                        is Screen.Library -> {
                                            LibraryScreen(onNavigate = ::navigateTo)
                                        }
                                        is Screen.Downloads -> {
                                            DownloadsScreen(onNavigate = ::navigateTo)
                                        }
                                        is Screen.Extensions -> {
                                            ExtensionsScreen()
                                        }
                                        is Screen.Settings -> {
                                            SettingsScreen()
                                        }
                                        is Screen.Details -> {
                                            DetailsScreen(
                                                url = screen.url,
                                                apiName = screen.apiName,
                                                onBack = ::navigateBack,
                                                onNavigate = ::navigateTo
                                            )
                                        }
                                        is Screen.Player -> {}
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
}