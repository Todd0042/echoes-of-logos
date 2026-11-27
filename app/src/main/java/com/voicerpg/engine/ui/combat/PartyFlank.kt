package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.ui.fx.CombatantFx
import com.voicerpg.engine.ui.sprites.PixelCharacterView
import com.voicerpg.engine.ui.sprites.SpriteBase
import com.voicerpg.engine.ui.sprites.SpriteLibrary

/**
 * The party's side of the arena: the fighters in formation ([BattleFormation]), full size, facing the enemies.
 * [frontIds] lists who stands in front, front first (by default the formation's: see
 * [BattleFormation.partyFrontOrder]). The acting hero steps forward and is drawn on top.
 */
@Composable
fun PartyFlank(
    party: List<PartyMember>,
    activePartyMemberId: String?,
    onSelectHero: (PartyMember) -> Unit,
    onPositionHero: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    fxOf: ((String) -> CombatantFx)? = null,
    modifier: Modifier = Modifier,
    frontIds: List<String>? = null
) {
    val density = LocalDensity.current.density
    val figures = remember(party.map { it.id }, density) { party.map { BattleFormation.figureOf(it, density) } }
    StageFormation(
        figures = figures,
        facingRight = true,
        activeId = activePartyMemberId,
        frontOrder = frontIds ?: BattleFormation.partyFrontOrder(party),
        modifier = modifier.fillMaxHeight(),
        fighter = { i ->
            val member = party[i]
            PartyFlankMemberItem(
                member = member,
                isActiveTurn = member.id == activePartyMemberId,
                arenaCoordinates = arenaCoordinates,
                onPositionHero = onPositionHero,
                fx = fxOf?.invoke(member.id)
            )
        },
        hitTarget = { i ->
            val member = party[i]
            val alive = member.currentHp > 0 && member.stance != CharacterStance.DEAD
            Box(
                Modifier
                    .semantics { contentDescription = member.name }
                    .clickable(enabled = alive) { onSelectHero(member) }
            )
        }
    )
}

@Composable
private fun PartyFlankMemberItem(
    member: PartyMember,
    isActiveTurn: Boolean,
    arenaCoordinates: LayoutCoordinates?,
    onPositionHero: ((String, Offset) -> Unit)?,
    fx: CombatantFx? = null
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
        isTurnReady = member.isTurnReady,
        isActiveTurn = isActiveTurn,
        stance = member.stance,
        isFlippedHorizontally = false,
        isGhost = member.isGhost,
        spriteAlpha = member.spriteAlpha,
        pixelSize = BattleFormation.PARTY_PIXEL_DP.dp,
        sprite = remember(member.id, member.spriteKey) { SpriteLibrary.source(SpriteLibrary.keyFor(member)) },
        baseAnim = SpriteBase.of(member, isActive = isActiveTurn),
        fx = fx,
        // Taps land on the formation's hit target over the body (StageFormation), not on the whole card.
        onClick = null,
        modifier = Modifier.onGloballyPositioned { cardCoords = it }
    )
}
