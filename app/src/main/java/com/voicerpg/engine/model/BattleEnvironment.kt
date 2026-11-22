package com.voicerpg.engine.model

import androidx.compose.ui.graphics.Color

/**
 * The canonical combat environments in VoiceRPG Android.
 * Each environment adheres to the Four-Frame Living Background Rule, featuring
 * distinct cycling animation frames for foliage sway, dynamic light/shadow shifts,
 * and atmospheric secondary motes.
 */
enum class BattleEnvironment(
    val displayName: String,
    val icon: String,
    val loreLocation: String,
    val subtitle: String,
    val ambientThemeColor: Color
) {
    FOREST(
        displayName = "Forest",
        icon = "🌲",
        loreLocation = "Ashwood Wilds",
        subtitle = "Swaying canopies, wind-blown grass & drifting fireflies",
        ambientThemeColor = Color(0xFF2E7D32)
    ),
    CASTLE(
        displayName = "Castle",
        icon = "🏰",
        loreLocation = "The Broken Garrison",
        subtitle = "Stone battlements, fluttering banners & roaring braziers",
        ambientThemeColor = Color(0xFFD32F2F)
    ),
    DUNGEON(
        displayName = "Dungeon",
        icon = "🏛️",
        loreLocation = "Ashwood Sanctum",
        subtitle = "Vaulted stone arches, shifting torch shadows & cyan runes",
        ambientThemeColor = Color(0xFF00E5FF)
    ),
    CAVE(
        displayName = "Cave",
        icon = "🪨",
        loreLocation = "Void Hollows",
        subtitle = "Stalactites, falling water ripples & pulsing crystals",
        ambientThemeColor = Color(0xFFAB47BC)
    ),
    SWAMP(
        displayName = "Swamp",
        icon = "沼",
        loreLocation = "The Sunken Mire",
        subtitle = "Murky bog pools, bobbing will-o'-wisps & swaying reeds",
        ambientThemeColor = Color(0xFF00BFA5)
    )
}
