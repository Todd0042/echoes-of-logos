package com.voicerpg.engine.ui.creation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.voicerpg.engine.model.SelfieFilterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Feature profile extracted from the user's selfie.
 */
data class AvatarProfile(
    val skinToneIndex: Int = 1,        // 0: Fair, 1: Peach (Aethel), 2: Olive, 3: Tan, 4: Deep Brown
    val eyeColorIndex: Int = 0,        // 0: Sky Blue, 1: Chestnut Brown, 2: Forest Emerald, 3: Golden Hazel, 4: Slate Grey
    val faceShapeIndex: Int = 0,       // 0: Slender V-Chin, 1: Square Heroic, 2: Soft Rounded
    val hairStyleIndex: Int = 0,       // 0: Short Spiky, 1: Medium Parted, 2: Long Braids
    val hairColorIndex: Int = 0,       // 0: Silver, 1: Blonde, 2: Brown, 3: Black, 4: Auburn
    val attireIndex: Int = 0,          // 0: Mage Robes, 1: Paladin Plate, 2: Scout Mantle
    val facialHairIndex: Int = 0,      // 0: Clean Shaven, 1: 5 O'Clock Stubble, 2: Goatee & Mustache, 3: Full Beard
    val hasGlasses: Boolean = false,
    val hasStubble: Boolean = false
)

/**
 * Parametric JRPG Avatar Synthesizer.
 * Analyzes on-device selfie features (skin tone, eye color, face shape, hair color, hair length, glasses, facial hair)
 * and assembles an authentic, hand-crafted 16-bit JRPG hero portrait matching Aethel and companions.
 */
object AvatarSynthesizer {

    val SKIN_LABELS = listOf("Fair Alabaster", "Warm Peach", "Olive Neutral", "Golden Tan", "Deep Espresso")
    val EYE_COLOR_LABELS = listOf("Logos Sky Blue", "Chestnut Brown", "Forest Emerald", "Golden Hazel", "Slate Grey")
    val FACE_SHAPE_LABELS = listOf("Slender V-Chin", "Square Heroic", "Soft Rounded")
    val HAIR_STYLE_LABELS = listOf("Short Spiky", "Medium Parted", "Long Braids")
    val HAIR_COLOR_LABELS = listOf("Silver / Grey", "Golden Blonde", "Chestnut Brown", "Raven Black", "Fiery Auburn")
    val FACIAL_HAIR_LABELS = listOf("Clean Shaven", "5 O'Clock Stubble", "Goatee & Mustache", "Full Beard")
    val ATTIRE_LABELS = listOf("Solaria Mage Robe", "Paladin Gold Plate", "Forest Scout Mantle")

    val SKIN_PALETTES = listOf(
        // 0: Fair Alabaster
        intArrayOf(Color.rgb(253, 240, 230), Color.rgb(252, 224, 207), Color.rgb(232, 180, 158), Color.rgb(160, 100, 80)),
        // 1: Warm Peach (Aethel)
        intArrayOf(Color.rgb(251, 230, 212), Color.rgb(243, 186, 147), Color.rgb(212, 124, 86), Color.rgb(142, 70, 51)),
        // 2: Olive / Neutral
        intArrayOf(Color.rgb(238, 216, 194), Color.rgb(216, 182, 147), Color.rgb(176, 138, 98), Color.rgb(104, 72, 42)),
        // 3: Golden Tan
        intArrayOf(Color.rgb(232, 184, 144), Color.rgb(200, 140, 94), Color.rgb(156, 94, 50), Color.rgb(84, 42, 18)),
        // 4: Deep Espresso
        intArrayOf(Color.rgb(168, 120, 90), Color.rgb(122, 76, 50), Color.rgb(78, 42, 24), Color.rgb(38, 16, 6))
    )

    val EYE_PALETTES = listOf(
        // 0: Sky Blue (Logos Default)
        intArrayOf(Color.rgb(120, 200, 255), Color.rgb(50, 140, 225), Color.rgb(20, 80, 165), Color.rgb(10, 40, 95)),
        // 1: Chestnut Brown
        intArrayOf(Color.rgb(210, 140, 80), Color.rgb(160, 95, 45), Color.rgb(105, 55, 25), Color.rgb(60, 30, 15)),
        // 2: Forest Emerald
        intArrayOf(Color.rgb(120, 210, 110), Color.rgb(70, 160, 65), Color.rgb(40, 110, 40), Color.rgb(20, 65, 25)),
        // 3: Golden Hazel
        intArrayOf(Color.rgb(235, 185, 90), Color.rgb(185, 135, 55), Color.rgb(125, 85, 30), Color.rgb(75, 45, 15)),
        // 4: Slate Grey
        intArrayOf(Color.rgb(200, 210, 220), Color.rgb(150, 160, 175), Color.rgb(100, 110, 125), Color.rgb(55, 62, 75))
    )

    val HAIR_PALETTES = listOf(
        // 0: Silver / Grey (Aethel default)
        intArrayOf(Color.rgb(255, 255, 255), Color.rgb(187, 210, 232), Color.rgb(124, 156, 187), Color.rgb(74, 101, 129)),
        // 1: Golden Blonde (Cedric)
        intArrayOf(Color.rgb(255, 245, 180), Color.rgb(225, 195, 100), Color.rgb(170, 130, 50), Color.rgb(90, 65, 25)),
        // 2: Chestnut Brown (Lyra)
        intArrayOf(Color.rgb(210, 160, 120), Color.rgb(155, 95, 55), Color.rgb(100, 55, 30), Color.rgb(50, 25, 15)),
        // 3: Raven Black
        intArrayOf(Color.rgb(110, 115, 130), Color.rgb(60, 62, 72), Color.rgb(35, 36, 42), Color.rgb(15, 16, 20)),
        // 4: Fiery Auburn / Red
        intArrayOf(Color.rgb(250, 170, 130), Color.rgb(200, 85, 45), Color.rgb(140, 45, 25), Color.rgb(75, 20, 15))
    )

    suspend fun analyzeSelfie(input: Bitmap): AvatarProfile = withContext(Dispatchers.Default) {
        val faces = detectFaces(input)
        if (faces.isEmpty()) {
            return@withContext AvatarProfile()
        }

        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return@withContext AvatarProfile()
        val box = face.boundingBox
        val w = input.width
        val h = input.height

        // 1. Sample skin tone from forehead and upper cheeks
        val foreheadX = (box.centerX()).coerceIn(0, w - 1)
        val foreheadY = (box.top + box.height() * 0.25f).toInt().coerceIn(0, h - 1)
        val skinColor = sampleAverageColor(input, foreheadX, foreheadY, radius = 10)
        val detectedSkinIndex = classifySkinTone(skinColor)

        // 2. Sample hair color from top of the head
        val hairX = (box.centerX()).coerceIn(0, w - 1)
        val hairY = (box.top - box.height() * 0.12f).toInt().coerceIn(0, h - 1)
        val hairColor = sampleAverageColor(input, hairX, hairY, radius = 12)
        val detectedHairColIndex = classifyHairColor(hairColor)

        // 3. Hair length estimation based on head bounding box vertical ratio
        val headRatio = box.height().toFloat() / h.toFloat()
        val detectedHairStyle = when {
            headRatio < 0.38f -> 2 // Long hair occupies more of lower frame
            headRatio < 0.52f -> 0 // Short spiky
            else -> 1 // Medium parted
        }

        // 4. Eye color classification from eye landmarks
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val detectedEyeColor = if (leftEye != null || rightEye != null) {
            val eyePos = leftEye ?: rightEye!!
            val eyeX = eyePos.x.toInt().coerceIn(0, w - 1)
            val eyeY = eyePos.y.toInt().coerceIn(0, h - 1)
            val eyeCol = sampleAverageColor(input, eyeX, eyeY, radius = 3)
            classifyEyeColor(eyeCol)
        } else 0

        // 5. Face shape / jawline classification
        val boxAspect = box.width().toFloat() / box.height().toFloat()
        val detectedFaceShape = when {
            boxAspect > 0.82f -> 1 // Square Heroic
            boxAspect < 0.72f -> 0 // Slender V
            else -> 2              // Soft Rounded
        }

        // 6. Glasses detection: inspect contrast/luminance variation on the nose bridge
        val hasGlasses = if (leftEye != null && rightEye != null) {
            val bridgeX = ((leftEye.x + rightEye.x) / 2f).toInt().coerceIn(0, w - 1)
            val bridgeY = ((leftEye.y + rightEye.y) / 2f).toInt().coerceIn(0, h - 1)
            val bridgeColor = sampleAverageColor(input, bridgeX, bridgeY, radius = 4)
            val bridgeLum = (299 * Color.red(bridgeColor) + 587 * Color.green(bridgeColor) + 114 * Color.blue(bridgeColor)) / 1000
            val skinLum = (299 * Color.red(skinColor) + 587 * Color.green(skinColor) + 114 * Color.blue(skinColor)) / 1000
            abs(bridgeLum - skinLum) > 32 // Distinct edge or dark bridge
        } else false

        // 7. Facial hair detection (clean, stubble, goatee, full beard)
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val detectedFacialHair = if (mouthBottom != null) {
            val chinX = mouthBottom.x.toInt().coerceIn(0, w - 1)
            val chinY = (mouthBottom.y + box.height() * 0.10f).toInt().coerceIn(0, h - 1)
            val chinColor = sampleAverageColor(input, chinX, chinY, radius = 8)
            val chinLum = (299 * Color.red(chinColor) + 587 * Color.green(chinColor) + 114 * Color.blue(chinColor)) / 1000
            val skinLum = (299 * Color.red(skinColor) + 587 * Color.green(skinColor) + 114 * Color.blue(skinColor)) / 1000
            val chinDiff = skinLum - chinLum

            val lipDiff = if (noseBase != null) {
                val lipX = ((noseBase.x + mouthBottom.x) / 2f).toInt().coerceIn(0, w - 1)
                val lipY = ((noseBase.y + mouthBottom.y) / 2f).toInt().coerceIn(0, h - 1)
                val lipColor = sampleAverageColor(input, lipX, lipY, radius = 4)
                val lipLum = (299 * Color.red(lipColor) + 587 * Color.green(lipColor) + 114 * Color.blue(lipColor)) / 1000
                skinLum - lipLum
            } else 0

            when {
                chinDiff > 42 && lipDiff > 28 -> 3 // Full Beard & Mustache
                chinDiff > 35 -> 2                  // Goatee & Mustache
                chinDiff > 20 -> 1                  // 5 O'Clock Stubble
                else -> 0                           // Clean Shaven
            }
        } else 0

        AvatarProfile(
            skinToneIndex = detectedSkinIndex,
            eyeColorIndex = detectedEyeColor,
            faceShapeIndex = detectedFaceShape,
            hairStyleIndex = detectedHairStyle,
            hairColorIndex = detectedHairColIndex,
            attireIndex = 0,
            facialHairIndex = detectedFacialHair,
            hasGlasses = hasGlasses,
            hasStubble = detectedFacialHair > 0
        )
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { cont ->
            try {
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build()
                val detector = FaceDetection.getClient(options)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                detector.process(inputImage)
                    .addOnSuccessListener { faces -> cont.resume(faces) }
                    .addOnFailureListener { cont.resume(emptyList()) }
            } catch (_: Exception) {
                cont.resume(emptyList())
            }
        }

    private fun sampleAverageColor(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val minX = max(0, cx - radius)
        val maxX = min(bitmap.width - 1, cx + radius)
        val minY = max(0, cy - radius)
        val maxY = min(bitmap.height - 1, cy + radius)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val pix = bitmap.getPixel(x, y)
                rSum += Color.red(pix)
                gSum += Color.green(pix)
                bSum += Color.blue(pix)
                count++
            }
        }

        if (count == 0) return Color.WHITE
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    private fun classifySkinTone(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val lum = (299 * r + 587 * g + 114 * b) / 1000

        return when {
            lum > 215 -> 0 // Fair Alabaster
            lum > 175 -> 1 // Warm Peach (Aethel default)
            lum > 140 -> 2 // Olive
            lum > 105 -> 3 // Golden Tan
            else -> 4      // Deep Espresso
        }
    }

    private fun classifyHairColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val lum = (299 * r + 587 * g + 114 * b) / 1000

        return when {
            lum < 55 -> 3 // Raven Black
            r > g + 35 && r > b + 40 -> 4 // Fiery Auburn / Red
            r > 170 && g > 150 && b < 120 -> 1 // Golden Blonde
            abs(r - g) < 20 && abs(g - b) < 20 && lum > 140 -> 0 // Silver / Grey
            else -> 2 // Chestnut Brown
        }
    }

    private fun classifyEyeColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val lum = (299 * r + 587 * g + 114 * b) / 1000
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val sat = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC.toFloat()

        return when {
            b > r + 15 && b > g -> 0       // Sky Blue
            g > r + 8 && g > b -> 2        // Forest Emerald
            sat > 0.20f && r > 120 && g > 90 && b < 80 -> 3 // Golden Hazel / Amber
            sat < 0.12f && lum in 60..180 -> 4              // Slate Grey
            else -> 1                                       // Chestnut Brown
        }
    }

    fun synthesizePortrait(
        context: Context,
        profile: AvatarProfile,
        selectedBgAsset: String?,
        filterConfig: SelfieFilterConfig
    ): Bitmap {
        val size = SelfiePortraitProcessor.PORTRAIT_SIZE
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isFilterBitmap = false }

        // 1. Draw Background (default dark realm tint + optional selected background asset)
        canvas.drawColor(Color.rgb(20, 18, 28))
        val bgPath = selectedBgAsset ?: filterConfig.availableBackgrounds.firstOrNull()?.assetPath
        if (!bgPath.isNullOrBlank()) {
            val bgBitmap = loadAsset(context, bgPath, size)
            if (bgBitmap != null) {
                canvas.drawBitmap(bgBitmap, 0f, 0f, paint)
            }
        }

        // 2. Draw Attire
        val attireAsset = when (profile.attireIndex) {
            1 -> "game/creation/avatar/body_knight.png"
            2 -> "game/creation/avatar/body_scout.png"
            else -> "game/creation/avatar/body_mage.png"
        }
        loadAsset(context, attireAsset, size)?.let {
            canvas.drawBitmap(it, 0f, 0f, paint)
        }

        // 3. Draw Base Head (with selected jawline variant) and Skin & Eye Palette Tints
        val headAsset = when (profile.faceShapeIndex) {
            1 -> "game/creation/avatar/head_base_square.png"
            2 -> "game/creation/avatar/head_base_round.png"
            else -> "game/creation/avatar/head_base.png"
        }
        val baseHead = loadAsset(context, headAsset, size)
            ?: loadAsset(context, "game/creation/avatar/head_base.png", size)
        if (baseHead != null) {
            val skinPalette = SKIN_PALETTES.getOrElse(profile.skinToneIndex) { SKIN_PALETTES[1] }
            val tintedHead = tintSprite(baseHead, skinPalette, isSkin = true)
            val eyePalette = EYE_PALETTES.getOrElse(profile.eyeColorIndex) { EYE_PALETTES[0] }
            val eyesTintedHead = tintEyes(tintedHead, eyePalette)
            canvas.drawBitmap(eyesTintedHead, 0f, 0f, paint)
        }

        // 4. Draw Facial Hair (Clean, Stubble, Goatee, Full Beard)
        val beardAsset = when {
            profile.facialHairIndex == 1 || (profile.hasStubble && profile.facialHairIndex == 0) ->
                "game/creation/avatar/beard_stubble.png"
            profile.facialHairIndex == 2 -> "game/creation/avatar/beard_goatee.png"
            profile.facialHairIndex == 3 -> "game/creation/avatar/beard_full.png"
            else -> null
        }
        if (beardAsset != null) {
            val beardRaw = loadAsset(context, beardAsset, size)
                ?: if (profile.facialHairIndex == 1) loadAsset(context, "game/creation/avatar/stubble.png", size) else null
            if (beardRaw != null) {
                val hairPalette = HAIR_PALETTES.getOrElse(profile.hairColorIndex) { HAIR_PALETTES[0] }
                val tintedBeard = tintSprite(beardRaw, hairPalette, isSkin = false)
                canvas.drawBitmap(tintedBeard, 0f, 0f, paint)
            }
        }

        // 5. Draw Hairstyle with Hair Palette Tint
        val hairAsset = when (profile.hairStyleIndex) {
            1 -> "game/creation/avatar/hair_parted.png"
            2 -> "game/creation/avatar/hair_long.png"
            else -> "game/creation/avatar/hair_spiky.png"
        }
        val hairBitmap = loadAsset(context, hairAsset, size)
        if (hairBitmap != null) {
            val hairPalette = HAIR_PALETTES.getOrElse(profile.hairColorIndex) { HAIR_PALETTES[0] }
            val tintedHair = tintSprite(hairBitmap, hairPalette, isSkin = false)
            canvas.drawBitmap(tintedHair, 0f, 0f, paint)
        }

        // 6. Draw Glasses (if enabled)
        if (profile.hasGlasses) {
            loadAsset(context, "game/creation/avatar/glasses.png", size)?.let {
                canvas.drawBitmap(it, 0f, 0f, paint)
            }
        }

        // 7. Draw Frame Border Overlay
        val frameAsset = filterConfig.frameBorderAsset ?: "game/creation/borders/gold_fantasy_frame.png"
        loadAsset(context, frameAsset, size)?.let {
            canvas.drawBitmap(it, 0f, 0f, paint)
        }

        return result
    }

    private fun tintSprite(source: Bitmap, palette: IntArray, isSkin: Boolean): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(total)

        for (i in 0 until total) {
            val p = pixels[i]
            val a = Color.alpha(p)
            if (a < 10) {
                out[i] = 0
                continue
            }
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            // Preserve eye whites, irises, and dark line art so expressions remain crisp
            if (isSkin) {
                val isEyeWhite = r > 240 && g > 240 && b > 240
                val isLineArt = r < 35 && g < 35 && b < 45
                val isEyeIris = (b > r + 25 && b > 90) || (r < 50 && g < 50 && b < 50 && (i % w in 250..375))
                if (isEyeWhite || isLineArt || isEyeIris) {
                    out[i] = p
                    continue
                }
            } else {
                val isLineArt = r < 35 && g < 35 && b < 45
                if (isLineArt) {
                    out[i] = p
                    continue
                }
            }

            val lum = (299 * r + 587 * g + 114 * b) / 1000
            val color = when {
                lum > 200 -> palette[0]
                lum > 145 -> palette[1]
                lum > 95 -> palette[2]
                else -> palette[3]
            }
            out[i] = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun tintEyes(source: Bitmap, palette: IntArray): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(total)

        for (i in 0 until total) {
            val p = pixels[i]
            val a = Color.alpha(p)
            if (a < 10) {
                out[i] = 0
                continue
            }
            val x = i % w
            val y = i / w

            if (y in 170..215 && x in 255..375) {
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Catchlight highlight: preserve pure white
                if (r > 240 && g > 240 && b > 240) {
                    out[i] = p
                    continue
                }
                // Pupil: preserve pure black
                if (r < 30 && g < 30 && b < 30) {
                    out[i] = p
                    continue
                }
                // Blue iris pixels
                if (b > r + 20 && b > 80) {
                    val lum = (299 * r + 587 * g + 114 * b) / 1000
                    val color = when {
                        lum > 165 -> palette[0]
                        lum > 115 -> palette[1]
                        lum > 75 -> palette[2]
                        else -> palette[3]
                    }
                    out[i] = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
                    continue
                }
            }
            out[i] = p
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun loadAsset(context: Context, path: String, targetSize: Int): Bitmap? = try {
        context.assets.open(path).use { stream ->
            val raw = BitmapFactory.decodeStream(stream)
            if (raw != null && (raw.width != targetSize || raw.height != targetSize)) {
                Bitmap.createScaledBitmap(raw, targetSize, targetSize, false)
            } else {
                raw
            }
        }
    } catch (_: Exception) {
        null
    }
}
