# VoiceRPG Combat — Redesign Planning Artifacts (Phase 1)

Branch: `wtf-combat-exists` (off `feat/ch9-16-campaign-expansion`)
Status: **PLANNING ONLY — no implementation.** These artifacts are the source of truth for the
combat overhaul described in conversation. Nothing here changes runtime behavior yet.

## What these fixes
Two shipped combat bugs (AOE single-target; false "all foes defeated") are already fixed on the
base branch. This folder plans the **remaining 15 combat issues** plus the **dead-code cleanup**:

1. MP is never spent / no affordability gate
2. Defend/Guard is a no-op (and wrongly speeds up the guard)
3. Status spells (stun/poison/haste/slow) do nothing
4. "Drain" spells heal allies but never damage the enemy
5. Flat, deterministic damage — no variance/crit, no attacker stat, no enemy defense
6. AOE damage numbers all stack at one screen coordinate
7. No elemental weakness/resistance, and no per-family status logic
8. Phase-3 "Death of Voice" forces a blanket 3× on everything, never cleared
9. State read-modify-write races (lost updates across delayed coroutines)
10. `roundNumber` never increments (UI stuck on "ROUND 1")
11. Three divergent duplicate spell tables (drifted base powers)
12. Enemy identity detected by brittle string match (`subtitle.contains`, `id=="shaman"`)
13. Enemy AI targets a random living hero (no focus/role logic)
14. **Every enemy has exactly one attack** (no moveset)
15. **No XP / leveling / stat growth**, and no stats feed combat (`level`/`xp` are dead fields)

## Reading order
| File | Purpose |
|---|---|
| [`SPEC.md`](SPEC.md) | Master design spec: principles, the affinity + status model, "nerdiness stays king" math, progression, movesets, architecture, methodology, locked decisions, hard invariants |
| [`data/affinity-matrix.json`](data/affinity-matrix.json) | 7 schools × 7 enemy families, impairment-only (floor 0.8) |
| [`data/status-catalog.json`](data/status-catalog.json) | Status definitions, tier-gated potency, per-family resistance (resisted, never blocked) |
| [`data/spells.json`](data/spells.json) | Single source of truth for every spell (replaces the 3 lists) |
| [`data/enemies.json`](data/enemies.json) | Every enemy template → family, self-element, role, stats, XP band, moveset, boss phases |
| [`data/movesets.json`](data/movesets.json) | Enemy moveset + boss phase scripts |
| [`data/progression.json`](data/progression.json) | XP curve, per-class stat growth, spell unlocks |
| [`BALANCE-HARNESS.md`](BALANCE-HARNESS.md) | The JVM auto-battle simulator + pass/fail "no useless ally" / "no dud spell" gates |
| [`DEAD-CODE.md`](DEAD-CODE.md) | Precise list of code that is dead now and that dies after the change |

## Non-negotiable design invariants (enforced by the harness in CI)
- **I-1 Resonance is sacred.** The only skill multiplier is `ResonanceEngine` (1.0×–3.0×). No stat,
  affinity, or level may exceed or replace it. A Transcendental chant on a *neutral* foe must still
  beat a Basic chant on a *signature-weakness* foe.
- **I-2 Nothing is ever blocked.** Effectiveness floor = **0.8×**, applied to **base damage only**
  — the resonance multiplier is never discounted by any matchup. Status resistance floor =
  **0.35× potency/duration**. There is no immunity, no 0-damage, no "this spell does nothing here."
- **I-3 No useless ally.** In every encounter, for every story-legal roster, no member's expected
  contribution (damage **or** always-available utility/heal/tank/buff, which are affinity-immune)
  may fall below the MVP-share floor. Support never bricks.
