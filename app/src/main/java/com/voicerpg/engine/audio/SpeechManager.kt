package com.voicerpg.engine.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    object Standby : SpeechState()
    data class Error(val message: String) : SpeechState()
}

class SpeechManager(private val context: Context? = null) {

    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var speechRecognizer: SpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    // Default to chime MUTED as requested by user
    private val _isChimeMuted = MutableStateFlow(true)
    val isChimeMuted: StateFlow<Boolean> = _isChimeMuted.asStateFlow()

    // Default to false: Tap-to-Speak mode (no unexpected mic opening or chime)
    private val _isAutoListen = MutableStateFlow(false)
    val isAutoListen: StateFlow<Boolean> = _isAutoListen.asStateFlow()

    // Real-time audio amplitude for live visual feedback (0f to 1f)
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _selectedLocale = MutableStateFlow("en")
    val selectedLocale: StateFlow<String> = _selectedLocale.asStateFlow()

    private val _selectedRegion = MutableStateFlow("")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    fun setSelectedLocale(locale: String) {
        _selectedLocale.value = locale.lowercase().take(2)
    }

    fun setSelectedRegion(region: String) {
        _selectedRegion.value = region.uppercase().trim()
    }

    private val pitchDetector = PitchDetector()
    private val rmsSamples = mutableListOf<Float>()
    private var speechStartTimeMs = 0L

    private val _lastAcousticProfile = MutableStateFlow(com.voicerpg.engine.model.AcousticProfile())
    val lastAcousticProfile: StateFlow<com.voicerpg.engine.model.AcousticProfile> = _lastAcousticProfile.asStateFlow()

    private var isMutedTemporary = false
    private var isListeningSessionActive = false
    private var autoListenRestartJob: Job? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var onStandbyCallback: (() -> Unit)? = null
    private var autoListenAttemptCount = 0
    private var maxAutoListenAttempts = 3

    fun getLatestAcousticProfile(): com.voicerpg.engine.model.AcousticProfile = _lastAcousticProfile.value

    val isAvailable: Boolean
        get() = context != null && try { SpeechRecognizer.isRecognitionAvailable(context) } catch (_: Exception) { false }

    val isSessionActive: Boolean
        get() = isListeningSessionActive

    fun getAutoListenAttemptCount(): Int = autoListenAttemptCount
    fun setMaxAutoListenAttempts(max: Int) {
        maxAutoListenAttempts = max
    }
    internal fun setSpeechStateForTesting(state: SpeechState) {
        _speechState.value = state
    }
    internal fun triggerStandbyForTesting() {
        isListeningSessionActive = false
        _speechState.value = SpeechState.Standby
        onStandbyCallback?.invoke()
    }

    fun toggleChimeMute() {
        _isChimeMuted.value = !_isChimeMuted.value
    }

    fun setChimeMuted(muted: Boolean) {
        _isChimeMuted.value = muted
    }

    fun toggleAutoListen() {
        setAutoListen(!_isAutoListen.value)
    }

    fun setAutoListen(enabled: Boolean) {
        _isAutoListen.value = enabled
        if (!enabled && isListeningSessionActive) {
            autoListenRestartJob?.cancel()
            autoListenRestartJob = null
        }
    }

    /**
     * Momentarily mutes system/notification streams so OS speech beeps/chimes are silent.
     * Keeps STREAM_MUSIC unmuted so game sound effects and TTS remain clear.
     */
    private fun suppressChime() {
        if (!_isChimeMuted.value) return
        val am = audioManager ?: return
        isMutedTemporary = true
        try { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
        try { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
    }

    /**
     * Restores system/notification streams after the chime window has elapsed.
     */
    private fun restoreVolume(delayMs: Long = 250L) {
        if (!isMutedTemporary) return
        CoroutineScope(Dispatchers.Main).launch {
            if (delayMs > 0) delay(delayMs)
            val am = audioManager ?: return@launch
            try { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
            try { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
            isMutedTemporary = false
        }
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (context == null) return null
        if (speechRecognizer == null) {
            try {
                speechRecognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                }
            } catch (_: Exception) {
                speechRecognizer = null
            }
        }
        return speechRecognizer
    }

    private fun recreateRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        getOrCreateRecognizer()
    }

    fun startListening(
        onStandby: (() -> Unit)? = null,
        onResult: (String) -> Unit
    ) {
        onFinalResultCallback = onResult
        onStandbyCallback = onStandby
        autoListenAttemptCount = 0

        if (!isAvailable) {
            _speechState.value = SpeechState.Error("Speech recognition is not available on this device.")
            return
        }

        isListeningSessionActive = true
        autoListenRestartJob?.cancel()
        autoListenRestartJob = null
        _liveTranscript.value = ""
        _speechState.value = SpeechState.Listening

        startListeningInternal()
    }

    fun resumeListening() {
        autoListenAttemptCount = 0
        isListeningSessionActive = true
        autoListenRestartJob?.cancel()
        autoListenRestartJob = null
        _liveTranscript.value = ""
        _speechState.value = SpeechState.Listening
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (!isAvailable || !isListeningSessionActive) return

        try {
            suppressChime()
            val recognizer = getOrCreateRecognizer() ?: return

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                val locale = _selectedLocale.value.lowercase()
                val region = _selectedRegion.value.uppercase()
                val langTag = if (region.isNotBlank()) {
                    "$locale-$region"
                } else {
                    locale
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Generous pause windows so chanting and multi-word incantations are not cut off
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            }

            try {
                recognizer.cancel()
            } catch (_: Exception) {}

            recognizer.startListening(intent)
            _speechState.value = SpeechState.Listening
            restoreVolume(400L)
        } catch (e: Exception) {
            recreateRecognizer()
            if (isListeningSessionActive && _isAutoListen.value) {
                scheduleRestart(500L)
            } else {
                restoreVolume(0L)
                _speechState.value = SpeechState.Error(e.localizedMessage ?: "Failed to start speech recognizer")
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        autoListenRestartJob?.cancel()
        autoListenRestartJob = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            if (isListeningSessionActive && _isAutoListen.value) {
                startListeningInternal()
            }
        }
    }

    fun stopListening() {
        autoListenAttemptCount = 0
        isListeningSessionActive = false
        autoListenRestartJob?.cancel()
        autoListenRestartJob = null
        suppressChime()
        try {
            speechRecognizer?.stopListening()
            _speechState.value = SpeechState.Processing
        } catch (_: Exception) {
            _speechState.value = SpeechState.Idle
        }
        restoreVolume(400L)
    }

    fun cancel() {
        autoListenAttemptCount = 0
        isListeningSessionActive = false
        autoListenRestartJob?.cancel()
        autoListenRestartJob = null
        suppressChime()
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        _speechState.value = SpeechState.Idle
        _liveTranscript.value = ""
        _rmsLevel.value = 0f
        restoreVolume(400L)
    }

    fun destroy() {
        isListeningSessionActive = false
        autoListenRestartJob?.cancel()
        autoListenRestartJob = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        restoreVolume(0L)
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isListeningSessionActive) {
                    _speechState.value = SpeechState.Listening
                }
                restoreVolume(150L)
            }

            override fun onBeginningOfSpeech() {
                if (isListeningSessionActive) {
                    _speechState.value = SpeechState.Listening
                }
                speechStartTimeMs = System.currentTimeMillis()
                rmsSamples.clear()
                pitchDetector.reset()
            }

            override fun onRmsChanged(rmsdB: Float) {
                val clamped = rmsdB.coerceIn(0f, 12f)
                rmsSamples.add(clamped)
                _rmsLevel.value = (clamped / 10f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                buffer?.let { pitchDetector.processPcmBuffer(it) }
            }

            override fun onEndOfSpeech() {
                _rmsLevel.value = 0f
                finalizeAcousticProfile()
                suppressChime()
                _speechState.value = SpeechState.Processing
                restoreVolume(400L)
            }

            override fun onError(error: Int) {
                _rmsLevel.value = 0f
                suppressChime()

                // In Hands-Free Auto-Listen mode, attempt up to maxAutoListenAttempts before standing by
                if (isListeningSessionActive && _isAutoListen.value) {
                    autoListenAttemptCount++
                    if (autoListenAttemptCount < maxAutoListenAttempts) {
                        _speechState.value = SpeechState.Listening
                        if (error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == 10 || // ERROR_TOO_MANY_REQUESTS
                            error == 11    // ERROR_SERVER_DISCONNECTED
                        ) {
                            try { speechRecognizer?.cancel() } catch (_: Exception) {}
                            scheduleRestart(500L)
                        } else {
                            // Calm, natural breathing pause (800ms) between retry attempts so system tones don't fire back-to-back
                            scheduleRestart(800L)
                        }
                        return
                    } else {
                        // Max attempts reached without speech detected!
                        // Enter graceful STANDBY mode to stop system tone loops and preserve battery.
                        isListeningSessionActive = false
                        autoListenRestartJob?.cancel()
                        autoListenRestartJob = null
                        _speechState.value = SpeechState.Standby
                        restoreVolume(350L)
                        val cb = onStandbyCallback
                        cb?.invoke()
                        return
                    }
                }

                // If not in auto-listen mode (manual single-tap) or session cancelled:
                isListeningSessionActive = false
                restoreVolume(350L)

                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    _speechState.value = SpeechState.Idle
                    return
                }

                if (error == SpeechRecognizer.ERROR_CLIENT || 
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || 
                    error == 10 || 
                    error == 11
                ) {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {}
                    _speechState.value = SpeechState.Idle
                    return
                }

                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    10 -> "Speech daemon busy"
                    11 -> "Speech daemon reconnecting"
                    else -> "Unknown error ($error)"
                }
                _speechState.value = SpeechState.Error(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                _rmsLevel.value = 0f
                finalizeAcousticProfile()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalTrans = matches?.firstOrNull()?.trim() ?: ""
                _liveTranscript.value = finalTrans

                if (finalTrans.isNotBlank()) {
                    // Speech was recognized! Reset attempt counter and complete session
                    autoListenAttemptCount = 0
                    isListeningSessionActive = false
                    autoListenRestartJob?.cancel()
                    autoListenRestartJob = null
                    _speechState.value = SpeechState.Processing
                    restoreVolume(400L)
                    onFinalResultCallback?.invoke(finalTrans)
                } else {
                    // Blank/silent result: check attempt count if in auto-listen mode
                    if (isListeningSessionActive && _isAutoListen.value) {
                        autoListenAttemptCount++
                        if (autoListenAttemptCount < maxAutoListenAttempts) {
                            _speechState.value = SpeechState.Listening
                            scheduleRestart(800L)
                        } else {
                            isListeningSessionActive = false
                            autoListenRestartJob?.cancel()
                            autoListenRestartJob = null
                            _speechState.value = SpeechState.Standby
                            restoreVolume(400L)
                            val cb = onStandbyCallback
                            cb?.invoke()
                        }
                    } else {
                        isListeningSessionActive = false
                        _speechState.value = SpeechState.Idle
                        restoreVolume(400L)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    _liveTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun finalizeAcousticProfile() {
        if (rmsSamples.isEmpty()) return
        val peakDb = rmsSamples.maxOrNull() ?: 5f
        val avgDb = rmsSamples.average().toFloat()
        val minDb = rmsSamples.minOrNull() ?: 0f
        val dynRange = (peakDb - minDb).coerceAtLeast(0f)

        val half = rmsSamples.size / 2
        val slope = if (half > 2) {
            val firstHalfAvg = rmsSamples.take(half).average().toFloat()
            val secondHalfAvg = rmsSamples.drop(half).average().toFloat()
            (secondHalfAvg - firstHalfAvg)
        } else 0f

        pitchDetector.recordRmsModulation(rmsSamples)

        val profile = com.voicerpg.engine.model.AcousticProfile(
            peakVolumeDb = peakDb,
            averageVolumeDb = avgDb,
            volumeDynamicRange = dynRange,
            volumeCrescendoSlope = slope,
            pitchVarianceHz = pitchDetector.getPitchVarianceHz(),
            estimatedPitchHz = pitchDetector.getAveragePitchHz(),
            durationMs = (System.currentTimeMillis() - speechStartTimeMs).coerceAtLeast(0L),
            sampleCount = rmsSamples.size
        )
        _lastAcousticProfile.value = profile
    }
}

