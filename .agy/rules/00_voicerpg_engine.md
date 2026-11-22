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
  story art, environments and audio under `app/src/main/assets/`.
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
