<!-- voicerpg-engine-rule:start -->
# VoiceRPG Engine Rule (installed by voice-rpg-engine; do not edit in the game)

This game runs on the shared **VoiceRPG engine** (`../voice-rpg-engine`, next to this repo). The same
engine code runs in every VoiceRPG game: echoes-of-logos, wallys-world, commander-america-rpg,
superstore-saga.

## The engine is read-only in this repo
These paths are exact copies of the engine and are listed in `.engine-sync/OWNED_PATHS`:
- `app/src/main/java/com/voicerpg/engine/**` (all engine code, including `MainActivity.kt`)
- `app/src/main/assets/game/commands/**` and `app/src/main/assets/game/localization/**`
- `app/src/test/java/com/voicerpg/engine/**` (engine tests) and `app/src/test/fixtures/template-pack/**`

**Do not edit, add or delete files there.** A pre-commit hook and the game's CI
(`.engine-sync/guard.sh`) reject any change that doesn't match the synced engine version, and
editing `.engine-sync/` by hand is detected too. Do not use `--no-verify`.

## What belongs in this repo
- Game content: `app/src/main/assets/game/**` (except `commands/` and `localization/`), plus portraits,
  story art, environments and audio under `app/src/main/assets/`. Translations of the game's own story
  and content phrases go in `app/src/main/assets/game/translations/strings_<lang>.json` (the engine's
  `localization/` holds only engine UI phrases).
- App identity: `applicationId`, app name, icon and other resources in `app/build.gradle.kts` and `app/src/main/res/`.
- Game-specific tests: `app/src/test/java/com/voicerpg/game/**`.

## When the game needs something the engine can't do
1. Make the change in `../voice-rpg-engine`, **content-neutral**: no names, story, spells or assets
   from any game in engine code. Per-game behaviour is a JSON field with a default that leaves the
   other games unchanged.
2. In the engine: `./gradlew testDebugUnitTest`, then `./scripts/test_content_packs.sh` (runs the
   engine tests against every game's content), then commit.
3. `./scripts/sync_engine_to_games.sh --verify` sends it to every game. Commit the sync in each game.

An engine test failing on this game's content means either the content breaks an engine contract
(fix the content) or the test assumes template data (fix the test in the engine, e.g. with
`TemplateFixture`). Never change or delete an engine test inside a game.

## 16 KB page size (release blocker)
Every APK must pass `bash ../voice-rpg-engine/scripts/check_16kb.sh app/build/outputs/apk/debug/app-debug.apk`
(or `check-16kb` if installed). Dependencies live in this game's `app/build.gradle.kts`: when adding or
upgrading one, rebuild and re-check. A failing `.so` means that dependency needs a 16 KB-aligned version
(e.g. TensorFlow Lite -> LiteRT `com.google.ai.edge.litert:litert`).

## Battle sprites (release blocker)
Every party fighter, enemy and boss needs a 64 px side-view Aseprite sheet (bosses 64, 96 or 128 px; each boss its
own) in `app/src/main/assets/game/sprites/sheets/` with a `feet` slice and its role's animations (party: idle,
ready, charge, cast, attack, hurt, ko, victory; enemies: idle, attack, hurt; bosses add windup, cast,
charge_ultimate, ultimate), `release=`/`impact=` events on cast, attack and ultimate. Spec:
`../voice-rpg-engine/docs/SPRITE_PLAN.md`. See what's missing with
`../voice-rpg-engine/scripts/sprite_coverage.sh <this game>`. A release build (`assembleRelease`, `bundleRelease`)
runs the release-variant tests through `.engine-sync/release_gate.gradle.kts` and fails until the contract is met;
set `"spriteContract": 1` in `manifest.json` to enforce it in every test run once the art is in.
<!-- voicerpg-engine-rule:end -->

# VoiceRPG Android: Agent Guidelines & Engineering Rules

This repository contains the native Android implementation of *VoiceRPG: Echoes of the Logos*. When pair programming on this codebase, adhere strictly to the following architectural guidelines and core project rules.

---

## 1. Combat Roster & Story Synchronization
* **Strict Narrative Roster Fidelity:** Never include companions in combat who have not yet officially joined the fellowship in the active story.
  * **Act I (Chapters 1–4):** Strictly the **Duo Fellowship** (**Aethel** + **Sir Cedric**). Lyra must NOT appear in combat.
  * **Act II Chapter 5:** Strictly the **Duo Fellowship** on a rescue operation.
  * **Act II Chapter 6:** **Trio Fellowship** (**Aethel**, **Sir Cedric**, and **Lyra the Grove Warden**).
  * Subsequent companions (Vane, Zephyr) join only when recruited in their respective chapters.
* Default fallback parties in `CombatViewModel` and test encounters must default to the Duo Fellowship unless testing multi-character mechanics explicitly.

---

## 2. Dialogue Hubs & Completed Option Elimination
* **No Endless Loops:** When the player visits a narrative hub offering multiple choices/inquiries (e.g. Camp, Aqueducts, Solaria Belfry, Drowned Fane):
  1. Each choice branch must set a distinct completion flag (e.g., `setFlagOnEnter = "ch5_creek_scouted"`).
  2. Choices with matching `completionFlag` must be filtered out / eliminated once completed so the player cannot loop endlessly.
  3. When all sub-story options in a hub are completed, the hub node must automatically advance to the next narrative milestone.
* **Rich Narrative Prose:** Maintain evocative, atmospheric JRPG storytelling with meaningful dialogue and distinct character voices, avoiding 1-line dismissive dialogue.

---

## 3. Screenless & Audio-First Playability (Pocket Mode)
* The game must remain **100% playable without looking at the screen**:
  * All dialogue lines and combat actions must support Text-To-Speech (TTS) narration.
  * Available choices must be readable aloud via `isReadChoicesEnabled` (*"Option 1: ... Option 2: ... What is your command?"*).
  * System toggles and status queries must be operable hands-free via speech meta-commands (*"Status"*, *"Enemies"*, *"Toggle narration"*, *"Read choices"*, *"Pocket mode"*, *"Auto listen"*, *"Options"*).
  * Auto-listen microphone re-arms seamlessly after TTS completion.

---

## 4. Multi-Voice Character Assignment
* Use Android's `android.speech.tts.Voice` API to programmatically discover installed voice models on the device:
  * Bind distinct physical voice models to each character:
    * **Sir Cedric:** Installed deep male baritone voice.
    * **Aethel (Hero):** Heroic female / invocator voice.
    * **Lyra:** Gentle nature warden voice.
    * **Narrator:** Impartial chronicle storyteller voice.
  * Fall back smoothly if fewer voice models are installed, using pitch and rate modulation as secondary character expressions.

---

## 5. Mandatory Verification, GitHub Release & Device Deployment
* **Automated Testing:** Always verify all changes with automated tests: `./gradlew test`.
* **Compilation:** Always build a fresh debug APK: `./gradlew assembleDebug`.
* **Mandatory GitHub Release For Every Change:** For every change or feature completed, always tag, create, and publish a new GitHub release with the compiled `app-debug.apk` attached as an asset.
* **Mandatory Phone Installation For Every Change:** For every change or feature completed, always automatically install the updated APK directly onto the user's connected phone via ADB (`adb install -r app/build/outputs/apk/debug/app-debug.apk`) without waiting to be asked.

---

## 6. Standalone Engine Synchronization
* **Engine Parity:** Any major engine-level feature, system capability, persistence improvement, audio option, or gameplay mode (e.g., Pure Story Mode auto-play, multi-slot `AtomicFile` persistence, narration playback rate controls, playstyle presets, cheat codes) added or updated in this repository MUST be synchronized to the standalone companion engine repository: `/home/todd/Documents/GitHub/voice-rpg-engine`.

