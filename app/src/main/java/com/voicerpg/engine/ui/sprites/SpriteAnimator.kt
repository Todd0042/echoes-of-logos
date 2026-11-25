package com.voicerpg.engine.ui.sprites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Plays one combatant's sprite animations on the effects clock (a hit-stop freezes the frame, slow motion
 * slows it). Three layers, the first that applies wins:
 *
 * 1. a **one-shot** (cast, attack, hurt, KO...), which can hold on the frame before its event
 *    (`release`, `impact`) until the battle says the blow happens;
 * 2. a looping **override** set by the battle (charging, winding up, charging an ultimate, victory);
 * 3. the looping **base** the view sets from the combat state (idle, ready, weak, KO).
 *
 * The battle's own timing (CombatTiming) stays in charge: animations are fitted into it, never the reverse.
 */
class SpriteAnimator {

    var source: SpriteSource? = null
        set(value) {
            if (field?.id != value?.id) { field = value; restart() } else field = value
        }

    /** The resting animation, set by the view from the combat state (SpriteBase). */
    var base: AnimTag = AnimTag.IDLE

    /** After a boss's phase shift: `p2_` animations first. */
    var phase2 = false

    /** Playback rate of loops (the charge follows the voice); one-shots always play at 1x. */
    var speed = 1f

    private var override: AnimTag? = null

    // The clip on screen.
    private var clip: AnimClip? = null
    private var index = 0
    private var inFrameMs = 0f
    private var oneShot = false
    private var holdLastFrame = false
    private var finished = false

    // A one-shot waiting at its event.
    private var holdEvent: String? = null
    private var holdIndex = -1
    private var holdLeftMs = 0f
    private var preStretch = 1f

    /** The sprite frame to draw (a sheet frame index, or a GridSprite frame). */
    var frame by mutableIntStateOf(0)
        private set

    /** The animation playing (the tag name the sprite really has, after fallbacks). */
    var clipName by mutableStateOf("idle")
        private set

    /** Milliseconds into the current clip (effects time). */
    var elapsedMs = 0f
        private set

    /** Whether a one-shot is waiting on the frame before its event. */
    val isHolding: Boolean get() = holdEvent != null && index == holdIndex

    fun setOverride(tag: AnimTag?) {
        override = tag
        if (!oneShot) syncLoop()
    }

    /**
     * Plays [tag] once. With [holdFor] (an event such as `release`), plays the frames before that event (stretched
     * to fill [windupMs] when given) and waits on the last of them until [release], or [holdTimeoutMs].
     * [holdLast]: stay on the final frame afterwards (KO, death).
     */
    fun play(tag: AnimTag, holdFor: String? = null, windupMs: Long = 0, holdTimeoutMs: Long = DEFAULT_HOLD_TIMEOUT_MS, holdLast: Boolean = false) {
        val src = source ?: return
        val c = src.clip(tag, phase2).copy(loops = false)
        start(c, oneShot = true)
        holdLastFrame = holdLast
        val eventAt = holdFor?.let { c.events[it] }
        if (eventAt != null) {
            holdEvent = holdFor
            holdIndex = (eventAt - 1).coerceAtLeast(0)
            holdLeftMs = holdTimeoutMs.toFloat()
            val preMs = c.durationsMs.take(holdIndex).sum().toFloat()
            preStretch = if (windupMs > 0 && preMs > 0f) windupMs / preMs else 1f
        }
    }

    /** The event happens: a held one-shot of [tag] continues from its event frame; otherwise [tag] starts there. */
    fun releaseAt(tag: AnimTag, event: String) {
        val src = source ?: return
        val current = clip
        if (oneShot && current != null && holdEvent == event && current.name == src.clip(tag, phase2).name) {
            jumpTo(current.events[event] ?: (holdIndex + 1))
            clearHold()
            return
        }
        val c = src.clip(tag, phase2).copy(loops = false)
        start(c, oneShot = true)
        jumpTo(c.events[event] ?: 0)
    }

    /** Lets a held one-shot go on, whatever its event. */
    fun release() {
        val c = clip ?: return
        if (holdEvent == null) return
        jumpTo(c.events[holdEvent] ?: (holdIndex + 1))
        clearHold()
    }

    fun reset() {
        override = null
        phase2 = false
        speed = 1f
        restart()
    }

    /** Advances by [fxSeconds] of effects time. */
    fun step(fxSeconds: Float) {
        if (clip == null || !oneShot) syncLoop()
        if (clip == null) return
        val ms = fxSeconds * 1000f
        if (ms <= 0f) return
        if (oneShot && isHolding) {
            holdLeftMs -= ms
            elapsedMs += ms
            if (holdLeftMs <= 0f) release()
            return
        }
        if (finished) return
        val rate = if (oneShot) 1f else speed.coerceIn(0.25f, 3f)
        inFrameMs += ms * rate
        elapsedMs += ms * rate
        while (true) {
            val cur = clip ?: return
            val stretch = if (holdEvent != null && index <= holdIndex) preStretch else 1f
            val d = cur.durationsMs[index] * stretch
            if (inFrameMs < d) break
            if (holdEvent != null && index == holdIndex) { inFrameMs = 0f; break } // wait at the event
            inFrameMs -= d
            if (index + 1 < cur.frames.size) {
                index++
            } else if (cur.loops && !oneShot) {
                index = 0
            } else if (oneShot && !holdLastFrame) {
                oneShot = false
                syncLoop(force = true)
                return
            } else {
                finished = true // KO, death: stay on the last frame
                inFrameMs = 0f
                break
            }
            frame = cur.frames[index]
        }
    }

    private fun desiredLoop(): AnimTag = override ?: base

    private fun syncLoop(force: Boolean = false) {
        val src = source ?: return
        val c = src.clip(desiredLoop(), phase2)
        if (force || clip?.name != c.name) start(c, oneShot = false)
    }

    private fun start(c: AnimClip, oneShot: Boolean) {
        clip = c
        index = 0
        inFrameMs = 0f
        elapsedMs = 0f
        this.oneShot = oneShot
        holdLastFrame = false
        finished = false
        clearHold()
        frame = c.frames.firstOrNull() ?: 0
        clipName = c.name
    }

    private fun jumpTo(i: Int) {
        val c = clip ?: return
        index = i.coerceIn(0, c.frames.lastIndex)
        inFrameMs = 0f
        frame = c.frames[index]
    }

    private fun clearHold() {
        holdEvent = null
        holdIndex = -1
        holdLeftMs = 0f
        preStretch = 1f
    }

    private fun restart() {
        clip = null
        oneShot = false
        clearHold()
        syncLoop(force = true)
    }

    companion object {
        /** A held cast or attack lets go on its own if its blow never comes (a buff, an interrupted move). */
        const val DEFAULT_HOLD_TIMEOUT_MS = 1200L
        const val HERO_CAST_HOLD_TIMEOUT_MS = 3000L
    }
}
