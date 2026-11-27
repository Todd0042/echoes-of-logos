# Enemy Weaknesses, Resistances & Armor

Per-enemy defenses for all **79 enemies** in [`enemies.json`](../app/src/main/assets/game/enemies/enemies.json)
(engine E6, [`COMBAT_PROGRESSION_PLAN.md`](https://github.com/Todd0042/voice-rpg-engine/blob/main/docs/COMBAT_PROGRESSION_PLAN.md) section 3.6).

## How it works

- **`weak`**: that school hits for **x2.0**, overriding the family table and the enemy's own element.
- **`resist`**: that school hits for **x0.8**, overriding the family table. Nobody is immune: 0.8 is the floor.
- Every other school uses the family table in [`affinity_matrix.json`](../app/src/main/assets/game/combat/affinity_matrix.json)
  (a school equal to the enemy's `selfElement` drops to 0.8). Some foes keep a family x2 on top of their own
  weakness (Verdant x2 to fire, Construct x2 to lightning, Undead x2 to holy); the reasons below say so where it matters.
- **`armor`**: flat damage taken off every hit after defense (omitted = 0). `piercing` spells ignore it;
  **no Echoes spell is `piercing` yet**, so armor is currently a straight reduction for everyone.
- Weaknesses are learned by hitting (the "Weak!" callout) and remembered in the save's bestiary.

Design rules followed: every boss has one clear weakness and a resistance; weaknesses follow each enemy's
name, story text, family and element, and vary inside a family (the 17 Void foes use six different weaknesses, the 24 Flesh foes five)
so the player learns per enemy, not per family. HP, attack, defense, stories and spells are unchanged.

## Bosses

| Boss | Chapter | Weak | Resist | Armor |
|---|---|---|---|---|
| Grave Broodmother | 3 | Holy | Shadow | - |
| Bog Behemoth | 6 | Electromancy | Nature | 4 |
| Ancient Mire Wyrm | 7 | Holy | Electromancy | 4 |
| Executioner Kaelen | 8 | Nature | Shadow | 4 |
| Sir Galahault | 9 | Shadow | Holy | 6 |
| Blighted Broodmother | 10 | Pyromancy | Nature | - |
| Master Nocturne | 11 | Electromancy | Shadow | - |
| Warmaster Ouros | 12 | Cryomancy | Pyromancy | 12 |
| Commander Vaelor | 13 | Pyromancy | Nature | 6 |
| Abyssal Leviathan | 14 | Holy | Pyromancy | - |
| Grand Inquisitor Malakor | 16 | Holy | Shadow | - |

The Bog Behemoth also takes x2 from fire (Verdant) and Warmaster Ouros x2 from lightning (Construct); their own
weakness is the one to learn. Who can hit each boss's weakness: Holy is always Cedric; Nature is Lyra (chapter 6 on);
Shadow is Zephyr (chapter 8 on) or a Shadowweaver Aethel; fire, frost and lightning come from Aethel's class.

## All enemies (in story order)

| Enemy | First fight | Family | Weak (x2.0) | Resist (x0.8) | Armor | Why |
|---|---|---|---|---|---|---|
| Shadow Wisp | `prologue_solo` | Void | Pyromancy, Shadow | Cryomancy | - | Obsidian-glass husk of a stolen voice: flame shatters the glass (as the prologue says) and an umbral siphon drinks the voice out; born of the cold mist. |
| Blighted Orc | `forest_ambush` | Flesh | Pyromancy | Shadow | 3 | Blight-rotted hide dries and burns; the corruption that made him drinks shadow. Scrap plate. |
| Corrupted Archer | `forest_ambush` | Flesh | Electromancy | Pyromancy | - | Wiry sniper on a taut string: a jolt spoils his aim; his rain-soaked bog cloak won't catch. |
| Void Shaman | `forest_ambush` | Void | Holy | Electromancy | - | A priest of the Silence: dawn-light unmakes his rites; the void he channels swallows the spark. |
| Obsidian Tracker | `blight_trackers` | Flesh | Holy | Shadow | - | Inquisition hunter in black glass: light floods straight through it; at home in the dark. |
| Blighted Stalker | `blight_trackers` | Flesh | Pyromancy | Electromancy | - | Rotting blight-flesh burns; leathery hide shrugs off shock. |
| Aqueduct Sentinel | `ch3_sentinels` | Construct | Electromancy | Pyromancy | 8 | The sentinels' acoustic flaw: lightning's crack rings the hollow bronze apart; water-cooled, it shrugs off fire. |
| Brood Stalker | `ch3_sentinels` | Flesh | Holy | Cryomancy | - | A cave-dweller of the dark brood: light blinds and burns it; used to the cave cold. |
| **Grave Broodmother** (boss) | `cave_broodmother` | Flesh | Holy | Shadow | - | A grave-queen nesting in the sun-gate: the light the gate was built for burns her ("cleanse the gate"); a queen of the dark brood, shadow only feeds her. |
| Crypt Guard | `dungeon_descent` | Undead | Holy | Electromancy | 4 | Consecrated dead risen wrong; dead nerves give lightning nothing to jolt. Rusted tomb-plate. |
| Skeletal Sniper | `dungeon_descent` | Undead | Pyromancy | Cryomancy | - | Dry bones and gut bowstring go up like kindling; already grave-cold. |
| Bone Acolyte | `dungeon_descent` | Undead | Holy | Pyromancy | - | A grave-priest: holy light breaks its summoning; it wards itself in the violet grave-embers of the catacombs. |
| Iron Vanguard | `castle_horde` | Flesh | Electromancy | Pyromancy | 5 | Iron plate carries the shock to the man inside and sheds flame. |
| Gate Captain | `castle_horde` | Flesh | Holy | Shadow | 6 | A Solarian officer who broke his oath: the dawn he swore to still burns him; he has already given himself to the dark. |
| Castle Arbalest | `castle_horde` | Flesh | Pyromancy | Cryomancy | - | Wooden crossbows and powder crews burn; wind-bitten tower crews in furs shrug off frost. |
| Court Warlock | `castle_horde` | Flesh | Holy | Pyromancy | - | A court sorcerer of the Silence: radiance tears his wards; his fire-warded robes don't catch. |
| Bog Ironclad | `marsh_rescue` | Construct | Cryomancy | Pyromancy | 8 | Bog-rusted plate with marsh water in every seam: frost cracks it open; too sodden to burn. |
| Mire Stalker | `marsh_rescue` | Flesh | Cryomancy | Electromancy | - | Cold-blooded marsh hunter, sluggish in frost; mud-caked hide grounds lightning. |
| Void Briar Binder | `marsh_rescue` | Void | Pyromancy | Electromancy | - | Void-briars are still thorns and burn; the briars ground the spark like any bramble. |
| **Bog Behemoth** (boss) | `swamp_behemoth` | Verdant | Electromancy | Nature | 4 | Waterlogged bulk conducts lightning (and its moss still burns, x2 by family); it IS the bog, so Lyra's roots slide off. Moss armor. |
| Marsh Leech | `swamp_behemoth` | Flesh | Holy | Shadow | - | Leeches shrivel and flee from bright light; it drinks the dark marsh water. |
| **Ancient Mire Wyrm** (boss) | `ch7_mire_wyrm` | Flesh | Holy | Electromancy | 4 | An ancient marsh guardian bent by the Blight's iron law (the chapter 7 steles): consecrated light burns the blight out, leaving jade moss; its silt core grounds lightning. Scaled hide. |
| Silt Ghoul | `ch7_mire_wyrm` | Undead | Nature | Electromancy | - | Silt and rot: Lyra's roots draw the marsh out of it; the silt grounds lightning. |
| **Executioner Kaelen** (boss) | `ch8_executioner_ambush` | Flesh | Nature | Shadow | 4 | A heavy-footed headsman: Lyra's briars root him in place; the Inquisition's executioner of the dark resists shadow. Executioner's plate. |
| Shadowblade | `ch8_executioner_ambush` | Flesh | Holy | Shadow | - | Elite of the dark: light leaves it nowhere to hide; shadow is its element. |
| Penitent Vowbreaker | `ch9_penitent_gate` | Undead | Shadow | Holy | 5 | A vowbreaker: darkness corrodes broken oaths; corrupted holy vows turn holy light. Penitent's plate. |
| Grave Archon | `ch9_penitent_gate` | Undead | Holy | Nature | - | An archon of the grave: holy light unmakes it; it commands the grave-earth, so roots answer to it. |
| **Sir Galahault** (boss) | `ch9_galahault_trial` | Undead | Shadow | Holy | 6 | A paladin whose oath broke: darkness corrodes oaths (Zephyr's or a Shadowweaver's strike finds the crack); still clad in the dawn he betrayed, so holy light is resisted. Corrupted plate. |
| Penitent Templar | `ch9_galahault_trial` | Undead | Nature | Holy | 4 | A dead penitent knight: the grave-earth claims its own; his corrupted holy vows still turn holy light. |
| Chantry Cleric | `ch9_galahault_trial` | Radiant | Shadow | Holy | - | A chantry priest of the Silence's light: darkness corrodes the radiance; holy light is its own. |
| Glassvine Colossus | `ch10_thicket_guardians` | Verdant | Cryomancy | Nature | 6 | Glass-veined vines shatter in the cold (and its wood still burns, x2 by family); grove-born, roots feed it. Glass bark. |
| Thorn Choir Singer | `ch10_thicket_guardians` | Verdant | Holy | Cryomancy | - | A corrupted choir: the Breviary Hymn's holy light breaks its false song (its thorns still burn x2 by family); evergreen and winter-hardy. |
| **Blighted Broodmother** (boss) | `ch10_broodmother_trial` | Flesh | Pyromancy | Nature | - | Webs and blight-silk go up like tinder; she has drunk the grove's poisoned spring, so the grove's roots feed her. |
| Corrupted Webweaver | `ch10_broodmother_trial` | Flesh | Pyromancy | Electromancy | - | Its webs burn; the silk insulates against shock. |
| Toxic Hatchling | `ch10_broodmother_trial` | Flesh | Holy | Nature | - | Newborn blight: light burns it before its venom sets; born of the grove's poisoned spring, nature's venom feeds it. |
| Black Guild Stalker | `ch11_gorge_stalkers` | Flesh | Holy | Shadow | - | Light strips away the shadows a Guild stalker hides in; shadow is his trade. |
| Guild Garrote | `ch11_gorge_stalkers` | Flesh | Electromancy | Shadow | 3 | His steel garrote wire carries the shock to his hands; shadow is his trade. Mail under the cloak. |
| Archive Enforcer | `ch11_archive_enforcers` | Flesh | Pyromancy | Electromancy | 5 | Oil-soaked archive robes over plate: fire catches; the plate is grounded to the archive floor. Plate. |
| Vault Watcher | `ch11_archive_enforcers` | Void | Electromancy | Cryomancy | - | A watching eye: a flash of lightning blinds it; vault-cold. |
| **Master Nocturne** (boss) | `ch11_nocturne_trial` | Flesh | Electromancy | Shadow | - | Hides among shadow duplicates: a flash of lightning shows the true body; he taught the Guild's shadow arts, so he reads every umbral strike. |
| Umbral Cutthroat | `ch11_nocturne_trial` | Void | Holy | Cryomancy | - | An umbral thing: dawn-light unmakes it; it waits in the cold dark. |
| Iron Warden | `ch12_automaton_patrol` | Construct | Electromancy | Pyromancy | 10 | Iron automaton: shock fries its works; forge-tempered iron sheds fire. Heavy plating. |
| Tripod Scorcher | `ch12_automaton_patrol` | Construct | Cryomancy | Pyromancy | 6 | A fire-spitting tripod: frost cracks its hot barrels; it IS fire. Iron housing. |
| Clockwork Phalanx | `ch12_warmaster_ouros` | Construct | Nature | Holy | 10 | Roots and vines jam clockwork gears (shock still hits x2 by family); no soul for holy light to judge. Shield-wall plating. |
| **Warmaster Ouros** (boss) | `ch12_warmaster_ouros` | Construct | Cryomancy | Pyromancy | 12 | Superheated boilers crack under frost (chapter 12 freezes its valves; shock still hits x2 by family); furnace-born, fire only stokes it. Heaviest plating in the game. |
| Steam Arbalest | `ch12_warmaster_ouros` | Construct | Cryomancy | Pyromancy | 6 | Steam-driven: frost seizes the boiler; furnace-hot, it shrugs off fire. Iron housing. |
| Plaza Legionnaire | `ch13_plaza_legion` | Flesh | Holy | Shadow | 5 | Capital legionnaires turned by the void: the light calls them home; the dark holds them. Legion plate. |
| Walking Obelisk | `ch13_plaza_legion` | Construct | Nature | Electromancy | 10 | Rune-stone on legs: roots split stone; stone doesn't conduct. Stone body. |
| Citadel Ironclad | `ch13_commander_vaelor` | Construct | Electromancy | Cryomancy | 10 | Iron citadel guard: shock runs through it; the cold iron of the north gate shrugs off frost. Heavy plating. |
| Obsidian Warden | `ch13_commander_vaelor` | Construct | Pyromancy | Shadow | 10 | Obsidian construct: golden fire cracks black glass; glazed in the void's dark. Heavy plating. |
| **Commander Vaelor** (boss) | `ch13_commander_vaelor` | Flesh | Pyromancy | Nature | 6 | Black-glass and iron plate: golden fire cracks black glass (chapter 13); obsidian greaves no root can grip. Commander's plate. |
| Spectral Reaper | `ch13_commander_vaelor` | Undead | Holy | Nature | - | A reaper spirit: holy light banishes it; no body for roots to grip. |
| Drowned Husk | `ch14_shallows_husks` | Undead | Electromancy | Cryomancy | - | Waterlogged corpse: water carries the shock through it; drowned in the cold deep. |
| Shrine Grasper | `ch14_shallows_husks` | Undead | Holy | Pyromancy | - | A dead thing clinging to a drowned shrine: the shrine's light burns it; shrine-water quenches fire. |
| Umbral Leech | `ch14_trench_leeches` | Void | Pyromancy | Shadow | - | Fire makes a leech let go; it drinks the dark. |
| Chorus Leech | `ch14_trench_leeches` | Void | Electromancy | Nature | - | A swollen sac of stolen songs: a jolt bursts it; no living flesh for roots to grip. |
| Mote Drinker | `ch14_trench_leeches` | Void | Holy | Cryomancy | - | It drinks voice-motes of light, and too much light bursts it; the trench is cold. |
| **Abyssal Leviathan** (boss) | `ch14_abyssal_leviathan` | Void | Holy | Pyromancy | - | Dweller of the black reservoir: voice-light holds its dark at bay (chapter 14); fire drowns in the reservoir. |
| Void Tendril | `ch14_abyssal_leviathan` | Void | Cryomancy | Pyromancy | - | Frost freezes a tendril stiff and brittle; like its master, fire drowns in the reservoir water. |
| Archon Custodian | `ch15_archon_custodians` | Radiant | Shadow | Holy | 8 | Radiant warden of the last trial: darkness corrodes radiance; holy light is its own. Celestial armor. |
| Celestial Spire | `ch15_archon_custodians` | Radiant | Electromancy | Holy | 6 | A walking spire is a lightning rod; holy light is its own. Stone body. |
| Gilded Creditor | `ch15_trial_gold` | Radiant | Nature | Holy | - | A wraith bound to gilt coin: verdigris and creeping moss eat the gilt; it wears stolen temple gold. |
| Glinted Vow Clade | `ch15_trial_gold` | Radiant | Shadow | Holy | 6 | Vow-bound radiance: darkness corrodes vows; holy light is its own. Glinted plate. |
| Thornvined Guardian | `ch15_trial_grove` | Verdant | Pyromancy | Nature | 6 | Thornvine burns (x2); it is the grove, so roots feed it. Bark armor. |
| Root Lash | `ch15_trial_grove` | Verdant | Shadow | Nature | - | Roots grown in false sunlight wither in darkness; roots feed roots. |
| Canopy Warden | `ch15_trial_grove` | Verdant | Electromancy | Cryomancy | - | The tallest thing in the grove: lightning finds the canopy; evergreen, frost-hardy. |
| Nocturne Lesson | `ch15_trial_shadow` | Void | Electromancy | Shadow | - | An echo of Nocturne's teaching carries his flaw: a flash shows the true body; shadow is the lesson. |
| Umbral Ghost | `ch15_trial_shadow` | Void | Holy | Nature | - | A ghost: holy light banishes it; no body for roots to grip. |
| Echo of the Guild | `ch15_trial_shadow` | Void | Pyromancy | Shadow | - | An echo of the Black Guild: firelight throws it into stark relief; shadow is its trade. |
| Nullifier Warden | `ch16_nullifier_gate` | Silence | Pyromancy | Electromancy | 8 | Black-glass plate cracks under fire; grounded to the vestibule floor. Heavy plating. |
| Nullifier Sibilant | `ch16_nullifier_gate` | Silence | Electromancy | Cryomancy | - | A hissing nullifier: lightning's crack overloads its hush; cold as the Spire. |
| Vestibule Archivist | `ch16_nullifier_gate` | Silence | Holy | Shadow | - | Keeper of erased words: the dawn reads them back aloud; shadow is the Silence's ink. |
| Shadow of the Knight | `ch16_mirror_gauntlet` | Void | Shadow | Holy | 6 | Cedric's doubt: a reflection resists its original's voice (holy), but a friend's breaks it: Zephyr's darkness corrodes the false oath. Knightly plate. |
| Shadow of the Warden | `ch16_mirror_gauntlet` | Void | Pyromancy | Nature | - | Lyra's doubt resists her roots; the Invocator's flame burns the false grove. |
| Shadow of the Blade | `ch16_mirror_gauntlet` | Void | Holy | Shadow | - | Zephyr's doubt resists his shadow; Cedric's light leaves it nowhere to hide. |
| Shadow of the Invocator | `ch16_mirror_gauntlet` | Void | Nature | Electromancy | - | Aethel's doubt resists the storm it mirrors; Lyra's roots hold it fast. |
| Gallery of Doubts | `ch16_mirror_gauntlet` | Silence | Electromancy | Shadow | 8 | A wall of mirrors: thunder's crack shatters the glass; doubt is woven of shadow. Mirror-wall. |
| **Grand Inquisitor Malakor** (boss) | `ch16_malakor_finale` | Silence | Holy | Shadow | - | The Mute Sovereign of stillness cannot bear a dawn-song; shadow is the Silence's own tongue. |
| Echo Nullifier | `ch16_malakor_finale` | Silence | Electromancy | Nature | - | Its nullifying hum overloads when lightning cracks through it; a thing of glass, no root grips it. |

## Coverage check

For every encounter in [`encounters.json`](../app/src/main/assets/game/encounters/encounters.json), with its
`party`, for each of Aethel's 4 classes, the party's damaging schools at that point of the story were checked
against every enemy in the fight. Damaging = `basePower > 0` and not a heal or guard (so Arcane Barrier, Shield Wall
and Haste Cadence don't count). Kits by story point:

| Member | Before `ch3_sentinels_complete` | Until `ch7_wyrm_defeated` | After |
|---|---|---|---|
| Aethel, Battlemage | Pyromancy | Pyromancy | Pyromancy, Electromancy |
| Aethel, Chanter | Electromancy | Electromancy | Electromancy, Pyromancy |
| Aethel, Elementalist | Pyromancy | Pyromancy, Cryomancy | + Electromancy |
| Aethel, Shadowweaver | Shadow | Shadow | Shadow |
| Cedric (from `forest_ambush`) | Holy | Holy | Holy |
| Lyra (`swamp_behemoth` on) | | Nature | Nature |
| Zephyr (joins mid-fight in `ch8_executioner_ambush`, full party from chapter 9) | | | Shadow |

**Required: every enemy has at least one party school at x1.0 or better.** Met for all 32 encounters x 4 classes.

**Ideal: a weakness someone in the party can hit.** Met in every encounter for every class except the two listed below.

### Gaps found and fixed

With the family table alone (before this pass) there were **2 hard gaps** and 46 class/encounter pairs with no
weakness to hit at all:

1. **Prologue, Shadowweaver vs Shadow Wisps** (solo fight): Shadow is x0.8 on Void and on a Shadow-element foe, and
   a Shadowweaver has nothing else. Fixed by giving the wisp **Pyromancy and Shadow** weaknesses (the prologue's own
   line: "the flame shattered their obsidian bodies"; the umbral siphon drinks the stolen voice out). This is the one
   deliberate oddity in the table: a "Shadow" wisp that is weak to Shadow.
2. **Chapter 6, Chanter vs Bog Behemoth**: Verdant resists lightning, holy and nature, which is the Chanter's whole
   party. Fixed with an **Electromancy** weakness (a waterlogged bulk conducts lightning).

Fixed while drafting (choices that would have left a class without a weakness to learn):

- A fire resistance on the Bog Behemoth ("too sodden to burn") would have left a chapter 6 Battlemage at x0.8
  everywhere: dropped; it resists Nature instead.
- Grave Broodmother and Ancient Mire Wyrm: a fire / frost weakness only 1-2 classes could hit; both are **Holy**
  (Cedric is always there), so every player can learn them.
- Marsh Leech, Brood Stalker, Toxic Hatchling, Black Guild Stalker, Thorn Choir Singer: moved to **Holy** weaknesses so
  the Shadowweaver party (Shadow, Holy, Nature) has a target in chapters 3, 6, 10 and 11.
- Clockwork Phalanx: **Nature** (roots jam the gears) so the chapter 12 Ouros fight has a target for every class.

### Remaining gaps (accepted)

- **Prologue, Chanter vs Shadow Wisps**: neutral (Electromancy x1.0), no weakness. A third weakness on a 160 HP
  tutorial wisp would be noise.
- **Chapter 12 patrol (`ch12_automaton_patrol`), Shadowweaver**: Iron Warden (lightning) and Tripod Scorcher (frost);
  the party hits both at x1.2 (Shadow on Construct) but has no weakness to hit.
- Structurally, a Shadowweaver Aethel and Zephyr share one school, so that party never reaches weaknesses to fire,
  frost or lightning (20 enemy/fight pairs); it always has a neutral-or-better school and a weakness elsewhere in the
  fight. Early Chapter 1-5 fights also leave some single enemies without a reachable weakness for one or two classes
  (e.g. the Corrupted Archer is lightning-weak: only a Chanter hits it), which is the intended "use a different spell
  on a different foe" choice.
