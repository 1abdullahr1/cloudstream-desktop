package com.lagradost.cloudstream4.engine.plugins

import com.lagradost.cloudstream4.FilePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopRepository(
    val name: String,
    val url: String,
    val description: String? = null
)

@Serializable
data class DesktopPluginInfo(
    val name: String,
    val internalName: String,
    val version: Int,
    val url: String,
    val repositoryUrl: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val iconUrl: String? = null,
    val tvTypes: List<String> = emptyList(),
    val language: String? = null,
    val status: Int = 1,
    val isInstalled: Boolean = false
)

object DesktopRepositoryManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(FilePreferenceStore.appDirectory, "repositories.json")

    val DEFAULT_REPOSITORIES = listOf(
        DesktopRepository(
            name = "Official Extensions",
            url = "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions/builds/plugins.json",
            description = "Official CloudStream plugin repository"
        ),
        DesktopRepository(
            name = "MegaRepo",
            url = "https://raw.githubusercontent.com/Stormunblessed/site-plugins/builds/plugins.json",
            description = "Community mega repository with movies, anime and TV"
        ),
        DesktopRepository(
            name = "English Providers",
            url = "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions-multilingual/builds/plugins.json",
            description = "Multilingual and international streaming sources"
        )
    )

    private val _repositoriesFlow = MutableStateFlow<List<DesktopRepository>>(emptyList())
    val repositoriesFlow = _repositoriesFlow.asStateFlow()

    init {
        loadRepositories()
    }

    private fun loadRepositories() {
        try {
            if (file.exists()) {
                val list = json.decodeFromString<List<DesktopRepository>>(file.readText())
                _repositoriesFlow.value = if (list.isNotEmpty()) list else DEFAULT_REPOSITORIES
            } else {
                _repositoriesFlow.value = DEFAULT_REPOSITORIES
                saveRepositories()
            }
        } catch (_: Throwable) {
            _repositoriesFlow.value = DEFAULT_REPOSITORIES
        }
    }

    private fun saveRepositories() {
        try {
            file.writeText(json.encodeToString(_repositoriesFlow.value))
        } catch (_: Throwable) {
            // Ignore
        }
    }

    fun addRepository(repo: DesktopRepository) {
        val current = _repositoriesFlow.value.toMutableList()
        if (current.none { it.url == repo.url }) {
            current.add(repo)
            _repositoriesFlow.value = current
            saveRepositories()
        }
    }

    fun removeRepository(url: String) {
        _repositoriesFlow.value = _repositoriesFlow.value.filterNot { it.url == url }
        saveRepositories()
    }

    suspend fun fetchPluginsFromRepo(repo: DesktopRepository): List<DesktopPluginInfo> = withContext(Dispatchers.IO) {
        try {
            val connection = (java.net.URI.create(repo.url).toURL().openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connectTimeout = 15000
                readTimeout = 30000
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val plugins = json.decodeFromString<List<DesktopPluginInfo>>(response)
            plugins.map { it.copy(repositoryUrl = repo.url) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun fetchAllOnlinePlugins(): List<DesktopPluginInfo> = withContext(Dispatchers.IO) {
        val all = mutableListOf<DesktopPluginInfo>()
        for (repo in _repositoriesFlow.value) {
            try {
                val plugins = fetchPluginsFromRepo(repo)
                all.addAll(plugins)
            } catch (_: Throwable) {
                // Skip failed repo
            }
        }
        all.distinctBy { it.internalName }
    }
}
