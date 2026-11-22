# Portrait Generation Re-Enablement & Asset Roadmap

> **Status:** Custom portrait generation is temporarily disabled in `echoes-of-logos` as of September 29, 2026.  
> **Target Re-Activation:** Next week.  
> **Goal:** Re-enable the feature with improved stylized fantasy assets tailored to the world of Solaria.

---

## 1. Quick Reversion Guide (How to Re-Enable Next Week)

To restore custom character portrait generation and its button in the UI, apply these 3 targeted changes:

### Step 1: Re-enable in `selfie_filters.json`
**File:** `app/src/main/assets/game/creation/selfie_filters.json`  
Change line 3:
```json
  "isEnabled": true,
```

### Step 2: Restore UI Button & Tap Trigger in `CharacterCreationScreen.kt`
**File:** `app/src/main/java/com/voicerpg/engine/ui/creation/CharacterCreationScreen.kt`

1. **Re-attach Clickable & Camera Badge on Portrait Box** (~line 543):
```kotlin
Box(
    modifier = Modifier
        .size(80.dp)
        .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = auraColorVal)
        .clip(RoundedCornerShape(10.dp))
        .border(2.dp, auraColorVal, RoundedCornerShape(10.dp))
        .then(
            if (selfieConfig.isEnabled) {
                Modifier.clickable {
                    if (!selfieConfig.disclaimerText.isNullOrBlank()) {
                        showWarningDialog = true
                    } else {
                        showSourceDialog = true
                    }
                }
            } else Modifier
        )
) {
    // ... inside Box, after image rendering:
    if (selfieConfig.isEnabled) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 6.dp))
                .background(RetroBlack.copy(alpha = 0.8f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(text = "📷", fontSize = 11.sp)
        }
    }
}
```

2. **Restore Initiation Button Row** (~line 625):
```kotlin
if (selfieConfig.isEnabled) {
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(RetroBlack)
                .border(1.dp, LogosGold, RoundedCornerShape(6.dp))
                .clickable {
                    if (!selfieConfig.disclaimerText.isNullOrBlank()) {
                        showWarningDialog = true
                    } else {
                        showSourceDialog = true
                    }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📷 SNAP SELFIE / PHOTO",
                color = LogosGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (hasCustomPortrait) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RetroBlack)
                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                    .clickable {
                        SelfiePortraitProcessor.deleteCustomPortrait(context, slot)
                        StoryAssetLoader.clearSlot(slot)
                        customPortraitBitmap = defaultHeroBitmap
                        hasCustomPortrait = false
                        combatNarrator?.speak("Portrait reset to default.", force = true)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↺ RESET DEFAULT",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
```

3. **Re-add Voice Triggers** (~line 210):
```kotlin
if (selfieConfig.isEnabled) {
    if (lower.contains("selfie") || lower.contains("camera") || lower.contains("picture") || lower.contains("photo")) {
        if (!selfieConfig.disclaimerText.isNullOrBlank()) {
            showWarningDialog = true
            combatNarrator?.speak(selfieConfig.disclaimerText, force = true)
        } else {
            showSourceDialog = true
        }
        return
    }
}
```

### Step 3: Update Test Assertion
**File:** `app/src/test/java/com/voicerpg/engine/content/SelfieFilterConfigTest.kt`  
Change line 23:
```kotlin
assertTrue("Template selfie filter should be enabled by default", config.isEnabled)
```

---

## 2. Asset Expansion Catalog (Ideas for Fantasy Fit)

All avatar layer assets live in:  
`app/src/main/assets/game/creation/avatar/` (512x512 transparent PNGs, centered face anchor).

### A. Outfits / Attire Layers (`body_*.png`)
*Current: `body_knight.png`, `body_mage.png`, `body_scout.png`*

- **`body_cleric.png` (Divine Healer)**: High collar white/cream linen tunic with gold embroidered liturgical stoles and holy talisman medallion.
- **`body_rogue.png` (Shadow Stalker)**: Dark leather brigandine, asymmetric shoulder pauldron with buckled dagger strap, charcoal hooded cowl resting around the neck.
- **`body_paladin.png` (Solarian Champion)**: Polished silver plate gorget with radiant gold sunburst insignia across breastplate.
- **`body_royal.png` (Noble / Sovereign)**: Velvet doublet with ermine fur mantle trim and ornate golden filigree chain.
- **`body_berserker.png` (Wild Warden)**: Thick wolf/bear pelt over reinforced hardened leather wraps with bone clasp.
- **`body_scholar.png` (Logos Chronicler)**: Layered scholar's vestment with brass scroll cylinder holster and inked parchment straps.

### B. Eyewear & Optical Overlays (`glasses_*.png`)
*Current: single generic `glasses.png`*

- **`glasses_wireframe.png` (Classic Round)**: Thin antique gold or dark brass circular frames (classic fantasy scholar/mage look).
- **`glasses_scholar_halfrim.png` (Half-Rim)**: Silver-wire half-rim spectacles sitting slightly down the nose.
- **`glasses_goggles_brass.png` (Artificer / Alchemist Goggles)**: Brass-rimmed adventurer goggles, optionally pushed up on forehead or resting over eyes.
- **`glasses_monocle.png` (Single Lens Occult)**: Fine gold chain attached to a single round lens over the right eye with subtle glowing runic etching.
- **`glasses_visor_runic.png` (Logos Blindfold / Visor)**: Thin embroidered silk blindfold with glowing turquoise arcane sigil in center.

### C. Hairstyles (`hair_*.png`)
*Current: `hair_spiky.png`, `hair_parted.png`, `hair_long.png`*

- **`hair_braided_ponytail.png`**: High pulled-back hair with warrior braids along the temples cascading into a long ponytail.
- **`hair_undercut.png`**: Modern fantasy cropped sides with swept textured top (fitting energetic knights/scouts).
- **`hair_wavy_noble.png`**: Shoulder-length soft layered waves with side sweep.
- **`hair_dreadlocks.png`**: Medium-length warrior dreadlocks gathered with small metal clips/bands.
- **`hair_wizard_wild.png`**: Long, wind-swept flowing locks extending behind shoulders and collar.
- **`hair_cropped_short.png`**: Clean military-style short crop for utilitarian combatants.

### D. Iris & Eye Color Presets (Configurable in `selfie_filters.json`)
*Currently uses single eye effect overlay.* Add pre-calibrated fantasy tint palettes:

- **Logos Cyan / Starlight**: `#00E5FF` (Luminescent celestial energy).
- **Solaria Amber / Sun Gold**: `#FFB300` (Awakened hero resonance).
- **Arcane Amethyst**: `#AB47BC` (Sorcery and void attunement).
- **Sylvan Emerald**: `#00E676` (Nature and elemental vitality).
- **Crimson Wyrm**: `#FF1744` (Dragon blood / berserk aura).
- **Silver Moonlit**: `#ECEFF1` (High elf / ancient bloodline).

### E. Face Accents & Accessories (`acc_*.png` / `face_*.png`)
- **`acc_circlet.png`**: Delicate golden diadem / circlet resting across the brow.
- **`acc_scar_cheek.png`**: Subtle battle-hardened diagonal slash scar across cheekbone.
- **`acc_runes_forehead.png`**: Faint luminous sigil etched onto forehead or temple.
- **`acc_elf_ears.png`**: Subtle pointed elven ear tips protruding from hair contour.

---

## 3. Technical Integration Checklist

1. **Asset Pipeline:** Keep transparent PNGs at 512×512, aligned with `head_base.png` eyes at `Y ≈ 42%` and chin at `Y ≈ 68%`.
2. **Metadata Binding:** In `selfie_filters.json`, add an `attireOptions` or `hairOptions` array so users can cycle through styles with arrows or voice keywords (`"change hair"`, `"wear armor"`, `"equip glasses"`).
3. **Synthesis Engine:** `AvatarSynthesizer.kt` already supports facial feature detection (eyes, nose, mouth landmarks) from ML Kit / TFLite; feeding detected hair/beard color directly into `ColorFilter` tinting keeps the pixel art cohesive.
