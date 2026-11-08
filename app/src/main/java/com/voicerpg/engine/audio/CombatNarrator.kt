package com.voicerpg.engine.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Audio-first Screenless / Pocket Mode Combat Narrator.
 * Powered by Android's on-device TextToSpeech engine.
 * Allows playing the entire game hands-free and eyes-free (e.g. while walking, with phone in pocket,
 * or for visually impaired / blind players).
 */
class CombatNarrator(
    private val context: Context? = null
) : TextToSpeech.OnInitListener {

    companion object {
        /**
         * Standard baseline allowed English voice regions, excluding Nigeria and India
         * from general cast distribution by default unless explicitly configured on a character.
         */
        val DEFAULT_ALLOWED_VOICE_REGIONS = listOf("US", "CA", "GB", "AU", "IE", "ZA")

        /**
         * Extracts the 2-letter uppercase ISO country/region code from a voice
         * (e.g. "US", "GB", "AU", "IN", "CA", "IE", "ZA", "NG").
         */
        fun getVoiceRegionCode(voice: Voice?): String {
            if (voice == null) return ""
            val country = voice.locale.country.uppercase(Locale.ROOT)
            if (country.isNotBlank()) return country
            val nameLower = voice.name.lowercase(Locale.ROOT)
            return when {
                nameLower.startsWith("en-us") || nameLower.contains("-us-") -> "US"
                nameLower.startsWith("en-gb") || nameLower.contains("-gb-") -> "GB"
                nameLower.startsWith("en-au") || nameLower.contains("-au-") -> "AU"
                nameLower.startsWith("en-in") || nameLower.contains("-in-") -> "IN"
                nameLower.startsWith("en-ng") || nameLower.contains("-ng-") -> "NG"
                nameLower.startsWith("en-ca") || nameLower.contains("-ca-") -> "CA"
                nameLower.startsWith("en-za") || nameLower.contains("-za-") -> "ZA"
                nameLower.startsWith("en-ie") || nameLower.contains("-ie-") -> "IE"
                else -> ""
            }
        }

        val DEFAULT_BUILTIN_PREFERRED_VOICE_IDS: Map<String, String> = mapOf(
            "commander_america" to "en-us-x-iob-local",
            "elon" to "en-us-x-tpc-local",
            "narrator_karoline" to "en-us-x-tpf-local",
            "narrator_kayleigh" to "en-us-x-sfg-local",
            "narrator_hulk" to "en-gb-x-gbd-local",
            "pelosi" to "en-us-x-tpf-local",
            "kamala" to "en-us-x-sfg-local",
            "obama" to "en-us-x-iog-local",
            "biden" to "en-us-x-iol-local",
            "rocket_man" to "en-au-x-aub-local",
            "media_anchor" to "en-us-x-tpd-local",
            "cedric" to "en-gb-x-gbd-local",
            "aethel" to "en-gb-x-gba-local",
            "lyra" to "en-us-x-tpf-local",
            "zephyr" to "en-us-x-tpc-local",
            "malakor" to "en-us-x-tpd-local"
        )

        /**
         * Fallback preferred voice packs per speaker, declared by the active game's
         * character data files (assets/game/characters/<name>.json).
         */
        val PREFERRED_VOICE_IDS: Map<String, String>
            get() = com.voicerpg.engine.content.GameContent.preferredVoiceIds.ifEmpty { DEFAULT_BUILTIN_PREFERRED_VOICE_IDS }

        /**
         * Resolves the voice a speaker should use: a persisted player override wins when its
         * voice model is still installed, then the first-try preferred pack, then the heuristic.
         */
        internal fun resolvePreferredVoiceName(
            speakerId: String,
            heuristicName: String?,
            installedNames: Set<String>,
            persistedAssignments: Map<String, String>,
            preferredByDefault: Map<String, String>
        ): String? {
            persistedAssignments[speakerId]?.let { if (it in installedNames) return it }
            val preferredVoice = com.voicerpg.engine.content.GameContent.preferredVoiceIds[speakerId]
                ?: preferredByDefault[speakerId]
            preferredVoice?.let { if (it in installedNames) return it }
            return heuristicName
        }

        private val SAINT_ABBREVIATION_REGEX = Regex("""\bSt\.?\s+([A-Z][a-zA-Z]*)""")
    }

    internal fun normalizeTtsText(input: String): String {
        var text = input
        // 1. Data-driven project/story pronunciation overrides from manifest (content neutrality)
        com.voicerpg.engine.content.GameContent.manifest.ttsReplacements.forEach { (target, replacement) ->
            text = text.replace(target, replacement, ignoreCase = true)
        }
        // 2. Generic English title/place abbreviation expansion: "St. <Name>" or "St <Name>" -> "Saint <Name>"
        // Prevents on-device TTS from pronouncing names like "St. Augustine" as "Street. Augustine"
        text = SAINT_ABBREVIATION_REGEX.replace(text) { matchResult ->
            "Saint ${matchResult.groupValues[1]}"
        }
        return text.replace(Regex("""[ \t]+"""), " ").trim()
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var pendingSpeechOnDone: (() -> Unit)? = null
    private var activeUtteranceId: String? = null

    // Persisted per-speaker voice assignments restored from the save file (speakerId -> voice name)
    private var pendingVoiceAssignments: Map<String, String> = emptyMap()

    // Dynamic Multi-Voice Character Profile Registry
    private var defaultVoice: Voice? = null
    private val dynamicSpeakerVoices = mutableMapOf<String, Voice?>()
    private val registeredSpeakers = mutableListOf<DialogueSpeaker>()
    private val customPreferredVoiceIds = mutableMapOf<String, String>()

    fun registerSpeaker(speaker: DialogueSpeaker, preferredVoiceId: String? = null) {
        if (registeredSpeakers.none { it.id == speaker.id }) {
            registeredSpeakers.add(speaker)
        }
        if (preferredVoiceId != null) {
            customPreferredVoiceIds[speaker.id] = preferredVoiceId
        }
    }

    fun registerSpeakers(speakers: Collection<DialogueSpeaker>) {
        speakers.forEach { speaker ->
            val pref = com.voicerpg.engine.content.GameContent.preferredVoiceIds[speaker.id]
            registerSpeaker(speaker, pref)
        }
    }

    fun getRegisteredSpeakers(): List<DialogueSpeaker> {
        if (registeredSpeakers.isEmpty()) {
            registerSpeakers(com.voicerpg.engine.content.GameContent.allSpeakers)
        }
        return registeredSpeakers.toList()
    }

    private val _availableVoiceCount = MutableStateFlow(0)
    val availableVoiceCount: StateFlow<Int> = _availableVoiceCount.asStateFlow()

    private val _installedVoiceCount = MutableStateFlow(0)
    val installedVoiceCount: StateFlow<Int> = _installedVoiceCount.asStateFlow()

    private val _isEyesFreeMode = MutableStateFlow(false)
    val isEyesFreeMode: StateFlow<Boolean> = _isEyesFreeMode.asStateFlow()

    private val _isPocketGuardLocked = MutableStateFlow(true)
    val isPocketGuardLocked: StateFlow<Boolean> = _isPocketGuardLocked.asStateFlow()

    private val _isPocketGuardEnabled = MutableStateFlow(true)
    val isPocketGuardEnabled: StateFlow<Boolean> = _isPocketGuardEnabled.asStateFlow()

    private val _isCombatNarrationEnabled = MutableStateFlow(true)
    val isCombatNarrationEnabled: StateFlow<Boolean> = _isCombatNarrationEnabled.asStateFlow()

    private val _isNarrationEnabled = MutableStateFlow(true)
    val isNarrationEnabled: StateFlow<Boolean> = _isNarrationEnabled.asStateFlow()

    private val _isReadChoicesEnabled = MutableStateFlow(true)
    val isReadChoicesEnabled: StateFlow<Boolean> = _isReadChoicesEnabled.asStateFlow()

    private val _isAutoReadHubChoices = MutableStateFlow(true)
    val isAutoReadHubChoices: StateFlow<Boolean> = _isAutoReadHubChoices.asStateFlow()

    private val _speechRate = MutableStateFlow(1.05f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _isCharacterPitchEnabled = MutableStateFlow(true)
    val isCharacterPitchEnabled: StateFlow<Boolean> = _isCharacterPitchEnabled.asStateFlow()

    private val _isSpeakerAttributionEnabled = MutableStateFlow(true)
    val isSpeakerAttributionEnabled: StateFlow<Boolean> = _isSpeakerAttributionEnabled.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val utteranceCounter = java.util.concurrent.atomic.AtomicLong(0)

    private val _isCombatActive = MutableStateFlow(false)
    val isCombatActive: StateFlow<Boolean> = _isCombatActive.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedRegion = MutableStateFlow("")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    fun setSelectedLanguage(langCode: String) {
        val clean = langCode.lowercase().take(2)
        _selectedLanguage.value = clean
        updateTtsLocale()
    }

    fun setSelectedRegion(regionCode: String) {
        _selectedRegion.value = regionCode.uppercase(Locale.ROOT).trim()
        updateTtsLocale()
    }

    fun setSelectedLanguageAndRegion(langCode: String, regionCode: String = "") {
        val clean = langCode.lowercase().take(2)
        _selectedLanguage.value = clean
        _selectedRegion.value = regionCode.uppercase(Locale.ROOT).trim()
        updateTtsLocale()
    }

    private fun updateTtsLocale() {
        tts?.let { engine ->
            val lang = _selectedLanguage.value
            val reg = _selectedRegion.value
            val locale = if (reg.isNotBlank()) {
                Locale(lang, reg)
            } else {
                Locale(lang)
            }
            try {
                engine.setLanguage(locale)
            } catch (_: Exception) {}
            assignCharacterVoices(engine)
        }
    }

    fun isTtsLanguageAvailable(langCode: String, regionCode: String = ""): Boolean {
        val engine = tts ?: return false
        val locale = if (regionCode.isNotBlank()) Locale(langCode, regionCode) else Locale(langCode)
        val result = engine.isLanguageAvailable(locale)
        return result != TextToSpeech.LANG_NOT_SUPPORTED && result != TextToSpeech.LANG_MISSING_DATA
    }

    fun setCombatActive(active: Boolean) {
        _isCombatActive.value = active
    }

    init {
        if (context != null) {
            try {
                tts = TextToSpeech(context, this)
            } catch (_: Exception) {
                // Headless/test fallback
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                try {
                    engine.setAudioAttributes(audioAttributes)
                } catch (_: Exception) {}
                val result = engine.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setSpeechRate(1.15f) // Crisp, brisk pacing for combat flow
                    engine.setPitch(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId != null && utteranceId == activeUtteranceId) {
                                _isSpeaking.value = true
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null && utteranceId == activeUtteranceId) {
                                _isSpeaking.value = false
                                activeUtteranceId = null
                                val cb = pendingSpeechOnDone
                                pendingSpeechOnDone = null
                                cb?.invoke()
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (utteranceId != null && utteranceId == activeUtteranceId) {
                                _isSpeaking.value = false
                                activeUtteranceId = null
                                pendingSpeechOnDone = null
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onError(utteranceId, TextToSpeech.ERROR)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId != null && utteranceId == activeUtteranceId) {
                                try {
                                    defaultVoice?.let { tts?.voice = it }
                                } catch (_: Exception) {}
                                _isSpeaking.value = false
                                activeUtteranceId = null
                                val cb = pendingSpeechOnDone
                                pendingSpeechOnDone = null
                                cb?.invoke()
                            }
                        }
                    })
                    assignCharacterVoices(engine)
                    isTtsInitialized = true
                }
            }
        }
    }

    private fun isVoiceInstalledAndUsable(engine: TextToSpeech, voice: Voice?): Boolean {
        if (voice == null) return false
        val features = voice.features ?: emptySet()
        val isNotInstalled = features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ||
                features.contains("notInstalled")
        if (isNotInstalled) return false
        if (voice.isNetworkConnectionRequired) return false
        return try {
            engine.isLanguageAvailable(voice.locale) >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Selects the most appropriate installed voice for a speaker: any persisted player override
     * for that speaker, else the first-try preferred pack, else the heuristic candidate.
     */
    private fun applyVoicePreference(
        speakerId: String,
        heuristic: Voice?,
        installed: List<Voice>
    ): Voice? {
        val chosenName = resolvePreferredVoiceName(
            speakerId = speakerId,
            heuristicName = heuristic?.name,
            installedNames = installed.map { it.name }.toSet(),
            persistedAssignments = pendingVoiceAssignments,
            preferredByDefault = PREFERRED_VOICE_IDS
        )
        return installed.firstOrNull { it.name == chosenName } ?: heuristic
    }

    /**
     * Programmatically discovers installed device voices and maps distinct voice models
     * to different characters (e.g. distinct male/female or tone models).
     * Strictly verifies that voices are downloaded and installed offline on the device
     * before assigning, safely falling back to defaultVoice to prevent speech skipping.
     */
    private fun assignCharacterVoices(engine: TextToSpeech) {
        try {
            val voices = engine.voices ?: emptySet()
            _availableVoiceCount.value = voices.size
            defaultVoice = engine.voice

            // Only consider voices that are verified to be installed and available offline on this device
            val installedVoices = voices.filter { isVoiceInstalledAndUsable(engine, it) }
            _installedVoiceCount.value = installedVoices.size

            // Filter out generic language aliases (e.g. en-US-language, en-AU-language)
            val physicalVoices = installedVoices.filter { v ->
                !v.name.endsWith("-language", ignoreCase = true) &&
                        !(v.features?.contains("legacySetLanguageVoice") ?: false)
            }.ifEmpty { installedVoices }

            val englishPhysicalVoices = physicalVoices.filter {
                it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true)
            }.ifEmpty { physicalVoices }

            android.util.Log.d("VoiceRPG_TTS", "Installed: ${installedVoices.size}, Physical English: ${englishPhysicalVoices.size}")

            // Google TTS & third-party voice classification tokens:
            // Female codes: sfg (Voice 1), iom (Voice 3), tpd (Voice 5), tpf (Voice 7), gba, gbb, gbg, aua, auc, inc, or "female"
            // Male codes: iob (Voice 2), iog (Voice 4), tpc (Voice 6), iol (Voice 8), rjs, gbc, gbd, aub, aud, ina, or "male"
            val femaleTokens = listOf("female", "#f", "-f-", "_f_", "_female", "-f0", "smtf", "sfg", "iom", "tpd", "tpf", "gba", "gbb", "gbg", "aua", "auc", "inc")
            val maleTokens = listOf("male", "#m", "-m-", "_m_", "_male", "-m0", "smtm", "iob", "iog", "tpc", "iol", "rjs", "gbc", "gbd", "aub", "aud", "ina")

            val femaleVoices = englishPhysicalVoices.filter { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                femaleTokens.any { lower.contains(it) } && !lower.contains("male")
            }

            val maleVoices = englishPhysicalVoices.filter { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                maleTokens.any { lower.contains(it) } && !lower.contains("female")
            }

            // Remaining unclassified voices to supplement pools
            val otherVoices = englishPhysicalVoices.filter { it !in maleVoices && it !in femaleVoices }

            val malePool = (maleVoices + otherVoices.filterIndexed { i, _ -> i % 2 == 1 }).ifEmpty { englishPhysicalVoices }
            val femalePool = (femaleVoices + otherVoices.filterIndexed { i, _ -> i % 2 == 0 }).ifEmpty { englishPhysicalVoices }

            // British narrator voice if present, else default
            val britishStoryteller = englishPhysicalVoices.firstOrNull {
                it.name.contains("rjs") || (it.locale.country.equals("GB", ignoreCase = true) && it.name in maleTokens)
            } ?: defaultVoice

            val distinctMalePool = if (britishStoryteller != null && malePool.size > 1) {
                malePool.filter { it != britishStoryteller }
            } else {
                malePool
            }

            val allSpeakers = getRegisteredSpeakers().ifEmpty {
                listOf(com.voicerpg.engine.content.GameContent.narrator)
            }

            var femaleIdx = 0
            var maleIdx = 0
            var neutralIdx = 0

            fun List<Voice>.filterByRegion(regions: List<String>): List<Voice> {
                val matched = filter { getVoiceRegionCode(it) in regions }
                return matched.ifEmpty { this }
            }

            // Dynamically distribute available physical voices respecting character gender and region
            allSpeakers.forEach { speaker ->
                val preferred = customPreferredVoiceIds[speaker.id] ?: PREFERRED_VOICE_IDS[speaker.id]
                val char = com.voicerpg.engine.content.GameContent.characterById(speaker.id)
                val charGender = speaker.gender ?: char?.gender
                val isFemale = charGender?.equals("female", ignoreCase = true) == true
                val isMale = charGender?.equals("male", ignoreCase = true) == true

                val rawRegions = speaker.allowedVoiceRegions.ifEmpty {
                    char?.allowedVoiceRegions?.ifEmpty { DEFAULT_ALLOWED_VOICE_REGIONS } ?: DEFAULT_ALLOWED_VOICE_REGIONS
                }.map { it.uppercase(Locale.ROOT) }

                val candidate = when {
                    preferred != null && englishPhysicalVoices.any { it.name == preferred } ->
                        englishPhysicalVoices.first { it.name == preferred }
                    speaker.isNarrator || speaker.id == "narrator" -> {
                        val narratorPool = englishPhysicalVoices.filterByRegion(rawRegions)
                        britishStoryteller ?: narratorPool.firstOrNull() ?: defaultVoice
                    }
                    isFemale -> {
                        val regional = femalePool.filterByRegion(rawRegions)
                        regional[femaleIdx++ % regional.size]
                    }
                    isMale -> {
                        val regional = distinctMalePool.filterByRegion(rawRegions)
                        regional[maleIdx++ % regional.size]
                    }
                    else -> {
                        val regional = englishPhysicalVoices.filterByRegion(rawRegions)
                        regional[neutralIdx++ % regional.size]
                    }
                }
                dynamicSpeakerVoices[speaker.id] = applyVoicePreference(speaker.id, candidate, englishPhysicalVoices)
                android.util.Log.d("VoiceRPG_TTS", "Assigned ${speaker.name} (${speaker.id}, gender=$charGender, regions=$rawRegions): ${dynamicSpeakerVoices[speaker.id]?.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceRPG_TTS", "Error assigning voices", e)
            getRegisteredSpeakers().forEach { speaker ->
                dynamicSpeakerVoices[speaker.id] = defaultVoice
            }
        }
    }

    fun setEyesFreeMode(enabled: Boolean, lockGuard: Boolean = true) {
        _isEyesFreeMode.value = enabled
        if (enabled) {
            if (lockGuard && _isPocketGuardEnabled.value) {
                _isPocketGuardLocked.value = true
            } else {
                _isPocketGuardLocked.value = false
            }
        } else {
            _isPocketGuardLocked.value = false
        }
    }

    fun toggleEyesFreeMode(): Boolean {
        val newState = !_isEyesFreeMode.value
        setEyesFreeMode(newState, lockGuard = true)
        return newState
    }

    fun unlockPocketGuard() {
        _isPocketGuardLocked.value = false
    }

    fun lockPocketGuard() {
        if (_isPocketGuardEnabled.value) {
            _isPocketGuardLocked.value = true
        }
    }

    fun setPocketGuardLocked(locked: Boolean) {
        _isPocketGuardLocked.value = locked && _isPocketGuardEnabled.value
    }

    fun setPocketGuardEnabled(enabled: Boolean) {
        _isPocketGuardEnabled.value = enabled
        if (!enabled) {
            _isPocketGuardLocked.value = false
        }
    }

    fun togglePocketGuardEnabled(): Boolean {
        _isPocketGuardEnabled.value = !_isPocketGuardEnabled.value
        if (!_isPocketGuardEnabled.value) {
            _isPocketGuardLocked.value = false
        }
        return _isPocketGuardEnabled.value
    }

    fun setCombatNarrationEnabled(enabled: Boolean) {
        _isCombatNarrationEnabled.value = enabled
    }

    fun toggleCombatNarrationEnabled(): Boolean {
        _isCombatNarrationEnabled.value = !_isCombatNarrationEnabled.value
        return _isCombatNarrationEnabled.value
    }

    internal fun setPocketGuardLockedForTesting(locked: Boolean) {
        _isPocketGuardLocked.value = locked
    }

    fun setNarrationEnabled(enabled: Boolean) {
        _isNarrationEnabled.value = enabled
        if (!enabled) stop()
    }

    fun toggleNarration(): Boolean {
        _isNarrationEnabled.value = !_isNarrationEnabled.value
        if (!_isNarrationEnabled.value) stop()
        return _isNarrationEnabled.value
    }

    fun setReadChoicesEnabled(enabled: Boolean) {
        _isReadChoicesEnabled.value = enabled
    }

    fun toggleReadChoices(): Boolean {
        _isReadChoicesEnabled.value = !_isReadChoicesEnabled.value
        return _isReadChoicesEnabled.value
    }

    fun setAutoReadHubChoices(enabled: Boolean) {
        _isAutoReadHubChoices.value = enabled
    }

    fun toggleAutoReadHubChoices(): Boolean {
        _isAutoReadHubChoices.value = !_isAutoReadHubChoices.value
        return _isAutoReadHubChoices.value
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        tts?.setSpeechRate(rate)
    }

    fun setSpeakerAttributionEnabled(enabled: Boolean) {
        _isSpeakerAttributionEnabled.value = enabled
    }

    fun toggleSpeakerAttribution(): Boolean {
        _isSpeakerAttributionEnabled.value = !_isSpeakerAttributionEnabled.value
        return _isSpeakerAttributionEnabled.value
    }

    fun setCharacterPitchEnabled(enabled: Boolean) {
        _isCharacterPitchEnabled.value = enabled
    }

    fun getInstalledVoices(): Set<Voice> = tts?.voices ?: emptySet()

    fun refreshInstalledVoices() {
        tts?.let { engine ->
            assignCharacterVoices(engine)
        }
    }

    /**
     * Restores persisted per-speaker voice assignments from a save file. The assignments are held
     * in reserve and applied whenever voices are (re)assigned, with graceful fallback to the
     * first-try preferred pack or heuristic if a saved voice is no longer installed.
     */
    fun setVoiceAssignments(assignments: Map<String, String>) {
        pendingVoiceAssignments = assignments
        tts?.let { engine ->
            try {
                assignCharacterVoices(engine)
            } catch (_: Exception) {}
        }
    }

    /**
     * Snapshots the currently assigned voice model name for every tracked speaker, suitable for
     * persisting into the save file.
     */
    fun getVoiceAssignments(): Map<String, String> =
        getRegisteredSpeakers().mapNotNull { speaker ->
            getVoiceForSpeaker(speaker)?.name?.let { speaker.id to it }
        }.toMap()

    fun openVoiceSettings(context: Context) {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val generalSettingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(installIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(ttsSettingsIntent)
            } catch (_: Exception) {
                try {
                    context.startActivity(generalSettingsIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun getVoiceForSpeaker(speaker: DialogueSpeaker): Voice? =
        dynamicSpeakerVoices[speaker.id] ?: defaultVoice

    fun setSpeakerVoice(speaker: DialogueSpeaker, voice: Voice) {
        dynamicSpeakerVoices[speaker.id] = voice
    }

    fun previewSpeakerVoice(speaker: DialogueSpeaker, onDone: (() -> Unit)? = null) {
        val samplePhrase = speaker.samplePhrase
            ?: "I am ${speaker.name}, ${speaker.title}. My voice stands ready."
        narrateDialogue(speaker, samplePhrase, emptyList(), force = true) {
            onDone?.invoke()
        }
    }

    fun getInstalledPhysicalVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        val voices = engine.voices ?: return emptyList()
        val installed = voices.filter { isVoiceInstalledAndUsable(engine, it) }
        val physical = installed.filter { v ->
            !v.name.endsWith("-language", ignoreCase = true) &&
                    !(v.features?.contains("legacySetLanguageVoice") ?: false)
        }.ifEmpty { installed }
        val english = physical.filter {
            it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true)
        }.ifEmpty { physical }
        return english.sortedBy { it.name }
    }

    fun cycleSpeakerVoice(speaker: DialogueSpeaker, onDone: (() -> Unit)? = null): Voice? {
        val physicalVoices = getInstalledPhysicalVoices()
        if (physicalVoices.isEmpty()) return null

        val char = com.voicerpg.engine.content.GameContent.characterById(speaker.id)
        val charGender = speaker.gender ?: char?.gender
        val isFemale = charGender?.equals("female", ignoreCase = true) == true
        val isMale = charGender?.equals("male", ignoreCase = true) == true

        val rawRegions = speaker.allowedVoiceRegions.ifEmpty {
            char?.allowedVoiceRegions?.ifEmpty { DEFAULT_ALLOWED_VOICE_REGIONS } ?: DEFAULT_ALLOWED_VOICE_REGIONS
        }.map { it.uppercase(Locale.ROOT) }

        val femaleTokens = listOf("female", "#f", "-f-", "_f_", "_female", "-f0", "smtf", "sfg", "iom", "tpd", "tpf", "gba", "gbb", "gbg", "aua", "auc", "inc")
        val maleTokens = listOf("male", "#m", "-m-", "_m_", "_male", "-m0", "smtm", "iob", "iog", "tpc", "iol", "rjs", "gbc", "gbd", "aub", "aud", "ina")

        val genderVoices = when {
            isFemale -> physicalVoices.filter { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                femaleTokens.any { lower.contains(it) } && !lower.contains("male")
            }.ifEmpty { physicalVoices }
            isMale -> physicalVoices.filter { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                maleTokens.any { lower.contains(it) } && !lower.contains("female")
            }.ifEmpty { physicalVoices }
            else -> physicalVoices
        }

        val eligibleVoices = genderVoices.filter { getVoiceRegionCode(it) in rawRegions }.ifEmpty { genderVoices }

        val current = getVoiceForSpeaker(speaker)
        val currentIndex = eligibleVoices.indexOfFirst { it.name == current?.name }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % eligibleVoices.size else 0
        val nextVoice = eligibleVoices[nextIndex]
        setSpeakerVoice(speaker, nextVoice)
        previewSpeakerVoice(speaker, onDone)
        return nextVoice
    }

    /**
     * Narrates a story dialogue node, including the speaker line and optionally
     * reading available choices/options aloud.
     */
    fun narrateDialogue(
        speaker: DialogueSpeaker,
        text: String,
        choices: List<DialogueChoice> = emptyList(),
        force: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        // If narration is turned off and we are not in Eyes-Free mode, skip TTS (unless forced)
        if (!force && !_isNarrationEnabled.value && !_isEyesFreeMode.value) {
            onDone()
            return
        }

        if (tts == null || !isTtsInitialized) {
            onDone()
            return
        }

        // Dynamically assign physical voice model per speaker if verified installed
        val speakerVoice = getVoiceForSpeaker(speaker)
        if (speakerVoice != null && isVoiceInstalledAndUsable(tts!!, speakerVoice)) {
            try {
                tts?.voice = speakerVoice
            } catch (_: Exception) {
                try { defaultVoice?.let { tts?.voice = it } } catch (_: Exception) {}
            }
        } else {
            try { defaultVoice?.let { tts?.voice = it } } catch (_: Exception) {}
        }

        // Apply character vocal inflection/pitch if enabled
        if (_isCharacterPitchEnabled.value) {
            tts?.setPitch(speaker.ttsPitch)
            tts?.setSpeechRate(_speechRate.value)
        } else {
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(_speechRate.value)
        }

        val cleanedText = text.replace("...", ". ").trim()
        val speakerPrefix = if (speaker.isNarrator || !_isSpeakerAttributionEnabled.value) "" else "${speaker.name} says: "

        val fullScript = StringBuilder()
        fullScript.append("$speakerPrefix$cleanedText")

        // Read choices aloud if setting enabled, or if in Screenless Pocket Mode with auto-read enabled
        val shouldReadChoices = choices.isNotEmpty() && (
            _isReadChoicesEnabled.value || (_isEyesFreeMode.value && _isAutoReadHubChoices.value)
        )
        if (shouldReadChoices) {
            fullScript.append(". Your options are: ")
            choices.forEachIndexed { index, choice ->
                fullScript.append("Option ${index + 1}: ${choice.text}. ")
            }
            fullScript.append("What is your command?")
        }

        speak(fullScript.toString(), force = true, preserveVoice = true, onDone = onDone)
    }

    /**
     * Speaks text aloud if eyes-free mode is active, or if force is true (e.g. for status queries).
     */
    fun speak(
        text: String,
        force: Boolean = false,
        preserveVoice: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        // If narration is suppressed and eyes-free is off, drop speech unless forced
        if (!force && !_isEyesFreeMode.value && !_isCombatNarrationEnabled.value) {
            onDone?.invoke()
            return
        }

        if (tts == null || !isTtsInitialized) {
            onDone?.invoke()
            return
        }

        if (!preserveVoice) {
            // Reset to Narrator voice for system / tactical announcements
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(_speechRate.value)
            getVoiceForSpeaker(com.voicerpg.engine.content.GameContent.narrator)?.let {
                try {
                    tts?.voice = it
                } catch (_: Exception) {}
            }
        }

        val utteranceId = "combat_tts_${utteranceCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId
        pendingSpeechOnDone = onDone
        _isSpeaking.value = true

        val translatedText = com.voicerpg.engine.localization.TranslationManager.translate(text)
        val speechText = normalizeTtsText(translatedText)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        var result = tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            // Safety fallback: if custom voice failed, reset to defaultVoice and retry immediately
            try {
                defaultVoice?.let { tts?.voice = it }
                result = tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (_: Exception) {}
        }

        if (result != TextToSpeech.SUCCESS) {
            _isSpeaking.value = false
            activeUtteranceId = null
            val cb = pendingSpeechOnDone
            pendingSpeechOnDone = null
            cb?.invoke()
        }
    }

    suspend fun speakSuspend(
        text: String,
        force: Boolean = false,
        preserveVoice: Boolean = false,
        timeoutMs: Long = 8000L
    ) {
        if (!force && !_isEyesFreeMode.value && !_isCombatNarrationEnabled.value) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                speak(text, force = force, preserveVoice = preserveVoice) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun stop() {
        pendingSpeechOnDone = null
        activeUtteranceId = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    internal fun setSpeakingForTesting(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    // =========================================================================
    // Tactical Combat Narration
    // =========================================================================

    fun narratePlayerTurn(hero: PartyMember, enemies: List<Enemy>, onDone: () -> Unit) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) {
            onDone()
            return
        }
        val aliveEnemies = enemies.filter { it.isAlive }
        val targeted = aliveEnemies.firstOrNull { it.isTargeted } ?: aliveEnemies.firstOrNull()
        val enemyCountDesc = when (aliveEnemies.size) {
            1 -> "One enemy remains"
            else -> "${aliveEnemies.size} enemies on field"
        }
        val targetDesc = if (targeted != null) ", target is ${targeted.name}" else ""
        val text = "${hero.name}'s turn. $enemyCountDesc$targetDesc. Ready to chant."
        speak(text, force = true, onDone = onDone)
    }

    suspend fun narratePlayerTurnSuspend(hero: PartyMember, enemies: List<Enemy>, timeoutMs: Long = 8000L) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                narratePlayerTurn(hero, enemies) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun narrateSpellCast(
        heroName: String,
        spellName: String,
        targetName: String,
        amount: Int,
        isHeal: Boolean,
        tierTitle: String?,
        defeatedNames: List<String> = emptyList(),
        effectNote: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) {
            onDone?.invoke()
            return
        }
        val prefix = if (tierTitle != null && (tierTitle.contains("Transcendental", ignoreCase = true) || tierTitle.contains("Mythic", ignoreCase = true))) {
            "$tierTitle resonance! "
        } else ""

        val action = if (isHeal) {
            "${prefix}$heroName casts $spellName to mend $targetName for $amount health."
        } else {
            "${prefix}$heroName strikes $targetName with $spellName for $amount damage."
        }
        val effectText = if (effectNote.isNullOrBlank()) "" else " $effectNote"

        val defeatText = when {
            defeatedNames.isEmpty() -> ""
            defeatedNames.size == 1 -> " ${defeatedNames.first()} is defeated!"
            else -> " ${defeatedNames.joinToString(", ")} are defeated!"
        }
        speak("$action$effectText$defeatText", force = true, onDone = onDone)
    }

    suspend fun narrateSpellCastSuspend(
        heroName: String,
        spellName: String,
        targetName: String,
        amount: Int,
        isHeal: Boolean,
        tierTitle: String?,
        defeatedNames: List<String> = emptyList(),
        effectNote: String? = null,
        timeoutMs: Long = 8000L
    ) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                narrateSpellCast(heroName, spellName, targetName, amount, isHeal, tierTitle, defeatedNames, effectNote) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun narrateEnemyAction(
        enemyName: String,
        targetHeroName: String,
        damage: Int,
        isFallen: Boolean = false,
        moveName: String? = null,
        statusNote: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) {
            onDone?.invoke()
            return
        }
        val actionText = if (!moveName.isNullOrBlank()) {
            "$enemyName unleashes $moveName on $targetHeroName for $damage damage."
        } else {
            "$enemyName attacks $targetHeroName for $damage damage."
        }
        val statusText = if (!statusNote.isNullOrBlank()) " $targetHeroName is $statusNote!" else ""
        val fallenDesc = if (isFallen) " $targetHeroName has fallen!" else ""
        speak("$actionText$statusText$fallenDesc", force = true, onDone = onDone)
    }

    suspend fun narrateEnemyActionSuspend(
        enemyName: String,
        targetHeroName: String,
        damage: Int,
        isFallen: Boolean = false,
        moveName: String? = null,
        statusNote: String? = null,
        timeoutMs: Long = 8000L
    ) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                narrateEnemyAction(enemyName, targetHeroName, damage, isFallen, moveName, statusNote) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun narrateReinforcements(count: Int, enemyNames: List<String>, onDone: (() -> Unit)? = null) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) {
            onDone?.invoke()
            return
        }
        val text = if (count == 1) {
            "Reinforcement arrived! ${enemyNames.firstOrNull() ?: "An enemy"} joined the battle."
        } else {
            "Reinforcements arrived! $count enemies joined the battle."
        }
        speak(text, force = true, onDone = onDone)
    }

    suspend fun narrateReinforcementsSuspend(count: Int, enemyNames: List<String>, timeoutMs: Long = 8000L) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                narrateReinforcements(count, enemyNames) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun narrateStatus(party: List<PartyMember>, enemies: List<Enemy>, onDone: (() -> Unit)? = null) {
        if (!_isCombatActive.value) {
            onDone?.invoke()
            return
        }
        val aliveHeroes = party.filter { it.isAlive }
        val partyStatus = aliveHeroes.joinToString(", ") { "${it.name} at ${it.currentHp} HP" }
        val aliveEnemies = enemies.filter { it.isAlive }
        val enemyStatus = aliveEnemies.joinToString(", ") { "${it.name} at ${it.currentHp} HP" }
        val targeted = aliveEnemies.firstOrNull { it.isTargeted }?.name ?: "none"

        val text = "Situation report. Party: $partyStatus. Enemies: $enemyStatus. Targeted foe: $targeted."
        speak(text, force = true, onDone = onDone)
    }

    fun narrateEnemies(enemies: List<Enemy>, onDone: (() -> Unit)? = null) {
        if (!_isCombatActive.value) {
            onDone?.invoke()
            return
        }
        val alive = enemies.filter { it.isAlive }
        val targeted = alive.firstOrNull { it.isTargeted }?.name ?: alive.firstOrNull()?.name
        val list = alive.mapIndexed { idx, e -> "Enemy ${idx + 1}, ${e.name} with ${e.currentHp} HP" }.joinToString(". ")
        val text = "${alive.size} enemies remaining. $list. Current target is $targeted."
        speak(text, force = true, onDone = onDone)
    }

    fun narrateParty(party: List<PartyMember>, onDone: (() -> Unit)? = null) {
        if (!_isCombatActive.value) {
            onDone?.invoke()
            return
        }
        val members = party.map {
            if (it.isAlive) "${it.name} the ${it.loreClass}, ${it.currentHp} HP" else "${it.name} has fallen"
        }.joinToString(". ")
        val text = "Party status. $members."
        speak(text, force = true, onDone = onDone)
    }

    fun narrateHelp(onDone: (() -> Unit)? = null) {
        if (!_isCombatActive.value) {
            onDone?.invoke()
            return
        }
        val text = "Voice commands. Say your spell name to cast. Say 'Status' for situation report. " +
                "Say 'Enemies' for target scan. Say 'Pocket mode' to toggle audio guidance. " +
                "Say 'Auto listen' to toggle hands-free mic. Say 'Options' to open settings."
        speak(text, force = true, onDone = onDone)
    }

    fun narrateConclusion(
        isVictory: Boolean,
        extraPrefix: String? = null,
        force: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        if (!force && (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value))) {
            onDone?.invoke()
            return
        }
        val manifest = com.voicerpg.engine.content.GameContent.manifest
        val baseLine = if (isVictory) manifest.victoryLine else manifest.defeatLine
        val text = if (!extraPrefix.isNullOrBlank()) "$extraPrefix $baseLine" else baseLine
        speak(text, force = true, onDone = onDone)
    }

    suspend fun narrateConclusionSuspend(isVictory: Boolean, timeoutMs: Long = 8000L) {
        if (!_isCombatActive.value || (!_isEyesFreeMode.value && !_isCombatNarrationEnabled.value)) return
        if (tts == null || !isTtsInitialized) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                narrateConclusion(isVictory) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun destroy() {
        pendingSpeechOnDone = null
        activeUtteranceId = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        tts = null
    }
}
