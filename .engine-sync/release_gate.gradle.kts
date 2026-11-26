// .engine-sync/release_gate.gradle.kts: installed by voice-rpg-engine (scripts/sync_engine_to_games.sh); don't edit
// in the game. Applied from app/build.gradle.kts.
//
// A release build (assembleRelease, bundleRelease) first runs the unit tests in the release variant. There the
// engine's release-gate tests are strict, among them the sprite contract (docs/SPRITE_PLAN.md in the engine):
// a game whose battle sprites fall short can't be released. Debug builds are not affected.
// Hooked to packaging (not preReleaseBuild: the release tests compile the release variant, which needs it),
// so no release APK or bundle is produced while the tests fail.
val releasePackaging = setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle", "assembleRelease", "bundleRelease")
tasks.matching { it.name in releasePackaging }.configureEach {
    dependsOn("testReleaseUnitTest")
}

// Unit tests read the game's content straight from src/main/assets, so it is a test input: a content-only change
// (a new sprite sheet, an enemy flagged as a boss) re-runs the tests instead of reporting an old result.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets").withPropertyName("gameContent").withPathSensitivity(PathSensitivity.RELATIVE)
}
