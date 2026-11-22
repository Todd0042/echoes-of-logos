package com.voicerpg.engine.content

import com.voicerpg.engine.model.SelfieFilterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SelfieFilterConfigTest {

    @Before
    fun setUp() {
        GameContent.reset()
    }

    // Selfie portraits are opt-in: these checks apply to whatever the game configures.
    @Test
    fun testSelfieFilterConfigLoadedFromAssets() {
        val config = GameContent.selfieConfig
        assertNotNull("SelfieFilterConfig should not be null", config)
        if (!config.isEnabled) return
        assertNotNull("Disclaimer title should be present", config.disclaimerTitle)
        assertNotNull("Disclaimer text should be present", config.disclaimerText)
        assertTrue("Available backgrounds should not be empty", config.availableBackgrounds.isNotEmpty())

        // Verify each background points to a real asset file
        for (bg in config.availableBackgrounds) {
            assertTrue("Background label must not be blank", bg.label.isNotBlank())
            assertTrue("Background asset path must not be blank", bg.assetPath.isNotBlank())
            val file = File("app/src/main/assets/${bg.assetPath}")
            assertTrue("Background file must exist at ${file.path}", file.exists() || File("src/main/assets/${bg.assetPath}").exists())
        }
    }

    @Test
    fun testEyeEffectConfig() {
        val eye = GameContent.selfieConfig.eyeEffect ?: return
        assertTrue("Eye effect label must not be blank", eye.label.isNotBlank())
        assertTrue("Eye effect description must not be blank", eye.description.isNotBlank())
        assertTrue("Eye effect dark center color must start with #", eye.darkCenterColorHex.startsWith("#"))
        assertTrue("Radius factor must be positive", eye.radiusFactor > 0f)
    }

    @Test
    fun testColorGradeAndScanlineConfigs() {
        GameContent.selfieConfig.colorGrade?.let { colorGrade ->
            assertEquals("Color grade matrix must contain 20 float entries for 4x5 ColorMatrix", 20, colorGrade.colorMatrix.size)
        }
        GameContent.selfieConfig.scanlines?.let { scanlines ->
            assertTrue("Line alpha must be within 0..255", scanlines.lineAlpha in 0..255)
            assertTrue("Line spacing must be positive", scanlines.lineSpacing > 0f)
            assertTrue("Stroke width must be positive", scanlines.strokeWidth > 0f)
        }
    }

    @Test
    fun testDefaultFallbackWhenConfigAbsent() {
        val emptySource = object : ContentSource {
            override fun readText(path: String): String? = null
            override fun listFiles(dir: String): List<String> = emptyList()
        }
        val pack = GameContentLoader(emptySource).load()
        assertFalse("SelfieFilterConfig should be disabled by default if missing", pack.manifest.selfieConfig.isEnabled)
        assertTrue("Available backgrounds should be empty in fallback", pack.manifest.selfieConfig.availableBackgrounds.isEmpty())
    }
}
