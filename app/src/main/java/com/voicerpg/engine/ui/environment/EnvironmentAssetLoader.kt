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

    /** A loaded backdrop and which variant it is (a missing `_p`/`_s` falls back to the base picture). */
    data class Backdrop(val image: ImageBitmap, val variant: BackgroundAspect)

    fun getOrLoad(
        context: Context,
        environmentId: String? = null,
        environment: BattleEnvironment = BattleEnvironment.FOREST,
        aspect: BackgroundAspect = BackgroundAspect.LANDSCAPE
    ): ImageBitmap? = loadBackdrop(context, environmentId, environment, aspect)?.image

    fun loadBackdrop(
        context: Context,
        environmentId: String? = null,
        environment: BattleEnvironment = BattleEnvironment.FOREST,
        aspect: BackgroundAspect = BackgroundAspect.LANDSCAPE
    ): Backdrop? {
        val envDef = GameContent.environmentById(environmentId) ?: GameContent.environmentById(environment.name)
        val basePath = envDef?.backgroundAsset?.ifBlank { null } ?: when (environment) {
            BattleEnvironment.FOREST -> "environments/forest.jpg"
            BattleEnvironment.CASTLE -> "environments/castle.jpg"
            BattleEnvironment.DUNGEON -> "environments/dungeon.jpg"
            BattleEnvironment.CAVE -> "environments/cave.jpg"
            BattleEnvironment.SWAMP -> "environments/swamp.jpg"
        }
        val primaryPath = BackgroundAspect.variantPath(basePath, aspect)
        cached(context, primaryPath)?.let { return Backdrop(it, aspect) }
        if (primaryPath == basePath) return null
        return cached(context, basePath)?.let { Backdrop(it, BackgroundAspect.LANDSCAPE) }
    }

    private fun cached(context: Context, path: String): ImageBitmap? =
        if (cache.containsKey(path)) cache[path] else tryLoadBitmap(context, path).also { cache[path] = it }

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
