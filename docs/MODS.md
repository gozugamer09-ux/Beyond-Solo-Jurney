# Mods and libraries for Beyond Solo Journey

Target: **Minecraft 1.21.1, NeoForge (21.1.x)**.
Checked on **2026-11-09** against the Modrinth API (version + loader + license) and, for
CurseForge-only mods, the CurseForge file listings. "Latest" is the newest 1.21.1 NeoForge
file at that date. Re-check versions before pinning them.

How to read this file:

- **Part A** lists what our own mod code compiles against (Gradle dependencies).
- **Part B** lists mods we add to the modpack around our mod, grouped by system.
- **Part C** covers combat visuals and how we make item/armor/weapon models.
- **Part D** lists mods we checked and rejected, so nobody re-checks them.
- **Part E** covers licensing and modpack distribution.

---

## Part A: Build dependencies (our mod's `build.gradle`)

Tip: every Modrinth project is also on the Modrinth Maven, so one repository covers almost
everything:

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
}
// usage: implementation "maven.modrinth:<slug>:<version number or version id>"
```

Pin by **Modrinth version id** (not version number) when a mod reuses the same version number
for its Fabric and NeoForge files, or Gradle may pull the Fabric jar. Checked examples for 1.21.1:
AttributeFix 21.1.3 = `TyNITLDY`, Bookshelf 21.1.81 = `1sdJl7J1`, Prickle 21.1.11 = `EE1FHDyD`
(all NeoForge files).

### A1. Required (the core mod needs these at runtime)

| Mod | Latest 1.21.1 NeoForge | License | What we use it for | Needs |
|---|---|---|---|---|
| [GeckoLib](https://modrinth.com/mod/geckolib) | 4.9.3 | MIT | Animated armor, weapons, gate model, bosses, System entities | - |
| [Curios API](https://modrinth.com/mod/curios) | 9.5.1+1.21.1 | LGPL-3.0+ | Extra equipment slots (rings, amulets, rank badge, rune slots) | - |
| [Apothic Attributes](https://modrinth.com/mod/apothic-attributes) (formerly "Attributes Lib") | 1.21.1-2.11.0 | MIT | Extra attributes the stat system can buff: crit chance/damage, armor pierce, life steal, dodge, fire/cold damage, mining speed, draw speed, etc. Also shows attributes in the inventory. | **Placebo** |
| [Placebo](https://modrinth.com/mod/placebo) | 1.21.1-9.9.4 | MIT | Library required by Apothic Attributes | - |

Note: the old Modrinth slug `attributeslib` no longer exists. Use `apothic-attributes` and add
`placebo` next to it.

### A2. Strongly recommended for the core mod

| Mod | Latest | License | Why |
|---|---|---|---|
| [AttributeFix](https://modrinth.com/mod/attributefix) | 21.1.3 | LGPL-2.1 | Raises vanilla attribute caps (max health 1024, armor 30, etc.). Without it, high-rank players hit hard limits. Needs Bookshelf + Prickle. Runtime only, no code dependency. |
| [Lithostitched](https://modrinth.com/mod/lithostitched) | 1.8.1-neoforge-21.1 | MIT | Worldgen modifiers by datapack. We can **inject the Evaluation Zone piece into village template pools** for vanilla, CTOV and Towns and Towers villages without code. Also used by most structure mods below. |
| [JEI](https://modrinth.com/mod/jei) API (`compileOnly`) | 19.70.0.469 | MIT | Show our recipes, elixirs, rank items. Compile against the API only. |
| [Jade](https://modrinth.com/mod/jade) API (`compileOnly`) | 15.10.6+neoforge | CC-BY-NC-SA-4.0 | Plugin to show mob rank / threat level and buildup bars when looking at a mob. |

### A3. Optional integrations (add when the matching thread starts)

| Mod | Latest | License | Thread | Why |
|---|---|---|---|---|
| [Better Combat](https://modrinth.com/mod/better-combat) | 2.4.0+1.21.1-neoforge | ARR (modpack use allowed via Modrinth/CF) | Combat | Our weapons ship a `weapon_attributes` JSON so they get combo animations. Data only; code dependency is optional. Needs playerAnimator + Cloth Config. |
| [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) | 1.21.1-3.16.3 | ARR | Combat / classes | Possible base for the Mage/Healer classes. Has spell schools including **Blood** and **Fire**, mana, spell casting UI, and an addon API. Our class system could grant spell slots instead of writing a spell engine. |
| [Easy NPC](https://modrinth.com/mod/easy-npc) | 7.18.0 | MIT | NPC quests | Placeable NPCs with dialog trees, skins, trading and actions. Good for hand-placed NPCs (the Evaluator, guild clerks). Needs easy-npc-core, easy-npc-config-ui. |
| [Patchouli](https://modrinth.com/mod/patchouli) or [Modonomicon](https://modrinth.com/mod/modonomicon) | 1.21.1-93 / 1.21.1-1.123.0 | CC-BY-NC-SA-3.0 / MIT | Core | In-game guidebook ("System Manual"). Modonomicon has quest-like unlock pages. |
| [Lodestone](https://modrinth.com/mod/lodestonelib) | 1.8.2 | LGPL-3.0 | Combat | Code-driven particles, screen shake, trails, world-space VFX. Lighter to integrate than Photon. |
| [Veil](https://modrinth.com/mod/veil) | 4.5.2 | LGPL-3.0 | Core UI / combat | Post-processing shaders and dynamic lights: blue System glow, red vignette for blood buildup, orange heat haze for deep burn, grey desaturation when exhausted. |
| [Photon Editor](https://modrinth.com/mod/photon-editor) | mc1.21.1-2.2.11-neoforge | Custom license (read before shipping) | Combat | Unity-style in-game VFX editor (particles, beams, trails, timelines). Best tool for "epic" skill effects without writing particle code. Needs LDLib + KilaGraph. |
| [Lithostitched](https://modrinth.com/mod/lithostitched) | see A2 | MIT | Gates | Also handles structure placement tweaks for the dungeon dimension. |

---

## Part B: Modpack additions (not code dependencies)

### B1. Combat feel and animation

**Decision (2026-11-09): Better Combat + Combat Roll, with Photon/Lodestone effects.** See Part C for the reasoning.

| Mod | Latest | License | Purpose |
|---|---|---|---|
| [Better Combat](https://modrinth.com/mod/better-combat) | 2.4.0+1.21.1-neoforge | ARR | Weapon combos, sweeping swings, two-handed and dual-wield animations |
| [playerAnimator](https://modrinth.com/mod/playeranimator) | 2.0.4+1.21.1 | MIT | Required by Better Combat |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 15.0.140+neoforge | LGPL-3.0 | Required by Better Combat |
| [Combat Roll](https://modrinth.com/mod/combat-roll) | 2.0.6+1.21.1-neoforge | GPL-3.0 | Dodge roll. Its roll cost can feed our exhaustion meter. |
| [Camera Overhaul](https://modrinth.com/mod/cameraoverhaul) | 2.1.2-neoforge | GPL-3.0+ | Camera tilt and sway that make swings feel heavier |
| [Shoulder Surfing Reloaded](https://modrinth.com/mod/shoulder-surfing-reloaded) | 1.21.1-5.3.0+neoforge | MIT | Over-the-shoulder 3rd-person camera (action-game feel) |
| [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) | 1.12.7 | tr7zw license | Better first/third person arm animations |
| [AAA Particles](https://modrinth.com/mod/aaa-particles) | neoforge-1.21.1-2.2.3 | MIT | Effekseer-based particles, nice slash/impact effects |
| [Subtle Effects](https://modrinth.com/mod/subtle-effects) | 1.15.1 | ARR | Ambient particles (sparks, embers) |
| [Epic Fight](https://modrinth.com/mod/epic-fight) | 21.17.3.1-mc1.21.1-neoforge | GPL-3.0+ | **Alternative** to Better Combat, see Part C. Do not install both. |

### B2. Bosses and mobs (for gates and dungeons)

| Mod | Latest | License | Purpose | Needs |
|---|---|---|---|---|
| [L_Ender's Cataclysm](https://modrinth.com/mod/l_enders-cataclysm) | 3.33 | CC-BY-NC-ND-4.0 | High-quality bosses with arenas. Great S/A-rank gate bosses. | Lionfish API, Curios |
| [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) | 1.8.2 | Mowzie's license | Bosses and mini-bosses with great animations | GeckoLib |
| [Bosses of Mass Destruction](https://modrinth.com/mod/bosses-of-mass-destruction-forge) | 1.3.3 | LGPL-3.0 | 4 bosses with good attack patterns | GeckoLib, Cerbon's API, Cloth Config |
| [Born in Chaos](https://modrinth.com/mod/borninchaos) | 1.7.6 | ARR | Dark-fantasy mobs, good for gate dungeon spawns | GeckoLib |
| [Mutant Monsters](https://modrinth.com/mod/mutant-monsters) | v21.1.1 | AGPL-3.0 | Mutant zombies/skeletons/creepers as elites | Puzzles Lib |
| [Aquamirae](https://modrinth.com/mod/aquamirae) | 7.2.13 | Obscuria license | Ice/sea mobs and bosses | Fragmentum, GeckoLib |
| [Apotheosis](https://modrinth.com/mod/apotheosis) | 1.21.1-8.9.0 | MIT | Affix loot with rarities (maps well to E-S ranks), gems, random elite bosses | Placebo, Apothic Attributes, Apothic Enchanting, Apothic Spawners, Patchouli |
| [Champions-Unofficial](https://modrinth.com/mod/champions-unofficial) | 21.1.1.7-neoforge | LGPL-3.0 | Elite mobs with tiers and affixes. Tiers can scale with gate rank. | Architectury |
| [In Control!](https://modrinth.com/mod/in-control) | 1.21-10.3.1 | MIT | Spawn rules by dimension/biome/light. Use it to control what spawns in gate dimensions. | - |

### B3. Quests and NPCs

Our own quest engine (thread 3) drives System quests and event-triggered quests, because those
need our custom UI and triggers. These mods are optional helpers.

| Mod | Latest | Where | License | Purpose |
|---|---|---|---|---|
| [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | 2101.1.33 | CurseForge only | Check FTB license | Classic quest book for a main storyline/tutorial. Needs FTB Library + FTB Teams (CurseForge only). Has KubeJS hooks. |
| [Odyssey Quests](https://modrinth.com/mod/odyssey-quests) (Heracles) | 2.0.3 | Modrinth | MIT | Open-source alternative to FTB Quests, quest trees, Modrinth-friendly. Needs Resourceful Lib. |
| [Easy NPC](https://modrinth.com/mod/easy-npc) | 7.18.0 | Modrinth | MIT | Quest-giver NPCs with dialog |
| [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | 7.7.36+1.21.1 | Modrinth | GPL-3.0 | Replaces villagers with talking NPCs. Heavy; only add if we want deeper villager life. May clash with our villager quest hooks, test first. |

### B4. Scripting and tuning (no recompiles)

| Mod | Latest | License | Purpose |
|---|---|---|---|
| [KubeJS](https://modrinth.com/mod/kubejs) | 2101.7.2-build.381 | LGPL-3.0 | Scripts for recipes, events, loot, quests. Lets us tune balance without rebuilding the mod. Needs Rhino. |
| [Rhino](https://modrinth.com/mod/rhino) | 2101.2.8-build.91 | MPL-2.0 | Required by KubeJS |
| [ProbeJS](https://modrinth.com/mod/probejs) | 8.0.3 | LGPL-2.1 | Autocomplete/types for KubeJS scripts in VS Code |
| [LootJS](https://modrinth.com/mod/lootjs) | 1.21.1-3.7.0 | LGPL-3.0 | Loot table edits by rank/dimension |
| [KubeJS Additions](https://modrinth.com/mod/kubejs-additions) | 6.0.0 | MIT | Extra KubeJS events and helpers |

Plan: expose our System events (rank-up, quest complete, gate cleared, buildup pop) as KubeJS
events later, so pack-side scripts can react to them.

### B5. World, villages and structures

| Mod | Latest | License | Purpose | Needs |
|---|---|---|---|---|
| [YUNG's API](https://modrinth.com/mod/yungs-api) + Better Dungeons, Strongholds, Mineshafts, Ocean Monuments, Desert/Jungle Temples, Nether Fortresses, Witch Huts, End Island, Bridges, Extras | 1.21.1-NeoForge 3.x-5.x | LGPL-3.0 | Overhauled vanilla structures | YUNG's API |
| [Towns and Towers](https://modrinth.com/mod/towns-and-towers) | 1.13.11 | CC-BY-NC-SA-4.0 | More village and outpost variants | Cristel Lib |
| [ChoiceTheorem's Overhauled Village](https://modrinth.com/mod/ct-overhaul-village) (CTOV) | 3.6.4 | CC-BY-NC-ND-4.0 | Biome-specific village overhaul. Our Evaluation Zone must be injected into its pools too. | Lithostitched |
| [When Dungeons Arise](https://modrinth.com/mod/when-dungeons-arise) | 2.1.68 | ARR | Huge dungeon structures, good "outside gate" landmarks | - |
| [MVS - Moog's Voyager Structures](https://modrinth.com/mod/moogs-voyager-structures) | 5.1.3 | MIT | Lots of small/medium structures | Moog's Structure Lib |
| [Repurposed Structures](https://modrinth.com/mod/repurposed-structures-forge) | 7.5.22+1.21.1-neoforge | LGPL-3.0 | Biome variants of vanilla structures | - |
| [Explorify](https://modrinth.com/mod/explorify) | v1.6.6 | ARR | Vanilla-style small structures | - |
| [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) | v4.4.4 | ARR | Taverns (good NPC quest hubs) and dungeons | - |
| [Structory](https://modrinth.com/mod/structory) | 1.3.17 | Stardust Labs | Ruins and small builds | - |
| [Tectonic](https://modrinth.com/mod/tectonic) | 3.0.28-neoforge-21.1 | MIT | Dramatic terrain | Lithostitched |
| [Terralith](https://modrinth.com/mod/terralith) | 2.6.2 | Stardust Labs | More biomes (vanilla blocks only) | Lithostitched |
| [Waystones](https://modrinth.com/mod/waystones) | 21.1.46 | ARR | Fast travel between found villages | Balm |

Warning: [Integrated Dungeons and Structures](https://modrinth.com/mod/idas) (1.13.7) lists
Create, Quark and Supplementaries as required on Modrinth. Skip it unless we want those mods.

### B6. HUD, UI and quality of life

| Mod | Latest | License | Purpose |
|---|---|---|---|
| [Overflowing Bars](https://modrinth.com/mod/overflowing-bars) | v21.1.1 | MPL-2.0 | Stacks hearts/armor rows. Needed once VIT raises health far past 20. Needs Puzzles Lib. |
| [AppleSkin](https://modrinth.com/mod/appleskin) | 3.0.9+mc1.21 | Unlicense | Food/saturation info |
| [Toast Control](https://modrinth.com/mod/toast-control) | 1.21.1-9.0.1 | MIT | Hides/limits vanilla toasts so our System pop-ups stand out. Needs Placebo. |
| [Jade](https://modrinth.com/mod/jade) | 15.10.6 | CC-BY-NC-SA-4.0 | Looking-at tooltips (we add a rank plugin) |
| [EMI](https://modrinth.com/mod/emi) or [JEI](https://modrinth.com/mod/jei) | 1.1.24 / 19.70.0.469 | MIT | Recipe viewer (EMI looks nicer, can run alongside JEI) |
| [Legendary Tooltips](https://modrinth.com/mod/legendary-tooltips) | 1.5.5 | CC-BY-NC-ND-4.0 | Rarity-colored tooltip frames for E-S rank gear. Needs Iceberg + Prism. |
| [FancyMenu](https://modrinth.com/mod/fancymenu) + [Drippy Loading Screen](https://modrinth.com/mod/drippy-loading-screen) | 3.9.14 / 3.1.5 | DSMSL | Custom title and loading screens in the System style. Needs Konkrete + Melody. |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) + [World Map](https://modrinth.com/mod/xaeros-world-map) | 26.6.0 / 1.47.0 | ARR | Maps (or [JourneyMap](https://modrinth.com/mod/journeymap) 6.0.9) |
| [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) | 0.33.0 | LGPL-3.0 | Parties for multiplayer gate raids, chunk claims |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | 3.26.21 | ARR | Backpacks (or our own "System inventory" later) |
| [Corpse](https://modrinth.com/mod/corpse) | 1.1.22 | ARR | Keeps items in a corpse after death, important in hard dungeons |
| [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) | 2.6.27 | ARR | Proximity voice for multiplayer |

### B7. Performance (client + server)

| Mod | Latest | License | Side | Note |
|---|---|---|---|---|
| [Sodium](https://modrinth.com/mod/sodium) | mc1.21.1-0.8.13-neoforge | Polyform Shield | Client | Now has an official NeoForge 1.21.1 build. **Use instead of Embeddium** (Embeddium's last 1.21.1 build is from Jan 2025). |
| [Sodium Extra](https://modrinth.com/mod/sodium-extra) | 0.9.5+neoforge | LGPL-3.0 | Client | Extra video settings |
| [Iris](https://modrinth.com/mod/iris) | 1.8.14-beta.1 | LGPL-3.0 | Client | Optional shaders (needs Sodium) |
| [Lithium](https://modrinth.com/mod/lithium) | 0.15.4-neoforge | LGPL-3.0 | Both | Game logic optimisations |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 7.0.3-neoforge | MIT | Both | Memory use |
| [ModernFix](https://modrinth.com/mod/modernfix) | 5.27.27+mc1.21.1 | LGPL-3.0 | Both | Load time, memory, bug fixes |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.6.15 | LGPL-3.0+ | Client | Faster HUD/text rendering (helps our pop-up windows) |
| [Entity Culling](https://modrinth.com/mod/entityculling) | 1.11.3 | tr7zw license | Client | Skips hidden entities |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 3.3.3 | LGPL-3.0 | Client | Optional far view |
| [spark](https://modrinth.com/mod/spark) | 1.10.124 | GPL-3.0 | Both | Profiler, for finding lag in gates |

---

## Part C: Epic combat and item/armor models

### C1. Better Combat vs Epic Fight

Both replace attack animations, so **they cannot be used together**.

| | Better Combat (+ Combat Roll) | Epic Fight |
|---|---|---|
| Look | Smooth weapon combos, swing trails, dual wield | Full-body action-game animations, stamina, dodges, guard, skills. Flashier. |
| How weapons are added | One JSON file per weapon (`weapon_attributes`) | Weapon-type datapack + animation assets, more work per weapon |
| Fit with our stat/damage system | Keeps vanilla damage events, so our buildup/damage types plug in directly | Has its own stamina and damage model; our exhaustion meter would overlap its stamina |
| Mod compatibility | Works with Iron's Spells, GeckoLib weapons, most mob mods | Many mods need Epic Fight patches/addons |
| License | ARR (fine in packs) | GPL-3.0 |

**Decision (chosen 2026-11-09): Better Combat + Combat Roll**, made "epic" with our own skill
VFX (Photon or Lodestone), Veil screen effects, Camera Overhaul and screen shake on big hits. It
is far less work for custom weapons and keeps our damage types simple. Epic Fight stays a later
option if we want a full action-game feel and accept the extra work.

### C2. How we make swords, armor and other items

| Step | Tool | Notes |
|---|---|---|
| Model + texture | [Blockbench](https://www.blockbench.net/) (free) | The standard Minecraft modelling tool. Exports Java item models and GeckoLib models. |
| Static 3D weapons (most swords) | Blockbench "Java Block/Item" format | Regular JSON item models, no library needed. Use NeoForge's `neoforge:separate_transforms` model loader so the inventory icon is a flat 2D sprite while the held weapon is the big 3D model. |
| Animated weapons/armor (glowing runes, moving parts, transforming blades) | Blockbench + **GeckoLib plugin** | GeckoLib `GeoItem` / `GeoArmorRenderer` give fully 3D armor sets and animated items. |
| Glow (System blue, crimson edges) | Emissive textures | GeckoLib supports glowmask textures (`_glowmask`); JSON models can use NeoForge's per-face `"neoforge_data": {"block_light": 15, "sky_light": 15}` for full-bright faces. |
| Pixel art | Aseprite, LibreSprite or Pixelorama | 16x/32x textures; keep one palette per rank (E grey, D green, C blue, B purple, A gold, S red/black). |
| Skill effects | Photon editor (in-game) or Lodestone (code) | Slashes, shockwaves, auras, beam attacks |
| Combat animations | Better Combat presets, or Blockbench animations exported for playerAnimator | Custom swings for signature weapons |
| Sounds | Audacity + freesound.org (CC0) | Register in `sounds.json` |

Armor stats are not tied to the model: we set them in code (armor material + attribute
modifiers via Apothic Attributes), and pick the vanilla, JSON or GeckoLib renderer per set.

---

## Part D: Checked and rejected (no 1.21.1 NeoForge build, or not suitable)

| Mod | Reason |
|---|---|
| Embeddium | Outdated for 1.21.1 (last build Jan 2025). Sodium replaces it. |
| Infiniverse (runtime dimensions) | No 1.21.1 build (stops at 1.20.4). See gate note below. |
| Structure Gel API | No 1.21.1 build (skips from 1.20.4 to 1.21.4) |
| Dungeons Enhanced | Same, no 1.21.1 |
| Champions (original) | Forge only, old versions. Use Champions-Unofficial. |
| Configured / Catalogue (Modrinth) | No 1.21.1 NeoForge on Modrinth. NeoForge 1.21.1 has a built-in config screen, so not needed. |
| Obscure API, Effective, Visuality, Particular, Status Effect Bars | No 1.21.1 NeoForge build |
| Alex's Caves, Ice and Fire, The Graveyard | No 1.21.1 NeoForge build on Modrinth |
| Custom NPCs | Forge 1.16.5 only. Use Easy NPC. |
| Pehkui | Has a 1.21/1.21.1 NeoForge build from June 2024, not updated since. Only needed if we want size-changing skills. |
| Immersive Portals | Has a NeoForge build (6.0.7, June 2025) but is heavy and often clashes with rendering mods. Our gates use a custom teleport instead. |
| Project MMO | Has its own skill/level system that would compete with ours |
| Fresh Animations | Resource pack, no NeoForge build needed but needs EMF/ETF; optional cosmetic only |

### Note on gate dungeons

NeoForge 1.21.1 cannot easily create new dimensions while the game runs, and Infiniverse is not
available. Plan for the gate thread: **one "gate" dimension**, with each gate instance placed in
its own far-apart grid slot (the same approach as [Dimensional Dungeons](https://modrinth.com/mod/dimensional-dungeons),
which has a 1.21.1 NeoForge build and is a good reference, but ARR so we do not copy its code).
Dungeon layouts come from jigsaw structures (template pools) placed on demand, so we design rooms
in Blockbench/structure blocks and let the game assemble them. In Control! handles spawns in
that dimension, and Lithostitched helps tweak structure data.

---

## Part E: Licensing and distribution

- Ship the modpack as a **Modrinth `.mrpack` or CurseForge manifest**. These point to the
  official download, so ARR mods (Better Combat, Iron's Spells, Waystones, Xaero's, etc.) are fine.
- **Never re-upload other mods' jars** (for example in a zip on Google Drive). ARR and
  CC-BY-NC-ND mods forbid that.
- FTB Quests/Library/Teams are **CurseForge only**. If we use them, the pack must be a
  CurseForge pack, or switch to Odyssey Quests for a Modrinth pack.
- NC licenses (Jade, Towns and Towers, Cataclysm, Patchouli, CTOV, Legendary Tooltips) mean
  the pack must stay free. No paywalled pack.
- ND licenses (Cataclysm, CTOV, Legendary Tooltips) mean we cannot ship modified copies of those
  mods. Datapack/config tweaks are fine.
- Our own mod should not reuse names, characters or art from the anime that inspired it.
