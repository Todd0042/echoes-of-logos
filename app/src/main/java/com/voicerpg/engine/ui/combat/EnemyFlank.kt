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
import androidx.compose.ui.unit.dp
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.ui.sprites.PixelCharacterView

@Composable
fun EnemyFlank(
    enemies: List<Enemy>,
    onSelectEnemy: (Enemy) -> Unit,
    onPositionEnemy: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    modifier: Modifier = Modifier
) {
    val isLargeHorde = enemies.size > 3

    if (!isLargeHorde) {
        // Diagonal SNES-style staggered enemy formation
        Column(
            modifier = modifier.padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            enemies.forEachIndexed { index, enemy ->
                val depthRatio = if (enemies.size <= 1) 0.5f else index.toFloat() / (enemies.size - 1)
                val depthScale = 0.92f + depthRatio * 0.14f
                val staggerX = (-index * 10).dp
                EnemyCard(
                    enemy = enemy,
                    isCompact = false,
                    depthScale = depthScale,
                    onSelectEnemy = onSelectEnemy,
                    onPositionEnemy = onPositionEnemy,
                    arenaCoordinates = arenaCoordinates,
                    modifier = Modifier.offset(x = staggerX)
                )
            }
        }
    } else {
        // 2-Column Staggered JRPG Formation (4 to 6 enemies)
        // Front row (closer to heroes) and Back row (rearguard)
        val frontRow = enemies.filterIndexed { index, _ -> index % 2 == 0 }
        val backRow = enemies.filterIndexed { index, _ -> index % 2 != 0 }

        Row(
            modifier = modifier.padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Front Row (closer to heroes)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                frontRow.forEachIndexed { index, enemy ->
                    val depthScale = 0.96f + index * 0.05f
                    EnemyCard(
                        enemy = enemy,
                        isCompact = true,
                        depthScale = depthScale,
                        onSelectEnemy = onSelectEnemy,
                        onPositionEnemy = onPositionEnemy,
                        arenaCoordinates = arenaCoordinates
                    )
                }
            }

            // Back Row (rearguard)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                backRow.forEachIndexed { index, enemy ->
                    val depthScale = 0.88f + index * 0.04f
                    EnemyCard(
                        enemy = enemy,
                        isCompact = true,
                        depthScale = depthScale,
                        onSelectEnemy = onSelectEnemy,
                        onPositionEnemy = onPositionEnemy,
                        arenaCoordinates = arenaCoordinates
                    )
                }
            }
        }
    }
}

@Composable
private fun EnemyCard(
    enemy: Enemy,
    isCompact: Boolean,
    depthScale: Float = 1f,
    onSelectEnemy: (Enemy) -> Unit,
    onPositionEnemy: ((String, Offset) -> Unit)? = null,
    arenaCoordinates: LayoutCoordinates? = null,
    modifier: Modifier = Modifier
) {
    val stance = when {
        !enemy.isAlive -> CharacterStance.DEAD
        enemy.isDamagedFlash -> CharacterStance.DAMAGED
        else -> CharacterStance.READY
    }

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
        characterId = enemy.spriteId.ifBlank { enemy.id },
        name = enemy.name,
        subtitle = enemy.subtitle,
        hpRatio = enemy.hpRatio,
        currentHp = enemy.currentHp,
        maxHp = enemy.maxHp,
        atbRatio = enemy.atbRatio,
        isTurnReady = enemy.isTurnReady,
        isActiveTurn = false,
        isTargeted = enemy.isAlive && enemy.isTargeted,
        stance = stance,
        isFlippedHorizontally = true,
        pixelSize = if (isCompact) 1.65.dp else 2.2.dp,
        depthScale = depthScale,
        onClick = if (enemy.isAlive) { { onSelectEnemy(enemy) } } else null,
        modifier = modifier.onGloballyPositioned { cardCoords = it }
    )
}
