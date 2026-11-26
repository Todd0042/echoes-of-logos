# echoes-of-logos: battle sprite brief

Generated from `art/sprite_roster.json` by `voice-rpg-engine/scripts/sprite_roster.py`; edit the roster, not this file.
Every fighter, enemy and boss is mapped to a sprite key below. A sheet named after its key, dropped into
`app/src/main/assets/game/sprites/sheets/`, appears in battle as soon as the game runs the sprite-ready engine.

**To draw: 0 sheets** (0 party fighters, 0 enemy families, 0 bosses). Everything else is a recolour of one of those.

## Style (every sprite)

- 16-bit SNES-era JRPG battle sprite, side view, facing RIGHT (the engine mirrors enemies to face the party).
- Match the delivered heroes and templates: limited palette (4-8 colours per sprite), 1 px dark outline, no anti-aliasing, transparent background.
- Cell 64x64 for regular enemies (the body about 40-56 px tall); bosses 96x96 or 128x128 as listed.
- Soles on the same row in every frame (row 58 of a 64 cell, 90 of 96, 122 of 128); floating creatures put their lowest point or hover shadow there. The feet slice's bottom edge is the sole line (scripts/sprite_lint.py checks it).
- Nothing but the character in a frame: no labels, frame names or text (sprite_lint removes them, but don't rely on it).
- Frame duration 190 ms unless a move needs faster frames; events in the tag user data, counted from 1 within the tag.

## Animations (Aseprite tags)

- **Party fighters**: idle (4f loop), ready (4f loop: turn ready, weapon up), charge (4f loop: speaking a chant), cast (5f, release=4), attack (5f, impact=4), hurt (4f), ko (6f, ends lying down), victory (4f loop). A fighter who is also fought as a boss adds windup, charge_ultimate, ultimate (and death).
- **Regular enemies**: required idle (4f loop), attack (5f, impact=4), hurt (4f); recommended windup (4f loop), cast (5f, release=4), death (6f; else the engine shatters it).
- **Bosses**: required idle (4f loop), windup (4f loop), attack (5f, impact=4), cast (5f, release=4), hurt (4f), charge_ultimate (4f loop), ultimate (6-8f, release=5); recommended death (6-8f), taunt (4f), p2_idle, p2_attack... (second phase).

## Delivering a sprite

1. Save `<key>.png` and its Aseprite JSON export `<key>.json` in `app/src/main/assets/game/sprites/sheets/`, with the tags above and a `feet` slice.
   Previews go in `art/sprite-previews/`, never in the sheets folder.
2. Run `python3 ../voice-rpg-engine/scripts/sprite_lint.py . --fix`, then `../voice-rpg-engine/scripts/sprite_coverage.sh <game>`.
3. Compare it with everyone else in the game's gallery, **SPRITES** tab.
4. A recolour (listed under *Recolours*) needs no art; a sheet named after its own id replaces the recolour if you want it to differ.

## Already drawn

- `hero`: Aethel (delivered)
- `cedric`: Sir Cedric (delivered)
- `lyra`: Lyra (delivered)
- `zephyr`: Zephyr (delivered)
- `template_brute`: Obsidian Brute family (delivered)
- `template_sniper`: Hooded Marksman family (delivered)
- `template_caster`: Hooded Occultist family (delivered)

## Enemy families (21, 64 px)

### `template_brute`: Obsidian Brute ✅ delivered

Hulking soldier in dark spiked plate with a heavy axe (delivered).

Draws: Iron Vanguard (Vanguard, flesh/physical)

Recolours:
- `gate_captain`: Gate Captain (Vanguard, flesh/physical): royal garrison captain: crimson tabard over the plate, gold trim, plumed helm
- `plaza_legionnaire`: Plaza Legionnaire (Vanguard, flesh/physical): Vaelor's garrison: glossy obsidian-black plate with violet lamp-glow highlights
- `archive_enforcer`: Archive Enforcer (Vanguard, flesh/physical): the guild's voiceless warden: dull grey plate, mouth sealed by a stitched leather strap
- `nullifier_warden`: Nullifier Warden (Vanguard, silence/physical): Malakor's gatekeeper: ash-grey and bone-white plate, silver mesh mouth-guard like Malakor's

### `template_sniper`: Hooded Marksman ✅ delivered

Crouched hooded crossbow marksman (delivered).

Recolours:
- `corrupted_archer`: Corrupted Archer (Sniper, flesh/physical): blighted hunter: sickly green-brown leathers, rot spots
- `castle_arbalest`: Castle Arbalest (Sniper, flesh/physical): royal arbalest: red and steel livery
- `obsidian_tracker`: Obsidian Tracker (Sniper, flesh/shadow): obsidian-black cloak with violet trim
- `mire_stalker`: Mire Stalker (Sniper, flesh/nature): swamp hunter: mossy green camouflage cloak, reeds in the hood

### `template_caster`: Hooded Occultist ✅ delivered

Dark hooded robed caster with a glowing staff (delivered).

Recolours:
- `void_shaman`: Void Shaman (Occultist, void/shadow): indigo robes, void-blue staff glow
- `court_warlock`: Court Warlock (Occultist, flesh/shadow): crimson and gold court robes
- `bone_acolyte`: Bone Acolyte (Summoner, undead/shadow): bone-white robes, skull mask, pale green glow
- `grave_archon`: Grave Archon (Occultist, undead/shadow): grave-pale robes with green soulfire
- `vault_watcher`: Vault Watcher (Occultist, void/shadow): grey robes, sealed lips, a hanging lantern
- `mote_drinker`: Mote Drinker (Occultist, void/shadow): deep-sea teal robes speckled with glowing stolen voice-motes
- `echo_of_the_guild`: Echo of the Guild (Occultist, void/shadow): translucent violet shade of a guild mage
- `vestibule_archivist`: Vestibule Archivist (Occultist, silence/shadow): ash-grey robes, chained book at the belt
- `chantry_cleric`: Chantry Cleric (Occultist, radiant/holy): white and gold vestments, golden censer glow
- `gilded_creditor`: Gilded Creditor (Occultist, radiant/holy): gold-leaf robes hung with coins

### `wisp`: Shadow Wisp ✅ delivered

Small floating shadow spirit, about 30 px tall: a ragged leafy body of black-green smoke, one glowing violet eye, small bat wings, a wispy tail instead of legs. Hovers with a faint shadow on the sole line (see portraits/shadow_wisp.jpg).

*Moves*: attack: darts forward and bites; cast: spits a violet shadow bolt.

Draws: Shadow Wisp (Sniper, void/shadow)

### `blighted_orc`: Blighted Orc ✅ delivered

Broad grey-green orc warrior covered in sickly blight boils, crude rusted cleaver, bone-and-hide armour, hunched shoulders.

*Moves*: attack: overhead cleaver chop.

Draws: Blighted Orc (Vanguard, flesh/physical)

Recolours:
- `blighted_stalker`: Blighted Stalker (Vanguard, flesh/physical): leaner tracker with a fur cloak and darker skin, a hunting knife in place of the cleaver (recolour is enough)

### `spider`: Brood Spider ✅ delivered

Low, wide giant arachnid (about 30 px tall, 56 px long) with razor foreclaws, a bloated abdomen, clusters of red eyes, glossy black-purple chitin dripping obsidian venom. Offspring of the Broodmothers.

*Moves*: attack: rears and slashes with the foreclaws; cast: spits a venom glob.

Draws: Brood Stalker (Sniper, flesh/physical)

Recolours:
- `corrupted_webweaver`: Corrupted Webweaver (Sniper, flesh/shadow): white web-silk draped over the body, pale violet sheen
- `toxic_hatchling`: Toxic Hatchling (Sniper, flesh/shadow): a hatchling: bright toxic green, drawn smaller (sprites.json scale 0.75)

### `assassin`: Black Guild Assassin ✅ delivered

Lithe masked assassin of the Black Guild: charcoal wrappings, a half-face mask, silver-threaded obsidian kunai, crouched forward stance (Zephyr's former brothers).

*Moves*: attack: dashing double slash; cast: throws a fan of kunai.

Draws: Shadowblade (Sniper, flesh/shadow)

Recolours:
- `black_guild_stalker`: Black Guild Stalker (Sniper, flesh/shadow): a poisoned blade dripping green
- `umbral_cutthroat`: Umbral Cutthroat (Sniper, void/shadow): void indigo wrappings with a faint blue glow
- `guild_garrote`: Guild Garrote (Vanguard, flesh/shadow): a garrote wire between the hands instead of kunai, heavier build
- `nocturne_lesson`: Nocturne Lesson (Sniper, void/shadow): a translucent violet shade, smoke trailing from the edges

### `skeleton`: Crypt Skeleton ✅ delivered

Skeletal legionnaire in a rusted half-helm and scraps of a purple burial tabard, notched sword and round shield.

*Moves*: attack: shield bash into a sword thrust.

Draws: Crypt Guard (Vanguard, undead/physical)

### `skeleton_archer`: Skeletal Sniper ✅ delivered

Skeleton archer with a long yew bow, tattered purple hood, a quiver of black-fletched arrows.

*Moves*: attack and cast: draws and looses an arrow (impact/release on the loose).

Draws: Skeletal Sniper (Sniper, undead/physical)

### `ghoul`: Silt Ghoul ✅ delivered

Hunched, mud-caked ghoul with long clawed arms, hollow glowing eyes, ragged burial cloth, dripping grey silt.

*Moves*: attack: lunging two-claw rake.

Draws: Silt Ghoul (Vanguard, undead/shadow)

Recolours:
- `drowned_husk`: Drowned Husk (Vanguard, undead/shadow): a drowned pilgrim: waterlogged pale-blue skin, seaweed and pilgrim beads
- `shrine_grasper`: Shrine Grasper (Sniper, undead/shadow): candle-wax drips on the shoulders, longer grasping arms

### `penitent_knight`: Penitent Templar ✅ delivered

Undead templar knight in cracked gilded plate with a sun emblem, bound in iron chains, a hollow-eyed helm leaking violet light; carries a greatsword (Galahault's fallen order).

*Moves*: attack: two-handed downward greatsword strike; cast: holy-violet flash from the sun emblem.

Draws: Penitent Templar (Vanguard, undead/holy)

Recolours:
- `penitent_vowbreaker`: Penitent Vowbreaker (Vanguard, undead/holy): darker tarnished plate, broken sword, heavier chains

### `wraith`: Spectral Reaper ✅ delivered

Floating hooded wraith in tattered purple-black robes with no legs, skeletal hands, a long scythe, cold violet eyes; hovers with its robe tips above the sole line.

*Moves*: attack: wide scythe sweep; cast: a spectral bolt from the hand.

Draws: Spectral Reaper (Sniper, undead/shadow)

Recolours:
- `umbral_ghost`: Umbral Ghost (Vanguard, void/shadow): void indigo, more translucent, no scythe-glint

### `automaton`: Clockwork Ironclad ✅ delivered

Clockwork knight automaton: riveted barrel chest, visible gears at the joints, glowing core behind a grille, tower shield and a piston mace; stiff, mechanical stance.

*Moves*: attack: piston-driven mace slam; cast: core vents a steam burst.

Draws: Iron Warden (Vanguard, construct/physical)

Recolours:
- `clockwork_phalanx`: Clockwork Phalanx (Vanguard, construct/physical): polished brass with a larger shield
- `bog_ironclad`: Bog Ironclad (Vanguard, construct/physical): moss and rust, swamp weed caught in the gears
- `aqueduct_sentinel`: Aqueduct Sentinel (Vanguard, construct/physical): verdigris bronze with razor claws in place of the mace
- `citadel_ironclad`: Citadel Ironclad (Vanguard, construct/physical): obsidian-black plating, red core
- `obsidian_warden`: Obsidian Warden (Vanguard, construct/physical): obsidian-black plating, violet core

### `clockwork_tripod`: Clockwork Tripod ✅ delivered

Three-legged clockwork walker with a turret body and a mounted steam ballista; brass boiler, smoking chimney.

*Moves*: attack and cast: the ballista recoils and fires a bolt.

Draws: Steam Arbalest (Sniper, construct/physical)

Recolours:
- `tripod_scorcher`: Tripod Scorcher (Sniper, construct/pyromancy): a flame nozzle in place of the ballista, orange-hot boiler

### `obelisk`: Walking Obelisk ✅ delivered

A rune-carved stone monolith on stubby stone legs, runes glowing as it gathers power, a single eye-rune near the top.

*Moves*: attack: topples forward and slams; cast: fires a beam from the eye-rune.

Draws: Walking Obelisk (Occultist, construct/physical)

Recolours:
- `celestial_spire`: Celestial Spire (Occultist, radiant/holy): white-gold crystal spire, golden runes, no legs (floats)
- `gallery_of_doubts`: Gallery of Doubts (Vanguard, silence/shadow): a mirror-faced obelisk in a silver frame, the party's doubts flickering in the glass

### `leech`: Trench Leech ✅ delivered

A blind giant leech rearing up from the floor, segmented slick body, a round toothed maw, knotted with faintly glowing motes of stolen voices.

*Moves*: attack: lunges and latches with the maw; cast: spits a glob.

Draws: Chorus Leech (Sniper, void/shadow)

Recolours:
- `marsh_leech`: Marsh Leech (Sniper, flesh/nature): swamp brown-green
- `umbral_leech`: Umbral Leech (Vanguard, void/shadow): void indigo

### `thorn_druid`: Thorn Choir Druid ✅ delivered

Antlered forest druid in a cloak of leaves and thorns, a briar staff, glowing green eyes; corrupted guardian of the grove.

*Moves*: cast: raises the briar staff and lashes thorns forward.

Draws: Canopy Warden (Occultist, verdant/nature)

Recolours:
- `thorn_choir_singer`: Thorn Choir Singer (Occultist, verdant/nature): glass-vine cloak, mouth open in song, obsidian venom streaks
- `void_briar_binder`: Void Briar Binder (Occultist, void/nature): void-tainted purple thorns, Inquisition chains on the staff

### `treant`: Glassvine Colossus ✅ delivered

A massive walking golem of braided vines and bark (fills most of the cell), crystalline glass shards growing from the vines, glowing green heart in the chest.

*Moves*: attack: a heavy two-armed vine slam.

Draws: Glassvine Colossus (Vanguard, verdant/nature)

Recolours:
- `thornvined_guardian`: Thornvined Guardian (Vanguard, verdant/nature): thorny bark, no glass, a moss crown

### `tendril`: Void Tendril ✅ delivered

A single thick tentacle rising out of a dark pool on the floor, suckers along its length, a hooked tip, dripping liquid silence.

*Moves*: attack: whips down and lashes; cast: flings a glob of void.

Draws: Void Tendril (Sniper, void/shadow)

Recolours:
- `root_lash`: Root Lash (Sniper, verdant/nature): a gnarled green root rising from moss instead of a pool

### `archon`: Archon Custodian ✅ delivered

Golden armoured celestial sentinel with folded light-wings, a faceless gold helm, a halberd of solidified harmonic light.

*Moves*: attack: halberd thrust; cast: a ring of light from the wings.

Draws: Archon Custodian (Vanguard, radiant/holy)

Recolours:
- `glinted_vow_clade`: Glinted Vow Clade (Vanguard, radiant/holy): a gilded reflection: polished mirror-gold, coins in the wing feathers

### `nullifier`: Echo Nullifier ✅ delivered

Silent Inquisition enforcer: grey high-collared coat, a stitched leather mask with no mouth, sound-dampening discs on a bandolier.

*Moves*: attack and cast: hurls a humming silencing disc.

Draws: Echo Nullifier (Sniper, silence/physical)

Recolours:
- `nullifier_sibilant`: Nullifier Sibilant (Sniper, silence/physical): paler grey, a hissing silver mouth-grille

## Mirror shadows (no art needed)

Dark violet recolours of the party sheets, generated by the script: `shadow_of_the_knight` ← `cedric`, `shadow_of_the_warden` ← `lyra`, `shadow_of_the_blade` ← `zephyr`, `shadow_of_the_invocator` ← `hero`.

## Bosses (10 to draw, each its own sprite)

### `grave_broodmother`: Grave Broodmother (128 px) ✅ delivered

Colossal arachnid queen crawling from a vaulted ceiling: bone-white carapace etched like grave stones, a swollen egg sac with hatchlings clinging to her back, eight barbed legs, a crown of eyes.

*Moves*: attack: Bite (lunging fangs); cast: Spawn Hatchlings (the egg sac bursts); charge_ultimate / ultimate: Venomous Brood Cataclysm (rears up, venom raining from her abdomen).

Element: flesh/shadow.

### `blighted_broodmother`: Blighted Broodmother (128 px)

The Grave Broodmother corrupted: glossy obsidian chitin dripping black venom, glowing green blight veins. A recolour of grave_broodmother is enough (deliver blighted_broodmother.json only if it should differ in shape).

*Moves*: as Grave Broodmother.

Element: flesh/shadow. A recolour of `grave_broodmother` (no art needed unless it should differ).

### `bog_behemoth`: Bog Behemoth (128 px) ✅ delivered

A towering swamp titan of mud, roots and rotting willow bark, moss hanging from its shoulders, glowing corrosive-green eyes and maw, one arm a club of tangled roots.

*Moves*: attack: Trample (a stomp); cast: Bog Breath (acid breath); charge_ultimate / ultimate: Primeval Tectonic Tremor (both fists into the ground, the floor splitting).

Element: verdant/nature.

### `ancient_mire_wyrm`: Ancient Mire Wyrm (128 px) ✅ delivered

An ancient serpentine wyrm rising out of the silt (lower body submerged at the sole line), mud-plated scales, a fanned crest, corrosive yellow eyes.

*Moves*: attack: Tail Lash; cast: Silt Breath; charge_ultimate / ultimate: Acidic Silt Torrent (rears high, a torrent of acid silt from the jaws).

Element: flesh/nature.

### `executioner_kaelen`: Grand Executioner Kaelen (96 px) ✅ delivered

A massive hooded executioner of the Inquisition: black leather hood, bare scarred arms, a blood-red sash, twin heavy execution axes; Black Guild sigil on the belt.

*Moves*: attack: Executioner Cut (cross-chop with both axes); cast: Shadow Fan (hurls a fan of shadow blades); Blink Strike (vanishes in smoke and reappears); charge_ultimate / ultimate: Headsman's Crimson Guillotine (axes raised overhead, a crimson arc).

Element: flesh/shadow.

### `sir_galahault`: Sir Galahault, the Obsidian Grandmaster (96 px) ✅ delivered

Petrified golden paladin (portraits/galahault.jpg): golden plate cracked through with black obsidian veins, a sun emblem on the breastplate, a plumed golden helm, a greatsword; tattered golden cape.

*Moves*: attack: Oathbreaker Smite; cast: Dawn Judgment (sun emblem flares); Petrified Aegis (crouch behind the sword, stone creeping over the gold); charge_ultimate / ultimate: Eclipse of the Corrupted Dawn (sword raised, the sun emblem eclipsed black).

Element: undead/holy.

### `master_nocturne`: Master Nocturne (96 px) ✅ delivered

Spymaster of the Mute (portraits/nocturne.jpg): tall, deep purple hood and laced dark leathers, pale face, violet smoke curling around him, many hidden blades.

*Moves*: attack: Silent Kill (vanishing lunge); cast: Murder of Blades (a spray of kunai); charge_ultimate / ultimate: Thousand Blade Umbral Requiem (rises into smoke, blades orbiting him).

Element: flesh/shadow.

### `warmaster_ouros`: Clockwork Warmaster Ouros (128 px) ✅ delivered

Iron Bastion Sovereign (portraits/ouros.jpg): a towering crowned brass-and-iron automaton king, glowing orange eyes, gears and steam pipes on the shoulders, a gear-toothed greataxe.

*Moves*: attack: Gear Cleave; Overdrive (vents steam, core glowing); cast: Bellow of Iron (chest grille opens, electric shockwave); charge_ultimate / ultimate: Omega Core Cataclysm Beam (core opens, a beam from the chest).

Element: construct/physical.

### `commander_vaelor`: Commander Vaelor, the Voice-Thief (96 px) ✅ delivered

Hand of Malakor (portraits/vaelor.jpg): grey-haired commander in black spiked plate and a black cape, a longsword and the Void Horn hanging at his belt.

*Moves*: attack: Standard Charge (shoulder charge with the banner-sword); cast: Void Horn Blast (raises the horn); Summon Spectral Legion; charge_ultimate / ultimate: Phantasmal Vanguard Hellstorm (horn to lips, spectral legion swirling).

Element: flesh/shadow.

### `abyssal_leviathan`: Abyssal Leviathan (128 px) ✅ delivered

A colossal void leviathan breaching from a lake of liquid silence (body below the sole line hidden in dark water), an armoured head with a huge maw, void tendrils lashing from its sides, star-like pale eyes.

*Moves*: attack: Tidal Maw (lunging bite); cast: Drowning Silence (tendrils spread, sound draining); Void Spawn; charge_ultimate / ultimate: Abyssal Trench Tsunami (rears into a towering wave).

Element: void/shadow.

### `grand_inquisitor_malakor`: Grand Inquisitor Malakor, the Mute Sovereign (96 px) ✅ delivered

The final boss (portraits/malakor.jpg): pale face with red eyes, slicked black hair, a silver mesh mouth-guard, a towering high collar, black and gold rune-embroidered robes over armour, spiked pauldrons.

*Moves*: attack: Sever Echo; cast: Nullifying Word / Choir of Silence (a gesture, silencing sigils); charge_ultimate / ultimate: Cords of Severance (below a third of his health: silver cords lash out from the mouth-guard). Please include a second phase (p2_idle, p2_attack, p2_cast): the mouth-guard torn away, levitating, robes streaming upward.

Element: silence/shadow.
