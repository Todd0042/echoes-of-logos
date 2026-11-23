package com.voicerpg.engine.ui.environment

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.ui.story.BackgroundAspect

/**
 * Loads and caches 16-bit SNES JRPG battle background artworks from assets.
 * Supports aspect-aware variants (_p.jpg / _s.jpg) and drop-in environments from GameContent.
 */
object EnvironmentAssetLoader {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun getOrLoad(
        context: Context,
        environmentId: String? = null,
        environment: BattleEnvironment = BattleEnvironment.FOREST,
        aspect: BackgroundAspect = BackgroundAspect.LANDSCAPE
    ): ImageBitmap? {
        val envDef = GameContent.environmentById(environmentId) ?: GameContent.environmentById(environment.name)
        val basePath = envDef?.backgroundAsset?.ifBlank { null } ?: when (environment) {
            BattleEnvironment.FOREST -> "environments/forest.jpg"
            BattleEnvironment.CASTLE -> "environments/castle.jpg"
            BattleEnvironment.DUNGEON -> "environments/dungeon.jpg"
            BattleEnvironment.CAVE -> "environments/cave.jpg"
            BattleEnvironment.SWAMP -> "environments/swamp.jpg"
        }

        val primaryPath = BackgroundAspect.variantPath(basePath, aspect)
        val fallbackPath = if (primaryPath != basePath) basePath else null

        if (cache.containsKey(primaryPath)) return cache[primaryPath]

        val primaryBitmap = tryLoadBitmap(context, primaryPath)
        if (primaryBitmap != null) {
            cache[primaryPath] = primaryBitmap
            return primaryBitmap
        }

        cache[primaryPath] = null
        if (fallbackPath == null) return null

        if (cache.containsKey(fallbackPath)) return cache[fallbackPath]
        val fallbackBitmap = tryLoadBitmap(context, fallbackPath)
        cache[fallbackPath] = fallbackBitmap
        return fallbackBitmap
    }

    fun getOrLoad(
        context: Context,
        environmentId: String? = null,
        environment: BattleEnvironment = BattleEnvironment.FOREST,
        isPortrait: Boolean = false
    ): ImageBitmap? = getOrLoad(
        context = context,
        environmentId = environmentId,
        environment = environment,
        aspect = if (isPortrait) BackgroundAspect.PORTRAIT else BackgroundAspect.LANDSCAPE
    )

    fun getOrLoad(context: Context, environment: BattleEnvironment): ImageBitmap? =
        getOrLoad(context, null, environment, BackgroundAspect.LANDSCAPE)

    private fun tryLoadBitmap(context: Context, path: String): ImageBitmap? = try {
        context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }

    fun clear() {
        cache.clear()
    }
}
