package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.ui.sprites.PixelCharacterView

@Composable
fun PartyFlank(
    party: List<PartyMember>,
    activePartyMemberId: String?,
    onSelectHero: (PartyMember) -> Unit,
    onPositionHero: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    modifier: Modifier = Modifier
) {
    if (party.size <= 3) {
        // Diagonal SNES-style staggered column anchored to the floor plane
        Column(
            modifier = modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            party.forEachIndexed { index, member ->
                val depthRatio = if (party.size <= 1) 0.5f else index.toFloat() / (party.size - 1)
                val depthScale = 0.92f + depthRatio * 0.14f
                val staggerX = (index * 10).dp
                PartyFlankMemberItem(
                    member = member,
                    isActiveTurn = member.id == activePartyMemberId,
                    scaledPixelSize = 2.0.dp,
                    depthScale = depthScale,
                    arenaCoordinates = arenaCoordinates,
                    onSelectHero = onSelectHero,
                    onPositionHero = onPositionHero,
                    modifier = Modifier.offset(x = staggerX)
                )
            }
        }
    } else {
        // Adaptive 2-Row Tactical Formation:
        // Front Row (Vanguard): First 2 members (or explicit vanguard) positioned forward
        // Back Row (Support): Remaining support heroes positioned on the left
        val (backRowMembers, frontRowMembers) = remember(party) {
            val hasVanguard = party.any { it.id == "hero" || it.id.contains("vanguard", ignoreCase = true) }
            if (hasVanguard) {
                val front = party.filter { it.id == "hero" || it.id.contains("vanguard", ignoreCase = true) }
                    .take(2)
                val back = party.filterNot { front.contains(it) }
                Pair(back, front)
            } else {
                Pair(party.drop(2), party.take(2))
            }
        }

        val verticalSpacing = when {
            backRowMembers.size >= 3 -> 6.dp
            party.size >= 4 -> 8.dp
            else -> 12.dp
        }
        val frontVerticalSpacing = when {
            backRowMembers.size >= 3 -> 24.dp
            backRowMembers.size == 2 -> 16.dp
            else -> verticalSpacing
        }
        val scaledPixelSize = when {
            party.size >= 5 -> 1.7.dp
            party.size >= 4 -> 1.8.dp
            else -> 2.0.dp
        }

        Row(
            modifier = modifier.padding(start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Row: left side of screen
            if (backRowMembers.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    backRowMembers.forEachIndexed { index, member ->
                        val depthScale = 0.88f + index * 0.05f
                        PartyFlankMemberItem(
                            member = member,
                            isActiveTurn = member.id == activePartyMemberId,
                            scaledPixelSize = scaledPixelSize,
                            depthScale = depthScale,
                            arenaCoordinates = arenaCoordinates,
                            onSelectHero = onSelectHero,
                            onPositionHero = onPositionHero
                        )
                    }
                }
            }

            // Front Row: in front of support row
            if (frontRowMembers.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(frontVerticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    frontRowMembers.forEachIndexed { index, member ->
                        val depthScale = 0.98f + index * 0.06f
                        PartyFlankMemberItem(
                            member = member,
                            isActiveTurn = member.id == activePartyMemberId,
                            scaledPixelSize = scaledPixelSize,
                            depthScale = depthScale,
                            arenaCoordinates = arenaCoordinates,
                            onSelectHero = onSelectHero,
                            onPositionHero = onPositionHero
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyFlankMemberItem(
    member: PartyMember,
    isActiveTurn: Boolean,
    scaledPixelSize: Dp,
    depthScale: Float = 1f,
    arenaCoordinates: LayoutCoordinates?,
    onSelectHero: (PartyMember) -> Unit,
    onPositionHero: ((String, Offset) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var cardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(cardCoords, arenaCoordinates) {
        val card = cardCoords
        val arena = arenaCoordinates
        if (card != null && arena != null && card.isAttached && arena.isAttached) {
            val pos = arena.localPositionOf(card, Offset.Zero)
            val center = Offset(
                pos.x + card.size.width / 2f,
                pos.y + card.size.height / 2f
            )
            onPositionHero?.invoke(member.id, center)
        }
    }

    PixelCharacterView(
        characterId = member.id,
        name = member.name,
        subtitle = member.loreClass,
        hpRatio = member.hpRatio,
        currentHp = member.currentHp,
        maxHp = member.maxHp,
        mpRatio = member.mpRatio,
        atbRatio = member.atbRatio,
        isTurnReady = member.isTurnReady,
        isActiveTurn = isActiveTurn,
        stance = member.stance,
        isFlippedHorizontally = false,
        isGhost = member.isGhost,
        spriteAlpha = member.spriteAlpha,
        pixelSize = scaledPixelSize,
        depthScale = depthScale,
        onClick = { onSelectHero(member) },
        modifier = modifier.onGloballyPositioned { cardCoords = it }
    )
}
