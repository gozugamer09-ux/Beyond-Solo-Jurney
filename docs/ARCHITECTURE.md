# Architecture

Mod id `beyondsolo`, base package `com.beyondsolo`.

## Packages

| Package | What it holds |
| --- | --- |
| `system` | Player data (`SystemData`), ranks, classes, stats, levels, stat buffs, exhaustion, events, and `SystemAPI` |
| `notify` | `SystemNotification` (pop-up content) and `Notifications.send(...)` |
| `network` | Packets: data sync, pop-ups, stat allocation, the evaluation overlay |
| `evaluation` | Evaluation Crystal block and block entity, the evaluation flow, village pool injection |
| `spawn` | Random first spawn and the "find a village" objective |
| `combat` | Elements, buildups and statuses, element damage, attributes, sync to clients, exhaustion from combat, Combat Roll hook |
| `item` | System weapons, armor (and set bonuses), remedies |
| `client` | Key bindings, client config, the theme colours (`SystemTheme`) |
| `client.gfx` | The drawing toolkit every System screen uses (see "Drawing the System UI") |
| `client.screen`, `client.hud`, `client.notify` | System window and tabs, status HUD, pop-ups |
| `client.evaluation` | The on-screen evaluation: scan read-out and the rank / class / stats reveal |
| `client.render` | World rendering: the crystal's renderer, render types, client-only effects |
| `client.combat` | Combat HUD (rail, enemy meters, damage numbers, screen tints) and world effects |
| `quest` | Quest engine: quests, templates, rank scaling, progress hooks, triggers (`quest.trigger`) |
| `client.quest` | Quest moments overlay, HUD tracker, QUESTS tab |
| `npc` | Villager requests: per-villager boards, request kinds, travellers, nests, hand-in and gossip (`NpcService`) |
| `client.npc` | The villager request window, world and HUD markers, the traveller renderer |
| `command` | `/system` admin commands (`/system quest ...` for quests, `/system npc ...` for villager requests) |
| `shadow`, `client.shadow` | The necromancer's shadow army (see "Shadow army") |
| `economy`, `client.economy` | Gold, the System storage, the System shop and the hunter license (see "Gold, storage, shop and license") |
| `gear`, `client.gear` | Bonus stat rolls, enhancement, element infusions, boss sets and signature loot, the FORGE tab (see "Gear: rolls, sets, boss loot, enhancement, infusions") |
| `crafting`, `client.crafting` | Monster cores, materials, mana stones and veins, System potions, recipes and the CRAFT tab (see "Cores, materials, potions and crafting") |
| `test` | Game tests (`./gradlew runGameTestServer`) |
| `gate` | Gates and dungeons: the gate block, gate instances and their saved data, the cave planner and builder, the boss, the reward cache, world-gen and natural spawning (`GateService` runs it) |
| `client.gate` | Gate, boss and cache renderers (GeckoLib), the gate moments overlay and boss bar, camera shake, the gate dimension's look |
| `awakening` | How a player gets the System: the stages (`AwakeningData`), the sealed gate and its tether, the trial, the Hollow Arbiter (`ArbiterEntity`), the awakening sequence and the legendary reward (`AwakeningService`) |
| `client.awakening` | The awakening overlay (called, pulled back, threshold, revive, slain, the awakening itself), the Arbiter's renderer, its attacks drawn as light |
| `skill` | Skills: definitions (`Skills`), what each one does (`SkillActions`), a player's known skills, bar, cooldowns and buffs (`SkillData`), passives, tomes, and `SkillAPI` |
| `client.skill` | The skill bar on the HUD, the Skills tab, the Skill Acquired moment, skill effects in the world (`SkillWorldFx`), the Appraising Gaze read-outs |

## Rules for new modules

1. **Read and change player progress only through `SystemAPI`.** It keeps the client in sync and fires events.
2. **Keep your own state in your own data attachment** (see `registry/ModAttachments`), not in `SystemData`.
3. **Listen to `SystemEvent`s** on `NeoForge.EVENT_BUS` instead of patching core code:
   `Awakened`, `XpGain` (cancel or change the amount), `LevelUp`, `RankChanged`, `StatAllocated`,
   `Collapsed`, `ObjectiveChanged`.
4. **Talk to the player with pop-ups:** `Notifications.send(player, SystemNotification.of(Kind.QUEST, title, body))`.
   Kinds: INFO, QUEST, ALERT, LEVEL_UP, REWARD. They show in whatever mode the player picked and land in the Log tab.

5. **Every feature owns its animations**, and they should look smooth. For a screen, use `client.gfx`. For light in
   the world, draw it in a renderer with `ModRenderTypes` instead of spawning dust particles (see "The evaluation").

## Drawing the System UI

Nothing in the System UI is a texture of a window. Shapes are drawn by one shader (`shaders/core/shape`), which
is why they stay smooth at any size and can glow.

- `Shapes`: `Shapes.box(x, y, w, h).radius(6).fillV(top, bottom).border(1, color).glow(8, color).draw(g)`.
  Also `pill`, `circle`, `ring` (with `.progress(fraction)` for progress rings and `.dashes(count, fill)`), `icon`,
  `vignette`, `rays`.
- `SysFont`: text in the System typeface at any size, in GUI pixels: `SysFont.text`, `title` (bold), `spaced`
  (tracked-out labels), `wrap`, `fit`, `width`. Minecraft draws scaled fonts without smoothing, so the font is
  registered once per pixel size and `SysFont` picks the size that matches the screen exactly. Inside a scaled
  pose, tell it with `SysFont.setContentScale(scale)`. The player can switch back to the vanilla font in the config.
- `Icons`: icon and rank-letter textures stored as distance fields, drawn with `Shapes.icon`. One small texture is
  sharp at any size and can take an outline or glow.
- `Ui`: ready-made pieces (panel, card, bar, chip, keycap, icon badge, rank emblem, divider).
- `Anim`: a frame clock and easings. `Anim.window(t, from, to)` turns a time into 0..1 for one step of a sequence.
- `Gfx`: colour helpers and a global opacity: `float old = Gfx.fade(0.5f); ...draw...; Gfx.setAlpha(old);`.
- Colours live in `SystemTheme`. Ranks and classes carry their own colour (`PlayerRank.color()`, `PlayerClass.color()`).

The fonts and icons are generated, not hand-made: `tools/gen_fonts.py` (subsets Exo 2, writes one font file per
pixel size) and `tools/gen_ui_assets.py` (icons from Phosphor SVGs, rank letters, the magic circle and the glow
textures). To add an icon, drop its Phosphor SVG into `tools/icons-src`, add a line to `ICONS` in that script, run
it, and add a constant in `Icons`.

`UiGalleryScreen` shows every piece of the toolkit on one page (dev only, see "Checking the UI without a screen").

## The evaluation

One evaluation runs on three parts that share the moments in `evaluation/EvaluationTimeline` (in ticks):

| Part | Class | Does |
| --- | --- | --- |
| Server | `EvaluationManager` | Runs the session: scan (the player must stay within 6 blocks), rolls and assigns rank and class when the reveal starts, sounds, a few particles, the pop-ups and chat line at the end |
| Screen | `client.evaluation.EvaluationOverlay` | What the evaluated player sees: the scan read-out, then rank roulette and stamp, class reveal, starting stats. Driven by `EvaluationPayload` (start, cancel, reveal with the result) |
| World | `EvaluationCrystalBlockEntity` + `client.render.EvaluationCrystalRenderer` | What everyone nearby sees: the crystal's states |

The crystal's state is not saved and has no packet of its own. The server announces it with block events
(`level.blockEvent`), which reach every client in range: `IDLE`, `SCANNING`, `REVEALING`, `LOCKED` (parameter: the
rank, the crystal flashes and takes its colour) and `CHANNELING` (parameter: the class being decided). The block
entity eases a handful of numbers towards the state each client tick, and the renderer draws from those: the
faceted shard, the shards circling it, the magic circle, the beam, trails of light, light being drawn in, and the
ring thrown along the floor on a lock.

Rules that came out of building it, worth keeping for gates and bosses:

- **Light in the world is drawn, not spawned.** `ModRenderTypes.lightTexture(texture)` adds a white-on-transparent
  texture to the scene as light, tinted by the vertex colour, through the mod's own `light` shader (the vanilla
  textured shaders cut every pixel under 10% opacity, which turns a soft glow into a hard disc). `CRYSTAL` and
  `CRYSTAL_GLOW` do the same for flat-coloured triangles. Vanilla particles are kept for things on the ground and
  for bursts, where their look fits.
- **Never spawn a real lightning bolt for show.** Even a "visual only" bolt on the server hands the vanilla
  lightning advancement to everyone nearby, charges lightning rods and cleans copper. `WorldEffects.lightning`
  makes one that exists only on the client.
- **The player watching in first person sees the overlay, not the particles.** Keep world effects on the object and
  on the ground, away from their face, and let the overlay carry the big moments.
- **Clocks.** The overlay and the renderer run on game time (ticks plus the partial tick), so they pause with the
  game and follow `/tick rate`. Decorative UI loops use `Anim`, which runs on real time.
- **Skipping** (`SkipEvaluationPayload`) only shortens the show. The result is already assigned when the reveal starts.

`/system evaluate [rank] [class]` plays the whole thing with a chosen result.

### Stat points, hidden ranks and re-evaluation

- **Stat points**: `Config progression.statPointsPerLevel` (3) per level, spent in the STATUS tab (click +1,
  shift +5, ctrl all; `SystemAPI.allocateStats`). Every point is a real buff through `StatEffects`; the stat tooltip
  lists the live bonuses (`StatEffects.bonusesOf(stat)`). Points spent by hand are counted in
  `evaluation/EvaluationRecord` (attachment `evaluation_record`), and the **Elixir of Rebirth** (CRAFT tab, C rank)
  gives exactly those back (`Reevaluation.refundAllocated`); rank, job-change and reward points stay.
- **Hidden rank thresholds** (`Reevaluation`, config `reevaluation.rankThresholds`, default D 90, C 140, B 210, A 300,
  S 420 on the total of the five stats): crossing one makes the player truly that rank (`Reevaluation.trueRank`), but
  the window, HUD and license keep the evaluated rank, and the true rank is never sent to the client. The System only
  whispers "something has changed" the first time (config `reevaluation.hintOnThreshold`).
- **Re-evaluation**: touching a crystal once a new milestone is reached (every `reevaluation.milestoneLevels` = 10
  levels) plays the evaluation again in re-evaluation mode (`EvaluationManager.beginReevaluation`): same scan, crystal
  states and overlay, but the roulette only shows ranks from the current one up, the class (or subclass) is confirmed
  instead of rolled, "RANK UP" with a C -> A line or "RANK CONFIRMED", and the summary shows growth since the last
  evaluation (`EvaluationPayload.previousRank`, ten stats). The rank only rises, can jump several ranks, and goes
  through `SystemAPI.setRank`, so skills, storage and the license follow. The attempt uses the milestone either way.
  Outside a milestone the crystal just repeats the result and names the next level. The STATUS tab's corner crystal
  glows when it is open (`ReevaluationSyncPayload`).
- Commands: `/system reeval [rank]` (play now, optionally forced), `reeval info` (total, true rank, thresholds),
  `reeval unlock`, `reeval reset`. Tests: `ReevaluationGameTests`.

## Hooks per planned module

### Quest engine (built: see "Quests" below)

### NPC quests (built: see "Villager requests" below)

### Gates and dungeons (built: see "Gates and dungeons" below)

### Combat (built: see "Combat" below)

## Combat

Better Combat gives attacks their animations, combos and reach; Combat Roll gives the dodge roll. Our weapons are
plain `SwordItem`s (`item/SystemWeaponItem`) and get a moveset from `data/beyondsolo/weapon_attributes/<item>.json`
(`{"parent": "bettercombat:claymore"}` and so on). Better Combat only needs those data files, so it is a runtime
dependency; Combat Roll is compiled against for `ServerSideRollEvents` (rolls cost exhaustion), its attributes (AGI
buffs) and its key binding (blocked during a collapse), all behind `ModList` checks in `combat/CombatSetup`.

### Elements, buildups, statuses

| Element | Buildup (`Statuses` / `Buildups`) | When full | Cure |
| --- | --- | --- | --- |
| Fire | `BURN`, 100, -8/s | Deep Burn (infinite, 1 + 1% max HP every 3 s, half healing) | Quenching Elixir only |
| Poison | `VENOM`, 35, -5/s | +1 Venom stack (I-IV), the 5th turns into Heavy Venom | Venom: milk, honey, Antidote. Heavy: Greater Antidote |
| Crimson | `CRIMSON`, 100, -6/s | 8% max HP burst + Crimson Brand (10 s: +25% damage taken, half healing, attackers heal 10%) | milk, Clotting Tonic |
| Blood | `BLOOD`, 100, -4/s | Blood pop: 10% max HP, ignores armor, enchantments and Resistance | (buildup) Clotting Tonic |

- Add buildup with `Statuses.apply(target, element, amount, source)` (or `Buildups.add`). The target's resistance
  attribute for that element shrinks it. Players and mobs work the same.
- Where buildup comes from (`combat/ElementalHits`): System weapons on melee hits (scaled by the attacker's
  `elemental_power`, which INT feeds), mobs in the entity type tags `beyondsolo:element_attackers/<fire|poison|
  heavy_poison|crimson|blood>` (amounts in the common config; `heavy_poison` applies Heavy Venom directly, for
  bosses), and any fire damage (lava triple).
- Statuses are `MobEffect`s (`combat/ModEffects`, class `CombatStatusEffect`) whose cures are NeoForge `EffectCure`s
  (`combat/ElementCures`). Leaving `milk` out of the list is what makes an effect ignore milk; leaving
  `protected_by_totem` out makes it survive a totem.
- Element damage (`combat/CombatDamage.deal`) uses the four `beyondsolo:` damage types. They are tagged to bypass
  armor, shields, knockback and the hit cooldown, and `deal` puts the target's hit cooldown back afterwards, so a
  proc in the middle of a combo neither eats the next swing nor is eaten by it. Blood also bypasses enchantments and
  Resistance (the "fixed 10%").
- Attributes (`combat/ModAttributes`, on every living entity): `fire/poison/crimson/blood_resistance` (0-0.9),
  `exhaustion_resistance`, `elemental_power`.

### Gear

- Weapons have an element, buildup per hit and a weight (dagger 0.6, sword 1, greatsword 1.8). Swing exhaustion is
  multiplied by the weight (`combat/CombatExhaustion`).
- Armor (`item/SystemArmorItem`, materials in `registry/ModArmorMaterials`) adds resistances per piece and a set
  bonus for four pieces (`item/ArmorSets`, applied on equipment change). Heavy sets make sprinting more tiring.
- Remedies are `item/ElixirItem`s: a drink that runs a cure and hands back the bottle.

### Combat on the client

- `CombatSync` sends each entity's buildups (as fractions) and status flags to the players tracking it, once per
  tick and only when something changed (`CombatStatePayload`). `CombatFx.play` sends one-off effects
  (`CombatFxPayload`: blood pop, ignite, venom stack, heavy venom, crimson burst, cure, roll). `DamageNumbers`
  sends damage numbers to the attacker, and element damage to everyone close by.
- `client/combat/CombatHud` draws, with the `client.gfx` toolkit: the player's rail of buildup gauges and status
  capsules next to the status capsule, meters and status chips over creatures in a fight, damage numbers
  (`FloatingNumbers`), the screen-edge tint of a deep burn, heavy venom or brand, and the flash of a pop or a cure.
  Things over entities are projected from the world (`WorldProjector`, matrices captured every frame) so they can
  use the same smooth shapes and font as the rest of the UI.
- `CombatWorldFx` draws the light: the crimson brand's sigil under a target, the glow of a deep burn or heavy venom,
  and the rings and bursts of pops, ignitions and cures, through `ModRenderTypes.lightTexture`. Vanilla particles
  only for bursts and embers. Game time, so it follows `/tick rate`.
- A collapsed player can't swing or roll: `CombatClientEvents` swallows the attack and roll keys (the server
  cancels the attack anyway).

Textures (weapons, armor icons and model layers, bottles, effect icons, the sigil and burst light textures) are
made by `tools/gen_combat_assets.py`.

## Arsenal and item grades

Every System weapon and armor piece has a **grade**: common, rare, epic, legendary, mythic or divine
(`item/ItemGrade`). It is stored per stack as the `beyondsolo:grade` data component (set as a default component in
`Arsenal.defaultGrades`), so enhancement or boss loot can raise one stack's grade later. The client shows it in
`client/combat/GradeVisuals`: coloured names (a moving two-colour gradient for mythic and divine), a tooltip frame in
the grade's colours, a glow behind graded items in inventory slots, and a light beam with a glowing sigil over dropped
gear (rare and up; legendary and up shed sparks too). Legendary and up also get an animated light sweep on the sprite.

- **Weapons:** `item/arsenal/Arsenal` registers 39 weapons on top of the 8 combat ones (6 common, 8 rare, 8 epic,
  8 legendary, 3 mythic, 6 divine). A weapon is a `WeaponKind` (base damage, speed, weight and Better Combat
  moveset), a grade (its `Tier`), an element and buildup, traits, a favoured class and maybe a signature. Melee
  weapons are `SystemWeaponItem`, bows are `SystemBowItem` (`ArsenalWeapon` is what both share).
- **Traits** (`item/arsenal/Trait`, effects in `combat/ArsenalHits`): frostbite (stacks to III, then freezes; the
  next hit shatters the ice for +40%), thunderclap (chain lightning), armor breaker, lifesteal, executioner, mending,
  rhythm, bulwark, backstab, twin and split shot. Trait and signature damage runs through `ArsenalHits.proc`, so it
  never procs traits or weapon buildup again.
- **Spell power** (`beyondsolo:spell_power`, carried by staffs) multiplies magic damage, which is how skill spells hit.
- **Signatures** (`item/arsenal/Signature`, effects in `combat/ArsenalAbilities`): right-click arts on mythic and
  divine weapons with an item cooldown. The divine bow's Starfall is passive (crit arrows). Each art has two beats,
  sent as `SignatureFxPayload`: the **wind-up** starts the wielder's own body animation and the cooldown, and the
  **impact** lands `Signature.impactTicks()` later (via `util/ServerScheduler`) on the animation's strike frame,
  carrying the points the visuals need (struck targets, a dash's ends, where a star hit).
  - Body animations are playerAnimator emotes (the library Better Combat ships; ours sits on a layer above Better
    Combat's attacks, `client/combat/BodyAnimator`) in `assets/beyondsolo/player_animations`, generated by
    `tools/gen_signature_anims.py` (sign conventions are in its header). Change an impact tick and the strike
    keyframe together.
  - `client/combat/SignatureFx` draws every art differently (ice-spike crystals, columns of light, a fire crescent
    and fissure, an afterimage dash with X wounds, a collapsing star, cracks and a golden dome, a flower of light, a
    falling star) with its own camera shake and wielder screen effect. Art direction (Yordan): no magic circles or
    auras under weapon arts; keep those for spells and the strongest arts, and keep particle counts light.
- **Divine weapons** are bound to one class each (`Arsenal.divineFor`). Anyone else, or a player without the System,
  deals half damage and can't use the signature (`ArsenalHits.refuses`).
- **Where they come from:** common and rare weapons have recipes; gate reward caches roll a grade by the gate's
  rank (`GateLoot.GRADE_WEIGHTS`: E gates drop common and rare, S gates drop epic and up with about 2% divine).
- **Adding a weapon:** add a line to `Arsenal`, a design (sprite, English name and lore) to
  `tools/gen_arsenal_assets.py`, and run it: it writes the texture, item model, Better Combat file and translations.
- **High-detail textures:** weapon, armor, refining stone and ward seal textures are 64x64 (worn armor layers 128x64).
  `tools/gen_hd_gear.py` renders the weapon designs above at that size with smooth multi-tone shading and grade
  dressing (runes and aura from epic, sparkles from legendary, white-gold radiance on divine), and draws the armor
  of all nine sets (`tools/hd_armor.py`) and the forge stones with the shared painter `tools/hd_paint.py`. Run it
  after the other generators, which still own models, names and the 32x32 originals.

## Quests

A quest is a self-contained instance (`quest/Quest`): texts, objectives already scaled to the player's rank,
rewards, penalty, clock and optional area are stored on it, so it keeps working if its template changes. A player's
quests live in their own attachment (`QuestData`, `ModAttachments.QUESTS`, kept through death).

| Piece | Does |
| --- | --- |
| `QuestEngine` | The lifecycle: `offer` (pop-up, accept key J or the QUESTS tab, expires), `start` (forced), `accept`, `decline`, `abandon`, `complete` (rewards through `SystemAPI`), `fail` (penalty), `progress`, `tick` (once a second per player) |
| `QuestBuilder` | Builds a quest for a player. Kill/break/gather counts and XP/stat points given as E-rank base values and scaled by `RankScaling` |
| `QuestTemplates` | Templates by id. Built in: the System line (`SET_LINE`: Proof of Strength, Provisions, Growth, Trial of the Depths), the daily, and the surprise quests |
| `QuestHooks` | Kills, block breaks, walking, death, login sync |
| `quest.trigger` | `QuestTrigger`s checked every 2 s: spawner nest (emergency), ambush (emergency), deep veins (offer). `QuestTriggers.register` for more |
| `QuestEvent` | `Started`, `Completed`, `Failed` (with a reason) on `NeoForge.EVENT_BUS` |

Objective types: `KILL`, `BREAK`, `COLLECT` (counted in the inventory, not taken), `TRAVEL` (blocks on foot), `LEVEL`,
and `CUSTOM` for other modules (`QuestEngine.progress(player, "mymod:key", 1)`). Targets are an id, a `#tag`, empty, or
`hostile`. Area-only objectives count inside the quest's area; leaving `leaveRadius` fails the quest.

Categories: `SET` (can't be abandoned; the next one starts 5 s after the last), `DAILY` (fails at the next in-game
day, a new one starts), `SURPRISE` (offered), `EMERGENCY` (forced, one at a time; dying or fleeing fails it),
and `NPC` / `GATE` reserved. Failing costs exhaustion plus weakness and slowness (half when abandoned), harsher with rank.

The first System quest and the daily are handed out once the player is evaluated and the reveal is over.

On the client (`client.quest`): `QuestOverlay` plays the big moments (accepted: line of light opens into the window,
emblem pops, title types, then it flies into the tracker; complete: seal stamps down with shockwaves, sparks, rays,
rewards count up; failed: red flash, shake, glitching header, struck-through title, penalty types out). New pop-ups
wait while one plays, and the hide key skips it. `QuestTracker` is the HUD list under the status capsule, and
`QuestLogTab` replaces the core Quests tab. The moments also land in the Log tab.

`/system quest list|start <id>|offer <id>|trigger <id>|accept|progress <n>|complete|fail|reset` for testing.

## Villager requests

Right-click any villager (or wandering trader) to open its request window; sneak + right-click trades as usual.
Each villager keeps a board (`npc/NpcBoard`, attachment `NPC_BOARD`): 2 to 4 requests rolled from its UUID, the day
and its profession, so every player sees the same board and it refreshes at dawn. A player holds at most 3 villager
requests (`NpcQuests.MAX_HELD`). Everything scales with the player's rank through `QuestBuilder`.

| Kind | What it asks | Ends |
| --- | --- | --- |
| `GATHER` | Bring items that fit the profession (librarian: books and feathers, farmer: crops...) | Hand in: items are taken |
| `HUNT` | Kill monsters of the player's tier | Report back to the villager |
| `CLEAR` | Clear a nest marked about 45 to 90 blocks out; the nest (helmeted mobs, a leader) spawns when the player nears | Report back |
| `ESCORT` | A traveller (`TravelerEntity`) follows the player to a marked spot; monsters target it; it catches up when left behind and steps out of walls or water | On arrival |
| `DELIVER` | A sealed parcel goes to the nearest other village (or a far spot); using it there delivers it | On delivery |

Quests got two new engine pieces for this: a `QuestGiver` (who to return to) and the `READY` status, which waits for
the hand-in (`QuestEngine.turnIn`). `COLLECT` objectives can consume their items. Finishing raises the villager's
gossip (better trade prices) and the window's rapport bar; failing lowers it, and a villager that was failed refuses
new requests until the next day. If the giver dies, its open requests are released.

On the client, `NpcBoardScreen` shows the villager's portrait and cards that fold in, get an ACCEPTED stamp, or fly
the handed-in items into the portrait with gold shockwaves. `NpcMarkers` draws the floating `!` (has a request), gold
`?` (ready to hand in), name plates, waypoint labels and a light beam over marked spots.

`/system npc open|reroll|spawn <profession> [level]|give <kind>|ready|nest` for testing.

## Gates and dungeons

A gate is a portal (`gate/GateBlock` + `GateBlockEntity`) with a rank, E to S. Each one owns a `GateInstance`
(saved in `GateData`, overworld saved data): its seed, its slot in the gate dimension, and how far the clear has got.

| Piece | Does |
| --- | --- |
| `GateTheme` | One per rank (plus `TRIAL`, the awakening trial's; monsters carry the theme's ordinal in `GateService.MOB_TAG`): the cave's rock, floor, accents and lights, the element, the monsters, how many rooms, boss stats, clear XP |
| `DungeonLayout` | The plan of a cave from its seed: a winding chain of caverns, side caves with a chest, the boss arena at the end, curved tunnels between them. Pure logic, tested without a world |
| `DungeonBuilder` | Carves the plan into `beyondsolo:gate_depths` (a flat dimension of solid rock, `data/beyondsolo/dimension`) a slice at a time (`BUDGET` blocks per tick), then decorates: wall lights, fill light, stalagmites, pillars round the arena, side-cave loot |
| `GateService` | Registering gates, going through (enter / exit with a short freeze while the overlay plays and the cave finishes building), waking monster packs as players reach each cavern, waking the boss, the boss's defeat (reward cache, exit gate, XP, quest progress), closing cleared gates |
| `GateBossEntity` | GeckoLib boss: dormant until someone enters the arena, then a roar intro; swipe and slam attacks with the gate's element; enrages under half health (adds, faster); long death animation |
| `RewardCacheBlock(Entity)` | Falls in a beam of light, lands, opens with a lid animation and bursts out `GateLoot` |
| `GateFeature`, `GateSpawner` | World-gen (`worldgen/placed_feature/gate.json`, rarity 1 in 220 chunks, rank grows with distance from 0,0) and gates tearing open near players (config `gates.openChancePerMinute`) |

Instances sit `SLOT_SPACING` (2048) blocks apart along x in the gate dimension; `GateData.bySlotPos` finds the gate
for a position there. A cleared cave closes once nobody is left in it, and its gate in the world collapses.

Hooks: the gate quest (`QuestCategory.GATE`, started on entering, fails if you die inside) uses CUSTOM objectives
`beyondsolo:gate_mob` and `beyondsolo:gate_boss`; any quest can use `beyondsolo:gate_clear` (the System line's
Trial of the Depths does). Gate monsters carry `MOB_TAG` and add their gate's element buildup on hit.

On the client (`client.gate`): `GateRenderer` (the GeckoLib ring of rock with glow masks, plus the vortex drawn as
light: it tears open as a slit and collapses with a flash), `GateBossRenderer` (magic circle, enrage, the pillar of
light on death), `RewardCacheRenderer` (beam, fall and bounce, landing ring, the burst), and `GateOverlay` (entering,
arriving, leaving, boss intro with cinema bars, defeated stamp, dungeon cleared, the boss bar, camera shake).
Models, animations and textures come from `tools/gen_gate_assets.py`.

The game-test server does not load datapack dimensions, so there (only) caves are carved into the overworld and
walled in first (`GateService.dungeon`, `DungeonBuilder.walledIn`).

`/system gate spawn [rank]|near|enter|boss|wake|kill|cache [rank]|list` for testing.

## The awakening

Players start without the System. `SystemData.hasSystem()` is the lock: without it there are no pop-ups
(`Notifications.send` drops them), no System window, HUD or keys, no quests, and the Evaluation Crystal stays dark.
`isAwakened()` still means "evaluated" and implies `hasSystem()`. Check `SystemAPI.hasSystem(player)` before showing
a player anything that belongs to the System; check `isAwakened` for anything that needs a rank or a class.
`SystemEvent.SystemGranted` fires when it is bound. Config `awakening.required = false` gives it at first login.

| Stage (`AwakeningData.Stage`) | What happens |
| --- | --- |
| `DORMANT` | No System. `GateSpawner` opens D-rank gates near such players (`awakening.gateChancePerMinute`). Gates let them in; players who have the System but are not evaluated are still turned away |
| `CALLED` | Set by `AwakeningService.gateCleared` when a gate of rank D or higher is cleared with them inside. When they step out, a sealed gate (`GateService.spawnSealedGate`, a `GateInstance` with an `owner`, theme `GateTheme.TRIAL`) opens in front of them. `tether` pulls them back past `awakening.tetherRadius` and re-opens the gate beside them if they get far away or change dimension. Only the owner can enter |
| `TRIAL` | Inside. Every player death in a trial is cancelled (`LivingDeathEvent`, highest priority), so nothing is dropped: before the Arbiter's hall they stand up again at the last cavern reached (`GateInstance.checkpoint`), in the hall the awakening begins |
| `AWAKENING` | `begin` freezes the player and plays `AwakeningPayload.AWAKEN` on their client (14.6 s). The server follows the same clock: out of the cave at `TELEPORT_TICK` (the screen is black), System bound at `GRANT_TICK`, released at `END_TICK`, then `FirstSpawnHandler.systemIntro` (welcome pop-up, "find a village"). Logging out halfway finishes it at once |
| `DONE` | Has the System. The evaluation follows as before |

The Hollow Arbiter (`ArbiterEntity`, a `DungeonBoss` like `GateBossEntity`, so the boss bar and intro work for both)
kneels dormant and invulnerable until its challenger is well inside the hall, rises for `INTRO_TICKS`, then uses
three attacks: a cleave across everything in front of it, a sunder that sends a ring along the floor (jump it), and
judgment, marks of light under each player that burst a moment later. Under half health it is unbound: faster,
double rings, three marks. 1500 health, heavy armour, arrows do little. Killing it (`arbiterSlain`) drops a cache
that holds one `ModItems.LEGENDARIES` weapon (`LegendaryWeaponItem`), then the awakening plays with
`arbiterSlain` set, which also gives the title `title.beyondsolo.arbiter_slayer`.

Its model, animations and textures, the sealed gate's texture and the legendary weapons' icons come from
`tools/gen_awakening_assets.py`. The server's timings (wind-ups, `INTRO_TICKS`, `DEATH_TICKS`) and the animation
lengths in that script must be changed together. In GeckoLib files a positive x rotation pitches a bone forward
(top to the front, a hanging limb's end to the back); the script's poses are written as a body moves and converted.

Testing: `/system trial status|reset|call|enter|arbiter|wake|fall|slay|grant`. `reset` takes the System away again
and closes the old trial; `grant` skips the trial.

## Skills

A `Skill` is a definition: active or passive, its class (`null` means every class), grade, unlock level, mana cost,
cooldown, element (`SkillElement`) and, for actives, the `Action` that runs it. `Skills` registers them all;
`SkillActions` holds what they do and the numbers they share. What a player knows lives in `SkillData`
(attachment `ModAttachments.SKILLS`, kept through death, mirrored to the client): level 1 to 10 and proficiency per
skill, the four bar slots, cooldowns, and short buffs other skills read (stealth, the empowered strike).

Everything goes through `SkillAPI`:

| Call | What it does |
| --- | --- |
| `grantUnlocked(player)` | Teaches what class, rank and level have unlocked. Runs on awakening, level-ups, rank changes, login. A higher rank unlocks skills a few levels earlier |
| `learn` / `addLevels` / `addProficiency` / `forget` | Single skills. Learning an active puts it in the first free bar slot |
| `cast(player, skill)` / `castSlot` | Checks awakened, not collapsed, cooldown, mana; posts the cancellable `SkillEvent.Cast`; runs the action; spends mana, starts the cooldown, adds fatigue and 1 proficiency. An action that finds no target returns false and costs nothing |
| `grantAdvanced(player)` | **The job change hook**: teaches the class's `advanced` skills (one per class so far) |
| `refreshForClass(player)` | After a class change: forgets other classes' skills, grants the new class's |
| `setBuff` / `hasBuff` / `clearBuff` | Timed flags, read in `SkillHooks` |

Skills grow by use (1 proficiency per cast; passives gain with player levels), by tomes and by `addLevels`. Power
scales with level and with the stat the skill names (`SkillActions.mult`, `statBonus`). Passives are transient
attribute modifiers reapplied by `SkillPassives.apply` whenever skills change. `SkillHooks` wires the rest:
damage modifiers (marks, stealth openers, weak spots), mobs losing track of a stealthed player, upkeep each second.

Skill tomes (`SkillTomeItem`): an unread one teaches a random fitting skill the player lacks, or raises one they
have; a named one (`ModComponents.SKILL`) teaches that skill. Gate caches drop them (`GateLoot`), two System quests
reward them. Events for other modules: `SkillEvent.Learned` (with its source), `LevelUp`, `Cast`.

Adding a skill: one `Skills` entry with its element, an action in `SkillActions` that sends a `SkillFxPayload`
kind for the look (`client.skill.SkillWorldFx` draws each kind with the light textures and particles; add a kind
for a new look), three lang lines (name, `.flavour`, `.desc`), an icon in `tools/gen_ui_assets.py`
(`skill_<path>`), and a test. Subclass skills live apart (see "Job change"). Every skill hit goes through `SkillActions.hit`, which is
where combos, range passives and elements apply; a new damaging skill should use it rather than `hurt`.

### Combos and element reactions

Both live in `SkillCombat` (server side, nothing saved).

**Combos.** Casting a different skill within 5 seconds of the last one builds a chain, up to 6. Each link adds 8%
to skill damage (`comboMultiplier`). The same skill twice in a row keeps the chain where it is; a cast that finds
no target does not count. `SkillAPI.cast` calls `begin` / `undo` / `commit`; `SkillEvent.Combo` fires from 2 up.

**Reactions.** A skill hit leaves its `SkillElement` on the creature for 8 seconds (`SkillCombat.aura`). A hit with
another element that reacts with it sets off a `SkillReaction` and uses the element up; otherwise the new element
replaces the old. Physical leaves nothing and reacts with nothing. The 15 reactions, each a damage bonus on the
hit plus a side effect in `SkillCombat.react`:

| Reaction | Elements | What it does |
| --- | --- | --- |
| Surge | water + lightning | +50%, bursts onto creatures within 3.5 blocks and stuns |
| Firestorm | air + fire | +60%, heavy burn that spreads to creatures nearby |
| Steam | fire + water | +30%, a cloud that blinds, slows and weakens |
| Wildfire | fire + nature | +40%, a deep lasting burn |
| Magma | fire + earth | +40%, slows and ignites |
| Frost | water + air | +20%, frozen in place |
| Overgrowth | water + nature | +20%, rooted; mends the caster |
| Eclipse | light + shadow | +100% |
| Tempest | lightning + air | +30%, arcs to three more creatures |
| Sandstorm | earth + air | +20%, blinds and slows around it |
| Overload | lightning + fire | +40%, a blast that throws creatures back |
| Blight | shadow + nature | +30%, venom and weakness |
| Shatter | earth + lightning | +50%, stuns |
| Radiance | light + fire | +30%, mends allies nearby |
| Resonance | arcane + anything else | +30%, returns mana |

Reaction splash damage carries no element, so reactions never chain off each other. `SkillEvent.Reaction` fires
for quests and later systems (elemental weaknesses of monsters can hook `SkillCombat.strike`). A new reaction is
one `SkillReaction` entry, a case in `SkillCombat.react` and a `skill_reaction.beyondsolo.<name>` lang line.
Weapon infusions should call `SkillCombat.strike` on their hits to join in.

On the client: keys Z, X, C, G cast the bar (rebindable, `SkillActionPayload`), `SkillBar` shows cooldown sweeps
and refusals, `SkillOverlay` plays Skill Acquired and the level-up banner (waits for the evaluation, quest and gate
overlays; the hide key skips it), `SkillsTab` is the Skills page of the System window (the gem grid adds columns as
a class gains skills), `ComboHud` draws the combo counter and the reaction names that pop out of creatures.

`/system skill list|learn <skill> [level]|levelup <skill> [n]|forget|cast|slot <n> <skill>|cooldowns|unlocked|advanced|tome [skill]|reset`
for testing; `screen skills` in the UI driver opens the tab.

## Job change

Code in `job/` (server) and `client/job/` (client); the module registers itself from `JobModule.init` with its own
attachment, item, payloads, config (`beyondsolo-jobs.toml`) and lang namespace (`assets/beyondsolo_jobs/lang`, made
by `tools/job_lang.py`). Icons and the key sprite come from `tools/gen_job_assets.py`.

- **Subclasses** (`Subclass`): 5 per base class, 30 in all. One per class is extremely rare (1% by default, doubled
  for a flawless trial) and much stronger: S-grade skills, 30 bonus stat points instead of 10, a second active.
  The other four share the rest evenly. Necromancer is a normal Mage subclass and the only one with
  `canRaiseShadows()` (for the shadow army).
- **Unlock**: at level 20 (config) a player who has the System gets a notification, the "Job Change" quest and the
  Key of Ascension (`JobAPI.unlockIfReady` on level-up). A lost key is handed back on login/respawn.
- **The trial** (`JobTrial`, one `Session` per player, ticked from the server tick): countdown, then a domed hall
  (`TrialArena`, carved in the gate dimension on lane z=60000, one slot per running trial), a camera glide, three
  waves of Echoes, the Ascendant Warden (a `GateBossEntity` with an unregistered `GateInstance`), and the verdict:
  an orbit shot while the client plays the reveal (`JobRevealOverlay`, timing in `JobRevealTimeline`). A blow that
  would kill the player ends the trial instead (no death, no totem used, a retry timer); logging out abandons it
  and the player is sent home on the next login. Surprise quests and the gate dimension's "no gate, go home" rule
  leave trial players alone.
- **Job change** (`JobAPI.changeJob`): bonus stats along the subclass's weights, the base class's advanced skills,
  the subclass's own skills (`SkillAPI.grantSubclass`); skills of any other subclass are forgotten.
- **Subclass skills** (`skill/SubclassSkills`, `SubclassActions`, `SubclassPassives`): a skill with a `subclass` set
  belongs to that subclass only (`Skills.forSubclass`); damage goes through `SkillActions.hit`, so combos and
  element reactions apply. The job change itself teaches the starters (`Skills.startersFor`: the `advanced` ones).
- **Subclass skill sets** (`skill/SubclassKit` and one file per base class, `WarriorPath`, `AssassinPath` and `MagePath` so far): every subclass
  grows to about 12 skills (8 actives, 4 passives; the ultimate last). These are not `advanced`: they carry an
  unlock level (22 to 50) and `SkillAPI.grantUnlocked` teaches them as the player levels, only to players of that
  subclass; a late job change catches up at once. A path file holds its skills, their numbers and actions, and
  its passive hooks (`strikeBonus`, `afterStrike`, `whenStruck`, `tickSecond`, `onKill`, `refusesDeath`), which
  `SubclassPassives` calls. `SubclassKit` has the builders (`active`, `passive`, `bonus` for attribute passives)
  and the building blocks (`cone`, `along`, `foes`, `dash`, `leap`, `repeat`, `later`, `push`, `pull`,
  `aimGround`). To add a class's sets: copy the shape of `WarriorPath`, call its `init()` from
  `SubclassSkills.init()` and its hooks from `SubclassPassives`, add names to `tools/job_lang.py` and icons to
  `tools/gen_job_assets.py`. World effects added for these sets (`SkillFxPayload.Kind`): `CLEAVE` (falling
  blade), `BANNER`, `BLADES` (circling swords), `CRACK` (fissures), `GEYSER`, `QUAKE`, `BEAM` (a steady beam), `ORB` (a hanging sphere), `THUNDER` (a bolt from the sky).
- **Cutscene camera** (`client/job/CutsceneCamera`, `CutscenePayload`): glide and orbit shots with letterbox bars.
  The camera follows a client-only marker entity, so the player's body shows. A shot holds on a black curtain until
  the world under it is drawn. Any system can send shots; `/system job camera` tries one anywhere.
- **Commands** (`/system job ...`, level 2): unlock, trial [now], jump 1|2|3|boss|verdict [subclass],
  set/reveal &lt;subclass&gt;, odds [rolls], info, reset, camera.

## Shadow army

Code in `shadow/` (server) and `client/shadow/` (client); the module registers itself from `ShadowModule.init` with its
own attachment (`shadow_army`, kept through death), entity type (`beyondsolo:shadow`), payloads, entity type tags
(`data/beyondsolo/tags/entity_type/shadow/`) and lang namespace (`assets/beyondsolo_shadows/lang`).
**Only a necromancer** (`Subclass.canRaiseShadows()`, checked by `ShadowService.canRaise`) gets any of it.

- **Souls.** A creature killed by a necromancer or one of their shadows leaves a soul for 20 s (a boss's for 60 s),
  only for that player. Tag `shadow/no_soul` leaves none. The raise key (H) next to a soul starts the extraction
  ritual (2 s); each soul has 3 tries. The chance (`ShadowService.chance`) falls with the creature's health against
  the player's level and INT; bosses are much harder. `/system shadow next success|fail|random` decides the next try.
- **Shadows** (`ShadowRecord` in `ShadowData`): the creature type, a role (warrior; caster from tag `shadow/casters`,
  bolts at range; mount from tag `shadow/mounts`), a rank (`ShadowRank`: Husk, Soldier, Vanguard, Knight, Captain,
  Commander, Marshal), level 1-10 inside the rank, and the health, damage and speed it was extracted with. Weak
  creatures start as Husks, strong ones higher. Dungeon bosses (`DungeonBoss`) and tag `shadow/bosses` become named
  elites (`ShadowNames`) that start at Knight to Commander, use Dread Slam and give nearby shadows +15% damage.
- **Growing.** A shadow gets XP for its kills (the creature's max health) and a quarter of that for its master's kills
  nearby; filling level 10 climbs a rank (`ShadowService.addXp`, rank-up moment and pop-up). The master gets half
  the usual System XP for their shadows' kills. Stats: base x rank power x (1 + 5%/level) x (1 + 1%/INT over base).
- **In the world** (`ShadowEntity`): rises out of the ground (`RISE_TICKS`), follows its master and slips to them when
  left behind, fights whatever hurts its master or what they hurt and monsters near them, never players or allies.
  Its hits go through `SkillCombat.strike` with the shadow element, so they can set off reactions. Never saved with
  the world; it goes away when its master logs out, and follows through dimension changes. A fallen shadow rests
  30 s (boss 90 s). Calling one costs mana (`summonCost`). Army size `capacity` and shadows out at once
  `activeLimit` grow with level and INT.
- **Mounts**: the mount key (Y) calls the strongest mount and seats the player (again to get off); right click works
  too. Space jumps (`PlayerRideableJumping`). The first job change into a necromancer gives the steed Duskmane.
- **Client**: `ShadowRenderer` draws the creature it was through `ShadowTint` (every vertex colour multiplied to a
  violet-black), burning eyes, aura and a ring; `ShadowWorldFx` draws souls, the ritual, the extraction burst,
  refusals, summons, recalls and rank-up pillars; `ShadowHud` the extract prompt, ritual ring, banners (cinematic
  for bosses) and the army counter; `ShadowsTab` the System window page (necromancers only).
- **Keys**: H extract / call the army, U recall, Y mount.
- **Commands** (`/system shadow ...`, level 2): info, give &lt;entity&gt; [rank] [boss], soul &lt;entity&gt; [boss],
  next, rise, summon, recall, mount, xp &lt;n&gt;, rankup [index], kill, reset.

## Gold, storage, shop and license

Code in `economy/` (server) and `client/economy/` (client); the module registers itself from `EconomyModule.init` with
its own attachment (`hunter`, kept through death: `HunterData`), item (`hunter_license`), data component
(`beyondsolo:license`), menu type (`system_storage`), payloads and lang namespace (`assets/beyondsolo_economy/lang`).
The license sprites and model come from `tools/gen_economy_assets.py`.

- **Gold** (`Economy`): the System's currency. Change it only through `Economy.add / spend / set`, which send the
  change to the client (`GoldPayload`, with an `Economy.Reason`). Sources (`EconomyHooks`): hostile monsters killed by a
  player with the System (max health / 5, gate monsters x(1.5 + 0.6 x gate tier)), quests (a quarter of their XP),
  gate clears (`GATE_GOLD` by rank, called from `GateService.bossDefeated`), and selling to the shop.
- **System storage** (`SystemStorage`, `StorageMenu`): 27 slots at E, +9 per rank, +9 per expansion bought (4 at
  most), 108 at most, in pages of 54. Slots past the capacity are locked (things can still be taken out). Menu
  buttons: prev, next, stack in (inventory items onto matching storage stacks; the hotbar is left alone), sort.
  Opened with the STORAGE tab of the System window or the I key; `StorageScreen` draws the System window around it,
  and picking another tab goes back to `SystemScreen` (`SystemScreen.openInstant`). `SystemScreen.layoutTabs`
  shrinks the tab padding when there are many tabs.
- **System shop** (`ShopCatalog`, `ShopService`, `client/economy/ShopTab`): categories Today's deals (3 entries a
  day at -30%, the same for everyone, picked from the day number), Supplies, Remedies, Weapons (arsenal common to
  epic; legendary and up only come from gates), Armor, Special (skill tome, totem, storage expansion with doubling
  price, a new license copy). Entries can need a rank and have a daily limit (`HunterData.purchases`, reset per
  in-game day). Selling (`SellPrices`): graded gear by grade (worn gear less), gate treasure, monster drops; a test
  checks that nothing in the shop sells back for more than its sale price.
- **Hunter license** (`HunterLicenseItem`, `LicenseInfo`): issued `LICENSE_DELAY` ticks after the evaluation (or at
  login for hunters evaluated before licenses existed). The card carries a snapshot (name, rank, class, subclass,
  level, gates cleared, highest gate, number, issue day) that the owner's own card refreshes every second while in
  their inventory; other people's cards are never rewritten. Right click shows `LicenseScreen` (flip in, photo
  scan, rank stamp with shockwave, holographic sheen that follows the mouse). The sprite follows the rank through
  the item property `beyondsolo:rank`. A rank change sends a "license renewed" pop-up.
- **Gold on the HUD** (`GoldHud`): only shows when gold changes, on the right edge a third of the way down (clear of toasts and effect icons):
  the balance rolls, coins spark, and "+18 Monster" style lines float under it (same-kind gains close together add up).
- **Commands** (`/system gold|storage|shop|license ...`, level 2): gold info|add|set, storage open|expand [n]|fill|clear,
  shop buy &lt;entry&gt; [n]|sell &lt;slot&gt;|reset, license give|issue|clear &lt;rank&gt; [n]|reset. UI driver: `screen storage|shop`,
  `shop buy|sell <category> [entry]`, `storagebtn <id>`, `license`.

## Gear: rolls, sets, boss loot, enhancement, infusions

Code in `gear/` (server) and `client/gear/` (client); `GearModule.init` registers its items, data components
(`beyondsolo:bonus_stats`, `beyondsolo:enhance`, `beyondsolo:infusion`), the six boss armor materials, the FORGE
creative tab and payloads (`ForgeActionPayload`, `ForgeResultPayload`). Lang lives in `assets/beyondsolo_gear/lang`.
Textures come from `tools/gen_gear_assets.py`.

- **Bonus stats** (`BonusStat`, `GearRolls`): every dropped or rewarded weapon and armor piece rolls random bonus stats
  by grade (common 0-1, rare 1, epic 2, legendary 3, mythic 4, divine 5), each scaled by `1 + 0.35 x tier`. Stats
  fit the gear kind (`GearKind`: melee, bow, armor), e.g. crit chance on weapons, max health or a resistance on armor.
  Armor from gate caches can roll one grade above the gate (`GearRolls.dropGrade`, capped at legendary).
  `GearAttributes` turns rolls and enhancement into attribute modifiers through `ItemAttributeModifierEvent`.
- **Enhancement** (`Enhancement`, `ForgeService.refine`): +1..+10. Chance for +1..+10: 100/95/90/80/70/60/50/40/30/20 %, damage
  +3 % up to +50 % (bows add arrow damage, armor adds armor and toughness). Costs gold (by grade and level) and
  refining stones. From +7 a failure drops one level; a **ward seal** (consumed only when it saves you) prevents that.
- **Infusions** (`Infusions`, `ForgeService.infuse`): an element crystal plus gold sets a weapon's element. 15 % of the
  hit lands again as that element through `SkillCombat.strike`, so it applies auras and triggers the skill system's
  reactions for every class; arrows carry the bow's infusion (`Infusions.tagArrow`).
- **Armor sets** (`item/ArmorSets`): every set has a 2-piece and a 4-piece bonus and may carry a perk. Boss sets
  (`BossSets`, one per gate rank E..S: Stonehide, Mirefang, Rimeguard, Gloomreaver, Emberwrought, Sovereign) add
  perks (`SetEffects`: Unbroken, Venom Skin, Frost Aegis, Night's Edge, Living Flame, Crimson Dominion) and an
  innate element for un-infused weapons.
- **Boss signature loot** (`BossLoot`): each rank's boss table drops its set pieces, a chance at its weighted signature
  weapons, refining stones, crystals and (C and up) ward seals. `GateLoot` adds it to the reward cache, which calls out
  signature drops with a pop-up (`BossLoot.isSignature(stack, rank)`).
- **Forge tab** (`client/gear/ForgeTab`, order 125 in the System window): pick gear, REFINE or INFUSE, see chances and
  costs; the ring charges with hammer strikes, then plays success rays and a +N stamp, cracks and a shake, the ward
  hexagon, or the infusion spiral. The shop sells refining stones, ward seals and crystals (category FORGE);
  `SellPrices` values gear more with enhancement and rolls (refining never pays back more than it costs).
- **Commands** (`/system gear ...`, level 2): info, roll &lt;grade&gt;, enhance &lt;0-10&gt;, refine [ward], infuse
  &lt;element|none&gt;, bossloot &lt;rank&gt; [n], set &lt;name&gt;, materials. UI driver: `screen forge`,
  `forge <slot> [refine|infuse] [element] [ward|noward]`, `forgego`.

## Enemy roster and boss pool

Gates no longer hold the same creatures and the same boss every time.

- **Creatures** (`enemy/`): six of the gate's own kinds (`MonsterKind`: gloomfang, broodling, shardback, hollow acolyte,
  ashen sentinel, emberwisp), one entity class `GateMonsterEntity` with a behaviour per style (hound, swarm, charger,
  caster, guard, bomber), mixed with vanilla mobs per rank in `EnemyRoster`. A gate's mix comes from its seed
  (`GateInstance.mix()`): three or four kinds, at least two of them the gate's own, plus an affinity.
- **Affinity** (flame, frost, venom, storm, shade, blood): texture, aura, what its hits leave, and an elemental
  strength and weakness (`Affinity.multiplier`, applied to skills in `SkillCombat.strike` through `EnemyHooks.skillMultiplier`).
- **Variants** (`Variant`: normal, veteran, elite, champion) scale health, damage, size and rewards; elites and champions
  carry affixes (`Affix`: swift, armoured, vampiric, frenzied, volatile, splitting, warded, thorned). A pack's first
  creature is its leader. Traits live in the entity's persistent data (`EnemyTraits`) and reach the client by
  `EnemyTraitsPayload` for name plates and auras.
- **Bosses** (`gate/BossKind`): brute, broodmother, alpha, knight, magus and wyrm, each with a rank range, its own
  model, hitbox, affinity, intro, death and scripted moves with ground telegraphs (`BossMoves`, `EnemyFx`). A gate
  picks one from its seed; about one gate in twelve holds an **ascended** boss (tougher, bigger, gold title, more loot).
- **Loot hook**: each boss kind rolls `data/beyondsolo/loot_table/gate_boss/<kind>.json` into the reward cache
  (`GateLoot.signature`), then posts `GateLoot.BossLootEvent` with a mutable loot list for other modules.

Reuse for dungeon breaks and events: `EnemyRoster.random(rank, r)`, `Enemies.roll(...)` and
`Enemies.spawn(level, rank, type, pos, traits, -1)` for creatures; `BossKind.roll(rank, r)` and
`GateBossEntity.configure(rank, kind, ascended)` then `wake()` for a boss outside a gate (its death opens nothing).

Test commands: `/system enemy spawn <kind> [rank] [variant] [affinity]`, `/system enemy boss <kind> [rank] [ascended]`,
`/system enemy mix [rank]`, `/system gate spawnboss <rank> <boss> [ascended]`. Assets come from
`tools/gen_enemy_assets.py`, texts from `tools/enemy_lang.py`.

## Cores, materials, potions and crafting

Code in `crafting/` (server) and `client/crafting/` (client); `CraftingModule.init` registers the items, the three vein
blocks, the `beyondsolo:core_affinity` data component, the CRAFT creative tab and payloads (`CraftActionPayload`,
`CraftResultPayload`, `CraftStockPayload`). Lang lives in `assets/beyondsolo_crafting/lang`; textures, models and
blockstates come from `tools/gen_crafting_assets.py`.

- **Monster cores** (`MonsterCoreItem`): one item per rank E..S plus a boss core per rank. A core keeps the affinity of
  the creature it came from (`core_affinity`, shown as "Frostbound C-rank Monster Core"); some recipes ask for one.
- **Drops** (`CraftDrops`): roster creatures (anything `Enemies` made) killed by a hunter with the System drop a core of
  their rank (30% normal, 50% veteran, always for elites, two for champions) and their kind's material (45% x the
  variant's reward multiplier). Other hostile monsters: 4% for an E-rank core. Bosses add a boss core (two when
  ascended), 2-4 cores, materials and mana stones through `GateLoot.BossLootEvent`; a boss outside a gate drops them.
  A core tearing free flashes a `NOVA` in its colour.
- **Materials**: one per gate creature kind (gloomfang fang, brood silk, shard carapace, hollow essence, ashen plate,
  ember cinder), `CraftingModule.MATERIALS`.
- **Mana stones** (`ManaStoneItem`: shard 15, stone 40, greater 100 mana): hold use to absorb (fails when mana is full),
  plays `CombatFxPayload.Kind.ABSORB`. They come from **mana veins** that `DungeonBuilder.decorate` sets into cavern and
  side-cave walls (`ManaVeins.place`, size by gate rank: E/D lesser, C/B plain, A/S greater). The vein model lays a
  fully lit seam layer over the rock (NeoForge per-element `block_light`), so they glow in dark caves. Drops in
  `loot_table/blocks/<vein>.json` (fortune, silk touch).
- **System potions** (`SystemPotionItem`): healing, greater healing, supreme healing elixir, mana, greater mana, stamina
  tonic (-40 exhaustion, not while collapsed), draught of might, ironskin draught. Drinking plays `RESTORE` (two rings
  climb the drinker, a spiral of motes). The combat remedies (quenching elixir for deep burn, greater antidote for
  heavy venom, antidote, clotting tonic) have recipes too.
- **Recipes** (`SystemRecipes`, same list on both sides): categories POTIONS, FORGE (refining stones, ward seals, the
  nine infusion crystals), CORES (four cores fuse into one of the next rank, mana stone fusion, mana extraction), GEAR
  (the eight epic arsenal weapons, forged around a C-rank boss core and rolled with `GearRolls`). Each needs a rank and
  some gold. `CraftingService.craft` takes ingredients from the inventory, then the System storage, using plain cores
  before cores with an affinity.
- **CRAFT tab** (`client/crafting/CraftTab`, order 127): categories and recipes on the left (can make N / missing /
  needs rank), the result in a crucible on the right with have/need per ingredient, an amount stepper (shift for 10),
  gold cost and CRAFT. Crafting: the ingredients fly into the crucible along curves, it draws in and flares, the
  result springs out with "+N" and rays; a refusal shakes the panel and says why. Dropped cores and mana stones glow
  in their colour (`CraftClientEvents`); boss cores get a short light column.
- **Economy**: cores sell by rank (`SellPrices.CORE`, boss cores x6), mana stones 6/30/120, materials 8, potions 15.
  The shop's REMEDIES sell four of the potions (dearer than brewing them). Numbers are first guesses for the balancing pass.
- **Commands** (`/system craft ...`, level 2): make &lt;recipe&gt; [n], cores &lt;rank&gt; [n] [affinity], bosscore &lt;rank&gt; [n],
  bossloot &lt;rank&gt;, kit, drainmana. UI driver: `screen craft`, `craft <recipe> [amount]`, `craftgo`.

## World generation

`evaluation/VillageInjector` adds `beyondsolo:evaluation_zone` to the five vanilla village house pools when the
server starts (weight from config, default 3). In testing, 7 of 8 plains villages got at least one zone. A big
village can get more than one (a savanna village in testing had three). They all work the same.
The structure file is generated by `tools/gen_evaluation_zone.py`; you can also rebuild it in game and save it with
a structure block. Lithostitched is available if we want datapack-driven injection for modded villages later.

## Checking the UI without a screen

`./gradlew runClient -PuiTest` starts the client with a small remote control (`client/UiTestDriver`). Write
commands, one per line, to `runs/client/uitest/cmd.txt` and it runs them in order: open a System page, run a server
command, play a crystal state, change the GUI scale, slow animations down, wait, take a screenshot. The full list
is in the class comment. The driver does nothing unless the game was started that way.

The scripts in `tools/uitest` wrap this up for a machine with no monitor: they start a server and that client on a
virtual screen, send the commands, and take screenshots and slow-motion videos. `tools/uitest/README.md` shows how.
Look at every new screen and animation that way before merging it; code alone does not show how it looks.

## Tests

`test/SystemGameTests` covers: the structure has its crystal, evaluation by the crystal, awakening hands out bonus
stats and buffs, XP levels up and stat points raise attributes, no XP before evaluation, exhaustion collapse and
recovery, and the blood buildup pop. `test/CombatGameTests` covers: burn into deep burn, deep burn ignores milk and
totems but the elixir cures it, venom stacking into heavy venom and which cures work, crimson brand damage, the
blood pop through armor, buildup on players, weapon hits, resistances, armor set bonuses, stats feeding combat,
exhaustion resistance, and that every weapon has a Better Combat file. `test/QuestGameTests` covers: quests after the
evaluation, kill progress and rewards, the quest line moving on, rank scaling, the spawner trigger, area objectives,
timeouts and penalties, fleeing, offers, inventory and level objectives, the daily at dawn, death, saving, custom objectives.
`test/GateGameTests` covers: the cave plan (same seed same cave, spacing, rooms per rank), world-gen ranks by distance, a placed gate registering, carving the cave with a dormant boss, the boss's defeat opening the exit and dropping the cache, entering starting one gate quest and leaving, Trial of the Depths needing a gate, gate monsters' scaling and element, loot by rank, and a closed gate collapsing.
`test/AwakeningGameTests` covers: new players have no System and the crystal ignores them, evaluation after the System is bound, only a D-rank or higher clear calls a player, the sealed gate being personal, the trial holding a dormant Arbiter at the bottom, a fall being refused then granting the System with the inventory intact, slaying the Arbiter (System and title), and the legendary roll.
`test/NpcGameTests` covers: board slots and the daily refresh, gather hand-in, rank scaling, the 3-request limit,
hunt report-back, right-click opening the window, failing and being refused, the giver dying, escort arrival and
death, parcel delivery, and the nest spawning. Fake players can't take damage, so damage
checks use zombies.
`test/SkillGameTests` covers: starter skills by class, rank moving unlocks earlier, level unlocks and passives, mana and cooldowns, refusing while collapsed, the Rending Arc cone, power scaling, proficiency, passive attributes, tomes, Ember Burst and Hunter's Mark, stealth, Shadowstep, the healer's skills, the job change hooks, the bar and saving, and that every skill has its text and element. Second batch: 8 skills per class, combo chains, the reaction table, water then lightning, air feeding fire, every new skill casting, and the new passives.
`test/ShadowGameTests` covers: only necromancers leave souls, the starting steed, extraction raising a shadow that never fights its master, three refusals crumbling a soul, boss souls becoming named elites, levels and ranks up to marshal, roles from tags, summon, recall and a fallen shadow recovering, and saving the army.
`test/EconomyGameTests` covers: gold from monsters only with the System, buying, rank and gold refusals, daily limits, storage size by rank and expansions (doubling price, maximum), locked slots and pages, shift-click, stack in and sort, saving, selling by grade and sell-all, that buying to sell never pays, the license being issued and kept up to date (and not rewritten by others), and gate clear gold.
`test/GearGameTests` covers: rolls by grade, attributes from stats and enhancement, refine odds and downgrades, the forge charging, refusing and the ward, infusion reactions, 2- and 4-piece set bonuses and Unbroken, boss signature tables, sell value against refine cost, and boss sets carrying their element.
`test/CraftingGameTests` covers: elites leaving cores with their affinity and material, core chances for ordinary and wild monsters, boss loot gaining a boss core, crafting from inventory then storage with gold, refusing rank/materials/gold without taking anything, plain cores used before affinity cores, infusion crystals needing their affinity, forged gear rolling stats, potions and mana stones, veins dropping their stones, sell prices and every recipe being whole.
