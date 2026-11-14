package com.voicerpg.engine.ui.story

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.ui.creation.SelfiePortraitProcessor

/**
 * Loads and caches story scene backgrounds and character portraits from assets.
 *
 * Background loading is orientation-aware: when [loadBackground] is called with
 * `isPortrait = true` it first resolves a portrait-variant path by inserting `_p`
 * before the file extension (e.g. `story/squeeze_title_p.jpg`).  If no portrait
 * variant asset exists the loader transparently falls back to the original landscape
 * path.  Both variants are cached independently so landscape↔portrait switches do
 * not evict one another from memory.
 *
 * Portrait asset naming convention (drop-in; no JSON changes required):
 *   story/<scene_name>_p.jpg  — 720×1280 (9:16), vertical composition
 *   story/<scene_name>.jpg    — 1280×720 (16:9), horizontal composition (original)
 */
object StoryAssetLoader {

    private val bitmapCache = mutableMapOf<String, ImageBitmap?>()

    // ------------------------------------------------------------------
    // Background loading — orientation-aware
    // ------------------------------------------------------------------

    /**
     * Loads the background for [assetPath], preferring a `_p` portrait variant
     * when [isPortrait] is true.  Falls back to the original path if the portrait
     * variant is not present in the APK assets.
     */
    fun loadBackground(context: Context, assetPath: String, isPortrait: Boolean): ImageBitmap? {
        val resolvedPath = if (isPortrait) portraitVariantPath(assetPath) else assetPath
        // If portrait variant path == landscape path (already has _p or blank) skip
        val primaryPath = resolvedPath
        val fallbackPath = if (resolvedPath != assetPath) assetPath else null

        // Check cache for primary first
        if (bitmapCache.containsKey(primaryPath)) return bitmapCache[primaryPath]

        // Try primary (portrait variant)
        val primaryBitmap = tryLoadBitmap(context, primaryPath)
        if (primaryBitmap != null) {
            bitmapCache[primaryPath] = primaryBitmap
            return primaryBitmap
        }

        // Portrait variant not found — mark as absent and use landscape fallback
        bitmapCache[primaryPath] = null  // cache the miss so we don't re-attempt
        if (fallbackPath == null) return null

        return loadBitmap(context, fallbackPath)
    }

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
        val isHeroPortrait = (defaultHeroPortrait != null && assetPath.equals(defaultHeroPortrait, ignoreCase = true)) ||
                GameContent.classes.any { it.portraitAsset != null && assetPath.equals(it.portraitAsset, ignoreCase = true) } ||
                assetPath.contains("custom", ignoreCase = true) ||
                assetPath.contains("hero", ignoreCase = true) ||
                assetPath.contains("applicant", ignoreCase = true)

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

    /**
     * Inserts `_p` before the final `.` in an asset path.
     * `story/squeeze_title.jpg` → `story/squeeze_title_p.jpg`
     * Returns the original path unchanged if no `.` is found.
     */
    private fun portraitVariantPath(assetPath: String): String {
        val dotIdx = assetPath.lastIndexOf('.')
        return if (dotIdx < 0) assetPath
        else assetPath.substring(0, dotIdx) + "_p" + assetPath.substring(dotIdx)
    }

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
