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
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.ui.fx.CombatantFx
import com.voicerpg.engine.ui.sprites.PixelCharacterView

/**
 * The enemies' side of the arena: the group in formation ([BattleFormation]), full size (each at its own `scale`),
 * facing the party, bosses at the back by default ([BattleFormation.enemyFrontOrder]); [frontIds] overrides who
 * stands in front, front first.
 */
@Composable
fun EnemyFlank(
    enemies: List<Enemy>,
    onSelectEnemy: (Enemy) -> Unit,
    onPositionEnemy: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    fxOf: ((String) -> CombatantFx)? = null,
    /** Where each enemy's sprite is drawn, in arena pixels (the death shatter starts there). */
    onSpriteBounds: ((String, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    /** Enemies whose health runs in a boss bar on top (BattleHud): they get no sliver on the field. */
    bossBarIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    frontIds: List<String>? = null
) {
    val density = LocalDensity.current.density
    val figures = remember(enemies.map { Triple(it.id, it.spriteId, it.scale) }, density) { enemies.map { BattleFormation.figureOf(it, density) } }
    StageFormation(
        figures = figures,
        facingRight = false,
        activeId = null,
        frontOrder = frontIds ?: BattleFormation.enemyFrontOrder(enemies),
        modifier = modifier.fillMaxHeight(),
        fighter = { i ->
            val enemy = enemies[i]
            EnemyCard(
                enemy = enemy,
                onPositionEnemy = onPositionEnemy,
                arenaCoordinates = arenaCoordinates,
                fx = fxOf?.invoke(enemy.id),
                onSpriteBounds = onSpriteBounds,
                hasBossBar = enemy.id in bossBarIds
            )
        },
        hitTarget = { i ->
            val enemy = enemies[i]
            Box(
                Modifier
                    .semantics { contentDescription = enemy.name }
                    .clickable(enabled = enemy.isAlive) { onSelectEnemy(enemy) }
            )
        }
    )
}

@Composable
private fun EnemyCard(
    enemy: Enemy,
    onPositionEnemy: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    fx: CombatantFx? = null,
    onSpriteBounds: ((String, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    hasBossBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val stance = when {
        !enemy.isAlive -> CharacterStance.DEAD
        enemy.isDamagedFlash -> CharacterStance.DAMAGED
        else -> CharacterStance.READY
    }

    val sprite = remember(enemy.id, enemy.spriteId) { com.voicerpg.engine.ui.sprites.SpriteLibrary.source(com.voicerpg.engine.ui.sprites.SpriteLibrary.keyFor(enemy)) }
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
            onPositionEnemy?.invoke(enemy.id, center)
        }
    }

    PixelCharacterView(
        characterId = com.voicerpg.engine.ui.sprites.SpriteLibrary.keyFor(enemy),
        name = enemy.name,
        subtitle = enemy.subtitle,
        hpRatio = enemy.hpRatio,
        currentHp = enemy.currentHp,
        isTurnReady = enemy.isTurnReady,
        isActiveTurn = false,
        isTargeted = enemy.isAlive && enemy.isTargeted,
        stance = stance,
        isFlippedHorizontally = true,
        pixelSize = BattleFormation.ENEMY_PIXEL_DP.dp * com.voicerpg.engine.ui.sprites.SpriteSizing.enemyFactor(enemy, sprite),
        sprite = sprite,
        baseAnim = com.voicerpg.engine.ui.sprites.SpriteBase.of(enemy),
        sliverAlpha = if (hasBossBar) null else BattleHud.sliverAlpha(enemy, fx, hasBossBar = false),
        fx = fx,
        onSpriteLayout = if (onSpriteBounds == null) null else { coords ->
            val arena = arenaCoordinates
            if (arena != null && arena.isAttached && coords.isAttached) onSpriteBounds(enemy.id, arena.localBoundingBoxOf(coords))
        },
        // Taps land on the formation's hit target over the body (StageFormation), not on the whole card.
        onClick = null,
        modifier = modifier.onGloballyPositioned { cardCoords = it }
    )
}
