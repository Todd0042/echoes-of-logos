<p align="center">
  <a href="https://ko-fi.com/todd0042">
    <img src="https://raw.githubusercontent.com/Todd0042/creator-assets/main/banners/todd_creator_banner_v1.0.png" alt="Support Todd on Ko-fi" width="100%" />
  </a>
</p>

---

# VoiceRPG Android: Echoes of the Logos

[![Latest Release](https://img.shields.io/github/v/release/Todd0042/voice-rpg-android?label=Latest%20Release&color=blue)](https://github.com/Todd0042/voice-rpg-android/releases)
[![Android](https://img.shields.io/badge/Android-15%20%7C%2016-green.svg)](https://developer.android.com/)
[![Page Size](https://img.shields.io/badge/16%20KB-Compatible-success.svg)](https://developer.android.com/16kb-page-size)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Offline AI](https://img.shields.io/badge/AI-100%25%20On--Device-orange.svg)](https://developers.google.com/ml-kit)

An immersive, voice-commanded retro JRPG engineered natively for Android. All gameplay—incantations, tactical ATB combat, visual novel story exploration, dialogue choices, and system settings—can be commanded completely hands-free via natural voice or classic retro touch controls.

> [!NOTE]
> * **Latest Testing Release:** [v1.6.50-preview](https://github.com/Todd0042/voice-rpg-android/releases/tag/v1.6.50-preview) (with downloadable standalone APK).
> * **System Specifications & Formulas:** See [DESIGN.md](DESIGN.md).
> * **Autonomous Engineering & Engine Rules:** See [AGENTS.md](AGENTS.md).

---

## 🌟 Core Pillars & Key Features

### 1. 🌐 Multilingual Voice Engine & Regional Dialects
* **First-Launch Dialect Calibration:** On initial clean launch, the game presents a dedicated **Language & Regional Accent Setup Screen** before any title or menu displays.
* **6 Fully Supported Languages:**
  * 🇪🇸 **Spanish (`es`)**: Spain (Castilian), Mexico, Argentina, Colombia, etc.
  * 🇩🇪 **German (`de`)**: Germany, Austria, Switzerland
  * 🇫🇷 **French (`fr`)**: France, Canada, Belgium, Switzerland
  * 🇵🇹 **Portuguese (`pt`)**: Brazil, Portugal
  * 🇮🇹 **Italian (`it`)**: Italy, Switzerland
  * 🇺🇸/🇬🇧 **English (`en`)**: US, UK, Australia, Canada, India, etc.
* **Full On-Screen & Audio Localization:**
  * All UI menus, character creation, story dialogues, prompt pills, options, and battle conclusion screens adapt instantly.
  * `CombatNarrator` binds physical TTS voices corresponding to the player's chosen language and native regional dialect.
  * Language-specific system commands (`commands_{lang}.json`) and incantation thesauri (`thesaurus.json`) allow voice navigation and spell chants in any supported language.

---

### 2. 📸 AI Selfie Hero Avatar & Neural Stylization
* **Front-Facing Camera Capture:** Convert yourself into an authentic 16-bit JRPG protagonist using on-device computer vision.
* **On-Device ML Kit & TFLite Pipeline:**
  * **Face Detection & Landmark Extraction:** Automatically detects facial contours, eyes, nose, and mouth orientation.
  * **Selfie Segmentation:** Isolates the player's head and hair silhouette from backgrounds.
  * **Neural Style Transfer:** Synthesizes realistic lighting, skin tones, and palette quantization to match 90s classic pixel art.
* **Customizable JRPG Likeness:**
  * Detects and matches hair length, eye color, and facial orientation (aligned to standard 3/4 right view).
  * Selectable retro JRPG hairstyles (Spiky, Short, Classic, Long).
  * Hero attire overlays: Paladin Gold Plate, Forest Scout Mantle, Rogue Leather, and Mage Robes.
  * Persistent portrait preview with sticky dialog presentation and memoir advisory disclaimers.

---

### 3. 🎧 100% Screenless & Pocket Mode (Audio-First Accessibility)
* **Play Eyes-Free Anywhere:** Designed from the ground up to be played while walking with your phone in your pocket, or fully accessible for blind and visually impaired gamers.
* **Continuous Hands-Free Voice Loop:** The microphone automatically re-arms after every spoken line and narrative beat.
* **AMOLED Touch Guard:** A true-black interactive screen shield blocks accidental pocket taps while keeping audio, TTS, and microphone listening active.
* **Instant Playstyle Presets:**
  * **🎧 Pocket Walk:** Screen dark & protected, auto-listen active, full immersive voice narration.
  * **📖 Storybook:** Hands-free listening with screen awake, full audio drama.
  * **🎮 Classic Tactical:** Visual RPG layout with manual mic activation and classic touch inputs.
* **Voice Meta-Commands:** Spoken queries available anytime:
  * *"Status"*, *"Enemies"*, *"Fellowship"*, *"Help"*, *"Read choices"*, *"Toggle narration"*, *"Pocket mode"*, *"Auto listen"*, *"Open options"*.

---

### 4. 📖 Narrative Hubs & Pure Story Mode
* **No Endless Loops:** Branching narrative hubs track progression with atomic completion flags. Completed choices are automatically eliminated upon return.
* **Milestone Progression:** Once all sub-stories in a hub are explored, the story automatically advances to the next chapter.
* **Dedicated "Pure Story Mode":** Players who wish to experience the narrative without tactical friction can enable Pure Story Mode to streamline combat resolution.
* **Chronicle Archives & Warp Menu:** Review past story chapters, companion bonds, unlocked lore, and warp directly to any reached milestone.
* **Isolated Multi-Slot Persistence (`SaveManager`):** Atomic local JSON saves (`save_slot_1.json`, etc.) with cloud auto-backup disabled to ensure total privacy and clean resets.

---

### 5. ⚔️ Tactical ATB Combat & Incantation Resonance
* **Dynamic ATB Initiative:** Agility-driven Active Time Battle system featuring hero flanks, front/back positioning, and dynamic enemy reinforcements (up to a 6-enemy cap).
* **Restored 32-Bit Party Sprites:** Distinct animated retro sprites for the entire fellowship: Aethel, Sir Cedric, Lyra, and Zephyr.
* **5-Pillar Acoustic & Lexical Resonance Engine:**
  * 100% offline speech recognition via Android's `SpeechRecognizer`.
  * Graded across: Thematic Vocabulary Density, Lexical Cadence, Acoustic Volume, Pitch Modulation, and Anti-Spam Novelty.
  * Unleash up to a **+200% Transcendental damage/healing multiplier** accompanied by screen-splitting shockwaves and 450+ particle VFX explosions.

---

## 📜 Story Overview & Campaign Roadmap

* **Act I: The Falling Silence Shattered (Chapters 1–4)**
  * *Prologue:* Awakening in Whispering Pines; awakening the spoken Logos.
  * *Chapter 1:* Forest Ambush at the Old Way Shrine with Sir Cedric.
  * *Chapter 2:* Campfire fellowship and investigating the petrified village.
  * *Chapter 3:* Solaria Aqueduct infiltration, corrupted sentinels, and the first tuning fork.
  * *Chapter 4:* Breaching the Sun-Tower Belfry and tolling the First Great Bell of Solaria!
* **Act II: The Severed Resonance (Chapters 5–8)**
  * *Chapter 5 (The Drowned Fane):* Descent into the Rotting Marsh to rescue Lyra (Duo party).
  * *Chapter 6 (The Warden's Oath):* Lyra joins the fellowship; Trio battle against the Bog Behemoth to purify the sacred pool.
  * *Chapter 7 (Tuning the Veridian Chime):* Striking the botanical fork, reading the First Word steles, and tolling the Second Bell.
  * *Chapter 8 (The Shadowed Crags):* Ambush by Executioner Kaelen and defection of assassin Zephyr to forge the 4-hero Quad Fellowship!
* **Act III: The Crucible of Oaths — Companion Trials (Chapters 9–11)**
  * *Chapter 9 (Sir Cedric's Trial):* In the Mausoleum of the Sun, Cedric confronts Grandmaster Galahault, unlocking *Aegis of the Dawn*.
  * *Chapter 10 (Lyra's Trial):* In the Emerald Choir, Lyra cleanses the venom spring and unlocks *Verdant Cataclysm*.
  * *Chapter 11 (Zephyr's Trial):* In the Blind Gorge, Zephyr slays Master Nocturne, unlocking *Umbral Oblivion*.
* **Act IV: Ascent of the Monolith (Chapters 12–14)**
  * *Chapter 12 (The Clockwork Bastion):* Shutting down the steam grid, defeating Warmaster Ouros, and raising the sky bridge.
  * *Chapter 13 (The Silent Citadel):* Shattering the black glass gate and defeating Commander Vaelor.
  * *Chapter 14 (The Void Reservoir):* Defeating the Abyssal Leviathan and liberating captive voice motes to illuminate the Celestial Stair.
* **Act V & Epilogue: The Primordial Syllable & The Great Awakening (Chapters 15–16 & Epilogue)**
  * *Chapter 15 (Celestial Spire):* Fellowship vigils on the threshold of eternity against Archon Custodians.
  * *Chapter 16 (The Primordial Syllable):* Confronting Grand Inquisitor Malakor; speaking the 4-line Primordial Incantation in unison shatters the silence.
  * *Epilogue (The Great Awakening):* Tolling the Bell of Eternity; voices return to every living soul across Aethelgard.

---

## 🏗️ Technical Architecture & Project Structure

```
app/src/main/
├── assets/
│   ├── environments/         # 16-bit scenic backdrops (landscape & portrait)
│   ├── game/
│   │   ├── commands/         # Localized voice command definitions (de, en, es, fr, it, pt)
│   │   ├── spells/           # Incantation catalogs & multilingual thesaurus
│   │   ├── story/            # Narrative dialogue nodes & scene configurations
│   │   └── encounters/       # Monster stats, affinities, and enemy groups
│   └── story/                # Atmospheric chapter narrative backgrounds
└── java/com/voicerpg/engine/
    ├── MainActivity.kt       # Screen navigation & root Compose entry point
    ├── audio/
    │   ├── CombatNarrator.kt # Multi-voice TTS, physical voice bindings & auto-listen
    │   └── SpeechManager.kt  # On-device SpeechRecognizer wrapper & permission handler
    ├── engine/
    │   ├── IntentParser.kt   # Dual-stream voice command & spell intent parser
    │   ├── ResonanceEngine.kt# 5-pillar acoustic & lexical resonance grader
    │   ├── SaveManager.kt    # Scoped internal JSON persistence & global settings
    │   └── SpellThesaurus.kt # Multilingual semantic matcher for incantations
    ├── localization/
    │   └── TranslationManager.kt # Real-time UI & dialogue translation engine
    ├── model/
    │   ├── CommandCatalog.kt # Data classes for localized meta-commands
    │   ├── LanguageCatalog.kt# Supported languages, dialect regions & TTS voice mappings
    │   └── StoryModels.kt    # Dialogue nodes, choices, and state flags
    ├── ui/
    │   ├── combat/           # Retro battle arena, ATB timeline & status indicators
    │   ├── creation/         # Selfie portrait camera, ML Kit facial stylizer, and class picker
    │   ├── setup/            # LanguageSelectionScreen & AudioSetupScreen
    │   ├── story/            # 16-bit dialogue bubble presentation & narrative choices
    │   └── title/            # Title screen, save slot management, and chronicle archives
    └── viewmodel/
        ├── CombatViewModel.kt# 60 FPS ATB battle state machine & voice combat dispatcher
        └── StoryViewModel.kt # Global navigation, save persistence & dialogue manager
```

---

## 🔧 Building, Testing & Deployment

### Build Requirements
* **Android Studio:** Hedgehog | Iguana | Jellyfish | Koala | Ladybug
* **JDK:** Version 17
* **Android SDK:** `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
* **16 KB Compatibility:** Native packaging configured with `useLegacyPackaging = false` for Android 15/16 16 KB page-size compatibility.

### Run Tests
```bash
# Run complete unit test suite
./gradlew testDebugUnitTest
```

### Build APKs
```bash
# Build debug APK (includes developer diagnostic overlays)
./gradlew assembleDebug

# Build release APK (signed with debug key for direct sideloading)
./gradlew assembleRelease
```
The compiled release APK will be located at:  
`app/build/outputs/apk/release/app-release.apk`.

### Sideload via ADB
```bash
# Install release APK to connected phone
adb install -r app/build/outputs/apk/release/app-release.apk

# Launch app directly
adb shell am start -n com.voicerpg.android/com.voicerpg.engine.MainActivity
```

---

## 🤝 Community & Contributions
Pull requests and bug reports are welcome! When adding new localized dialects, voice commands, or story branches, ensure all changes pass `./gradlew testDebugUnitTest` and adhere to the guidelines in [AGENTS.md](AGENTS.md).

---

<p align="center">
  <a href="https://ko-fi.com/todd0042">
    <img src="https://raw.githubusercontent.com/Todd0042/creator-assets/main/banners/todd_creator_banner_v2.0.png" alt="Support Todd on Ko-fi" width="100%" />
  </a>
</p>

