package com.voicerpg.engine.ui.gallery

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicerpg.engine.audio.MusicManager
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.ui.story.StoryAssetLoader
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroDeepSlate
import com.voicerpg.engine.ui.theme.RetroPanel

data class PortraitAssetItem(
    val id: String,
    val name: String,
    val title: String,
    val assetPath: String,
    val themeColor: Color
)

data class BackgroundAssetItem(
    val id: String,
    val name: String,
    val chapterOrAct: String,
    val assetPath: String
)

data class MusicAssetItem(
    val id: String,
    val title: String,
    val assetPath: String,
    val sourceDescription: String
)

object AssetCatalog {
    fun getPortraits(context: Context): List<PortraitAssetItem> {
        val list = mutableListOf<PortraitAssetItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. From GameContent speakers
        for (speaker in GameContent.allSpeakers) {
            val path = speaker.portraitAsset ?: continue
            if (seenPaths.add(path)) {
                list.add(
                    PortraitAssetItem(
                        id = speaker.id,
                        name = speaker.name,
                        title = speaker.title.ifBlank { "Speaker" },
                        assetPath = path,
                        themeColor = speaker.themeColor
                    )
                )
            }
        }

        // 2. From GameContent classes
        for (cls in GameContent.classes) {
            val path = cls.portraitAsset ?: continue
            if (seenPaths.add(path)) {
                list.add(
                    PortraitAssetItem(
                        id = cls.id,
                        name = cls.title,
                        title = cls.subtitle.ifBlank { "Hero Class" },
                        assetPath = path,
                        themeColor = LogosGold
                    )
                )
            }
        }

        // 3. Scan assets/portraits/
        try {
            val files = context.assets.list("portraits") ?: emptyArray()
            for (f in files) {
                if (f.endsWith(".jpg", ignoreCase = true) || f.endsWith(".png", ignoreCase = true)) {
                    val fullPath = "portraits/$f"
                    if (seenPaths.add(fullPath)) {
                        val cleanName = f.substringBeforeLast('.')
                            .replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                        list.add(
                            PortraitAssetItem(
                                id = f,
                                name = cleanName,
                                title = "Asset Portrait",
                                assetPath = fullPath,
                                themeColor = FrostCyan
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }

    fun getBackgrounds(context: Context): List<BackgroundAssetItem> {
        val list = mutableListOf<BackgroundAssetItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Manifest title background
        GameContent.manifest.titleBackgroundAsset?.let { path ->
            if (seenPaths.add(path)) {
                list.add(
                    BackgroundAssetItem(
                        id = "manifest_title",
                        name = GameContent.manifest.gameTitle + " Title",
                        chapterOrAct = "Main Title Artwork",
                        assetPath = path
                    )
                )
            }
        }

        // 2. Story Scenes
        for (scene in GameContent.scenes.values) {
            val path = scene.backgroundAsset
            if (path.isNotBlank() && seenPaths.add(path)) {
                list.add(
                    BackgroundAssetItem(
                        id = scene.id,
                        name = scene.name.ifBlank { scene.id },
                        chapterOrAct = if (scene.actTitle.isNotBlank()) scene.actTitle else "Act ${scene.actNumber}",
                        assetPath = path
                    )
                )
            }
        }

        // 3. Scan story/, backgrounds/, environments/ folders for unreferenced assets
        val folders = listOf("story", "backgrounds", "environments")
        for (folder in folders) {
            try {
                val files = context.assets.list(folder) ?: emptyArray()
                for (f in files) {
                    if (f.contains("_p.")) continue
                    if (f.endsWith(".jpg", ignoreCase = true) || f.endsWith(".png", ignoreCase = true)) {
                        val fullPath = "$folder/$f"
                        if (seenPaths.add(fullPath)) {
                            val cleanName = f.substringBeforeLast('.')
                                .replace("bg_", "")
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                            list.add(
                                BackgroundAssetItem(
                                    id = f,
                                    name = cleanName,
                                    chapterOrAct = "Visual Asset ($folder)",
                                    assetPath = fullPath
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return list
    }

    fun getMusicTracks(context: Context): List<MusicAssetItem> {
        val list = mutableListOf<MusicAssetItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Core Engine Themes
        val coreThemes = listOf(
            Triple(MusicManager.TRACK_TITLE, "Title Theme", "Main Menu & Opening"),
            Triple(MusicManager.TRACK_EXPLORATION, "Exploration Theme", "World Ambient & Story"),
            Triple(MusicManager.TRACK_COMBAT, "Combat Arena Theme", "Battle Encounters")
        )
        for ((path, title, desc) in coreThemes) {
            if (seenPaths.add(path)) {
                list.add(MusicAssetItem(id = path, title = title, assetPath = path, sourceDescription = desc))
            }
        }

        // 2. Scene BGMs
        for (scene in GameContent.scenes.values) {
            val path = scene.musicAsset
            if (path.isNotBlank() && seenPaths.add(path)) {
                val title = path.substringAfterLast('/').substringBeforeLast('.')
                    .replace("bgm_", "")
                    .replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
                list.add(
                    MusicAssetItem(
                        id = scene.id,
                        title = "$title (${scene.name})",
                        assetPath = path,
                        sourceDescription = "Scene: ${scene.chapterTitle}"
                    )
                )
            }
        }

        // 3. Scan audio/music/ and audio/
        for (folder in listOf("audio/music", "audio")) {
            try {
                val files = context.assets.list(folder) ?: emptyArray()
                for (f in files) {
                    if (f.endsWith(".ogg", ignoreCase = true) || f.endsWith(".mp3", ignoreCase = true) || f.endsWith(".wav", ignoreCase = true)) {
                        val fullPath = "$folder/$f"
                        if (seenPaths.add(fullPath)) {
                            val cleanTitle = f.substringBeforeLast('.')
                                .replace("bgm_", "")
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                            list.add(
                                MusicAssetItem(
                                    id = f,
                                    title = cleanTitle,
                                    assetPath = fullPath,
                                    sourceDescription = "Soundtrack Audio ($folder)"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return list
    }
}

enum class GalleryTab {
    PORTRAITS,
    BACKGROUNDS,
    JUKEBOX
}

@Composable
fun AssetViewerScreen(
    musicManager: MusicManager,
    speechManager: SpeechManager? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var currentTab by remember { mutableStateOf(GalleryTab.PORTRAITS) }

    val portraits = remember(context) { AssetCatalog.getPortraits(context) }
    val backgrounds = remember(context) { AssetCatalog.getBackgrounds(context) }
    val musicTracks = remember(context) { AssetCatalog.getMusicTracks(context) }

    var selectedPortraitIndex by remember { mutableStateOf<Int?>(null) }
    var selectedBackgroundIndex by remember { mutableStateOf<Int?>(null) }
    var isBackgroundImmersionMode by remember { mutableStateOf(false) }

    val currentPlayingTrack by musicManager.currentTrack.collectAsState()
    val isMusicEnabled by musicManager.isMusicEnabled.collectAsState()
    val musicVolume by musicManager.musicVolume.collectAsState()

    // Hands-free Voice Command handling
    LaunchedEffect(Unit) {
        speechManager?.let { sm ->
            sm.startListening(
                onResult = { recognized ->
                    val lower = recognized.lowercase()
                    when {
                        lower.contains("back") || lower.contains("exit") || lower.contains("title") || lower.contains("close") -> {
                            if (isBackgroundImmersionMode) {
                                isBackgroundImmersionMode = false
                            } else if (selectedPortraitIndex != null) {
                                selectedPortraitIndex = null
                            } else {
                                onBack()
                            }
                        }
                        lower.contains("portrait") || lower.contains("character") -> {
                            currentTab = GalleryTab.PORTRAITS
                        }
                        lower.contains("background") || lower.contains("scene") || lower.contains("art") -> {
                            currentTab = GalleryTab.BACKGROUNDS
                        }
                        lower.contains("music") || lower.contains("jukebox") || lower.contains("song") -> {
                            currentTab = GalleryTab.JUKEBOX
                        }
                        lower.contains("next") -> {
                            when (currentTab) {
                                GalleryTab.PORTRAITS -> selectedPortraitIndex?.let {
                                    selectedPortraitIndex = (it + 1) % portraits.size
                                }
                                GalleryTab.BACKGROUNDS -> selectedBackgroundIndex?.let {
                                    selectedBackgroundIndex = (it + 1) % backgrounds.size
                                }
                                GalleryTab.JUKEBOX -> {
                                    val curIdx = musicTracks.indexOfFirst { it.assetPath == currentPlayingTrack }
                                    val nextIdx = if (curIdx >= 0) (curIdx + 1) % musicTracks.size else 0
                                    musicManager.playTrack(musicTracks[nextIdx].assetPath)
                                }
                            }
                        }
                        lower.contains("previous") || lower.contains("prev") -> {
                            when (currentTab) {
                                GalleryTab.PORTRAITS -> selectedPortraitIndex?.let {
                                    selectedPortraitIndex = if (it > 0) it - 1 else portraits.size - 1
                                }
                                GalleryTab.BACKGROUNDS -> selectedBackgroundIndex?.let {
                                    selectedBackgroundIndex = if (it > 0) it - 1 else backgrounds.size - 1
                                }
                                GalleryTab.JUKEBOX -> {
                                    val curIdx = musicTracks.indexOfFirst { it.assetPath == currentPlayingTrack }
                                    val prevIdx = if (curIdx > 0) curIdx - 1 else musicTracks.size - 1
                                    musicManager.playTrack(musicTracks[prevIdx].assetPath)
                                }
                            }
                        }
                        lower.contains("play") -> {
                            val track = musicTracks.find { lower.contains(it.title.lowercase()) } ?: musicTracks.firstOrNull()
                            track?.let { musicManager.playTrack(it.assetPath) }
                        }
                        lower.contains("pause") -> {
                            musicManager.pause()
                        }
                        lower.contains("resume") -> {
                            musicManager.resume()
                        }
                        lower.contains("stop") -> {
                            musicManager.stopTrack()
                        }
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroBlack)
    ) {
        // Main Screen Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RetroDeepSlate)
                            .border(1.dp, RetroBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ARCHIVES & JUKEBOX",
                            color = LogosGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Game Asset Viewer • Tap or speak to preview",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Voice Command Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(RetroDeepSlate)
                        .border(1.dp, RetroBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🎤 Hands-Free",
                        color = HolyYellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(
                    title = "PORTRAITS (${portraits.size})",
                    isSelected = currentTab == GalleryTab.PORTRAITS,
                    onClick = { currentTab = GalleryTab.PORTRAITS },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    title = "SCENES (${backgrounds.size})",
                    isSelected = currentTab == GalleryTab.BACKGROUNDS,
                    onClick = { currentTab = GalleryTab.BACKGROUNDS },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    title = "JUKEBOX (${musicTracks.size})",
                    isSelected = currentTab == GalleryTab.JUKEBOX,
                    onClick = { currentTab = GalleryTab.JUKEBOX },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    GalleryTab.PORTRAITS -> {
                        PortraitsGrid(
                            portraits = portraits,
                            onSelect = { selectedPortraitIndex = it }
                        )
                    }
                    GalleryTab.BACKGROUNDS -> {
                        BackgroundsGrid(
                            backgrounds = backgrounds,
                            isPortrait = isPortrait,
                            onSelect = {
                                selectedBackgroundIndex = it
                                isBackgroundImmersionMode = true
                            }
                        )
                    }
                    GalleryTab.JUKEBOX -> {
                        JukeboxPlayer(
                            musicTracks = musicTracks,
                            musicManager = musicManager,
                            currentPlayingTrack = currentPlayingTrack,
                            isMusicEnabled = isMusicEnabled,
                            musicVolume = musicVolume
                        )
                    }
                }
            }
        }

        // Portrait Inspector Modal
        selectedPortraitIndex?.let { index ->
            val item = portraits.getOrNull(index)
            if (item != null) {
                PortraitInspectDialog(
                    item = item,
                    index = index,
                    total = portraits.size,
                    onPrev = {
                        selectedPortraitIndex = if (index > 0) index - 1 else portraits.size - 1
                    },
                    onNext = {
                        selectedPortraitIndex = (index + 1) % portraits.size
                    },
                    onDismiss = { selectedPortraitIndex = null }
                )
            }
        }

        // Fullscreen Background Immersion Overlay
        if (isBackgroundImmersionMode && selectedBackgroundIndex != null) {
            val item = backgrounds.getOrNull(selectedBackgroundIndex!!)
            if (item != null) {
                BackgroundImmersionView(
                    item = item,
                    index = selectedBackgroundIndex!!,
                    total = backgrounds.size,
                    isPortrait = isPortrait,
                    onPrev = {
                        selectedBackgroundIndex = if (selectedBackgroundIndex!! > 0) selectedBackgroundIndex!! - 1 else backgrounds.size - 1
                    },
                    onNext = {
                        selectedBackgroundIndex = (selectedBackgroundIndex!! + 1) % backgrounds.size
                    },
                    onDismiss = { isBackgroundImmersionMode = false }
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .border(
                1.dp,
                if (isSelected) LogosGold else RetroBorder,
                RoundedCornerShape(8.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) LogosGold.copy(alpha = 0.2f) else RetroDeepSlate
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) LogosGold else Color.LightGray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PortraitsGrid(
    portraits: List<PortraitAssetItem>,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val gridColumns = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 4

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(portraits) { index, item ->
            val bitmap = remember(item.assetPath) {
                StoryAssetLoader.loadBitmap(context, item.assetPath)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .border(1.dp, item.themeColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = RetroDeepSlate.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RetroBlack)
                            .border(1.dp, item.themeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "👤",
                                fontSize = 40.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.title,
                        color = item.themeColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundsGrid(
    backgrounds: List<BackgroundAssetItem>,
    isPortrait: Boolean,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val gridColumns = if (isPortrait) 1 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(backgrounds) { index, item ->
            val bitmap = remember(item.assetPath, isPortrait) {
                StoryAssetLoader.loadBackground(context, item.assetPath, isPortrait)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .border(1.dp, RetroBorder, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = RetroDeepSlate.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(RetroBlack)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "🌄",
                                fontSize = 48.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Gradient overlay at bottom of thumbnail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, RetroBlack.copy(alpha = 0.85f))
                                    )
                                )
                        )
                    }

                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "VIEW ➔",
                                color = LogosGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = item.chapterOrAct,
                            color = FrostCyan,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.assetPath,
                            color = Color.Gray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JukeboxPlayer(
    musicTracks: List<MusicAssetItem>,
    musicManager: MusicManager,
    currentPlayingTrack: String?,
    isMusicEnabled: Boolean,
    musicVolume: Float
) {
    val isPlaying = musicManager.isPlaying

    Column(modifier = Modifier.fillMaxSize()) {
        // Player Control Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RetroPanel)
                .border(2.dp, RetroBorderGold, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val activeItem = musicTracks.find { it.assetPath == currentPlayingTrack }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPlaying) "NOW PLAYING 🎵" else "JUKEBOX PLAYER ⏸️",
                            color = LogosGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = activeItem?.title ?: "Select a track to start playback",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeItem?.sourceDescription ?: "Tap any theme below",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Animated Equalizer Visualizer
                    EqualizerVisualizer(isPlaying = isPlaying)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track
                    IconButton(
                        onClick = {
                            val curIdx = musicTracks.indexOfFirst { it.assetPath == currentPlayingTrack }
                            val prevIdx = if (curIdx > 0) curIdx - 1 else musicTracks.size - 1
                            musicManager.playTrack(musicTracks[prevIdx].assetPath)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RetroDeepSlate)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play / Pause Toggle
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                musicManager.pause()
                            } else {
                                if (currentPlayingTrack != null) {
                                    musicManager.resume()
                                } else {
                                    musicTracks.firstOrNull()?.let { musicManager.playTrack(it.assetPath) }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(LogosGold)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = RetroBlack,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Stop Button
                    IconButton(
                        onClick = { musicManager.stopTrack() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RetroDeepSlate)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Next Track
                    IconButton(
                        onClick = {
                            val curIdx = musicTracks.indexOfFirst { it.assetPath == currentPlayingTrack }
                            val nextIdx = if (curIdx >= 0) (curIdx + 1) % musicTracks.size else 0
                            musicManager.playTrack(musicTracks[nextIdx].assetPath)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RetroDeepSlate)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Volume slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = LogosGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = musicVolume,
                        onValueChange = { musicManager.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = LogosGold,
                            activeTrackColor = LogosGold,
                            inactiveTrackColor = RetroBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(musicVolume * 100).toInt()}%",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Track List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(musicTracks) { index, track ->
                val isTrackActive = track.assetPath == currentPlayingTrack && isPlaying

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTrackActive) RetroDeepSlate else RetroPanel)
                        .border(
                            1.dp,
                            if (isTrackActive) LogosGold else RetroBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            musicManager.playTrack(track.assetPath)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isTrackActive) LogosGold else RetroDeepSlate)
                                    .border(1.dp, if (isTrackActive) LogosGold else RetroBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTrackActive) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isTrackActive) RetroBlack else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = track.title,
                                    color = if (isTrackActive) LogosGold else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.sourceDescription,
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = if (isTrackActive) "PLAYING" else "PLAY",
                            color = if (isTrackActive) HolyYellow else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerVisualizer(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val b1 by transition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1")
    val b2 by transition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b2")
    val b3 by transition.animateFloat(0.1f, 0.8f, infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b3")
    val b4 by transition.animateFloat(0.3f, 0.95f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b4")

    val heights = if (isPlaying) listOf(b1, b2, b3, b4) else listOf(0.2f, 0.2f, 0.2f, 0.2f)

    Row(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((28 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlaying) LogosGold else Color.Gray)
            )
        }
    }
}

@Composable
private fun PortraitInspectDialog(
    item: PortraitAssetItem,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(item.assetPath) {
        StoryAssetLoader.loadBitmap(context, item.assetPath)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, item.themeColor, RoundedCornerShape(16.dp)),
            color = RetroBlack.copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PORTRAIT ${index + 1} OF $total",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Enlarged Portrait Box
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RetroDeepSlate)
                        .border(2.dp, item.themeColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(text = "👤", fontSize = 60.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = item.title,
                    color = item.themeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ID: ${item.id} • Path: ${item.assetPath}",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Previous & Next Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onPrev,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, RetroBorder, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = RetroDeepSlate),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "◀ PREV", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, item.themeColor, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = item.themeColor.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "NEXT ▶", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundImmersionView(
    item: BackgroundAssetItem,
    index: Int,
    total: Int,
    isPortrait: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(item.assetPath, isPortrait) {
        StoryAssetLoader.loadBackground(context, item.assetPath, isPortrait)
    }

    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroBlack)
            .clickable { showControls = !showControls }
    ) {
        // High-res Background Artwork
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Floating HUD Header (toggleable on tap)
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(RetroBlack.copy(alpha = 0.95f), Color.Transparent)
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = LogosGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${item.chapterOrAct} (${index + 1}/$total)",
                            color = FrostCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.assetPath,
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RetroDeepSlate.copy(alpha = 0.8f))
                            .border(1.dp, RetroBorder, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Floating Bottom Navigation Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, RetroBlack.copy(alpha = 0.95f))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrev,
                        modifier = Modifier.border(1.dp, RetroBorder, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = RetroDeepSlate.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "◀ PREVIOUS", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Text(
                        text = "Tap to toggle UI",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = onNext,
                        modifier = Modifier.border(1.dp, RetroBorderGold, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = RetroDeepSlate.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "NEXT ▶", color = LogosGold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
