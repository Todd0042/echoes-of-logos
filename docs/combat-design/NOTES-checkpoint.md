# Combat Design — State Notes (pre-retry checkpoint)

> ⚡ READ FIRST (post-compaction directive). You just compacted. Do NOT re-plan from scratch and do
> NOT start writing app code yet. FIRST action after compaction: open these files and treat them as
> the source of truth:
>   1. `docs/combat-design/NOTES-checkpoint.md` (this file)
>   2. `docs/combat-design/SPEC.md`
>   3. `docs/combat-design/data/{affinity-matrix,status-catalog,spells,enemies,movesets,progression}.json`
>   4. `docs/combat-design/{BALANCE-HARNESS,DEAD-CODE}.md`
>
> Then continue from "## The durable principle" and "## Retry prompt the user will send next" below.
> Branch is `wtf-combat-exists`. These are untracked planning docs (no code committed).
> **Two lessons to NOT repeat:** (a) validate "no useless ally" at Basic/Adept/Master, NEVER just
> Transcendental (that bar trivially passes and is meaningless). (b) Do not rely on hand-inventing
> enemy families or per-companion extra lanes — the guarantee must be content-proof (a system
> floor that future chapters cannot violate).

> Saved so the current line of thinking survives compaction. This is the *latest* thinking and
> supersedes parts of SPEC.md §4/§8 where noted. Branch: `wtf-combat-exists`. Planning only — no code.

## Where we landed
Full analysis + data tables exist under `docs/combat-design/` (affinity-matrix, status-catalog,
spells, enemies, movesets, progression, balance harness, dead-code inventory, SPEC). Design so far
fixed all 15 combat issues + the 2 new ones (single-attack enemies; dead `level`/`xp`/no progression).

## Two corrections from the last exchange (important)
1. **Bad proof bar.** My earlier "no brick = PASS" test used a perfect Transcendental chant every
   turn, which trivially clears everything. The fights that actually hurt a player are at
   **Basic/Adept** resonance — that is where a construct/void/radiant wall feels like dead weight.
   Any re-validation MUST be run at Basic and Adept (and Master), not just Transcendental.
2. **The "give Lyra/Zephyr a PHYSICAL second lane" idea is CONTENT-FRAGILE.** It only holds because
   the 7 families were hand-picked. New story chapters (psychic "Doubt", a "Sound/Resonance" enemy
   type, new boss archetypes) could re-brick a fixed Duo/Trio/Quad roster. That was a "for now"
   patch — explicitly rejected by the user.

## The durable principle (content can add rewards, never walls)
The no-useless-ally guarantee must be a **property of the system, independent of the enemy table**:
- New content may only **add weak points (rewards)**; it must be **structurally incapable of
  creating a damage wall** against a fixed-roster member.
- Mechanism: **global impairment floor + resonance penetration active at EVERY tier**, applied
  *below* the matrix lookup:
    `effAffinity = clamp(base + pen(tier), FLOOR, 2.2)`
  - `FLOOR` is a system constant, not a per-family authored number → future families can't set it.
  - `pen(tier)` was previously gated so only Transcendental pierced; move the useful portion of
    penetration down to Master/adequate tiers so a resisted ally is never near-zero at the tiers a
    player realistically casts.
- Plus the existing affinity-immune utility/heal/control lane so a member is never reduced to
  "0.8× damage and nothing else."

## Retry prompt the user will send next
"Create a combat design that maintains the importance of the current combat resonance system —
nerd shit is rewarded above all else — but is balanced like a normal RPG. If something would be
immune, replace it with a 20% base damage reduction instead."

Interpretation for the retry:
- **I-1 stays sacred:** resonance (thesaurus/volume/inflection/novelty, 1.0–3.0×) is the #1 lever.
- **Normal-RPG balance:** bounded affinity (0.8–2.0-ish), defense, crit, statuses, progression,
  movesets — all the standard levers, just tuned so they never overwhelm chanting.
- **"Immune" → "0.8× (20% base reduction)"**: no blocked matchups/statuses anywhere; the worst case
  is a 20% damage reduction (and resisted-but-live statuses). This is already the design's floor;
  the retry likely wants it made the headline rule and everything re-tuned under it.

## Open items to resolve in the retry
- Re-run the I-3 (no useless ally) proof honestly at Basic/Adept/Master with the FLOOR + penetration
  model, across all 32 encounters + each legal fixed roster.
- Confirm penetration curve that keeps **knowledge (weakness pick) meaningful** vs skill:
  e.g. a Basic chant on a 2.0 weakness should still beat a mediocre chant on a resisted target.
- Keep weakness *discovery* + pocket-mode narration ("The Crypt Guard recoils — holy sears its bones!").
- Everything else (MP, defend-as-guard, statuses, drains, AOE number positions, movesets, boss
  phases, XP/level growth, C-lite pure `combat/` package, sim harness) stands as already designed.

## STATUS (post-approval): IMPLEMENTED Phase 2-4 (core doctrine)
- Approved model: affinity multiplies BASE only; resonance 1.0-3.0 is pure voice, never discounted.
  No penetration. "Immune" replaced by 20% base reduction (engine floor, content-proof).
- combat/ pure package implemented (AffinityTable/EnemyCodex/StatusCatalogTable/MovesetTable GENERATED
  from docs data + DamageResolver/StatusSystem/EnemyBrain/Progression hand-written).
- CombatViewModel rewired: MP cost enforced (soft fail), per-enemy affinity + defense + crit/variance,
  tier-gated statuses resisted-never-blocked, enemy movesets + cooldowns + boss phase unlocks,
  DRAIN/SUMMON/BUFF moves, guard+taunt, lifesteal, DoT begin-turn ticks (stun crawl, no deadlock),
  XP award to story-legal roster + level-ups into SavedCharacterStats, camp rest attrition (half heal;
  Midnight Vigil milestone keeps full restore), per-enemy/party floating-text slots, effectiveness +
  level-up + status narration for pocket mode.
- Tests: app/src/test/java/com/voicerpg/android/combat/* (resolver, status, progression, brain, doctrine).
  All ./gradlew test green.

## v1.6.1 — Economy actions (hero-only mana sustain)
- Doctrine: NOTHING ever modifies the resonance multiplier (flat 35% MP restore on Attune;
  sustain is separate from the voice axis).
- Aethel (all 4 hero classes) gains free 0-MP `Attune` (aliases: breathe/steady/concentrate/
  recover mana...): costs the turn, restores 35% max MP. Sweet spot sized so a MINIMUM-resonance
  party "barely survives" corridors by spending 1–2 turns/fight breathing while allies cast/heal.
- All heals party-wide with x0.65 potency split (Lay on Hands, Haste Cadence, Soothing Rain,
  Aegis). Exceptions kept single/personal: Zephyr's Umbral Siphon + hero Void Drain (drains).
- Not-enough-mana now soft-fails with a pocket-mode hint "say attune".
- Fixed the pre-existing tickAtb/summon lost-update race (SPEC #9) with atomic MutableStateFlow.update.
- NEXT: CombatSim headless harness to sweep attune%/costs/rest cadence across bands & chapters.

## v1.6.2 — Breath fixes
- Every member now owns a breath: Aethel "Attune", Cedric "Steady Breath", Lyra "Deep Root",
  Zephyr "Quiet Lungs" (all free, turn-cost, 35% max MP, resonance-independent).
- Restoration spells excluded from ALL IntentParser auto-pick fallbacks and from hero-by-spell
  turn steering -> "attune" acts on whoever's turn it is; garbled spell utterances fall back to
  the first REAL spell, never the breath.
- Breath actions sorted LAST in every kit for the same reason.
