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
| `test` | Game tests (`./gradlew runGameTestServer`) |
| `gate` | Gates and dungeons: the gate block, gate instances and their saved data, the cave planner and builder, the boss, the reward cache, world-gen and natural spawning (`GateService` runs it) |
| `client.gate` | Gate, boss and cache renderers (GeckoLib), the gate moments overlay and boss bar, camera shake, the gate dimension's look |
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
| `GateTheme` | One per rank: the cave's rock, floor, accents and lights, the element, the monsters, how many rooms, boss stats, clear XP |
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
(`skill_<path>`), and a test. Subclasses will be extra `Skills` entries marked `advanced` with their own owner,
granted through `grantAdvanced`. Still to come: combos (chained casts) and element reactions; `SkillEvent.Cast`
and the element on every skill are there for them.

On the client: keys Z, X, C, G cast the bar (rebindable, `SkillActionPayload`), `SkillBar` shows cooldown sweeps
and refusals, `SkillOverlay` plays Skill Acquired and the level-up banner (waits for the evaluation, quest and gate
overlays; the hide key skips it), `SkillsTab` is the Skills page of the System window.

`/system skill list|learn <skill> [level]|levelup <skill> [n]|forget|cast|slot <n> <skill>|cooldowns|unlocked|advanced|tome [skill]|reset`
for testing; `screen skills` in the UI driver opens the tab.

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
`test/NpcGameTests` covers: board slots and the daily refresh, gather hand-in, rank scaling, the 3-request limit,
hunt report-back, right-click opening the window, failing and being refused, the giver dying, escort arrival and
death, parcel delivery, and the nest spawning. Fake players can't take damage, so damage
checks use zombies.
`test/SkillGameTests` covers: starter skills by class, rank moving unlocks earlier, level unlocks and passives, mana and cooldowns, refusing while collapsed, the Rending Arc cone, power scaling, proficiency, passive attributes, tomes, Ember Burst and Hunter's Mark, stealth, Shadowstep, the healer's skills, the job change hooks, the bar and saving, and that every skill has its text and element.
