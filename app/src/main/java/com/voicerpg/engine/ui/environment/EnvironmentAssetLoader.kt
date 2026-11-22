package com.voicerpg.engine.ui.environment

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.BattleEnvironment

/**
 * Loads and caches 16-bit SNES JRPG battle background artworks from assets.
 * Supports orientation-aware variants (_p.jpg) and drop-in environments from GameContent.
 */
object EnvironmentAssetLoader {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun getOrLoad(
        context: Context,
        environmentId: String? = null,
        environment: BattleEnvironment = BattleEnvironment.FOREST,
        isPortrait: Boolean = false
    ): ImageBitmap? {
        val envDef = GameContent.environmentById(environmentId) ?: GameContent.environmentById(environment.name)
        val basePath = envDef?.backgroundAsset?.ifBlank { null } ?: when (environment) {
            BattleEnvironment.FOREST -> "environments/forest.jpg"
            BattleEnvironment.CASTLE -> "environments/castle.jpg"
            BattleEnvironment.DUNGEON -> "environments/dungeon.jpg"
            BattleEnvironment.CAVE -> "environments/cave.jpg"
            BattleEnvironment.SWAMP -> "environments/swamp.jpg"
        }

        val resolvedPath = if (isPortrait) portraitVariantPath(basePath) else basePath
        val primaryPath = resolvedPath
        val fallbackPath = if (resolvedPath != basePath) basePath else null

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

    fun getOrLoad(context: Context, environment: BattleEnvironment): ImageBitmap? =
        getOrLoad(context, null, environment, false)

    private fun portraitVariantPath(assetPath: String): String {
        val dotIdx = assetPath.lastIndexOf('.')
        return if (dotIdx < 0) assetPath
        else assetPath.substring(0, dotIdx) + "_p" + assetPath.substring(dotIdx)
    }

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
