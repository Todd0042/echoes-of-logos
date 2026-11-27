package com.voicerpg.engine.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.voicerpg.engine.model.GameSaveData
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SaveSlotInfo
import com.voicerpg.engine.model.SaveSummary
import com.voicerpg.engine.model.SavedCharacterStats
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

/**
 * Robust Persistence Manager powered by AtomicFile and Multi-Slot Support.
 * Uses Android's AtomicFile to guarantee zero save file corruption even across unexpected
 * process termination or battery depletion.
 * Supports up to 3 independent campaign slots with seamless migration from single-save legacy versions.
 */
class SaveManager(private val context: Context? = null) {

    companion object {
        const val MAX_SLOTS = 3
        const val DEFAULT_SLOT = 1
        const val STORY_MODE_SLOT = 0
        private const val LEGACY_SAVE_FILE = "save_game_v1.json"
        private const val GLOBAL_SETTINGS_FILE = "global_settings.json"
        fun getSlotFileName(slot: Int): String = if (slot == STORY_MODE_SLOT) "save_story_mode.json" else "save_slot_$slot.json"
    }

    data class GlobalGameSettings(
        val isLanguageSetupCompleted: Boolean = false,
        val selectedLanguage: String = "en",
        val selectedRegion: String = ""
    )

    fun sanitizeSlot(slot: Int): Int = if (slot == STORY_MODE_SLOT) STORY_MODE_SLOT else slot.coerceIn(1, MAX_SLOTS)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var currentSlot: Int = DEFAULT_SLOT
        set(value) {
            field = sanitizeSlot(value)
        }

    // In-memory slot storage for unit tests and headless JVM environments
    private val inMemorySlots = mutableMapOf<Int, String>()
    private var inMemorySettings: String? = null

    fun loadGlobalSettings(): GlobalGameSettings {
        val file = context?.let { File(it.filesDir, GLOBAL_SETTINGS_FILE) }
        val json = if (file != null) {
            if (file.exists()) readAtomic(file) else null
        } else {
            inMemorySettings
        }
        return if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, GlobalGameSettings::class.java)
            } catch (_: Exception) {
                GlobalGameSettings(isLanguageSetupCompleted = context == null)
            }
        } else {
            GlobalGameSettings(isLanguageSetupCompleted = context == null)
        }
    }

    fun saveGlobalSettings(settings: GlobalGameSettings): Boolean {
        return try {
            val json = gson.toJson(settings)
            val file = context?.let { File(it.filesDir, GLOBAL_SETTINGS_FILE) }
            if (file != null) {
                writeAtomic(file, json)
            } else {
                inMemorySettings = json
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Saves from an older format [load] has cleared and nobody has been told about yet. */
    private var clearedOutdatedSaves: Int = 0

    /** How many older-format saves were cleared since the last call (for a one-time notice); resets to 0. */
    fun takeClearedOutdatedSaves(): Int = clearedOutdatedSaves.also { clearedOutdatedSaves = 0 }

    init {
        migrateLegacySaveIfNeeded()
    }

    private fun getSaveFile(slot: Int): File? {
        val validSlot = sanitizeSlot(slot)
        return context?.let { File(it.filesDir, getSlotFileName(validSlot)) }
    }

    private fun getLegacySaveFile(): File? {
        return context?.let { File(it.filesDir, LEGACY_SAVE_FILE) }
    }

    private fun migrateLegacySaveIfNeeded() {
        val legacy = getLegacySaveFile()
        val slot1 = getSaveFile(1)
        if (legacy != null && legacy.exists() && slot1 != null && !slot1.exists()) {
            try {
                val legacyJson = legacy.readText()
                if (legacyJson.isNotBlank()) {
                    writeAtomic(slot1, legacyJson)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun writeAtomic(file: File, content: String) {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        // AtomicFile keeps the previous save as a backup until the new one is fully written, so a crash
        // or power loss mid-write never leaves a truncated save.
        val atomicFile = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            stream = null
        } catch (e: Exception) {
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            throw e
        }
    }

    private fun readAtomic(file: File): String? {
        // AtomicFile restores from its .bak copy when a write was interrupted, so check both.
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return try {
            String(AtomicFile(file).readFully(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasSave(slot: Int = currentSlot): Boolean {
        val validSlot = sanitizeSlot(slot)
        val file = getSaveFile(validSlot)
        return if (file != null) {
            file.exists() && file.length() > 0
        } else {
            !inMemorySlots[validSlot].isNullOrBlank()
        }
    }

    fun save(data: GameSaveData, slot: Int = currentSlot, updateTimestamp: Boolean = true): Boolean {
        val validSlot = sanitizeSlot(slot)
        return try {
            val updated = if (updateTimestamp) data.copy(saveTimestamp = System.currentTimeMillis()) else data
            val json = gson.toJson(updated)
            val file = getSaveFile(validSlot)
            if (file != null) {
                writeAtomic(file, json)
            } else {
                inMemorySlots[validSlot] = json
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun load(slot: Int = currentSlot): GameSaveData? {
        val validSlot = sanitizeSlot(slot)
        return try {
            val file = getSaveFile(validSlot)
            val json = if (file != null) {
                readAtomic(file)
            } else {
                inMemorySlots[validSlot]
            }

            if (json.isNullOrBlank()) return null
            val data = gson.fromJson(json, GameSaveData::class.java)
            if (data.saveVersion < GameSaveData.CURRENT_VERSION) {
                // A save from an older format (e.g. with levels) is cleared, not converted.
                deleteSave(validSlot)
                clearedOutdatedSaves++
                return null
            }
            data
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteSave(slot: Int = currentSlot): Boolean {
        val validSlot = sanitizeSlot(slot)
        inMemorySlots.remove(validSlot)
        context?.let { ctx ->
            try {
                com.voicerpg.engine.ui.creation.SelfiePortraitProcessor.deleteCustomPortrait(ctx, validSlot)
            } catch (_: Exception) {}
        }
        val file = getSaveFile(validSlot)
        return if (file != null) {
            AtomicFile(file).delete()
            true
        } else {
            true
        }
    }

    fun getSlotSummary(slot: Int): SaveSummary? {
        val data = load(slot) ?: return null
        val scene = StoryScript.ALL_SCENES[data.currentSceneId] ?: StoryScript.INITIAL_SCENE
        val heroClass = com.voicerpg.engine.content.GameContent.classById(data.player.heroClassId)
        return SaveSummary(
            slotIndex = slot,
            heroName = data.player.name,
            heroClassTitle = heroClass.title,
            chapterTitle = scene.chapterTitle,
            sceneName = scene.name,
            partySize = data.partyStats.size.coerceAtLeast(1),
            timestamp = data.saveTimestamp
        )
    }

    fun getAllSlotInfos(): List<SaveSlotInfo> {
        return (1..MAX_SLOTS).map { slot ->
            SaveSlotInfo(
                slotIndex = slot,
                summary = getSlotSummary(slot)
            )
        }
    }

    fun createInitialSave(
        customization: PlayerCustomization,
        slot: Int = currentSlot,
        selectedLanguage: String = "en",
        selectedRegion: String = ""
    ): GameSaveData {
        val validSlot = sanitizeSlot(slot)
        val heroClass = com.voicerpg.engine.content.GameContent.classById(customization.heroClassId)
        val manifest = com.voicerpg.engine.content.GameContent.manifest

        val initialHeroStats = SavedCharacterStats(
            id = "hero",
            name = customization.name,
            loreClass = heroClass.title,
            currentHp = heroClass.startingHp,
            maxHp = heroClass.startingHp,
            currentMp = heroClass.startingMp,
            maxMp = heroClass.startingMp,
            speed = heroClass.startingSpeed
        )

        val trackDef = manifest.campaignTracks.firstOrNull { it.id == customization.campaignTrackId }
        val initialSceneId = trackDef?.startSceneId?.ifBlank { null } ?: manifest.initialSceneId
        val initialNodeId = trackDef?.startNodeId?.ifBlank { null } ?: manifest.initialNodeId

        val newSave = GameSaveData(
            player = customization,
            currentSceneId = initialSceneId,
            currentNodeId = initialNodeId,
            decisionsMade = emptyList(),
            // A track told by one of the game's characters starts with that character instead of the class hero.
            partyStats = listOf(
                customization.protagonistCharacterId?.let { com.voicerpg.engine.content.GameContent.characterById(it)?.toSavedStats() }
                    ?: initialHeroStats
            ),
            defeatedEncounters = emptyList(),
            unlockedCompanions = listOf("hero"),
            achievements = listOf(manifest.startingAchievementId),
            selectedLanguage = selectedLanguage,
            selectedRegion = selectedRegion
        )

        save(newSave, validSlot)
        return newSave
    }
}
