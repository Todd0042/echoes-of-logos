package com.voicerpg.engine

import com.voicerpg.engine.audio.MusicManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicManagerTest {

    @Test
    fun testInitialState() {
        val musicManager = MusicManager(null)
        assertTrue(musicManager.isMusicEnabled.value)
        assertEquals(0.55f, musicManager.musicVolume.value, 0.01f)
        assertFalse(musicManager.isDucked.value)
    }

    @Test
    fun testToggleMusic() {
        val musicManager = MusicManager(null)
        assertTrue(musicManager.isMusicEnabled.value)

        val toggledOff = musicManager.toggleMusic()
        assertFalse(toggledOff)
        assertFalse(musicManager.isMusicEnabled.value)

        val toggledOn = musicManager.toggleMusic()
        assertTrue(toggledOn)
        assertTrue(musicManager.isMusicEnabled.value)
    }

    @Test
    fun testSetVolumeClamping() {
        val musicManager = MusicManager(null)
        musicManager.setVolume(0.8f)
        assertEquals(0.8f, musicManager.musicVolume.value, 0.01f)

        musicManager.setVolume(1.5f)
        assertEquals(1.0f, musicManager.musicVolume.value, 0.01f)

        musicManager.setVolume(-0.2f)
        assertEquals(0.0f, musicManager.musicVolume.value, 0.01f)
    }

    @Test
    fun testDuckingState() {
        val musicManager = MusicManager(null)
        assertFalse(musicManager.isDucked.value)

        musicManager.duckForSpeech()
        assertTrue(musicManager.isDucked.value)

        musicManager.restoreFromSpeech()
        assertFalse(musicManager.isDucked.value)
    }

    @Test
    fun testTrackSwitching() {
        val musicManager = MusicManager(null)
        musicManager.playTrack(MusicManager.TRACK_EXPLORATION)
        assertEquals(MusicManager.TRACK_EXPLORATION, musicManager.currentTrack.value)

        musicManager.playTrack(MusicManager.TRACK_TITLE)
        assertEquals(MusicManager.TRACK_TITLE, musicManager.currentTrack.value)

        musicManager.playCombatMusic()
        assertEquals(MusicManager.TRACK_COMBAT, musicManager.currentTrack.value)
    }
}
