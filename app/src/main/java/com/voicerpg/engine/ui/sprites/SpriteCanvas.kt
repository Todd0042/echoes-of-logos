package com.voicerpg.engine.ui.sprites

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Sheet pictures, decoded once (and recoloured once per variant). */
object SpriteBitmaps {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(context: Context, sprite: SheetSprite): ImageBitmap? = synchronized(cache) {
        val key = sprite.sheet.imagePath + "#" + sprite.recolor.entries.sortedBy { it.key }.joinToString()
        cache.getOrPut(key) { load(context, sprite) }
    }

    private fun load(context: Context, sprite: SheetSprite): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inScaled = false; inPremultiplied = true }
        val bitmap = context.assets.open(sprite.sheet.imagePath).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        if (sprite.recolor.isEmpty()) return bitmap.asImageBitmap()
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) sprite.recolor[px[i]]?.let { px[i] = it }
        android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()

    /** The pixels of [frame] of [sprite] down to its feet line (what shatters on death). */
    fun framePixels(context: Context, sprite: SheetSprite, frame: Int): PixelGrid {
        val image = get(context, sprite) ?: return PixelGrid.EMPTY
        val f = sprite.sheet.frames.getOrNull(frame) ?: return PixelGrid.EMPTY
        val w = sprite.cellW
        val h = sprite.feet.second.coerceIn(1, sprite.cellH)
        val map = image.toPixelMap(f.x, f.y, f.w, f.h)
        val cells = arrayOfNulls<Color>(w * h)
        for (y in 0 until f.h) for (x in 0 until f.w) {
            val cx = x + f.offX
            val cy = y + f.offY
            if (cx !in 0 until w || cy !in 0 until h) continue
            val c = map[x, y]
            if (c.alpha > 0.1f) cells[cy * w + cx] = c
        }
        return PixelGrid(w, h, cells)
    }
}

/** The death frame of a combatant's sprite as pixels, for the shatter. */
fun deathPixels(context: Context, source: SpriteSource): PixelGrid = when (source) {
    is SheetSprite -> SpriteBitmaps.framePixels(context, source, source.clip(AnimTag.DEATH).frames.last())
    is GridSprite -> PixelGrid.fromRows(source.rows(source.clip(AnimTag.DEATH).frames.last()), source.palette)
}

/**
 * Draws [frame] of [source] with its feet at the bottom centre of the canvas, so the layout that places this
 * canvas on the floor (BattleStage) places the feet. Sheets draw with nearest-neighbour filtering at
 * [artPx] device pixels per art pixel (a whole number keeps pixels crisp); legacy grids draw their cells.
 * Art faces right; [flipped] mirrors it (enemies). [flash] 0..1 turns it toward white (a hit).
 */
@Composable
fun SpriteCanvas(
    source: SpriteSource,
    frame: Int,
    artPx: Float,
    flipped: Boolean,
    flash: Float = 0f,
    hurtTint: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val (feetX, feetY) = source.feet
    val wPx = source.cellW * artPx
    val hPx = feetY.coerceAtLeast(1) * artPx
    val context = LocalContext.current
    val image = if (source is SheetSprite) remember(source.sheet.imagePath, source.recolor) { SpriteBitmaps.get(context, source) } else null
    val sizeDp = with(density) { Size(wPx, hPx).let { androidx.compose.ui.unit.DpSize(it.width.toDp(), it.height.toDp()) } }

    Canvas(modifier = modifier.size(sizeDp)) {
        // Centre the feet: shift the cell so its feet x sits at the canvas centre.
        val dx = (source.cellW / 2f - feetX) * artPx
        scale(scaleX = if (flipped) -1f else 1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            when (source) {
                is SheetSprite -> {
                    val f = source.sheet.frames.getOrNull(frame) ?: return@scale
                    val img = image ?: return@scale
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(f.x, f.y),
                        srcSize = IntSize(f.w, f.h),
                        dstOffset = IntOffset((f.offX * artPx + dx).roundToInt(), (f.offY * artPx).roundToInt()),
                        dstSize = IntSize((f.w * artPx).roundToInt(), (f.h * artPx).roundToInt()),
                        colorFilter = if (flash > 0f) ColorFilter.tint(Color.White.copy(alpha = flash.coerceIn(0f, 1f)), BlendMode.SrcAtop) else null,
                        filterQuality = FilterQuality.None
                    )
                }
                is GridSprite -> {
                    val rows = source.rows(frame)
                    val palette = source.palette
                    for (r in rows.indices) {
                        val line = rows[r]
                        for (c in line.indices) {
                            val ch = line[c]
                            if (ch == '.') continue
                            val color = palette[ch] ?: continue
                            val base = if (hurtTint && flash == 0f) Color(0xFFFF5252) else color
                            drawRect(
                                color = if (flash > 0f) androidx.compose.ui.graphics.lerp(base, Color.White, flash) else base,
                                topLeft = Offset(c * artPx + dx, r * artPx),
                                size = Size(artPx, artPx)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Device pixels per art pixel for a sheet: about 1.25 dp, rounded to a whole number, times the sprite's size. */
@Composable
fun sheetArtPx(sizeFactor: Float): Float {
    val base = with(LocalDensity.current) { 1.25.dp.toPx() }
    return (base * sizeFactor).roundToInt().coerceAtLeast(1).toFloat()
}
