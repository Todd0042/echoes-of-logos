package com.voicerpg.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural retro sound synthesizer for hit impacts, spell casts, and Logos fanfare.
 * Requires zero external audio files.
 */
class SfxManager {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val sampleRate = 22050
    var isMuted: Boolean = false

    fun mute(muted: Boolean) {
        isMuted = muted
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

    fun playLogosFanfare() {
        scope.launch {
            // Victorious arpeggio: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
            val notes = listOf(523.25, 659.25, 783.99, 1046.50)
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
