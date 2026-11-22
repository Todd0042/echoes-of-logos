# Dead-Code & Cleanup Inventory

Precise targets. "DEAD" = present but never meaningfully read; "OBSOLETE" = works but is brittle and
gets replaced; "DIES AFTER" = becomes dead once the fix lands (must be deleted in the same change so
we don't ship two sources of truth). Line refs are on `feat/ch9-16-campaign-expansion`.

## Currently dead / no-op
| What | Where | Status |
|---|---|---|
| `Spell.mpCost` | `model/CombatModels.kt:19` + every spell def | **DEAD** — declared ~39×, read **0×** (no gate, no spend). |
| `SavedCharacterStats.level` / `.xp` | `model/SaveModels.kt:98-99` | **DEAD** — initialized everywhere, never read or incremented. |
| `CombatState.roundNumber` | `CombatViewModel.kt:41, 1442` (read `InitiativeTrack.kt:54`) | **DEAD as feature** — never incremented; UI frozen on "ROUND 1". |
| Defend / Guard | `CombatViewModel.kt:619-651` | **NO-OP** — prints "(+Def)" but applies zero mitigation; `isGuarding` never modeled; wrongly sets ATB 0.35 (speeds guard up). |
| Stun/poison/haste/slow spells | `briar_entangle`,`temporal_stasis`,`venom_flurry`,`haste_cadence` (3 lists) | **NO-OP** — descriptions promise control, no status system exists. |
| Drain spells | `umbral_siphon` (`CombatViewModel.kt:99`,`StoryEncounters.kt:35`), `void_drain` (`ClassSpellLibrary.kt:33`) | **NO-OP** — coded `isHeal` only; heal an ally, never damage the foe they "siphon". |
| `TargetSelection.ORC/ARCHER/SHAMAN` | `CombatModels.kt:78-80` → `CombatViewModel.kt:1205-1207`, `IntentParser.kt:153-171` | **OBSOLETE** — hardcoded to Chapter-1 enemy ids; dead weight for the other ~97 enemies. |
| Summoner/element by string | `CombatViewModel.kt:271-272` (`subtitle.contains("Summoner"/"Occultist")`), `:316` (`id=="shaman"` school) | **OBSOLETE** — fragile identity sniffing. |
| 2 of 3 duplicate spell tables | `CombatViewModel.kt:78-99` vs `ClassSpellLibrary.kt` vs `StoryEncounters.kt:14-36` | **OBSOLETE** — same spells, drifted `basePower` (shield_wall 40/50, verdant 140/50, chain 48/55). |

## Becomes dead the moment the fix lands (delete in the same PR)
| Replaced | Where | Replaced by |
|---|---|---|
| Flat `finalAmount = basePower*multiplier` | `CombatViewModel.kt:1060` | `combat/DamageResolver` (attack + resonance + affinity + variance/crit − defense). |
| Inline single-attack enemy block | `CombatViewModel.kt:312-392` | `combat/EnemyBrain` move chooser over `moveset`/`phases`. |
| Blanket Phase-3 3× override | `CombatViewModel.kt:983-993` | Scripted, **one-time** "Death of Voice" break in `movesets.json` boss_malakor phase. |
| `IntentParser.defaultFallbackSpell()` | `IntentParser.kt:220-230` | `spells.json` generic `strike` (free, applies Bleed). |
| Hardcoded party spell lists | `CombatViewModel.kt:78-99` | `data/spells.json` loaded via `SpellCatalog`. |
| `tickAtb` MP-zero-on-nonready branch | `CombatViewModel.kt:183, 1080` | Real MP economy + death handling (spend on cast, restore on rest). |
| `isHeal` overload as "support" | `CombatViewModel.kt:1123-1138` | `role` field (HEAL/SUPPORT/BUFF) so Guard/Bless/Haste aren't shoehorned through heal math. |

## Kept — do NOT remove (verified live despite looking dead)
- `CombatPhase.SPELL_VFX_PLAYING` / `INCANTATION_RESOLVING` — read by `InitiativeTrack.kt:65-74` (alive).
- `FloatingCombatText.isCrit` — rendered (used as the super-tier flag today; will also carry real crits).
- `MetaCommand.*` (STATUS_REPORT, CHECK_ENEMIES, CHECK_PARTY, HELP) — all handled in `CombatViewModel` (this work only *extends* STATUS_REPORT to include level/XP/weaknesses).

## Cleanup acceptance
After implementation: `grep -rn "\.mpCost\|\.level\|\.xp\|ORC\b\|ARCHER\b\|contains(\"Summoner\")\|defaultFallbackSpell\|Subtitle.*Summoner"` should return **only** the new `combat/` + JSON loader call sites, and `./gradlew lint` must show no unused-field warnings on `Spell`/`SavedCharacterStats`/`CombatState`. The 3 old spell lists must be gone (one `SpellCatalog`).
