package com.voicerpg.engine.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight real-time pitch and vocal inflection estimator.
 * Analyzes audio buffers (via autocorrelation) and acoustic micro-variations
 * to determine fundamental vocal pitch (Hz) and pitch inflection variance.
 */
class PitchDetector {

    private val pitchSamples = mutableListOf<Float>()
    private val sampleRate = 16000 // Standard Android speech recognition sampling rate

    fun reset() {
        pitchSamples.clear()
    }

    /**
     * Processes a raw PCM audio chunk (16-bit mono) from SpeechRecognizer.onBufferReceived.
     */
    fun processPcmBuffer(buffer: ByteArray) {
        if (buffer.size < 512) return

        val numShorts = buffer.size / 2
        val samples = FloatArray(numShorts)
        for (i in 0 until numShorts) {
            val sample = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
            samples[i] = sample / 32768f
        }

        // Fast Autocorrelation within human vocal range: 80Hz (lag 200) to 400Hz (lag 40)
        val minLag = (sampleRate / 400).coerceAtLeast(10)
        val maxLag = (sampleRate / 80).coerceAtMost(numShorts / 2)

        var bestLag = -1
        var maxCorr = 0f

        for (lag in minLag..maxLag) {
            var corr = 0f
            var n = 0
            while (n < numShorts - lag) {
                corr += samples[n] * samples[n + lag]
                n += 4 // Fast subsampled correlation
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        if (bestLag > 0 && maxCorr > 0.05f) {
            val pitchHz = sampleRate.toFloat() / bestLag
            if (pitchHz in 80f..500f) {
                pitchSamples.add(pitchHz)
            }
        }
    }

    /**
     * Fallback prosody estimator when raw PCM buffers are restricted by OEM speech services.
     * Computes inflection from RMS decibel sample dynamics and syllabic cadence.
     */
    fun recordRmsModulation(rmsHistory: List<Float>) {
        if (rmsHistory.size < 6) return
        // Count zero-crossing / derivative directional changes in volume to estimate vocal modulation
        var directionChanges = 0
        for (i in 2 until rmsHistory.size) {
            val d1 = rmsHistory[i - 1] - rmsHistory[i - 2]
            val d2 = rmsHistory[i] - rmsHistory[i - 1]
            if ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) {
                directionChanges++
            }
        }
        // Synthesize equivalent pitch variance: 0 to 45 Hz
        val estimatedVariance = (directionChanges.toFloat() / rmsHistory.size) * 60f
        pitchSamples.add(estimatedVariance)
    }

    fun getAveragePitchHz(): Float {
        return if (pitchSamples.isNotEmpty()) pitchSamples.average().toFloat() else 165f // Default median vocal pitch
    }

    fun getPitchVarianceHz(): Float {
        if (pitchSamples.size < 3) return 8f // Baseline
        val avg = pitchSamples.average().toFloat()
        val variance = pitchSamples.map { (it - avg) * (it - avg) }.average()
        return sqrt(variance).toFloat()
    }
}
