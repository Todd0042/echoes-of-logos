#!/usr/bin/env bash
# ==============================================================================
# .engine-sync/guard.sh  (installed by voice-rpg-engine/scripts/sync_engine_to_games.sh; don't edit here)
#
# The engine owns the paths listed in .engine-sync/OWNED_PATHS. In this game they are exact copies
# of the engine version in .engine-sync/ENGINE_VERSION, with checksums in MANIFEST.sha256.
# Changes to them must be made in voice-rpg-engine and synced here.
#
#   guard.sh --ci       Working tree check (CI): every engine file matches its checksum, none
#                       missing, no extra files in engine-owned paths.
#   guard.sh --staged   Same check on what's about to be committed (pre-commit hook). If the
#                       manifest itself changes, it must match the engine at ENGINE_VERSION
#                       (checked against ../voice-rpg-engine when it's there).
# ==============================================================================
set -euo pipefail
export LC_ALL=C   # one sort order for sort/comm
cd "$(git rev-parse --show-toplevel)"
SYNC=.engine-sync
MODE="${1:---ci}"

fail() {
    echo "" >&2
    echo "✗ Engine files changed in this game repo." >&2
    echo "$1" | sed 's/^/    /' >&2
    echo "" >&2
    echo "  The engine (com.voicerpg.engine and the other paths in ${SYNC}/OWNED_PATHS) is shared by every" >&2
    echo "  VoiceRPG game and is read-only here. Make the change in voice-rpg-engine, keep it content-" >&2
    echo "  neutral (per-game behaviour goes in JSON with a default), then run" >&2
    echo "  voice-rpg-engine/scripts/sync_engine_to_games.sh to bring it here." >&2
    echo "  Game-specific tests belong in app/src/test/java/com/voicerpg/game/." >&2
    exit 1
}

[ -f "$SYNC/MANIFEST.sha256" ] && [ -f "$SYNC/OWNED_PATHS" ] || {
    echo "✗ ${SYNC}/ is missing: this game hasn't been synced from voice-rpg-engine yet." >&2; exit 1; }
mapfile -t OWNED < "$SYNC/OWNED_PATHS"

case "$MODE" in
--ci)
    problems=""
    if ! out=$(sha256sum --quiet -c "$SYNC/MANIFEST.sha256" 2>&1); then problems+="Modified or missing:"$'\n'"$out"$'\n'; fi
    expected=$(awk '{print $2}' "$SYNC/MANIFEST.sha256" | sort)
    actual=$(for p in "${OWNED[@]}"; do [ -d "$p" ] && find "$p" -type f ! -name '.DS_Store'; done | sort)
    extra=$(comm -13 <(echo "$expected") <(echo "$actual"))
    [ -n "$extra" ] && problems+="Not part of the engine:"$'\n'"$extra"$'\n'
    [ -z "$problems" ] || fail "$problems"
    echo "✓ Engine files match engine $(cut -c1-12 "$SYNC/ENGINE_VERSION")"
    ;;
--staged)
    # Only look closer if this commit touches engine-owned paths or the sync record.
    touched=$(git diff --cached --name-only -- "${OWNED[@]}" "$SYNC")
    [ -z "$touched" ] && exit 0
    problems=""
    while read -r sum path; do
        staged=$(git show ":$path" 2>/dev/null | sha256sum | cut -d' ' -f1) || staged="missing"
        [ "$staged" = "$sum" ] || problems+="$path"$'\n'
    done < <(git show ":$SYNC/MANIFEST.sha256")
    expected=$(git show ":$SYNC/MANIFEST.sha256" | awk '{print $2}' | sort)
    actual=$(git ls-files -- "${OWNED[@]}" | sort)
    extra=$(comm -13 <(echo "$expected") <(echo "$actual"))
    [ -n "$extra" ] && problems+="Not part of the engine:"$'\n'"$extra"$'\n'
    [ -z "$problems" ] || fail "Modified, missing or extra:"$'\n'"$problems"
    # A changed manifest must describe a real engine version (blocks "update the checksums too").
    if git diff --cached --name-only -- "$SYNC/MANIFEST.sha256" | grep -q .; then
        engine="../voice-rpg-engine"
        version=$(git show ":$SYNC/ENGINE_VERSION")
        if [ -d "$engine/.git" ] && [[ "$version" != *-dirty ]]; then
            git -C "$engine" cat-file -e "${version}^{commit}" 2>/dev/null || fail "ENGINE_VERSION ${version} is not a commit in ${engine}"
            while read -r sum path; do
                real=$(git -C "$engine" show "${version}:${path}" 2>/dev/null | sha256sum | cut -d' ' -f1)
                [ "$real" = "$sum" ] || fail "${path} doesn't match engine ${version:0:12}: the manifest was edited by hand"
            done < <(git show ":$SYNC/MANIFEST.sha256")
        fi
    fi
    ;;
*) echo "usage: guard.sh --ci | --staged" >&2; exit 2 ;;
esac
