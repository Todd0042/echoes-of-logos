# Echoes of the Logos — Story Background Generation Prompts

This document outlines all scene background assets required by **Echoes of the Logos** for both **Landscape (16:9)** and **Portrait (9:16)** orientations.

The engine's `StoryAssetLoader` dynamically detects device orientation:
- **Landscape**: Loads `story/<scene_name>.jpg`
- **Portrait**: Looks for `story/<scene_name>_p.jpg`; if absent, gracefully falls back to the landscape image.

Adding `_p.jpg` variants across all scenes provides a seamless, immersive visual experience in both phone orientations without requiring any code or JSON changes.

---

## Technical Specifications & Art Direction

### Dimensions & Ratios
- **Landscape (`.jpg`)**: Aspect Ratio **16:9** (Recommended: `1920×1080` or `1280×720`, JPG quality 85–90%).
- **Portrait (`_p.jpg`)**: Aspect Ratio **9:16** (Recommended: `1080×1920` or `720×1280`, JPG quality 85–90%).

### Core Art Style Guidelines
- **Genre & Tone**: Romantic high fantasy JRPG matte painting, blending classic 16/32-bit nostalgia (*Chrono Trigger*, *Final Fantasy VI / IX*, *Octopath Traveler*) with lush painterly backgrounds.
- **Compositional Neutrality**: **Do NOT render main characters, party members, or text** in the artwork. Character sprites, dialogue bubbles, and busts are rendered dynamically by the engine on top of the background.
- **UI Breathing Room**:
  - In **Landscape**, keep central points of interest slightly elevated or offset to account for bottom-docked command consoles and dialogue panels.
  - In **Portrait**, emphasize verticality (towering canopies, colossal arches, soaring spires, deep ravines) while keeping the lower 35% visually uncluttered so narrative text cards remain legible.
- **Recommended Negative Prompt**:
  `characters, people, anime figures, text, watermark, signature, modern elements, interface, blurry, oversaturated, low quality, deformed, photographic noise, cars, modern buildings`

---

## Quick Asset Checklist

| Scene ID | Location Name | Landscape Asset | Portrait Asset |
|---|---|---|---|
| `scene_cottage` | Aethel's Cottage | `story/cottage_bedroom.jpg` | `story/cottage_bedroom_p.jpg` |
| `scene_village` | Whispering Pines (Blighted) | `story/village_square.jpg` | `story/village_square_p.jpg` |
| `scene_crossroads` | Sun Shrine Crossroads | `story/forest_crossroads.jpg` | `story/forest_crossroads_p.jpg` |
| `scene_camp` | Fellowship Campfire | `story/fellowship_camp.jpg` | `story/fellowship_camp_p.jpg` |
| `scene_cave` | Whispering Caverns | `story/caverns.jpg` | `story/caverns_p.jpg` |
| `scene_swamp` | The Rotting Marsh | `story/rotting_marsh.jpg` | `story/rotting_marsh_p.jpg` |
| `scene_aqueduct` | Aqueducts of Solaria | `story/aqueducts.jpg` | `story/aqueducts_p.jpg` |
| `scene_dungeon` | Crypt of the Foundation | `story/foundation_crypt.jpg` | `story/foundation_crypt_p.jpg` |
| `scene_tower` | Solaria Bell Chamber | `story/bell_chamber.jpg` | `story/bell_chamber_p.jpg` |
| `scene_marsh_fane` | The Drowned Fane | `story/marsh_fane.jpg` | `story/marsh_fane_p.jpg` |
| `scene_willow_sanctuary` | Weeping Willow Sanctuary | `story/willow_sanctuary.jpg` | `story/willow_sanctuary_p.jpg` |
| `scene_sunken_catacombs` | Sunken Catacombs | `story/sunken_catacombs.jpg` | `story/sunken_catacombs_p.jpg` |
| `scene_shadowed_crags` | Shadowed Crags | `story/shadowed_crags.jpg` | `story/shadowed_crags_p.jpg` |
| `scene_mausoleum` | Mausoleum of the Sun | `story/mausoleum_sun.jpg` | `story/mausoleum_sun_p.jpg` |
| `scene_emerald_choir` | Emerald Choir Grove | `story/emerald_choir.jpg` | `story/emerald_choir_p.jpg` |
| `scene_blind_gorge` | The Blind Gorge | `story/blind_gorge.jpg` | `story/blind_gorge_p.jpg` |
| `scene_clockwork_bastion` | Clockwork Bastion of Ouros | `story/clockwork_bastion.jpg` | `story/clockwork_bastion_p.jpg` |
| `scene_silent_citadel` | Silent Citadel Gates | `story/silent_citadel_gates.jpg` | `story/silent_citadel_gates_p.jpg` |
| `scene_void_reservoir` | The Void Reservoir | `story/void_reservoir.jpg` | `story/void_reservoir_p.jpg` |
| `scene_celestial_spire` | Celestial Ribbon Stair | `story/celestial_stair.jpg` | `story/celestial_stair_p.jpg` |
| `scene_final_summit` | Bell of Eternity Summit | `story/final_summit.jpg` | `story/final_summit_p.jpg` |
| `scene_obsidian_vaults` | Obsidian Vaults (Black Guild) | `story/obsidian_vaults.jpg` | `story/obsidian_vaults_p.jpg` |
| `scene_umbral_trench` | The Umbral Trench | `story/umbral_trench.jpg` | `story/umbral_trench_p.jpg` |
| `scene_celestial_vestibule` | Vestibule of Echoes | `story/vestibule_echoes.jpg` | `story/vestibule_echoes_p.jpg` |
| `scene_epilogue` | Whispering Pines Awakened | `story/village_bright.jpg` | `story/village_bright_p.jpg` |

---

## Detailed Prompt Catalog

---

### Act I: The Ashen Awakening

#### Scene 1: Aethel's Cottage
- **Scene ID**: `scene_cottage`
- **Location**: Invocator's Bedroom & Study
- **Lore Context**: The morning of the Great Muting. Sunlight pours through leaded-glass windows into a cozy timber cottage. Dust motes hang frozen mid-air; the grandfather clock's pendulum is frozen still. An arcane hearth has gone cold with grey ash.
- **Landscape (`story/cottage_bedroom.jpg`)**:
  > **Prompt**: `16-bit JRPG digital matte painting, cozy fantasy invocator cottage bedroom, morning golden sunlight streaming through diamond-paned leaded glass windows, rustic oak timber floorboards, wooden desk strewn with parchment scrolls, spellbooks, and glass potion vials, cold hearth with silent pale ash, dust motes frozen suspended in sunlight shafts, warm yet eerily quiet atmosphere, detailed fantasy interior, Studio Ghibli environmental aesthetic, painterly lighting --ar 16:9 --style raw`
- **Portrait (`story/cottage_bedroom_p.jpg`)**:
  > **Prompt**: `Vertical composition, 16-bit JRPG fantasy invocator bedroom, high-angle downward perspective showing wooden rafters, tall vertical sunbeams streaming from an arched window onto a rustic timber bed and study desk covered in scrolls, frozen dust motes glowing in sunlight shafts, serene yet uncanny stillness, warm morning light, painterly fantasy concept art, clean bottom third --ar 9:16 --style raw`

---

#### Scene 2: Whispering Pines (The Blighted Square)
- **Scene ID**: `scene_village`
- **Location**: Village Square of Whispering Pines
- **Lore Context**: The village square silenced by the Void. Obsidian crystal vines coil around cobblestones, timber houses, and market stalls. Townspeople stand petrified into pitch-black volcanic glass, frozen mid-step with crying hollow eyes.
- **Landscape (`story/village_square.jpg`)**:
  > **Prompt**: `Dark fantasy JRPG landscape, desolate medieval fantasy village square, cobblestone plaza strangled by jagged pitch-black obsidian crystal vines and creeping black glass tendrils, timber-frame cottages with shuttered dark windows, eerie pale violet fog creeping between buildings, chilling silence, somber atmospheric lighting, high fantasy matte painting, melancholic mood --ar 16:9 --style raw`
- **Portrait (`story/village_square_p.jpg`)**:
  > **Prompt**: `Vertical framing, dark fantasy village street, tall crooked timber houses flanking a narrow cobblestone lane, jagged obsidian vines climbing up stone walls toward a brooding twilight sky, ominous pale purple mist pooling at ground level, oppressive silence, moody atmospheric depth, clean lower perspective for mobile interface --ar 9:16 --style raw`

---

#### Scene 3: The Sun Shrine Crossroads
- **Scene ID**: `scene_crossroads`
- **Location**: Ancient Forest Path & Sun Shrine
- **Lore Context**: A weathered stone shrine dedicated to Solaria at a forest fork. Ancient towering pines filter amber sunlight. Cracked golden runes and sunburst motifs adorn the weathered stone altar where Sir Cedric first rests.
- **Landscape (`story/forest_crossroads.jpg`)**:
  > **Prompt**: `Fantasy woodland crossroad, ancient sun shrine carved from weather-worn granite standing at a fork in an ancient pine forest, golden sunburst carvings and faint solar runes glowing warmly, dappled amber sunlight piercing through giant coniferous pine trees, mossy stones, wildflower patches along dirt trails, heroic high fantasy adventure atmosphere, lush painterly environment art --ar 16:9 --style raw`
- **Portrait (`story/forest_crossroads_p.jpg`)**:
  > **Prompt**: `Vertical composition, towering ancient pine trees stretching high into a bright azure canopy, a solitary stone sun-shrine altar at the base with intricate sun carvings glowing faintly, sunlight beams cascading downward through the tall needle branches, winding woodland trail below, majestic fantasy landscape, mobile wallpaper framing --ar 9:16 --style raw`

---

#### Scene 4: Camp of the Fellowship
- **Scene ID**: `scene_camp`
- **Location**: Night Encampment beside Ancient Ruins
- **Lore Context**: The fellowship's night rest before entering the deeper trials. A crackling stone campfire casts flickering warm amber light against old sun-shrine pillars. Beyond the firelight circle, the dark pine forest looms quietly under a starry night.
- **Landscape (`story/fellowship_camp.jpg`)**:
  > **Prompt**: `Fantasy RPG campfire campsite at night, glowing campfire in a stone fire ring casting dancing golden and orange light, wooden supply crates, leather bedrolls, ancient moss-covered stone pillars of a forgotten sun shrine in the background, towering dark pine forest under a clear starlit night sky, cozy sanctuary amidst brooding darkness, painterly atmospheric lighting --ar 16:9 --style raw`
- **Portrait (`story/fellowship_camp_p.jpg`)**:
  > **Prompt**: `Vertical perspective, cozy fantasy campsite beneath a vast indigo night sky brimming with stars and nebulae, campfire flames crackling at the base casting warm embers spiraling upward, ancient granite pillars rising into the night, shadowy pine branches framing the upper borders, deep atmospheric contrast between warm fire and cool celestial dark --ar 9:16 --style raw`

---

#### Scene 5: The Whispering Caverns
- **Scene ID**: `scene_cave`
- **Location**: Subterranean Crystal Grotto
- **Lore Context**: Damp limestone caverns deep beneath Solaria. Clusters of glowing cyan and azure resonance crystals sprout from stalactites and cave walls, humming with ancient harmonic power over reflecting pools of calm subterranean water.
- **Landscape (`story/caverns.jpg`)**:
  > **Prompt**: `Subterranean fantasy cavern grotto, damp limestone cave walls embedded with glowing cyan and azure bioluminescent crystals, stalactites hanging over a tranquil underground reflecting pool, gentle turquoise water ripples, soft ambient blue glow illuminating ancient stone carvings along the cavern floor, mysterious mystical atmosphere, fantasy environment concept art --ar 16:9 --style raw`
- **Portrait (`story/caverns_p.jpg`)**:
  > **Prompt**: `Vertical underground cavern chasm, towering stalactites encrusted with radiant glowing cyan crystals descending from high unseen cave ceilings, sparkling turquoise dust motes falling into a clear underground lake below, dramatic subterranean scale, bioluminescent fantasy art, mobile wallpaper framing --ar 9:16 --style raw`

---

#### Scene 6: The Rotting Marsh
- **Scene ID**: `scene_swamp`
- **Location**: Outer Sunken Bog & Deadwood
- **Lore Context**: Murky black waters and twisted weeping cypress trees choked with suffocating viridian fog. Swamp gas, black mire, and gnarled roots conceal corrupted void creatures lurking in the muck.
- **Landscape (`story/rotting_marsh.jpg`)**:
  > **Prompt**: `Grim fantasy swamp marshland, murky black stagnant waters covered in duckweed, gnarled twisted cypress trees dripping with spanish moss, thick eerie emerald and pale viridian fog clinging to the surface, decaying wooden boat half-submerged, ghostly faint will-o'-the-wisps floating in the distance, dark fantasy concept art, atmospheric eerie lighting --ar 16:9 --style raw`
- **Portrait (`story/rotting_marsh_p.jpg`)**:
  > **Prompt**: `Vertical view of a haunted dark fantasy swamp, towering gnarled mangrove and dead cypress branches arching overhead shrouded in poisonous green mist, still black mire water below reflecting ghostly floating spirit orbs, eerie depth and vertical fog layers, haunting gothic nature, painterly composition --ar 9:16 --style raw`

---

#### Scene 7: The Aqueducts of Solaria
- **Scene ID**: `scene_aqueduct`
- **Location**: Mountain Gorge & Monumental Aqueducts
- **Lore Context**: Colossal white limestone Romanesque aqueducts spanning a vast alpine chasm. Cascading mountain waterfalls have been frozen solid into crystalline obsidian glass by the silence. Morning mist swirls around monumental stone arches.
- **Landscape (`story/aqueducts.jpg`)**:
  > **Prompt**: `Colossal fantasy aqueduct bridge spanning a breathtaking mountain gorge, monumental limestone arches rising into swirling alpine morning mist, waterfalls cascading down cliffs that have frozen mid-fall into jagged black obsidian glass, rugged granite peaks under a pale dawn sky, grand monumental scale, classical epic fantasy matte painting --ar 16:9 --style raw`
- **Portrait (`story/aqueducts_p.jpg`)**:
  > **Prompt**: `Vertical grand vista, looking up from the base of a deep mountain canyon at colossal tiered Romanesque aqueduct arches soaring thousands of feet into the mist, towering waterfalls frozen into black crystal ice, dramatic vertical perspective, sweeping scale, epic fantasy landscape art --ar 9:16 --style raw`

---

#### Scene 8: Crypt of the Foundation
- **Scene ID**: `scene_dungeon`
- **Location**: Subterranean Vaults of the First Bell Tower
- **Lore Context**: Sacred catacombs under the Bell Tower of Solaria. Vaulted stone pillars carved with ancient musical clefs and hymn reliefs. Iron braziers flicker with violet fire; stone sarcophagi of the Primordial Chanters line the alcoves.
- **Landscape (`story/foundation_crypt.jpg`)**:
  > **Prompt**: `Underground gothic stone crypt, vaulted cathedral ceilings supported by massive carved pillars inscribed with ancient musical notations and acoustic hymns, heavy stone sarcophagi resting in arched alcoves, iron braziers burning with eerie violet and amber embers, dusty stone flagstone floor, dark solemn dungeon atmosphere, rich fantasy environmental design --ar 16:9 --style raw`
- **Portrait (`story/foundation_crypt_p.jpg`)**:
  > **Prompt**: `Vertical perspective of an ancient cathedral crypt, soaring ribbed stone arches ascending into deep subterranean shadows, tall carved pillars with sacred musical glyphs, iron hanging lanterns casting soft purple and golden glows down the long flagstone hallway, grand gothic scale, clean lower frame for text overlay --ar 9:16 --style raw`

---

#### Scene 9: The Solaria Bell Chamber
- **Scene ID**: `scene_tower`
- **Location**: Open-Air Summit Belfry
- **Lore Context**: High above the sea of clouds at the peak of the First Bell Tower. Open gothic parapets overlook the kingdom. In the center hangs the colossal First Great Bell of Solaria, forged of sun-bronze and etched with radiant solar sigils.
- **Landscape (`story/bell_chamber.jpg`)**:
  > **Prompt**: `Open-air gothic cathedral belfry at the summit of a colossal stone tower, high above a rolling sea of white clouds, golden morning sunbeams breaking through storm clouds, a massive ornate sun-bronze cathedral bell hanging from ancient timber belfry beams, ornate stone balustrades overlooking the sky, triumphant and awe-inspiring atmosphere, fantasy architecture concept art --ar 16:9 --style raw`
- **Portrait (`story/bell_chamber_p.jpg`)**:
  > **Prompt**: `Vertical framing looking up inside a monumental open-air bell tower summit, the massive bronze Bell of Solaria suspended overhead with intricate celestial engravings, stone gothic buttresses framing an endless azure sky and sea of white clouds, radiant golden godrays streaming downward, epic scale, high fantasy mobile view --ar 9:16 --style raw`

---

### Act II: The Severed Resonance

#### Scene 10: The Drowned Fane
- **Scene ID**: `scene_marsh_fane`
- **Location**: Sunken Gothic Ruin in the Mire
- **Lore Context**: An ancient sunken temple half-swallowed by the black waters of the Rotting Marsh. Half-submerged marble pillars, shattered arches entwined with thorny briars, and glowing green marsh spores drift through the twilight.
- **Landscape (`story/marsh_fane.jpg`)**:
  > **Prompt**: `Ancient sunken gothic temple ruins emerging from dark swamp waters, crumbling marble colonnades and broken archways draped in tangled thorny briars and emerald moss, reflective dark water rippling around sunken stone steps, glowing pale green spores floating in the humid dusk air, mysterious melancholic atmosphere, rich fantasy landscape --ar 16:9 --style raw`
- **Portrait (`story/marsh_fane_p.jpg`)**:
  > **Prompt**: `Vertical composition, half-submerged gothic temple arches rising from dark marsh water, weeping willow branches and glowing emerald vines framing the vertical view, ethereal greenish mist hanging over the submerged stone causeway, ancient submerged sanctuary, dark romantic fantasy art --ar 9:16 --style raw`

---

#### Scene 11: The Weeping Willow Sanctuary
- **Scene ID**: `scene_willow_sanctuary`
- **Location**: Sacred Heart of the Grove
- **Lore Context**: The sacred grove of the Grove Wardens. A colossal primeval weeping willow tree with luminous jade-green foliage towering over a crystal-clear spring. Droplets of dew hum with harmonic resonance as thousands of emerald motes drift like living fireflies.
- **Landscape (`story/willow_sanctuary.jpg`)**:
  > **Prompt**: `Majestic ancient sacred weeping willow tree glowing with luminous emerald leaves, branches cascading like green waterfalls over a crystalline sacred forest pool, thousands of floating bioluminescent jade motes and fireflies dancing in the air, lush mossy roots, vibrant ethereal nature fantasy, peaceful sacred grove, Studio Ghibli inspired painterly lighting --ar 16:9 --style raw`
- **Portrait (`story/willow_sanctuary_p.jpg`)**:
  > **Prompt**: `Vertical view of the colossal sacred Weeping Willow, trunk soaring high with radiant cascading green leaf curtains, glowing jade light filtering from the canopy down into a tranquil crystal pond, spiritual and magical forest sanctuary, awe-inspiring vertical scale, fantasy environment art --ar 9:16 --style raw`

---

#### Scene 12: The Sunken Catacombs
- **Scene ID**: `scene_sunken_catacombs`
- **Location**: Submerged Belfry of the Veridian Chime
- **Lore Context**: The cleansed water vaults beneath the Weeping Willow. Carved jade walkways rise from the clear pool. Hanging above the sacred basin is the Second Great Bell: the Veridian Chime, cast in sea-green jade-bronze with leaf-etched tuning tines.
- **Landscape (`story/sunken_catacombs.jpg`)**:
  > **Prompt**: `Ancient submerged subterranean temple chamber with crystal clear turquoise water, carved jade and white marble causeways crossing the pool, hanging from the vaulted botanical ceiling is a monumental jade-bronze chime bell etched with vine runes, soft sunlight filtering from ceiling fissures creating luminous water caustics, serene ancient fantasy sanctuary --ar 16:9 --style raw`
- **Portrait (`story/sunken_catacombs_p.jpg`)**:
  > **Prompt**: `Vertical perspective of an underwater-influenced stone temple vault, looking up at the monumental jade Veridian Chime bell suspended over a tranquil turquoise reflecting basin, water ripples casting dancing light patterns onto towering jade columns, tranquil yet grand, mobile vertical framing --ar 9:16 --style raw`

---

#### Scene 13: The Shadowed Crags
- **Scene ID**: `scene_shadowed_crags`
- **Location**: Obsidian Canyon & High Cliffs
- **Lore Context**: Treacherous, knife-edge obsidian spires and dark volcanic cliffs leading toward the Black Guild's territory. Freezing winds sweep through purple mountain mist; narrow ledge trails wind over dizzying abyssal drops.
- **Landscape (`story/shadowed_crags.jpg`)**:
  > **Prompt**: `Treacherous mountain pass carved through jagged razor-sharp obsidian rock formations, towering volcanic spires piercing through swirling purple and deep twilight mist, narrow cliffside trail clinging to sheer dark stone precipices, freezing cold winds, dramatic jagged silhouettes against an ominous dusky sky, dark fantasy concept art --ar 16:9 --style raw`
- **Portrait (`story/shadowed_crags_p.jpg`)**:
  > **Prompt**: `Vertical dramatic canyon vista, towering black obsidian needles and spires flanking a terrifyingly deep mountain gorge, swirling violet mist rising from the shadowy abyss below, narrow mountain ledge winding upwards, staggering vertical drop, dark fantasy adventure matte painting --ar 9:16 --style raw`

---

### Act III: The Iron Bastion

#### Scene 14: The Mausoleum of the Sun
- **Scene ID**: `scene_mausoleum`
- **Location**: Subterranean Vault of the Fallen Templars
- **Lore Context**: The resting place of the Golden Chime knights. Crumbling white marble colonnades and shattered knightly statues kneeling before tattered, weeping golden sunburst banners. Silent candles flicker around fallen paladin seals.
- **Landscape (`story/mausoleum_sun.jpg`)**:
  > **Prompt**: `Grand ancient marble mausoleum of fallen holy knights, shattered classical statues of armored paladins kneeling in solemn rows, faded golden sunburst banners hanging from high ruined arches, dust motes drifting in shafts of pale amber light, cold white marble floor with cracked golden inlay, tragic heroic elegance, dark fantasy interior art --ar 16:9 --style raw`
- **Portrait (`story/mausoleum_sun_p.jpg`)**:
  > **Prompt**: `Vertical perspective in a soaring cathedral mausoleum, tall fluted marble pillars reaching up to vaulted sunburst arches, long golden heraldic banners hanging vertically from the rafters, shattered knight statues flanking a central tomb path below, solemn and regal, painterly fantasy lighting --ar 9:16 --style raw`

---

#### Scene 15: The Emerald Choir Grove
- **Scene ID**: `scene_emerald_choir`
- **Location**: Deep Sunken Redwood Glade
- **Lore Context**: A secluded glade of massive primeval redwoods. Petrified dryads stand frozen in black obsidian around a bubbling dark mineral spring, trapped mid-chorus. Silent crimson leaves hang motionless in the windless air.
- **Landscape (`story/emerald_choir.jpg`)**:
  > **Prompt**: `Sunken primeval forest glade with massive ancient redwood trunks, petrified wooden and black obsidian statues of tree dryads frozen mid-song around a bubbling black mineral spring, crimson and emerald leaves blanketed on mossy ground, shafts of muted emerald sunlight breaking through thick foliage, haunting silence, dark fairy tale matte painting --ar 16:9 --style raw`
- **Portrait (`story/emerald_choir_p.jpg`)**:
  > **Prompt**: `Vertical woodland perspective, gigantic redwood tree trunks stretching endlessly toward the upper edge of the frame, petrified dryad figures frozen in stone among the mossy root arches below, quiet emerald forest lighting, mystical and haunting mood, high fantasy environment art --ar 9:16 --style raw`

---

#### Scene 16: The Blind Gorge
- **Scene ID**: `scene_blind_gorge`
- **Location**: Outlaw Fortress Chasm & Alchemical Smog
- **Lore Context**: The secluded canyon hideout of Nocturne and the Black Guild. Razor obsidian slabs, dark alchemical smog vents, rope bridges crossing bottomless crevices, and hidden sniper perches carved into the rock.
- **Landscape (`story/blind_gorge.jpg`)**:
  > **Prompt**: `Dark canyon chasm filled with poisonous amber and violet alchemical smog vents, jagged obsidian cliffs, suspended wooden rope bridges crossing bottomless rocky crevices, hidden assassin watchtowers carved into volcanic crags, industrial dark fantasy rogue hideout, moody cinematic lighting --ar 16:9 --style raw`
- **Portrait (`story/blind_gorge_p.jpg`)**:
  > **Prompt**: `Vertical canyon composition, sheer dark obsidian rock walls boxing in a narrow foggy chasm, rope suspension bridges crisscrossing high overhead against toxic amber haze, glowing volcanic vents illuminating the dark path below, treacherous verticality, mobile wallpaper framing --ar 9:16 --style raw`

---

#### Scene 17: The Clockwork Bastion of Ouros
- **Scene ID**: `scene_clockwork_bastion`
- **Location**: The Iron Belfry & Foundry Forge
- **Lore Context**: The monumental industrial fortress of Clockwork Warmaster Ouros. Colossal brass gears, grinding iron cogs, glowing molten steel channels, and roaring steam pipes. At the pinnacle hangs the Third Great Bell: The Resonant Bastion.
- **Landscape (`story/clockwork_bastion.jpg`)**:
  > **Prompt**: `Monumental steampunk fantasy clockwork fortress, colossal brass gears, giant grinding iron cogs and roaring steam pipes inside an immense iron foundry, rivers of molten orange steel flowing through channels below, suspended catwalks and iron chains, high industrial fantasy aesthetic, epic warm orange and dark steel lighting --ar 16:9 --style raw`
- **Portrait (`story/clockwork_bastion_p.jpg`)**:
  > **Prompt**: `Vertical perspective of an immense clockwork foundry tower, gigantic interlocking brass gearwheels towering vertically into smog-filled iron ceilings, pressurized steam plumes bursting from pipes, glowing orange molten forge basin at the base, impressive mechanical scale, dynamic industrial concept art --ar 9:16 --style raw`

---

#### Scene 18: The Obsidian Vaults of the Black Guild
- **Scene ID**: `scene_obsidian_vaults`
- **Location**: Subterranean Reliquary of Stolen Voices
- **Lore Context**: The hidden dungeon vault beneath the gorge. Shelves upon shelves of sealed lead jars containing stolen human voices, trembling with faint bioluminescent motes. Dark stone ledgers and iron tables where the Guild recorded their silent harvests.
- **Landscape (`story/obsidian_vaults.jpg`)**:
  > **Prompt**: `Subterranean stone reliquary vault, endless rows of dark iron shelves filled with sealed lead canisters and glowing glass jars holding trapped luminescent voice motes, heavy iron tables strewn with dark leather ledgers and alchemical instruments, dim green and amber lantern light, oppressive secrecy, dark fantasy laboratory --ar 16:9 --style raw`
- **Portrait (`story/obsidian_vaults_p.jpg`)**:
  > **Prompt**: `Vertical view of an ominous subterranean archive, soaring shelves of sealed lead jars reaching high into vaulted shadows, jars pulsing with faint eerie cyan and violet light, a lonely stone lectern and ledger on the floor below, haunting and claustrophobic, mobile interface framing --ar 9:16 --style raw`

---

### Act IV: The Celestial Spire

#### Scene 19: The Silent Citadel Gates
- **Scene ID**: `scene_silent_citadel`
- **Location**: Floating Plateau of Sol-Aethel & Great Citadel
- **Lore Context**: The floating mountain city of Sol-Aethel. Monolithic battlements of polished black glass reflect the silent sky. Huge obsidian gates sealed with dark sorcery stand at the end of the Whisperway pilgrim road, hung with tattered ceremonial banners.
- **Landscape (`story/silent_citadel_gates.jpg`)**:
  > **Prompt**: `Monolithic dark fantasy citadel fortress perched on a floating mountain plateau above the cloudline, colossal gates of polished reflective black obsidian glass, grand cobblestone pilgrim causeway leading to the entrance, tattered banners fluttering in high altitude winds, starry twilight sky above, majestic and imposing scale, epic fantasy matte painting --ar 16:9 --style raw`
- **Portrait (`story/silent_citadel_gates_p.jpg`)**:
  > **Prompt**: `Vertical framing of colossal black glass citadel gates towering into an ethereal twilight sky with drifting clouds and distant stars, grand stone staircase leading up from the bottom of the frame to the monumental archway, terrifying architectural grandeur, clean lower area for UI --ar 9:16 --style raw`

---

#### Scene 20: The Void Reservoir
- **Scene ID**: `scene_void_reservoir`
- **Location**: Lake of Liquid Silence
- **Lore Context**: A vast sunken lake of pure liquid silence high above the world. Pitch-black water with mirror-like stillness beneath a weeping starlight dome. Trapped voice motes swirl beneath the surface like drowning stars, making no sound as ripples glide across the surface.
- **Landscape (`story/void_reservoir.jpg`)**:
  > **Prompt**: `Eerie vast sunken lake of pitch-black liquid silence under a weeping cosmic starlight dome, mirror-like black water reflecting distant purple and silver nebulae, glowing celestial motes trapped beneath the dark water surface like submerged stars, stone pilgrim ruins on the shoreline, cosmic fantasy surrealism, painterly atmosphere --ar 16:9 --style raw`
- **Portrait (`story/void_reservoir_p.jpg`)**:
  > **Prompt**: `Vertical composition, looking across an infinite mirror-smooth black void lake toward a towering celestial cosmic sky filled with auroras and weeping stars, submerged glowing motes visible beneath the glass-like water in the foreground, haunting cosmic beauty, high fantasy wallpaper art --ar 9:16 --style raw`

---

#### Scene 21: The Umbral Trench
- **Scene ID**: `scene_umbral_trench`
- **Location**: Sunken Void Chasm beneath the Reservoir
- **Lore Context**: The dark abyss beneath the void lake. The trench walls are lined with calcified pearl-and-glass shells of swallowed songs, holding trapped motes thrashing in dim luminescence. A narrow path of worked stone cuts through the living dark.
- **Landscape (`story/umbral_trench.jpg`)**:
  > **Prompt**: `Surreal deep abyss trench beneath a void lake, dark organic cavern walls encrusted with calcified pearl and sea-glass cocoons, each glowing softly with trapped blue and gold song motes, a narrow ancient stone pathway bridging across the pitch-black chasm, cosmic deep sea and void aesthetic, ethereal dark fantasy concept art --ar 16:9 --style raw`
- **Portrait (`story/umbral_trench_p.jpg`)**:
  > **Prompt**: `Vertical chasm perspective, towering trench walls rising into darkness, walls dotted with hundreds of glowing crystalline pearl pods containing trapped starlight, a narrow stone path traversing the abyss, dramatic vertical scale, mysterious cosmic dungeon concept art --ar 9:16 --style raw`

---

#### Scene 22: The Celestial Ribbon Stair
- **Scene ID**: `scene_celestial_spire`
- **Location**: The Solidified Stair of Light
- **Lore Context**: A ribbon staircase of solidified harmonic light suspended between the upper clouds and cosmic stars. Far below, the four Great Bell Towers of Aethelgard form a colossal cross of gold, jade, and iron. Ahead rises the spire toward the auroral ring.
- **Landscape (`story/celestial_stair.jpg`)**:
  > **Prompt**: `Breathtaking fantasy stairway made of solidified glowing harmonic light ascending between open clouds and starry space, sweeping spiral ribbon stair rising toward a celestial spire, brilliant multi-colored cosmic auroras in the sky, far below in the cloud breaks are glowing golden bell towers, grand transcendent scale, romantic fantasy concept art --ar 16:9 --style raw`
- **Portrait (`story/celestial_stair_p.jpg`)**:
  > **Prompt**: `Vertical perspective of a luminous spiral staircase of crystal light curving upward toward a magnificent celestial citadel among swirling cosmic auroras and brilliant constellations, dramatic vertical ascent into the heavens, breathtaking divine fantasy landscape, mobile framing --ar 9:16 --style raw`

---

#### Scene 23: The Vestibule of Echoes
- **Scene ID**: `scene_celestial_vestibule`
- **Location**: Chamber of the Four Prisms
- **Lore Context**: The antechamber before the final summit. Four towering pillars of living glass, each containing a severed fragment of the world's primordial song. Frozen supplicants kneel in concentric rings around the pillars, petrified in reverence to silence.
- **Landscape (`story/vestibule_echoes.jpg`)**:
  > **Prompt**: `Grand celestial vestibule chamber high in the clouds, four colossal monolithic pillars of clear living glass glowing with trapped elemental light (amber, jade, cyan, and violet), concentric marble circles on the floor with frozen stone supplicants kneeling in silent reverence, cosmic starlight shining through open colonnades, awe-inspiring sacred atmosphere --ar 16:9 --style raw`
- **Portrait (`story/vestibule_echoes_p.jpg`)**:
  > **Prompt**: `Vertical view inside an open-air celestial temple hall, towering glass crystal pillars reaching toward cosmic sky skylights, glowing harmonic energy swirling within the pillars, kneeling statues around circular marble dias below, divine grand scale, high fantasy concept art --ar 9:16 --style raw`

---

#### Scene 24: The Spire Summit — Bell of Eternity
- **Scene ID**: `scene_final_summit`
- **Location**: Pinnacle of Solaria
- **Lore Context**: The ultimate peak where the spire touches the cosmos. Suspended beneath blazing cosmic auroras hangs the Fourth Great Bell: The Bell of Eternity, forged from star-metal and meteoric glass. Grand Inquisitor Malakor's throne dais overlooks the world.
- **Landscape (`story/final_summit.jpg`)**:
  > **Prompt**: `Pinnacle summit of a celestial spire touching the outer cosmos, suspended beneath vibrant cosmic nebulae and auroras hangs the colossal Bell of Eternity forged from iridescent star-metal and meteoric glass, an ornate crystalline dais at the summit edge overlooking the curved horizon of the earth below, climactic epic final battle arena, celestial fantasy matte painting --ar 16:9 --style raw`
- **Portrait (`story/final_summit_p.jpg`)**:
  > **Prompt**: `Vertical framing looking up at the colossal Bell of Eternity suspended at the apex of the world, dazzling celestial auroras and shooting stars swirling around the star-metal bell, crystal summit platform below overlooking an infinite sea of stars, ultimate climactic scale, epic mobile art --ar 9:16 --style raw`

---

#### Scene 25: Whispering Pines Awakened (Epilogue)
- **Scene ID**: `scene_epilogue`
- **Location**: Village Square Restored
- **Lore Context**: The Great Awakening. The Bell of Eternity has tolled; obsidian vines have shattered into iridescent dust. Golden morning sunlight floods Whispering Pines, songbirds fill the living green pines, and the village square is alive with music and joy.
- **Landscape (`story/village_bright.jpg`)**:
  > **Prompt**: `Beautiful idyllic fantasy village square basking in brilliant golden morning sunlight, lush green pine trees with singing birds, cobblestone plaza with colorful festival banners, blooming flowers in window boxes of timber cottages, sparkling iridescent dust fading into the air, vibrant joyful atmosphere, heartwarming Studio Ghibli epilogue aesthetic, lush painterly lighting --ar 16:9 --style raw`
- **Portrait (`story/village_bright_p.jpg`)**:
  > **Prompt**: `Vertical vista of an awakened idyllic mountain village, quaint timber cottages and a lively cobblestone lane leading toward a sunny mountain overlook, tall green pine trees framing a bright azure sky with gentle floating flower petals and golden sunrays, joyful rebirth, heartwarming fantasy epilogue, mobile wallpaper framing --ar 9:16 --style raw`

---

## Bonus: Battle Environments Prompts

The combat engine also supports orientation-aware backgrounds for the 5 battle stages:
- `environments/<id>.jpg` (Landscape 16:9)
- `environments/<id>_p.jpg` (Portrait 9:16)

### 1. The Training Grounds (`castle`)
- **Landscape (`environments/castle.jpg`)**:
  > `16-bit JRPG battle background, grand medieval stone castle courtyard battle arena, fortress battlements flying blue and gold heraldic banners, weapon racks and training dummies, cobblestone ground, sweeping panoramic battle view, retro pixel aesthetic lighting --ar 16:9`
- **Portrait (`environments/castle_p.jpg`)**:
  > `Vertical battle arena, towering castle stone battlements rising into a blue sky with fluttering banners, cobblestone courtyard floor with training shields, vertical battle stage composition --ar 9:16`

### 2. The Crystal Cavern (`cave`)
- **Landscape (`environments/cave.jpg`)**:
  > `16-bit JRPG battle arena, glowing subterranean crystal cavern, massive clusters of cyan and amethyst bioluminescent gems sprouting from limestone walls, calm underground stream, glowing dust motes --ar 16:9`
- **Portrait (`environments/cave_p.jpg`)**:
  > `Vertical cave battle stage, towering stalactites encrusted with radiant glowing blue crystals hanging above, subterranean rock floor, mystical blue atmospheric glow --ar 9:16`

### 3. The Echo Hall (`dungeon`)
- **Landscape (`environments/dungeon.jpg`)**:
  > `16-bit JRPG combat backdrop, ancient subterranean dungeon hall, grand carved stone arches, iron wall sconces burning with bright orange flames, stone flagstone floor, dark dungeon crawler aesthetic --ar 16:9`
- **Portrait (`environments/dungeon_p.jpg`)**:
  > `Vertical dungeon arena, towering gothic stone pillars ascending into vaulted shadows, iron braziers glowing warm orange on flagstone floor, dramatic vertical perspective --ar 9:16`

### 4. The Whispering Woods (`forest`)
- **Landscape (`environments/forest.jpg`)**:
  > `16-bit JRPG battle stage, lush ancient enchanted woodland, towering oak trees, drifting green spores, sunbeams piercing through green leafy canopy, moss-covered earth and fallen logs --ar 16:9`
- **Portrait (`environments/forest_p.jpg`)**:
  > `Vertical forest combat stage, massive tree trunks rising high into sunlit emerald canopy, glowing green spores drifting in sunbeams, woodland path arena --ar 9:16`

### 5. The Sunken Hollow (`swamp`)
- **Landscape (`environments/swamp.jpg`)**:
  > `16-bit JRPG battle environment, eerie sunken swamp marshland, gnarled cypress roots emerging from dark murky waters, thick pale green mist, ghostly will-o'-the-wisps, dark fantasy battle scene --ar 16:9`
- **Portrait (`environments/swamp_p.jpg`)**:
  > `Vertical swamp battle arena, gnarled dead trees stretching upward through creeping emerald fog, still dark swamp water with floating reeds, eerie atmospheric depth --ar 9:16`

---

## Drop-In Integration Instructions

1. **Generate the Images**: Use the prompts above in Midjourney, DALL-E 3, Stable Diffusion, or your preferred AI image generator.
2. **File Naming & Placement**:
   - Save Landscape images to: `app/src/main/assets/story/<name>.jpg`
   - Save Portrait images to: `app/src/main/assets/story/<name>_p.jpg`
3. **Verify**:
   The engine's `StoryAssetLoader.kt` automatically switches between `story/<name>.jpg` and `story/<name>_p.jpg` based on device rotation with zero configuration changes required.
