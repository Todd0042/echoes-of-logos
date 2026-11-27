package com.voicerpg.engine.balance

import com.google.gson.GsonBuilder
import com.voicerpg.engine.combat.AutoPolicy
import com.voicerpg.engine.content.GameContent
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The balance tuner (on demand: `VB_BALANCE_TUNE=1 VB_TEST_FILTER="*BalanceTunerTest*" scripts/test_content_packs.sh
 * <game>`). For every story fight it searches how much to scale the enemies' HP and attack so the sensible player
 * at the game's balance point wins in the middle of the fight's target rounds with a safety margin in the middle
 * of its band (BalanceTargets), measured across every route that has the fight. Writes
 * `app/build/reports/balance-tuning.json` (`{ encounterId: { hpScale, attackScale, ... } }`) for
 * scripts/apply_balance_tuning.py to write into the game's encounters.json.
 */
class BalanceTunerTest {

    data class Tuning(val kind: String, val hpScale: Float, val attackScale: Float, val rounds: Int, val margin: Float, val winRate: Float)

    private data class Eval(val rounds: Int, val margin: Float, val winRate: Float, val support: Float = 0f)

    private fun scaled(setup: BattleSimulator.FightSetup, hp: Float, attack: Float): BattleSimulator.FightSetup =
        setup.copy(encounter = setup.encounter.copy(enemies = setup.encounter.enemies.map { e ->
            val maxHp = (e.maxHp * hp).roundToInt().coerceAtLeast(1)
            e.copy(maxHp = maxHp, currentHp = maxHp, baseAttack = (e.baseAttack * attack).roundToInt().coerceAtLeast(1))
        }))

    /** VB_BALANCE_ONLY=<encounter id>: tune only that fight and print every run. */
    private val only: String? = System.getenv("VB_BALANCE_ONLY")?.ifBlank { null }
    private val debug get() = only != null

    private fun evaluate(setups: List<Pair<String, BattleSimulator.FightSetup>>, hp: Float, attack: Float, at: Float): Eval {
        val labelled = setups.flatMap { (label, s) -> (1..3).map { seed -> label to BattleSimulator.run(scaled(s, hp, attack), AutoPolicy.SENSIBLE, at, seed) } }
        val runs = labelled.map { it.second }
        if (debug) labelled.forEach { (label, r) ->
            println(String.format(Locale.US, "    [%s] hp x%.2f atk x%.2f: %s %s rounds %d, took %d of %d HP (margin %.2f), %d falls, %d/%d support, spells %s",
                label, hp, attack, if (r.finished) "finished" else "UNFINISHED", if (r.won) "won" else "lost", r.rounds, r.damageTaken, r.partyMaxHp,
                r.safetyMargin, r.falls, r.supportActions, r.partyActions, r.spellsUsed))
        }
        val wins = runs.filter { it.won }
        val rounds = (wins.ifEmpty { runs }).map { it.rounds }.sorted().let { it[it.size / 2] }
        val margin = runs.map { it.safetyMargin }.sorted().let { it[it.size / 2] }
        return Eval(rounds, margin, wins.size.toFloat() / runs.size, runs.map { it.supportShare }.average().toFloat())
    }

    @Test
    fun `tune every story fight to its targets`() {
        assumeTrue("set VB_BALANCE_TUNE=1 to run the tuner", System.getenv("VB_BALANCE_TUNE") == "1")
        val at = GameContent.balance.balanceResonance
        val byEncounter = BalanceTargets.routeFights().groupBy { it.encounterId }
        val out = linkedMapOf<String, Tuning>()
        for ((id, fights) in byEncounter) {
            if (only != null && id != only) continue
            // The hardest kind any route gives the fight decides its targets; at most 4 routes are measured.
            val kind = fights.maxByOrNull { it.kind.ordinal }!!.kind
            val setups = fights.distinctBy { it.route }.take(4).map { it.route to it.setup }
            var hp = 1f
            var attack = 1f
            var e = evaluate(setups, hp, attack, at)
            repeat(4) {
                // Damped steps (a partial move toward the target) so noisy fights settle instead of swinging past it.
                hp = (hp * Math.pow((kind.targetRounds / e.rounds.coerceAtLeast(1)).coerceIn(0.5f, 2f).toDouble(), 0.6).toFloat()).coerceIn(0.3f, 6f)
                e = evaluate(setups, hp, attack, at)
                attack = (attack * Math.pow((e.margin / kind.targetMargin).coerceIn(0.5f, 2f).toDouble(), 0.6).toFloat()).coerceIn(0.3f, 6f)
                e = evaluate(setups, hp, attack, at)
                // The sensible player must still win: ease off the attack until they do.
                var guard = 0
                // ... and without spending more than about a third of its turns healing (no heal loops).
                while ((e.winRate < 0.67f || e.support > 0.3f) && attack > 0.3f && guard++ < 6) {
                    attack *= 0.85f
                    e = evaluate(setups, hp, attack, at)
                }
            }
            out[id] = Tuning(kind.label, hp, attack, e.rounds, e.margin, e.winRate)
            println(String.format(Locale.US, "%-40s %-9s hp x%.2f attack x%.2f -> %d rounds, margin %.2f, win %d%%",
                id.take(40), kind.label, hp, attack, e.rounds, e.margin, (e.winRate * 100).toInt()))
        }
        val dir = listOf(File("build/reports"), File("app/build/reports")).firstOrNull { it.parentFile?.isDirectory == true } ?: File("build/reports")
        dir.mkdirs()
        File(dir, "balance-tuning.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(out))
    }
}
