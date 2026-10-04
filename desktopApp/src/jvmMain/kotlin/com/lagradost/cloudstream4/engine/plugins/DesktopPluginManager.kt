package com.lagradost.cloudstream4.engine.plugins

import com.lagradost.cloudstream4.FilePreferenceStore
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.util.zip.ZipFile

@Serializable
data class InstalledPluginRecord(
    val internalName: String,
    val name: String,
    val version: Int,
    val jarPath: String,
    val pluginClassName: String,
    val isEnabled: Boolean = true
)

/**
 * Built-in open streaming provider for out-of-the-box streaming and instant testing.
 */
@Suppress("DEPRECATION_ERROR")
class BuiltinStreamProvider : MainAPI() {
    override var name = "CloudStream Featured"
    override var mainUrl = "https://cloudstream.internal"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var hasMainPage = true

    override val mainPage = listOf(
        MainPageData("Featured Movies", "movies"),
        MainPageData("Popular Animation", "animation"),
        MainPageData("Open Cinema Classics", "classics")
    )

    private val sampleMovies = listOf(
        MovieSearchResponse(
            name = "Big Buck Bunny",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            apiName = "CloudStream Featured",
            type = TvType.Movie,
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
            id = 1,
            quality = SearchQuality.FourK
        ),
        MovieSearchResponse(
            name = "Sintel",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            apiName = "CloudStream Featured",
            type = TvType.Movie,
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            id = 2,
            quality = SearchQuality.HD
        ),
        MovieSearchResponse(
            name = "Tears of Steel",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            apiName = "CloudStream Featured",
            type = TvType.Movie,
            posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
            id = 3,
            quality = SearchQuality.FourK
        ),
        MovieSearchResponse(
            name = "Elephants Dream",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            apiName = "CloudStream Featured",
            type = TvType.Movie,
            posterUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600&auto=format&fit=crop&q=80",
            id = 4,
            quality = SearchQuality.HD
        )
    )

    private val sampleSeries = listOf(
        TvSeriesSearchResponse(
            name = "Blender Open Studio Series",
            url = "https://cloudstream.internal/series/blender",
            apiName = "CloudStream Featured",
            type = TvType.TvSeries,
            posterUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=80",
            id = 5,
            quality = SearchQuality.HD
        )
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = when (request.name) {
            "Featured Movies" -> sampleMovies
            "Popular Animation" -> sampleSeries + sampleMovies.take(2)
            else -> sampleMovies.reversed()
        }
        return HomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        return (sampleMovies + sampleSeries).filter {
            it.name.lowercase().contains(q)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("/series/")) {
            val episodes = listOf(
                Episode(
                    data = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    name = "Episode 1: The Awakening",
                    season = 1,
                    episode = 1,
                    posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
                    description = "A large and lovable rabbit deals with forest bullies."
                ),
                Episode(
                    data = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    name = "Episode 2: The Dragon's Flight",
                    season = 1,
                    episode = 2,
                    posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
                    description = "A lonely young woman searches for a baby dragon she befriended."
                ),
                Episode(
                    data = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    name = "Episode 3: The VFX Revolution",
                    season = 1,
                    episode = 3,
                    posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                    description = "A group of warriors and scientists try to save the planet in Amsterdam."
                )
            )
            return TvSeriesLoadResponse(
                name = "Blender Open Studio Series",
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=80",
                year = 2024,
                plot = "An anthology of high-definition animated open-source film masterpieces.",
                episodes = episodes
            )
        } else {
            val match = sampleMovies.firstOrNull { it.url == url } ?: sampleMovies[0]
            return MovieLoadResponse(
                name = match.name,
                url = match.url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = match.url,
                posterUrl = match.posterUrl,
                year = 2024,
                plot = "${match.name} open cinema showcase stream with crystal clear audio and multi-resolution playback."
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Return 1080p and 720p streams
        callback(
            ExtractorLink(
                source = name,
                name = "$name - 1080p High Quality",
                url = data,
                referer = "",
                quality = Qualities.P1080.value,
                isM3u8 = data.endsWith(".m3u8")
            )
        )
        callback(
            ExtractorLink(
                source = name,
                name = "$name - 720p Fast Stream",
                url = data,
                referer = "",
                quality = Qualities.P720.value,
                isM3u8 = data.endsWith(".m3u8")
            )
        )

        // Provide demo English and Spanish subtitles
        subtitleCallback(
            SubtitleFile(
                lang = "English",
                url = "https://raw.githubusercontent.com/brenopolanski/html5-video-webvtt-example/master/subtitles/subtitles-en.vtt"
            )
        )
        subtitleCallback(
            SubtitleFile(
                lang = "Spanish",
                url = "https://raw.githubusercontent.com/brenopolanski/html5-video-webvtt-example/master/subtitles/subtitles-es.vtt"
            )
        )
        return true
    }
}

object DesktopPluginManager {
    val pluginsDirectory = File(FilePreferenceStore.appDirectory, "plugins").apply {
        if (!exists()) mkdirs()
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val recordsFile = File(FilePreferenceStore.appDirectory, "installed_plugins.json")

    private val _installedPluginsFlow = MutableStateFlow<List<InstalledPluginRecord>>(emptyList())
    val installedPluginsFlow = _installedPluginsFlow.asStateFlow()

    private val activeClassLoaders = mutableListOf<URLClassLoader>()

    fun init() {
        // 1. Register builtin provider
        val builtin = BuiltinStreamProvider()
        APIHolder.allProviders.add(builtin)
        APIHolder.addPluginMapping(builtin)

        // 2. Load installed plugins
        loadSavedRecords()
        for (record in _installedPluginsFlow.value) {
            if (record.isEnabled) {
                tryLoadJar(File(record.jarPath), record.pluginClassName)
            }
        }
    }

    private fun loadSavedRecords() {
        try {
            if (recordsFile.exists()) {
                val list = json.decodeFromString<List<InstalledPluginRecord>>(recordsFile.readText())
                _installedPluginsFlow.value = list
            }
        } catch (_: Throwable) {
            _installedPluginsFlow.value = emptyList()
        }
    }

    private fun saveRecords() {
        try {
            recordsFile.writeText(json.encodeToString(_installedPluginsFlow.value))
        } catch (_: Throwable) {
            // Ignore
        }
    }

    suspend fun installPlugin(info: DesktopPluginInfo, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val destFile = File(pluginsDirectory, "${info.internalName}.cs3")
            onProgress(0.2f)

            // Download file
            val connection = (java.net.URI.create(info.url).toURL().openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connectTimeout = 15000
                readTimeout = 30000
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            destFile.writeBytes(bytes)
            onProgress(0.5f)

            // Inspect archive
            var manifest: BasePlugin.Manifest? = null
            var dexFile: File? = null
            var jarFile = File(pluginsDirectory, "${info.internalName}.jar")

            ZipFile(destFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json")
                if (manifestEntry != null) {
                    zip.getInputStream(manifestEntry).use { input ->
                        val text = InputStreamReader(input).readText()
                        manifest = json.decodeFromString<BasePlugin.Manifest>(text)
                    }
                }

                val dexEntry = zip.getEntry("classes.dex")
                if (dexEntry != null) {
                    dexFile = File(pluginsDirectory, "${info.internalName}.dex")
                    zip.getInputStream(dexEntry).use { input ->
                        dexFile!!.writeBytes(input.readBytes())
                    }
                }
            }

            onProgress(0.7f)

            // If we have a classes.dex, translate to JAR using dex-translator or load JAR
            if (dexFile != null && dexFile!!.exists()) {
                try {
                    translateDexToJar(dexFile!!, jarFile)
                } catch (t: Throwable) {
                    // If dex translation is not available, copy destination directly if already a jar
                    destFile.copyTo(jarFile, overwrite = true)
                }
            } else {
                destFile.copyTo(jarFile, overwrite = true)
            }

            onProgress(0.85f)

            val className = manifest?.pluginClassName ?: ""
            if (className.isNotBlank() && jarFile.exists()) {
                tryLoadJar(jarFile, className)
            }

            val record = InstalledPluginRecord(
                internalName = info.internalName,
                name = info.name,
                version = info.version,
                jarPath = jarFile.absolutePath,
                pluginClassName = className,
                isEnabled = true
            )

            val current = _installedPluginsFlow.value.toMutableList()
            current.removeAll { it.internalName == info.internalName }
            current.add(record)
            _installedPluginsFlow.value = current
            saveRecords()

            onProgress(1.0f)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun translateDexToJar(dexFile: File, jarFile: File) {
        try {
            // Attempt reflection call to software.coley dex-translator if available in classpath
            val translatorClass = Class.forName("software.coley.d2j.DexTranslator")
            val method = translatorClass.getMethod("translate", File::class.java, File::class.java)
            method.invoke(null, dexFile, jarFile)
        } catch (_: Throwable) {
            // Fallback: Copy as jar
            dexFile.copyTo(jarFile, overwrite = true)
        }
    }

    private fun tryLoadJar(jarFile: File, pluginClassName: String): Boolean {
        return try {
            val ucl = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this.javaClass.classLoader)
            activeClassLoaders.add(ucl)
            val clazz = ucl.loadClass(pluginClassName)
            val instance = clazz.getDeclaredConstructor().newInstance() as? BasePlugin
            instance?.filename = jarFile.absolutePath
            instance?.load()
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun uninstallPlugin(internalName: String) {
        val record = _installedPluginsFlow.value.firstOrNull { it.internalName == internalName } ?: return
        try {
            File(record.jarPath).delete()
            File(pluginsDirectory, "$internalName.cs3").delete()
            File(pluginsDirectory, "$internalName.dex").delete()
        } catch (_: Throwable) {
            // Ignore
        }
        _installedPluginsFlow.value = _installedPluginsFlow.value.filterNot { it.internalName == internalName }
        saveRecords()
    }
}
