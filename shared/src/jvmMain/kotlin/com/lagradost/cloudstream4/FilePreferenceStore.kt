package com.lagradost.cloudstream4

import com.mihon.common.preference.PreferenceData
import com.mihon.common.preference.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * File-backed PreferenceStore implementation for Desktop (Windows/JVM)
 * Persists all configuration and user preferences to %APPDATA%\CloudStream\preferences.json
 */
class FilePreferenceStore private constructor(private val file: File) : PreferenceStore {

    private val memoryStore = ConcurrentHashMap<String, Any>()
    private val changeNotifier = MutableSharedFlow<Pair<String, Any?>>(extraBufferCapacity = 64)
    private val saveScope = CoroutineScope(Dispatchers.IO)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        loadFromFile()
    }

    private fun loadFromFile() {
        try {
            if (file.exists()) {
                val text = file.readText()
                val parsed = json.parseToJsonElement(text) as? JsonObject
                parsed?.forEach { (k, element) ->
                    when {
                        element is JsonPrimitive -> {
                            val prim = element.jsonPrimitive
                            when {
                                prim.isString -> memoryStore[k] = prim.content
                                prim.booleanOrNull != null -> memoryStore[k] = prim.booleanOrNull!!
                                prim.longOrNull != null -> memoryStore[k] = prim.longOrNull!!
                                prim.intOrNull != null -> memoryStore[k] = prim.intOrNull!!
                                prim.floatOrNull != null -> memoryStore[k] = prim.floatOrNull!!
                                prim.doubleOrNull != null -> memoryStore[k] = prim.doubleOrNull!!
                            }
                        }
                        element is JsonArray -> {
                            val set = element.jsonArray.mapNotNull {
                                (it as? JsonPrimitive)?.content
                            }.toSet()
                            memoryStore[k] = set
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback to empty store if corrupt
        }
    }

    @Synchronized
    private fun saveToFile() {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val jsonMap = buildJsonObject {
                memoryStore.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        is Int -> put(key, JsonPrimitive(value))
                        is Long -> put(key, JsonPrimitive(value))
                        is Float -> put(key, JsonPrimitive(value))
                        is Double -> put(key, JsonPrimitive(value))
                        is Set<*> -> {
                            val array = JsonArray(value.mapNotNull { item ->
                                item?.let { JsonPrimitive(it.toString()) }
                            })
                            put(key, array)
                        }
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            }
            file.writeText(json.encodeToString(JsonElement.serializer(), jsonMap))
        } catch (_: Throwable) {
            // Ignore write errors during shutdown
        }
    }

    private fun notifyChanged(key: String, value: Any?) {
        saveScope.launch {
            saveToFile()
            changeNotifier.emit(key to value)
        }
    }

    override fun getString(key: String, defaultValue: String): PreferenceData<String> {
        return FilePreference(key, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): PreferenceData<Long> {
        return FilePreference(key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): PreferenceData<Int> {
        return FilePreference(key, defaultValue)
    }

    override fun getFloat(key: String, defaultValue: Float): PreferenceData<Float> {
        return FilePreference(key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): PreferenceData<Boolean> {
        return FilePreference(key, defaultValue)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defaultValue: Set<String>): PreferenceData<Set<String>> {
        return FilePreference(key, defaultValue)
    }

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T> {
        return object : PreferenceData<T> {
            override fun key(): String = key
            override fun get(): T {
                val str = memoryStore[key] as? String ?: return defaultValue
                return deserializer(str)
            }
            override fun set(value: T) {
                memoryStore[key] = serializer(value)
                notifyChanged(key, value)
            }
            override fun isSet(): Boolean = memoryStore.containsKey(key)
            override fun delete() {
                memoryStore.remove(key)
                notifyChanged(key, null)
            }
            override fun defaultValue(): T = defaultValue
            override fun changes(): Flow<T> = changeNotifier
                .filter { it.first == key }
                .map { get() }
                .onStart { emit(get()) }
                .distinctUntilChanged()
            override fun stateIn(scope: CoroutineScope): StateFlow<T> =
                changes().stateIn(scope, SharingStarted.Eagerly, get())
        }
    }

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): PreferenceData<T> {
        return object : PreferenceData<T> {
            override fun key(): String = key
            override fun get(): T {
                val raw = memoryStore[key] as? Int ?: return defaultValue
                return deserializer(raw)
            }
            override fun set(value: T) {
                memoryStore[key] = serializer(value)
                notifyChanged(key, value)
            }
            override fun isSet(): Boolean = memoryStore.containsKey(key)
            override fun delete() {
                memoryStore.remove(key)
                notifyChanged(key, null)
            }
            override fun defaultValue(): T = defaultValue
            override fun changes(): Flow<T> = changeNotifier
                .filter { it.first == key }
                .map { get() }
                .onStart { emit(get()) }
                .distinctUntilChanged()
            override fun stateIn(scope: CoroutineScope): StateFlow<T> =
                changes().stateIn(scope, SharingStarted.Eagerly, get())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): PreferenceData<Set<T>> {
        return object : PreferenceData<Set<T>> {
            override fun key(): String = key
            override fun get(): Set<T> {
                val set = memoryStore[key] as? Set<String> ?: return defaultValue
                return set.mapNotNull(deserializer).toSet()
            }
            override fun set(value: Set<T>) {
                val serialized = value.map(serializer).toSet()
                memoryStore[key] = serialized
                notifyChanged(key, value)
            }
            override fun isSet(): Boolean = memoryStore.containsKey(key)
            override fun delete() {
                memoryStore.remove(key)
                notifyChanged(key, null)
            }
            override fun defaultValue(): Set<T> = defaultValue
            override fun changes(): Flow<Set<T>> = changeNotifier
                .filter { it.first == key }
                .map { get() }
                .onStart { emit(get()) }
                .distinctUntilChanged()
            override fun stateIn(scope: CoroutineScope): StateFlow<Set<T>> =
                changes().stateIn(scope, SharingStarted.Eagerly, get())
        }
    }

    override fun getAll(): Map<String, *> = HashMap(memoryStore)

    private inner class FilePreference<T>(
        private val key: String,
        private val defaultValue: T,
    ) : PreferenceData<T> {
        override fun key(): String = key

        @Suppress("UNCHECKED_CAST")
        override fun get(): T = (memoryStore[key] as? T) ?: defaultValue

        override fun isSet(): Boolean = memoryStore.containsKey(key)

        override fun delete() {
            memoryStore.remove(key)
            notifyChanged(key, null)
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = changeNotifier
            .filter { it.first == key }
            .map { get() }
            .onStart { emit(get()) }
            .distinctUntilChanged()

        override fun stateIn(scope: CoroutineScope): StateFlow<T> {
            return changes().stateIn(scope, SharingStarted.Eagerly, get())
        }

        override fun set(value: T) {
            if (value != null) {
                memoryStore[key] = value
            } else {
                memoryStore.remove(key)
            }
            notifyChanged(key, value)
        }
    }

    companion object {
        val appDirectory: File by lazy {
            val appData = System.getenv("APPDATA")
            val baseDir = if (!appData.isNullOrBlank()) {
                File(appData, "CloudStream")
            } else {
                val userHome = System.getProperty("user.home") ?: "."
                File(userHome, ".cloudstream")
            }
            if (!baseDir.exists()) baseDir.mkdirs()
            baseDir
        }

        val defaultFile: File by lazy {
            File(appDirectory, "preferences.json")
        }

        val instance: FilePreferenceStore by lazy {
            FilePreferenceStore(defaultFile)
        }
    }
}
