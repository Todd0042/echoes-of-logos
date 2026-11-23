package com.voicerpg.engine.ui.story

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.ui.creation.SelfiePortraitProcessor

/**
 * Aspect-ratio buckets used to resolve orientation-specific background variants:
 *   - [PORTRAIT] (`_p`): narrow vertical phone screens (`width / height < 0.78`)
 *   - [SQUARE] (`_s`): near-square unfolded foldable inner screens (`0.78..1.28`)
 *   - [LANDSCAPE] (base asset): widescreen phones/tablets (`width / height > 1.28`)
 */
enum class BackgroundAspect(val suffix: String) {
    PORTRAIT("_p"),
    SQUARE("_s"),
    LANDSCAPE("");

    companion object {
        private const val PORTRAIT_MAX_RATIO = 0.78f
        private const val SQUARE_MAX_RATIO = 1.28f

        fun fromDimensions(width: Number, height: Number): BackgroundAspect {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return LANDSCAPE
            val ratio = w / h
            return when {
                ratio < PORTRAIT_MAX_RATIO -> PORTRAIT
                ratio <= SQUARE_MAX_RATIO -> SQUARE
                else -> LANDSCAPE
            }
        }

        fun fromConfiguration(configuration: Configuration): BackgroundAspect {
            if (configuration.screenWidthDp > 0 && configuration.screenHeightDp > 0) {
                return fromDimensions(configuration.screenWidthDp, configuration.screenHeightDp)
            }
            return if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                PORTRAIT
            } else {
                LANDSCAPE
            }
        }

        /**
         * Inserts [aspect]'s suffix (`_p` or `_s`) before the final `.` in [assetPath].
         * Returns [assetPath] unchanged for [LANDSCAPE], if no `.` exists, or if the
         * filename stem already ends with `_p` or `_s`.
         */
        fun variantPath(assetPath: String, aspect: BackgroundAspect): String {
            if (aspect.suffix.isEmpty()) return assetPath
            val dotIdx = assetPath.lastIndexOf('.')
            if (dotIdx < 0) return assetPath
            val stem = assetPath.substring(0, dotIdx)
            if (stem.endsWith("_p", ignoreCase = true) || stem.endsWith("_s", ignoreCase = true)) {
                return assetPath
            }
            return stem + aspect.suffix + assetPath.substring(dotIdx)
        }
    }
}

/**
 * Loads and caches story scene backgrounds and character portraits from assets.
 *
 * Background loading is aspect-aware: when [loadBackground] is called with
 * [BackgroundAspect.PORTRAIT] (`_p`) or [BackgroundAspect.SQUARE] (`_s`), it first
 * resolves the variant path by inserting the suffix before the file extension
 * (e.g. `story/scene_p.jpg` or `story/scene_s.jpg`). If no variant asset exists
 * the loader transparently falls back to the original landscape path. Variants are
 * cached independently so fold/unfold/rotation switches do not evict one another.
 *
 * Asset naming convention (drop-in; no JSON changes required):
 *   story/<scene_name>_p.jpg  — 720×1280 (9:16), vertical phone composition
 *   story/<scene_name>_s.jpg  — 1024×1024 (1:1), unfolded foldable square composition
 *   story/<scene_name>.jpg    — 1280×720 / 1376×768 (16:9), widescreen composition
 */
object StoryAssetLoader {

    private val bitmapCache = mutableMapOf<String, ImageBitmap?>()

    // ------------------------------------------------------------------
    // Background loading — aspect-aware
    // ------------------------------------------------------------------

    /**
     * Loads the background for [assetPath], preferring the `_p` or `_s` variant
     * matching [aspect] and falling back to [assetPath] when absent.
     */
    fun loadBackground(context: Context, assetPath: String, aspect: BackgroundAspect): ImageBitmap? {
        val primaryPath = BackgroundAspect.variantPath(assetPath, aspect)
        val fallbackPath = if (primaryPath != assetPath) assetPath else null

        if (bitmapCache.containsKey(primaryPath)) return bitmapCache[primaryPath]

        val primaryBitmap = tryLoadBitmap(context, primaryPath)
        if (primaryBitmap != null) {
            bitmapCache[primaryPath] = primaryBitmap
            return primaryBitmap
        }

        // Variant not found — mark as absent and use landscape fallback
        bitmapCache[primaryPath] = null
        if (fallbackPath == null) return null

        return loadBitmap(context, fallbackPath)
    }

    /**
     * Loads the background for [assetPath], preferring a `_p` portrait variant
     * when [isPortrait] is true. Falls back to the original path if absent.
     */
    fun loadBackground(context: Context, assetPath: String, isPortrait: Boolean): ImageBitmap? =
        loadBackground(context, assetPath, if (isPortrait) BackgroundAspect.PORTRAIT else BackgroundAspect.LANDSCAPE)

    // ------------------------------------------------------------------
    // Generic bitmap loading (character portraits, title backgrounds, etc.)
    // ------------------------------------------------------------------

    var activeSlot: Int = SaveManager.DEFAULT_SLOT

    /**
      * Loads any arbitrary asset path (portraits, title screens).
      * Not orientation-aware — use [loadBackground] for scene backgrounds.
      */
    fun loadBitmap(context: Context, assetPath: String, slot: Int? = null): ImageBitmap? {
        val targetSlot = slot ?: activeSlot
        val defaultHeroPortrait = GameContent.manifest.defaultHeroPortrait
        // The player's portrait (which a selfie portrait replaces) is whatever the content declares as
        // the hero's: the manifest default, a class portrait, or the hero character's own portrait.
        val heroCharacterPortrait = GameContent.heroCharacter?.speaker?.portraitAsset
        val isHeroPortrait = (defaultHeroPortrait != null && assetPath.equals(defaultHeroPortrait, ignoreCase = true)) ||
                (heroCharacterPortrait != null && assetPath.equals(heroCharacterPortrait, ignoreCase = true)) ||
                GameContent.classes.any { it.portraitAsset != null && assetPath.equals(it.portraitAsset, ignoreCase = true) } ||
                assetPath.contains("custom", ignoreCase = true)

        val cacheKey = if (isHeroPortrait) "${assetPath}_slot_${targetSlot}" else assetPath
        if (bitmapCache.containsKey(cacheKey)) return bitmapCache[cacheKey]
        val bitmap = tryLoadBitmap(context, assetPath, targetSlot, isHeroPortrait)
        bitmapCache[cacheKey] = bitmap
        return bitmap
    }

    /**
      * Loads raw asset bitmap directly from assets without intercepting for custom hero portraits.
      * Useful for character creation when showing the default fallback portrait.
      */
    fun loadRawAssetBitmap(context: Context, assetPath: String): ImageBitmap? = try {
        val cacheKey = "raw_$assetPath"
        if (bitmapCache.containsKey(cacheKey)) {
            bitmapCache[cacheKey]
        } else {
            val bitmap = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
            bitmapCache[cacheKey] = bitmap
            bitmap
        }
    } catch (_: Exception) {
        null
    }

    fun clear() {
        bitmapCache.clear()
    }

    fun clearSlot(slot: Int) {
        val suffix = "_slot_$slot"
        val keysToRemove = bitmapCache.keys.filter { it.endsWith(suffix) }
        keysToRemove.forEach { bitmapCache.remove(it) }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun tryLoadBitmap(
        context: Context,
        assetPath: String,
        slot: Int = activeSlot,
        isHeroPortrait: Boolean = false
    ): ImageBitmap? = try {
        val customPortraitFile = if (isHeroPortrait) {
            SelfiePortraitProcessor.getCustomPortraitFile(context, slot)
        } else null

        if (customPortraitFile != null && customPortraitFile.exists()) {
            BitmapFactory.decodeFile(customPortraitFile.absolutePath)?.asImageBitmap()
        } else {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }
    } catch (_: Exception) {
        null
    }
}
