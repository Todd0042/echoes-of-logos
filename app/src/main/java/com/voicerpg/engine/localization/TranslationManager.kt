package com.voicerpg.engine.localization

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class ModelDownloadStatus {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

/**
 * Universal On-Device Translation Engine.
 * 
 * Supports all 59 languages available in Google ML Kit on-device translation.
 * Uses a 3-tier architecture:
 * 1. Tier 1: Pre-compiled offline JSON dictionaries / APK assets for zero-latency instant rendering.
 * 2. Tier 2: On-device ML Kit neural translator backed by persistent disk cache (translations_<lang>.json).
 * 3. Tier 3: Graceful fallback to original text.
 */
object TranslationManager {

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _currentRegion = MutableStateFlow("")
    val currentRegion: StateFlow<String> = _currentRegion.asStateFlow()

    // Download status for gatekeeper standby and progress tracking
    private val _downloadStatus = MutableStateFlow(ModelDownloadStatus.IDLE)
    val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadTargetName = MutableStateFlow("")
    val downloadTargetName: StateFlow<String> = _downloadTargetName.asStateFlow()

    private var progressJob: Job? = null

    // Version counter incremented when new dynamic translations arrive to trigger Compose recomposition
    private val _translationVersion = MutableStateFlow(0)
    val translationVersion: StateFlow<Int> = _translationVersion.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeTranslator: Translator? = null
    private var activeTargetLanguage: String = "en"
    private var isModelDownloaded = false

    private var appContext: Context? = null
    private val assetDictionaries = ConcurrentHashMap<String, Map<String, String>>()

    // In-memory cache for dynamic translations: [lang:text] -> translatedText
    private val dynamicCache = ConcurrentHashMap<String, String>()

    // Disk cache directory
    private var cacheDir: File? = null

    fun initialize(context: Context? = null) {
        appContext = context?.applicationContext
        cacheDir = context?.cacheDir
        loadAssetDictionary(_currentLanguage.value)
        loadDiskCache(_currentLanguage.value)
    }

    fun dismissDownloadBanner() {
        _downloadStatus.value = ModelDownloadStatus.IDLE
    }

    fun loadAssetDictionary(lang: String) {
        val clean = lang.lowercase().trim().take(2)
        if (clean == "en" || clean.isBlank()) return
        if (assetDictionaries.containsKey(clean)) return
        val ctx = appContext ?: return
        try {
            val assetPath = "game/localization/strings_$clean.json"
            ctx.assets.open(assetPath).use { inputStream ->
                val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String>? = com.google.gson.Gson().fromJson(reader, type)
                if (!map.isNullOrEmpty()) {
                    assetDictionaries[clean] = map
                    _translationVersion.value++
                }
            }
        } catch (_: Throwable) {
            // Asset might not exist or test environment without assets
        }
    }

    private fun loadDiskCache(lang: String) {
        val clean = lang.lowercase().trim().take(2)
        if (clean == "en" || clean.isBlank()) return
        val dir = cacheDir ?: return
        scope.launch {
            try {
                val file = File(dir, "translations_$clean.json")
                if (file.exists()) {
                    file.reader(Charsets.UTF_8).use { reader ->
                        val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                        val map: Map<String, String>? = com.google.gson.Gson().fromJson(reader, type)
                        map?.forEach { (k, v) ->
                            dynamicCache["$clean:$k"] = v
                        }
                    }
                    _translationVersion.value++
                }
            } catch (_: Throwable) {}
        }
    }

    private fun persistDiskCache(lang: String) {
        val clean = lang.lowercase().trim().take(2)
        if (clean == "en" || clean.isBlank()) return
        val dir = cacheDir ?: return
        scope.launch {
            try {
                val prefix = "$clean:"
                val entries = dynamicCache.entries
                    .filter { it.key.startsWith(prefix) }
                    .associate { it.key.removePrefix(prefix) to it.value }
                if (entries.isNotEmpty()) {
                    val file = File(dir, "translations_$clean.json")
                    file.writer(Charsets.UTF_8).use { writer ->
                        com.google.gson.Gson().toJson(entries, writer)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Checks if the ML Kit translation model for the given language is already installed on the phone.
     */
    suspend fun isModelInstalled(lang: String): Boolean {
        val clean = lang.lowercase().trim().take(2)
        if (clean == "en" || clean.isBlank()) return true
        val mlkitTarget = TranslateLanguage.fromLanguageTag(clean) ?: return false
        val remoteModel = TranslateRemoteModel.Builder(mlkitTarget).build()
        val modelManager = RemoteModelManager.getInstance()
        return suspendCancellableCoroutine { cont ->
            modelManager.isModelDownloaded(remoteModel)
                .addOnSuccessListener { downloaded ->
                    if (cont.isActive) cont.resume(downloaded)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    fun setLanguageAndRegion(lang: String, region: String = "") {
        val cleanLang = lang.lowercase().trim().take(2)
        val cleanRegion = region.uppercase().trim()
        _currentLanguage.value = cleanLang
        _currentRegion.value = cleanRegion
        loadAssetDictionary(cleanLang)
        loadDiskCache(cleanLang)

        if (cleanLang == "en" || cleanLang.isBlank()) {
            activeTranslator?.close()
            activeTranslator = null
            activeTargetLanguage = "en"
            isModelDownloaded = true
            _downloadStatus.value = ModelDownloadStatus.IDLE
            return
        }

        if (cleanLang != activeTargetLanguage) {
            activeTargetLanguage = cleanLang
            val mlkitTarget = TranslateLanguage.fromLanguageTag(cleanLang)
            val langDisplayName = Locale(cleanLang).getDisplayLanguage(Locale.ENGLISH)
                .ifBlank { cleanLang.uppercase() }
            _downloadTargetName.value = langDisplayName

            if (mlkitTarget != null) {
                try {
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(mlkitTarget)
                        .build()
                    val translator = Translation.getClient(options)
                    activeTranslator = translator

                    val remoteModel = TranslateRemoteModel.Builder(mlkitTarget).build()
                    val modelManager = RemoteModelManager.getInstance()
                    modelManager.isModelDownloaded(remoteModel)
                        .addOnSuccessListener { downloaded ->
                            if (downloaded) {
                                isModelDownloaded = true
                                _downloadStatus.value = ModelDownloadStatus.IDLE
                                _translationVersion.value++
                                logInfo("TranslationManager", "MLKit neural model already present for $langDisplayName")
                            } else {
                                startDownload(translator, langDisplayName)
                            }
                        }
                        .addOnFailureListener {
                            startDownload(translator, langDisplayName)
                        }
                } catch (t: Throwable) {
                    logWarn("TranslationManager", "MLKit setup skipped: ${t.message}")
                }
            }
        }
    }

    private fun logInfo(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logWarn(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun startDownload(translator: Translator, langName: String) {
        _downloadStatus.value = ModelDownloadStatus.DOWNLOADING
        _downloadProgress.value = 0.05f

        progressJob?.cancel()
        progressJob = scope.launch {
            var p = 0.05f
            while (p < 0.90f) {
                delay(250)
                p += 0.02f
                _downloadProgress.value = p
            }
        }

        // Unconditionally allow cellular data - zero Wi-Fi pause
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                progressJob?.cancel()
                _downloadProgress.value = 1.0f
                _downloadStatus.value = ModelDownloadStatus.COMPLETED
                isModelDownloaded = true
                _translationVersion.value++
                logInfo("TranslationManager", "MLKit neural model successfully downloaded for $langName")
                scope.launch {
                    delay(2000)
                    if (_downloadStatus.value == ModelDownloadStatus.COMPLETED) {
                        _downloadStatus.value = ModelDownloadStatus.IDLE
                    }
                }
            }
            .addOnFailureListener { error ->
                progressJob?.cancel()
                _downloadStatus.value = ModelDownloadStatus.FAILED
                isModelDownloaded = false
                logWarn("TranslationManager", "MLKit model download failed for $langName: ${error.message}")
            }
    }

    /**
     * Translates text. Protects dynamic bracketed tokens like {player} or {metric}
     * from neural model degradation.
     */
    fun translate(text: String, targetLang: String = _currentLanguage.value): String {
        if (text.isBlank()) return text
        val lang = targetLang.lowercase().trim().take(2)
        if (lang == "en" || lang.isBlank()) return text

        // 1. Direct or fuzzy lookup in pre-compiled dictionary
        val dictMatch = lookupDictionary(text, lang)
        if (dictMatch != null) return dictMatch

        // 2. Pattern matches for compound UI strings
        val patternMatch = matchPattern(text, lang)
        if (patternMatch != null) return patternMatch

        // 3. Dynamic cache lookup
        val cacheKey = "$lang:$text"
        val cached = dynamicCache[cacheKey]
        if (cached != null) return cached

        // 4. Queue background MLKit on-device translation
        enqueueDynamicTranslation(text, lang)

        return text
    }

    /**
     * Suspending translation for TTS and narration where waiting for on-device translation
     * guarantees spoken audio is delivered in the player's chosen language.
     */
    suspend fun translateAsync(text: String, targetLang: String = _currentLanguage.value): String {
        if (text.isBlank()) return text
        val lang = targetLang.lowercase().trim().take(2)
        if (lang == "en" || lang.isBlank()) return text

        val dictMatch = lookupDictionary(text, lang)
        if (dictMatch != null) return dictMatch

        val patternMatch = matchPattern(text, lang)
        if (patternMatch != null) return patternMatch

        val cacheKey = "$lang:$text"
        val cached = dynamicCache[cacheKey]
        if (cached != null) return cached

        val translator = activeTranslator
        if (translator != null && lang == activeTargetLanguage) {
            // Mask variable placeholders to protect from neural degradation
            val (maskedText, tokenMap) = maskPlaceholders(text)
            return suspendCancellableCoroutine { cont ->
                translator.translate(maskedText)
                    .addOnSuccessListener { translated ->
                        if (!translated.isNullOrBlank()) {
                            val unmasked = unmaskPlaceholders(translated, tokenMap)
                            dynamicCache[cacheKey] = unmasked
                            persistDiskCache(lang)
                            cont.resume(unmasked)
                        } else {
                            cont.resume(text)
                        }
                    }
                    .addOnFailureListener {
                        cont.resume(text)
                    }
            }
        }

        return text
    }

    private fun enqueueDynamicTranslation(text: String, lang: String) {
        val translator = activeTranslator ?: return
        if (lang != activeTargetLanguage) return

        val (maskedText, tokenMap) = maskPlaceholders(text)
        translator.translate(maskedText)
            .addOnSuccessListener { translated ->
                if (!translated.isNullOrBlank()) {
                    val unmasked = unmaskPlaceholders(translated, tokenMap)
                    val cacheKey = "$lang:$text"
                    dynamicCache[cacheKey] = unmasked
                    _translationVersion.value++
                    persistDiskCache(lang)
                }
            }
            .addOnFailureListener {
                // Ignore failure, original text remains visible
            }
    }

    private fun maskPlaceholders(text: String): Pair<String, Map<String, String>> {
        val regex = Regex("""\{[a-zA-Z0-9_]+\}""")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return text to emptyMap()

        val tokenMap = mutableMapOf<String, String>()
        var masked = text
        matches.forEachIndexed { i, m ->
            val placeholder = "__VAR_${i}__"
            tokenMap[placeholder] = m.value
            masked = masked.replace(m.value, placeholder)
        }
        return masked to tokenMap
    }

    private fun unmaskPlaceholders(translated: String, tokenMap: Map<String, String>): String {
        var result = translated
        tokenMap.forEach { (placeholder, original) ->
            result = result.replace(placeholder, original)
        }
        return result
    }

    private fun matchPattern(text: String, lang: String): String? {
        val trimmed = text.trim()
        val continueSlotRegex = Regex("""^CONTINUE\s+\[SLOT\s+(\d+)\]$""", RegexOption.IGNORE_CASE)
        val continueMatch = continueSlotRegex.find(trimmed)
        if (continueMatch != null) {
            val slotNum = continueMatch.groupValues[1]
            val cont = translate("CONTINUE", lang)
            val slot = translate("SLOT", lang)
            return "$cont [$slot $slotNum]"
        }

        val optRegex = Regex("""^Option\s+(\d+):\s*(.*)$""", RegexOption.IGNORE_CASE)
        val optMatch = optRegex.find(trimmed)
        if (optMatch != null) {
            val num = optMatch.groupValues[1]
            val rest = optMatch.groupValues[2]
            val optWord = translate("Option", lang)
            val translatedRest = translate(rest, lang)
            return "$optWord $num: $translatedRest"
        }

        return null
    }

    fun lookupDictionary(text: String, lang: String): String? {
        val normalized = text.trim()

        // 1. Check loaded JSON asset dictionary for this language
        val assetMap = assetDictionaries[lang]
        if (assetMap != null) {
            assetMap[normalized]?.let { return it }
            assetMap.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value?.let { return it }
        }

        // 2. Check built-in fallback dictionary
        val langMap = BuiltInDictionaries.DICTIONARY[lang] ?: return null

        // Exact match
        langMap[normalized]?.let { return it }

        // Case-insensitive match
        langMap.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value?.let { return it }

        return null
    }
}
