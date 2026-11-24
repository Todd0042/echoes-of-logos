package com.voicerpg.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural retro sound synthesizer for hit impacts, spell casts, and Logos fanfare (no audio files),
 * plus recorded sound bites from the game's assets (characters', spells' and enemies' soundBites).
 * Without a [context] (unit tests) sound bites are skipped.
 */
class SfxManager(private val context: Context? = null) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val sampleRate = 22050
    var isMuted: Boolean = false

    fun mute(muted: Boolean) {
        isMuted = muted
    }

    private var soundBitePlayer: MediaPlayer? = null

    /** Plays one of [clips] (asset paths) at random, replacing any sound bite still playing. */
    fun playSoundBite(clips: List<String>) {
        if (clips.isEmpty()) return
        playAssetAudio(clips.random())
    }

    /** Plays a compressed audio asset (e.g. audio/quotes/x.ogg). A missing or unplayable file is logged, not fatal. */
    fun playAssetAudio(assetPath: String) {
        val ctx = context ?: return
        if (isMuted) return
        try {
            soundBitePlayer?.release()
            val player = MediaPlayer()
            ctx.assets.openFd(assetPath).use { fd -> player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            player.setOnCompletionListener { it.release(); if (soundBitePlayer === it) soundBitePlayer = null }
            player.prepare()
            player.start()
            soundBitePlayer = player
        } catch (e: Exception) {
            android.util.Log.e("SfxManager", "Sound bite '$assetPath' couldn't be played: ${e.message}", e)
        }
    }

    fun playHitImpact() {
        if (isMuted) return
        scope.launch {
            val durationMs = 90
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val envelope = 1.0f - progress
                val noise = (Random.nextFloat() * 2f - 1f) * 0.7f
                val lowTone = sin(2.0 * Math.PI * 90.0 * (i.toDouble() / sampleRate)).toFloat() * 0.5f
                val sample = ((noise + lowTone) * envelope * Short.MAX_VALUE * 0.6f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer)
        }
    }

    fun playSpellCast() {
        if (isMuted) return
        scope.launch {
            val durationMs = 180
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val envelope = sin(progress * Math.PI).toFloat()
                // Frequency sweeps from 250Hz up to 900Hz
                val freq = 250.0 + (650.0 * progress)
                val tone = sin(2.0 * Math.PI * freq * (i.toDouble() / sampleRate)).toFloat()
                val sample = (tone * envelope * Short.MAX_VALUE * 0.45f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer)
        }
    }

    /** [pitch] > 1 raises the arpeggio (a running resonance streak climbs). */
    fun playLogosFanfare(pitch: Float = 1f) {
        scope.launch {
            // Victorious arpeggio: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
            val notes = listOf(523.25, 659.25, 783.99, 1046.50).map { it * pitch }
            val noteDurationMs = 80
            val totalDurationMs = noteDurationMs * notes.size + 150
            val numSamples = (sampleRate * (totalDurationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (noteIdx in notes.indices) {
                val freq = notes[noteIdx]
                val startSample = (sampleRate * ((noteIdx * noteDurationMs) / 1000.0)).toInt()
                val endSample = (sampleRate * (((noteIdx + 1) * noteDurationMs + 100) / 1000.0)).toInt().coerceAtMost(numSamples)

                for (i in startSample until endSample) {
                    val noteProgress = (i - startSample).toFloat() / (endSample - startSample)
                    val envelope = (1.0f - noteProgress).coerceIn(0f, 1f)
                    val tone = sin(2.0 * Math.PI * freq * (i.toDouble() / sampleRate)).toFloat()
                    val sample = (tone * envelope * Short.MAX_VALUE * 0.4f).toInt()
                    buffer[i] = (buffer[i] + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            playPcm(buffer)
        }
    }

    // ---- Per-school impact sets (M8) -----------------------------------------------------------------

    /** A school's impact, louder and longer for stronger voices, with ±8% random pitch. */
    fun playImpact(school: com.voicerpg.engine.model.SpellSchool, tier: com.voicerpg.engine.model.ResonanceTier = com.voicerpg.engine.model.ResonanceTier.BASIC) {
        if (isMuted) return
        val sound = ImpactSound.forSchool(school)
        val pitch = 1f + (Random.nextFloat() * 0.16f - 0.08f)
        val power = 0.55f + 0.1f * tier.ordinal
        scope.launch { playPcm(synthImpact(sound, pitch, power)) }
    }

    private fun synthImpact(sound: ImpactSound, pitch: Float, power: Float): ShortArray {
        val ms = when (sound) {
            ImpactSound.FIRE_CRACKLE -> 260
            ImpactSound.ICE_SHATTER -> 320
            ImpactSound.THUNDER -> 380
            ImpactSound.HOLY_CHIME -> 520
            ImpactSound.SHADOW_THRUM -> 340
            ImpactSound.WIND_WHOOSH -> 300
            ImpactSound.PHYSICAL_THUD -> 160
        }
        val n = (sampleRate * ms / 1000.0).toInt()
        val out = ShortArray(n)
        var phaseA = 0.0
        var phaseB = 0.0
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val sec = i.toDouble() / sampleRate
            val decay = (1f - t)
            val noise = Random.nextFloat() * 2f - 1f
            val v: Float = when (sound) {
                ImpactSound.FIRE_CRACKLE -> {
                    // crackles: sparse sharp pops over a hissing body
                    val pop = if (Random.nextFloat() < 0.012f) (Random.nextFloat() * 2f - 1f) * 1.6f else 0f
                    lp += (noise - lp) * 0.35f
                    (0.45f * (noise - lp) + pop) * decay * decay
                }
                ImpactSound.ICE_SHATTER -> {
                    phaseA += 2.0 * Math.PI * 2600.0 * pitch / sampleRate
                    phaseB += 2.0 * Math.PI * 3900.0 * pitch / sampleRate
                    val glass = (sin(phaseA) * 0.5 + sin(phaseB) * 0.35).toFloat() * (1f - t).let { it * it * it }
                    glass + 0.35f * noise * decay * decay
                }
                ImpactSound.THUNDER -> {
                    lp += (noise - lp) * 0.04f
                    val crack = if (t < 0.08f) noise * (1f - t / 0.08f) else 0f
                    (lp * 3.2f + crack * 0.8f) * decay
                }
                ImpactSound.HOLY_CHIME -> {
                    phaseA += 2.0 * Math.PI * 1318.5 * pitch / sampleRate
                    phaseB += 2.0 * Math.PI * 1975.5 * pitch / sampleRate
                    ((sin(phaseA) * 0.55 + sin(phaseB) * 0.3).toFloat()) * kotlin.math.exp(-4f * t)
                }
                ImpactSound.SHADOW_THRUM -> {
                    phaseA += 2.0 * Math.PI * 55.0 * pitch / sampleRate
                    phaseB += 2.0 * Math.PI * 57.5 * pitch / sampleRate
                    val saw = ((phaseA / Math.PI) % 2.0 - 1.0).toFloat() + ((phaseB / Math.PI) % 2.0 - 1.0).toFloat()
                    0.45f * saw * decay + 0.1f * noise * decay
                }
                ImpactSound.WIND_WHOOSH -> {
                    val sweep = 0.02f + 0.25f * sin(t * Math.PI).toFloat()
                    lp += (noise - lp) * sweep
                    lp * 1.8f * sin(t * Math.PI).toFloat()
                }
                ImpactSound.PHYSICAL_THUD -> {
                    phaseA += 2.0 * Math.PI * (110.0 - 60.0 * t) * pitch / sampleRate
                    (sin(phaseA).toFloat() * 0.8f + 0.3f * noise * (1f - t * 4f).coerceAtLeast(0f)) * decay
                }
            }
            out[i] = (v * power * Short.MAX_VALUE * 0.55f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** The low "whomp" under a strong hit's freeze: a muffled thump as long as the hit-stop. */
    fun playWhomp(durationMs: Long) {
        if (isMuted || durationMs <= 0) return
        scope.launch {
            val n = (sampleRate * (durationMs + 120) / 1000.0).toInt()
            val out = ShortArray(n)
            var phase = 0.0
            var lp = 0f
            for (i in 0 until n) {
                val t = i.toFloat() / n
                phase += 2.0 * Math.PI * (70.0 - 30.0 * t) / sampleRate
                lp += ((Random.nextFloat() * 2f - 1f) - lp) * 0.02f // low-passed rumble
                val v = (sin(phase).toFloat() * 0.75f + lp * 2f) * (1f - t)
                out[i] = (v * Short.MAX_VALUE * 0.5f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(out)
        }
    }

    /** An enemy shatters: bright glassy tinkles scattering downward. */
    fun playShatter() {
        if (isMuted) return
        scope.launch {
            val n = (sampleRate * 0.55).toInt()
            val out = ShortArray(n)
            val tinkles = List(9) { Triple(Random.nextInt(n / 2), 1800.0 + Random.nextDouble() * 3200.0, Random.nextFloat() * 0.5f + 0.3f) }
            for ((start, freq, amp) in tinkles) {
                for (i in start until minOf(n, start + n / 5)) {
                    val k = (i - start).toFloat() / (n / 5)
                    val v = sin(2.0 * Math.PI * freq * (i - start) / sampleRate).toFloat() * amp * (1f - k) * (1f - k)
                    out[i] = (out[i] + (v * Short.MAX_VALUE * 0.3f).toInt()).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            playPcm(out)
        }
    }

    /** Stagger / broken ultimate: a sharp crack falling into a low thud. */
    fun playStaggerStinger() {
        if (isMuted) return
        scope.launch {
            val n = (sampleRate * 0.32).toInt()
            val buffer = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val freq = 880.0 * (1.0 - t * 0.85)
                phase += 2.0 * Math.PI * freq / sampleRate
                val crack = (Random.nextFloat() * 2f - 1f) * (1f - t).let { it * it * it }
                val tone = sin(phase).toFloat() * (1f - t)
                val sample = ((0.55f * tone + 0.45f * crack) * Short.MAX_VALUE * 0.5f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer)
        }
    }

    // ---- Charge hum: a continuous tone that follows the voice while a chant charges -----------------
    @Volatile private var humLevel = 0f
    private var humJob: kotlinx.coroutines.Job? = null

    /** Starts the hum (no-op without an audio context, i.e. in unit tests). */
    fun startChargeHum() {
        if (context == null || isMuted || humJob?.isActive == true) return
        humLevel = 0f
        humJob = scope.launch { streamHum() }
    }

    /** 0..1: pitch rises and the hum swells with the voice. */
    fun setChargeLevel(level: Float) {
        humLevel = level.coerceIn(0f, 1f)
    }

    fun stopChargeHum() {
        humJob?.cancel()
        humJob = null
        humLevel = 0f
    }

    private suspend fun streamHum() {
        val chunk = sampleRate / 50 // 20 ms
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(chunk * 2 * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) { return }
        val buffer = ShortArray(chunk)
        var phase = 0.0
        var level = 0f
        try {
            track.play()
            while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                for (i in 0 until chunk) {
                    level += (humLevel - level) * 0.002f // glide, no clicks
                    val freq = 90.0 + 260.0 * level
                    phase += 2.0 * Math.PI * freq / sampleRate
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                    val saw = (phase / Math.PI - 1.0).toFloat()
                    val tone = 0.65f * sin(phase).toFloat() + 0.35f * saw
                    val amp = 0.04f + 0.22f * level
                    buffer[i] = (tone * amp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                track.write(buffer, 0, chunk)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun playPcm(pcmData: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()
            Thread.sleep((pcmData.size * 1000L) / sampleRate + 50)
            audioTrack.release()
        } catch (_: Exception) {}
    }
}
