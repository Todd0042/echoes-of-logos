package com.voicerpg.engine.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusSystemTest {

    @Test
    fun `statuses are resisted never blocked - every status family tier lands`() {
        val problems = mutableListOf<String>()
        for (defKey in StatusCatalogTable.DEFS.keys) {
            val id = StatusId.fromNameOrNull(defKey) ?: continue
            for (family in EnemyFamily.entries) {
                for (tier in listOf("BASIC", "ADEPT", "MASTER", "MYTHIC", "TRANSCENDENTAL")) {
                    val landed = StatusSystem.apply(id, tier, family, fromHit = 100f)
                    if (landed == null) { problems += "$id/$tier/$family landed null"; continue }
                    if (landed.turnsLeft < 1) problems += "$id/$tier/$family turns=${landed.turnsLeft}"
                    val base = StatusCatalogTable.DEFS.getValue(defKey).tiers.getValue(tier).potPct
                    if (landed.potPct < base * 0.25f - 0.0001f) problems += "$id/$tier/$family potency=${landed.potPct}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `eloquence strengthens control - higher tiers bind longer and harder`() {
        val basic = StatusSystem.apply(StatusId.ROOT, "BASIC", EnemyFamily.FLESH, 100f)!!
        val transc = StatusSystem.apply(StatusId.ROOT, "TRANSCENDENTAL", EnemyFamily.FLESH, 100f)!!
        assertTrue("basic=${basic.turnsLeft} transc=${transc.turnsLeft}", transc.turnsLeft > basic.turnsLeft)
        assertTrue(transc.potPct >= basic.potPct)
    }

    @Test
    fun `strong resist degrades but the effect is still felt`() {
        val rootOnConstruct = StatusSystem.apply(StatusId.ROOT, "MASTER", EnemyFamily.CONSTRUCT, 100f)!!
        val rootOnFlesh = StatusSystem.apply(StatusId.ROOT, "MASTER", EnemyFamily.FLESH, 100f)!!
        assertTrue(rootOnConstruct.potPct < rootOnFlesh.potPct)
        assertTrue("construct root must still land", rootOnConstruct.turnsLeft >= 1 && rootOnConstruct.potPct > 0f)
    }

    @Test
    fun `dot escalation and expiry`() {
        val burn = StatusSystem.apply(StatusId.BURN, "MYTHIC", EnemyFamily.FLESH, 100f)!!
        var remaining = listOf(burn)
        var total = 0
        var ticks = 0
        while (remaining.isNotEmpty()) {
            val (dmg, updated) = StatusSystem.advanceTurn(remaining)
            total += dmg
            ticks++
            remaining = updated
        }
        assertTrue("burn must deal damage over its life", total > 0)
        assertEquals(burn.turnsLeft, ticks)
    }

    @Test
    fun `overload and freeze stun - chill and root slow but do not stun`() {
        val overload = listOf(StatusSystem.apply(StatusId.OVERLOAD, "MASTER", EnemyFamily.FLESH, 50f)!!)
        assertTrue(StatusSystem.isStunned(overload))
        val crawl = StatusSystem.speedMult(overload)
        assertTrue("stun must crawl (never a deadlocked gauge), got $crawl", crawl in 0.05f..0.25f)
        val chill = listOf(StatusSystem.apply(StatusId.CHILL, "ADEPT", EnemyFamily.FLESH, 50f)!!)
        val slow = StatusSystem.speedMult(chill)
        assertTrue("chilled speed $slow must be slowed but not immobile", slow in 0.3f..0.85f)
        org.junit.Assert.assertFalse(StatusSystem.isStunned(chill))

        val root = listOf(StatusSystem.apply(StatusId.ROOT, "ADEPT", EnemyFamily.FLESH, 50f)!!)
        val rootSlow = StatusSystem.speedMult(root)
        assertTrue("rooted speed $rootSlow must slow ATB gauge", rootSlow in 0.3f..0.8f)
        org.junit.Assert.assertFalse("root must NOT stun the actor into skipping their turn", StatusSystem.isStunned(root))
    }

    @Test
    fun `corrode lowers defense bless raises it`() {
        val corrode = StatusSystem.defenseMult(listOf(StatusSystem.apply(StatusId.CORRODE, "MASTER", EnemyFamily.CONSTRUCT, 60f)!!))
        assertTrue(corrode < 1f)
        val bless = StatusSystem.defenseMult(listOf(StatusSystem.apply(StatusId.BLESS, "MASTER", EnemyFamily.FLESH, 0f)!!))
        assertTrue(bless > 1f)
    }

    @Test
    fun `merge keeps the stronger instance not the newest weak one`() {
        val strong = StatusSystem.apply(StatusId.BURN, "TRANSCENDENTAL", EnemyFamily.FLESH, 100f)!!
        val weak = StatusSystem.apply(StatusId.BURN, "BASIC", EnemyFamily.FLESH, 100f)!!
        val merged = StatusSystem.merge(listOf(strong), weak)
        assertEquals(1, merged.size)
        assertTrue(merged.first().turnsLeft == strong.turnsLeft)
    }
}
