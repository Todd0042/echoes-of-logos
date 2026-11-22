# Balance Harness — the thing that makes "pristine + realistic" verifiable

> The point of extracting a pure `combat/` core (C-lite) is so balance can be **measured**, not
> guessed. This harness is a JVM-only test (no Robolectric) that auto-plays every encounter
> thousands of times and asserts the invariants. It fails CI if the design regresses.

## What it runs
A headless `CombatSim` (`combat/` package) reproducing the real turn loop:
1. Build party from `progression.json` at a target level band; build encounter enemies from
   `enemies.json` + `movesets.json` + `affinity-matrix.json`.
2. For each combatant action, drive the **real** `ResonanceEngine` + `SpellThesaurus` with synthetic
   chants at four skill bands so the skill axis is genuinely exercised:
   - **BASIC** (2-word literal, e.g. "fireball orc") → ~0–15% bonus
   - **ADEPT** (spell name + 1 root) → ~20–50%
   - **MYTHIC** (poetic, multi-root, novel) → ~100–150%
   - **TRANSCENDENTAL** (full archaic invocation) → ~155–200%
3. AI: party greedy-picks best-known spell/moves; enemies run weighted moveset + phase logic.
4. Seeded RNG; N≥2000 trials per (encounter × skill band × roster) cell for stable means.

## Metrics reported (per cell)
- **Win rate**, **turns-to-kill (TTK)**, **avg party damage taken**, **wiped-party rate**.
- **Per-spell contribution**: share of total damage+utility, flagged `DUD` (<15%) or `GODPICK`
  (>60% while others idle).
- **Per-enemy threat**: flagged `BRICK` (TTK>threshold at BASIC band) or `TRIVIAL`.
- **Per-member usefulness**: MVP-share (each member's damage+utility ÷ best member).

## Pass/fail gates (the invariants, made executable)
- **I-1 Resonance matters:** `dmg(TRANSCENDENTAL, neutral-target)` must exceed
  `dmg(BASIC, signature-weakness-target)` for ≥95% of spells. If a weak-point basic ever beats a
  great chant, affinity/level math is too fat → fail.
- **I-2 Nothing blocked:** assert `min effectiveness multiplier == 0.8` and `min status landing
  potency >= 0.25 for >=1 turn` across **every** school×family and status×family pair in the data.
  Any 0 or sub-floor → fail (also catches a future bad data-entry).
- **I-3 No useless ally:** for every encounter × its story-legal roster, every mandatory member's
  MVP-share ≥ **0.40** at the ADEPT band. Fails if e.g. a Nature-weak construct wall zeroes Lyra.
- **Difficulty curve:** win-rate must stay in a target band per skill band and rise smoothly:
  - BASIC band: lose ~30–50% early chapters (you must learn to chant well) — not 0%, not 100%.
  - MYTHIC/TRANSCENDENTAL band: ≥90% win, but not 100% (a great chant shouldn't trivialize; enemies
    have defense/guard/summons).
  - Chapter TTK must increase monotonically-ish; flag any early chapter harder than a later one.
- **Economy sanity (post MP-fix):** at MP costs, a member must be able to afford their kit an
  expected K actions before a rest; flag `ALWAYS_OUT_OF_MP` or `NEVER_OUT_OF_MP`.

## Output
- `docs/combat-design/balance-report.md` (generated): the big table of every cell.
- Tuning deltas: when a gate fails, the harness prints the **specific data change** that would
  clear it (e.g. "`verdant_cataclysm` DUD vs CONSTRUCT-heavy ch12 — raise nature->construct
  0.8->0.9 OR add an Overload side-benefit"). We edit JSON, not code, and re-run.

## Where it lives
`src/test/.../combat/BalanceHarnessTest.kt` (JVM unit test, `./gradlew test`), plus a `main()`
"ReportGen" runner for the full matrix export. Uses only the pure `combat/` package — no Android.

## Why not just tune by hand in-game
99 enemy templates × 7 families × 20 spells × 4 skill bands × 32 encounters is far beyond what a
person can eyeball; and the two bugs we already fixed (AOE, false-victory) are exactly the kind of
silent regression this harness catches structurally. Simulation is the only path to *realistic*
balance for a system this combinatorial.
