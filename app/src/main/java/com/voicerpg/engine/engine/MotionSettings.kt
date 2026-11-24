package com.voicerpg.engine.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visual comfort options (Options → Visual comfort). Saved with the slot like the audio options;
 * the battle screen reads them live.
 *
 *  - Reduce motion: the battle arena never shakes or zooms, and hit-stops stay short.
 *  - Reduce flashing: no full-screen white flash (flashes are already capped at three a second).
 */
class MotionSettings {
    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(enabled: Boolean) {
        _reduceMotion.value = enabled
    }

    fun toggleReduceMotion(): Boolean {
        _reduceMotion.value = !_reduceMotion.value
        return _reduceMotion.value
    }

    private val _reduceFlashing = MutableStateFlow(false)
    val reduceFlashing: StateFlow<Boolean> = _reduceFlashing.asStateFlow()

    fun setReduceFlashing(enabled: Boolean) {
        _reduceFlashing.value = enabled
    }

    fun toggleReduceFlashing(): Boolean {
        _reduceFlashing.value = !_reduceFlashing.value
        return _reduceFlashing.value
    }
}
