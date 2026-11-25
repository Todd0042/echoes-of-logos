package com.voicerpg.engine.ui.fx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.exp

/**
 * One combatant's live effects, read by its sprite (PixelCharacterView) every frame. The director
 * starts them; [step] runs them down. Knockback and squash run on arena time (they freeze in a
 * hit-stop); the white flash and glow run on real time (they stay visible through it).
 */
class CombatantFx {
    /** 0..1: the sprite is drawn toward solid white. */
    var flash by mutableFloatStateOf(0f)
        private set

    /** dp along x: positive toward the enemy side. */
    var knockback by mutableFloatStateOf(0f)
        private set

    /** -1..1: positive squashes (wider, shorter), negative stretches. */
    var squash by mutableFloatStateOf(0f)
        private set

    /** A coloured aura behind the sprite (wind-up, charge); [glowStrength] 0..1. */
    var glow by mutableStateOf<Color?>(null)
    var glowStrength by mutableFloatStateOf(0f)

    /** A telegraphed intent above the sprite ("⚔", "☄"…), or null. */
    var intent by mutableStateOf<String?>(null)

    /** 0..1 fill of a boss's ultimate charge bar, or null when not charging. */
    var ultimateCharge by mutableStateOf<Float?>(null)

    /** The sprite has shattered (or faded) away. */
    var hidden by mutableStateOf(false)

    /** 1 = solid; runs down to 0 during a fade-out death. */
    var fade by mutableFloatStateOf(1f)
        private set

    /** Degrees: a comic spin after a hit. */
    var spin by mutableFloatStateOf(0f)
        private set

    /** A taunt in a speech bubble above the sprite, or null. */
    var speech by mutableStateOf<String?>(null)

    /** Leaning forward to attack (wind-up), 0..1; eases toward [leanTarget]. */
    var lean by mutableFloatStateOf(0f)
        private set
    var leanTarget = 0f

    /** 0..1: how visible this combatant's health sliver is (shown after a hit or heal, then fades). */
    var vitals by mutableFloatStateOf(0f)
        private set
    private var vitalsHold = 0f

    /** The sprite's animations (docs/SPRITE_PLAN.md), driven by the director's cues on effects time. */
    val animator = com.voicerpg.engine.ui.sprites.SpriteAnimator()

    /** Shows the health sliver for a few seconds (a hit, a heal, a tick). */
    fun revealVitals() {
        vitals = 1f
        vitalsHold = VITALS_HOLD_SECONDS
    }

    private var knockAmp = 0f
    private var knockT = 0f
    private var squashAmp = 0f
    private var squashT = 0f

    private var fading = false
    private var spinning = false
    private var spinT = 0f

    fun hit(direction: Float, amplitudeDp: Float, reaction: com.voicerpg.engine.model.HitReaction = com.voicerpg.engine.model.HitReaction.FLINCH) {
        flash = 1f
        val amp = if (reaction == com.voicerpg.engine.model.HitReaction.STAGGER) amplitudeDp * 1.6f else amplitudeDp
        knockAmp = direction * amp
        knockT = 0f
        knockback = knockAmp
        squashAmp = if (reaction == com.voicerpg.engine.model.HitReaction.STAGGER) 0.5f else 0.35f
        squashT = 0f
        if (reaction == com.voicerpg.engine.model.HitReaction.COMIC_SPIN) {
            spinning = true
            spinT = 0f
        }
    }

    fun startFade() {
        fading = true
    }

    /** A small stretch then settle (casting, attacking). */
    fun pulse(strength: Float = 0.25f) {
        squashAmp = -strength
        squashT = 0f
    }

    fun step(realSeconds: Float, fxSeconds: Float) {
        animator.step(fxSeconds)
        if (fading && fade > 0f) {
            fade = (fade - fxSeconds / FADE_SECONDS).coerceAtLeast(0f)
            if (fade == 0f) hidden = true
        }
        if (spinning) {
            spinT += fxSeconds
            val t = (spinT / SPIN_SECONDS).coerceAtMost(1f)
            spin = 360f * (1f - (1f - t) * (1f - t)) // ease out
            if (t >= 1f) { spinning = false; spin = 0f }
        }
        if (lean != leanTarget) {
            lean += (leanTarget - lean) * (fxSeconds * 12f).coerceAtMost(1f)
            if (kotlin.math.abs(lean - leanTarget) < 0.01f) lean = leanTarget
        }
        if (flash > 0f) flash = (flash - realSeconds / FLASH_SECONDS).coerceAtLeast(0f)
        if (vitalsHold > 0f) vitalsHold -= realSeconds
        else if (vitals > 0f) vitals = (vitals - realSeconds / VITALS_FADE_SECONDS).coerceAtLeast(0f)
        if (knockAmp != 0f) {
            knockT += fxSeconds
            val e = exp(-knockT / KNOCK_DECAY)
            knockback = knockAmp * e * cos(knockT * KNOCK_RATE)
            if (e < 0.02f) { knockAmp = 0f; knockback = 0f }
        }
        if (squashAmp != 0f) {
            squashT += fxSeconds
            val e = exp(-squashT / SQUASH_DECAY)
            squash = squashAmp * e * cos(squashT * SQUASH_RATE)
            if (e < 0.02f) { squashAmp = 0f; squash = 0f }
        }
    }

    fun reset() {
        flash = 0f; knockAmp = 0f; knockback = 0f; squashAmp = 0f; squash = 0f
        glow = null; glowStrength = 0f; intent = null; ultimateCharge = null; hidden = false; lean = 0f; leanTarget = 0f
        fade = 1f; fading = false; spin = 0f; spinning = false; speech = null; vitals = 0f; vitalsHold = 0f
        animator.reset()
    }

    private companion object {
        const val FLASH_SECONDS = 0.14f
        const val VITALS_HOLD_SECONDS = 2.5f
        const val VITALS_FADE_SECONDS = 0.4f
        const val FADE_SECONDS = 0.6f
        const val SPIN_SECONDS = 0.45f
        const val KNOCK_DECAY = 0.11f
        const val KNOCK_RATE = 16f
        const val SQUASH_DECAY = 0.09f
        const val SQUASH_RATE = 30f
    }
}
