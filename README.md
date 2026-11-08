<p align="center">
  <a href="https://ko-fi.com/todd0042">
    <img src="https://raw.githubusercontent.com/Todd0042/creator-assets/main/banners/todd_creator_banner_v1.0.png" alt="Support Todd on Ko-fi" width="100%" />
  </a>
</p>

---

# 🔮 Echoes of the Logos: High-Fantasy Voice JRPG

[![Version](https://img.shields.io/badge/Version-1.7.0--preview-ff2a85.svg)](https://github.com/Todd0042/echoes-of-logos/releases)
[![License: PolyForm Noncommercial](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-00f0ff.svg)](LICENSE)
[![Platform: Android 8.0+](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![16 KB Page Size](https://img.shields.io/badge/16%20KB-Compatible-success.svg)](https://developer.android.com/16kb-page-size)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI: Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%202024.10.01-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Unit Tests](https://img.shields.io/badge/Unit%20Tests-224%20Passing-39ff14.svg)](app/src/test/java/com/voicerpg/engine/)
[![Engine: VoiceRPG](https://img.shields.io/badge/Engine-VoiceRPG%20v2.0.0-teal.svg)](https://github.com/Todd0042/voice-rpg-engine)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Creator-F16061?style=flat&logo=ko-fi&logoColor=white)](https://ko-fi.com/todd0042)

> **An immersive, high-fantasy voice-commanded retro JRPG for Android where hands-free incantations, tactical ATB combat, and mystical storytelling unite — featuring 16 chapters of story, complete offline multilingual translation (ES, DE, FR, PT, IT), poetic resonance scoring, and 100% screenless Pocket Mode.**

```
       _________________________________________________________________________
      |  SHADOW ARCHON MALAKOR                      HP [████████████░░░]  75%   |
      |  "The silence of the void consumes all!"    MP [███████████████] 100%   |
      |_________________________________________________________________________|
      |  PLAYER: "By the sacred dawn, illuminate the shadows!"                  |
      |  RESONANCE: MYTHIC (136%) >>> RADIANT BURST DEALS 2.25x HOLY DAMAGE!    |
      |_________________________________________________________________________|
```

---

## 📖 Table of Contents

* **Part 1 — Consumer Overview & Quick Start**
  * [✨ What Is Echoes of the Logos?](#-what-is-echoes-of-the-logos)
  * [🪄 How a Spell Incantation Works in 3 Steps](#-how-a-spell-incantation-works-in-3-steps)
  * [🌍 Complete Offline Multilingual Support (5 Languages)](#-complete-offline-multilingual-support-5-languages)
  * [📜 The 16-Chapter Epic Campaign & Elemental Magic](#-the-16-chapter-epic-campaign--elemental-magic)
  * [🚶 Screenless Pocket Mode & Interactive Chronicle](#-screenless-pocket-mode--interactive-chronicle)
  * [🚀 Quick Download & Installation (Android APK)](#-quick-download--installation-android-apk)
* **Part 2 — Technical Architecture & Engine Integration**
  * [1. Powered by VoiceRPG Engine](#1-powered-by-voicerpg-engine)
  * [2. How Echoes of the Logos Configures the Engine (`assets/game/`, `GameContent.kt`)](#2-how-echoes-of-the-logos-configures-the-engine)
  * [3. Multilingual Speech Recognition & Dialect Bundling (`BuiltInDictionaries.kt`)](#3-multilingual-speech-recognition--dialect-bundling)
  * [4. Calibrated Elemental Magic Resonance Formulas (`SpellChantPresets.kt`)](#4-calibrated-elemental-magic-resonance-formulas)
  * [5. Interactive Chronicle & Backlog Architecture (`StoryScreen.kt`)](#5-interactive-chronicle--backlog-architecture)
  * [6. Multi-Repo Synchronization Workflow (`sync_engine_to_games.sh`)](#6-multi-repo-synchronization-workflow)
  * [7. Automated Unit Verification Suite (`tests/`)](#7-automated-unit-verification-suite)
  * [8. Clean-Room Provenance & Non-Commercial Licensing](#8-clean-room-provenance--non-commercial-licensing)

---

# Part 1 — Consumer Overview & Quick Start

## ✨ What Is Echoes of the Logos?

**Echoes of the Logos** is an epic voice-first high-fantasy JRPG created natively for Android. 

In a realm where spoken language itself is the conduit of arcane power, you awaken as a wielder of the primordial **Logos**. Lead your party—Sir Cedric the stalwart paladin, Lyra the flame mage, and Zephyr the wind stalker—across 16 story chapters filled with tactical ATB battles, dungeon choices, and moral crossroads.

No complex buttons needed:
- **🗣️ Speak Real Arcane Incantations**: Cast spells by saying their names or reciting dramatic poetic chants (*"Spirits of flame, incinerate our foes!"*).
- **⚡ Dynamic Resonance Feedback**: Your pitch, volume, and rhythm are evaluated in real time. Reach **MYTHIC** and **TRANSCENDENTAL** resonance tiers to trigger massive 2.25x and 3.0x damage surges.
- **🎧 Full Hands-Free Audio Narration**: Every companion, narrator, and villain speaks using expressive, distinctive on-device voice models.

---

## 🪄 How a Spell Incantation Works in 3 Steps

1. **Wait for Your Turn**: The Active Time Battle (ATB) gauge charges based on your character's Agility.
2. **Chant Your Incantation**: Speak your desired spell into the microphone. Use poetic flair, deliberate cadence, and natural pitch inflection.
3. **Unleash Arcane Damage**: The engine computes your Resonance multiplier instantly and narrates the combat outcome through Text-To-Speech.

---

## 🌍 Complete Offline Multilingual Support (5 Languages)

Echoes of the Logos features complete on-device translations for:
- 🇪🇸 **Spanish (`es`)**
- 🇩🇪 **German (`de`)**
- 🇫🇷 **French (`fr`)**
- 🇵🇹 **Portuguese (`pt`)**
- 🇮🇹 **Italian (`it`)**

Every single story node, UI button, battle action, and spoken keyword prompt is localized with zero internet or cloud downloading required.

---

## 📜 The 16-Chapter Epic Campaign & Elemental Magic

- **Act I: The Awakening & The Foothills** (Chapters 1–4): Learn the basic Logos incantations and recruit Sir Cedric.
- **Act II: The Ashen Peaks & Windbound Vale** (Chapters 5–8): Uncover the Zephyr mystery and battle elemental chimeras.
- **Act III: The Sunken Ruins of Logos** (Chapters 9–12): Solve acoustic rune puzzles and confront corrupted guardians.
- **Act IV: The Void Convergence** (Chapters 13–16): Final assault on the Archon Citadel and the ultimate battle for the Logos.

---

## 🚶 Screenless Pocket Mode & Interactive Chronicle

- **Screenless Pocket Mode**: Commute, walk, or hike while playing through chapters eyes-free with continuous auto-listen and headphone audio routing.
- **Pokémon-Style Quest Chronicle**: Access the in-game Backlog anytime to view a chronological journey recap, completed quest milestones, and verbatim dialogue transcripts.

---

## 🚀 Quick Download & Installation (Android APK)

1. Download **`echoes-of-logos-v1.7.0-debug.apk`** from [Releases](https://github.com/Todd0042/echoes-of-logos/releases).
2. Install on any Android 8.0+ device (API 26 or higher).
3. Grant **Microphone Permission** on startup.

---

# Part 2 — Technical Architecture & Engine Integration

## 1. Powered by VoiceRPG Engine

Echoes of the Logos is built upon the modular **[VoiceRPG Engine](https://github.com/Todd0042/voice-rpg-engine)** framework. 

All core audio session arbitration, speech recognition, ATB turn scheduling, and resonance mathematics are managed by the engine, allowing Echoes of the Logos to focus entirely on world lore, narrative trees, and localized voice packs.

For comprehensive engine architecture documentation and equations, visit:  
👉 **[https://github.com/Todd0042/voice-rpg-engine](https://github.com/Todd0042/voice-rpg-engine)**

---

## 2. How Echoes of the Logos Configures the Engine

Echoes of the Logos utilizes the engine's data-driven asset pipeline:
- **`assets/game/story/`**: Over 350 branching narrative nodes and choice hubs serialized in compact JSON.
- **`assets/game/characters/`**: Hero class definitions, starting stats, and affinity charts.
- **`assets/game/commands/` & `localization/`**: Pre-compiled multilingual phrase dictionaries and speech recognition prompts.

---

## 3. Multilingual Speech Recognition & Dialect Bundling

Through [`BuiltInDictionaries.kt`](file:///home/todd/Documents/GitHub/echoes-of-logos/app/src/main/java/com/voicerpg/engine/localization/BuiltInDictionaries.kt), localized speech utterances are mapped directly to engine action IDs:
- In Spanish, saying *"atacar"* or *"bola de fuego"* maps directly to `ATTACK` and `fireball`.
- In German, saying *"heilen"* or *"feuerball"* triggers instant party heals and pyromancy.

---

## 4. Calibrated Elemental Magic Resonance Formulas

Incantation phrases are calibrated in [`SpellChantPresets.kt`](file:///home/todd/Documents/GitHub/echoes-of-logos/app/src/main/java/com/voicerpg/engine/engine/SpellChantPresets.kt) to ensure equitable scoring across all languages:
- **Spanish MYTHIC:** 7-word phrases with `"por el poder"` achieve 100%–150% resonance.
- **German MYTHIC:** 7-word phrases with `"durch die macht"` guarantee consistent tier scaling without accidental transcendental overshoot.

---

## 5. Interactive Chronicle & Backlog Architecture

The narrative backlog in [`StoryScreen.kt`](file:///home/todd/Documents/GitHub/echoes-of-logos/app/src/main/java/com/voicerpg/engine/ui/story/StoryScreen.kt) maintains an in-memory chronological journal:
- Records key narrative decisions, companion campfire conversations, and boss defeat milestones.
- Automatically generates recap cards formatted for quick review.

---

## 6. Multi-Repo Synchronization Workflow

Engine updates are synchronized from upstream `voice-rpg-engine` via:
```bash
/path/to/voice-rpg-engine/scripts/sync_engine_to_games.sh /path/to/echoes-of-logos
```

---

## 7. Automated Unit Verification Suite

Echoes of the Logos includes **224 automated unit tests** verifying multilingual intent resolution, combat movesets, and story graph integrity:
```bash
# Run unit test suite:
./gradlew testDebugUnitTest

# Assemble debug APK:
./gradlew assembleDebug
```

---

## 8. Clean-Room Provenance & Non-Commercial Licensing

- **Clean-Room Audio & Narrative:** All storyline text, character concepts, and sound effects are original creations.
- **PolyForm Noncommercial 1.0.0:** Free for personal and hobbyist use. For commercial licensing, contact **[todd.0042.git@gmail.com](mailto:todd.0042.git@gmail.com)**.

---

<p align="center">
  If you enjoy playing Echoes of the Logos, consider supporting open-source development:<br/><br/>
  <a href="https://ko-fi.com/todd0042" target="_blank">
    <img src="https://img.shields.io/badge/☕_Support_Echoes_of_the_Logos_on_Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support on Ko-fi">
  </a>
</p>

---

<p align="center">
  <a href="https://ko-fi.com/todd0042">
    <img src="https://raw.githubusercontent.com/Todd0042/creator-assets/main/banners/todd_creator_banner_v2.0.png" alt="Support Todd on Ko-fi" width="100%" />
  </a>
</p>
