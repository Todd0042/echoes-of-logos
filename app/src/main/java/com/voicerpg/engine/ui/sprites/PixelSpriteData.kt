package com.voicerpg.engine.ui.sprites

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.content.GameContent

enum class SpriteFrame {
    IDLE_UPRIGHT,
    IDLE_CROUCH,
    ACTION_CAST,
    DAMAGED
}

/**
 * 32-bit Enhanced Pixel Sprites (20x26 grid) with rich multi-tone shading,
 * detailed silhouettes, gear articulation, and animation frames.
 *
 * Sprites are dynamically loaded from game asset JSON files (characters/ and
 * enemies/enemy_sprites.json). If no sprite is mapped, a clean generic fallback is used.
 */
object PixelSpriteData {

    val Transparent = Color.Transparent
    val DarkOutline = Color(0xFF0C0E14)
    val WhiteSpec = Color(0xFFFFFFFF)

    // Neutral 20x26 fallback silhouette
    private val FALLBACK_PALETTE = mapOf(
        '.' to Color.Transparent,
        'K' to Color(0xFF0C0E14),
        'X' to Color(0xFF78909C),
        'x' to Color(0xFF455A64)
    )

    private val FALLBACK_UPRIGHT = listOf(
        "....................",
        ".......KKKK.........",
        "......KXXXXK........",
        ".....KXXXXXXK.......",
        ".....KXXXXXXK.......",
        "......KXXXXK........",
        ".......KKKK.........",
        "......KKKKKK........",
        ".....KXXXXXXK.......",
        "....KXXXXXXXXK......",
        "...KXXXXXXXXXXK.....",
        "...KXXXXXXXXXXK.....",
        "...KXXXXXXXXXXK.....",
        "...KXXxxxxxxXXK.....",
        "...KXXXXXXXXXXK.....",
        "....KXXXXXXXXK......",
        "....KXXXXXXXXK......",
        "....KXXKKKKXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "...KXXXK..KXXXK.....",
        "...KKKKK..KKKKK.....",
        "...................."
    )

    private val FALLBACK_CROUCH = listOf(
        "....................",
        "....................",
        ".......KKKK.........",
        "......KXXXXK........",
        ".....KXXXXXXK.......",
        ".....KXXXXXXK.......",
        "......KXXXXK........",
        ".......KKKK.........",
        "......KKKKKK........",
        ".....KXXXXXXK.......",
        "....KXXXXXXXXK......",
        "...KXXXXXXXXXXK.....",
        "...KXXXXXXXXXXK.....",
        "...KXXXXXXXXXXK.....",
        "...KXXxxxxxxXXK.....",
        "...KXXXXXXXXXXK.....",
        "....KXXXXXXXXK......",
        "....KXXXXXXXXK......",
        "....KXXKKKKXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "....KXXK..KXXK......",
        "...KXXXK..KXXXK.....",
        "...KKKKK..KKKKK.....",
        "...................."
    )

    fun getPixelMatrix(
        characterId: String,
        frame: SpriteFrame
    ): Pair<List<String>, Map<Char, Color>> {
        // 1. Dynamic sprite resolution from loaded character content
        val directCharacter = GameContent.characterById(characterId)
        val character = directCharacter ?: GameContent.characters.values.firstOrNull { c ->
            characterId.equals(c.id, ignoreCase = true) ||
            characterId.lowercase().contains(c.id.lowercase()) ||
            characterId.equals(c.speaker.name, ignoreCase = true) ||
            c.voiceAliases.any { it.equals(characterId, ignoreCase = true) }
        }
        if (character?.sprite != null) {
            val grid = character.sprite.getFrame(frame)
            return grid to character.sprite.palette
        }

        // 2. Dynamic sprite resolution from loaded enemy sprites
        val directEnemySprite = GameContent.enemySpriteById(characterId)
        val enemySprite = directEnemySprite ?: run {
            val templateSpriteId = GameContent.enemyTemplateById(characterId)?.spriteId
            if (!templateSpriteId.isNullOrBlank()) {
                GameContent.enemySpriteById(templateSpriteId)
            } else {
                GameContent.enemySprites.entries.firstOrNull { (k, _) ->
                    characterId.equals(k, ignoreCase = true) ||
                    characterId.lowercase().contains(k.lowercase()) ||
                    k.lowercase().contains(characterId.lowercase())
                }?.value
            }
        }
        if (enemySprite != null) {
            val grid = enemySprite.getFrame(frame)
            return grid to enemySprite.palette
        }

        // 3. Fallback neutral silhouette
        val grid = when (frame) {
            SpriteFrame.IDLE_UPRIGHT -> FALLBACK_UPRIGHT
            else -> FALLBACK_CROUCH
        }
        return grid to FALLBACK_PALETTE
    }
}
