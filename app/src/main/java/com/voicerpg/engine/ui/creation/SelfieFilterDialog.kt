package com.voicerpg.engine.ui.creation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicerpg.engine.model.SelfieFilterConfig
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroPanel

@Composable
fun SelfieFilterDialog(
    rawBitmap: Bitmap,
    filterConfig: SelfieFilterConfig,
    onDismiss: () -> Unit,
    onSavePortrait: (Bitmap) -> Unit
) {
    val context = LocalContext.current

    val hasBgs = filterConfig.availableBackgrounds.isNotEmpty()
    var applySegmentation by remember { mutableStateOf(hasBgs) }
    var selectedBgAsset by remember {
        mutableStateOf(filterConfig.availableBackgrounds.firstOrNull()?.assetPath)
    }

    val hasSynthesis = filterConfig.avatarSynthesis != null
    var applyAvatarSynthesis by remember {
        mutableStateOf(filterConfig.avatarSynthesis?.enabledByDefault ?: false)
    }
    val defaultAttire = filterConfig.avatarSynthesis?.defaultAttireIndex ?: 0
    var avatarProfile by remember {
        mutableStateOf(AvatarProfile(attireIndex = defaultAttire))
    }

    // Automatically analyze selfie features via ML Kit face landmarking
    LaunchedEffect(rawBitmap) {
        if (filterConfig.avatarSynthesis != null) {
            val detected = AvatarSynthesizer.analyzeSelfie(rawBitmap)
            avatarProfile = detected.copy(attireIndex = defaultAttire)
        }
    }

    var applyPixelArt by remember {
        mutableStateOf(filterConfig.pixelArt?.enabledByDefault ?: true)
    }
    var applyTFLite by remember {
        mutableStateOf(filterConfig.tfliteStylization?.enabledByDefault ?: false)
    }
    var applyCelShading by remember {
        mutableStateOf(filterConfig.celShading?.enabledByDefault ?: false)
    }
    var applyInkOutlines by remember {
        mutableStateOf(filterConfig.inkOutlines?.enabledByDefault ?: false)
    }
    var applyEyeEffect by remember {
        mutableStateOf(filterConfig.eyeEffect?.enabledByDefault ?: false)
    }
    var applyScanlines by remember {
        mutableStateOf(filterConfig.scanlines?.enabledByDefault ?: false)
    }
    var applyColorGrade by remember {
        mutableStateOf(filterConfig.colorGrade?.enabledByDefault ?: false)
    }

    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }

    // Re-process image whenever any filter setting, avatar feature, or chosen background changes
    LaunchedEffect(
        applyAvatarSynthesis,
        avatarProfile,
        applySegmentation,
        selectedBgAsset,
        applyPixelArt,
        applyTFLite,
        applyCelShading,
        applyInkOutlines,
        applyEyeEffect,
        applyScanlines,
        applyColorGrade
    ) {
        isProcessing = true
        val result = if (applyAvatarSynthesis && filterConfig.avatarSynthesis != null) {
            AvatarSynthesizer.synthesizePortrait(
                context = context,
                profile = avatarProfile,
                selectedBgAsset = if (applySegmentation) selectedBgAsset else null,
                filterConfig = filterConfig
            )
        } else {
            SelfiePortraitProcessor.processSelfie(
                context = context,
                inputBitmap = rawBitmap,
                filterConfig = filterConfig,
                selectedBgAsset = if (applySegmentation) selectedBgAsset else null,
                applySegmentation = applySegmentation && !selectedBgAsset.isNullOrBlank(),
                applyPixelArt = applyPixelArt,
                applyTFLite = applyTFLite,
                applyCelShading = applyCelShading,
                applyInkOutlines = applyInkOutlines,
                applyEyeEffect = applyEyeEffect,
                applyScanlines = applyScanlines,
                applyColorGrade = applyColorGrade
            )
        }
        processedBitmap = result
        isProcessing = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(RetroPanel)
                .border(2.dp, LogosGold, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fixed Header: Title & Subtitle
                Text(
                    text = filterConfig.filterDialogTitle ?: "📸 CUSTOM PORTRAIT FILTER",
                    color = LogosGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                filterConfig.filterDialogSubtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed Portrait Preview Frame (stays pinned at top during scrolling)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, LogosGold, RoundedCornerShape(10.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val currentProcessed = processedBitmap
                    if (currentProcessed != null) {
                        Image(
                            bitmap = currentProcessed.asImageBitmap(),
                            contentDescription = "Portrait Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = LogosGold,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "PROCESSING...",
                                    color = LogosGold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Customization Options (middle area scrolls while preview stays fixed at top)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mode Selector: JRPG Avatar Synthesis
                    filterConfig.avatarSynthesis?.let { synth ->
                        FilterToggleRow(
                            label = synth.label,
                            description = synth.description,
                            checked = applyAvatarSynthesis,
                            onCheckedChange = { applyAvatarSynthesis = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                if (applyAvatarSynthesis && filterConfig.avatarSynthesis != null) {
                    val synth = filterConfig.avatarSynthesis
                    val skinLabels = synth.skinToneLabels.ifEmpty { AvatarSynthesizer.SKIN_LABELS }
                    val eyeColorLabels = synth.eyeColorLabels.ifEmpty { AvatarSynthesizer.EYE_COLOR_LABELS }
                    val faceShapeLabels = synth.faceShapeLabels.ifEmpty { AvatarSynthesizer.FACE_SHAPE_LABELS }
                    val hairStyleLabels = synth.hairStyleLabels.ifEmpty { AvatarSynthesizer.HAIR_STYLE_LABELS }
                    val hairColorLabels = synth.hairColorLabels.ifEmpty { AvatarSynthesizer.HAIR_COLOR_LABELS }
                    val facialHairLabels = synth.facialHairLabels.ifEmpty { AvatarSynthesizer.FACIAL_HAIR_LABELS }
                    val attireLabels = synth.attireLabels.ifEmpty { AvatarSynthesizer.ATTIRE_LABELS }

                    // Hairstyle Selector
                    AvatarChipSelectorRow(
                        title = "💇 HAIRSTYLE",
                        options = hairStyleLabels,
                        selectedIndex = avatarProfile.hairStyleIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(hairStyleIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Hair Color Selector
                    AvatarChipSelectorRow(
                        title = "🎨 HAIR COLOR",
                        options = hairColorLabels,
                        selectedIndex = avatarProfile.hairColorIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(hairColorIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Eye Color Selector
                    AvatarChipSelectorRow(
                        title = "👀 EYE COLOR",
                        options = eyeColorLabels,
                        selectedIndex = avatarProfile.eyeColorIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(eyeColorIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Skin Tone Selector
                    AvatarChipSelectorRow(
                        title = "✨ SKIN COMPLEXION",
                        options = skinLabels,
                        selectedIndex = avatarProfile.skinToneIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(skinToneIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Jawline / Face Shape Selector
                    AvatarChipSelectorRow(
                        title = "🗿 JAWLINE / FACE SHAPE",
                        options = faceShapeLabels,
                        selectedIndex = avatarProfile.faceShapeIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(faceShapeIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Facial Hair Selector
                    AvatarChipSelectorRow(
                        title = "🧔 FACIAL HAIR",
                        options = facialHairLabels,
                        selectedIndex = avatarProfile.facialHairIndex,
                        onSelectIndex = {
                            avatarProfile = avatarProfile.copy(facialHairIndex = it, hasStubble = it > 0)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Attire Selector
                    AvatarChipSelectorRow(
                        title = "🛡️ HERO ATTIRE",
                        options = attireLabels,
                        selectedIndex = avatarProfile.attireIndex,
                        onSelectIndex = { avatarProfile = avatarProfile.copy(attireIndex = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Glasses Toggle
                    FilterToggleRow(
                        label = "👓 Glasses / Spectacles",
                        description = "Detected wireframe eyewear",
                        checked = avatarProfile.hasGlasses,
                        onCheckedChange = { avatarProfile = avatarProfile.copy(hasGlasses = it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Background Environment Selection
                    if (hasBgs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RetroBlack.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🏰 REALM BACKDROP",
                                color = LogosGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filterConfig.availableBackgrounds.forEach { bgOption ->
                                    val isSelected = selectedBgAsset == bgOption.assetPath
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) LogosGold.copy(alpha = 0.3f) else RetroBlack)
                                            .border(1.dp, if (isSelected) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
                                            .clickable { selectedBgAsset = bgOption.assetPath }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = bgOption.label,
                                            color = if (isSelected) LogosGold else Color.LightGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    // Filter Option: Retro Pixel Art
                    filterConfig.pixelArt?.let { pixel ->
                        FilterToggleRow(
                            label = pixel.label,
                            description = pixel.description,
                            checked = applyPixelArt,
                            onCheckedChange = { applyPixelArt = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Neural Anime Stylization (TFLite)
                    filterConfig.tfliteStylization?.let { tflite ->
                        FilterToggleRow(
                            label = tflite.label,
                            description = tflite.description,
                            checked = applyTFLite,
                            onCheckedChange = { applyTFLite = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Cel-Shading (Anime Style)
                    filterConfig.celShading?.let { cel ->
                        FilterToggleRow(
                            label = cel.label,
                            description = cel.description,
                            checked = applyCelShading,
                            onCheckedChange = { applyCelShading = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Stylized Ink Outlines
                    filterConfig.inkOutlines?.let { ink ->
                        FilterToggleRow(
                            label = ink.label,
                            description = ink.description,
                            checked = applyInkOutlines,
                            onCheckedChange = { applyInkOutlines = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Eye Effect
                    filterConfig.eyeEffect?.let { eye ->
                        FilterToggleRow(
                            label = eye.label,
                            description = eye.description,
                            checked = applyEyeEffect,
                            onCheckedChange = { applyEyeEffect = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: AI Background Cutout
                    if (hasBgs) {
                        FilterToggleRow(
                            label = filterConfig.backgroundToggleLabel ?: "AI Background Cutout",
                            description = filterConfig.backgroundToggleDescription ?: "Replace background with chosen environment",
                            checked = applySegmentation,
                            onCheckedChange = { applySegmentation = it }
                        )

                        if (applySegmentation) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filterConfig.availableBackgrounds.forEach { bgOption ->
                                    val isSelected = selectedBgAsset == bgOption.assetPath
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) LogosGold.copy(alpha = 0.3f) else RetroBlack)
                                            .border(1.dp, if (isSelected) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
                                            .clickable { selectedBgAsset = bgOption.assetPath }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = bgOption.label,
                                            color = if (isSelected) LogosGold else Color.LightGray,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Scanlines
                    filterConfig.scanlines?.let { scanlines ->
                        FilterToggleRow(
                            label = scanlines.label,
                            description = scanlines.description,
                            checked = applyScanlines,
                            onCheckedChange = { applyScanlines = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Filter Option: Color Grade
                    filterConfig.colorGrade?.let { colorGrade ->
                        FilterToggleRow(
                            label = colorGrade.label,
                            description = colorGrade.description,
                            checked = applyColorGrade,
                            onCheckedChange = { applyColorGrade = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                } // End of scrollable customization Column

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed Action Buttons: Cancel / Save (always accessible at the bottom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.LightGray
                        )
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            val ready = processedBitmap
                            if (ready != null) {
                                onSavePortrait(ready)
                            }
                        },
                        enabled = !isProcessing && processedBitmap != null,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LogosGold,
                            contentColor = RetroBlack,
                            disabledContainerColor = RetroBorder,
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        Text(
                            text = filterConfig.filterConfirmButton ?: "USE PORTRAIT ✔",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroBlack.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RetroBlack,
                checkedTrackColor = LogosGold,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = RetroPanel
            )
        )
    }
}

@Composable
private fun AvatarChipSelectorRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroBlack.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = LogosGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) LogosGold.copy(alpha = 0.3f) else RetroBlack)
                        .border(1.dp, if (isSelected) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectIndex(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) LogosGold else Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

