package com.voicerpg.engine.ui.creation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.voicerpg.engine.model.CelShadingConfig
import com.voicerpg.engine.model.ColorGradeConfig
import com.voicerpg.engine.model.EyeEffectConfig
import com.voicerpg.engine.model.InkOutlineConfig
import com.voicerpg.engine.model.PixelArtConfig
import com.voicerpg.engine.model.ScanlineConfig
import com.voicerpg.engine.model.SelfieFilterConfig
import com.voicerpg.engine.model.TFLiteStylizeConfig
import com.voicerpg.engine.model.VignetteConfig
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.ui.story.StoryAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.min

/**
 * ActivityResultContract that requests the system camera to launch using the front-facing
 * (selfie) lens by default across device vendors.
 */
class TakeSelfiePreviewContract : ActivityResultContracts.TakePicturePreview() {
    override fun createIntent(context: Context, input: Void?): Intent {
        return super.createIntent(context, input).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 1) // 1 = CameraInfo.CAMERA_FACING_FRONT
            putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            putExtra("android.intent.extra.CAMERA_FACING", 1)
            putExtra("default_camera", "1")
            putExtra("camerafacing", "front")
            putExtra("previous_mode", "front")
        }
    }
}

/**
 * Generic image processor for custom hero portraits.
 * Operates on [SelfieFilterConfig] to apply game-defined backgrounds, face landmark shading,
 * color matrices, scanlines, and vignette effects.
 */
object SelfiePortraitProcessor {

    const val PORTRAIT_SIZE = 512
    const val CUSTOM_PORTRAIT_FILENAME = "custom_hero_portrait.jpg"

    fun loadFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                android.media.ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = android.graphics.Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
                else -> return raw
            }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun processSelfie(
        context: Context,
        inputBitmap: Bitmap,
        filterConfig: SelfieFilterConfig,
        selectedBgAsset: String? = null,
        applySegmentation: Boolean = true,
        applyPixelArt: Boolean = true,
        applyTFLite: Boolean = false,
        applyCelShading: Boolean = false,
        applyInkOutlines: Boolean = false,
        applyEyeEffect: Boolean = true,
        applyScanlines: Boolean = true,
        applyColorGrade: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Center crop and scale to square 512x512
        val squareInput = cropAndScale(inputBitmap, PORTRAIT_SIZE)

        // 2. AI Background Segmentation Mask
        val maskBuffer = if (applySegmentation && !selectedBgAsset.isNullOrBlank()) {
            runSegmentation(squareInput)
        } else null

        // 3. Optional Neural Anime Stylization (TFLite On-Device Model)
        val stylizedSubject = if (applyTFLite && filterConfig.tfliteStylization != null) {
            applyTFLiteStylization(context, squareInput, filterConfig.tfliteStylization) ?: squareInput
        } else {
            squareInput
        }

        // 4. Cel-Shading & Anime Surface Smoothing (Subject Only)
        val celShadedSubject = if (applyCelShading && filterConfig.celShading != null) {
            applyCelShading(stylizedSubject, maskBuffer, filterConfig.celShading)
        } else {
            stylizedSubject
        }

        // 5. Stylized Ink Outlines (Internal contours + silhouette edge)
        val inkedSubject = if (applyInkOutlines && filterConfig.inkOutlines != null) {
            applyInkOutlines(celShadedSubject, maskBuffer, filterConfig.inkOutlines)
        } else {
            celShadedSubject
        }

        // 6. Background Composite (if background selected)
        val composited = if (maskBuffer != null && !selectedBgAsset.isNullOrBlank()) {
            val bgBitmap = loadAssetBitmap(context, selectedBgAsset, PORTRAIT_SIZE)
            compositeWithBackground(inkedSubject, bgBitmap, maskBuffer)
        } else {
            inkedSubject
        }

        // 7. Eye Orbit Shading (Arcane fatigue in Wally's or Logos resonance glow in Echoes)
        val withEyeEffect = if (applyEyeEffect && filterConfig.eyeEffect != null) {
            applyEyeShading(composited, filterConfig.eyeEffect)
        } else {
            composited
        }

        // 8. Retro JRPG Pixel-Art Pipeline (Kuwahara + 144px grid + palette quantization + 1px inking)
        val pixelArtOutput = if (applyPixelArt && filterConfig.pixelArt != null) {
            applyPixelArtPipeline(withEyeEffect, filterConfig.pixelArt)
        } else {
            withEyeEffect
        }

        // 9. Frame Border Overlay (e.g. ornate gold fantasy frame in Echoes or CRT bezel in Wally's)
        val framedResult = if (!filterConfig.frameBorderAsset.isNullOrBlank()) {
            overlayFrameBorder(context, pixelArtOutput, filterConfig.frameBorderAsset)
        } else {
            pixelArtOutput
        }

        // 10. Color Grading, Scanlines, and Vignette
        val finalResult = applyPostProcessing(
            framedResult,
            applyScanlines = applyScanlines,
            scanlineConfig = filterConfig.scanlines,
            applyColorGrade = applyColorGrade,
            colorGradeConfig = filterConfig.colorGrade,
            vignetteConfig = filterConfig.vignette
        )

        finalResult
    }

    fun getCustomPortraitFilename(slot: Int): String {
        return if (slot == SaveManager.STORY_MODE_SLOT) {
            "custom_hero_portrait_story.jpg"
        } else {
            "custom_hero_portrait_slot_$slot.jpg"
        }
    }

    fun saveCustomPortrait(context: Context, bitmap: Bitmap, slot: Int = SaveManager.DEFAULT_SLOT): File {
        val filename = getCustomPortraitFilename(slot)
        val destFile = File(context.filesDir, filename)
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        StoryAssetLoader.clearSlot(slot)
        return destFile
    }

    fun getCustomPortraitFile(context: Context, slot: Int = SaveManager.DEFAULT_SLOT): File? {
        val filename = getCustomPortraitFilename(slot)
        val f = File(context.filesDir, filename)
        if (f.exists()) return f

        // Backward-compatibility: if slot is DEFAULT_SLOT (1), check legacy single-file name
        if (slot == SaveManager.DEFAULT_SLOT) {
            val legacy = File(context.filesDir, CUSTOM_PORTRAIT_FILENAME)
            if (legacy.exists()) {
                try {
                    legacy.copyTo(f, overwrite = true)
                    legacy.delete()
                    if (f.exists()) return f
                } catch (_: Exception) {
                    return legacy
                }
            }
        }
        return null
    }

    fun deleteCustomPortrait(context: Context, slot: Int = SaveManager.DEFAULT_SLOT): Boolean {
        val f = getCustomPortraitFile(context, slot)
        StoryAssetLoader.clearSlot(slot)
        return f?.delete() ?: false
    }

    private fun cropAndScale(src: Bitmap, targetSize: Int): Bitmap {
        val minDim = min(src.width, src.height)
        val xOffset = (src.width - minDim) / 2
        val yOffset = (src.height - minDim) / 2
        val cropped = Bitmap.createBitmap(src, xOffset, yOffset, minDim, minDim)
        return if (cropped.width != targetSize || cropped.height != targetSize) {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        } else {
            cropped
        }
    }

    private fun loadAssetBitmap(context: Context, assetPath: String, targetSize: Int): Bitmap? = try {
        context.assets.open(assetPath).use { stream ->
            val raw = BitmapFactory.decodeStream(stream)
            if (raw != null) cropAndScale(raw, targetSize) else null
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun runSegmentation(bitmap: Bitmap): ByteBuffer? =
        suspendCancellableCoroutine { cont ->
            try {
                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
                val segmenter = Segmentation.getClient(options)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                segmenter.process(inputImage)
                    .addOnSuccessListener { mask ->
                        cont.resume(mask.buffer)
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

    private fun compositeWithBackground(
        subject: Bitmap,
        background: Bitmap?,
        maskBuffer: ByteBuffer?
    ): Bitmap {
        if (background == null || maskBuffer == null) return subject

        val w = subject.width
        val h = subject.height
        val totalPixels = w * h

        val subjectPixels = IntArray(totalPixels)
        val bgPixels = IntArray(totalPixels)
        val outPixels = IntArray(totalPixels)

        subject.getPixels(subjectPixels, 0, w, 0, 0, w, h)
        background.getPixels(bgPixels, 0, w, 0, 0, w, h)

        maskBuffer.rewind()

        for (i in 0 until totalPixels) {
            val confidence = if (maskBuffer.hasRemaining()) maskBuffer.float else 1f
            val alpha = confidence.coerceIn(0f, 1f)

            val sCol = subjectPixels[i]
            val bCol = bgPixels[i]

            val r = (Color.red(sCol) * alpha + Color.red(bCol) * (1f - alpha)).toInt()
            val g = (Color.green(sCol) * alpha + Color.green(bCol) * (1f - alpha)).toInt()
            val b = (Color.blue(sCol) * alpha + Color.blue(bCol) * (1f - alpha)).toInt()

            outPixels[i] = Color.rgb(r, g, b)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private suspend fun applyEyeShading(source: Bitmap, eyeConfig: EyeEffectConfig): Bitmap =
        suspendCancellableCoroutine { cont ->
            try {
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build()
                val detector = FaceDetection.getClient(options)
                val inputImage = InputImage.fromBitmap(source, 0)

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        if (faces.isEmpty()) {
                            cont.resume(source)
                            return@addOnSuccessListener
                        }

                        val result = source.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(result)

                        val darkCenterColor = parseColor(eyeConfig.darkCenterColorHex, Color.argb(135, 45, 25, 42))
                        val transparentColor = Color.argb(0, Color.red(darkCenterColor), Color.green(darkCenterColor), Color.blue(darkCenterColor))
                        val radiusFactor = eyeConfig.radiusFactor.coerceIn(0.1f, 1.0f)
                        val yOffsetFactor = eyeConfig.yOffsetFactor.coerceIn(0.1f, 1.5f)

                        for (face in faces) {
                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

                            if (leftEye != null && rightEye != null) {
                                val dx = rightEye.x - leftEye.x
                                val dy = rightEye.y - leftEye.y
                                val eyeDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                val radius = eyeDist * radiusFactor

                                // Left eye shading
                                drawEyeShade(canvas, leftEye.x, leftEye.y + radius * yOffsetFactor, radius, darkCenterColor, transparentColor)

                                // Right eye shading
                                drawEyeShade(canvas, rightEye.x, rightEye.y + radius * yOffsetFactor, radius, darkCenterColor, transparentColor)
                            }
                        }
                        cont.resume(result)
                    }
                    .addOnFailureListener {
                        cont.resume(source)
                    }
            } catch (_: Exception) {
                cont.resume(source)
            }
        }

    private fun drawEyeShade(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        centerColor: Int,
        edgeColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                centerColor, edgeColor,
                Shader.TileMode.CLAMP
            )
        }
        val oval = RectF(
            cx - radius * 1.15f,
            cy - radius * 0.55f,
            cx + radius * 1.15f,
            cy + radius * 0.75f
        )
        canvas.drawOval(oval, paint)
    }

    private fun applyPostProcessing(
        source: Bitmap,
        applyScanlines: Boolean,
        scanlineConfig: ScanlineConfig?,
        applyColorGrade: Boolean,
        colorGradeConfig: ColorGradeConfig?,
        vignetteConfig: VignetteConfig?
    ): Bitmap {
        val w = source.width
        val h = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 1. Color Grading Matrix
        if (applyColorGrade && colorGradeConfig != null && colorGradeConfig.colorMatrix.size >= 20) {
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val matrix = ColorMatrix(colorGradeConfig.colorMatrix.toFloatArray())
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(source, 0f, 0f, filterPaint)
        }

        // 2. Scanlines
        if (applyScanlines && scanlineConfig != null) {
            val alpha = scanlineConfig.lineAlpha.coerceIn(0, 255)
            val scanlinePaint = Paint().apply {
                color = Color.argb(alpha, 0, 0, 0)
                strokeWidth = scanlineConfig.strokeWidth
            }
            var y = 0f
            val spacing = if (scanlineConfig.lineSpacing > 0.5f) scanlineConfig.lineSpacing else 3.8f
            while (y < h) {
                canvas.drawLine(0f, y, w.toFloat(), y, scanlinePaint)
                y += spacing
            }
        }

        // 3. Vignette
        if (vignetteConfig?.enabled == true) {
            val vigColor = parseColor(vignetteConfig.colorHex, Color.argb(85, 10, 12, 18))
            val radius = w * (if (vignetteConfig.radiusFactor > 0.1f) vignetteConfig.radiusFactor else 0.7f)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, radius,
                    Color.TRANSPARENT,
                    vigColor,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        }

        return result
    }

    private fun applyCelShading(
        source: Bitmap,
        maskBuffer: ByteBuffer?,
        config: CelShadingConfig
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val srcPixels = IntArray(total)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(total)

        // Read mask alpha into a float array (0f..1f)
        val maskAlphas = FloatArray(total)
        if (maskBuffer != null) {
            maskBuffer.rewind()
            for (i in 0 until total) {
                maskAlphas[i] = if (maskBuffer.hasRemaining()) maskBuffer.float.coerceIn(0f, 1f) else 1f
            }
        } else {
            maskAlphas.fill(1f)
        }

        // Pass 1: Edge-preserving skin smoothing (separable bilateral approximation)
        val smoothed = IntArray(total)
        val radius = config.smoothRadius.coerceIn(1, 4)
        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val idx = yOffset + x
                val centerPix = srcPixels[idx]
                val centerLum = (299 * Color.red(centerPix) + 587 * Color.green(centerPix) + 114 * Color.blue(centerPix)) / 1000

                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0

                val minY = (y - radius).coerceAtLeast(0)
                val maxY = (y + radius).coerceAtMost(h - 1)
                val minX = (x - radius).coerceAtLeast(0)
                val maxX = (x + radius).coerceAtMost(w - 1)

                for (ny in minY..maxY) {
                    val nYOffset = ny * w
                    for (nx in minX..maxX) {
                        val nIdx = nYOffset + nx
                        val nPix = srcPixels[nIdx]
                        val nLum = (299 * Color.red(nPix) + 587 * Color.green(nPix) + 114 * Color.blue(nPix)) / 1000
                        if (kotlin.math.abs(centerLum - nLum) < 28) {
                            sumR += Color.red(nPix)
                            sumG += Color.green(nPix)
                            sumB += Color.blue(nPix)
                            count++
                        }
                    }
                }

                if (count > 0) {
                    smoothed[idx] = Color.rgb(sumR / count, sumG / count, sumB / count)
                } else {
                    smoothed[idx] = centerPix
                }
            }
        }

        // Pass 2: Perceptual luminance quantization & JRPG palette shifting
        val bands = config.bands.coerceIn(2, 6)
        val cooling = config.shadowCoolingFactor.coerceIn(0f, 0.4f)

        for (i in 0 until total) {
            val maskAlpha = maskAlphas[i]
            val origPix = srcPixels[i]
            if (maskAlpha < 0.05f) {
                outPixels[i] = origPix
                continue
            }

            val sPix = smoothed[i]
            val r = Color.red(sPix)
            val g = Color.green(sPix)
            val b = Color.blue(sPix)
            val lum = (299 * r + 587 * g + 114 * b) / 1000

            val bandIdx = (lum * bands / 256).coerceIn(0, bands - 1)
            val qLum = ((bandIdx + 0.5f) * (255f / bands)).coerceIn(0f, 255f)
            val lumFactor = if (lum > 0) (qLum / lum.toFloat()).coerceIn(0.5f, 1.8f) else 1f

            var qr = (r * lumFactor).toInt().coerceIn(0, 255)
            var qg = (g * lumFactor).toInt().coerceIn(0, 255)
            var qb = (b * lumFactor).toInt().coerceIn(0, 255)

            // JRPG palette shift (warm highlights, cool shadows)
            if (qLum < 120f) {
                val shadowRatio = (120f - qLum) / 120f
                val blueShift = (cooling * shadowRatio * 32).toInt()
                val redReduce = (cooling * shadowRatio * 16).toInt()
                qb = (qb + blueShift).coerceAtMost(255)
                qr = (qr - redReduce).coerceAtLeast(0)
            } else if (qLum > 180f) {
                val highlightRatio = (qLum - 180f) / 75f
                val warmShift = (highlightRatio * 12).toInt()
                qr = (qr + warmShift).coerceAtMost(255)
                qg = (qg + (warmShift / 2)).coerceAtMost(255)
            }

            val finalR = (qr * maskAlpha + Color.red(origPix) * (1f - maskAlpha)).toInt()
            val finalG = (qg * maskAlpha + Color.green(origPix) * (1f - maskAlpha)).toInt()
            val finalB = (qb * maskAlpha + Color.blue(origPix) * (1f - maskAlpha)).toInt()

            outPixels[i] = Color.rgb(finalR, finalG, finalB)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyInkOutlines(
        source: Bitmap,
        maskBuffer: ByteBuffer?,
        config: InkOutlineConfig
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val srcPixels = IntArray(total)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(total)

        // Read mask alpha into a float array (0f..1f)
        val maskAlphas = FloatArray(total)
        if (maskBuffer != null) {
            maskBuffer.rewind()
            for (i in 0 until total) {
                maskAlphas[i] = if (maskBuffer.hasRemaining()) maskBuffer.float.coerceIn(0f, 1f) else 1f
            }
        } else {
            maskAlphas.fill(1f)
        }

        // Luminance buffer
        val lums = IntArray(total)
        for (i in 0 until total) {
            val c = srcPixels[i]
            lums[i] = (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000
        }

        val inkColor = parseColor(config.inkColorHex, Color.rgb(24, 20, 42))
        val inkR = Color.red(inkColor)
        val inkG = Color.green(inkColor)
        val inkB = Color.blue(inkColor)
        val sensitivity = config.sensitivity.coerceIn(0.5f, 2.0f)
        val tLow = (55f / sensitivity)
        val tHigh = (130f / sensitivity)

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val idx = yOffset + x
                val origPix = srcPixels[idx]
                val maskAlpha = maskAlphas[idx]

                if (maskAlpha < 0.05f || x == 0 || x == w - 1 || y == 0 || y == h - 1) {
                    outPixels[idx] = origPix
                    continue
                }

                // 3x3 Sobel on luminance
                val p00 = lums[(y - 1) * w + (x - 1)]
                val p01 = lums[(y - 1) * w + x]
                val p02 = lums[(y - 1) * w + (x + 1)]
                val p10 = lums[yOffset + (x - 1)]
                val p12 = lums[yOffset + (x + 1)]
                val p20 = lums[(y + 1) * w + (x - 1)]
                val p21 = lums[(y + 1) * w + x]
                val p22 = lums[(y + 1) * w + (x + 1)]

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                val mag = kotlin.math.abs(gx) + kotlin.math.abs(gy)

                // Silhouette contour edge detection using mask gradient
                var silhouetteFactor = 0f
                if (config.outlineSilhouette && maskBuffer != null) {
                    val m00 = maskAlphas[(y - 1) * w + (x - 1)]
                    val m02 = maskAlphas[(y - 1) * w + (x + 1)]
                    val m10 = maskAlphas[yOffset + (x - 1)]
                    val m12 = maskAlphas[yOffset + (x + 1)]
                    val m20 = maskAlphas[(y + 1) * w + (x - 1)]
                    val m22 = maskAlphas[(y + 1) * w + (x + 1)]
                    val mgx = (m02 + 2 * m12 + m22) - (m00 + 2 * m10 + m20)
                    val mgy = (m20 + 2 * maskAlphas[(y + 1) * w + x] + m22) - (m00 + 2 * maskAlphas[(y - 1) * w + x] + m02)
                    val maskMag = kotlin.math.abs(mgx) + kotlin.math.abs(mgy)
                    silhouetteFactor = (maskMag * 1.5f).coerceIn(0f, 1f)
                }

                val contourFactor = if (mag > tLow) {
                    ((mag - tLow) / (tHigh - tLow)).coerceIn(0f, 1f)
                } else 0f

                val totalInkFactor = kotlin.math.max(contourFactor * 0.85f, silhouetteFactor * 0.95f) * maskAlpha

                if (totalInkFactor > 0.02f) {
                    val r = (inkR * totalInkFactor + Color.red(origPix) * (1f - totalInkFactor)).toInt()
                    val g = (inkG * totalInkFactor + Color.green(origPix) * (1f - totalInkFactor)).toInt()
                    val b = (inkB * totalInkFactor + Color.blue(origPix) * (1f - totalInkFactor)).toInt()
                    outPixels[idx] = Color.rgb(r, g, b)
                } else {
                    outPixels[idx] = origPix
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyPixelArtPipeline(
        source: Bitmap,
        config: PixelArtConfig
    ): Bitmap {
        val targetDim = config.gridResolution.coerceIn(96, 256)
        // 1. Bilinear downsample to retro grid size (e.g. 144x144)
        val small = Bitmap.createScaledBitmap(source, targetDim, targetDim, true)
        val w = small.width
        val h = small.height
        val total = w * h

        val srcPixels = IntArray(total)
        small.getPixels(srcPixels, 0, w, 0, 0, w, h)

        // 2. Fast Kuwahara Filter at retro grid size (flattens skin noise, preserves silhouette edges)
        val r = config.kuwaharaRadius.coerceIn(1, 4)
        val smoothed = applyKuwaharaFilter(srcPixels, w, h, r)

        // 3. Palette Quantization with 2x2 Bayer Dithering & JRPG Warm Highlight / Cool Shadow Shifting
        val bands = config.paletteBands.coerceIn(2, 8)
        val ditherScale = config.ditherStrength.coerceIn(0f, 0.25f) * 255f
        val shadowCooling = config.shadowCoolingFactor.coerceIn(0f, 0.4f)
        val bayer2x2 = floatArrayOf(
            -0.5f, 0.0f,
            0.25f, -0.25f
        )

        val lums = FloatArray(total)
        val quantized = IntArray(total)

        for (y in 0 until h) {
            val yOff = y * w
            val bayerY = (y % 2) * 2
            for (x in 0 until w) {
                val idx = yOff + x
                val pix = smoothed[idx]
                val cr = Color.red(pix)
                val cg = Color.green(pix)
                val cb = Color.blue(pix)
                val lum = (299 * cr + 587 * cg + 114 * cb) / 1000f
                lums[idx] = lum

                val dither = bayer2x2[bayerY + (x % 2)] * ditherScale
                val dLum = (lum + dither).coerceIn(0f, 255f)
                val band = (dLum * bands / 256f).toInt().coerceIn(0, bands - 1)
                val qLum = ((band + 0.5f) * (255f / bands)).coerceIn(0f, 255f)
                val ratio = if (lum > 1f) (qLum / lum).coerceIn(0.4f, 2.2f) else 1f

                var qr = (cr * ratio).toInt().coerceIn(0, 255)
                var qg = (cg * ratio).toInt().coerceIn(0, 255)
                var qb = (cb * ratio).toInt().coerceIn(0, 255)

                // JRPG tonal warmth shift
                if (qLum < 120f) {
                    val shadowRatio = (120f - qLum) / 120f
                    val blueShift = (shadowCooling * shadowRatio * 32).toInt()
                    val redReduce = (shadowCooling * shadowRatio * 16).toInt()
                    qb = (qb + blueShift).coerceAtMost(255)
                    qr = (qr - redReduce).coerceAtLeast(0)
                } else if (qLum > 180f) {
                    val highlightRatio = (qLum - 180f) / 75f
                    val warmShift = (highlightRatio * 12).toInt()
                    qr = (qr + warmShift).coerceAtMost(255)
                    qg = (qg + (warmShift / 2)).coerceAtMost(255)
                }

                quantized[idx] = Color.rgb(qr, qg, qb)
            }
        }

        // 4. 1px Pixel-Art Edge Inking (Sobel in grid space)
        val inkColor = parseColor(config.outlineColorHex, Color.rgb(30, 26, 45))
        val inkR = Color.red(inkColor)
        val inkG = Color.green(inkColor)
        val inkB = Color.blue(inkColor)
        val sensitivity = config.outlineSensitivity.coerceIn(0.5f, 2.0f)
        val tLow = 50f / sensitivity
        val tHigh = 120f / sensitivity

        val outPixels = IntArray(total)

        for (y in 0 until h) {
            val yOff = y * w
            for (x in 0 until w) {
                val idx = yOff + x
                val origPix = quantized[idx]

                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) {
                    outPixels[idx] = origPix
                    continue
                }

                val p00 = lums[(y - 1) * w + (x - 1)]
                val p01 = lums[(y - 1) * w + x]
                val p02 = lums[(y - 1) * w + (x + 1)]
                val p10 = lums[yOff + (x - 1)]
                val p12 = lums[yOff + (x + 1)]
                val p20 = lums[(y + 1) * w + (x - 1)]
                val p21 = lums[(y + 1) * w + x]
                val p22 = lums[(y + 1) * w + (x + 1)]

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                val mag = kotlin.math.abs(gx) + kotlin.math.abs(gy)

                if (mag > tLow) {
                    val inkFactor = ((mag - tLow) / (tHigh - tLow)).coerceIn(0f, 1f) * 0.85f
                    val r = (inkR * inkFactor + Color.red(origPix) * (1f - inkFactor)).toInt()
                    val g = (inkG * inkFactor + Color.green(origPix) * (1f - inkFactor)).toInt()
                    val b = (inkB * inkFactor + Color.blue(origPix) * (1f - inkFactor)).toInt()
                    outPixels[idx] = Color.rgb(r, g, b)
                } else {
                    outPixels[idx] = origPix
                }
            }
        }

        val retroSmall = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        retroSmall.setPixels(outPixels, 0, w, 0, 0, w, h)

        // 5. Point-sample (Nearest-Neighbor) 4x upscale to PORTRAIT_SIZE (512x512)
        val upscaled = Bitmap.createBitmap(PORTRAIT_SIZE, PORTRAIT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(upscaled)
        val paint = Paint().apply {
            isFilterBitmap = false // Crucial for crisp, sharp retro pixel blocks!
        }
        val destRect = RectF(0f, 0f, PORTRAIT_SIZE.toFloat(), PORTRAIT_SIZE.toFloat())
        canvas.drawBitmap(retroSmall, null, destRect, paint)
        return upscaled
    }

    private fun applyKuwaharaFilter(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val out = IntArray(w * h)
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            lum[i] = (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000f
        }

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                if (x < radius || x >= w - radius || y < radius || y >= h - radius) {
                    out[yOffset + x] = pixels[yOffset + x]
                    continue
                }

                var minVar = Float.MAX_VALUE
                var bestR = 0
                var bestG = 0
                var bestB = 0

                for (q in 0..3) {
                    val xStart = if (q == 0 || q == 2) x - radius else x
                    val xEnd = if (q == 0 || q == 2) x else x + radius
                    val yStart = if (q == 0 || q == 1) y - radius else y
                    val yEnd = if (q == 0 || q == 1) y else y + radius

                    var sumLum = 0f
                    var sumLumSq = 0f
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0

                    for (qy in yStart..yEnd) {
                        val qyOff = qy * w
                        for (qx in xStart..xEnd) {
                            val l = lum[qyOff + qx]
                            val p = pixels[qyOff + qx]
                            sumLum += l
                            sumLumSq += l * l
                            sumR += Color.red(p)
                            sumG += Color.green(p)
                            sumB += Color.blue(p)
                            count++
                        }
                    }

                    val meanLum = sumLum / count
                    val variance = (sumLumSq / count) - (meanLum * meanLum)

                    if (variance < minVar) {
                        minVar = variance
                        bestR = sumR / count
                        bestG = sumG / count
                        bestB = sumB / count
                    }
                }

                out[yOffset + x] = Color.rgb(bestR, bestG, bestB)
            }
        }
        return out
    }

    private fun overlayFrameBorder(
        context: Context,
        portrait: Bitmap,
        frameBorderAsset: String
    ): Bitmap {
        return try {
            val frameBitmap = loadAssetBitmap(context, frameBorderAsset, PORTRAIT_SIZE) ?: return portrait
            val result = Bitmap.createBitmap(PORTRAIT_SIZE, PORTRAIT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(portrait, 0f, 0f, null)
            val framePaint = Paint().apply {
                isFilterBitmap = false // Preserve sharp pixel edges of the decorative frame
            }
            canvas.drawBitmap(frameBitmap, 0f, 0f, framePaint)
            result
        } catch (_: Exception) {
            portrait
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd(modelPath)
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                fileDescriptor.close()
                buffer
            }
        } catch (_: Exception) {
            context.assets.open(modelPath).use { stream ->
                val bytes = stream.readBytes()
                val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
                buffer
            }
        }
    }

    private fun applyTFLiteStylization(
        context: Context,
        source: Bitmap,
        config: TFLiteStylizeConfig
    ): Bitmap? {
        var interpreter: Interpreter? = null
        return try {
            val modelBuffer = loadModelFile(context, config.modelAssetPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            val inputSize = config.inputSize.coerceAtLeast(128)

            // Scale source to inputSize x inputSize for the neural network
            val scaledInput = if (source.width == inputSize && source.height == inputSize) {
                source
            } else {
                Bitmap.createScaledBitmap(source, inputSize, inputSize, true)
            }

            // Allocate direct byte buffers for input and output: 1 * H * W * 3 * 4 (float32)
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val pixels = IntArray(inputSize * inputSize)
            scaledInput.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            // UGATIT model expects input in [-1.0f, +1.0f] range
            for (pixel in pixels) {
                inputBuffer.putFloat((Color.red(pixel) / 127.5f) - 1.0f)
                inputBuffer.putFloat((Color.green(pixel) / 127.5f) - 1.0f)
                inputBuffer.putFloat((Color.blue(pixel) / 127.5f) - 1.0f)
            }

            val outputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter.run(inputBuffer, outputBuffer)

            // UGATIT generator ends with Tanh, producing outputs in [-1.0f, +1.0f] range.
            // Denormalize: pixel = (tanh_val + 1.0f) * 127.5f -> [0..255]
            outputBuffer.rewind()
            val outPixels = IntArray(inputSize * inputSize)
            for (i in 0 until inputSize * inputSize) {
                val r = ((outputBuffer.float + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                val g = ((outputBuffer.float + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                val b = ((outputBuffer.float + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                outPixels[i] = Color.rgb(r, g, b)
            }

            val animeBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            animeBitmap.setPixels(outPixels, 0, inputSize, 0, 0, inputSize, inputSize)

            // Upscale back to portrait target size (512x512)
            if (animeBitmap.width != PORTRAIT_SIZE || animeBitmap.height != PORTRAIT_SIZE) {
                Bitmap.createScaledBitmap(animeBitmap, PORTRAIT_SIZE, PORTRAIT_SIZE, true)
            } else {
                animeBitmap
            }
        } catch (e: Exception) {
            android.util.Log.e("SelfiePortraitProcessor", "TFLite anime stylization failed", e)
            null
        } finally {
            try {
                interpreter?.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseColor(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return runCatching {
            val clean = hex.removePrefix("#").trim()
            val value = clean.toLong(16)
            when (clean.length) {
                6 -> (0xFF000000L or value).toInt()
                8 -> value.toInt()
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}
