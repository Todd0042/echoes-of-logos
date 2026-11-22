package com.voicerpg.engine

import com.voicerpg.engine.audio.MusicManager
import com.voicerpg.engine.engine.StoryScript
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards that every artwork path wired into the story engine (scene backgrounds and
 * character portraits) resolves to a real asset file, and that the narrator's
 * effective portrait is deterministic and stable.
 */
class AssetWiringTest {

    private val assetsDir: File = findAssetsDir()

    private fun findAssetsDir(): File {
        val candidates = listOf(File("src/main/assets"), File("app/src/main/assets"))
        for (candidate in candidates) {
            if (candidate.isDirectory) return candidate
        }
        throw IllegalStateException(
            "Could not locate assets dir from working dir ${System.getProperty("user.dir")}"
        )
    }

    @Test
    fun everySceneBackgroundAssetResolvesToARealFile() {
        val missing = mutableListOf<String>()
        for (scene in StoryScript.ALL_SCENES.values) {
            val file = File(assetsDir, scene.backgroundAsset)
            if (!file.isFile) missing.add("${scene.id} -> ${scene.backgroundAsset}")
        }
        assertTrue("Missing scene background assets: $missing", missing.isEmpty())
    }

    @Test
    fun everySceneMusicAssetResolvesToARealFile() {
        val missing = mutableListOf<String>()
        for (scene in StoryScript.ALL_SCENES.values) {
            val file = File(assetsDir, scene.musicAsset)
            if (!file.isFile) missing.add("${scene.id} -> ${scene.musicAsset}")
        }
        assertTrue("Missing scene music assets: $missing", missing.isEmpty())
    }

    @Test
    fun allBgmAudioFilesExist() {
        val expectedTracks = (
            listOf(
                MusicManager.TRACK_TITLE,
                MusicManager.TRACK_EXPLORATION,
                MusicManager.TRACK_COMBAT
            ) + StoryScript.ALL_SCENES.values.map { it.musicAsset }
        ).distinct()
        val missing = mutableListOf<String>()
        for (track in expectedTracks) {
            val file = File(assetsDir, track)
            if (!file.isFile) missing.add(track)
        }
        assertTrue("Missing BGM tracks: $missing", missing.isEmpty())
    }

    @Test
    fun everyPortraitAssetResolvesToARealFile() {
        val speakers = StoryScript.ALL_SPEAKERS
        val missing = mutableListOf<String>()
        for (speaker in speakers) {
            val asset = speaker.portraitAsset
            if (asset == null) {
                missing.add("${speaker.id} has no portrait asset")
                continue
            }
            if (!File(assetsDir, asset).isFile) missing.add("${speaker.id} -> $asset")
        }
        assertTrue("Missing portrait assets: $missing", missing.isEmpty())
    }

    @Test
    fun effectivePortraitAssetIsStableForNonNarrators() {
        val nonNarrators = StoryScript.ALL_SPEAKERS.filterNot { it.isNarrator }
        for (speaker in nonNarrators) {
            repeat(200) {
                assertTrue(
                    "${speaker.id} must always resolve its own portrait",
                    speaker.effectivePortraitAsset() == speaker.portraitAsset
                )
            }
        }
    }

    @Test
    fun narratorEffectivePortraitChoosesOnlyValidArt() {
        // The randomized easter-egg alternation was removed from the engine: the narrator
        // now always resolves its own declared portrait asset, deterministically.
        val narrator = StoryScript.ALL_SPEAKERS.first { it.isNarrator }
        repeat(10000) {
            assertEquals(narrator.portraitAsset, narrator.effectivePortraitAsset("scene_node_$it"))
        }
    }

    @Test
    fun narratorEffectivePortraitIsDeterministicWithSeed() {
        val narrator = StoryScript.ALL_SPEAKERS.first { it.isNarrator }
        val asset1 = narrator.effectivePortraitAsset("scene_node_42")
        val asset2 = narrator.effectivePortraitAsset("scene_node_42")
        assertEquals(asset1, asset2)
    }
}