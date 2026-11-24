package com.voicerpg.engine.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background Music (BGM) Manager for the VoiceRPG Engine.
 * Provides continuous, seamlessly looping ambient soundtracks per story scene and combat arena.
 * Scene tracks are declared per scene in story data files (musicAsset); the engine ships
 * template tracks (title / exploration / combat) under assets/audio/music/.
 *
 * Audio Architecture & Routing:
 * - Uses [AudioAttributes.USAGE_MEDIA] and [AudioAttributes.CONTENT_TYPE_MUSIC]
 * - Requests and maintains persistent audio focus with [AudioManager.AUDIOFOCUS_GAIN]
 * - Keeps the media audio pipeline alive so Android Auto / Bluetooth car sinks never sleep
 *   or clip initial TTS dialogue syllables.
 * - Supports dynamic speech ducking (drops BGM to ~20% during narration, restoring smoothly).
 */
class MusicManager(
    private val context: Context? = null,
    scopeOverride: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "MusicManager"
        const val DEFAULT_TRACK_TITLE = "audio/music/bgm_title.ogg"
        const val DEFAULT_TRACK_EXPLORATION = "audio/music/bgm_exploration.ogg"
        const val DEFAULT_TRACK_COMBAT = "audio/music/bgm_combat.ogg"

        // The game may name its own default tracks in the manifest (titleMusic, explorationMusic, combatMusic).
        val TRACK_TITLE: String get() = com.voicerpg.engine.content.GameContent.manifest.titleMusic ?: DEFAULT_TRACK_TITLE
        val TRACK_EXPLORATION: String get() = com.voicerpg.engine.content.GameContent.manifest.explorationMusic ?: DEFAULT_TRACK_EXPLORATION
        val TRACK_COMBAT: String get() = com.voicerpg.engine.content.GameContent.manifest.combatMusic ?: DEFAULT_TRACK_COMBAT

        const val DUCK_RATIO = 0.22f
    }

    private val scope = scopeOverride ?: CoroutineScope(Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val _isMusicEnabled = MutableStateFlow(true)
    val isMusicEnabled: StateFlow<Boolean> = _isMusicEnabled.asStateFlow()

    private val _musicVolume = MutableStateFlow(0.55f)
    val musicVolume: StateFlow<Float> = _musicVolume.asStateFlow()

    private val _currentTrack = MutableStateFlow<String?>(null)
    val currentTrack: StateFlow<String?> = _currentTrack.asStateFlow()

    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    private var currentAppliedVolume = 0.55f
    private var volumeFadeJob: Job? = null
    private var isPausedByLifecycle = false

    private val audioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    private val focusChangeListener by lazy {
        AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (_isMusicEnabled.value && !isPausedByLifecycle) {
                    resume()
                    applyEffectiveVolume(targetVolume(), animated = true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // System requested ducking (e.g. navigation prompt)
                applyEffectiveVolume(targetVolume() * 0.3f, animated = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Transient loss (e.g. phone call or voice assistant)
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss
                hasAudioFocus = false
                pauseInternal()
            }
        }
    }
}

    init {
        if (context != null) {
            try {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize AudioManager: ${e.message}")
            }
        }
    }

    /**
     * Plays a given music track from the asset folder.
     * If the requested track is already playing, this is a no-op to allow seamless continuation.
     */
    fun playTrack(assetPath: String) {
        if (_currentTrack.value == assetPath && mediaPlayer?.isPlaying == true) {
            return
        }
        _currentTrack.value = assetPath

        if (!_isMusicEnabled.value || context == null) {
            return
        }

        startTrackPlayback(assetPath)
    }

    fun playCombatMusic() {
        playTrack(TRACK_COMBAT)
    }

    private fun startTrackPlayback(assetPath: String) {
        if (context == null) return
        try {
            requestAudioFocus()

            val player = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
            player.reset()
            player.setAudioAttributes(audioAttributes)
            player.isLooping = true

            var afd: AssetFileDescriptor? = null
            try {
                afd = context.assets.openFd(assetPath)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd?.close()
            }

            val targetVol = targetVolume()
            player.setVolume(targetVol, targetVol)
            currentAppliedVolume = targetVol
            player.prepare()
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start music track $assetPath: ${e.message}", e)
        }
    }

    /**
     * Ducks music volume smoothly when TTS speech begins.
     */
    fun duckForSpeech() {
        if (_isDucked.value) return
        _isDucked.value = true
        applyEffectiveVolume(targetVolume(), animated = true)
    }

    /**
     * Restores music volume smoothly when TTS speech ends.
     */
    fun restoreFromSpeech() {
        if (!_isDucked.value) return
        _isDucked.value = false
        applyEffectiveVolume(targetVolume(), animated = true)
    }

    /**
     * Sets user music volume between 0.0f and 1.0f.
     */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _musicVolume.value = clamped
        applyEffectiveVolume(targetVolume(), animated = false)
    }

    /**
     * Enables or disables music playback.
     */
    fun setMusicEnabled(enabled: Boolean) {
        if (_isMusicEnabled.value == enabled) return
        _isMusicEnabled.value = enabled
        if (enabled) {
            val track = _currentTrack.value ?: TRACK_EXPLORATION
            startTrackPlayback(track)
        } else {
            mediaPlayer?.pause()
            abandonAudioFocus()
        }
    }

    /**
     * Toggles music on / off. Returns new state.
     */
    fun toggleMusic(): Boolean {
        val newState = !_isMusicEnabled.value
        setMusicEnabled(newState)
        return newState
    }

    fun pause() {
        isPausedByLifecycle = true
        pauseInternal()
    }

    fun resume() {
        isPausedByLifecycle = false
        if (_isMusicEnabled.value) {
            val player = mediaPlayer
            if (player != null && !player.isPlaying) {
                requestAudioFocus()
                try {
                    currentAppliedVolume = targetVolume()
                    applyMix()
                    player.start()
                    intensityPlayer?.let { if (!it.isPlaying) it.start() }
                } catch (e: Exception) {
                    _currentTrack.value?.let { startTrackPlayback(it) }
                }
            } else if (player == null && _currentTrack.value != null) {
                startTrackPlayback(_currentTrack.value!!)
            }
        }
    }

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    fun isTrackPlaying(assetPath: String): Boolean {
        return _currentTrack.value == assetPath && mediaPlayer?.isPlaying == true
    }

    fun stop() {
        volumeFadeJob?.cancel()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {}
        }
        setIntensityTrack(null)
        abandonAudioFocus()
    }

    fun stopTrack() {
        stop()
        _currentTrack.value = null
    }

    fun destroy() {
        volumeFadeJob?.cancel()
        setIntensityTrack(null)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    private fun pauseInternal() {
        runCatching { intensityPlayer?.let { if (it.isPlaying) it.pause() } }
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing music: ${e.message}")
        }
    }

    // ---- Intensity layer (M8): a second track crossfaded in when a battle turns desperate ------------
    private var intensityPlayer: MediaPlayer? = null
    private var intensityTrack: String? = null
    @Volatile private var intensityMix = 0f
    private var intensityFadeJob: Job? = null

    /** The current battle's intensity track (or none). Prepared silent, in step with the battle music. */
    fun setIntensityTrack(assetPath: String?) {
        if (assetPath == intensityTrack) return
        intensityTrack = assetPath
        intensityFadeJob?.cancel()
        intensityMix = 0f
        intensityPlayer?.release()
        intensityPlayer = null
        val ctx = context ?: return
        if (assetPath == null || !_isMusicEnabled.value) return
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            player.isLooping = true
            ctx.assets.openFd(assetPath).use { fd -> player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            player.setVolume(0f, 0f)
            player.prepare()
            player.start()
            intensityPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Intensity track $assetPath couldn't be prepared: ${e.message}", e)
        }
    }

    /** Crossfade the intensity layer in or out over 1.5 s. No-op without an intensity track. */
    fun setIntensity(on: Boolean) {
        if (intensityPlayer == null) return
        intensityFadeJob?.cancel()
        val from = intensityMix
        val to = if (on) 1f else 0f
        intensityFadeJob = scope.launch {
            val steps = 30
            for (i in 1..steps) {
                intensityMix = from + (to - from) * i / steps
                applyMix()
                delay(50)
            }
        }
    }

    private fun applyMix() {
        val v = currentAppliedVolume
        try {
            mediaPlayer?.setVolume(v * (1f - intensityMix), v * (1f - intensityMix))
            intensityPlayer?.setVolume(v * intensityMix, v * intensityMix)
        } catch (_: Exception) {}
    }

    private fun targetVolume(): Float {
        val base = _musicVolume.value
        return if (_isDucked.value) {
            base * DUCK_RATIO
        } else {
            base
        }
    }

    private fun applyEffectiveVolume(target: Float, animated: Boolean) {
        val player = mediaPlayer ?: return
        if (!animated) {
            volumeFadeJob?.cancel()
            currentAppliedVolume = target
            applyMix()
            return
        }

        volumeFadeJob?.cancel()
        volumeFadeJob = scope.launch {
            val startVol = currentAppliedVolume
            val steps = 10
            val durationMs = 200L
            val stepDelay = durationMs / steps

            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val current = startVol + (target - startVol) * t
                currentAppliedVolume = current
                applyMix()
                delay(stepDelay)
            }
            currentAppliedVolume = target
            applyMix()
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        if (hasAudioFocus) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build().also { audioFocusRequest = it }
                val result = manager.requestAudioFocus(request)
                hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                val result = manager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusChangeListener)
            }
            hasAudioFocus = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }
}
