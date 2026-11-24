package com.voicerpg.engine.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.SpellSchool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Developer FX test bench: scripted cue sequences that play straight into a [BattleFxDirector] on the
 * current encounter, without touching the battle. The main way to judge feel on a device
 * (docs/COMBAT_FEEL_PLAN.md "Verification for every milestone"). Grows with each milestone.
 */
object FxBench {

    /** A scripted beat: wait [afterMs], then play [cue]. */
    data class Step(val afterMs: Long, val cue: BattleCue)

    fun heroCast(tier: ResonanceTier, casterId: String, targetIds: List<String>, timing: CombatTiming = CombatTiming.DEFAULT): List<Step> {
        val bonus = tier.minBonusPercent
        val resonance = ResonanceResult(
            rawText = "fx bench", score = bonus / 200f, bonusPercent = bonus, tier = tier,
            damageMultiplier = 1f + bonus / 100f, particleCount = tier.baseParticleCount,
            matchedThematicRoots = emptyList(), syllableCount = 0
        )
        val reveal = BattleCue.ResonanceReveal(casterId, resonance, showBanner = bonus >= 50)
        val travel = timing.travelMs(fromParty = true, isBossUltimate = false, tier = tier)
        val charge = BattleCue.ChargeStart(casterId, typed = true)
        return listOf(
            Step(0, charge),
            Step(timing.holdMs(charge, pocket = false), BattleCue.ChargeEnd(casterId)),
            Step(0, BattleCue.Release(
                casterId = casterId, fromParty = true, targetIds = targetIds, school = SpellSchool.PYROMANCY,
                style = BattleCue.ReleaseStyle.PROJECTILE, tier = tier, bonusPercent = bonus,
                particleCount = tier.baseParticleCount, travelMs = travel
            )),
            Step(travel, reveal),
            Step(0, BattleCue.Impact(casterId, fromParty = true, tier = tier, shake = 8f,
                hits = targetIds.mapIndexed { i, id -> BattleCue.Hit(id, 100 + 40 * tier.ordinal, affinity = if (i == 0 && tier >= ResonanceTier.MASTER) 1.6f else 1f) }))
        )
    }

    fun enemyStrike(enemyId: String, targetIds: List<String>, bossUltimate: Boolean, timing: CombatTiming = CombatTiming.DEFAULT): List<Step> {
        val travel = timing.travelMs(fromParty = false, isBossUltimate = bossUltimate)
        return listOf(
            Step(0, BattleCue.EnemyMove(enemyId, if (bossUltimate) "Ultimate" else "Strike", "BASIC", bossUltimate)),
            Step(0, BattleCue.Release(
                casterId = enemyId, fromParty = false, targetIds = targetIds, school = SpellSchool.SHADOW,
                style = BattleCue.ReleaseStyle.PROJECTILE, particleCount = 15, travelMs = travel
            )),
            Step(travel, BattleCue.Impact(enemyId, fromParty = false, shake = if (bossUltimate) 18f else 6f,
                hits = targetIds.map { BattleCue.Hit(it, 60) }))
        )
    }
}

/** A row of bench buttons for the battle screen's developer tools. */
@Composable
fun FxBenchBar(
    director: BattleFxDirector,
    partyIds: List<String>,
    enemyIds: List<String>,
    bossId: String?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    fun play(steps: List<FxBench.Step>) {
        scope.launch {
            for (step in steps) {
                if (step.afterMs > 0) delay(step.afterMs)
                director.play(step.cue)
            }
        }
    }
    val hero = partyIds.firstOrNull()
    val foe = enemyIds.firstOrNull()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BenchChip("FX") {}
        if (hero != null && foe != null) {
            ResonanceTier.entries.forEach { tier ->
                BenchChip(tier.name.take(5)) { play(FxBench.heroCast(tier, hero, listOf(foe))) }
            }
            BenchChip("AOE") { play(FxBench.heroCast(ResonanceTier.MASTER, hero, enemyIds)) }
            BenchChip("HIT ME") { play(FxBench.enemyStrike(foe, listOf(hero), bossUltimate = false)) }
            BenchChip("BOSS ULT") { play(FxBench.enemyStrike(bossId ?: foe, partyIds, bossUltimate = true)) }
        }
    }
}

@Composable
private fun BenchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF263238))
            .border(1.dp, Color(0xFF80CBC4), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, color = Color(0xFFE0F2F1), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
