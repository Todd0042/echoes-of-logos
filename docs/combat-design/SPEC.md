# Combat Redesign — Master Design Spec (Phase 1)

> Planning artifact. No code. This is the document the data tables in `data/` implement.
> Locked constraints from review: (a) preserve the "power from nerdiness" resonance core,
> (b) class/school-true interactions, (c) **nothing is blocked — only impaired.**

---

## 1. Ground truth (what exists today)

**Resonance ("nerdiness") is the soul and is untouched by this redesign.**
`ResonanceEngine.evaluate()` scores a chant on 6 axes → `bonusPercent` 0–200 → `damageMultiplier`
**1.0×–3.0×**:
- Lexical (word/syllable count, ≤45) · Thematic roots via `SpellThesaurus` (≤45) ·
  Poetic/archaic structure (+12) · Acoustic **volume** (≤40) · **Inflection/crescendo/pitch** (≤40) ·
  **Novelty** anti-repetition (≤30).
- Tiers (`ResonanceTier`): Basic(0–15) · Adept(20–50) · Master(55–95) · Mythic(100–150) ·
  Transcendental(155–200).

**Current damage**: `finalAmount = spell.basePower * damageMultiplier`
(`CombatViewModel.kt:1060`). No defense, no affinity, no status, no attacker stat. That is what we
add — **around** resonance, never instead of it.

**Schools today (6)**: `PYROMANCY · CRYOMANCY · ELECTROMANCY · HOLY · SHADOW · PHYSICAL`.
Problems found in `SpellThesaurus`:
- `HOLY_ROOTS` conflates **divine** (`radiance, celestial, smite, aegis`) with **nature**
  (`grove, briar, thorns, vines, rain, nature, rejuvenate`). So Lyra the Grove Warden is secretly a
  nature caster coded as Holy.
- `SHADOW_ROOTS` conflates with **heal/lifesteal** (`siphon, drain, mend`).
- `MARTIAL_ROOTS` overlaps (`strike` appears in both SHADOW and MARTIAL).

**Party spellkits** (`StoryEncounters.kt:14-36`, duplicated in `CombatViewModel.kt:78-99` and
`ClassSpellLibrary.kt`) + 4 customizable hero classes (Elementalist/Battlemage/Chanter/Shadowweaver).

**Enemies**: 32 encounters, 112 instantiated rows, ~99 unique names, 13 bosses. The `subtitle`
field encodes **role** (Vanguard=bruiser, Sniper=fast glass-cannon, Occultist=caster,
Summoner, Phantom, Boss-*) — it does **not** encode element. Element/family must be assigned from
lore (the name), which is exactly the work done in `data/enemies.json`.

---

## 2. The core principle: two orthogonal axes (approved model)

```
base        = (spell.basePower + levelPower) × affinityMultiplier   // affinity lives ONLY on base
hitDamage = max(1,
      base
    × resonanceMultiplier   (1.0–3.0)   // I-1 SACRED — untouched by ANYTHING else
    × variance (0.9–1.1) [× crit 1.5 if crit]
    - target.defense                     // mitigation, floored at 1
)
```

**Resonance is a pure multiplier on the voice: a Transcendental hit is always 3.0×, in any
matchup, against any enemy. Nothing — not resistance, not immunity, not defense — ever clips
eloquence.** Knowledge (affinity) modifies the *base* it multiplies. Full grid:

| base affinity → tier ↓ | Resist 0.8 | Neutral 1.0 | Weakness 2.0 |
|---|---|---|---|
| Basic 1.0× | 0.8 | 1.0 | 2.0 |
| Adept 1.5× | 1.2 | 1.5 | 3.0 |
| Master 2.0× | 1.6 | 2.0 | 4.0 |
| Mythic 2.5× | 2.0 | 2.5 | 5.0 |
| Transcendental 3.0× | 2.4 | 3.0 | **6.0** |

Resonance's vertical spread (3.0× on every row) dominates affinity's horizontal spread (2.5× max):
**speaking well beats picking well** — a Transcendental hit on a resisted target (2.4) out-damages
a Basic hit on a true weakness (2.0). Picking right still pays double; no penetration, no coupling,
no immunity: the absolute worst legal outcome is a 20% base reduction.

**No-useless-ally corollary (content-proof):** the guarantee is just two engine constants applied
*below* any data lookup — affinity floor 0.8 + resonance untouched. A resisted ally who chants well
(Master resist = 1.6×) out-damages a fumbled neutral (1.0×). Any future enemy family can only ever
add weak points, never walls.

---

## 3. Schools (7) and the Nature split

Split `HOLY` → `HOLY` (divine) + **`NATURE`** (verdant), re-point Lyra, and move the plant roots
out of the Holy thesaurus set. Final set:

| School | Thematic root family (thesaurus) | Damage flavor | Signature status |
|---|---|---|---|
| **PYROMANCY** | cinder, inferno, blaze, ignite, conflagration… | burst | **Burn** (escalating DoT) |
| **CRYOMANCY** | glacial, frost, blizzard, rime, absolute zero… | slow control | **Chill → Freeze** |
| **ELECTROMANCY** | tempest, galvanic, arc, storm, plasma… | chain/fast | **Overload** (ATB stun) |
| **NATURE** (new) | grove, briar, thorn, vine, rain, verdant, root… | attrition/control | **Root/Snare**, **Poison/Wither** |
| **HOLY** | radiance, divine, seraph, celestial, aegis, sanctify… | vs evil, heal | **Bless/Haste**, banish-void |
| **SHADOW** | umbra, abyss, void, eclipse, hollow, dread… | drain/corrode | **Corrode/Blight**, lifesteal |
| **PHYSICAL** | blade, cleave, rend, shatter, crush, vanguard… | steady | **Guard**, **Sunder** (−def) |

`SHADOW.mend/drain` roots move to a lifesteal keyword path, not the heal path.

---

## 4. Enemy families (7) — the "realistic" logic

Assign every enemy a **family** (creature nature) + a **self-element** (its own affinity, for the
I-2 same-element 0.8× impairment). The families and what makes them "real":

| Family | What they are | Resisted by biology/logic (→ mild/strong status resist) |
|---|---|---|
| **FLESH** | living humanoids & animals (orcs, soldiers, assassins, wyrm, spiders, leeches) | nothing special; bleed/poison/root/stun all land well |
| **UNDEAD** | corpses & restless dead (crypt guard, ghouls, husks, reapers, bone acolyte) | no blood → **poison/bleed resisted(strong)**; hates **holy**, fears **fire** |
| **CONSTRUCT** | clockwork & stone (phalanx, ironclads, arbalests, obelisk, Ouros) | no biology → **poison/root/fear resisted(strong)**; **shock disrupts**(×2) |
| **VERDANT** | plant & vine colossi (glassvine, thornvined, bog behemoth, canopy warden) | **fire burns**(×2); poison/entangle **resisted**(self); bleed mild |
| **VOID** | amorphous shadow-watery things (void shaman, tendrils, umbra, leviathan, wisps) | physical **passes through**(0.8) & **holy severs**(×1.5–2); poison resisted |
| **RADIANT** | holy oaths & choirs made manifest (archons, celestials, templars, clade) | **shadow corrodes**(×1.5–2); holy self **resisted**; blunt good |
| **SILENCE** | the Act-III anti-voice regime (Malakor, Echo Nullifiers, Wardens, Archivist) | lore-critical vs resonance; **holy/sonic** pierce, drain resisted |

**I-2 same-element rule**: if `spell.school == enemy.selfElement` → affinity 0.8 (e.g. fire on the
fire-affinity **Tripod Scorcher** = 0.8×, still useful). Full grid in
[`data/affinity-matrix.json`](data/affinity-matrix.json).

The 7 families are deliberately spread across every encounter so no fixed roster ever faces a wall
against all its damage (see §8).

---

## 5. Status system — tier-gated potency, resisted never blocked

Every offensive spell carries a **status** with lore-true behavior (e.g. *Briar Entangle* →
**Root/Snare**: it sounds like it would impede, and it does — by slowing the target's ATB).
Potency/duration are **gated by resonance tier**, so eloquence strengthens control (§7).
Per-family **status resistance** multiplies potency & duration down, **never to zero**.

| Status | Effect (on a target) | Tier gating (Basic → Transcendental) |
|---|---|---|
| **Burn** | DoT = k% of spell dmg/turn, +1 stack/tier | 1 → 5 turns, escalating |
| **Chill / Freeze** | −ATB speed; at Master+ becomes 1-turn **stun** | slow → slow+stun |
| **Overload** | skip next turn (ATB stun); chains on Construct | 0–1 → 1–2 |
| **Root / Snare** | heavy −ATB, blocks flee/retarget | 1 → 3 turns |
| **Poison / Wither** | stacking DoT, −maxHp for duration | 1 → 3 stacks |
| **Corrode / Blight** | −defense & −ATB | 10–30% def |
| **Weaken / Silence** | −target attack; Silence lowers *our* resonance (boss tool) | — |
| **Bless / Haste** | +ATB, regen, temp defense (support lane) | duration by tier |
| **Guard** | −incoming ×0.5 + taunt (Cedric; support, not affinity-gated) | — |

Resistance handling: `normal ×1.0 · mild ×0.6 · strong ×0.35` (floor). So **poison on Undead still
withers**, just at 1/3 potency for 1 turn; **root on a Construct still jams its ATB** briefly.
Details + per-family overrides in [`data/status-catalog.json`](data/status-catalog.json).

**I-2 guard**: a status is only "applied" if `effectivePotency>0 and effectiveTurns>=1` after
resistance; resistance never zeroes it (clamp), and every status has a **minimum 1-turn, ≥25%
potency** landing so the effect is *always* felt.

---

## 6. Enemy defense & movesets (fixes #5-defense, #14)

Enemies gain a `defense` (small flat, scaled by chapter) so mitigation exists, and a **moveset**
so they are never one-note. Bosses get **phase scripts** (new moves below HP thresholds) rather
than the current blanket 3× hack.

| Family/role example | Moves (name · school · kind · mult) |
|---|---|
| Orc bruiser (FLESH) | Cleave (PHYS·BASIC·1.0) · **Savage Rip** (PHYS·HEAVY·1.6, applies Bleed) · War Cry (self Bless) |
| Skeletal Sniper (UNDEAD) | Bone Shot (PHYS·BASIC, low-block) · **Gravewail** (SHADOW·AOE, −ATB) |
| Clockwork Phalanx (CONSTRUCT) | Iron Slam (PHYS·BASIC) · **Steam Vent** (PYRO·AOE·Burn) |
| Glassvine Colossus (VERDANT) | Vine Lash (NATURE) · **Root Grasp** (NATURE·SINGLE·Root) · Thorn Burst (NATURE·AOE) |
| Void Tendril (VOID) | Tendril Rake (SHADOW) · **Silence Grip** (SHADOW·SINGLE·Weaken our resonance) |
| Archon Custodian (RADIANT) | Smite (HOLY) · **Sanctify** (self/Bless) |
| Malakor (SILENCE, boss) | Hush (SHADOW) · Null Wave (AOE −resonance) · **[Phase<40%] Death of Voice** (scripted turn, once) |

Move selection = weighted random, filtered by active cooldowns + current HP phase; target rule per
move (single / lowest-HP / random / party-AOE). This also gives us real enemy AOE for free.

---

## 7. Keeping "nerdiness" dominant (the whole trick)

1. **Bounded non-resonance multipliers** (affinity ≤2.0, level ~1.3× endgame, crit ≤1.5, variance
   0.9–1.1). Resonance's 3.0× always outranges them.
2. **Tier-gated statuses**: a MASTER+ Nature chant roots 3 turns; a Basic chant only slows 1 turn.
   Chanting *well* is what makes Briar actually bind. Burn stacks / freeze-vs-chill / overload-chain
   all scale on tier the same way.
3. **Attunement**: chanting the school's own thesaurus roots (which already raise resonance) is the
   same act that "aims" the elemental — so the *nerdy* behavior is what triggers the affinity payoff.
4. **Weakness discovery (opt-in, default on)**: weaknesses hidden until observed/struck, then shown
   via the `Status`/`Enemies` voice command. Turns affinity into *earned knowledge* — thematically
   perfect for a game about knowing the right words.
5. Level/XP grow HP/MP/speed + ~3%/level potency — foundation, never a stat-wall that makes
   chanting irrelevant.

---

## 8. Progression + the fixed-roster safeguard (fixes #15, and enforces I-3)

**Progression** (`data/progression.json`): `xpToNext(level)=80+60·level`; per-class `StatGrowth`
(cedric +HP/def, lyra +Nature potency/MP, etc.); XP summed from defeated `enemy.xpReward`,
distributed to the **story-legal roster** (AGENTS §1); `onCombatVictory` applies level-ups into
`SavedCharacterStats` (which already has `level`/`xp` fields, so no save migration).

**No useless ally — enforced structurally (content-proof):**
- *System floor*: affinity is applied to **base damage** and hard-floored at 0.8 by the engine
  itself (below any data lookup) — so **no future enemy family can re-brick a member**, only add
  weak points. Resonance (1.0–3.0×) is *never* reduced by any matchup: a resisted ally who chants
  Master (1.6× total) out-damages a fumbled Basic neutral (1.0×). The retry rule ("immune" → 20%
  base reduction) is therefore a *theorem of the engine*, not a content-authoring convention.
- *Role-weighted contribution gate*: support-lane value (Cedric guard/taunt/heal, Lyra heal,
  Zephyr drain/jam) is affinity-immune and always lands. The balance harness (§Phase 3) scores each
  mandatory member's *total expected contribution* (damage + control + sustain + tempo) per
  encounter × legal roster, never raw damage — supports in resisted fights are near-max contributors.
- *Proven by CI*: the harness fails on any (encounter × roster × tier) cell where a mandatory
  member's total contribution drops under ~40% of the cell's MVP — before a phone ever sees it.

---

## 9. Architecture: C-lite (approved)

New pure-Kotlin `com.voicerpg.android.combat` package, **no Android/Compose deps**:
`DamageResolver`, `AffinityTable`, `StatusSystem`, `EnemyBrain(movesets)`, `Progression`,
`CombatSim` (headless turn loop for the harness). `CombatViewModel` becomes a thin adapter that
feeds the resolver and plays TTS/VFX from its results. Promoteable to a `:combat-core` Gradle
module later without touching callers. State writes across the ViewModel move to
`MutableStateFlow.update {}` (fixes #9 races).

Why C-lite and not a full module now: the **balance harness can only simulate a UI-free core**,
and realistic balance is impossible without it — but we can get that with a package boundary and
upgrade to a module later if we want auto-battle/replay features.

---

## 10. Methodology — how the task is conquered

- **Phase 0 — Inventorize** *(done)*: every spell/school/status, every enemy→family/role/stats,
  every chapter roster. This folder is the frozen record.
- **Phase 1 — Spec + data** *(this artifact)*: lock the matrices as `data/*.json`. **Numbers are
  proposed defaults, tuned in Phase 3; review the model, not exact values, before coding.**
- **Phase 2 — Pure rule core**: implement §2–§6 as dependency-free Kotlin + unit tests on the
  resolver/status/progression math.
- **Phase 3 — Balance SIM harness** (`BALANCE-HARNESS.md`): run thousands of auto-battles per
  encounter feeding Basic/Adept/Mythic/Transcendental chants through the *real* thesaurus; report
  TTK, win-rate, per-spell dud/god-pick, per-enemy brick flags, and the **I-1 / I-2 / I-3 gates**;
  iterate the JSON until green.
- **Phase 4 — Wire in**: ViewModel consumes resolver; per-enemy-coordinate floating numbers (#6);
  effectiveness + level-up + status narration for pocket mode ("*The Crypt Guard recoils — holy
  sears its bones!*").
- **Phase 5 — Campaign content pass**: verify all 32 encounters under the roster + family rules.
- **Phase 6 — Test, assemble, tag `-preview` release, `adb install`** per AGENTS §5.

---

## 11. Decisions log (from review)
- ✅ Split **Nature** from Holy (7 schools).
- ✅ **Impairment only** — no blocked spells/status; damage floor 0.8×, status-resist floor 0.35×
  with a ≥25%/1-turn minimum landing.
- ✅ **Tier-gated statuses** keep resonance dominant (Mechanism §7.2).
- ✅ Level → HP/MP/speed + ~3%/level potency; **not** a big ATK stat.
- ✅ Weakness **discovery** on (hidden until observed).
- ✅ **C-lite** pure `combat/` package + JVM sim harness.
- ✅ Enemies get real **movesets** + **boss phase scripts** (replaces blanket 3×).

**Open tuning (defaults chosen, revisit in Phase 3):** crit chance/cap; whether Silence debuff on
party is too punishing vs resonance; exact `defense` per chapter; xp distribution to fallen allies.
