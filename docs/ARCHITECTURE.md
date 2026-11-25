# Architecture

Mod id `beyondsolo`, base package `com.beyondsolo`.

## Packages

| Package | What it holds |
| --- | --- |
| `system` | Player data (`SystemData`), ranks, classes, stats, levels, stat buffs, exhaustion, events, and `SystemAPI` |
| `notify` | `SystemNotification` (pop-up content) and `Notifications.send(...)` |
| `network` | Packets: data sync, pop-ups, stat allocation, the evaluation overlay |
| `evaluation` | Evaluation Crystal block and block entity, the evaluation flow, village pool injection |
| `spawn` | Random first spawn (everyone after the world's first player lands beside them: `LandingData`, `spawn.together`) and the "find a village" objective, with the mark on the nearest village that has an Evaluation Zone (`VillageGuide`) |
| `combat` | Elements, buildups and statuses, element damage, attributes, sync to clients, exhaustion from combat, Combat Roll hook |
| `item` | System weapons, armor (and set bonuses), remedies |
| `client` | Key bindings, client config, the theme colours (`SystemTheme`) |
| `client.gfx` | The drawing toolkit every System screen uses (see "Drawing the System UI") |
| `client.screen`, `client.hud`, `client.notify` | System window and tabs, status HUD, pop-ups |
| `client.evaluation` | The on-screen evaluation: scan read-out and the rank / class / stats reveal |
| `client.render` | World rendering: the crystal's renderer, render types, client-only effects |
| `client.combat` | Combat HUD (rail, enemy meters, damage numbers, screen tints) and world effects |
| `quest` | Quest engine: quests, templates, rank scaling, progress hooks, triggers (`quest.trigger`) |
| `client.quest` | Quest moments overlay, HUD tracker, QUESTS tab, the mark over a situation's target |
| `quest.situation` | Situation quests: quests the System makes up from what is happening, that change mid-fight and leave consequences (see "Situation quests") |
| `npc` | Villager requests: per-villager boards, request kinds, travellers, nests, hand-in and gossip (`NpcService`) |
| `client.npc` | The villager request window, world and HUD markers, the traveller renderer |
| `command` | `/system` testing commands (`/system quest ...` for quests, `/system npc ...` for villager requests) |
| `command.solo` | `/solo`, the operator commands: one tree with help, tab completion and English and Spanish feedback (see "Operator commands: /solo") |
| `shadow`, `client.shadow` | The necromancer's shadow army (see "Shadow army") |
| `economy`, `client.economy` | Gold, the System storage, the System shop and the hunter license (see "Gold, storage, shop and license") |
| `gear`, `client.gear` | Bonus stat rolls, enhancement, element infusions, boss sets and signature loot, the FORGE tab (see "Gear: rolls, sets, boss loot, enhancement, infusions") |
| `armory` | The class armor sets: the catalogue (`ArmorCatalog`), their items and ARMORY tab (`ArmoryModule`), set perks (`SetPerks`), the divine weapons' passives (`DivinePassives`), drop rules (`ArmorDrops`), the level and class checks and set pop-ups (`ArmoryEvents`) (see "The armory: class armor sets") |
| `crafting`, `client.crafting` | Monster cores, materials, mana stones and veins, System potions, recipes and the CRAFT tab (see "Cores, materials, potions and crafting") |
| `penalty`, `client.penalty` | The Penalty Zone: sentences for a failed daily training, the Wastes, the survival, its overlay and sky (see "The Penalty Zone and the daily training") |
| `rune`, `client.rune` | Rune stones: inscribed and sealed stones, absorbing, engraving runes, drops, recipes, the stone's composite model, the absorb on screen and in the world (see "Rune stones"); the rune arts themselves are in `skill/RuneArts` |
| `key`, `client.key` | Dungeon keys: turning a key to open a gate on demand, keyed runs (doors that seal, refunds, chains, the run clock and records), drops, recipes, the shop, the GATES tab, the lock of light (see "Dungeon keys and re-runs"); `gate/GateWiper` recycles closed caves |
| `depths`, `client.depths` | What lies off the beaten path of a gate's cave: hidden chambers behind disguised walls, trap rooms, the weather inside a gate, and finding creatures' weaknesses; the creature profiles themselves are `enemy/ElementalProfile` (see "Hidden chambers, traps, weaknesses and gate weather") |
| `breaks`, `client.breaks` | Dungeon breaks: the collapse timer on every uncleared gate, the break itself (waves, the boss coming through, suppression), and gate sensing in Pulse Scan; the break's banners, timer chip, gate compass and world effects (see "Dungeon breaks and gate sensing") |
| `tower`, `client.tower` | The Crimson Citadel: the castle's hundred floors (`TowerFloors`), its halls (`CitadelHalls`), runs of the climb, the Endless Spire and the boss rush (`TowerRun`), the lord (`CitadelLordEntity`), the key and the Sigil of Dominion, the hunter's records (`TowerData`, read through `TowerAPI`); the CITADEL page, the HUD, the light in the halls and the citadel's sky (see "The Crimson Citadel") |
| `worldboss`, `client.worldboss` | The world's great threats: world bosses and monarchs (the schedule and the call, the fights, rewards, the Monarch's Seal), monarch armor and its aura, the boss HUD and the warnings drawn on the ground (see "World bosses and monarchs") |
| `party`, `client.party` | Parties for playing together: invitations, the party itself and its saved data, `/party`, shared kills, no friendly fire, gates that wait for the party, regrouping; the PARTY page, the party HUD and the markers in the world (see "Parties") |
| `monarch`, `client.monarch` | Monarch ascension: the hunter's crown (`MonarchData`, `MonarchAPI`), the four crowns (`Dominion`), the throne trial in the Hall of the Empty Throne (`ThroneTrial`, `ThroneHall`, the Arbiter Unchained) and the coronation, what a monarch's powers leave running (`MonarchDomain`); the throne's question, the coronation, the crown keys' bar and the monarch HUD. The powers themselves are `skill/MonarchPowers` (see "Monarch ascension") |
| `ruler`, `client.ruler` | The rulers of light, the monarchs' enemies: the three rulers (`VerdictRuler`, `WingsRuler`, `HandRuler` on `RulerBoss`), their heralds (`HeraldEntity`, `RulerHost`), why and when one comes (`Rulers`: the Eternal War, the Sentence of Light), choosing a side (`RulerHooks`), the Mote of Radiance and the Blessing of Light, the hunter's record (`RulerRecord`) and titles (`RulerJournal`); the herald's renderer. The rulers' fights, bars and banners are the world bosses' (`worldboss`, `client.worldboss`) (see "The rulers of light") |
| `association`, `client.association` | The Hunter Association and the gate broker: the posts at every Evaluation Zone (`AssociationPosts`) and their people (`AssociationNpc`), the office's contracts, permits, rank exam and merit (`Association`, `Standing`), the broker's whereabouts and scouting (`Broker`), the hunter's file (`AssociationRecord`) and the claimed gates (`AssociationData`); the office and broker windows, the marks over their heads and their renderer (see "The Hunter Association and the gate broker") |
| `test` | Game tests (`./gradlew runGameTestServer`) |
| `gate` | Gates and dungeons: the gate block, gate instances and their saved data, the cave planner and builder, the boss, the reward cache, world-gen and natural spawning (`GateService` runs it) |
| `client.gate` | Gate, boss and cache renderers (GeckoLib), the gate moments overlay and boss bar, camera shake, the gate dimension's look |
| `awakening` | How a player gets the System: the stages (`AwakeningData`), the sealed gate and its tether, the trial, the Hollow Arbiter (`ArbiterEntity`), the awakening sequence and the legendary reward (`AwakeningService`) |
| `client.awakening` | The awakening overlay (called, pulled back, threshold, revive, slain, the awakening itself), the Arbiter's renderer, its attacks drawn as light |
| `skill` | Skills: definitions (`Skills`), what each one does (`SkillActions`), a player's known skills, bar, cooldowns and buffs (`SkillData`), passives, tomes, and `SkillAPI` |
| `client.skill` | The skill bar on the HUD, the Skills tab, the Skill Acquired moment, skill effects in the world (`SkillWorldFx`), the Appraising Gaze read-outs |

## Rules for new modules

1. **Read and change player progress only through `SystemAPI`.** It keeps the client in sync and fires events.
2. **Keep your own state in your own data attachment** (see `registry/ModAttachments`), not in `SystemData`. Register
   it in the `beyondsolo` namespace and `/solo wipe` resets it with no change there. State kept anywhere else (a
   running session, a map in memory, a mark in the player's persistent data) needs its own cancel in
   `SoloWipe.progress`.
3. **Listen to `SystemEvent`s** on `NeoForge.EVENT_BUS` instead of patching core code:
   `Awakened`, `XpGain` (cancel or change the amount), `LevelUp`, `RankChanged`, `StatAllocated`,
   `Collapsed`, `ObjectiveChanged`.
4. **Talk to the player with pop-ups:** `Notifications.send(player, SystemNotification.of(Kind.QUEST, title, body))`.
   Kinds: INFO, QUEST, ALERT, LEVEL_UP, REWARD. They show in whatever mode the player picked and land in the Log tab.

5. **Every feature owns its animations**, and they should look smooth. For a screen, use `client.gfx`. For light in
   the world, draw it in a renderer with `ModRenderTypes` instead of spawning dust particles (see "The evaluation").
6. **Every word a player reads comes from a lang file, in English and Spanish.** Put the key in your namespace's
   `en_us.json` and the Spanish in the `es_es.json` next to it; never build a sentence by gluing words in code (word
   order changes between languages, so give the whole phrase a key with `%s` slots). See "Languages" below.

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

### Draw calls: shape batches

Every shape carries its settings (size, colours, border, glow...) in its own four corners (`ShapeFormat`: a vertex
format with eight extra elements, ids taken from the top of the 32 the game allows; `BufferBuilder.beginElement` is
opened by the access transformer), so shapes go through the GUI's buffer source like vanilla text and fills, and
shapes that follow each other can share one draw call. They still draw over everything without the depth test, the
way they always did.

- Inside `try (var b = Shapes.batch(g)) { ... }` shapes and `SysFont` text are collected and drawn together, a run at a
  time, in the order they were drawn. Items, vanilla text and fills still draw where they come, after what was
  collected so far. Outside a batch each shape draws at once, as before.
- Batched now: the System window's frame, each System page whose `SystemTab.batched()` says true (all of today's), the
  storage window, and every `beyondsolo:` HUD layer (`HudClientEvents` opens one per layer). With the storage page
  open, a frame went from about 920 draw calls to about 570: the storage window's own from about 380 to about 110 and
  the HUD's from about 165 to about 85. Most of what is left are the items, which vanilla draws one at a time.
- **The rule inside a batch:** what is collected is drawn later, with the clip and shader colour of that moment. Call
  `Shapes.flush(g)` (or `g.flush()`) before `g.enableScissor`/`g.disableScissor`, `RenderSystem.setShaderColor`/
  `g.setColor`, `g.blit` and `InventoryScreen.renderEntityInInventory...` (they clip), or the shapes before them get
  clipped, tinted or drawn over the blit. A new System page starts unbatched (`batched()` is false by default): say
  true once it follows the rule. A new HUD layer is batched at once, so follow the rule there from the start.
- Draw a grid's backgrounds in one loop and its icons in the next (as the storage cells and their locks): a plain shape
  and an icon need different render types, so alternating them costs a draw call each.
- The UI test driver's `perf <label> [frames] [sync]` logs the frame time, the open screen's share, and the shapes and
  shape draw calls a frame (`client/UiPerf`); `tools/uitest/README.md` also shows how to count every GL draw call of a
  frame. The cloud machine draws in software: compare its numbers with each other.

### The System HUD

While `systemHud` is on (client config, default on), the System draws Minecraft's own HUD itself (`client.hud`):

- `VitalsHud` (layer `beyondsolo:vitals`, right above vanilla's hotbar layer): the hotbar as glass tiles with a
  gliding highlight and a glow under graded gear (the off-hand slot and the "hotbar" attack indicator where vanilla
  puts them), the experience strip with the level gem (the System level once awakened, vanilla's level before that;
  vanilla's level is then the small green number at the strip's end), and leaning bars with their numbers: health
  (armour chip, absorption in gold, poison/wither/frozen tints) and mana on the left, hunger (saturation line, what
  the food in hand would give), fatigue, a mount's health and air on the right, each only while it matters. Bars
  glide, leave a pale trail when hit, flash when they rise and pulse when low. Numbers of five digits and more are
  shortened (12.3k). It raises `Gui.leftHeight`/`rightHeight`, so the item name and the action bar stay above it.
- `EffectsHud` (layer `beyondsolo:effects`, above vanilla's effects layer): status effects in vanilla's corner and
  rows as discs with a ring that runs down, the time left and the level. It leaves out burn, venom and the crimson
  brand, which have their meters on the combat rail. `bottom()` and `left()` say where the icons end (vanilla's too
  while the System HUD is off): the pop-ups and the gold capsule start under them, and the icons go under the unread
  badge of hidden pop-ups.
- `HudClientEvents` cancels the vanilla layers it replaces (hotbar, jump meter, experience bar and level, health,
  armour, food, mount health, air, effects) and AppleSkin's layers. Spectators keep vanilla's HUD.
- `StatusHud`'s capsule shrinks to rank and class (level, mana and fatigue are at the bottom), and the combat rail
  follows its edge (`StatusHud.right()`). `VitalsHud.hotbarLeft()` is the hotbar's left edge with the off-hand slot,
  for pieces that sit beside it (the shadow army counter).
- `HudSpace` shares the screen with other mods' HUD pieces. **Xaero's minimap:** where Xaero put it this frame is read
  back by reflection (`HudMod.INSTANCE` → `Hud` → module `xaerominimap:minimap` → `getPushState()`, plus 12 px for
  its coordinates line; nothing needs Xaero, and a failed read only logs once and stops making room). In a top
  corner it keeps that corner: on the left `StatusHud` starts under it (`StatusHud.top()`, the quest cards and the
  combat rail follow); on the right the unread badge and the System's effect icons start under it, and
  `EffectsHud.bottom()`/`left()` count it, so pop-ups, gold and party frames do too. The pack puts the minimap top
  right (`modpack/overrides/config/xaerohud.txt`). **The top centre:** our bars there (gate and world boss bars, run
  clock, break timer and compass, red gate and depths chips, tower plaque, trial wave, penalty timer, pinned pop-ups)
  `claimTop` the box they drew each frame (cleared on `RenderGuiEvent.Pre`). `JadePlugin` (`@WailaPlugin`, loaded by
  Jade only; Jade is `compileOnly`) adds a before-render callback, `JadeRoom`, that holds Jade's tooltip under
  those claims, gliding, the way Jade keeps it under vanilla's boss bars (Jade only sees vanilla's). The skill
  level-up banner drops under them too (`topClaimedLately` also counts last frame's claims, since mods' layers draw in
  no fixed order); the Halcyon raid's chip claims its box like the rest, and so do the windows of a great kill (a great threat's fall, the raid's report), so the extract prompt over a fallen creature's soul (`ShadowHud`) moves under them.

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
- **Seen through walls means a depth function of ALWAYS.** Vanilla's `NO_DEPTH_TEST` only skips turning the depth
  test on, and the particle engine leaves it on, so at `AFTER_PARTICLES` (where the world effects draw) a "no depth
  test" render type is still hidden behind every block in front of it. `ModRenderTypes.lightThroughWalls` sets the
  depth function to `ALWAYS` for its own draw (`PASS_DEPTH`): gate sense pillars, the Depths frames and the village
  mark's column use it.
- **Never spawn a real lightning bolt for show.** Even a "visual only" bolt on the server hands the vanilla
  lightning advancement to everyone nearby, charges lightning rods and cleans copper. `WorldEffects.lightning`
  makes one that exists only on the client.
- **The player watching in first person sees the overlay, not the particles.** Keep world effects on the object and
  on the ground, away from their face, and let the overlay carry the big moments. Particles on a player's own body
  go through `util.BodyParticles`: everyone else sees them on the body, the player only a few at their feet (one
  particle right under a first-person camera fills a corner of the screen).
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
- **Hidden rank thresholds** (`Reevaluation`, config `reevaluation.rankStatTotals`, default D 90, C 135, B 185, A 240,
  S 300 on the total of the five stats, about the levels each rank's gates are made for): crossing one makes the player truly that rank (`Reevaluation.trueRank`), but
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

## The power curve (`system/Balance`)

All of the game's growth lives in `system/Balance`, and what keeps old flat numbers fair next to it in
`system/BalanceHooks`. It is the interim pass; the final balancing pass tunes the same constants.

- **The hunter**: levels 1 to 100 (`Balance.MAX_LEVEL`). Each level adds 4 max health and 3% to every blow, arrow,
  skill and summon (`levelHealth`, `levelDamage`). Stat points are big steps: a VIT point is +5% max health (of the
  base, the levels and flat gear health), a STR point +10% melee damage, a SENSE point +10% with bows, crossbows and
  thrown weapons (`rangedPower`, applied when the arrow lands, since Apothic's arrow attributes are capped). A fresh
  hunter has 20 health; a level 75 hunter about 1,500 (a VIT-heavy guardian twice that). XP to the next level is
  `100 x L^1.4 x (1 + L/50)` (102 at level 1, about 102,000 at 74); quest XP grows by the same `xpGrowth`.
- **Skill power** (`Balance.power`): every skill hit (`SkillActions.hit`), flat skill and gear proc (`flatDamage`) and
  summon grows with the level bonus times `1 + 3%` for each point of a stat the class leans on (weighted by
  `PlayerClass.statWeight`, the main stat fully). Parts of a skill's damage that are already in today's sizes (health
  paid, blows taken and thrown back) go through `keepSize` so the power is not applied twice. The STATUS tab's stat
  tooltip shows what each stat adds (`powerFrom`).
- **Creatures** keep pace: a gate rank's typical creature is an even match for a hunter at the rank's home level
  (`homeLevel`: E 3, D 13, C 26, B 41, A 56, S 74). Its health is multiplied by `health(rank)` (E 1 up to S 55: as much
  as a hunter's blows grew by then) and its damage by `damage(rank)` (E 1 up to S 70: as much as a hunter's staying
  power grew). The higher ranks' pools hold heavier kinds (an S pool's average kind is about 2.7 times an E pool's
  `HiddenLevel.power`, `EnemyRoster.basePower`), so both are evened out by `Enemies.poolScale` (E 1, D 0.78, C 0.57,
  B 0.46, A 0.40, S 0.37, worked out from the pools, so adding a kind to a pool moves it): the pool's average creature
  lands on the curve and its kinds keep their spread around it (`Enemies.rankHealth`/`rankDamage`, through
  `Enemies.apply`). A kill's System XP counts the health before that evening (`Enemies.killWorth`), so a level costs
  about 70 to 150 home-rank kills. Bosses are `300 x health` and `7 x damage`. `healthAt`/`damageAt` are the same
  curve by level for content made for one hunter (job trials, the penalty zone, nests; `Enemies.strengthForLevel`,
  evened out by the pool the creature came from), and `strengthAt` is both in one number (`levelForStrength` reads it
  back).
  Summons, Call of the Pack and the shadow army grow with their master (`summonHealth`, `summonDamage`); a soul is sized
  the old way (`ShadowService.soulSize`). The sealed door's cave stays at an old D-rank gate's strength (it is fought
  without the System); the Citadel's lord has 60,000 health.
- **Old flat numbers** (`BalanceHooks`): flat heals (food, potions, regeneration, most healing skills), Absorption
  shields, hazards with no attacker and the blows of creatures the System did not make are multiplied by
  `legacyScale` (how much bigger the hunter's pool is than it was before), so each keeps its share of the hunter's
  health. Heals that are already sized (a share of max health or of a blow) go through `Balance.healRaw`; another
  mod's heal in the middle of a blow (Apothic's life steal) is left alone too. Armour works on a blow's size next to
  the body it lands on (`armorScale` = max health / 20), so it protects as much against a 400-damage blow on a
  1,500-health hunter as against a 6-damage blow on a 20-health one. Venom and the other statuses take a share of
  max health; flat max health passives and gear rolls are percentages.
- **Where a hunter stands** (`powerLevel`, the scale of creatures' hidden levels and of the rankings): their level
  plus every stat point beyond what their levels gave (rank bonus, rewards, the job change, unspent points), 6 to a
  level (`POINTS_PER_LEVEL`: a level is its three points and about as much again in health and damage), never below
  E's home level. The rival hunters on the rankings stand near their ranks' home levels and climb at most 30% past
  their start (`Rival.GROWTH_CAP`), so the best ends just under the cap and a hunter at level 100 can take first place.
- **AttributeFix** is a required dependency: it lifts vanilla's attribute caps (1024 max health, 2048 damage).
- Tests: `BalanceGameTests`.

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
| Blood | `BLOOD`, 100, -4/s | Blood pop: 10% max HP (5% for bosses, elites and champions, `Statuses.popCap`), ignores armor, enchantments and Resistance, and never more than that share whatever bonuses the hit carried (`ElementalHits.capPop`) | (buildup) Clotting Tonic |

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
- **Held and dropped weapon models** come from `tools/gen_weapon_models.py` (the other generators call it at the end;
  run it alone after touching a weapon texture). In the hand, on the ground and in item frames a weapon is a solid:
  the sprite with a depth of its own under every texel. The painters build a weapon from parts along its axis
  (blade, guard, grip, shaft, head...), and each part knows its shape, so `tools/weapon_relief.py` gives a blade a
  ridge and a honed edge, a shaft a round bar, a hammer head a bevelled block, an orb a ball. `tools/gen_hd_gear.py`
  writes that depth per texel to `tools/weapon_relief/<name>.png` (and each weapon's length and grip to
  `meta.json`), and the model generator turns it into a mesh with smooth normals, a Wavefront OBJ read by NeoForge's
  loader (`models/item/held/<name>.obj`; rim walls take their colour from `textures/item/held/<name>_side.png`). The
  grade glow around a weapon is the sprite once more on a see-through plane inside the solid. The inventory icon
  stays the flat sprite. `python3 tools/weapon_preview.py <name>...` draws a model alone and in first person in a
  second, without the game.
  - **Sizes and grips.** `PROFILES` gives each Better Combat kind a length in pixels of the player model and where
    the hand closes (`LENGTHS` for single weapons); the model's scale follows from the sprite's own length, so a
    dagger whose picture fills its icon is still a dagger in the hand. Every weapon is held at its grip, at the same
    place in the fist, in vanilla's hand rotation: Better Combat's swings are made for that rotation, and one idle
    pose per kind then fits every weapon of the kind. `FP_HOLDS` lays out the first-person view per kind in the
    view's own space (where the grip is, where the tip points, which way the face looks).
  - Every melee kind has an idle stance: `tools/weapon_stances.py` fits the arms to it (two hands on claymores,
    katana, polearms, hammers, heavy axe and scythes) and writes the result as idle poses
    (`player_animations/pose_<stance>.json`), which the weapon's Better Combat file names as `attributes.pose` on
    top of its parent. Poses only cover standing and walking; attacks still use Better Combat's swings.
  - **Bows** (`BOW_SPECS` in `tools/gen_arsenal_assets.py`) are drawn for six draw lengths each: at rest and five
    pull frames whose limbs bend and shorten as the string comes back (`PULLS`: the seconds of drawing each frame
    shows from). In the hand the string and the arrow are not the painted texels but real shapes
    (`bow_extras`: a thin cord, a round shaft, a ridged broadhead, three feathers), so the bow looks right from
    behind and along the arrow; their texels are left out of the solid (height 0 in the relief file). The limbs and
    the handle are flat strips, the same depth right across (relief kinds `limb` and `riser`): on a rounded limb
    the painted outline sat on the slope and showed from the front as two jagged black lines. A bow has two
    holds: at the side at rest, and raised with the arrow under the crosshair while drawn (the game's own bow-use
    hand movement is undone in the pull frames' transforms). `GradeVisuals` narrows the view while a System bow is
    drawn, which the game only does for its own bow.
  - Gauntlets (the `fist` kind) are worn, not held. `tools/gen_gauntlets.py` builds each as a few boxes in the arm's
    own space (cuff, vambrace, plated hand, knuckle guard, claws or spikes), painted with the armor art kit at four
    texels a pixel, and writes them as an item model (`models/item/worn/<name>.json`) whose hand transform undoes
    what the game does to a held item, so the gauntlet lands on the arm and follows every animation. The weapon's
    item model shows it for both hands and on the ground (a raised fist); the icon and item frames keep the sprite.
    A second fit (`<name>_armored`, picked by the item property `beyondsolo:armored`) clears a chestplate's arm.
    `client/combat/WornGauntlets` does the rest: a layer puts the pair's second gauntlet on the arm of the empty
    hand, the arm keeps the empty-hand stance, and in first person the bare arms are drawn wearing them (the game
    only draws an arm for an empty hand). `python3 tools/gauntlet_preview.py <name>` shows a design on a mannequin
    without starting the game.
  - Better Combat reads `weapon_attributes` only when the server starts: after changing one, restart the server
    (`tools/uitest/stop.sh` and `start.sh`); `/reload` and a client restart are not enough.
- **High-detail textures:** weapon, armor, refining stone and ward seal textures are 64x64 (worn armor layers are
  128 pixels wide, see "Worn armor art"). `tools/gen_hd_gear.py` renders the weapon designs above at that size with
  smooth multi-tone shading and grade dressing (runes and aura from epic, sparkles from legendary, white-gold radiance
  on divine) and the forge stones with the shared painter `tools/hd_paint.py`, and has the nine first sets' armor drawn
  by the armory's art pipeline. Run it after the other generators, which still own names and the 32x32 originals;
  it rebuilds the weapon models itself.

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

Categories: `SET` (can't be abandoned; the next one starts 5 s after the last), `DAILY` (the daily training; fails at
the next in-game day and a new one starts; failing it means the Penalty Zone), `SURPRISE` (offered), `EMERGENCY` (forced, one at a time; dying or fleeing fails it),
and `NPC` / `GATE` reserved. Failing costs exhaustion plus weakness and slowness (half when abandoned), harsher with rank.

The first System quest and the daily are handed out once the player is evaluated and the reveal is over.

On the client (`client.quest`): `QuestOverlay` plays the big moments (accepted: line of light opens into the window,
emblem pops, title types, then it flies into the tracker; complete: seal stamps down with shockwaves, sparks, rays,
rewards count up; failed: red flash, shake, glitching header, struck-through title, penalty types out). New pop-ups
wait while one plays, and the hide key skips it. A quest moment never starts over a title moment (`TitleOverlay`), and
a title earned by finishing a quest arrives in the same tick as the quest's moment, so the title waits for it
(`QuestOverlay.waiting()`): Quest Complete first, then the title. `QuestTracker` is the HUD list under the status capsule, and
`QuestLogTab` replaces the core Quests tab. The moments also land in the Log tab.

`/system quest list|start <id>|offer <id>|trigger <id>|accept|progress <n>|complete|fail|reset` for testing.

## Situation quests

Unscripted quests made up on the spot from what the player runs into (`quest/situation`). A **situation**
(`Situation`) notices something (`detect`, once a second, or an event), hands out the first quest of a run, keeps
watching while it lasts (`update`, `killed`, `runEntityDied`, `playerHurt`, `expiring`, `playerDied`) and can
**branch**: `QuestEngine.branch` rewrites the running quest into the next one in the same slot, optionally paying what
the old one promised first, and the client plays a "QUEST CHANGED" moment (old title struck out, new one typed in).
Each quest carries its run in `Quest.extra()` (`Run`: situation, stage, target and its network id, counters, a
waypoint), so a run survives logging out. Every quest can end with an **outcome** line in the System's words
(`situation.beyondsolo.outcome.*`), shown on the complete/failed moment and in the log.

| Situation | Starts when | Turns into |
| --- | --- | --- |
| `OverwhelmingFoe` ("foe") | a creature hunting you is red-named (ratio 1.5; plain vanilla ones 2.0) | Survive (hold 60 s) → Annihilate when it drops below 40% → It Fights Back (it heals 30% and enrages) if left standing; running away makes it come back later as a **Nemesis**, 20% stronger each time (3 times at most) |
| `LastStand` | at 25% health with hunters around | Last Stand (hold 20 s) → Second Wind (heal, strength, speed: strike back) |
| `PackLeader` ("pack") | a champion, elite or patrol leader hunting with followers | Head of the Pack → Rout the Pack once the leader falls (the rest falter) |
| `ProtectVillager` ("protect") | a monster hurts a villager near you | Protect the Villager → Avenge Them if they die; saving them is remembered (gossip, emeralds), walking away too |
| `DefendVillage` ("raid") | a raid near you | Defend the Village (waves) → The Final Wave |
| `BeyondRank` | entering a gate above your rank | Beyond Your Rank → Retreat when you fall under 30% |
| `Streaks.Untouchable` / `Streaks.Frenzy` | clean kills in a row / kills in quick succession | three tiers each, paid as they go; a hit (or the clock) ends it, banked tiers stay paid |
| `CatchBreath` | badly hurt after a fight, nothing hunting you | rest 15 s for regeneration, absorption and mana |
| `Cure` | a zombie villager in sight (offered) | Cure it → Keep It Safe while it converts |
| `Places.SilentCity` / `Outpost` / `Ruins` | inside an ancient city, a pillager outpost, ruins or a temple (offered) | loot quietly → It Has Awoken (the warden); clear it → Counterattack (hold the outpost); search → its guardian rises |
| `Revenge` | a creature at least your match killed you (offered after respawning) | Unfinished Business: it waits where you fell, marked until it is dead |

Rules (`Situations`): one emergency at a time; one calmer situation at a time and a gap after one ends
(`situations.minGapSeconds`); a cooldown per situation; nothing in the Penalty Zone, a job trial, a red gate or a
Crimson Citadel run (`QuestTriggers.quiet`). **Follow-ups** (`FollowUps`, saved in `SituationData`, attachment
`SITUATIONS`) are what comes back later: a nemesis, a revenge offer. A handler that can't happen yet is asked again a
minute later (a nemesis waits while the hunter is in a gate or the Citadel). When the creature a quest watches dies,
its health row drops to 0 before the quest ends. Its death is the hunter's own (`Foes.killedBy`) when they, a summon of
theirs or a party member beside them (`Parties.sharers`, the rule shared quest kills go by) dealt it; anyone else's
kill ends the quest with part of the pay. Heals that are a share of health go through `Balance.healRaw`. A boss
(`Foes.isBoss`: a gate's, a world boss or monarch, anything tagged `c:bosses`) can start Survive, but it never recovers
or rages in It Fights Back and never comes back as a nemesis (nor does a break's creature, a monarch's soldier or a
summon); any other creature recovers once per fury however many hunters' quests watch it.
**Secret quests** (`Secrets`, category `SECRET`) are never announced: brave or unusual deeds (a far stronger kill, a
whole raid, an untouched gate, a lone clear above your rank, a nemesis, a finished streak, a silent city plundered)
reveal them already complete, paid once, with a title kept in the chronicle (`Secrets.registerTitles` puts the eight
of their own on the TITLES page as secret ones; the Giant Slayer is the chronicle's, earned by the same kill). Settings
live in `beyondsolo-situations.toml` (`SituationConfig`).

On the client, `SituationMarkers` locks brackets onto a situation's target (a green ward for someone to keep alive)
with its health as an arc, `NpcMarkers` shows a situation's marked spot as a waypoint, and the tracker draws health
rows (heart, coloured bar) and rules (gold shield) of the new objective shapes (`QuestObjective.Display`).

`/system situation list|start <situation>|follow [kind]|secret <id>|watch|clear` (also `/solo dev situation ...`)
starts one from what is around you, brings a waiting follow-up forward, reveals a secret, shows what the System has
noticed, or resets it all. `/solo wipe` forgets it all too (`Situations.clear`).

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
| `GateTheme` | One per rank (plus `TRIAL`, the awakening trial's; monsters carry the theme's ordinal in `GateService.MOB_TAG`): the element, the monsters, how many rooms, boss stats, clear XP. The look now comes from the realm |
| `GateRealm` | The kind of place behind the gate: 20 realms, several per rank (a gate's seed picks one, `forSeed`), each with its rank range, how it is built (natural caves, built halls, a hive of cells), its blocks (walls, floors, accents, trimmings, lights, liquid, carpet and banners), the decorations it uses (`Decor`), the shapes it suits with their weights, its affinity and element, the creature habitats it draws from (`enemy/Habitat`), the bosses it favours, and its fog colour, fog distance, drifting particles and the colour of its light (`glow`). The first six are the original rank caves (`classic(rank)`), `THRESHOLD` is the trial's |
| `DungeonShape` | How the whole dungeon is laid out: caverns (the original chain), labyrinth (a grid maze with dead ends and loops), gauntlet (great halls one after another through gatehouses), spire (a tower climbed up winding stairs), descent (a ramp down a deep shaft with ledge caves), chasm (islands joined by bridges over a sea of water or lava), hollow (one vast cavern with camps, a lake and pillars), hive (round cells grown off each other). The realm says which shapes suit it and the seed picks one (`GateRealm.shapeFor`) |
| `DungeonPlanner`, `DungeonLayout` | The plan of a dungeon from its seed, realm and shape: rooms (`Kind` entrance, cavern, side with a cache, boss, passage; `Shape` cave, hall, round hall, cell, island), passages (`Style` tube, corridor, bridge, spiral stairs) and open spaces (`SpaceShape` void, shaft, hollow, pool). One boss room, the biggest; every room reachable; nothing overlapping; between `FLOOR_LIMIT` and `CEILING_LIMIT` and inside its slot. Pure logic, tested without a world. `CAVERNS` takes exactly the draws the original planner took, so classic caves and the trial come out as before |
| `DungeonBuilder`, `DungeonDecor` | Carves the plan into `beyondsolo:gate_depths` (a flat dimension of solid rock, `data/beyondsolo/dimension`) a slice at a time (`BUDGET` blocks per tick): spaces, rooms, passages, the realm's skin on every surface, liquids, islands, the floor of rooms on a chasm's rim (a ledge where the chasm ate into it) and stair landings, walkways, then dresses it: lights per room shape, fill light, the boss's arena (pillar rows, standing stones, braziers, a throne in built halls), side-room caches, mana veins, and the realm's decorations, kept off the paths between doors |
| `GateService` | Registering gates, going through (enter / exit with a short freeze while the overlay plays and the cave finishes building), waking monster packs as players reach each cavern, waking the boss, the boss's defeat (reward cache, exit gate, XP, quest progress), closing cleared gates |
| `GateBossEntity` | GeckoLib boss: dormant until someone enters the arena, then a roar intro; swipe and slam attacks with the gate's element; enrages under half health (adds, faster); long death animation |
| `RewardCacheBlock(Entity)` | Falls in a beam of light, lands, opens with a lid animation and bursts out `GateLoot` |
| `GateFeature`, `GateSpawner` | World-gen (`worldgen/placed_feature/gate.json`, rarity 1 in 220 chunks) and gates tearing open near players. Both roll the rank at random (`GateService.randomRank`, weights `gates.rankWeights`, E most common, S 4 in 100): never by the player's rank or the distance from spawn. The spawner runs a minute for each overworld player (`GateSpawner.minute`, outcomes in `GateSpawner.Outcome`): a gate within `NEAR` (160) blocks the player has not seen (not within 40 blocks of it, not told of it) is pointed out first (System alert, or a grey line of thought for players without the System); else, while fewer than `gates.maxOpenNearby` (2) are open around them, one opens 40-80 blocks away by chance (`gates.openChancePerMinute`) or for sure after `gates.guaranteeMinutes` (6) quiet minutes (`ModAttachments.GATE_QUIET`, -1 before the first). A player's first gate opens at their first minute (30 s after joining at the earliest), 22-36 blocks in front of them. Never within `SPACING` (32) of another gate. Players without the System get D-rank gates only, one at a time (`awakening.gateChancePerMinute`, `awakening.gateGuaranteeMinutes` 3); players with the System but not evaluated get none (gates refuse them) and one `gate.locked` alert a session. `/system gate spawner [now]` shows what it sees around you (and runs your minute now). Until 0.1.2 any open gate within 160 blocks, seen or not, held back every new one and nothing came for sure: the 0.1.1 playtest waited over ten minutes beside an unseen world-gen gate |

Instances sit `SLOT_SPACING` (2048) blocks apart along x in the gate dimension; `GateData.bySlotPos` finds the gate
for a position there. A cleared cave closes once nobody is left in it, and its gate in the world collapses.

A `GateInstance` keeps its realm and shape (`realm()`, `shape()`, saved as `realm` and `shape`). Its name in the
pop-ups is the realm's (`dungeonName()`), its arrival title adds the shape, its creature mix comes from the realm's
habitats and affinity (`EnemyRoster.forSeed(rank, seed, habitats, affinity)`), its boss favours the realm's
(`BossKind.forSeed(rank, seed, realm)`), and its monsters carry the realm's element (`GateService.ELEMENT_TAG`, read by
`elementOf`). A cave saved as built before realms existed is a **legacy** cave: it keeps the classic realm of its
rank, the caverns shape, its old boss and its old mix, so players still arrive in it where it was carved; one that
was never carved takes a realm like any new gate. On the client `GateAtmosphere` tints the fog with the realm's
colour (at normal priority, so the gate's weather can tint over it), shortens it to the realm's distance, drifts
its particles (ash, snow, spores, motes) and turns the light to the realm's `glow` (`tintLight`, from the gate
dimension's `adjustLightmapColors` in `GateClientEvents`: icy in Rimefrost, violet in the Crystal Seam, sea green in
the Sunken Sanctum; the classic caves keep warm torchlight); the server sends the realm with `GatePayload.REALM` on
arrival and login.

Hooks: the gate quest (`QuestCategory.GATE`, started on entering, fails if you die inside) uses CUSTOM objectives
`beyondsolo:gate_mob` and `beyondsolo:gate_boss`; any quest can use `beyondsolo:gate_clear` (the System line's
Trial of the Depths does). Gate monsters carry `MOB_TAG` and add their gate's element buildup on hit.

On the client (`client.gate`): `GateRenderer` (the GeckoLib ring of rock with glow masks, plus the vortex drawn as
light: it tears open as a slit and collapses with a flash), `GateBossRenderer` (magic circle, enrage, the pillar of
light on death), `RewardCacheRenderer` (beam, fall and bounce, landing ring, the burst), and `GateOverlay` (entering,
arriving, leaving, boss intro with cinema bars, defeated stamp, dungeon cleared, the boss bar, camera shake).
Models, animations and textures come from `tools/gen_gate_assets.py`.

The game-test server does not load datapack dimensions, so there (only) caves are carved into the overworld and
walled in first (`GateService.dungeon`, `DungeonBuilder.walledIn`). It also runs its turns back to back without
waiting, so chunks that other threads finish (generating, loading, handing a chunk over to the world) can lag a
thousand turns behind, and a creature added to a chunk not yet handed over can't be found in the world. A test that
needs chunks ready (the boss a dungeon was just built with, a wave that just rose) waits for them by the clock with
`test/TestChunks`, not over turns.

`/system gate spawn [rank] [realm|any] [shape]|near|enter|boss|wake|kill|exit|info|cache [rank]|list` for testing: `spawn`
opens a gate into a realm and shape of your choice, `boss` takes you to the door of the boss's room, `exit` takes you
out through the way out once it is open (its moment and all, as stepping into it does), `info` names the
dungeon you are in (realm, shape, rooms, boss, element, seed).

## The awakening

Players start without the System. `SystemData.hasSystem()` is the lock: without it there are no pop-ups
(`Notifications.send` drops them), no System window, HUD or keys, no quests, and the Evaluation Crystal stays dark.
`isAwakened()` still means "evaluated" and implies `hasSystem()`. Check `SystemAPI.hasSystem(player)` before showing
a player anything that belongs to the System; check `isAwakened` for anything that needs a rank or a class.
`SystemEvent.SystemGranted` fires when it is bound. Config `awakening.required = false` gives it at first login.

| Stage (`AwakeningData.Stage`) | What happens |
| --- | --- |
| `DORMANT` | No System. `GateSpawner` opens D-rank gates near such players (`awakening.gateChancePerMinute`, for sure every `awakening.gateGuaranteeMinutes`; the first one in front of them within a minute and a half) or points out a D-rank one standing near. Gates let them in; players who have the System but are not evaluated are still turned away. The first gate of rank D or higher they walk into, before anyone has (so before its rock is carved), hides the sealed door: `AwakeningService.enteringGate` (from `GateService.beginEnter` and `arrive`) forces its hidden chamber to `HiddenKind.SEALED`, which `DungeonLayout.planSealed` puts past the boss's room instead of off a cavern (a gate whose own layout has no room there, a boss on an island over a chasm say, is laid out as caverns instead: `DungeonLayout.shapeForDoor`). Caves from before hidden chambers (`GateDepths.legacy()`) are passed over |
| `CALLED` | Set by `AwakeningService.gateCleared` when the boss of the cave that hides their door falls with them inside. `REVEAL_DELAY` later (the boss has crumbled, the exit is not open yet) `revealDoors` breaks the wall (no HIDDEN DUNGEON moment, no XP: knocks and Pulse Scan get nothing from this wall) and opens a sealed gate (`GateService.spawnSealedGate`, a `GateInstance` with an `owner`, theme `GateTheme.TRIAL`, `temple` set) for each called player in the chamber (`DepthsBuild.sealedDoor`), or one for each party called there together (owned by its leader if they are among them, every member bound to it), with `trialHome` set to the cave's own home. While it stands the cave's exit turns them back (`heldBack`, checked in `GateService.beginExit`). Out of the gate depths without going through (they fell, logged out and the cave closed), the call lapses back to `DORMANT` (`tether`, `leftGate`); a door opened out in the world (`/system trial call` outside a cave, old saves) still pulls them back past `awakening.tetherRadius`. Only those it opened for can enter (`mayEnter`) |
| `TRIAL` | Inside. The door shuts behind them (`arrived`; a party's behind the last of them still in the cave). A temple trial (`GateInstance.temple`, every trial since 0.1.1) is planned by `DungeonLayout.temple`: the way in, an antechamber with the Arbiter's three tenets on a stele (`DungeonDecor.tenets`, signs read from `trial.beyondsolo.tenet.*`), the hall; no side caves, and no trial wakes packs (`GateService.watch`). Every player death in a trial is cancelled (`LivingDeathEvent`, highest priority), so nothing is dropped: before the Arbiter's hall they stand up again at the last room reached (`GateInstance.checkpoint`), in the hall the awakening begins |
| `AWAKENING` | `begin` freezes the player and plays `AwakeningPayload.AWAKEN` on their client (14.6 s). The server follows the same clock: out of the cave at `TELEPORT_TICK` (the screen is black; to `trialHome`, in front of the gate they first went into, or for a door out in the world, in front of it), System bound at `GRANT_TICK`, released at `END_TICK`, then `FirstSpawnHandler.systemIntro` (welcome pop-up, "find a village"). Logging out halfway finishes it at once |
| `DONE` | Has the System. The evaluation follows as before |

The Hollow Arbiter (`ArbiterEntity`, a `DungeonBoss` like `GateBossEntity`, so the boss bar and intro work for both)
kneels dormant and invulnerable until its challenger is well inside the hall, rises for `INTRO_TICKS`, then uses
three attacks: a cleave across everything in front of it, a sunder that sends a ring along the floor (jump it), and
judgment, marks of light under each player that burst a moment later. A party fights it together: it passes its verdict only when the last of them falls (`challengers`), and if they bring it down every one still standing gets the title and a cache of their own. Under half health it is unbound: faster,
double rings, three marks. A strike that fells its challenger ends the fight in the middle of an attack
(`passVerdict` clears every ring and mark), so the attacks walk copies and stop at once (`fighting()`); walking the
lists themselves crashed the server in the 0.1.0 playtest. 1500 health, heavy armour, arrows do little. Killing it (`arbiterSlain`) drops a cache
that holds one `ModItems.LEGENDARIES` weapon (`LegendaryWeaponItem`), then the awakening plays with
`arbiterSlain` set, which also gives the title `title.beyondsolo.arbiter_slayer`.

The light's way: a temple opened since the light came also hides the chapel of the Envoy of Dawn off its antechamber.
A hunter who finds it and swears to the light never faces the Arbiter: `beginLight` plays the awakening in the light
on the same clock and they wake sworn to it (see "The light: a hunter's road to ruler").

Its model, animations and textures, the sealed gate's texture and the legendary weapons' icons come from
`tools/gen_awakening_assets.py`. The server's timings (wind-ups, `INTRO_TICKS`, `DEATH_TICKS`) and the animation
lengths in that script must be changed together. In GeckoLib files a positive x rotation pitches a bone forward
(top to the front, a hanging limb's end to the back); the script's poses are written as a body moves and converted.

Testing: `/system trial status|reset|call|enter|arbiter|wake|fall|slay|grant`. `reset` takes the System away again
and closes the old trial; `grant` skips the trial. `call` inside a cave that hides the door breaks its wall and opens
the door at once (as the boss's fall would); anywhere else it opens a sealed gate in front of you.
`/system depths gate D seed sealed` opens a gate whose cave hides the door whoever goes in first (it still only opens
for players without the System).

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

**Recasts and gates.** A skill can take a second press while its first is still working (`Skill.recast`, with the
buff that marks the window): `SkillAPI.cast` offers the press to the recast first, and a press it takes costs no
mana and skips the cooldown (the client lets it through its own cooldown check while the buff is on, and the bar
slot beats instead of showing the cooldown). A skill can also be held only by some players (`Skill.gate`, read with
`allows`): `grantUnlocked`, `refreshForClass`, tomes, rune stones and `/solo skill learn all` respect it,
`Skills.forClass` leaves gated skills out, and `SkillAPI.refreshGated` runs when what a gate asks for comes or goes
(a crown).

**Utility skills** (the series' telekinesis and shadow exchange; neither belongs to a class, and the exchange is only
for hunters who raise shadows):

- *Sovereign Hand* (`SovereignHand`, B grade, level 30): looking at a creature, the hand takes it (`GRASP` effect: a
  shell, five fingers of force and an arm of force from the hunter's hand) and holds it in front of their eyes,
  without gravity or legs, unable to hurt anyone or explode, for 3 s and up. The recast hurls it where they look
  (`HURL`): it crashes into the first wall (x1.3), the ground or a creature (which takes 80%). Held too long, it is
  slammed down (70%). A boss or anything wider than 2 or taller than 3.2 blocks is pinned instead (slowed to 15%,
  no jumping, half damage). At nothing, it shoves (`FORCE`): a cone of force that hurts and throws creatures back,
  deflects arrows and spells the way the hunter looks, and pulls loot to them. Shadows, hired hunters, association
  people and the hunter's mother are never taken.
- *Umbral Exchange* (`UmbralExchange`, A grade, level 22, gated by `ShadowService.canRaise`): trades places with one
  of the hunter's shadows: the one looked at (48 blocks and up), else the one lying the way they look (22 degrees): a
  guard at its post anywhere in this world, loaded or not, or a shadow out in the world they can see (no trading
  through a wall into a sealed room). `EXCHANGE` draws a pool of shadow at both ends. The shadow rises anew where the
  hunter stood (`ShadowService.exchange` summons a fresh entity that keeps the old one's health and effects: a
  teleported entity is interpolated by the client and would be seen sliding from one spot to the other).

Skill tomes (`SkillTomeItem`): an unread one teaches a random fitting skill the player lacks, or raises one they
have; a named one (`ModComponents.SKILL`) teaches that skill. Gate caches drop them (`GateLoot`), two System quests
reward them. Events for other modules: `SkillEvent.Learned` (with its source), `LevelUp`, `Cast`.

Adding a skill: one `Skills` entry with its element, an action in `SkillActions` that sends a `SkillFxPayload`
kind for the look (`client.skill.SkillWorldFx` draws each kind with the light textures and particles; add a kind
for a new look), three lang lines (name, `.flavour`, `.desc`), an icon in `tools/gen_ui_assets.py`
(`skill_<path>`), and a test.

**How skills look.** Three client pieces draw a cast, and a new skill gets all three without extra work:

- *The body.* After a cast goes off `SkillAPI.cast` sends `SkillCastPayload` (caster, skill, the first effect kind
  the action showed, its duration; `SkillFx` notes the first kind while the action runs, `RuneFx` too, as
  `rune:<kind>`). `client.skill.SkillGestures` picks a gesture from the kind and the skill's class (a cut swings,
  `SLAM` brings both hands down, `QUAKE` stomps, `BOLT` throws from the hand, `HEAL` prays, `ARROW` draws and looses,
  `TRAP` sets something down...) and plays `player_animations/skill_<gesture>.json` on its own playerAnimator layer
  (priority 1950, under the weapon arts' 2000). 28 gestures, written by `tools/gen_skill_anims.py`; strikes land by
  tick 2 to 4, because the effects appear on the cast tick. Spells also gather light at the hand. In these emote
  files (no `version`) playerAnimator reads `torso` as `body`, the whole player turned about the hips, and its `y`
  moves the whole player by that many **blocks**: a `"y": 2.6` meant as a crouch lifted casters two blocks off the
  ground. Leave `y` off the torso; `gen_skill_anims.py` turns the legs back against every lean so the feet stay down.
- *The light.* `SkillWorldFx` draws each kind as shapes wearing a skin, never as a picture of a glow: a blade's cut is
  a crescent (`Skins.crescent`), beams, columns and geysers are tubes with a white-hot core, blasts and domes are
  shells (`Skins.sphere`, `cap`), ground blows are flat cracks with a sheet of light standing on them, tornadoes are
  funnels, chains are links, wings are feathers. The shapes are in `client.skill.Skins` (ribbon, line, strip,
  crescent, tube, sphere, cap, ring, disk, wall, lightning, lance, spikes, billboard, decal) and the composite effects
  in `SkinFx` (blast, shock, cracks, fault, geyser, flames, column, shells, barrier, tornado, sword, smoke, scorch).
  Skins use the `energy` render type (`shaders/core/energy.fsh`, premultiplied alpha): a skin texture's red is heat
  (the colour ramping up to white) and its alpha is cover (a body in the effect's own colour), so a skin stays solid
  against a bright sky instead of washing out the way added light does. The 16 skins in `textures/effect/skin` come
  from `tools/gen_skill_skins.py` and move by scrolling their UVs in code; smoke and scorch marks are blended over the
  world (`softTexture`). Every effect carries its skill's `SkillElement` (`SkillFxPayload.element`, set while the
  action runs by `SkillFx.beginCast`, read by `SkillFx.castElement`), and `Skins.Look` dresses it: fire burns, water
  runs, air swirls, shadow is dark with a bright edge, the rest are raw energy. An effect of no skill guesses its
  element from its colour. All energy types share the one buffer of the frame's `BufferSource`: they draw in the
  order they are asked for, and every change of texture is another draw, so draw many of one thing together (see
  `motes`); `Skins.flush` ends the open batches when the frame's effects are done. **Weapon skills draw no circle or
  sigil** (Yordan's rule): magic circles and crisp rings are for spells (`CAST`, only under `Skill.castCircle`
  skills), zones, shields, marks and ultimates; hits, blinks, bashes, shots and reactions land with a shell, spikes
  and a `SHOCK` ripple. Ribbons, tubes and shells fade as they come close to the eye (a fountain at your own feet),
  beams and shots start past the hand in first person, and a caster's own dome, wings or halo are faint or left out
  in first person.
- *The particles.* `FxParticles` is the skill effects' own particle system: smoke and dust that billow and thin
  (blended, darkened where the world is dark), flames from a flipbook, sparks that streak and skip off the ground,
  embers, motes, shards, petals, leaves, feathers, drops, shock rings and ground glows, all soft textures from
  `tools/gen_skill_fx_textures.py` (`textures/effect/fx`). Ready-made bursts (`smoke`, `dust`, `fire`, `fireRing`,
  `sparks`, `embers`, `motes`, `flash`, `shockwave`...) keep the effects short; counts follow the particle setting.
  Skill server code calls `SkillParticles.send` where it used to call `ServerLevel.sendParticles`: vanilla flames,
  blasts, sparks, motes and smoke go out as `SkillParticlesPayload` and come back as soft particles
  (`SkillParticleLooks`); block debris stays vanilla. Heavy blows jolt the camera (`SkillShake`, off with the
  screen effects option), harder the closer they land.

New skill code: show a `SkillFxPayload` kind first (it picks the gesture), use `SkillParticles.send` for particles,
and keep circles for spells. Subclass skills live apart (see "Job change"). Every skill hit goes through `SkillActions.hit`, which is
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
- **Subclass skill sets** (`skill/SubclassKit` and one file per base class, `WarriorPath`, `AssassinPath`, `MagePath`, `GuardianPath` and `HealerPath` so far): every subclass
  grows to about 12 skills (8 actives, 4 passives; the ultimate last). These are not `advanced`: they carry an
  unlock level (22 to 50) and `SkillAPI.grantUnlocked` teaches them as the player levels, only to players of that
  subclass; a late job change catches up at once. A path file holds its skills, their numbers and actions, and
  its passive hooks (`strikeBonus`, `afterStrike`, `whenStruck`, `tickSecond`, `onKill`, `refusesDeath`), which
  `SubclassPassives` calls. `SubclassKit` has the builders (`active`, `passive`, `bonus` for attribute passives)
  and the building blocks (`cone`, `along`, `foes`, `dash`, `leap`, `repeat`, `later`, `push`, `pull`,
  `aimGround`). To add a class's sets: copy the shape of `WarriorPath`, call its `init()` from
  `SubclassSkills.init()` and its hooks from `SubclassPassives`, add names to `tools/job_lang.py` and icons to
  `tools/gen_job_assets.py`. World effects added for these sets (`SkillFxPayload.Kind`): `CLEAVE` (falling
  blade), `BANNER`, `BLADES` (circling swords), `CRACK` (fissures), `GEYSER`, `QUAKE`, `BEAM` (a steady beam), `ORB` (a hanging sphere), `THUNDER` (a bolt from the sky), `SHIELD` (a shield held in front of someone), `CHAIN` (links from a caster to a point), `HAMMER` (a thrown, tumbling hammer), `DISCUS` (a thrown shield), `HALO`, `WINGS`, `BUBBLE` (all three worn by a creature for a while), `STREAM` (drops flowing from a point into a creature: heals, drained blood), `ORBIT` (small lights circling a creature, one per charge left).
  Three rules keep the sets looking different. Bursts take their particles from the element: `NOVA`, `GEYSER`, `SPLASH`, `WAVE` and `BLINK` read their `duration` as a look (flames, sparks, water, motes of light, soul fire, the ground's own debris), so a water or light skill never throws embers. Things that stand in the world for a while (walls, pillars, flowers, a tree) are block marker particles from `SubclassKit.marker`, so nothing is built and nothing is left behind. And every heal a healer's skill gives goes through `HealerPath.healing` (also the base class's and the starters'), which is where the healing passives and the Afterglow hook in.
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

- **Souls.** Any living creature, ours, vanilla's or another mod's, killed by a necromancer or one of their shadows
  leaves a soul for 20 s (a boss's for 60 s), only for that player. One that dies of what came after their blow (it
  burned, fell, drowned, or their pet finished it) counts as theirs, as Minecraft gives them the kill
  (`getKillCredit`). Tag `shadow/no_soul` leaves none (players, the training dummy, visions). A creature that died in
  the air leaves its soul on the ground or the water under it, in reach (`ShadowService.soulSpot`). A death that
  can't be read (a broken mod creature) leaves no soul and is logged once (`ShadowHooks.onDeath`). The raise key (H) next to a soul starts the extraction
  ritual (2 s); each soul has 3 tries. The chance (`ShadowService.chance`) falls with the creature's health against
  the player's level and INT; bosses are much harder. `/system shadow next success|fail|random` decides the next try.
- **Shadows** (`ShadowRecord` in `ShadowData`): the creature type, a role (`ShadowForm.roleOf`: tags
  `shadow/mounts`, `shadow/casters`, `shadow/warriors` first; then a steed carries, a creature that shoots or throws
  casts, bolts at range, unless it holds a blade; the rest fight up close), its form (below), a rank (`ShadowRank`: Husk, Soldier, Vanguard, Knight, Captain,
  Commander, Marshal, and Regent for a monarch's), level 1-10 inside the rank, and the health, damage and speed it was
  extracted with. Weak
  creatures start as Husks, strong ones higher. Dungeon bosses (`DungeonBoss`), the world's great threats
  (`ColossalBoss`) and tags `shadow/bosses` and `c:bosses` (the dragon, the wither, other mods' bosses) become named
  elites (`ShadowNames`) that start at Knight to Commander, use Dread Slam and give nearby shadows +15% damage.
  Health always follows the army's curve (`ShadowService.maxHealth` with `Balance.summonHealth`), whatever the
  creature was.
- **Any creature's shape** (`ShadowForm`, kept in the record and saved). How it gets about (`Movement`): tags
  `shadow/fliers`, `shadow/swimmers`, `shadow/walkers` first, then a water creature or water-bound navigation swims,
  a flying navigation or move control or a flier's body (bats, ghasts, phantoms, the dragon, the wither, vexes, the
  magus) flies, the rest walk; a steed always walks. Its size: the creature's own with its size roll (a baby, a big
  slime); anything wider than 4 or taller than 6 blocks rises smaller, in proportion (`fit`: the dragon at a quarter,
  the giant at half). Its look (`lookOf`): what the creature saved less what only mattered while it lived (`DROP`:
  where and who it was, health, mind, loot, owner, anger, asleep or hanging), so a skeleton's shadow holds its bow, a
  sheep's keeps its colour and a mod's creature its variant; over 4 KB only its gear is kept. Shadows raised before
  forms, and those given by command, take a fresh one of their kind's shape (`ShadowForm.probe`, at login and when
  called). The army sent to the client with every change leaves the looks out (`ShadowData.save(false)`); the
  Shadows page asks for a shadow's look when it shows it (`ShadowActionPayload.LOOK`, answered by
  `ShadowLookPayload`).
- **Growing.** A shadow gets XP for its kills (the creature's max health) and a quarter of that for its master's kills
  nearby; filling level 10 climbs a rank by itself up to Knight (`ShadowService.addXp`, rank-up moment and pop-up).
  A new rank never makes a shadow weaker: it comes in at the lowest level as strong as it was (`ShadowRank.entryLevel`;
  from the top of a rank that is level 4 or 5, as growth inside a rank, +45%, outweighs a rank step, about +25%).
  From Knight on, a shadow at level 10 waits (`ShadowRecord.ready`, takes no XP) for its master to promote it. The
  master gets half the usual System XP for their shadows' kills. Stats: base x rank power x (1 + 5%/level) x
  (1 + 1%/INT over base) x 1.10 if its master named it.
- **Promotions** (`ShadowPromotions`; series: the named shadows promoted to knights, commanders, a marshal). Captain,
  Commander, Marshal and Regent come only by promotion (`ShadowRank.byPromotion`): the Shadows page's *Promote* (or
  `promote`, payload action 9) takes the master's level and shadow essence (`promotionLevel` / `promotionCost`:
  30/150, 45/400, 60/1000, 80/2500), and the shadow comes into its new rank as above. A hunter's shadows stop at Marshal,
  a monarch's go on to Regent. `refusal` names the missing piece (shown on the page and as a pop-up). The
  promotion moment (`ShadowFxPayload.Kind.PROMOTED`) is a column of the new rank's colour, rings and rays, and a
  banner with what the rank brings. Raising the first marshal earns the title *Raiser of Marshals*.
- **Shadow essence** (`ShadowData.essence`, saved). A soul its master leaves to fade, or one that crumbles after three
  refusals, drifts into its master's shadow as essence if the master is within 48 blocks in the same world
  (`soulEssence`: health / 8, at least 1, a boss's x4); the wisp (`Kind.ESSENCE`) flies to the master's chest and the
  army chip shows "+N". Letting a shadow go leaves `releaseEssence` behind.
- **Given names** (`ShadowPromotions.name`, `ShadowNamePayload`). A Knight or stronger, or any boss's shadow, can be
  given a name from the tag on its portrait (letters, spaces, `'` and `-`, 2-16 long, `ShadowNames.clean`/`fit`; two
  shadows can't share one). A named shadow is bound closer (`ShadowRecord.bonded`): 10% more health and damage
  (`BOND`), its name in its rank's colour over its head and on the page. Names: 2, +1 every 20 levels, +3 for a
  monarch (`nameSlots`). An empty name takes it back; a boss's shadow is known by its own name again
  (`ShadowRecord.bestow` keeps it). The first name earns the title *Keeper of Names*.
- **Commanders lead**: shadows within 12 blocks of one of their master's Commanders (or higher) hit 10% harder
  (`LEAD`); a boss shadow's +15% still counts first (`ShadowService.auraBonus`).
- **In the world** (`ShadowEntity`): rises out of the ground (`RISE_TICKS`), follows its master and slips to them when
  left behind, fights whatever hurts its master or what they hurt and monsters near them, never players or allies.
  Its hits go through `SkillCombat.strike` with the shadow element, so they can set off reactions. A flier's shadow
  flies (`FlyingMoveControl`, `FlyingPathNavigation`, no gravity), hovers over its master's head (`hoverOver`) and
  swoops on what it fights; a swimmer's swims through water and air alike, low over the ground. Neither drowns or
  is carried off by water, and no shadow chokes in a wall or a crowd. Never saved with
  the world; it goes away when its master logs out, and follows through dimension changes. A fallen shadow rests
  30 s (boss 90 s). Calling one costs mana (`summonCost`). Army size `capacity` and shadows out at once
  `activeLimit` grow with level and INT.
- **Mounts**: the mount key (Y) calls the strongest mount and seats the player (again to get off); right click works
  too. Space jumps (`PlayerRideableJumping`). The first job change into a necromancer gives the steed Duskmane.
- **Standing guard** (series: shadows left to guard someone): the Shadows page's *Stand guard* leaves a shadow that is
  out (not a mount) at the spot it stands (`ShadowService.post`; out in the world only, not in the mod's own
  dimensions). The post is kept in the record (`ShadowRecord.post`, saved). A guard stays put (`GuardPostGoal`),
  fights what comes within 14 blocks of its post and walks back, and the recall key leaves it there. It still takes one
  of the places for shadows out (`ShadowData.used`), even while away. Minecraft keeps an unsaved entity frozen and
  out of sight when nobody is near its spot any more, so `ShadowHooks` catches that (`EntityLeaveLevelEvent` without
  a removal): the guard goes back into the dark and `reformGuards` stands it up again at its post (every second)
  once its spot ticks again; a shadow that was following rises again at its master's side (after a long jump or an
  exchange). A dimension change, logout or respawn leaves the guards posted. *Leave post* takes it off (it follows
  again, or rests if it was away); summoning it by name or sending it back does too. `PostMarkers` marks each post in
  this world on the HUD, with the distance, brighter on the one Umbral Exchange would take.
- **Client**: `ShadowRenderer` draws the creature it was through `ShadowTint` (every vertex colour multiplied to
  blue-black ink) and records its outline as it draws (`client.fx.Outline`); `client.fx.Aura` then burns that
  outline in the series' style (see "Series look" below): a hot rim, black flame standing on it, ink wisps, eyes
  with comet trails, and a pool of ink when it rises, sinks or falls. The creature is made on the client from the
  shadow's look (`ShadowLook`: any kind, with its own renderer, vanilla's, GeckoLib's or another mod's), takes the
  shadow's place and pose every tick and runs its own tick with its particles held back (a bat's wings, a slime's
  squish; a swimmer and the dragon are posed by hand, the dragon's wings and neck kept as Minecraft keeps them).
  `ShadowDraw` keeps whatever goes wrong in a renderer inside the shadow: it draws on its own copy of the pose and
  finishes any vertex or quad a failing renderer left half written. A kind that can't be made or drawn is told in the
  log once and drawn from then on as a stand-in of its size and way of moving (a zombie or a spider, a vex or a
  phantom, a dolphin); one whose own tick fails is only posed. The rank shows in the burning (`LOOKS`): a husk barely
  holds an edge, from Vanguard light shows through the seams, from Knight a taller crest of flame, from Commander a
  crown of flame spikes; a Regent has a second, violet rim and stands in its own pool; each rank above Knight is a
  little bigger (`tierScale`). `ShadowWorldFx` draws souls (a small pool with a black flame and an ember), the ritual
  (the pool spreads, a ring of black flame rises, ink smoke is drawn in, a hot thread from the hand), the extraction
  burst (ink thrown out and an eruption of black flame), refusals (a red-edged ink splash), summons, recalls, rank-up
  and promotion pillars of black flame (a promotion sets a crown of it), essence streaks and the naming ring, with ink
  smoke from `FxParticles`; `ShadowHud` the extract prompt, ritual
  ring, banners (cinematic for bosses and promotions) and the army counter; `ShadowsTab` the System window page
  (necromancers only), the portrait in the creature's own look with a "Flies" or "Swims" chip: filters (all, out, ready, named) and an order (rank, kills, newest, name), essence and names
  used under the card, the promotion strip (what it needs, ticked off) with *Promote*, and the name tag on the
  portrait that opens the name field (Enter keeps, Escape cancels).
- **Keys**: H extract / call the army, U recall, Y mount.
- **Tests**: `ShadowGameTests`, and `ShadowFormGameTests`, which raises a shadow of every living kind in the game
  (whatever mods are loaded) and checks its soul, shape, health and flight, besides looks, roles, fliers, swimmers
  and sizes.
- **Commands** (`/system shadow ...`, level 2): info, give &lt;entity&gt; [rank] [boss], soul &lt;entity&gt; [boss],
  next, rise, summon, recall, mount, xp &lt;n&gt;, rankup [index] (a promotion's rank is staged as a free promotion),
  kill, reset. `/solo shadow post|unpost [players]` leaves every shadow out on guard or takes the guards off,
  `/solo shadow posts [player]` lists the posts. `/solo shadow list [player]` lists the army with numbers,
  `promote <number> [player]` promotes one for free, `name <number> <name> [player]` names one (`""` takes it back),
  `essence set|add <amount> [players]`.

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
- **Gold on the HUD** (`GoldHud`): only shows when gold changes, on the right edge a third of the way down (under the effect icons, `EffectsHud.bottom()`):
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
- **Armor sets** (`item/ArmorSets`): every set has a 2-piece and a 4-piece bonus and may carry a perk. The brute's
  sets (`BossSets`, one per gate rank E..S: Stonehide, Mirefang, Rimeguard, Gloomreaver, Emberwrought, Sovereign) add
  perks (`SetEffects`: Unbroken, Venom Skin, Frost Aegis, Night's Edge, Living Flame, Crimson Dominion) and an
  innate element for un-infused weapons. They are class sets now, like the armory's (see "The armory").
- **Boss signature loot** (`BossLoot`): each rank's table has a chance at its weighted signature weapons, refining
  stones, crystals and (C and up) ward seals. Set pieces are no longer in it: they come from `armory/ArmorDrops`
  through `GateLoot.BossLootEvent`. `GateLoot` adds both to the reward cache, which calls out signature drops with a
  pop-up (`BossLoot.isSignature(stack, rank)`: the rank's weapons, any boss set piece, any divine piece).
- **Forge tab** (`client/gear/ForgeTab`, order 125 in the System window): pick gear, REFINE or INFUSE, see chances and
  costs; the ring charges with hammer strikes, then plays success rays and a +N stamp, cracks and a shake, the ward
  hexagon, or the infusion spiral. The shop sells refining stones, ward seals and crystals (category FORGE);
  `SellPrices` values gear more with enhancement and rolls (refining never pays back more than it costs).
- **Commands** (`/system gear ...`, level 2): info, roll &lt;grade&gt;, enhance &lt;0-10&gt;, refine [ward], infuse
  &lt;element|none&gt;, bossloot &lt;rank&gt; [n], set &lt;name&gt;, materials. UI driver: `screen forge`,
  `forge <slot> [refine|infuse] [element] [ward|noward]`, `forgego`.

## The armory: class armor sets

Code in `armory/`; `ArmoryModule.init` registers the items, one armor material per set (`ArmorMaterial.Layer(<set>)`
for the worn texture) and the ARMORY creative tab. Lang lives in `assets/beyondsolo_armory/lang` (written by
`tools/armory_lang.py`); icons, worn layers, 3D parts and item models come from `tools/gen_armory_assets.py`
(`--sheet <png>` writes a contact sheet of every icon), see "Worn armor art".

- **The catalogue** (`ArmorCatalog`): 39 sets on top of the brute's six, 45 class sets in all, every one made for a
  single class. Each class has a set at every gate rank E..S and a divine set. Sources: the villages (one plain
  common set per class, `villager:<set>`), every gate creature (`creature:<kind>`, one to three sets across the ranks
  it lives in), every boss (`boss:<kind>`) and `divine`. Grade follows source and rank (`ArmorCatalog.gradeFor`:
  village common; creatures common to legendary; bosses rare to mythic; divine). A set is heavy, medium or light
  (`Build`): its armor values (`ArmoryModule.VALUES`, by rank and build, never above netherite until A rank) and how
  much more tiring sprinting gets.
- **Small buffs, for one class** (`ArmorSets.attuned`): two pieces give one or two stats, four a little more and the
  set's perk. Only an awakened hunter of the set's class draws them out; anyone else just gets the armor (the
  2-piece line says so). Creatures always draw them out. Stats include the two new attributes
  `beyondsolo:mana_efficiency` (skills cost less mana, cap 50%) and `beyondsolo:skill_haste` (shorter cooldowns,
  cap 40%), applied in `Runes.manaCost` / `Runes.cooldown` (`ModAttributes.manaFactor` / `hasteFactor`), so the
  skill bar shows the real numbers.
- **Level gates** (`ArmorSets.refusal`): E 1, D 10, C 20, B 30, A 40, S 50, divine 60 (`ArmorSets.levelFor`).
  Divine pieces are also bound to their class. `SystemArmorItem.canEquip` and `use` refuse with the reason;
  `ArmoryEvents` puts back anything that got on some other way (dispensers, commands) every second, and works the
  bonuses out again when the class changes. Creative players are never refused.
- **Perks** (`SetPerks`, 33): four-piece mechanics for the armory's creature, boss and divine sets (village sets are
  stats only). Offence: Venom Fangs, Hunter's Mark, Cinder Burst, Pounce, Webshot, Prism Charge, Pyre Burst, Hollow
  Echo, Moonlit Hunt, Alpha's Howl, Vanguard Cleave, Storm Sigil, Frostbite Arrows, Empress Venom, Ashfall, Phantom
  Blade, Arcane Cascade, Dragon's Blood. Defence and support: Crystal Spines, Bone Ward, Tower Guard, Saint's Grace,
  Ashen Bulwark, Cradle Silk, Rime Bloom, Oath of Iron, Starbinding. Divine: Warlord's Fury, Shadow Veil, Arcane
  Overflow, Sanctuary, Overflowing Grace, Windwalker. Pounce, Phantom Blade and Windwalker listen to Combat Roll
  (`ArmoryEvents.RollHook`, only when the mod is there). Effects stay light: sparks, a ring, a sound.
- **Divine weapon passives** (`DivinePassives`): each divine weapon has a passive of its own while its class holds
  it, on top of its traits and signature art: Ruinbringer: Ruin (the fifth mark detonates), Calamity Fang: Vanishing
  Edge (a kill hides you, next strike +80%), Eye of the Insatiable: Insatiable (+3% spell damage per kill, up to 10),
  Colossus Aegis: Unyielding (a killing blow leaves you at 1 health, every 90 s), World Tree Branch: Rootbound Life
  (allies under 30% are caught and healed), Starfall Longbow: Astral Quiver (every 4th full draw is a star arrow).
  The tooltip shows it under the signature art.
- **Drops** (`ArmorDrops`), slow on purpose: gate creatures killed by a hunter with the System leave a piece of one
  of their kind's sets 0.6% of the time (veteran 1.2%, elite 5%, champion 10%). Sets of the gate's own rank come up
  most, each rank away halves it, never more than one rank above; the killer's own class 2.5 times as often.
  Bosses: a piece of one of their sets, 50% (E) to 75% (S) a kill, an ascended boss always one and 35% a second;
  the brute leaves its rank's set. Divine pieces: 1% from an S-rank boss (3% ascended), 0.5% from an ascended A-rank
  one. Village requests: the giver's craft's starter set (`ArmorCatalog.PROFESSIONS`), 12% of requests, 25% for
  clearing a nest or an escort. Pieces roll bonus stats and may come out a grade higher (`GearRolls.dropGrade`).
- **Tooltips** (`GearTooltip`): the set name, its class, the level it needs (red when too low), the 2- and 4-piece
  bonuses with the perk, where it drops, and for divine pieces that they are bound. The 2-piece line over the hotbar
  and the 4-piece System pop-up (`ArmoryEvents.equipped`) say what just switched on.
- **Commands**: `/system gear set <name>` puts on any set; `/system gear bossloot <rank> [n]` now includes the brute's
  pieces as an ascended kill would.

## Worn armor art

Every armor set of the mod (the 39 armory sets and the nine first sets: hunter, knight, dusk, stonehide, mirefang,
rimeguard, gloomreaver, emberwrought, sovereign) is drawn by one pipeline from a design, and worn with its own 3D
model.

- **Designs** (`tools/armor_designs.py`, `D`): per set a palette and, per piece, a kind and options: the helm style
  (sallet, greathelm, bascinet, kettle, barbute, cheek, nasal, bars, mask, cap; open-faced helms let the face show),
  hoods, bone masks and hats; plate, leather, robe or vestment bodies and sleeves; legs, waists and boots; and the
  extra parts (`extras`: pauldrons, plumes, crests, horns, wings, capes, tabards, quivers, crystals, halos, wolf heads,
  wizard hats, brims, tassets, skirts, toe caps...). `CLASS` lists the sets by class for line-ups.
- **Painting** (`tools/armor_worn.py`, `Worn(design).build()`): paints both layers on a tone canvas with hue-shifted
  ramps (`tools/armor_kit.py`): plates shaded as curved forms with a lit side, a core shadow and a specular streak,
  lames, rivets, etched borders, cracks and wear, mail, quilting, leather grain, cloth folds, fur, scales; helm backs
  get a nape guard and a spine, hood backs draped folds. Every boot gets a toe cap unless the design says `toes=False`.
- **Geometry** (`tools/armor_rig.py`): the vanilla armor boxes plus the extra parts, packed into the texture below the
  vanilla layout. Textures are 128 pixels wide (two pixels per model unit); a set whose parts don't fit gets a taller
  texture.
- **Writing** (`tools/gen_armory_assets.py [set ...]`): the two layers (`textures/models/armor/<set>_layer_{1,2}.png`),
  the parts (`models/armor/<set>.json`: texture heights per layer and one entry per part: body part, origin, size,
  uv, inflate, pivot, rotation, mirror, layer) and the item icons. Icons are drawn from the worn model itself
  (`tools/armor_icons.py`): an orthographic three-quarter view (the chestplate with short sleeves, the boots in
  profile), drawn four times too big and brought down by keeping each block's most common colour, then outlined and
  given the grade's aura and sparkles.
- **In game** (`client/armor/ArmorModels`): a reload listener reads every `models/armor/*.json` and builds an outer
  and an inner `HumanoidModel` per set; `IClientItemExtensions.getHumanoidArmorModel` hands them out for every armor
  item of the mod (found by the material's texture name). Parts on the right arm or leg are mirrored onto the left
  one there; head and body parts come with their own twins. NeoForge copies the pose and part visibility, so head
  parts only show with the helmet and so on. Arms are a hair bigger than the body so the shoulders don't flicker.
- **Looking at it without the game:** `python3 tools/armor_preview.py <set>` (a mannequin from five sides,
  `--walk`), `python3 tools/armor_icons.py <set>` (the four icons, big).

## Series look

How the anime this game is inspired by draws power, darkness and light is written down in the project files
(`art-style/STYLE-GUIDE.md`, with `palettes.png`); the series' own pictures never go into the repo, the pack or the
project files, and its names, characters, emblems and designs are never copied. In short: dark power is a black body
with a burning edge, black flame and ink; light is a white-gold body with flame of light, broken halos and wings of
light; energy is thin and sharp; nothing glows like a ball; circles of runes belong only to spells.

- **`client.fx.Outline` and `client.fx.Aura`.** `Outline` records a figure's outline as its model draws, through any
  renderer (`tap` a buffer source, optionally only one render type: a GeckoLib boss taps its body, not its glow
  layer), and `Aura.burnWorld` burns it in an `Aura.Style` (hue, rim, seams, flame, crest, crown, glow, lean, wisps;
  `light()` for flame of light): the rim, flame curtains (flame shader), the light in the seams, ink wisps (`State`,
  one per figure) and eyes. Beside it: `pool` and `splash` (ink on the ground) for the dark, and for the light `halo`
  (a broken halo upright behind a head), `seal` (the same lying on the ground), `wings` (a fan of feathers of light on
  each side, `open` 0 folded to 1 spread, `beat`) and `glint` (a hot point with four short rays: a mote, light
  gathering). The pictures are `textures/effect/series`, made by `tools/gen_series_fx.py` (red = heat, alpha =
  cover, drawn by the energy shader); feathers and halo lines carry a little cover so they read against a bright sky.
- **Who burns.** Shadows (`ShadowRenderer`, the rank looks), worn monarch armour and crown forms (`MonarchAuraLayer`),
  monarchs and world bosses (`ColossalBossRenderer`: a calm, a wrath and a death style per kind), the rulers (flame of
  light; the Verdict's model wings are hidden in `preRender` and drawn as wings of light; a shadow's stand-in keeps
  them, in ink), heralds (`HeraldRenderer`, a small flame of light and small wings of light in place of their model's
  feathers) and the Envoy (broken rings and glints, no soft sun). Only the real entity burns: a shadow's stand-in
  drawn with the same renderer is checked against the level's own entity.
- **Breath** (`WorldBossWorldFx.breathe`, each tick within 64 blocks of the threat or the ruler abroad): the frost
  queen's cold mist and frost motes, the wolf king's blood mist, sparks off the storm lord, the flame giant's embers
  and black smoke and the beast's dark blood in their last phase, light dust off a ruler; ink smoke pours off a dying
  monarch, black smoke off a dying world boss, light rises off a dying ruler. Server particles keep the rule: a
  monarch's descent, death and army leave ink dust (`MonarchBoss.ink`, `ColossalBoss.remains`), a ruler leaves motes
  of light (`END_ROD`).
- **Sky and land.** A monarch's gate is a hole of ink with a burning rim and its domain turns the land to ink with
  veins of its light; a ruler's rift is a still tear of white light with broken rings and rays, and every seal of the
  light (a sanctum's, a light hunter's, the vigil's) is a broken ring (`Aura.HALO`), never runes. Warnings are thin
  rims and fills; blows are streaks with white-hot heads, stars of light (`Aura.SPARK`) and thin rings.
- **Skills** (`client.skill`). A blast (`SkinFx.blast`) is a white flash with long rays and a shell of the element
  gone in a quarter of a second (never standing long enough to read as a dome), a low shock wall, a scorch, and smoke
  of its element (`smokeTint`: black for fire, ink for shadow, dust otherwise); a war cry (`SkinFx.shells`) is a band
  of thin rings of shuddering air running out from the chest; a shadow blast throws ink instead of flame
  (`SkillWorldFx`, NOVA); vanilla explosion and smoke particles are drawn dark (`SkillParticleLooks`).
- **Checking it.** Look from outside, by day and by night (`tools/uitest`: `camera`, `time set`): light washes out
  against a bright sky, ink disappears in the dark.

## Enemy roster and boss pool

Gates no longer hold the same creatures and the same boss every time.

- **Creatures** (`enemy/`): 25 of the gate's own kinds (`MonsterKind`), one entity class `GateMonsterEntity` whose
  moves come from its kind's style (`MonsterGoals`): hound and swarm (bite, leap), charger (wind-up, rush), caster
  (bolts; the tusk shaman also mends its kin, the brimling blinks away), guard (a shield or claws take hits from the
  front), bomber (flies in, bursts), brute (a ground slam inside a ring telegraph), archer (bow, sidesteps), skirmisher
  (dashes in, backs off), swooper (circles overhead, dives), lunger (strikes along a strip from out of reach, or leaps:
  the hive reaver), haunt (floats; a pulse: the wraith's wail saps strength, the drifter's discharge shocks), shambler
  (the grave thrall gets up once unless finished while down) and turret (the thorn spitter is rooted and spits). Each
  hard move shows a ground telegraph timed to the strike of its animation. Creatures never hurt one another.
- **No vanilla mobs** behind a gate: `EnemyRoster` pools per rank hold only the gate's own kinds, the awakening trial
  draws from `EnemyRoster.trial()` and the job-change trial's echoes are gate creatures too. A gate's mix comes from
  its seed (`GateInstance.mix()`): three to five kinds, always one that fights up close, plus an affinity.
- **Kin**: the six originals (gloomfang, broodling, shardback, hollow acolyte, ashen sentinel, emberwisp) carry the
  drops (crafting material, rune art, armour sets); every later kind names one of them as its `kin()` and shares those.
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
`/system enemy mix [rank]`, `/system gate spawnboss <rank> <boss> [ascended]`. Creature assets come from
`tools/gen_creatures.py` (model, animations, and the seven skins painted on the model by `tools/creature_skins.py`;
`python3 tools/gen_creatures.py <name>` redoes one), bosses and the gloomfang from `tools/gen_enemy_assets.py`, texts
from `tools/enemy_lang.py`.

### Hidden levels and the colour of names

Every System creature (anything with `EnemyTraits`, every `GateMonsterEntity`, every `DungeonBoss`) has a hidden level
(`enemy/HiddenLevel`). Nobody sees the number: a hunter with the System sees the creature's name over its head in the
colour of how it stands against them (`HiddenLevel.Threat`): **white** when it is weaker, **orange** when it is about
their level or a little above, **red** when it is far stronger. A boss's name on its bar takes the same colour.

- **The creature's level** is the level of the hunter it is an even match for. `HiddenLevel.power` measures a creature
  as it really is once everything is on it (kind, gate rank, variant, size roll, affixes, a red gate's fury, a boss's
  kind and ascension): `sqrt(health x (1 + 0.04 armour + 0.01 toughness) x damage)`, the product an even fight comes
  down to. `powerAt(level)` is the same measure for a hunter of each level: an E gate's typical creature
  (`EnemyRoster.basePower(E)`) grown along the power curve (`Balance.strengthAt`), so a gate rank's typical plain
  creature sits at its rank's home level (E 3, D 13, C 26, B 41, A 56, S 74; each rank's pool is evened out to it by
  `Enemies.poolScale`; `CreatureNameGameTests` checks it). Elites, champions, giants, a pool's heavy kinds and bosses
  climb from there.
- **The hunter's level** for the comparison (`hunterLevel` = `Balance.powerLevel`): their level, plus every stat point
  beyond what their levels gave (the rank's bonus points, rewards, the job change, unspent points), 6 to a level, and
  never below E's home level (E gates are made for hunters who have just awakened). Gear does not count yet.
- **The colour**: the ratio of the two powers. Under 0.85 white, from 1.5 red, orange in between
  (`Threat.WEAKER_BELOW`, `Threat.FAR_FROM`): in the middle levels about three levels below to seven above is orange.
  All of it is first guesses for the balancing pass.
- **Kept, not recomputed**: the level is worked out the first time it is asked for (a player starts seeing the
  creature) and kept in its persistent data under `beyondsolo_level`. Code that makes a creature stronger or weaker
  after it appeared calls `HiddenLevel.refresh` (red gate fury, a splitting creature's halves), which also tells the
  players who see it.
- **To the client**: `EnemyTraitsPayload` carries the level, now for every System creature, not only notable ones
  (`EnemyClient.level(id)`). `client/enemy/EnemyHud` draws the names (System font, a slim capsule; red ones glow);
  elites and champions keep their bigger plate, its name in the threat colour. Buildup meters stack over the name
  (`EnemyHud.above`), Minecraft's own name tag is turned off for these creatures, and a boss fighting under its bar
  shows its name only there (`GateOverlay.barBossId`). Without the System only elites and champions show a plate, in
  their affinity's colour, as before. Client option `combat.enemyNames` turns the names off.
- The first time a hunter with the System sees a creature, a pop-up tells them what the colours mean (flag in the
  player's persisted data).

Test command: `/system enemy levels` lists the creatures within 24 blocks with their hidden level, power and colour,
against your own hunter level.

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
- **Materials**: one per original gate creature kind (gloomfang fang, brood silk, shard carapace, hollow essence, ashen
  plate, ember cinder), `CraftingModule.MATERIALS`; the later kinds drop their kin's.
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

## The Penalty Zone and the daily training

Code in `penalty/` (server) and `client/penalty/` (client); `PenaltyModule.init` registers the attachment
(`PenaltyData`), the payload and the config (`beyondsolo-penalty.toml`). Lang lives in `assets/beyondsolo_penalty/lang`.

- **Daily training** (`QuestTemplates.DAILY`, "Tempering the Body"): the same four drills every day at every rank:
  run 1000 blocks, 80 jumps, 60 squats (crouching on the ground), 120 strikes. The run is the engine's TRAVEL objective;
  `quest/TrainingHooks` counts the other three and restores health, mana and fatigue when the training is finished.
- **The trigger**: a `QuestPenalty` can carry `zone` (`QuestBuilder.penaltyZone()`; the daily does). When such a quest
  fails or is given up, `QuestEngine.fail` calls `PenaltyZone.sentence(player, title)` instead of the fatigue and
  debuffs (those still apply when the zone is off in the config or the player has no System). Any quest can use it.
- **The sentence** (`PenaltyZone`, one `Session` per player, ticked from the server tick): it stays `pending` in
  `PenaltyData` until the player is free (not in a gate, the job trial, the awakening or an evaluation), then:
  `WARNING` (10 s, red window and countdown, the player can still move) -> `ARRIVAL` (pulled into the zone, frozen
  and unharmed while a camera glides in) -> `SURVIVE` (3 min by default) -> `SURVIVED` or `FAILED` -> home.
- **Surviving**: creatures of the player's rank (`EnemyRoster.random`, `Enemies.spawn`, tagged `PenaltyZone.FOE_TAG`)
  claw out of the sand around the player, more of them as time passes; a surge at one third and two thirds; a
  champion for the last 30 s; sand eruptions under the player (a red ring warns for 1.5 s; creatures caught in one
  take double damage); the edge at `PenaltyArena.RADIUS` pushes back and singes.
- **Ending**: when the time runs out the creatures crumble, light lifts the player and they are sent home healed. A
  blow that would kill ends it instead (no death, nothing dropped): home, collapsed, weak and slow for 90 s.
  Logging out does not escape: the player is sent home at login and the sentence starts over 15 s later.
  `PenaltyEvent.Ended` fires either way.
- **The place**: its own dimension `beyondsolo:penalty_zone` (flat red sand, biome `penalty_wastes` with no spawns,
  fixed dusk so undead don't burn). `PenaltyArena.build` shapes one basin per running sentence (slots 512 apart on
  z = -60000): dunes, a ring of leaning black spires, ribcages, the Pillar of Judgment, hidden light. The game-test
  server has no datapack dimensions, so there it is built in the overworld.
- **Client**: `PenaltyOverlay` (warning window with heartbeat, typed lines, countdown ring, the screen cracking to
  black; the arrival title card; the timer with kills and banners; the PENALTY COMPLETE stamp or PENALTY FAILED),
  `PenaltyWorldFx` (the edge as a curtain of red light that burns brighter near the player; the eclipse in the sky),
  `PenaltyClientEvents` (the dimension's effects, red haze).
- **Commands** (`/system penalty ...`, level 2): sentence, now, jump arrive|fight|time <s>|survive|fail|surge [n]|erupt|champion,
  cancel, reset, status.

## Rune stones

Code in `rune/` (server) and `client/rune/` (client); the rune arts are skills, in `skill/RuneArts`. `RuneModule.init`
registers the items, the `beyondsolo:rune_grade` data component (the skill rides on the tomes' `ModComponents.SKILL`),
the `RuneData` attachment (kept through death), the RUNES creative tab and the payloads (`RuneFxPayload`, `RuneSyncPayload`,
`RuneActionPayload`). Lang lives in `assets/beyondsolo_runes/lang`. Textures, models, glyphs, icons, light textures,
the two poses and all the text come from `tools/gen_rune_assets.py` (`--preview <dir>` also writes contact sheets).

- **The stone** (`RuneStoneItem`, one item): either **inscribed** with a skill (`Runes.of(skill)`, at the skill's
  grade) or **sealed** (`Runes.sealed(rank)`). An inscribed stone holds a rune art (any class) or a class skill (a
  *class rune*: only that class, or that subclass, can take it). A sealed stone settles on a skill when it shatters:
  `Runes.pool` lists rune arts and the absorber's own class skills (not the job change's) no higher than its grade
  and not mastered, rune arts and skills of the stone's own grade weighted up.
- **Absorbing**: hold use for `USE_TICKS` (2 s). Too strong a stone (more than `RANK_REACH` grade above the rank), a
  class rune of another class, a mastered skill, a sealed stone with nothing left or no System: the stone won't
  answer (`Runes.refusal`, worked out on the client too so the channel never starts; the server decides). When it
  shatters it teaches the skill at level 1, or raises a known one by `RAISE_LEVELS`; `SkillEvent.Source.RUNE`.
  Letting go early loses nothing.
- **Rune arts** (`skill/RuneArts`, 14): the essence of each original gate creature (later kinds carry their kin's) and
  boss, plus two *deep* runes no creature carries. Never unlocked by level and no tome rolls them (`Skill.rune()`); a class change keeps them. Gloomfang:
  Hunter's Howl. Broodling: Skittering Brood. Shardback: Crystal Carapace (passive). Hollow Acolyte: Hollow Lance.
  Ashen Sentinel: Ashen Bulwark. Emberwisp: Cinder Wisp. Bosses: Titan's Fall (Brute), Widow's Snare (Broodmother),
  Dread Presence (Alpha), Phantom Flurry (Knight), Sigil Cascade (Magus, the only one with a cast circle), Wyrmfire
  Roar (Wyrm). Deep: Quickened Pulse, Iron Resolve (passives). Their effects are `RuneFxPayload` kinds drawn by
  `RuneWorldFx` (no circles or auras, light shapes instead: the web, the tower-shield ward, sigils, the lance).
- **Engraving runes** (`Engraving`: Haste cooldown x0.8, Thrift mana x0.7, Might hits x1.2): using one opens
  `RuneEngraveScreen` (known actives, numbers before and after, CARVE). One engraving per skill, a new one replaces
  the old; stored in `RuneData`. `SkillAPI` reads `Runes.manaCost` / `Runes.cooldown`, `SkillActions.hit` multiplies by
  `Runes.damageMultiplier`.
- **Drops** (`RuneDrops`): gate creatures killed by a hunter with the System: their kind's rune (normal 0.4% up to
  champion 8%), class runes and sealed runes from veterans, elites and champions; a rune above the creature's rank
  halves its chance per grade. Bosses: their rune art (15%, 35% ascended), a sealed rune of the gate's rank (35%,
  always when ascended), deep runes from A and S gates, all into the reward cache (`GateLoot.BossLootEvent`).
- **Economy and crafting**: rune stones sell by grade (`Runes.SELL`, 80 for E up to 5000 for S), engraving runes 600.
  The shop sells a sealed D-rank stone and the three engraving runes. CRAFT tab, RUNES (`RuneRecipes`): seal four E cores and a mana stone into
  an E-rank stone, fuse three sealed stones (plus a core of the next rank, mana stones from B up) into one of the next
  grade, and make the engraving runes. Recipes only take sealed stones, never inscribed ones.
- **Looks**: `RuneStoneModel` replaces the item model after baking with one put together from 20 small models
  (`item/rune/stone_<grade>`, `glyph_<n>`, `crack_<n>`, all `neoforge:item_layers` with blank padding layers so tint
  index = layer), cached per grade, glyph and crack stage. The glyph and cracks are full-bright and tinted in the rune's
  colour (`RuneClientEvents`). Six stones, one per grade: river pebble, mossy tablet, carved hexagon, obsidian gem,
  gold-bound shield, crimson monolith. While held: the stone is raised and trembles (first-person hand transform), the
  body takes `rune_channel` then `rune_release` (`RuneAnimator`), `RuneOverlay` fills a ring round the crosshair and
  pours the light in before the System pop-up; other players see it as `RuneWorldFx`. A rune on the ground glows.
- **Commands** (`/system rune ...`, level 2): give &lt;skill&gt;, sealed &lt;rank&gt;, engraving &lt;kind&gt;, kit, absorb,
  engrave &lt;skill&gt; &lt;kind&gt;, clear, bossloot &lt;boss&gt;. UI driver: `engrave <skill>`, `engravego`.

## Dungeon keys and re-runs

Code in `key/` (server) and `client/key/` (client). `KeyModule.init` registers the six keys (`dungeon_key_e` .. `_s`,
`DungeonKeyItem`), the `beyondsolo:key_seal` data component (`KeySeal`: a boss's sigil, ascendant), the `KeyRunData`
attachment (records, kept through death), the KEYS creative tab and the payloads (`KeyFxPayload`, `KeyRunPayload`,
`KeySyncPayload`, `KeyActionPayload`). Lang lives in `assets/beyondsolo_keys/lang`. Textures, models, light textures,
icons, the `key_turn` pose and all the text come from `tools/gen_key_assets.py` (`--preview <dir>` writes contact sheets).

- **Turning a key** (`DungeonKeys`): hold use for `USE_TICKS` (1.5 s), or press TURN on the GATES tab (no hold,
  `turnFromInventory`; walking off or swapping the key away stops it). A lock of light forms where the gate will
  open (`findSpot`: about three steps ahead on solid ground with room for a gate, not on another gate still
  standing), cracks at each notch, and gives: `GateService.spawnKeyedGate` opens a gate of the key's rank there and
  the key is used up. Keys of that rank then rest for `REST_TICKS` (an item cooldown), so a hold that goes on past
  the turn doesn't start on the next key.
  Refusals (`Refusal`, worked out on the client too for the ones it can know): no System, the key more than
  `RANK_REACH` above the rank, the Wastes / a job trial / the gate depths outside a cave, a cave whose boss stands,
  the last keyed gate still waiting unentered, no room, already turning.
- **Keyed runs** (`KeyedRuns`, `GateInstance.Keyed`): the gate in the world is only a door to a fresh cave of its
  rank. It seals `SEAL_TICKS` (10 s) after the first one goes through; one nobody enters gives up after
  `UNUSED_TICKS` (2 min, counted while its holder is online) and the key comes back, seal and all. The way out at the
  end leads back to where the key was turned. The run's clock starts at the first arrival and stops when the boss
  falls (the clear goes to the players' screens right then, `KeyedRuns.cleared`); the time goes into each hunter's
  records (`KeyRunData`: turned, cleared, best time per rank, longest chain).
  Once everyone inside has fallen out, the run is over and the cave closes. A keyed run's gate quest pays its XP but
  no stat points (keys can be bought and crafted).
- **Re-runs and chains**: in a cleared cave a key opens the next gate right there, as the next run of a chain; each
  run of a chain pays `CHAIN_BONUS` (+10%) more XP and gold, up to `CHAIN_MAX` (5) steps, and the whole chain still
  goes home to where its first key was turned (`GateService.home`). When a cave is cleared and the player carries a
  key of its rank, the re-run prompt shows over the hotbar once the cleared banner is done; the accept key (J) turns
  that key (`rerunSlot`: the same seal first, then a plain one, then any of the rank).
- **Seals**: a key can carry a boss's sigil (`KeySeal`), so its gate ends with that boss again; an ascendant seal
  brings it ascended. Shown in the key's name, tooltip and looks (item property `beyondsolo:seal`: 0 plain, 1 sealed,
  2 ascendant; the seal hangs from the bow as a medallion).
- **Where keys come from** (`KeyDrops`, `KeyRecipes`, `ShopCatalog`): bosses leave a plain key of their rank (30%)
  and a sigil key (8%, 40% ascended, half of those ascendant) into the reward cache; veteran, elite and champion gate
  creatures rarely leave one. The CRAFT tab's KEYS page forges a key of each rank around a boss core (plus four cores,
  mana stones and metal). The shop sells E to C keys a few a day and buys keys back (`DungeonKeys.SELL`, sealed x1.5,
  ascendant x2). All numbers are first guesses for the balancing pass.
- **Cave recycling** (`gate/GateWiper`): a closed cave (cleared and left, run over, given up) is put back to rock,
  box by box (`DungeonLayout.boxes`: every room, passage and open space, with a margin), after `GRACE_TICKS` (20 s)
  with nobody in its slot, `BUDGET` blocks a tick, one cave at a time; whatever was left
  in it (items, monsters tagged with `GATE_ID_TAG`) is removed, its slot goes back to `GateData.freeSlots` for the
  next gate and its record is forgotten (an awakening trial's is kept). Monsters saved in unloaded chunks are turned
  away when they load; a player who logs in inside a cave that is gone is sent home. Without this every run would
  leave a cave behind.
- **Looks**: six keys, one per rank (iron, bronze with patina, frost with icicles, blackened steel with amethyst,
  gold with sun rays, crimson obsidian with thorns), with glowing parts drawn full-bright. While a key turns: the
  key is held out and twisted in first person (`KeyClientEvents.HAND`), the body takes `key_turn` (`KeyAnimator`),
  and `KeyWorldFx` draws the lock (tumbler ring, lock face, keyhole, cracks, a thread of light from the key, the
  burst). Held still, a key sits small and upright in first person so the whole key shows (`models/item/key/key_held`,
  whose transform `KeyClientEvents` undoes while turning). A keyed gate has a keyhole at its heart, a tumbler ring in
  the swirl and no beam (`GateRenderer`). On
  screen (`RunHud`): the run clock at the top (gold when cleared, with the best time or NEW BEST), the time and chain
  bonus under the cleared banner, and the re-run prompt. The GATES tab (`GatesTab`): the key ring, the chosen key in
  a lock with what its gate holds and TURN, and the records by rank. The System window's tab bar now shrinks its
  labels when there are too many tabs to fit.
- **Commands** (`/system key ...`, level 2): give &lt;rank&gt; [n], sealed &lt;rank&gt; &lt;boss&gt; [ascendant], kit, turn,
  runs, reset, expire, seal, wipe. UI driver: `screen gates`, `gates <n>`, `gatesgo`, `rerun`.

## Hidden chambers, traps, weaknesses and gate weather

Code in `depths/` (server), `client/depths/` (client) and `enemy/ElementalProfile`. `DepthsModule.init` registers the
three blocks (`hidden_seal`, `trap_core`, `trap_ward`), the `weaknesses` attachment (`WeaknessLog`, kept through
death) and the payloads (`DepthsPayload`, `DepthsFxPayload`, `WeaknessPayload`). Lang lives in
`assets/beyondsolo_depths/lang`. Icons, light textures, block art, block models and the English text come from
`tools/gen_depths_assets.py`; the Spanish (`es_es.json`) is kept by hand next to it. Everything about one cave lives in `GateDepths` on its `GateInstance` and is saved
with it: what the seed says unless a command or a test forces it, and how far the party got. Caves saved before this
load as `GateDepths.legacy()` (weather only, their rock has no chamber or traps in it). An awakening trial has none of
it. `DepthsHooks` runs it all every half second for each cave with a party in it.

- **Elemental profiles** (`ElementalProfile`): every living thing takes each element by steps: undead fear light and
  fire and shrug off shadow and rot, spiders burn, blazes are immune to fire and drown, witches laugh at arcane, and
  so on for the vanilla monsters, the gate's own creatures (by kind), a boss's kind and a creature's affinity (a
  frostbound hound fears fire; an affinity always resists its own element). Steps add up into a rating: immune (x0),
  strong resist (x0.25), resist (x0.5), neutral, weak (x1.5), severe (x2). Physical is never rated. The multiplier
  applies to skill hits (`SkillCombat.strike`), weapon infusions, element procs and buildup (`EnemyHooks`); the hit
  carries its rating to the damage number (`HitTags`, a label over the number: WEAK, WEAK+, RESIST, IMMUNE). The
  first time a player lands an element on a weakness of a kind of creature, the System notes it (`WeaknessLog`, by
  profile key) and says so; from then on the creature under the crosshair shows the weaknesses that player knows
  (`DepthsHud`). Appraising Gaze shows the whole profile (weak and resists rows).
- **Gate weather** (`GateWeather`, `WeatherService`): each cave rolls its air from its seed: still in half the E
  caves down to a quarter of the S ones, else one of eight conditions (the theme's own three times as likely). Each
  favours one element and dulls another (skill hits and element damage such as infusion procs, burns and venom, by
  where the target stands) and pays more for the
  clear (x1.10 to x1.25 XP and gold). Hazards, every few seconds while the boss stands: mist (sight a few blocks),
  searing heat (burn buildup out of water), frost gale (slows, frost at the edge of the screen), mana storm (bolts on
  marked spots, leaving creatures charged with lightning), spore miasma (venom buildup), crimson haze (the cave's
  creatures hit 15% harder and heal 10% of what they deal), devouring dark (sight a few blocks, creatures 15% faster)
  and tremors (quakes, rocks on marked spots). On screen (`WeatherFx`): fog colour and distance laid over the cave's
  own (low priority, so the gate's fog runs first), the air itself drawn as light (motes, sparks, snow, spores, ash,
  wisps), storm flashes with a far rumble, and the condition's banner and chip under the run clock. None of it leaves
  the cave: going through to another dimension (the exit, a command) or coming back from a fall tells the screen at
  once (`DepthsHooks.leftCave`, `DepthsPayload.STATE` -1; the next beat would no longer see a cave to leave), the
  client lets go of the weather, a trap, the banners and the world moments by itself as soon as it is out of the gate
  dimension (`DepthsClient.tick`), and the frost a gale left thaws on the way out. Until 0.1.3 the way out forgot to
  say so and the condition's chip stayed on screen in the world.
- **Hidden chambers** (`HiddenKind`, `HiddenRooms`, `DepthsBuild`): 30% of E caves up to 55% of S ones hide a
  chamber off one of their later caverns, at the end of a tunnel nothing else comes near (`DungeonPlanner.placeHidden`,
  appended after the rest of the plan with its own random so the cave is otherwise unchanged). Classic caves place it
  exactly as they always did; the other layouts also keep it clear of open spaces, built rooms' corners and the slot's
  edge, and hide it off any room on the way (not the boss's, not an island) when no cavern has room: a chasm's
  entrance, a descent's ledge caves. The tunnel's mouth is plugged with `HiddenSealBlock`s that pass for the realm's
  own walls (a look for every realm's wall blocks, course by course; each look's model points at that block's textures; some
  blocks have faint cracks with a violet speck; now and then a mote of light seeps out). It can't be mined: three
  knocks (hits, or arrows) bring it down; Pulse Scan within range outlines it through the rock and says something is
  hidden. Opening it gives the party XP and the System's HIDDEN DUNGEON moment. A vault holds a cache one rank above
  the gate's; a shrine blesses each who steps in once (strength, regeneration and resistance for 5 minutes); a lair
  wakes guardians of one rank higher and drops its hoard when the last one falls. Hidden caches have the violet look.
  `SEALED` is the awakening's door (see "The awakening"): never rolled (weight 0), forced into a player's first D-rank
  cave, placed past the boss's room (`DungeonLayout.planSealed`, `DungeonPlanner.placeOffBoss`) and dressed like the temple behind
  it (`DepthsBuild.dressSealed`); its wall ignores knocks and scans and breaks once the boss falls.
- **Trap rooms** (`TrapKind`, `TrapRooms`): past the first cavern, a cavern may be a trap room (12% plus 3% a rank,
  at most 1 + rank/3 a cave). It springs on the first step in, with the System's call-out: spikes under the party's
  feet, vents blasting the gate's element in columns, rockfall on marked spots (all marked on the floor first), or a
  lockdown: walls of red light (`TrapWardBlock`) across every way out and two waves of the gate's creatures, then it
  lets go and leaves treasure. The first three stop when their `TrapCoreBlock` breaks (four strikes; Pulse Scan shows
  cores; a core broken before its trap springs disarms it). A lockdown nobody is left in stands down after 20 s and
  springs again. Beating a trap pays XP. Strikes hurt the cave's creatures too. A lockdown only goes in a room rock
  shuts in (`DungeonLayout.enclosed`): a camp out in a hollow, an island over a chasm or a ledge cave open onto a
  shaft rolls one of the other traps instead (`TrapKind.pickOpen`; every room of the original caves is shut in, so
  their traps roll as before). A rockfall room's loose ceiling never hangs lower than four blocks over the floor under
  it, whatever it hangs from (`DepthsBuild.hang`).
- **Looks**: banners (`DepthsHud`: the weather's condition card, the glitching HIDDEN DUNGEON stamp, the red TRAP
  stamp, waves, cleared), the weather and trap chips under the run clock, the weakness tag under the crosshair;
  world moments (`DepthsWorldFx`): knocks cracking the wall, Pulse Scan's ring with walls and cores outlined through
  rock, the wall crumbling with light pouring out, bolts, falling rocks, a core cracking and shattering, the shrine's
  column of light. The depths' vanilla particles never come within about two blocks of a first-person camera, counting
  how far a burst spreads and flies (`NearFx` on the server, `DepthsWorldFx.particle` on the client): those players
  get the moment from the light effects. A player who comes into a cave (or logs back in) while a trap is going off
  gets its chip back (`DepthsPayload.TRAP_ACTIVE`, sent with the cave's state).
- **Commands** (`/system depths ...`, level 2): `gate <rank> [weather] [hidden] [trap]` opens a gate five steps ahead
  with its depths forced (`seed` keeps what the seed says), `weather <id|seed>`, `info`, `hidden` (go to the wall),
  `open`, `reveal`, `trap` (just outside the next trap room), `core` (beside the nearest trap core), `bolt`, `rock`,
  `profile` (of the creature looked at), `weaknesses reset`. A forced trap goes in the first cavern past the first
  that it works in, or another room past the entrance (`DepthsCommand.trapRoom`). All numbers are first guesses for
  the balancing pass.

## Dungeon breaks and gate sensing

Code in `breaks/` (server) and `client/breaks/` (client); lang in `assets/beyondsolo_breaks/lang`. `BreakModule`
registers the payloads (`BreakPayload`, `GateSensePayload`). A gate's clock is a `BreakState` on its
`GateInstance`, saved with it.

- **The clock** (`DungeonBreaks.step`, every second for each gate that `canBreak`: open, uncleared, not a trial, a
  keyed door or a gate in the gate dimension). It starts as soon as the gate stands in the world and runs
  `gates.breakMinutes` (40, give or take a fifth; 0 turns breaks off). STABLE, then UNSTABLE for the last third (the
  System's ALERT pop-up to everyone within `WATCH`, 160 blocks, with rank, distance and direction; the collapse timer
  on their HUD), then IMMINENT for the last minute (a second warning), then BROKEN. Fair to the player: the clock waits
  while anyone is in the cave or on their way in (`GateService.playersIn`, `inTransitTo`); a gate nobody is near
  (`watched`: a living player within `WATCH`, the spot ticking, not peaceful) holds at one minute left and its last
  minute only counts down in front of someone. A gate whose block is gone from a loaded spot (taken out with a
  command; players can't break one) has nothing left to break and stops counting. The gate block gets the phase and
  times (`GateBlockEntity.setBreak`) for its looks.
- **The break** (`breakOpen`): sounds, the BREAK moment for everyone within 200 blocks (shake by distance), the
  System's quest (`QUEST_TEMPLATE`, objectives `MOB_KEY` and `BOSS_KEY`) for everyone with the System within 96
  blocks: an emergency, started at once, when the break is at most one rank above them, offered otherwise; then the
  first wave. Waves
  (`spill`) are the gate's own creatures (`GateInstance.mix`, through `Enemies.spawn`), `waveSize` at a time every
  `WAVE_TICKS` (20 s) while someone watches, up to `waves` in all and `cap` out at once; the boss comes through with
  wave `BOSS_WAVE` (3) (`bossOut`: `GateBossEntity.configure` + `wake()`, its own entrance up close, a banner
  further off). Everything that came out carries `BREAK_TAG` (the gate's id) and is leashed to the gate (`LEASH`, 40
  blocks: walked back, or pulled back through the rift when far out). What they bring out later joins the break too
  (`adopt`: the boss's summoned adds, its enrage adds, the halves of a creature that splits), so it is leashed and
  crumbles with the rest. A boss lost without falling comes through again after 30 s. A broken gate turns everyone
  away (`message.beyondsolo.gate.broken`).
- **Suppression** (`suppress`, when the boss dies): the gate collapses (CLOSED), the rest of the break crumbles to
  ash (no loot), the boss's reward cache lands where it fell, and everyone within `SHARE` (64) of the boss or the gate gets
  `REWARD` (1.25) times the gate's clear XP and gold, the SUPPRESSED moment, and their break quest done. Break creatures that load back in
  after their break ended crumble too (`onJoin`).
- **Gate sensing** (`GateSense`): Pulse Scan also sends `GateSensePayload`: every gate within `range(level)` (112
  blocks at level 1, 16 more a level), nearest first, at most 12, with rank, state (holding, unstable, imminent,
  broken, cleared, or the viewer's own sealed door) and ticks left. Others' sealed doors and keyed gates are left out.
- **Looks** (`client.breaks`): `BreakClient` keeps the statuses, banners and sensed gates. `BreakHud`: the collapse
  timer chip under the boss bar and run clock (amber, then red with a heartbeat in the last minute, crimson with
  monsters out and the boss once broken, dimmed with an hourglass while hunters inside hold it), the gate compass
  across the top while a pulse lasts (rank letters, red pulse for gates coming apart, labels with distance and
  countdown) with the same marker hanging over each sensed gate in view, and the banners: the torn DUNGEON BREAK stamp, the boss's arrival, BREAK SUPPRESSED with its XP. Quest
  windows wait for a break banner (`QuestOverlay` checks `bannerPending`). `BreakWorldFx`: the break's flash, pillar
  and shock rings, each wave's pulse, the boss's ash column, the red leak from a gate coming apart, and sensed gates
  as pillars of their rank's light seen through the ground (far ones drawn nearer along the same line, so fog doesn't
  hide them). `GateRenderer` cracks the ring, tints and speeds up the vortex and makes it tremble in the last minute;
  a broken gate stays torn wide with a red beam. Near a broken gate the air turns red (`BreakClientEvents.fogColor`)
  and the screen's edge throbs. Without the System a player only feels it: the shake, the flash, the red air; no
  banners, chip, compass or System sounds.
- **Commands** (`/system break ...`, level 2, on the nearest gate within 96 blocks): `gate <rank>`, `status`,
  `unstable`, `imminent`, `clock <seconds>`, `now`, `boss`, `suppress`, `sense [level]`. All numbers are first
  guesses for the balancing pass.

## Red gates

Code in `redgate/` (server) and `client/redgate/` (client); lang in `assets/beyondsolo_redgates/lang`. `RedGateModule`
registers the payload (`RedGatePayload`), the hunter's record (`RedRecord`, an attachment: cleared, escaped, fallen,
nights, most days) and the config (`RedGateConfig`, `redgates.*`). A red gate's run is a `RedGate` in `RedGateData` (saved with the
world), keyed by its gate's id.

- **Which gates** (`RedGates.latent`): any gate, any rank, about one in sixteen (`redgates.chance`, 0.06), decided by
  its seed so the answer never changes. Never an awakening trial or a keyed gate. The first one through decides: an
  awakened hunter turns it red (`enter`, from `GateService.beginEnter`); someone without the System defuses it for
  good. `/system redgate force` makes the nearest gate turn red.
- **Turning red** (`start`): the gate block turns red (`GateBlockEntity` red time, `GateRenderer`/`GateModels` draw it
  in blood), the run takes a slot (`RedWorld.SLOT_SPACING`, 4096 blocks apart in `beyondsolo:red_gate`; the game-test
  server has no datapack dimensions, so tests lay the slots out in the overworld down `TEST_LANE_Z`), and what waits
  inside is a rank above the gate's (now and then two; S stays S). For `redgates.openSeconds` (8) the rest of a party
  can follow; then it SEALS: the gate in the world turns everyone away, the way out inside turns its hunters back
  (`exit`), and the gate's break clock is held (`holding`, read by `DungeonBreaks`). Other systems ask `occupied`
  before pulling a hunter away (penalty zone, job trial, surprise quests).
- **The Red Winter** (`RedWorld`, `RedWorldBuilder`): a snow world of spruce woods, ice spires and frozen seas (its own
  biome and noise settings, both `beyondsolo:red_winter`). Each run's patch is shaped a step per tick: the breach where hunters land (black
  stones, cracked tiles, red veins, two campfires, the way out sealed and burning red), the master's keep about a
  hundred blocks off (ice and deepslate walls, six towers, a throne, a spire with a red light that shows from the
  breach) and `CAMPS` (5) camps in the snow. Each of these sites stands on its own mound (`site`): a skirt slopes from
  its edge down into the land round it, so the keep never ends up on a pillar in a frozen sea. The patch ends at `EDGE`
  (200 blocks from the breach): the blizzard there is a wall that pushes hunters back, and throws back anyone who gets
  24 blocks past it.
- **Time** (`RedGate.insideTime`): runs `redgates.timeRate` (8) times faster while anyone is inside. Night
  (`NIGHT_START`..`NIGHT_END`) brings raids (`raid`: the land's own creatures 20 to 28 blocks out, after one hunter,
  angrier than outside: `fury`, +25% health, +15% damage), dawn sends them back into the snow, blizzards blow in now
  and then. Creatures come from the run's own mix (`mix`: frost-touched, our own kinds only, no vanilla mobs).
- **Cold** (`cold`, every second, off with `redgates.cold`): out in the open at night or in a blizzard a hunter
  chills (0..`MAX_CHILL`); past `FROST_RIM` (70) vanilla's frost creeps in from the screen's edges, and frozen through,
  vanilla freezing takes over. A fire close by (`warmth`: a lit campfire,
  flames, lava, magma, a lit furnace) warms them; a roof overhead cuts the chill to a quarter. Past `EDGE` the wind
  chills them faster.
- **Camps and keep:** a pack rises round a camp's fire as hunters come within `CAMP_REACH`; once it is dead its stash
  is theirs (a quest objective). The keep's guard turns out within `KEEP_REACH`; the master (`masterKind`: a boss
  that lives at the inside rank, `GateBossEntity.configure`) wakes within `WAKE_REACH` with its intro, and calls two
  adds when enraged.
- **The end:** the master's fall (`masterFell`) releases it: the seal breaks, raiders scatter, a red reward cache
  (`RewardCacheBlockEntity`, twice its rank's loot, often a key) comes down where it fell, a way out opens there, and
  everyone inside gets `redgates.rewardMultiplier` (2) times a gate of the inside rank in XP and gold, the quest done
  and a clear on their record. Out they go, and the gate closes. If `redgates.releaseDay` (8) is set, the dawn of that
  day breaks the hold too (no reward). A hunter who dies inside (`playerFell`) is out of it and wakes where they
  respawn, their quest failed and a fall on their record; once everyone it took has fallen (`failed`), the red fades,
  it is an ordinary gate again (defused) and its break clock starts at its last minute.
- **Looks** (`client.redgate`): `RedGateClient` keeps the state the server sends and the inside clock (it sets the
  sky's time and the snowfall). `RedGateOverlay`: the System's WARNING window (glitches in, types its lines, the seal
  ring counting down then locking), the inside capsule (sun and moon dial, day and release day, clock and rate, the cold
  bar, camps and kills, a compass to the master or the way out), the capsule outside for anyone near a red gate,
  banners (night, dawn, raid, blizzard, camp taken, freezing, edge, released) and the summary card once home.
  `RedGateClientEvents`: the dimension's sky, the red cast of the fog at night and the white-out of a blizzard.
  `GateOverlay` draws red gates' moments in blood (`GatePayload.RED`). Its moments and the warning wait while a world
  loads behind its own screen (`GateOverlay.loading`): otherwise the card coming home played out unseen behind
  "Loading terrain" whenever the chunks at home took a few seconds, and the summary waited on the cover after it.
- **Commands** (`/system redgate ...`, level 2): `force`, `gate [rank]`, `now [rank]`, `build`, `jump <to> [n]` (night,
  dawn, day n, time n, raid, blizzard [s], calm, seal, release, clear, chill n, or to the breach, keep, a camp, its
  master), `leave`, `end`, `status`. All numbers are first guesses for the balancing pass.

## The Crimson Citadel: the tower, the Endless Spire and the boss rush

Code in `tower/` (server) and `client/tower/` (client); lang in `assets/beyondsolo_tower/lang`. `TowerModule.init`
registers the hunter's records (`TowerData`, attachment `beyondsolo:citadel`, kept through death), the key, the Sigil,
the lord and the payloads (`TowerPayload` for the run, `TowerSyncPayload` for the records, `TowerActionPayload` for the
page's buttons). The citadel is its own dimension (`data/beyondsolo/dimension/crimson_citadel.json`, an empty void with
the `citadel_sky` biome); the game-test server has no datapack dimensions, so there the halls go up in the overworld.

- **The key** (`CitadelKeyItem`): sold in the System shop's special shelf from D rank (`TowerAPI.KEY_RANK`, 12000
  gold, `ShopCatalog` entry `citadel_key`). Used, it binds to an awakened hunter for good (`TowerAPI.bind`) and is
  used up; the CITADEL page opens. The System tells a hunter once where it is sold when they reach D rank.
- **The castle** (`TowerFloors`, worked out from the floor number alone): floors 1 to 100 in ten wings of ten
  (Outer Gate, Barracks, Ossuary, Forge, Archive, Cloister, Garden, Court, Ramparts, Throne), each wing its own
  stone and light. Ordinary floors are two to four waves; elite floors (the fives) a champion and its elites; siege
  floors (the sevens) a hall to hold for 40 s while it keeps sending more; every tenth floor a gatekeeper (a named gate
  boss, ascended from the 60th, in a round hall); the 100th is the lord's throne hall. Creatures come from the
  creature roster at the floor's rank (`EnemyRoster`, `Enemies.spawn`), never vanilla mobs. A beaten gatekeeper is a
  checkpoint: the next climb starts above it (`TowerData.startFloor`).
- **A run** (`TowerRun`, one `Session` per hunter, each in its own slot 1024 blocks apart along x): `PULL` (a crimson
  ring and pillar, the screen closes in red) while the first hall is raised a slice at a time (`CitadelHalls.Job`,
  `BUDGET` blocks a tick), `ARRIVING` (the title card; its clock waits until the hunter's screen shows the hall, which
  the client says with `TowerActionPayload.ARRIVED`, at most 10 s, so the card is never lost behind "Loading terrain"
  and the floor never begins there), `FIGHT`, `CLEARED` (the seal comes off the stair door, the exit
  sigil lights, the floor's rewards; the next hall is already going up on the other pad), `CLIMBING` (the stair leads
  straight into it). A gatekeeper's floor drops a reward cache; the stair waits until it is opened. Standing on the
  exit sigil for 2 s lets them out (`LEAVING`). A blow that would kill ends the run instead (`FALLEN`): no death, nothing
  dropped, home weakened for a minute, the floors above the checkpoint lost. Logging out ends it too. No harm comes
  to them while they are pulled, arriving, climbing or leaving.
- **The lord** (`CitadelLordEntity`, GeckoLib model from `tools/gen_tower_assets.py`): Vharos, the Crimson King,
  dormant before his throne until woken (or until 5 s pass), then a roar intro; untouchable only while dormant and
  rising. His moves are announced on the floor: cleave (a wide sweep), lance (spears of light along a line, three
  lines enraged), decree (lightning on marked ground under everyone he fights), leap (onto his challenger). Under half
  health he is enraged; under 30% he calls the blood moon (the sky turns red, two champions of his court come out of the
  light, blood lightning keeps falling). No regeneration, no second life. His fall conquers the Citadel
  (`TowerAPI.conquered`, title `citadel_conqueror`) and may drop the **Sigil of Dominion** (`DominionSigilItem`): one
  kill in four, never more than four without it (`SIGIL_CHANCE`, `SIGIL_PITY`). `TowerAPI.hasSigil` stays true once
  held: it opens the Sovereign rush and is what the monarch path will ask for.
- **The Endless Spire**: past the throne the stair climbs on (or the page's button starts at floor 101). The wings
  come round again, every floor 5% more health and 3% more damage on its creatures than the last, a gatekeeper (always
  ascended) every ten floors. The best floor cleared is the record (`TowerData.spireBest`).
- **The boss rush** (`RushTier`): in the colosseum, every gate boss of the roster once in a random order at the tier's
  rank, the last one ascended, with a 12 s breather between bouts (30% health and mana back). Tiers E to S open up to
  one rank above the hunter's own; the Sovereign rush (the Sigil) brings all six ascended at S rank and then the lord
  (title `rush_sovereign`). The clock runs from the first boss waking to the last one falling; the best time and the
  clears are kept per tier.
- **Client**: `CitadelTab` (the CITADEL page: the castle's floors lit to the checkpoint, CLIMB, the spire and its record,
  the rush tiers with their times; the sealed gate until the key is bound), `TowerHud` (the pull, title cards, the
  floor plaque under the boss bar, banners, the climb, the ends, the exit hold), `TowerWorldFx` (the seal curtain on
  the stair door, the exit sigil, the moon and the red haze in the sky), `CitadelLordRenderer` (his glowing seams,
  the glare of his eyes, the blood moon's heat, the light that takes him as he dies), `TowerClientEvents`. While the
  Citadel has the screen (`TowerClient.holdsScreen`: pulled in, a title card, the climb, an end card) the System's
  pop-ups and quest windows wait; they ask every frame instead of sharing `NotificationManager`'s held flag. A run's
  creatures saved with the world when it stopped mid-run are turned away as their chunk loads (`TowerRun.onJoin`).
- **Commands** (`/system tower ...`, level 2): bind, climb, enter <floor>, lord, endless, rush <tier>,
  jump arrive|clear|climb|wake|kill|enrage|moon|fall|breather|leave|siege, leave, cancel, checkpoint <floor>, sigil,
  sigil dry <kills> (stages the pity), info, reset. All numbers are first guesses for the balancing pass.

## World bosses and monarchs

Code in `worldboss/` (server) and `client/worldboss/` (client); lang in `assets/beyondsolo_worldboss/lang`.
`WorldBossModule` registers the five bosses, `BossProjectile`, the Monarch's Seal, the monarch armor (pieces, the
`monarch_aura` component), the hunter's record (`WorldBossRecord`, an attachment kept through death: kills by kind,
times they did the most, the pity count), the payloads and the config (`WorldBossConfig`, `worldbosses.*`). What is
abroad lives in `WorldBossData` (saved with the overworld): at most one great threat at a time, when the next comes,
and how many have been slain.

- **The kinds** (`WorldBossKind`): two world bosses that stride over the land as it is (`GiantBoss`: through trees,
  over hills, never into deep water; they roam `ROAM` blocks round where they rose and give up a chase past `LEASH`):
  Ghorrath, the Walking Mountain (`TitanBoss`, flame) and Thurvok, the Horned Calamity (`BehemothBoss`, blood). Three
  monarchs (`MonarchBoss`: they walk, leap and blink): Veyrith of Stillness (`StillnessMonarch`, frost), Khorvane of
  Hunger (`HungerMonarch`, blood) and Thyrax of Storms (`StormMonarch`, storm). Each kind has its health and damage (multiples of an S-rank gate
  boss's, `Balance.bossHealth`/`bossDamage`, so they move with the power curve: about 85,000 to 105,000 health), colour and two phase lines (world bosses 66% and 33%, monarchs 60% and 25%). Each class comment lists its moves
  phase by phase.
- **When they come** (`WorldBosses`): once an awakened hunter in the overworld reaches `minLevel` (26, a C-rank gate's home level), the first
  world boss rises `firstMinutes` (30) later, about 80 to 130 blocks from a hunter (`minDistance`, `maxDistance`),
  and the next comes `intervalMinutes` (75) after the last one left. Once `monarchAfterKills` (2) world bosses have
  fallen and a hunter is `monarchMinLevel` (56, an A-rank gate's), the next is a monarch by `monarchChance` (0.3). A Monarch's Seal
  (`MonarchSealItem`, the best hunter's chance gift from a world boss, `sealChance`) calls one down at once when
  broken in the overworld. The System calls every hunter in the overworld (a pop-up, a banner, a quest from
  `world_boss/hunt`, its mark on screen), and the status goes out every two seconds (`WorldBossPayload`). If no hunter
  comes near it or hurts it for `abandonMinutes` (12), it sinks back into the earth (a monarch rises back into its gate).
- **The fight** (`ColossalBoss`): three phases. A phase can't be skipped: however hard it is hit, its health stops
  at the next phase's line (`phaseFloor`) until that phase has begun (a short transition: a roar, the land shaking,
  the phase's banner). It takes damage all the while: no shield, no immunity, no healing, and no floor in its last
  phase. Moves (`Move`) are an animation plus a script run on its ticks; every blow is announced on the ground first
  (`WorldBossFx` sends `WorldBossFxPayload`s: discs, bands, strips, wedges, squares, and waves that run out along the
  ground to be jumped), and what it throws (`BossProjectile`) flies a set path in a set time so it lands as the warning
  said. It only ever hits hunters, what they own (shadows, pets), iron golems and whatever it is fighting. The health
  each hunter takes off it is kept (their shadows and pets count for them).
- **Monarchs** (`MonarchBoss`): a gate tears open in the sky (`GATE_HEIGHT` up), and `descentSeconds` (60) later the
  monarch comes down through it and lands with a blow. Its domain (`DOMAIN` blocks round it) is its own rule:
  Stillness slows and freezes, Hunger starves and bleeds, Storms calls lightning down (drawn on the ground first).
  Hunters below `PRESSURE_LEVEL` (74, an S-rank gate's home level) within `PRESSURE_RANGE` are weighed down. Its army (`MonarchArmy`, tagged `ARMY_TAG`)
  is S-rank gate creatures of its affinity, called up as it lands and again as it angers; they crumble when it falls
  or leaves (soldiers in unloaded land as soon as it loads). Its authority, in the last phase, is named on every
  hunter's screen with the time left to get clear.
- **The kill** (`slain`, `pay`): every hunter with at least `MIN_SHARE` of the damage is paid by their share (in full
  from half of it up, so a pair both get everything): System XP (a quarter of its health, seven tenths for a monarch), gold, greater mana stones, a supreme healing elixir,
  S-rank weapons, an Elixir of Rebirth from a monarch, a quest step for `SLAY_KEY`, and a line on their record.
- **The chronicle** (`WorldBossJournal`): each kind has a journal page among the bosses (`boss:<asset name>`,
  `JournalService.pageOf` maps the entity, `JournalArt` draws it), a kill counts as a boss for the boss titles and
  the boss-damage perk, and every paid hunter earns Colossus Breaker (legendary, a world boss) or Crownbreaker
  (mythic, a monarch); the one who did the most to a monarch also earns the secret Throne Taker. The TITLE ACQUIRED
  moment waits for the kill's banner.
- **Monarch armor** (`MonarchArmor`): grade `MONARCH`, a tier above the divine sets, level `LEVEL` (60), any class.
  Each monarch leaves its own regalia (`setOf`): Stillness (blows slow, every fifth freezes), Hunger (kills heal and
  stack strength up to three times), Storms (every fourth blow calls lightning that leaps to two more), with 2- and
  4-piece bonuses like any set. Each hunter who did a tenth of a monarch's health gets a piece by
  `monarchArmorChance` (0.2), or for sure after `monarchArmorPity` (5) monarchs without one; `piece` prefers one they
  lack. A piece takes its wearer's aura when put on (the `monarch_aura` component: their class's colour, the
  monarchs' violet `DEFAULT_AURA` without one). On the client `MonarchAuraLayer` draws its light over the worn plate:
  `<set>_aura_1/2.png` (seams, crest and edges, breathing in the aura's colour) and `monarch_current.png` (a slow
  current running over the whole of it); the item colour handler tints the icon's `_aura` layer. Both use
  `ModRenderTypes.armorGlow`/`armorCurrent`: vanilla draws armor with a view offset (a hair toward the camera), so a
  layer laid over it needs the same offset or the plate hides it, and like the enchantment glint they test for equal
  depth, so they only light the plate itself and never the see-through parts of a wing or a crest. Monarch pieces
  have no enchantment glint (it would cover the aura's colour). `WorldBossWorldFx.regalia` adds motes and wisps of the
  aura rising off whoever wears pieces (more for more pieces) and a faint halo for a whole set.
- **Looks** (`client.worldboss`, in the series look: see "Series look"): `ColossalBossRenderer` and `WorldBossModel`
  (GeckoLib, glowing parts, the Walking Mountain's molten skin in its last phase; a monarch burns in black flame over
  a rim of its colour and stands in a pool of ink, black flame taking it as it dies; a world boss's seams and edges
  open and burn in its last phase; a world boss rising from or sinking into the earth stays on the ground and is drawn
  sunk by `ColossalBoss.buried`, because the renderer skips an entity whose block is in a chunk section that was never
  drawn, as buried rock is), `BossProjectileRenderer`, `WorldBossWorldFx` (the warnings lying on the land as it is
  through `Terrain` as thin rims that fill, waves, falling stones as streaks with white-hot heads, lightning, pillars,
  stars of light, cracks of light; a column of light over a far world boss, a pillar of ink over a far monarch; the
  gate in the sky, a hole of ink; a monarch's domain, the land turned to ink with veins of its light; each threat's
  breath, `breathe`), `WorldBossHud` (the bar with phase notches and your share, its name in the threat colours
  of `enemy/HiddenLevel` like every System creature's, a marker with distance and arrow when it is far, the domain's
  edge tint, banners). Ground textures are `textures/effect/wb_*.png`.
- **Art** is made by scripts: `tools/gen_worldboss_assets.py <titan|behemoth|stillness|hunger|storms|fx>` writes a
  boss's model, skins (main and glow) and animations, or (`fx`) the warning textures and the armor's current.
  `tools/gen_monarch_armor.py [set...|seal]` writes the regalia (worn layers, aura masks, worn model parts, item
  icons) and the seal; `--preview <set> [rrggbb]` and `--icons [rrggbb]` render them in an aura colour into
  `build/preview/`.
- **Commands** (`/system worldboss ...`, level 2): `spawn <kind> [here]`, `land`, `phase <n>`, `move <name>`,
  `hurt <amount>`, `kill`, `withdraw`, `stop`, `status`, `next <minutes>`, `armor <set> [n]`, `seal`, `record`. All
  numbers are first guesses for the balancing pass.

## Parties

Friends who join a server should end up in the same place and go through gates together. Three things kept them apart
before parties: every new player's random first spawn was 800 to 3000 blocks from everyone else's, the awakening's
sealed door was personal (two friends without the System each got their own trial), and a keyed or red gate sealed
shortly after the first hunter went in. Parties fix that.

| Piece | What it does |
|---|---|
| `Party`, `PartyData` | A party: its id, leader and members (in the order they joined, with the name each went by). Saved with the overworld (`beyondsolo_parties`); a hunter is in one party at most, a party of one is never kept (loading drops one, and a hunter found in two parties stays in the first) |
| `Parties` | Everything that changes a party goes through here: `invite` (the leader, or anyone not in a party yet; the party is only made when the first invitation is accepted), `accept`/`decline` (by inviter, or the newest), `leave` (a leader who leaves hands the lead to the first member online; a party left with one member ends), `kick`, `promote`, `disband`, and `handle` for the PARTY page's buttons. Invitations live in memory for `party.inviteSeconds`. News goes out as a System pop-up to those who have the System and as a grey chat line to those who don't (`inform`); invitations also come as a chat line with clickable `[Accept]` `[Decline]` |
| `PartyCommand` | `/party` (no permission needed): `list`, `invite <player>`, `accept [name]`, `decline [name]`, `leave`, `kick <name>`, `leader <name>`, `disband`, `regroup <name>`. `list` shows each member's rank, class, level, health, where they are and a clickable `[Regroup]`. It is how players without the System use parties (they have no System window) |
| Sharing | A kill counts for the party members near it (`sharers`: alive, in the same gate's cave, or within `party.shareRange` blocks anywhere else): the quest progress in full (`QuestHooks`, `GateService`'s key objective), `party.killShare` of the XP (`SystemEventHandler.onKill`, source `beyondsolo:party_kill`) and gold (`EconomyHooks`, reason `PARTY`) |
| Friendly fire | `onIncomingDamage` cancels a hit when the attacker (the player, or the owner of the shadow, pet or arrow: `culprit`) and the victim (or its owner) are in the same party, unless `party.friendlyFire` |
| Gates | Going in together already shared one cave. A keyed gate's door (`KeyedRuns`) and a red gate (`RedGates`) about to seal hold up to `party.followSeconds` while a party member of someone inside is within 48 blocks of it outside (`followers`, `holdFor`), and say who they wait for. Party members out by a gate hear who went in (`wentIn`). A dormant friend near a System hunter still gets their door: `AwakeningService.enteringGate` hides it when the one walking in, or a party member of theirs within 48 blocks, still waits for it |
| `Regroup` | Travel to a party member's side: stand still `CHANNEL_TICKS` (5 s; moving more than `DRIFT` or being hurt breaks it), then land beside them (`spotNear`). Both must be out in the overworld, the Nether or the End, not in a gate, trial, the Citadel, the Penalty Zone, an evaluation or the awakening. At least `party.regroupMinDistance` blocks apart; then `party.regroupCooldownMinutes` (kept on the player in `PartyModule.REGROUP_READY`, through deaths and logouts; -1 turns regrouping off) |
| Sync | `PartySyncPayload` (members with health and whereabouts, `Place`: world, gate with its rank and name, red gate, Citadel, Penalty Zone, trial, job trial, offline; invitations; the regroup clock) on every change and every `SYNC_TICKS` to everyone in a party with someone else online. `PartyEventPayload` carries the regroup moments and the answer to a button. `PartyActionPayload` is a button |
| `client.party` | `PartyClient` (what the client knows), `PartyTab` (the PARTY page: members with class, rank, level, health, where, and regroup / lead / remove buttons; invitations; hunters online to invite; leave and disband), `PartyHud` (a frame per member on the right with health and where they are, an arrow and distance when they are where the player is: the same world out in the open, or the same cave (`PartyClient.samePlace`: every gate's cave shares one dimension, so the cave's slot must match); a marker over each such member in view and an arrow at the screen's edge for those out of view; the regroup gathering and its end) |

What stays solo, as in the series: the Crimson Citadel, the Penalty Zone and the job change trial. The awakening is
shared only when friends are called in the same cave. Config: `beyondsolo-party.toml` (`party.maxSize` 6,
`party.inviteSeconds` 60, `party.shareRange` 48, `party.killShare` 0.5, `party.friendlyFire` false,
`party.followSeconds` 45, `party.regroupCooldownMinutes` 10, `party.regroupMinDistance` 64) and `spawn.together` in the
main config. Game tests stand fake players in for real ones; `util/OnlinePlayers` lets them be found by id
(`addTestPlayer(helper, player)`), since fake players are never in the server's player list, and only while that test
runs: NeoForge keeps fake players for the whole run, and one a finished test left in a cave would otherwise count as
still standing in that cave slot when another test's gate reuses it.

Landing together (`spawn.LandingData`): the world's first player lands on the usual random first spawn and it is kept;
everyone after lands 3 to 8 blocks from it. Both pick only spots with room to stand (`roomToStand`: nothing solid at the
feet or head, so nobody starts out crawling under a tree's low leaves).

Testing: `/party ...` with a second account, or in the headless client `partydemo [alone]` (a made-up party round the
player), `partygo <action> [name]` and `partyfx start|broken|done|joined` (`tools/uitest`). A second real client can
join the dev server under another name (offline mode) for checks with two players.

## Guilds and raids

A party lasts an evening; a guild lasts. Hunters band together under a name, a tag and a crest, pay into a treasury,
hire hunters of their own and take on raids: gates too big for one party, with lieutenants that seal the boss's
chamber, a boss grown for the number of raiders and fighting in phases, and raid roles. As in the series, an
association-style System hands raid gates to guilds that can pay for them, and the big ones also open on their own.

| Piece | What it does |
|---|---|
| `guild/Guild`, `GuildData` | A guild: id, name, tag (2-4 capitals or digits), crest (`GuildColors.CRESTS`) and colour (`GuildColors.PALETTE`), master, members in the order they joined (`GuildRole` MASTER, OFFICER, MEMBER, each with what they gave in guild XP), level and XP, treasury, gates and raids cleared, hired hunters, today's recruits and a log of its last 14 events. Saved with the overworld (`beyondsolo_guilds`); a hunter is in one guild at most and a guild whose master is missing on load is handed to its first member |
| `guild/Guilds` | Everything that changes a guild goes through here and answers with an `Outcome` (a `guild.beyondsolo.*` line): `found` (evaluated hunters only, `guild.foundingCost` gold; name 3-24 letters, digits and `' & . -`, both name and tag unique in any case; gives the Guild Founder title), `invite` (master or officer; invitations live in memory for `guild.inviteSeconds`), `accept`/`decline` (by guild, or the newest; joining another guild leaves the old one, but a master can't walk out of a guild they lead that way), `leave` (a master hands it to their first officer, else the member who has been in longest; the last one out ends it), `kick` (officers only plain members), `promote`/`demote`/`transfer` (master), `disband` (the treasury goes to the master), `deposit` (anyone; `guild.goldPerXp` gold is one guild XP), `withdraw` (master), `hire`/`dismiss`, `permit`, `chat`, and `handle` for the GUILD page's buttons. A member's tag shows before their name in chat, over their head and in the player list |
| `guild/GuildPerks` | What a level (1 to 10) gives: members (`guild.baseMembers` + `guild.membersPerLevel` a level), hired hunters (`guild.baseRoster`, one more every second level and at the top), a little more gold (kills, quests, gates, raids, the Citadel; `Economy.add` asks it) and System XP (`Guilds.onXp`), raid permits of higher ranks (E-C from level 1, B 3, A 5, S 7) at a discount. XP to the next level grows as `n^1.7`; a member's gate clear gives `guild.gateXp` x (rank tier + 1)^2, a cleared raid ten times that for each member who was there |
| `guild/GuildHunter`, `Recruits` | A hired hunter: name, class, level (their rank is their level's, `Balance.rankFor`), skin, price, raids, kills and the time they are hurt until. Each guild sees `hunters.recruits` hunters looking for a guild each day (the ranks lean higher as the guild grows); their price is `hunters.hireCost` for their rank and 3% more for each level into it. A hunter who falls in a raid is hurt for `hunters.injuryMinutes` and stays home until then |
| `guild/GuildHunterEntity` | A hired hunter's body in a raid's cave: a player-shaped fighter in their class's role (`RaidRole.forClass`: guardians are vanguards and taunt, healers support and mend the worst hurt, the rest strike; rangers and mages shoot). Goals in order: run for the light in a cataclysm, mend, break the nearest pylon while the barrier stands, shoot, melee, keep with the raiders (slipping through to them when far behind). Never hurt by raiders, other hunters, pets or shadows; never saved: the raid brings its hunters back beside a raider after an unload, and one the raid no longer wants goes home in a puff of light |
| `guild/GuildCommand` | `/guild` (no permission): `info`, `create <tag> <name>`, `invite`, `accept [tag]`, `decline [tag]`, `leave`, `kick`, `promote`, `demote`, `leader`, `disband`, `deposit`, `withdraw`, `hunters`, `permit <rank>`, `chat`; `/gc <message>` for guild chat |
| `raid/RaidState` | A raid, saved with its gate (`GateInstance.raid()`): phase (`MUSTER`, `COUNTDOWN`, `RUNNING`, `CLEARED`, `FAILED`), leader, the guild of a permit raid, raiders (role, status `WAITING`/`INSIDE`/`FALLEN`/`LEFT`, class and level at the muster), the guild hunters along (fallen, kills), the lieutenants still standing, the weight it was made for (players, and half a raider for each hunter, past the first) and how far the boss's fight has got |
| `raid/Raids` | The muster: walking into a raid gate before it starts musters you instead of taking you through (`enter`, the briefing opens); the first to muster leads; anyone picks a role; the leader starts a `raid.countdownSeconds` countdown and decides whether their guild's hunters come; then everyone within `raid.musterRange` goes through together (`launch`, through `GateService.pullIn`) with as many fit hunters as there is room for, and the gate seals behind them. Inside: packs `raid.packScale` bigger, two lieutenants (three from B; `lieutenantPosts`) that wake as raiders come near and keep the boss's chamber shut (`watch` throws raiders back from it) while one stands, a boss `raid.bossHealth` + `raid.bossHealthPerRaider` x weight times as strong (`grow`). It minds whoever hurts it most (a vanguard's blows count `roles.vanguardThreat` times). At 70% its barrier rises with `raid.pylons` pylons of `raid.pylonHits` hits each (more of them, and tougher, for big raids), healing it while they stand; the last pylon down makes it reel for `raid.staggerSeconds` and take `raid.staggerDamage` more. At 50% it calls more creatures than a gate boss does, and from 25% every `raid.cataclysmSeconds` it channels a cataclysm (`raid.cataclysmDamage` of everyone's health) that only the lights it leaves on the floor shelter from. Strikers deal `roles.strikerDamage` more, vanguards take `roles.vanguardGuard` less, supports heal everyone near them a little every ten seconds. The clear pays `rewards.scale` times a gate's with a cache for each raider (up to `rewards.caches`), guild XP, one to three levels for each hunter who came (more the further the raid's rank is above theirs), the Raid Breaker title, and Spearhead of the Raid for the raider who dealt the most in a raid of four or more (hunters count); the results board shows who dealt what. A raid nobody is left inside for 15 seconds (after the first 30) has failed: the gate shuts and its hunters go home. While every raider still on it is offline (a restart, a lost connection) it waits for them instead. A failed raid takes its quest off its raiders; one who was away when it ended loses it on logging back in (`dropStaleQuests`) |
| `raid/RaidPylonEntity` | A pylon: an end crystal on the chamber's floor with its beam in the boss, its name showing the hits it has left. Only raiders' and hunters' blows count, one hit per moment (a sweep or a volley is one); never saved, raised again if the chamber was unloaded while the barrier stood |
| Raid gates | `raid.naturalChance` (by rank, B 4%, A 7%, S 10%) of a gate that opens on its own is a raid gate (`GateSpawner.trySpawnNear`, `Raids.naturalRoll`); a guild's master or officer can request one of a rank its level allows with `/guild permit` or the GUILD page, paid from the treasury (`GuildPerks.permitPrice`), one open at a time, opening in front of them with them leading its muster. Everyone near hears a raid gate open |
| `raid/RaidCommand` | `/raid` (no permission): `status`, `join` (the nearest raid gate), `leave`, `role <vanguard/striker/support>`, `start`, `hunters on/off`, `brief` |
| Sync | `GuildSyncPayload` (the player's guild, members with who is online, hunters, recruits, the log, the guild's raid, invitations) on every change; `GuildEventPayload` (founded, level up, a button's answer); `GuildActionPayload` (a button). `RaidSyncPayload` (the raid the player is on: phase, countdown, roster with roles, status and health, the guild's hunters, lieutenants and pylons left, the boss's health and the cataclysm's clock) on every change and twice a second inside; `RaidEventPayload` (the briefing, banners, the cataclysm's warning, the results); `RaidActionPayload` (the briefing's buttons) |
| `client.guild` | `GuildClient` (what the client knows), `GuildTab` (the GUILD page of the System window, order 104: founding with name, tag, crest and colour; HALL with the crest, level, perks, treasury and log; MEMBERS with ranks and the master's and officers' buttons; HUNTERS with the roster and today's recruits; RAIDS with the guild's raid and the permits its level allows), `GuildArt` (the crests, drawn), `GuildHunterRenderer` (a player model in its skin, with class armor) |
| `client.raid` | `RaidClient`, `RaidBriefingScreen` (the roster by role, role picks, the leader's start and hunters switch), `RaidHud` (the muster's capsule out at the gate; inside, raid frames on the right instead of the party's, coloured by role, the objective under the boss bar (lieutenants, pylons, the boss), banners when the fight turns, the cataclysm's warning, the results board). The capsule and the objective stack under the boss bar, run clock and break timer like the other top pieces, and the cave's weather and trap chips go under the objective (`RaidHud.objectiveHeight`). Banners wait for a quest or title moment or a gate card on screen; the cataclysm folds those away (it can't wait); the results board comes in after the boss's DEFEATED and DUNGEON CLEARED cards and any quest or title moment already up, and the hide key folds it away. While a banner, a cataclysm or the board is up, quest and title moments wait (`RaidClient.holdsScreen`, as for the tower's cards) |

Config: `beyondsolo-guild.toml` (`guild.*` and `hunters.*`) and `beyondsolo-raid.toml` (`raid.*`, `roles.*`,
`rewards.*`); every number is a first guess for the balancing pass. Text: `assets/beyondsolo_guilds/lang` (English and
Spanish). Operators: `/solo guild create/level/gold/hunter/heal hunters/disband` and `/solo raid open/make/start
now/phase barrier|cataclysm` (`SoloGuilds`). `/solo wipe` takes a player out of their guild as if they left and off
any raid.

Testing: `GuildGameTests` and `RaidGameTests`; in the headless client (`tools/uitest`) `guilddemo`, `guildtab <page>`,
`guildform`, `guildfx`, `raiddemo`, `raidbrief`, `raidresults` and `raidfx` stand in made-up guilds and raids
(`client.guild.GuildDemo`), and `lang es_es` switches the language for Spanish screenshots.

## Monarch ascension

The road past every other hunter: the System player can ascend to the throne and become a monarch, as strong as the
monarchs that come down from beyond the gates and with powers of their own. Code in `monarch/` (server),
`client/monarch/` (client) and `skill/MonarchPowers` (the powers); text in `assets/beyondsolo_monarch/lang`. Names are
the mod's own.

| Piece | What it does |
|---|---|
| `MonarchData` | A hunter's road and reign (the `beyondsolo:monarch` attachment, kept through death): called or not, tries, when they may try again, in the hall or not (with where they came from, so they are sent back even after a restart), the crown (`Dominion`) and when they took it, monarchs slain, domains that clashed, authorities used |
| `Dominion` | The four crowns: Night (shadow), Winter (frost), Cinders (flame) and Thunder (storm), each with its colour, element, domain, authority and title; `leaning` is the crown a class leans to (the System's pick if the hunter doesn't choose) |
| `MonarchAPI` | The one entry point: `eligible` (Sigil of Dominion held, job changed, level `monarch.minLevel`, not crowned), `callIfReady`/`call` (the System's notice "[The Throne Calls]" and the quest `beyondsolo:monarch/throne`, 20,000 XP and 10 stat points), `raiseSigil` (using the sigil: says what it waits for, or starts the trial), `crown`, `uncrown`, `reset`, the strength (`applyBonuses`, transient modifiers put back with the skill passives: `monarch.healthBonus` 25% max health, `monarch.damageBonus` 12% attack damage, `monarch.manaBonus` 150 mana), `extraShadows` and the record (`monarchSlain`, `usedAuthority`). Calls are checked on login, level up, job change and while the sigil is carried (`DominionSigilItem.inventoryTick`) |
| `ThroneTrial`, `ThroneHall` | The trial, always alone. Raising the sigil starts a countdown, then the hunter is taken to the Hall of the Empty Throne (gate dimension, lane z = 75,000, one slot every 256 blocks; the overworld in game tests): a round hall under a dome, ten pillars, the throne on a dais at the north end. Phases: `INTRO` (the camera comes down the hall from the throne), `COURT` (two waves of the fallen court rise from the pillars: the gates' strongest kinds at 2.2 times the health and 1.3 times the blows of creatures made for the hunter's hidden level, more each wave), `ARBITER_INTRO` (the Arbiter kneeling before the dais tears its chains), `ARBITER` (`ThroneArbiterEntity`, the awakening's giant with its own colours at 3 times a gate boss's health for the hunter's level), `ARBITER_DOWN`, `CHOICE` (the throne's question; after `monarch.choiceSeconds` the System picks the crown the class leans to) and `CORONATION` (`CoronationTimeline`: light gathers, the crown locks in, the card, home). The Arbiter never having touched the hunter earns The Unblemished Crown. Falling in the hall doesn't kill: the hunter is thrown out with half their health and may come again after `monarch.retryMinutes`. Logging out ends the session in `PlayerLoggedOutEvent`, before the player is saved: past the Arbiter the crown is theirs (their pick or the class's leaning); before it they are sent home when they come back. The hall counts as busy for gates, keys, regrouping, the Citadel, the Penalty Zone and parties (`PartySyncPayload.THRONE`) |
| The crown | `MonarchAPI.crown`: the crown's title (Monarch of Night/Winter/Cinders/Thunder, mythic), the four powers, the strength above, the aura of worn monarch armor turning the crown's colour (`MonarchArmor.retint`), a chat line for everyone. A monarch's shadows climb past the marshals to `ShadowRank.REGENT` (`ShadowService.top`; a shadow keeps it if the crown is taken back), the army holds half as many again and 4 more stand out at once (8 for Night). A Night monarch raises shadows whatever their class |
| Against the monarchs from beyond the gates | A crowned hunter is never weighed down by a monarch's presence, hits monarchs `monarch.versusMonarch` (1.2) times harder, and their domain shelters whoever stands in it from a monarch's domain and the Storms monarch's tempest (`MonarchDomain.shelters`): the domains clash (Domain Unbowed). A share in a monarch's kill counts it (Crown Against Crown) |
| `MonarchPowers` | Four powers on the crown keys (Alt + the first four skill keys by default: Alt+Z, X, C, G), never on the skill bar, never from level, tome or rune stone; `SkillAPI.cast` refuses them to anyone not crowned. They level like any skill (to 10). **Domain** (60 mana, 90 s to 60 s): `monarch.domainRadius` (16) blocks round the monarch for 14 + level s, following them; the monarch and their allies (hunters, every shadow) hit 15 + level % harder and take 10% less, foes are worn down by the crown's element every second (Night: weakened and slowed, and the monarch's shadows mend and fight harder; Winter: slowed and chilled until they freeze; Cinders: set alight; Thunder: one foe in three struck by lightning each second, harder, and stunned). **Dread** (40 mana, 45 s to 30 s): everything within 12 + level/2 blocks freezes in terror for 2.5 + 0.15 x level s, weakened, and takes 20 + level % more from every blow for 8 s (bosses only flinch and take half). **Crown form** (80 mana, 3 min to 2 min): 18 + level s of blows 25 + 2 x level % heavier carrying the crown's element, 15 + level % less taken, faster; never untouchable. **Authority** (100 mana, 2 min to 80 s): Eclipse of Kings (binds every foe within 18 blocks in the dark, calls the whole army out at once, mended and free, 40% stronger for 15 s), The Long Winter (freezes everything within 16 blocks solid for 6 s, then shatters it), Ashen Heaven (a burning star falls where they look: a blast of 9 blocks and 5 s of fire), Thousand Thunders (8 + level/2 bolts on foes within 22 blocks, one after another, each stunning). Every hit goes through the hunter's power like a skill's (`mult`); none touches players, villagers, tamed animals or shadows (`foes`) |
| `MonarchDomain` | What the powers leave running (server only, never saved; it ends when the monarch leaves, falls or changes worlds): domains, crown forms, the dread marks and the eclipse; read by `MonarchHooks.onIncomingDamage` (might, guard, dread, the crown's weight against monarchs) and `ShadowService.auraBonus` |
| Sync | `MonarchSyncPayload` (the player's crown, whether they were called, the level the throne asks for), `MonarchFxPayload` (domains and crown forms near the player, authorities, clashes, dread, the throne's question and the coronation), `MonarchActionPayload` (a crown key, a crown picked, the coronation skipped) |
| `client.monarch` | `ThroneScreen` (the four crowns side by side with their domains and authorities, the class's leaning marked; 1-4 or a click picks, Enter claims, Esc looks away and the System key brings it back), `CoronationOverlay` (light gathering from the edges, the braziers flaring, the crown's emblem bursting in with "MONARCH OF ..." and the screen shaking, then the card with the four powers; the hide key skips), `CrownBar` (the crown keys over the skill bar with cooldowns and what is left of a domain or the crown form), `MonarchHud` (an authority's name across everyone's screen, a clash, dread's red edges and heartbeat, the crown's light at the edges in crown form and a tint inside a domain, how to answer the throne if the hunter looked away; while an authority's or a clash's banner is up, new pop-ups, quest moments and titles wait (`bannerUp`), as for a world boss's banner; Eclipse of Kings puts the sun out for everyone near while the dark strikes, on its own layer `beyondsolo:monarch_eclipse` registered below every other, so the world darkens and the HUD does not), `ThroneArbiterRenderer`; `WorldBossWorldFx` draws hunter monarchs' domains on the land, their crown of light, the circle under their feet and the aura burning off them (its light textures share one buffer: asking for one texture's buffer closes the last one's, so a buffer is asked for again right before each use, never kept across another `buf` call, or the game crashes with "Not building!") |

Config: `beyondsolo-monarch.toml` (`monarch.minLevel` 80, `monarch.retryMinutes` 20, `monarch.choiceSeconds` 90,
`monarch.healthBonus` 0.25, `monarch.damageBonus` 0.12, `monarch.manaBonus` 150, `monarch.versusMonarch` 1.2,
`monarch.domainRadius` 16); every number, the powers' included, is a first guess for the final balancing pass: a
monarch must feel far above every other hunter and still be something another monarch can beat (no immunity, no
second life, no absurd regeneration). Titles: Monarch of each crown, The Unblemished Crown (secret), Crown Against
Crown and Domain Unbowed (secret). Art: `tools/gen_monarch_assets.py` (the crown emblems, the power icons, the Arbiter
Unchained's skin and glow mask). Operators: `/solo monarch crown/uncrown/call/reset/jump/power` (`SoloMonarch`) and
`/solo trial throne`; `/solo dungeon leave` ends a throne trial, `/solo info` shows the crown and its record.

Testing: `MonarchGameTests`; in the headless client the whole road runs from real commands (`/solo trial throne`,
`/solo monarch jump 2|arbiter|throne`, `/solo monarch crown <crown>` and `/solo monarch power <power>`).

## The rulers of light

The monarchs' enemies since before the gates, and the other side of a hunter's choice: three rulers of light that come
down through a rift in the sky to make war on the monarchs, and to pass sentence on the hunters who took a crown. Code
in `ruler/` (server) and `client/ruler/` (the herald's renderer); the fights, bars, banners and the light drawn on the
land are the world bosses' (`worldboss/`, `client/worldboss/`), since a ruler is a great threat of its own tier
(`WorldBossKind.Tier.RULER`; `sovereign()` is a monarch or a ruler). Text in `assets/beyondsolo_rulers/lang`. Names
are the mod's own.

| Piece | What it does |
|---|---|
| The rulers | `RulerBoss` on `ColossalBoss`, as strong as a monarch and as beatable (no immunity, no second life, no healing; phase lines 60% and 25%; one blow can't skip a phase). **Aurenthe, Ruler of the Verdict** (`VerdictRuler`: a knight of white gold with a greatsword of light and steel-feathered wings; the Herald, the Scales, the Final Verdict: a cross of light cut through the land, then turned). **Caelistor, Ruler of a Thousand Wings** (`WingsRuler`: a lancer on four wings who fights from the sky; the Spear, the Gale, A Thousand Wings: volleys and the shadows of her wings swept across the land, then her dive). **Ithrael, Ruler of the Unseen Hand** (`HandRuler`: a masked one in robes whose great hands float at his side; the Hand, the Weight, Unseen Dominion: the land pressed flat ring after ring, outward). Each class comment lists its moves; every move is drawn on the ground before it lands, like a monarch's |
| Its sanctum | `RulerBoss.SANCTUM` (26) blocks round a landed ruler. Its foes inside are laid bare (they glow), their shadows and pets burn (2% of their health a second), a monarch's soldiers burn (1%), and each ruler's own judgement falls on one of them every few seconds (a pillar of light, a feather, the air taking hold). Whoever it does not stand against is sheltered there: a monarch's domain and presence don't reach them (`Rulers.shelters`, read by `MonarchBoss`). A crowned hunter's domain holds the sanctum back from whoever it shelters |
| Friend and foe | Its foes: monarchs and their soldiers (`WorldBosses.ARMY_TAG`), hunters who took a crown, the hunter it came to judge, anyone who struck it or one of its heralds, and what any of them own (shadows, pets). Every other hunter it leaves be: its blows pass through them (`canHit`), it never targets them, and their area skills, shadows and hired hunters pass it by (`Rulers.spares`, read by `SkillActions`, `ShadowEntity`, `GuildHunterEntity`). Monarchs and their armies count the light as their foe too (`MonarchBoss.foe`) |
| Choosing a side | `RulerHooks.onIncomingDamage`: a hunter the light doesn't stand against has their first blow on a ruler or a herald stayed, with a warning ("The light stayed your blow"); a second blow within `WARN_TICKS` (10 s) makes them its foe, and that one lands. The warnings are kept by each ruler. A crowned hunter's blows on a ruler count `monarch.versusMonarch` times, as on a monarch |
| `Rulers` | Why one comes, in its own slot beside the great threat abroad (`WorldBossData.ruler()`, one at a time): **the Eternal War** (`WAR`: a monarch has landed and the light answers it by `rulers.warChance`, unless it is a monarch of the System's story, which answers it itself; it comes down 18 to 30 blocks from the monarch and goes for it, and is called off if the monarch is gone before it lands), **a Sentence of Light** (`SENTENCE`: once a crowned hunter is in the overworld, the first comes after `rulers.firstSentenceMinutes` and then every `rulers.sentenceMinutes`, give or take a quarter, for one of the crowned; it comes down 24 to 40 blocks from them and hunts them, and the hunter's death carries the sentence out) and **called** (`CALLED`, commands). The rift opens `rulers.descentSeconds` before it lands and everyone hears why, where and when; a ruler with no hunter near for `rulers.abandonMinutes` rises back into the sky, and so does one whose work is done (its monarch fell, its hunter died) once no foe stands against it. Its status goes out every 2 s like a world boss's |
| Quests | The judged hunter: **Overturn the Sentence** (`beyondsolo:ruler/sentence`, surprise, fell the ruler: 9,000 XP, 3 stat points). Every awakened hunter without a crown during a war: **The Eternal War** (`beyondsolo:ruler/war`, bring the monarch down while the ruler stands: 4,000 XP, 2 stat points) |
| Rewards | A ruler that falls pays each hunter who took at least half a percent of its health off it, like a monarch's kill and by their share (`Rulers.slain`): System XP, gold, greater mana stones, a supreme healing elixir, S-rank weapons, an Elixir of Rebirth, and 2 to 4 Motes of Radiance; the judged hunter felling it overturns the sentence. A monarch falling while a ruler is at war with it (`Rulers.monarchFell`): every uncrowned hunter who had a share of the monarch's kill and never raised a hand against the light is blessed by the ruler (the Blessing of Light, a mote, the war in their record) |
| The Blessing of Light | `RulerModule.BLESSING` (`rulers.blessingMinutes`, 20): a blessed hunter's blows on monarchs and their soldiers land `rulers.blessingMight` (15%) harder, and a monarch's presence doesn't weigh on them. Given by a ruler's hand (the banner, the record) or by a **Mote of Radiance** (`MoteItem`, used); the light never answers a crowned hunter |
| Heralds | `HeraldEntity`: lesser beings of light in white plate with a spear and a crown of crystal, called round a ruler as it lands (4) and as it angers (3 at its second phase) by `RulerHost`, each in a pillar of light. They fight their ruler's foes and only them, and fade when it falls or leaves, when they are left without it, or when their land loads after its event ended (`Rulers.onJoin`) |
| `RulerRecord` | The hunter's record with the light (the `beyondsolo:rulers` attachment, kept through death): rulers felled by kind and how often they did the most, wars won at a ruler's side, sentences passed and overturned, heralds cut down, blessings received. `/solo info` and `/solo ruler record` show it |
| `RulerJournal` | Titles: Rulerbreaker (a share in a ruler's fall, mythic), Eclipse (the most damage to a ruler that fell, mythic, secret), Ally of the Dawn (a monarch brought down at a ruler's side, legendary), Herald's Bane (25 heralds, epic), Unsentenced (felled the ruler come to judge you, mythic, secret). The rulers' journal pages come with the great threats' |
| Client | `WorldBossClient` keeps two slots (the great threat abroad and the ruler) and `WorldBossHud` draws two bars, the ruler's under the other with whom it fights under it ("at war with Thyrax", or "it has come for you" for the hunter it judges), and the light's banners: the rift opening, the Eternal War (the ruler's name against the monarch's), the Sentence (yours, or another's), Blessed, the ruler slain. `WorldBossWorldFx` draws a ruler's rift as a still tear of white light in the sky (a cool white heart and gold lips, broken rings turning round it, rays, and a shaft down to the land while it comes down) and its sanctum as a ring of gold with six seals of broken rings on the land; `ColossalBossRenderer` burns a ruler in flame of light with a broken halo behind its head, the Verdict's wings of light, its seal underfoot in its last phase, and glints of light gathering as a pillar of light takes it (see "Series look"). Art: `tools/gen_ruler_assets.py` (the three rulers and the herald, their animations, the mote and the blessing's icon) |

Config: `beyondsolo-rulers.toml` (`rulers.enabled` true, `rulers.warChance` 0.4, `rulers.descentSeconds` 30,
`rulers.sentences` true, `rulers.firstSentenceMinutes` 30, `rulers.sentenceMinutes` 120, `rulers.abandonMinutes` 8,
`rulers.blessingMinutes` 20, `rulers.blessingMight` 0.15); every number is a first guess for the final balancing pass.
Operators: `/solo ruler call [ruler]`, `war [ruler]`, `sentence [ruler] on [player]`, `land`, `stop`, `status`,
`phase <1-3>`, `move <move>` (aimed at the sender, whose side it still respects), `kill`, `next <minutes>`,
`bless [players]`, `mote [count]`, `record [player]` (`SoloRulers`).

Testing: `RulerGameTests` (a ruler can die, the foes it chooses, a first blow stayed and a second one landing, the
sanctum's shelter, a sentence overturned, a war's blessing, the mote, the commands); `WorldBossGameTests` covers every
ruler's assets and lines with the other great threats. In the headless client, `/solo ruler call <ruler>` then
`/solo ruler land` brings one down in front of you.

## The light: a hunter's road to ruler

The other side of a hunter's choice: instead of a crown, the light. It isn't picked at the job change: it is found.
Every awakening temple hides a chapel where the Envoy of Dawn waits, and a hunter who finds it before the Arbiter takes
them can swear to the light and wake in it. Sworn, they can never take a crown; at level 80 the light calls them to
the Investiture, and the mantle they take up makes them a ruler of light, a monarch's equal and counterpart. Code in
`light/` (server), `client/light/` (client) and `skill/RulerPowers` (the powers); text in
`assets/beyondsolo_light/lang`. Names are the mod's own.

| Piece | What it does |
|---|---|
| `Chapel` | The secret way in. A temple opened since the light came (`GateInstance.chapel`, set when `light.chapel` is on) is planned with a second room off its antechamber (`DungeonLayout.temple(seed, chapel)`), walled off like a hidden chamber (`HiddenKind.CHAPEL`, never rolled anywhere else; `GateDepths`). It is meant to be found: golden light seeps from the wall, a hum comes from behind it when a hunter without the System comes near, a gold sign by it says the light waits behind the stone (`DepthsBuild.chapelSign`, `trial.beyondsolo.chapel_sign.*`), and three knocks bring it down with a bell each (`HiddenRooms`; no HIDDEN DUNGEON moment, no XP). Inside, a chapel bright as day after the temple's dark (`DepthsBuild.dressChapel`): white and gold, pillars of light, the altar at the far end (`DepthsBuild.chapelAltar`) and the Envoy of Dawn over it (`EnvoyEntity`, put back whenever the room loads without it) |
| `Covenant` | Speaking to the Envoy opens `CovenantScreen`: its words, then the answer. **Swear to the light**: the Envoy lays its hand on them (`bless`) and `AwakeningService.beginLight` plays the awakening on the same clock, in the light (`AwakeningPayload.AWAKEN` with 2: no Arbiter, no fall), and `bind` makes them sworn (`LightAPI.awoken`: Judgement, the title Dawnsworn). **Turn away**: the Envoy lets them go; the Arbiter waits, and the way back stays open while the temple stands. A party: each member answers for themselves |
| `LightData` | The hunter's road in the light (the `beyondsolo:light` attachment, kept through death): sworn (and when), turned away, called, tries at the vigil and when they may try again, the mantle (and when they took it up), monarchs slain, foes judged, authorities used |
| `LightAPI` | The one entry point: `sworn`, `invested`, `mantle`, `awoken`/`swear`/`unswear`, `eligible` (sworn, job changed, level `light.minLevel`, no mantle) and `callIfReady`/`call` (the light's notice, the Envoy's feather, the quest `beyondsolo:light/investiture`), `invest` (the mantle: its sanctum and authority, the strength, the mantle's title; takes a crown back), `divest`, `reset`, the strength (`applyBonuses`, transient modifiers put back with the skill passives: `light.healthBonus` 25% max health, `light.damageBonus` 12% attack damage, `light.manaBonus` 150 mana, a third of it while only sworn), and the record. Calls are checked on login, level up and job change |
| `Vigil` | The Investiture. Raising the Envoy's feather (`EnvoyFeatherItem`, never used up) in the overworld begins it where the hunter stands: a ring of light (radius 11) and the Envoy coming down over it. The light gathers (5 s), then three waves of the gates' creatures of the hunter's rank rise round the ring, each bigger than the last, the last led by a champion; whoever leaves the ring for 5 s, logs out or falls has failed and may keep it again after `light.retryMinutes`. Then the Envoy lays the four mantles before them (`MantleScreen`; after `light.choiceSeconds` the light takes the one their class leans to, `Mantle.leaning`) and invests them. Other hunters may fight beside them. Nothing is saved: a vigil cut short by a restart is simply kept again |
| `Mantle` | The light's answer to a crown, each the way of one of the rulers who came down from the sky (and the Envoy's own): **Verdict** (judgement: everyone inside the sanctum stands judged; Blade of Heaven), **Wings** (the open sky: foes slowed, fliers most, allies faster and falling like feathers; Choir of Wings), **Unseen Hand** (what no one can lift: foes pressed down and pinned; Hand of Heaven), **Dawn** (the light that heals: foes catch fire, allies mend twice as fast and shed harmful effects; Daybreak). Each colours the ruler's light and has its title (Ruler of the Verdict, of Wings, of the Unseen Hand, of Dawn, mythic) |
| `RulerPowers` | Four powers on the crown keys (the two roads never meet, so a ruler uses the keys a monarch would), never on the skill bar, never from level, tome or rune stone; `SkillAPI.cast` refuses them to anyone not sworn. They level like any skill (to 10). **Sanctum** (Investiture; 60 mana, 90 s to 60 s): a dome of light (`light.sanctumRadius` 14) that goes where the ruler goes for 14 + level s; they and every other hunter inside (and their shadows) hit 12 + level % harder, take 12% less and mend 1% a second, foes burn each second and suffer the mantle's law, and the domain of a monarch from beyond the gates doesn't touch whoever it shelters (`LightSanctum.shelters`, a clash). **Judgement** (as they wake; 40 mana, 45 s to 30 s): a sigil over every foe within 12 + level/2 blocks and a lance of light onto each; the judged are stunned and take 15 + level % more from every blow for 8 s (bosses half). **Seraph form** (`light.seraphLevel` 45; 80 mana, 3 min to 2 min): wings of light and a halo for 18 + level s, blows 20 + 2 x level % heavier, 15 + level % less taken, faster, falling like a feather; never untouchable. **Authority** (Investiture; 100 mana, 2 min to 80 s): Blade of Heaven (a sword of light the size of a tower comes down where they look: a blast of 8 blocks and burning light where it stands for 5 s), Choir of Wings (wings of light at their back and 12 + level feathers like spears on foes within 20 blocks over 6 s), Hand of Heaven (a hand of light comes down out of the sky, closes on everything within 7 blocks, holds them 1.5 s and slams them down), Daybreak (a sun rises over the fight for 8 s: foes within 16 blocks burn, allies mend 3% a second, and it flares as it sets). Every hit goes through the hunter's power like a skill's; none touches players, villagers, tamed animals or shadows |
| `LightSanctum` | What the powers leave running (server only, never saved; it ends when the ruler leaves, falls or changes worlds): sanctums, seraph forms and the judged; read by `LightHooks` (might, guard, the judged's extra share, the light's weight against monarchs) and `ShadowService.auraBonus` |
| Against the monarchs | A hunter sworn to the light hits monarchs from beyond the gates `light.versusMonarch` (1.2) times harder; a ruler of light is never weighed down by a monarch's presence (`MonarchBoss`). A share in a monarch's kill while sworn earns Dawn Over Crowns (mythic); a sanctum holding a monarch's domain back earns Unbroken Light (mythic, secret). The throne never calls one sworn to the light (`MonarchAPI.eligible`) and the Sigil of Dominion stays dark for them; a crown (a command) sets them free of the light, and a mantle takes a crown back |
| `LightConstruct` | The light's greater shapes made real for a moment: Judgement's lances, the Blade of Heaven, the Hand of Heaven, Daybreak's sun, the choir and its feathers. Only something to see (never touches, never saves); drawn from the world's clock so they fall the same for a hunter who only now came near. `LightConstructRenderer`: a body of light you can half see through, white at the heart, tinted with the mantle's colour, its edges burning (glow mask) and a bloom round it |
| Sync | `LightSyncPayload` (the player's road: sworn, called, the mantle, the level the Investiture asks for), `LightFxPayload` (sanctums and seraph forms near the player, authorities, judgement, clashes, the chapel's hum, the call, the vigil and the Investiture), `LightActionPayload` (a crown key, the covenant's answer, a mantle picked) |
| `client.light` | `CovenantScreen`, `MantleScreen` (the four mantles side by side with their sanctum's law and authority, the class's leaning marked; 1-4 or a click picks, Enter takes it up, Esc looks away and the System key brings it back), `LightBar` (the ruler's keys over the skill bar with cooldowns and what is left of a sanctum or seraph form; a key not given yet is locked), `LightHud` (an authority's name across the screen of everyone near, a clash, judgement's gold edges and bell, the ruler's light at the edges in seraph form and a warm tint inside any sanctum, the chapel's words, the call, the vigil's waves and ring, the Investiture's title), `SeraphLayer` (wings and halo on a ruler in seraph form, for everyone who can see them), `EnvoyRenderer`; `WorldBossWorldFx.lightRulers` draws sanctums on the land (a ring of gold, seals and a dome) |

Config: `beyondsolo-light.toml` (`light.chapel` true, `light.judgementLevel` 1, `light.seraphLevel` 45,
`light.minLevel` 80, `light.retryMinutes` 20, `light.choiceSeconds` 90, `light.healthBonus` 0.25,
`light.damageBonus` 0.12, `light.manaBonus` 150, `light.versusMonarch` 1.2, `light.sanctumRadius` 14); every number,
the powers' included, is a first guess for the final balancing pass: a ruler of light stands where a monarch stands,
far above every other hunter and still someone a monarch can beat. The main story follows the road: for a sworn hunter
chapter IV is the Investiture and is done when they take up a mantle (`MainStory`). Art: `tools/gen_light_assets.py`
(the Envoy of Dawn, the seraph's wings and halo, the six shapes of light, their animations, skins and glow masks, the
power and mantle icons, the feather). Operators: `/solo light swear/unswear/invest <mantle>/divest/call/vigil
[next|stop]/chapel [wall]/power <power>/reset` (`SoloLight`); `/solo info` shows the road and the record.

Testing: `LightGameTests`; in the headless client, `/system trial call` and `/system trial enter` take a new player into a
temple, `/solo light chapel wall` stands them before the chapel's wall and `/solo light chapel` before the Envoy, `/solo light swear` and `/solo light invest <mantle>` skip ahead, `/solo light vigil` (then `next`)
walks the Investiture and `/solo light power <power>` fires a power.

## The Hunter Association and the gate broker

The hunters' own institution, as in the series: an office at every Evaluation Zone that pays bounties for closing
gates, sells the clearing rights to a gate, examines hunters whose mana has outgrown their license, and keeps a merit
record that places them on a national board. Beside it hangs about a gate broker, who sells what the association
doesn't say out loud: where a gate is, and what waits inside one. Code in `association/` (server) and
`client/association/` (client); text in `assets/beyondsolo_association/lang`. Names are the mod's own.

| Piece | What it does |
|---|---|
| `AssociationPosts` | Every Evaluation Zone has a post: an officer (`beyondsolo:association_officer`) inside the zone two blocks from the crystal on its open side, and a gate broker (`beyondsolo:gate_broker`) on open ground six to ten blocks out. The crystal's server ticker looks every 100 ticks (each crystal at its own moment) while someone is within 48 blocks; a post found empty on two looks in a row (entities load late) gets its person, so villages from worlds made before the association get theirs too, and a post whose person is gone gets a new one. Overworld only, never on a game test server (its plots are Evaluation Zones, so the tests place people themselves); `association.posts` false stops new ones coming |
| `AssociationNpc` | The officer and the broker (one class, the role from the entity type): player-shaped, a name of their own ("Officer Halden", "Silas Venn, gate broker") and a look (3 officers, 2 brokers), they stand at their post, turn to whoever comes near and can't be hurt or pushed (only damage that bypasses invulnerability, such as `/kill`). Two at one post (chunks loading late): the one with the higher UUID leaves. One blown more than 14 blocks off is put back. Right-click opens the office or the broker's window |
| `Association` | The office. **Contracts:** the board lists the gates within `association.watchRadius` (512) of the office that are open, in the overworld, not a trial, a key's or a raid's, and not claimed (`contractable`), broken gates first, then gates coming apart, then the nearest, `association.boardSize` (6) at most. A contract's bounty is `association.contractGold` (0.6) times a clear's gold, x`unstableBonus` (1.5) for a gate whose collapse timer shows, x`breakBonus` (2.5) for one that has broken, and the standing's bounty bonus; merit by `association.contractMerit`. A contract is a quest (`beyondsolo:association/contract`) with a waypoint to the gate; it is done when its holder is in the cave as the boss falls or has a share in putting the break down, and fails once the gate closes without them (`check`, once a second). Up to `association.maxContracts` (2) at once. **Permits:** for `association.permitFee` (0.4) times a clear's gold a gate of the rank asked for (up to `association.permitReach` (1) above the hunter's) tears open near them, its clearing rights theirs: only they and their party get in (`refusal`, read by `GateService` at the gate), `association.permitsPerDay` (2) a day; the permit's quest pays contract merit. **The rank exam:** when the hunter's true rank (`Reevaluation.trueRank`) is above their license, they may sit the exam for the next rank for `association.examFee` (1.0) times its clear's gold: a gate of that rank opens for them alone, to be cleared within `association.examMinutes` (25). Passing certifies the rank at once (`SystemAPI.setRank`, no crystal milestone needed) with `association.examMerit`; dying, the clock or the gate closing fails it, and the next waits `association.examRetryDays` (1). **Merit:** every clear (`association.clearMerit`, contract or not), contract, break put down (`association.breakMerit`) and exam passed; the standing it reaches is said once with its perk. The **national merit board** sets the server's hunters beside the world's rival hunters (`chronicle/ranking/Rivals`, their merit their clears at their rank's rate) |
| `Standing` | Registered, Recognized (50 merit: a contract more at once), Trusted (200: bounties x1.1), Distinguished (600: a permit more a day), Elite (1,500: bounties x1.2, the broker's prices x0.8), National (4,000: bounties x1.3 and Pride of the Association); thresholds `association.standings` |
| `Broker` | **Whereabouts:** for `broker.tipPrice` (1.25) times a clear's gold the broker names the nearest gate of the rank asked for, within `broker.tipRange` (1,500) of them, that the hunter has not heard of (`GateSpawner.knows`; the sale tells them, `GateSpawner.tell`). If they know of none, they swear one is about to tear open, and it does near the hunter. The hunter gets a lead (`beyondsolo:association/tip`) with a waypoint, done within 12 blocks of the gate. `broker.tipsPerDay` (4) a day. **Scouting:** for `broker.scoutPrice` (0.35) times a clear's gold, a report on an open gate the hunter knows of (seen, told, or a quest on it) within `broker.scoutRange` (400): its dungeon and layout, its boss (and whether ascended), its element and weather, the rumour of a hidden chamber, how many caverns deep it runs and how long it holds (the break clock). Kept in the hunter's file (48 at most) |
| `AssociationRecord` | The hunter's file (`beyondsolo:association`, kept through death): merit and the standing reached, contracts done and failed, clears, breaks put down, exams passed and failed, the day the next exam may be taken, today's permits and tips, bounties earned, the gates scouted. `/solo association record` shows it |
| `AssociationData` | The gates the association has claimed, saved with the overworld (`beyondsolo_association`): an exam gate (only its examinee goes in) or a permit's gate (its holder and their party). A claim ends when the gate is cleared, a break is put down, the exam fails, the permit is given back, the hunter is wiped, or (once a minute) the gate is no longer open or has broken out |
| Titles | Association Certified (the first exam passed, rare), Bounty Hunter (25 contracts or permits' gates, epic), Pride of the Association (National standing, legendary); their perks are first guesses for the balancing pass |
| Sync | `AssociationSyncPayload` (which window, and everything it shows as one tag: the license, the board, held contracts, permits, the exam's reading, the standing and the national board; or the broker's leads, prices and scouted gates; with the answer to the last button) on every open and button; `AssociationActionPayload` (a button). The server keeps the desk the hunter talks at (`Session`: the officer or broker, where they stand, which window) and answers only while the hunter is within 10 blocks of it, and only with that desk's buttons (no broker deals at the office's window or the other way round) |
| `client.association` | `AssociationWindow` (what both windows share: the frame opening out of a line of light, the one at the desk on the left with their portrait over a turning ring, buttons, the last answer; drawn in one shape batch), `AssociationScreen` (the office: CONTRACTS, PERMITS, EXAM with the crystal's reading against the license, STANDING with the ladder of standings and the national board), `BrokerScreen` (WHEREABOUTS with a tile per rank, SCOUTING with the gates in reach and the report), `AssociationMarkers` (the officer's seal and the broker's coins over their heads, their name, role and what they do once the hunter is close), `AssociationNpcRenderer` (a player model in their own skins). Quest waypoints for contracts, breaks, permits, exams and leads have their own icons and colours (`NpcMarkers`, via the quest's `wpKind`) |

Config: `beyondsolo-association.toml` (`association.*`, `broker.*`); every number is a first guess for the final
balancing pass. Art: `tools/gen_association_assets.py` (the officers' and brokers' skins; `--preview` renders them
side by side into `build/preview/association.png`). Operators: `/solo association open [tab]`, `broker`, `post`,
`merit add|set <merit> [players]`, `exam start|pass|fail [player]`, `contract [player]`, `record [player]`,
`reset [players]` (`SoloAssociation`); `/solo wipe` clears the hunter's file and their claims.

Testing: `AssociationGameTests` (a contract paid on a clear and failed when its gate closes without the hunter, a
permit's gate kept for its holder, the exam certifying the crystal's rank and failing on death, merit raising the
standing and its perks, the broker's whereabouts and scouting, the officer keeping the desk and its buttons, one
officer at a post, the commands). In the headless client, `/solo association post` puts both people at the nearest
crystal, and `/solo association open <tab>` and `/solo association broker` open the windows.

## Random boxes and mana poisoning

Two things the series' hunters live with. **Random boxes**, as the System of the story handed them out: a box that
opens into one thing nobody chose, a blessed one that lets the hunter choose, and a cursed one that gives more and
takes something back. **Mana poisoning**: the mana inside high-rank gates is thick enough to sicken the hunters who
breathe it, the further their rank is below the gate's the faster. Code in `reward/` and `manapoison/` (server),
`client/reward/` and `client/manapoison/` (client); text in `assets/beyondsolo_boxes/lang` and
`assets/beyondsolo_mana/lang`.

| Piece | What it does |
|---|---|
| `RewardBoxItem` | `beyondsolo:random_box`, `blessed_random_box`, `cursed_random_box` (`BoxKind`): the rank (E..S) sits in the stack's custom data ("B-Rank Random Box"), a blessed box also keeps the seed its offers are rolled from. Using one asks `RewardBoxes.open`; only a hunter with the System can open one, one a second |
| `BoxRolls` | What boxes hold, by the box's rank and the opener's class (`BoxReward`: items, gold, stat points and a `Category`: gold, remedies, a weapon or a piece of armor for the class, monster cores and stones, a skill tome, a sealed rune, a dungeon key, stat points). **Plain:** one thing. **Blessed:** three offers of three different kinds rolled from the box's seed (the same three every time it is opened until one is taken), gear rolled as if from a gate a rank up. **Cursed:** one thing, never remedies or cores, gear a rank up, two and a half times the gold, two tomes or keys, a stat point more. Weights and amounts are first guesses |
| `RewardBoxes` | Opening: a plain box is used up and gives its thing; a blessed one sends its three to the screen and gives the one picked (`pick`, refused unless the box in that hand still has the seed shown; the boxes left in the stack get a new seed); a cursed one rolls its thing and its curse (`BoxCurse`): **Wardens** (3 + rank/2 of the rank's creatures climb out around the hunter 4.5 s later, as the moment on screen fades; the box's things held in an emergency quest `beyondsolo:box/wardens` and paid when the last one is down; fleeing or the 2 minutes running out and they go back with it; no room for them and it becomes Frailty), **Frailty** (Hex of Frailty, `beyondsolo:hex_of_frailty`: 15% of max health for 10 minutes; a purifying draught lifts it), **Backlash** (45 mana poisoning wherever the hunter stands), **Greed** (10% of their gold). **Where they come from:** the daily training pays a plain box of the hunter's rank (`daily`), every secret quest a blessed one (`secret`), and a reward cache adds at most one (`fromCache`): a cursed box from a red gate's (60%), a blessed from a hidden chamber's (30%, else a plain one 50%) or an ascended boss's (35%, else plain 25%), a plain one from any boss's (25%) |
| `ManaPoisoning` | The poisoning, 0..100 (`beyondsolo:mana_poisoning` attachment, cleared at death). Every half second for each hunter in survival: inside the built, open cave of an A or S gate (`denseGate`: `mana.minGateRank`; never a trial or the temple) it rises at `mana.matchedRate` (0.05/s) for a hunter at the gate's rank, `aboveRate` (0.25) one rank below, `farAboveRate` (0.6) further below (by `Reevaluation.trueRank`), not at all above it; x`sGateMultiplier` (1.3) in an S gate, less `intPerPercent` (1% for every 4 INT above 10, up to `intCap` 40%); it stops once the boss is down. Elsewhere it fades at `mana.decay` (1/s). **30 Mana Sickness:** blows lose 10%. **60 Mana Poisoning:** 20%, 10% speed, 2% of max health every 3 s. **90 Mana Collapse:** 30%, 20% speed, 4%. **100:** the mana bursts out (20% of max health, a flash, a heartbeat) and it falls back to 75. Its damage is its own (`beyondsolo:mana_poisoning`: through armour, no knockback, never touches the hit cooldown). A pop-up as each stage is reached, one on entering a thick gate, one when it is gone |
| `ManaCrystals`, `PurifyingCrystalBlock` | A step of `DungeonBuilder` after the depths grows purifying crystals into A (2) and S (3) caves, each in a cavern, side room or passage of its own (side rooms first), never the entrance, the boss's hall, the hidden chamber or a trap room; where follows from the seed. A crystal can't be broken (light 12, pale motes) and draws the poisoning out of a hunter within 4 blocks at `crystalPurify` (6/s), even while the mana around them is thick: the place to rest in the middle of a long gate. Motes stream from the hunter into it (the hunter's own start a step along the way, clear of their camera) |
| Purifying draught | `beyondsolo:purifying_draught`: washes `mana.draught` (50) out, lifts Hex of Frailty and gives Clear Breath (`beyondsolo:clear_breath`, 2 min: the mana can't build up). From caches of A and S gates (half of them; two in a hidden chamber's), the shop (REMEDIES, 450 gold, B rank), the System's recipes (a bottle, a B core, a mana stone) and from A up in boxes' remedies |
| Sync | `RewardBoxPayload` (one tag: an opening, with the kind, rank, what it gave, the curse and what the curse did; or a blessed box's offers, its hand and seed), `RewardBoxPickPayload` (the offer taken), `ManaPoisonPayload` (the level in tenths and flags: thick mana, purifying, Clear Breath, burst), sent only when they change |
| `client.reward` | `BoxOverlay`: every opening as a moment of its own (the box shaking, the lid flying open in rings and rays, what it gave drawn big with its name typed out, its kind and rank, the rest of it below; a cursed box's curse in a red panel after it), skippable with the hide key and queued behind other moments (quest and title moments wait for it); `BlessedBoxScreen`: three cards with the offers (hover for the item's tooltip), 1-3 or a click takes one, Esc keeps the box |
| `client.manapoison` | The poisoning meter in the vitals (`VitalsHud`, under fatigue: up while poisoned or in thick mana) and the haze (`ManaPoisonClient`: a violet edge that thickens with each stage and beats at the worst, a flash when it bursts, a slow teal glow at the edges while a crystal draws it out) |

Config: `beyondsolo-boxes.toml` (`boxes.*`) and `beyondsolo-mana.toml` (`mana.*`); every number is a first guess for
the final balancing pass. Art: `tools/gen_reward_assets.py` (the three boxes as 3D models with lit runes, a crest and
chains, the crystal and its pool, the draught, the two effect icons). Operators: `/solo box give plain|blessed|cursed
[rank] [count] [players]`, `/solo box curse wardens|frailty|backlash|greed [rank] [players]` (a cursed box opened on
the spot), `/solo mana set|add <amount> [players]`, `/solo mana clear [players]`, `/solo mana crystal`, `/solo mana
crystals [player]` (the gate's crystals, each a click from a teleport beside it), `/solo mana info [player]`
(`SoloBoxes`); `/solo wipe` clears the poisoning.

Testing: `RewardBoxGameTests` (a plain box opening and used up, only for the awakened, a blessed box's three offers
kept until one is taken and that one given, every roll's promises, the wardens holding the box until they fall or
taking it away, frailty until purified, greed and backlash, caches by where they stand, the daily's box, the
commands) and `ManaPoisonGameTests` (the stages, the rate by rank in an A gate and thinning once the boss is down,
the crystals growing and drawing it out, the burst, weaker blows, the draught, the commands). In the headless client,
`/solo box give blessed` and using it opens the pick; `/solo mana set 95` shows the meter and the haze.

## The Long Sleep: the hunter's own story

The series' hunter fights for a reason close to home: a mother who has slept for years and whom no healer can wake. The
Long Sleep is that story for every hunter, told in scenes at her bedside. Code in `story/` (server) and
`client/story/` (client); text in `assets/beyondsolo_story/lang`.

| Piece | What it does |
|---|---|
| `Kin`, `KinRecord` | Home is the village whose crystal first evaluated the hunter (`EvaluationManager` calls `evaluatedAt`; hunters evaluated before the story came get the crystal they are near, from its ticker). Once a second while the hunter is within 64 blocks of home, her bed is checked: the nearest free bed head of the village within `story.bedReach` (48) of the crystal (not slept in, not a hunter's respawn point, not another hunter's mother's; loaded chunks only), else a white bed placed on open ground 3-12 blocks from the crystal. Missing three checks in a row within 40 blocks of her bed, she is put in it. The record (`beyondsolo:kin` attachment, kept through death, cleared by `/solo wipe`) holds home, her bed, her entity's id (a stray copy of her removes itself), her look, the stage, the Citadel souls gathered, the day she woke and the day she last cooked |
| `KinEntity` | `beyondsolo:hunters_mother`, in the slim player shape: SLEEPING (in her bed with the vanilla sleeping pose, the bed marked slept in; her skin pale, her eyes shut), STIRRING (the moment she wakes), SITTING (up against the head of the bed, the day she woke), STANDING (beside the bed, looking about, from the next day). Never hurt, pushed, leashed or despawned; frees her bed when removed. Right-clicking her is `Kin.interact`: someone else's mother only says whose she is |
| `LongSleep` | The stages: UNTOLD, VISIT (level `story.visitLevel` 5: a quest to see her), VISITED, SEEKING (level `story.cureLevel` 25, in the overworld and not in the Citadel: the System finds the cure, a short scene, the cure quest), BREWED (the elixir in hand, the waking quest), AWAKE. Story quests (`QuestCategory.STORY`, `beyondsolo:story/long_sleep/visit|cure|waking`) can't be given up and come back if lost; visit and waking point at her bed (the "Mom" waypoint with a heart). **The cure:** `story.souls` (1,000) souls from creatures of the Crimson Citadel in the hunter's own climb or rush (`TowerRun`: 1 each, `keeperSouls` 10 for a boss, `lordSouls` 50 for the Crimson King), kept in the record so a lost quest loses none; Elder Root Dew from the cache of a hidden chamber whose cache is above `story.dewRank` (B, so the chamber of an A gate or a B gate's vault or lair: cache rank is gate rank + 1), one for every hunter within 32 blocks still looking for it; the Crimson King's Chalice from his fall at the top of the climb (`TowerRun.victory`). With all three the quest completes: the System takes the dew and the chalice and gives the Elixir of Waking (and the waking quest four seconds later). **Waking her** pays 2,000 XP, 3 stat points and the title Promise Kept (`promise_kept`, legendary, +6% vitality, +5% System XP). **After:** once a day she cooks; her stew (`home_cooked_stew`, 10 food) gives Home Comfort (`beyondsolo:home_comfort`, 20 min: regeneration and a little resistance); otherwise she has a word for them |
| `StoryScenes` | A scene is lines (speech with the speaker's name in their colour, the hunter's thoughts in italics, quiet narration), camera shots (`CutscenePayload` glides and orbits), blinks to black and beats that change the world, on its own clock. A held scene keeps the hunter still and from harm. The hide key skips: the world beats still happen (the visit counted, the elixir given, her waking) without the show. The visit (at her bedside, the hunter moved beside the pillow, two shots), the reveal (thoughts only), the waking (the elixir, light sinking into her, her eyes opening in the dark, a slow orbit of the bed while they talk) and the meal |
| Sync | `StoryLinePayload` (a line, a blink, a scene beginning (held or not) or ending), `StorySkipPayload` (the hide key) |
| `client.story` | `StoryOverlay`: the lines typed out at the bottom (the speaker's name letter-spaced, thoughts in pale blue italics, narration between two rules), letterbox bars when a held scene has no camera of its own, the skip hint, the blink; the System HUD steps aside during a scene. `KinMarkers`: a warm plate over her (a heart and "Mom" for her own hunter with what they can do when close, "Name's mother" for anyone else). `KinRenderer`: the slim player model with her three looks |

Config: `beyondsolo-story.toml` (`story.*`); every number is a first guess for the final balancing pass. Art:
`tools/gen_story_assets.py` (her three looks, each awake and asleep: fair with a greying bun in a rose dress and cream
shawl, tan with a grey-streaked tail in sage and oatmeal, deep with silver curls in lavender and wine; the dew, the
chalice, the elixir, the stew and the Home Comfort icon). Operators: `/solo story info [player]`, `step
untold|visit|visited|cure|brewed|awake [players]`, `souls <amount> [players]`, `give dew|chalice|elixir|stew
[players]`, `home [player]` (the nearest crystal becomes home, a new bed is chosen), `mother [player]` (her in her
bed now), `scene visit|waking|meal|reveal [player]`, `reset [players]` (`SoloStory`).

Testing: `LongSleepGameTests` (her bed the nearest free one and not another mother's, a bed made when the village has
none, her put in it near home and put back when gone, never hurt, the visit at level 5 completed by its scene, story
quests not given up, the cure found at 25 and given back when lost with no soul lost, the three ingredients only while
the cure is sought and the elixir brewed from them, the waking (sitting up that day, the title, up beside her bed the
next day), the meal once a day and Home Comfort, a stranger unable to wake her, and `/solo story`). In the headless
client: `/solo story home`, `/solo story mother`, then `/solo story scene visit` or `/solo story step brewed` and
right-click her.

## The System's story

The series is one hunter's road from the weakest of them to a king: a System nobody else has, a statue's smile in the
dark, a throne left empty for him, the truth of who built the System and why, and the father who walked into a gate
years before and came back as a vessel of the light, fighting beside his child against the monarchs. Here that road is
the System's own story, told in chapters (each a story quest that leads through what the game already holds) with
scenes between them. Names and lines are the game's own. Code in `story/` (server) and `client/story/` (client); text in
`assets/beyondsolo_story/lang`.

| Piece | What it does |
|---|---|
| `MainStory`, `MainRecord` | The chapters: **Prologue** (once the hunter's quest log has started and they have been calm for `PROLOGUE_CALM` 20 seconds: in the overworld or their residence, not in a gate's passage or the Citadel, nothing having hurt them for 10 seconds (kept by the story itself: the game's own last-hurt time is saved with the player while their tick count restarts at every login); ten years since the first gate, the father who never came back, the mother in the Long Sleep, the System's first words), **I The Player** (level 10 and 3 gates cleared), **II The Calling** (change their job), **III The Crimson Citadel** (fell Vharos, the Crimson King), **IV The Empty Throne** (wear a crown: Monarch Ascension), **V The Vessel of Light** (Khorvane, Monarch of Hunger, comes for the new crown), **VI The Last War** (Veyrith, then Thyrax), **Epilogue: Home** (back to their mother). Story quests (`beyondsolo:story/main/*`, `QuestCategory.STORY`) can't be given up and are given again if lost; their goals are read from the hunter's records once a second, so what was done before counts. A chapter done in the story's own time owes its scene (`owed`), told once the hunter is calm after a pause (40 ticks, the throne 60, a monarch's fall 110 so its death plays out first); a chapter up to the Citadel whose quest was done within 2 seconds of being given (a hunter far along already) closes quietly, untold; the throne's truth is always told. Rewards: XP and stat points per chapter (first guesses), the title Heir to the Empty Throne (`heir_to_the_empty_throne`, legendary) after the truth and Beyond the System (`beyond_the_system`, mythic) at the end. The record (`beyondsolo:main_story` attachment, kept through death, cleared by `/solo wipe`) holds the chapter, the scene owed and when, when the quest was given, the monarch fight, the next fight's time, the monarchs fallen (bits: Veyrith, Thyrax, Khorvane), whether their father was met and is gone, his look and the day the story ended. `story.main` (true) turns it off |
| `MainScenes` | The scenes (`StoryScenes`): the prologue (an orbit round the hunter, the sky, the System's first window), the statue's smile and the voice after the job change (lines only), Vharos' last words, **the truth** (held: a heartbeat, a blink, and Morvath, the first King of Night, standing a few steps in front of the hunter as a vision; over-the-shoulder, a slow push in and a two-shot orbit while he tells them he was the light's sword, was cast down for asking why, made the Arbiter and its System to find who would sit on his throne after him, and that the Arbiter meant to wear them; he gives the throne freely and warns that the light will come wearing a face they know; then he fades), each monarch's threat as it is called (the Last War's opening is filmed: two monarchs speak from the sky), their father's coming (the first time: who he almost is), **the talk** after the first monarch (held: him standing in front of them, two-shots and over-the-shoulder, where he has been and what the light made of him, their mother asleep or awake, a promise, and him rising into the light), **his last light** after the Last War (held: him on one knee, an orbit, his goodbye, rising, the System's line) and **home** (at their mother's bedside or beside her: awake she already knows, asleep they tell her anyway), then the System's last words. Every camera spot is checked against the walls and hills round the hunter (`front`, `clear`); under a roof the shots up at the sky become a close shot of the hunter's face (`opensUp`, `faceShot`). Skipping still moves the story on |
| `MainFights` | The monarchs: in Chapter V Khorvane (2 minutes after the truth), in VI Veyrith and then Thyrax 30 seconds after she falls. Each is a great threat like any other (`WorldBosses.begin`), called by the story 36-64 blocks from the hunter when they stand under the overworld's sky (its light at least 8 where they stand) and nothing else is abroad (not in a gate's passage, the Citadel or a scene), or the one already abroad within 320 blocks if it is the kind the chapter waits for. One that leaves unbeaten, or falls with the hunter not there (not hurt by them, not within 160 blocks), comes again 2 minutes later. The Last War's first comes `story.warDelayMinutes` (10) after Chapter V. The story answers its own monarchs when they land (`MonarchBoss.touchDown` asks `MainFights.monarchLanded` first, and the light's usual chance, `Rulers.monarchLanded`, is not taken): in Chapter V the light answers through their father alone; in the Last War each monarch that lands draws a ruler down to make war on it (`Rulers.war`). **Their father** comes down beside the hunter once the monarch has landed within 160 blocks, 10 seconds after (`ARRIVE_AFTER`: its domain's banner and the landing's run 9.2 seconds, and his words wait for them; a ruler's war banner first adds 6.5 more, `RULER_BANNER`), stays between the Last War's monarchs and for the chapter's closing scene, and goes when the story no longer wants him. On the client a world boss banner drawn low on the screen (a landing, a domain) also waits while a story scene is up (`WorldBossClient.banner`), so the two never share the bottom of the screen |
| `FatherEntity` | `beyondsolo:hunters_father`, a hired hunter's body (`GuildHunterEntity`) in a long coat with a seam of light at the waist, cuffs and hem, three looks (the mother's look if she has one, else random). He fights with his fists: every blow a burst of gold light, every fourth a wave of it through the foes near the one he struck. As strong as his child's level makes a hunter, times 3 health and 1.5 power; he takes half damage, mends 3% a second after 3 quiet seconds, and the light holds him up at 30% (he staggers but never falls). States: FIGHTING, STILL (to talk), KNEELING, RISING (into the sky in a pillar of light). One per hunter, never saved with the world; to anyone else he is "Name's father" |
| `VisionEntity` | `beyondsolo:story_vision`: Morvath as the truth shows him, a tall dark figure with violet light in his eyes and crown, never hurt, pushed or saved, fading out at the end |
| Sync | `StoryLinePayload.SYSTEM` (a line in one of the System's windows) beside the Long Sleep's speech, thoughts and narration |
| `client.story` | `StoryOverlay`: System lines in a glass window like the System's pop-ups (a flash and a sheen as it opens, the "!" badge and "SYSTEM" letter-spaced); `KinMarkers`: a plate of light over their father ("Dad", a spark) when near; `FatherRenderer` (the player model, the skin and a glowing layer for the seams of light; kneeling and rising poses), `VisionRenderer` (translucent, its glow layer additive, fading) |

Config: `beyondsolo-story.toml` (`story.main`, `story.warDelayMinutes`); every number is a first guess for the final
balancing pass. Art: `tools/gen_story_assets.py` (the father's three looks: light skin with brown hair and beard in a
charcoal coat, tan with black hair, stubble and a scar in olive, deep with a silver crop and grey beard in navy, each
with a glow image for the light in the seams; the King of Night: black armour and robe with violet light, translucent
edges). Operators: `/solo story main info [player]`, `chapter untold|player|arise|citadel|throne|father|last_war|home|done
[players]`, `scene prologue|statue|arise|vharos|truth|father|war|last_light|home|end [player]`, `fight [player]` (the
chapter's monarch now, or why not), `father [player]` (call him down to stay, or send him away), `reset [players]`
(`SoloStory`). Playing `scene father` or `last_light` calls him down for it when the story hasn't brought him.

Testing: `MainStoryGameTests` (the prologue waiting for a calm moment and for the quest log; a chapter done later owing
its scene and moving on after it; chapters done before closing quietly; the truth with the vision a few steps away,
unhurt, gone with the scene, and the title; Khorvane taken up as the chapter's fight, another threat abroad making the
story wait, their father coming once it lands, held up by the light, staying, the fall counting and the talk after;
the Last War with the light making war on Veyrith, Thyrax after her, his last light and home next; home by their
mother, the System's last words and the title; a lost quest given again beside the Long Sleep's; the operator
commands). Each fight test is in a batch of its own (one great threat at a time): Khorvane's checks that no ruler
comes to him and that their father waits out the landing's banners, the Last War's that he also waits out the ruler's. In the headless client:
`/solo story main chapter throne`, `/solo story main scene truth`, `/solo story main father`.

## The Halcyon raid: the island infestation

The series' great joint raid: an island the insects took years before, retaken by every strong hunter the Association
can call, with hunters of other guilds fighting beside them, down to the queen in the dark under it and the king she
leaves behind. Here it is Halcyon Isle, a resort island the swarm overran, called up every so often as a world event.
Code in `isle/` (server) and `client/isle/` (client); text in `assets/beyondsolo_isle/lang`.

| Piece | What it does |
|---|---|
| `IsleWorld` | The island's world, `beyondsolo:halcyon_isle` (open sea to every horizon, a fixed dusk, its own biome; `data/beyondsolo/dimension*`). One island, planned from the world's seed (`Layout`): a wandering coast (mean radius 84) round rolling hills; the Association's camp near the south shore, placed from where the coast actually runs there so its circle of light is always on land; the nests spread round the other 290 degrees, clear of the camp and the hive and each other; the resort's ruins; the great hive at the heart with its mouth and the pit on its south side; and deep in the rock under it all the queen's chamber (her dais at its north end, the way in at its south end, the cocoon on the wall behind her). The game-test server has no datapack dimensions: there a small island (radius 30) floats over the overworld far down a lane of its own (z -150,000), and `onIsle` asks by position |
| `IsleBuilder` | Builds the island once, a chunk at a time as its ground loads (or at once when a landing can't wait): the land from the sea floor up (sand beaches, grass, the swarm's mud and resin creeping out from the hive), the hive's dome of honeycomb and mud with lit spires, its mouth sealed with resin, the nests' resin rings (scorched and burning once burned), the camp (tents, fires, crates, banners, the circle of light the hunters arrive and leave by), a cracked road and the resort's white ruins and palms, and the vaulted chamber of resin with eggs along its walls and the queen's dais ringed with light. The swarm's eggs are clutches that never hatch (laid off the sand; the sniffer eggs used at first hatched into sniffers after twenty minutes). Before every landing the nests, the seal and the cocoon are set back, the chamber's eggs a fight trampled are laid again and anything left over is cleared. `VERSION` goes up when the island's look changes: an older island is built again before the next landing |
| `IsleRaids`, `IsleRaid`, `IsleData` | The raid. **The call:** every `isle.callMinutes` (90, give or take a quarter; the first `firstCallMinutes` 25 after a hunter it would call is online) the Association calls every awakened hunter of `minRank` (B) or above who is online and free (not in a gate, a trial, the Penalty Zone, a red gate, the Citadel or an evaluation): an alert, a chat line with [Join] and [Decline] (`/isle join`, `/isle decline`), the call's moment and a horn. **The muster** stays open `musterSeconds` (150), cut to four seconds once everyone called has answered; then everyone who answered lands together at the circle of light, and `allies` (5) of the strongest A and S hunters of the national rankings step in beside them in their guilds' colours. **The beach:** `beachWaves` (3) waves of the swarm come down from the island's heart (`swarmRank` A: broodlings, drones, reavers from the second, shardbacks from the third; the last wave led by an elite), at most `swarmCap` (26) alive. **The nests:** each of the `nests` (5) has a beating heart that broods while hunters are near (every `nestSeconds`, 7) and bursts after `nestHits` (14, and `nestHitsPerHunter` 4 more for each hunter beyond the first) blows: any blow of the hunters' side (a hunter's, their shadows' and pets', an ally's) is one, one every four ticks; every third it screams and its young come out. Its nest burns, and its brood with it. **The hive:** the last nest tears its mouth open; the queen waits asleep on her dais, her elite guard in the hollow above. Dropping into the pit takes a hunter down to the chamber, and the first one down wakes her; crouching in the ring of light at the chamber's south end climbs back up. **Seven seconds after the queen falls** her cocoon splits and the king is born. **When he falls** the island is retaken: the swarm dies with him and everyone who landed and didn't walk off is paid (the fallen half): XP and gold `rewardScale` (3) times an S-rank gate's clear, 400 merit (200), a blessed S-rank box, greater mana stones, half the time a top-grade weapon, the title Hive Breaker, and the report. Everyone goes home 45 seconds later to where they were called from. **Lost:** when nobody is left fighting (all carried off or gone), nobody has stood on the island for a minute, or `timeLimitMinutes` (60) have run out: the quests fail and everyone goes home ten seconds later. The raid is kept with the overworld's data over a restart (`IsleData`: the next call, the raid, which island stands, what is owed to hunters who were offline when it was won, paid when they come back); a raid that throws lets its hunters go home |
| `NestHeartEntity` | `beyondsolo:nest_heart`: never saved; raised again, with the blows it had taken, when its nest's ground loads. Nothing but the hunters' side hurts it, and a command's kill bursts it |
| `HiveBoss`, `QueenBoss`, `KingBoss` | The hive's royalty: great threats of a tier of their own (`WorldBossKind.QUEEN`, `KING`, `Tier.HIVE`) that fight only in the chamber and never leave it; the rankings' hunters are their foes too, every dash and leap stops short of the walls, and their bars and banners are the great threats' (`WorldBossHud`). The chamber is theirs: awake, they go for the nearest hunter or ally in it, seen or not, and never stand idle while one is there; the allies down there with nothing else to fight go for them (`IsleRaids.rally`). Bringing them down is the hunters' own work: the allies' blows land on them at a tenth (`isle.allyRoyalDamage`) and carry no burn, brand or venom onto them (`HiveBoss.takesBuildup`), and no venom takes hold on them at all (it is theirs). Like every great threat they don't flush red under blows (`ColossalBossRenderer`: with a raid's hunters on one, it would never stop). The chamber has a roof: where their moves land is looked for from the point down (`HiveBoss.ground` with `WorldBosses.groundBelow`; looked for from above, a leap at a hunter on the dais found the roof where the vault comes down low, and the king landed in the rock), and a royal ever left in the rock or up under the roof is set back down on the floor toward the middle (`IsleRaids.unstick`). **Vessa, Mother of the Hive** (4.2 by 4.6 blocks, fights from her dais): I acid globs spat across the chamber (each marked on the ground first), a slam, eggs hatching round her; II her royal guard out of the walls (S-rank reavers) and a shriek (a ring to jump that leaves whoever it catches reeling); III acid raining from the vault, and her whole body dragged across the chamber. **Azhrak, the Hive King** (lean and winged, 1.5 by 3.9): I scythe claws, a dash straight through his prey, a dive from the air, a spray of acid; II a roar that leaves hunters shaking, and devouring his own swarm to mend (2.5% of his health each, three at most); III faster still, and a frenzy of blows. Each pays every hunter of the raid by their share of the damage (XP, gold, greater mana stones, an elixir for the top hunter, a chance of a weapon from the king); the king's top hunter becomes the Kingslayer. A great threat's killing blow counts toward its hunter's share before its fall is told |
| Falling | A blow that would kill a hunter fighting on the island doesn't: the medics carry them off it (`carriedOff`), out of the raid (paid half if it is won), home at once with a quarter of their health, weak and slow, their quests failed. A death that got past it anyway is cancelled the same way. A hunter who walks off the island (or `/isle leave`) is out of it, unpaid |
| The island holds | Nobody but an operator in creative digs or builds on it, and no blast takes a block of it |
| Quests | `beyondsolo:isle/retake` on landing (the nests, then the queen; Association, 6,000 XP and 2 stat points, timed by the raid's limit) and `beyondsolo:isle/king` when he is born (emergency, 8,000 XP and 3 stat points) |
| `IsleRecord`, `IsleJournal` | A hunter's record (`beyondsolo:halcyon` attachment, kept through death, cleared by `/solo wipe`): raids landed on and won, nests burned, queens and kings, the best time, and while on the island the raid they came with and where they came from. Titles: Hive Breaker (legendary), Kingslayer (mythic, secret until earned), Nestburner (epic, at the burning of 12 nests over every raid). The queen's and the king's journal pages come with the great threats' |
| Sync | `IslePayload` (one tag: the raid as this hunter sees it, about once a second while they are called or on it; a moment for the middle of the screen; the result; or forget it) and the great threats' `WorldBossPayload` for the bosses |
| `client.isle` | `IsleHud`: the raid's chip at the top (the call's countdown and who answered; on the island what is going on now, the nests left and which way the nearest lies, the time left, the hunters, allies and kills; after, retaken or lost and the way home), marks in the world (every standing nest with its heart's blows filling a bar, the pit once open, the ring back up in the chamber) and the System's moments (the System's pop-ups wait while one of them is up or waiting: they would cover it; the island retaken and the report wait for the king's fall to be told first, `WorldBossClient.slainPending`); `IsleReport`: the report window after (the time, kills, the swarm slain, nests, allies who fell, and what it paid, counting up; it claims its room at the top like the bars, so the king's soul to extract shows under it); `NestHeartRenderer` (its glow beating faster as it weakens, a spray of light as it bursts) |

Config: `beyondsolo-isle.toml` (`isle.*`); every number is a first guess for the final balancing pass. Art:
`tools/gen_isle_assets.py` (the queen: an amber egg sac of membrane and black plates on spider legs, a crowned head
with mandibles and scythe forelegs; the king: lean black chitin with venom-green markings, scythe claws and four
wings; the nest heart: a lumpy sac of resin and flesh with a glowing seam in curling ribs; every animation their
moves play). Players: `/isle` (how it stands), `/isle join`, `decline`, `leave` (`IsleCommand`). Operators:
`/solo isle call [players]` (named players: any rank), `land [players]`, `launch`, `phase
beach|nests|hive|queen|king`, `win`, `fail`, `stop`, `status`, `next <minutes>`, `build`, `visit [player]`, `record
[player]`, `reset [players]` (`SoloIsle`, `IsleAdmin`).

Testing: `IsleGameTests` (the island laid out soundly for 48 seeds with one to eight nests at both sizes; the call
reaching only awakened hunters of its rank, a call nobody takes closing, an operator's call reaching any rank and an
answer taken back; a whole raid from the call to home: landing at the circle, the heart's blows, the hive opening,
down the pit, the queen, the king's birth, the island retaken and paid for, the swarm's eggs that never hatch and a trampled one laid again; a hunter carried off instead of dying while
the raid goes on with the allies beside them, then lost when nobody is left; the island holding against picks, blocks
and blasts; the titles). Each test that runs a raid is in a batch of its own (one raid at a time), and keeps the
island's ground loaded itself (fake players load no chunks). In the headless client: `/solo isle land`, then
`/solo isle phase queen` or `king`.

## The hunter's residence

In the series the hunter's family moves from a cramped flat into a fine apartment once the gates start paying, and
the hunter keeps coming back to it between raids: a bed, a meal with his mother and sister, a place the fighting stays
out of. Here every awakened hunter can buy a home of their own on the top floor of a tower over a city at night, and
go back to it from wherever they stand. Code in `home/` (server) and `client/home/` (client); text in
`assets/beyondsolo_home/lang`.

| Piece | What it does |
|---|---|
| `HomeWorld` | The homes' world, `beyondsolo:residence` (a flat city floor, a fixed night, its own biome; `natural` and beds work there, no mobs; `data/beyondsolo/dimension*`, `worldgen/biome/residence.json`). A hunter's home is their plot (`HomeData`, kept with the overworld's data): plot `i` is a tower at x = 1024 i with a city of its own round it out to 200 blocks, out of sight of the next. `Layout` says where everything on the floor is: the Studio in the middle (y 150), the Family Home's wing west of it, the Training Hall's east (twice as tall), the Penthouse's gallery over the Studio and the terrace over the west wing, the lift's button, the beds, the desk and bell, the dummies' and pedestals' spots. The game-test server has no datapack dimensions: there the floors alone float over the overworld down a lane of their own (z -170,000, 160 apart) and `plotAt` asks by position |
| `HomeTier`, `HomeConfig` | The four parts, each bought in turn with gold once the hunter's level and rank are high enough (first guesses): **Studio** (level 10, E, 8,000: living room, bed, kitchenette, chests), **Family Home** (25, C, 30,000: kitchen, dining room, their mother's room, the study with the Association's desk and the broker's bell), **Training Hall** (45, B, 90,000: a tall hall with three training dummies, one armoured, and weapons on the wall), **Penthouse** (70, A, 250,000: the trophy hall of the great threats and the roof terrace) |
| `HomeBuilder` | Builds a plot a piece at a time as its ground loads: the floor first (each part the owner has, the doorways between them, what stands in them), so the hunter can come in at once, then the city round it a chunk at a time (streets and lamps, squares and parks, towers of lit windows, the hunter's own the tallest). The city is laid once for a plot (`VERSION`); a newly bought part is added to the floor. A part built over an older one first clears what stood there (dummies, frames, dropped items) |
| `Homes` | **Owning:** `buy` checks level, rank and gold (`Economy.Reason.HOME`) and `own` builds the new part, tells the hunter what it is, gives the titles and opens the new rooms round them if they are home. **Going home:** the HOME page's button or `/home`: the hunter stands still `channelSeconds` (3; drifting 0.75 blocks or being hurt breaks it, `HomeEventPayload` tells the HUD) and is in the foyer; not out of a gate (or on the way into one), the awakening, an evaluation, a job or throne trial, the Penalty Zone, the Citadel, a red gate, the island, a story scene or a regroup (`busy`), and after arriving they wait `cooldownSeconds` (60) before going again. Where they stood is kept (`HomeRecord.away`) and the lift's button, the page or `/home leave` takes them back there (or to their spawn if that world is gone). **Visiting:** a party member's home, the same way (`visits`); a guest is sent back out when they leave the party or the host's home is taken. **Keeping it:** only the owner builds or breaks inside the rooms they own, and never the walls, the lift or what the home is built round (`mayEdit`); no fire, blasts, pistons, portals or spawn points; a guest opens nothing and sleeps in no bed; anyone who falls out of a home is put back at its door. Distances and directions for the Association and the broker are reckoned from where the hunter left the world (`worldPosition`) |
| The rooms | **Rest:** a night in their own bed brings back health, mana and fatigue and, once a day, Well Rested (`well_rested`, `restMinutes` 30: skills a little cheaper and quicker, slower to tire). **The study:** the desk opens the Association's office and the bell calls the broker, both for the gates round where the hunter left the world (`Association.openAt`, `Broker.openAt`). **Their mother** (the Long Sleep's `Kin`): once she is awake and the Family Home is theirs she moves into her room (`Kin.moveIn`), and back to the village if the home is ever taken (`moveOut`). **The dummies** (`TrainingDummy`, `beyondsolo:training_dummy`): take every blow, skill, burn and summon's hit like a creature (they count as foes) and never fall; a bout starts with the first blow and ends after three quiet seconds; the hunter's best damage a second over ten seconds is kept and announced; one stands armoured (20 armour, 12 toughness). **The trophy hall** (`TrophyPedestalBlock`): a pedestal for each of the ten great threats (the two world bosses, the three monarchs, the three rulers, the hive's queen and king); slain by the owner, its likeness stands there |
| `HomeRecord`, `HomeJournal` | A hunter's home (`beyondsolo:residence` attachment, kept through death, cleared by `/solo wipe`): the tier, where they left from, whose home they are visiting, the wait, the day they last woke rested, their mother, the best at the dummies, which parts' rooms they have been shown. Titles: A Place to Return (rare: stamina, vitality) for the first home, Skyline Sovereign (legendary: gold, XP, focus) for the Penthouse |
| Sync | `HomeSyncPayload` (one tag: what they own and what the next part asks, where they are, the wait, rest, mother, best, trophies, the build's progress (sent again every two seconds while it goes up, and when a part is done), the party's homes), `HomeEventPayload` (the gathering started or broke, arrived, left, the answer to a button), `HomeActionPayload` (the page's buttons) |
| `client.home` | `HomeTab`: the HOME page (order 109; the residence with the way home, out or the wait, the next part with what it asks and its button, the last answer, rest, mother, best and trophies; the four parts; the party's homes with a Visit button). `HomeHud`: the gathering's card, a red word if it breaks, the home's name over the room on arriving. `HomePlates`: over a dummy its meter (damage a second, the last blow, the bout) or its name under the crosshair; over a pedestal the threat's name and how many times the owner slew it, or a question mark. `TrainingDummyRenderer` (GeckoLib: swaying on its post, rocking under blows, the armour shown on the armoured one), `TrophyPedestalRenderer` (the likeness in the pose it sleeps in, small enough to fit, on a glow of its colour) |
| Elsewhere | Parties show "At home" / "At X's home" (`PartySyncPayload.HOME`) and don't regroup out of a gathering; `/solo wipe` takes the home away |

Config: `beyondsolo-home.toml` (`home.*`); every number is a first guess for the final balancing pass. Art:
`tools/gen_home_assets.py` (the dummy: burlap, rope and straw on a post with a sewn face and a kettle hat and
breastplate for the armoured one, its sway and blow animations; the pedestal's model and textures; the Well Rested
icon) and the house, bed and city icons in `tools/gen_ui_assets.py`. Players: `/home` (go), `/home leave`, `/home visit
<player>`, `/home buy`, `/home stay`, `/home status` (`HomeCommand`). Operators: `/solo home info [player]`, `give
studio|family|hall|penthouse|none [players]`, `go [player]`, `visit <host> [player]`, `leave [player]`, `build`,
`rebuild [player]`, `rest [players]`, `ready [players]`, `trophies <count> [players]` (`SoloHome`, `HomeAdmin`).

Testing: `HomeGameTests` (the plan's rooms and fixtures inside the floor; buying in order with level, rank and gold;
going home and back to the same spot, the gathering broken by a step or a blow, the wait; a guest's visit and its end;
the rooms kept: a stranger's block, the walls; a night's rest; the dummies' meter, best and never falling; the trophy
hall; the mother moving in and out; a fall caught). In the headless client: `/solo home give penthouse`, `/solo home
go`, `/solo home trophies 1`, and the driver's `screen home` and `homego go|leave|buy|stay` for the page.

## Rival parties and hands for hire

In the series the hunter is never the only one going for a gate: guild parties race each other to the good ones, the
association gives a gate to whoever gets in first, and a hunter short of help hires porters and freelance hunters for
the day. Here a gate that opens near a hunter now and then draws another party of the gate's rank, and every
association office has hands looking for work. Code in `rival/` (server) and `client/rival/` (client); text in
`assets/beyondsolo_rivals/lang`. Names are the mod's own.

| Piece | What it does |
|---|---|
| `RivalParties` | **Setting out:** `gateOpened` (from `GateSpawner`'s random gates, never a hunter's very first gate) sends a party for a fresh open gate with `rivals.dispatchChance` (35%) if it is `eligible` (open, in the overworld, no trial, key, raid, red gate in waiting, broken gate or association claim, no other party on it). `dispatch` makes three to five hunters of the gate's rank (a leader, someone to stand in front, a healer for four or more), led half the time by a free hunter of the national rankings (`Rivals`) with their guild's people, else for one of the four guilds or a band of independents. Awakened hunters within 160 blocks are told (direction, distance, the time they need: `etaMin/MaxSeconds` 75 to 150) and see them on the HUD. **The clocks:** every second each party moves on: EN_ROUTE until it arrives, GATHERING in front of the gate (`gatherSeconds` 10), then INSIDE. First in, it holds the gate (`holds`): the association posts no contract on it and a hunter walking up is turned away (`refusal`, `refuse.held`) unless they crouch to force their way in, which costs `trespassMerit` merit once (`Association.takeMerit`) and starts a race. A hunter who got in first beats them to it: most parties stand down, a bold one (its guild's nerve: Red Lantern 60%, independents 30%, the others 8 to 15%, scaled by `followInScale`) follows them in, and it is a race. A gate cleared before they got in, or claimed by a permit or an exam while they gather, sends them home. **Inside, unseen:** their progress runs on `clearMinutes` by rank; they clear it (`guildSuccess` 85%, `independentSuccess` 70%, rolled as they go in), taking the boss and closing the gate, or are beaten back halfway and leave it open. **The race:** `onDamage` tallies what the party and what the hunters (and everything on their side: shadows, summons, guild and hired hunters) dealt the boss; when it falls (`bossFell`, from `GateService.bossDefeated`) whoever dealt more takes it: the party's win means no reward cache, a hunter's win the cache and a race won on their record. A ranked leader's clears count on the national rankings and merit board |
| Their hunters | `RivalHunterEntity` (`beyondsolo:rival_hunter`, a `GuildHunterEntity`: class armour and weapon, the guild's name in its colour over their head, never fights a hunter and can't be hurt by one) is only there while a hunter is within 96 blocks to see them: walking a loose file from where they set out to the gate (each paced to reach their place as the clock runs out, `keepPace`: a mob walks about 2.16 x (speed x pace)² blocks a tick, so the walk is set from the distance left and the ticks left), standing in an arc in front of it, walking into it. In a cave with a hunter in it they step out where the party has got to on its way to the boss (`route`: room by room along the passages, never through a hidden chamber; the caverns behind them emptied) and walk on (`march`/`marchFrom`), waking what waits ahead and drawing what idles near them; their feet are then the clock (80% of the way at the boss's door). One who falls is out for good; all of them down, the run is over. Right-clicking one gets a line for where the party is at (`talk`, three each), the leader named by the others |
| `Hires` | **The list:** each office has two porters (an E and a D) and `hire.offers` (4) freelance hunters of the hunter's rank or one below, the same faces all day for that hunter (`offers`, seeded by office, day and hunter); the hired ones leave it. Fees up front (`Economy.Reason.HIRE`): a porter `hire.porterFee` (40/90), a hunter `hunterFee` (0.6) of their rank's gate gold, more deeper into the rank. One porter and `hire.maxHunters` (2) hunters at a time, a hunter of a rank above yours refuses. **The job:** `hire.minutes` (20), or until a gate cleared together is behind them (`gateCleared`); dismissing is free and refunds nothing. They step out beside their hunter wherever they go in the world and into gate caves (not a trial or an exam), and beside them again when left 40 blocks behind. A hired hunter is a guild hunter fighting for the hunter (`CAUSE`, kills counted). **The porter** (`PorterEntity`, `beyondsolo:porter`) never fights, keeps out of creatures' way and picks up what falls within 10 blocks (not what a player threw down) into a pack of `hire.porterSlots` (27); their hunter opens it by talking to them; at the end of the job it goes into the inventory, then the System's storage, then at their feet; a porter who falls drops it where they fell |
| `RivalRecord` | `beyondsolo:rivalry` attachment (kept through death, cleared by `/solo wipe`, which lets every hand go and hands the pack over): races won and lost, gates forced, the hands on the job (a porter's pack with them), who was hired today. Titles: **Gate Racer** (three races won) and **Claim Jumper** (a boss taken from a party that held its gate; hidden until earned) |
| Sync | `RivalSyncPayload` every second: the parties a hunter watches or shares a gate with (four at most, theirs first), each with its guild, leader, state, clock, progress, standing members and the race's share (a hunter who knows of none is told once, not every second). The hire tab rides on the office's own data (`Hires.officeTag`) |
| `client.rival` | `RivalHud`: a chip at the top centre for the nearest party (the guild's emblem in a ring that turns while they move; a clock to their arrival or to their going in, with the gate's distance and an arrow; the percent through a gate they hold; in a race a split bar YOU / THEM by damage, or their way to the boss with a skull at its end; how it ended, gold with a crown when the hunter beat them; "+N more"), and a mark over each gate a party is after (its emblem, a ring filling as they clear it, "Here in 1:20" / "In in 0:08" / "Held · 43%"), fading out beyond 72 blocks and kept under the top-centre bars when the gate is close (`HudSpace.topClaimedLately`). A party is named by whoever leads it, as in the System's messages. `RivalHunterRenderer` (a guild hunter with an armband in the guild's colour, runtime-baked), `PorterRenderer` (three porter skins, the pack: a sack with flap and pocket, a bedroll, a frame, straps, a swinging lantern that glows at night and a pot). The office's **HIRE** tab (`AssociationScreen`): today's hands as tiles (porter or class, rank, level or slots, fee; why one can't be hired), the hands on the job with their time, carried stacks or kills and a LET GO button, and the hunter's record against the other parties |

Config: `beyondsolo-rivals.toml` (`rivals.*`, `hire.*`); every number is a first guess for the final balancing pass.
Art: `tools/gen_rival_assets.py` (`--preview` renders the three porters and the armband to `build/preview/rivals.png`)
and the guild emblems, race and trail icons in `tools/gen_ui_assets.py`. Operators: `/solo rival send
[guild|random] [ranked]` (the nearest free gate within 128 blocks, a new one if there is none), `arrive`, `enter`,
`finish clear|retreat` (the nearest party), `list`, `forget`, `record [player]`; `/solo hire porter [e|d] [players]`,
`hunter [rank] [class] [players]` (for nothing), `dismiss [players]`, `list [player]` (`SoloRivals`).

Testing: `RivalGameTests` (a party setting out, told to the hunters near, holding a gate it went into first: no
contract, hunters turned away unless crouching; standing down when beaten to it or too late, the bold following in for
a race; leaving a gate a permit claimed; clearing alone or beaten back, the gate open again; the race going to whoever
dealt the boss most, three wins bringing Gate Racer; forcing a way in costing merit once and Claim Jumper; a party
gathering in sight standing in front of its gate in its guild's colour; the office's list the same all day; a porter
picking up what fell and handing it over; hired hunters up to the limit, following their hunter, gone with the gate;
the commands and `/solo wipe`). The tests' gates have no block, their parties are kept out of sight
(`RivalTestAccess.unseen`) and the hunter "in" a gate stands at its cave's origin. In the headless client: `/solo
rival send red_lantern ranked`, `/solo rival arrive`, `/solo hire porter d` and the office's HIRE tab.

## Guild scouting, the bidding war and the hunter news

In the series a hunter who ranks up becomes news overnight: the big guilds' scouts turn up with signing bonuses, they
outbid each other, and turning every one of them down makes headlines of its own. Here a rank-up (or a high awakening)
brings the four great guilds of the national rankings that want the new rank, one scout after another; the hunter
holds out, plays them against each other, signs or says no; and a broadcast-style news feed runs the country's hunter
news for everyone on the server. Code in `scouting/` and `news/` (server), `client/news/` and the scout's window in
`client/association/` (client); text in `assets/beyondsolo_scouting/lang`. Guild and scout names are the mod's own.

| Piece | What it does |
|---|---|
| `GuildTerms` | Each great guild's terms (first guesses): **Red Lantern** comes from rank C, the richest bonus (x1.35) and the biggest cut (20% of gate gold), no perk, impatient (walks away 30% of the time a hunter holds out with nothing better on the table); **Iron Bastion** from B, x1.0, 15%, hired hands 30% cheaper (`Hires`), patient (10%); **Sunfall** from A, x1.1, 12%, +15% Association merit, 18%; **Azure Tide** from S only, x1.2, the smallest cut (10%), +5% System XP, 20% |
| `Scouting` | **The offers:** `onRankChanged` (up) and `onAwakened` (C and up) call `scout`: each guild that wants the rank and hasn't come for it yet (`cameFor`), isn't the hunter's guild and has no offer pending puts one on its way, the first after `firstScoutSeconds` (45), the next `scoutGapSeconds` (40) apart. A bonus is `bonusGates` (10) gate golds of the rank (`EconomyHooks.GATE_GOLD`) x the guild's rate x 0.9-1.1, to the hundred; its ceiling is 1.3x (C) to 2x (S) of it. Once open an offer stands `offerMinutes` (20), then lapses. **The scout:** every second (`tick`) a newly open offer sends its scout (`ScoutEntity`) from 18-24 blocks behind the hunter if they can be visited (in the overworld, on their feet, not hurt by a creature in 8 s: a hit from before they last joined doesn't count, as the game keeps its time while the tick count starts over), else word of it (answer at the office); one that couldn't come comes later. Each guild sends its own people (its share of the scouts' names). The second guild of a rank-up's round on the table makes the news (a bidding war). **Answers** (`sign`, `holdOut`, `decline`, from the scout's window or the office): holding out against a better offer on the table beats it by 5-10% (up to the ceiling, then final; "can't match" if the ceiling is below it); with nothing better the guild raises 10-20% (`raiseMin/Max`) or walks away (its patience + 12% for each raise already given, `walkAwayStep`; counted apart from offers turned down). Turning one down raises the others' open offers 5% (`declineRaise`); turning down all four in a round gives **Not for Sale** (breaking news). Signing pays the bonus (`Economy.Reason.CONTRACT`), withdraws the open offers and those of the same rank still on the way, buys out a contract the hunter is under (no repayment), and an S-rank signing (not a renewal) gives **Franchise Hunter**. **The contract** (`contractDays` 7, the world's days): the guild takes its cut of gate gold (`gold`, from `Economy.add`; `paid` keeps the tally), its perk applies (`hireFee`, `merit` from `Association.addMerit`, `onXp`), and its parties are allies (below). When it runs out the guild sends a renewal (x1.1, `renewal`) 20 s later; walking out at the office repays what is left of the bonus (`owed`: bonus x days left / days, to the hundred) and makes the news |
| Allies | A contracted guild's rival parties (`RivalParties`) are on its hunter's side: on its way to a gate the hunter got into first it stands down (`stood_down.ally`), never follows; one holding a gate lets the hunter in (`refusal` is null, no trespass); there is no race with them inside, and the boss falling with the hunter in there is a clear together (`notify.joint`: the party's clear counts, the cache is the hunter's). Their HUD chip reads YOUR GUILD with a handshake, their notices are titled YOUR GUILD and say so (`dispatched.ally`, `went_in.ally`; no race warnings), and they greet the hunter as one of theirs (`talk.ally`) |
| `ScoutRecord` | `beyondsolo:scouting` attachment (kept through death, cleared by `/solo wipe`, which also sends the scouts home): the offers (on the table or on their way: bonus, first bonus, ceiling, cut, days, raises, final, opens/lapses, the scout's name and look), the contract (guild, rank, bonus, cut, from/until day), gate gold paid under it, contracts signed, offers turned down, raised and lapsed, guilds that walked away, the best bonus, the rank each guild last came for and the current round (its rank, the guilds that came, those turned down) |
| `ScoutEntity` | `beyondsolo:guild_scout`: a person in a sharp suit (three looks, `tools/gen_scout_assets.py`) with their guild's band in its colour, their name over their head in it. Walks up to their hunter (faster from far off), says hello in their guild's manner (`scout.hello.<guild>` / `scout.renewal.<guild>`, an action-bar hint to right-click), waits `scoutWaitSeconds` (150) while the window isn't open, then leaves the offer with the association and walks off. Never saved with the world, can't be hurt or pushed; right-clicking one that came for someone else gets "I'm here for another hunter" |
| `HunterNews` | **The feed** (`NewsData`, saved: the last `news.keep` (40) headlines, each with its time, day, kind (rank, guild, race, break, boss, world), accent and whether it is breaking): `post` keeps a headline and sends it to every player at once (`NewsPayload`); the whole feed at login. **What makes the news:** rank-ups from C (S breaking) and C+ awakenings, bidding wars, signings (and renewals, buy-outs), Not for Sale, walking out on a guild, gate races won and lost against rival parties, a party led by a hunter of the national rankings clearing its gate, dungeon breaks opening (A and S breaking) and put down, world bosses and monarchs slain (monarchs breaking). Every ~25 min (`ambientMinutes`, randomized) one of eight headlines about the world's other hunters (a ranked hunter's clear, the top of the merit board (a player who tops it too), recruiting, joint raids, solo clears, exam season, an injury, a rookie) |
| `client.news` | `NewsTicker` (a HUD layer): a headline runs in a strip at the top centre under the other bars there (`HudSpace`), like a broadcast's: a tag on the left (HUNTER NEWS with its kind's mark, or a red BREAKING with a live dot and a bell) and the headline beside it, crawling along when it is longer than the strip; one at a time, the rest wait their turn. It keeps clear of the bars it claims room under (not of its own claim: `HudSpace.topClaimedLately(left, right, mine)`) and, pushed down beside the pop-ups floating at the right side (`NotificationRenderer.sideLeft/Top/Bottom`), runs narrower between them and the left column. The gold capsule on that side eases down under those pop-ups too (`GoldHud`). The Chronicle's **NEWS** section (`NewsPage`, a bell with a dot while there are unread headlines): a masthead (the day, how many headlines, the last breaking story) and every headline, newest first, with its mark and when it ran |
| Windows | `ScoutScreen` (an `AssociationWindow` in the guild's colour): the guild's crest and motto, the hunter's contract or "free agent", the offer (a bonus that counts up when it changes, +N% since the first offer, FINAL), its days, cut and perk, what being the guild's own means for its parties, the rest of the table, what each answer does (the row of the button under the mouse lit) and SIGN / HOLD OUT / DECLINE; once signed, a welcome card (the bonus paid, the terms that run now, what walking out costs). The portrait leaves the scout's floating name off (`AssociationWindow.drawingPortrait`). The office's **GUILDS** tab: the contract (bonus, cut, gold taken so far, days left, WALK OUT with what it costs) or, with none, which guild comes from which rank; every offer on the table with its clock and Sign / More / No. Both ride on the association's payloads (`Association.SCOUT`, `TAB_GUILDS`; actions `SIGN`, `HOLD_OUT`, `DECLINE`, `BREAK_CONTRACT`). The HIRE tab shows Iron Bastion's discount (the old fee struck through) |

Config: `beyondsolo-scouting.toml` (`scouting.*`, `news.*`); every number is a first guess for the final balancing pass.
Art: `tools/gen_scout_assets.py` (`--preview` renders the three scouts to `build/preview/scouts.png`); the band is the
rival parties' (`guild_band.png`). Operators: `/solo scout offer <guild>|all [players]` (on the table now), `send
[players]` (a scout walks up with the best offer), `talk [player]` (the nearest of their scouts' window), `lapse [players]`, `list [player]`, `reset [players]`; `/solo
contract sign <guild> [players]`, `expire [players]` (runs its course: the renewal comes), `end [players]`, `info
[player]`; `/solo news ambient [count]`, `rankup [player]`, `list`, `clear` (`SoloScouting`).

Testing: `ScoutingGameTests` (a rank-up bringing the guilds that want the rank, one after another, each once a rank,
bonuses at their guild's rate; holding out beating a better offer up to the ceiling, raising or walking away by the
rolls passed in, "can't match"; turning a guild down raising the rest, all four making the hunter Not for Sale and
breaking news; signing paying the bonus, withdrawing the rest, the cut of gate gold and nothing else, Iron Bastion's
cheaper hires, a buy-out, walking out repaying the rest or refused without the gold; a contract running out bringing a
renewal, Sunfall's merit and Azure Tide's XP, offers lapsing; the guild's own parties standing down, letting the hunter
in, a joint clear, another guild's party still turning them away; a scout coming from out of sight with the offer; the
news keeping its last headlines; the commands). In the headless client: `/solo scout offer all`, `/solo scout send`,
the office's GUILDS tab, `/solo news ambient 3`, `/solo news rankup` and the Chronicle's NEWS page.

## The chronicle: titles, rankings and the journal

Code in `chronicle/` (server) and `client/chronicle/` (client); lang in `assets/beyondsolo_chronicle/lang`.
`ChronicleModule.init` registers the hunter's chronicle (`ChronicleData`, attachment `beyondsolo:chronicle`, kept
through death: titles earned with the game time, the chronicle's own counters, journal pages, what is not looked at
yet, and whether the worn title was picked by hand) and four payloads (`ChronicleSyncPayload` the chronicle, never how
far along a title is; `TitleEarnedPayload` the TITLE ACQUIRED moment, `RankingsPayload` the boards,
`ChronicleActionPayload` the page's buttons: wear, take off, read, ask for the boards).

- **Titles** (`title/Titles`, 52 of them and the eight secret ones of the situation quests, in five categories: hunt,
  gates, citadel, system, mastery; five tiers from common to mythic). A title with a `Feat` is earned when that number
  reaches its goal: the chronicle counts kills, bosses, ascended bosses, clutch kills (a boss falling while the hunter is under 15% health), kills of far stronger
  creatures, deaths, kills by creature family (`journal/CreatureFamily`), dungeon breaks held, hidden and trap rooms,
  dailies, surprise quests, reactions and the longest combo itself; everything else is read from the record its own
  feature keeps (gates cleared, red gates, keys, weaknesses, the Citadel and the rush, quests, the penalty zone, level,
  gold earned, the shadow army, journal pages). `TitleService.check` runs every 2 s. A title without a feat is given by
  its feature: `SystemAPI.setTitle(player, key)` fires `SystemEvent.TitleGranted` (cancellable) and the chronicle keeps
  it. A new title is worn when nothing is worn or when the System picked the worn one and the new one is rarer; one the
  hunter picked on the page stays on. What earns a title, how far along it is and its perks stay hidden until it is
  earned: the page shows the rest by name and rarity only, as a guide, and TITLE ACQUIRED tells what earned it. Some
  titles are secret (not even the name: "???" until earned). Titles worn before the chronicle existed are taken in
  quietly at login.
- **Perks** (`TitlePerk`, only the worn title's): attribute ones as transient modifiers `beyondsolo:title_<perk>`
  (attack, health, speed, armor, luck, elemental and spell power, mana efficiency, skill haste, exhaustion
  resistance) put back on login, respawn and every change; event ones in `TitleService` (System XP on `XpGain`, gold
  in `Economy.add` for kills, quests, gates and the Citadel, more damage to creatures, bosses, a family or far stronger
  foes, less damage taken capped at 50%, and both below 30% health). Every number waits for the balancing pass.
- **Rankings** (`ranking/`): the hunters' board by power (ten times `HiddenLevel.hunterLevel`), the Citadel (highest
  floor, then the spire), the boss rush per tier (best time) and gates cleared. Players' standings are kept in the
  saved data `beyondsolo_rankings` (taken at login, every minute and at logout); a fixed roster of 16 rival hunters
  (`Rivals`, own names, guilds, ranks E to S) stands in for the national rankings and grows a little with the days of
  the world. Climbing past someone on the hunters' board is announced with the next one to pass.
- **The journal** (`journal/Journal`, about 107 pages: every gate creature by family, every boss and the Arbiter and
  the lord, every realm plus the red gates' world, the Citadel and the Wastes, every graded weapon). `JournalService`
  fills pages in from what the hunter sees within 24 blocks (every second), where they stand (a gate's realm or a
  place) and what they carry (every 2 s); kills count on the page and a boss remembers each rank it was met at and
  its ascended form. Another feature adds its pages with `Journal.register`. Lore lines are
  `chronicle.beyondsolo.lore.<category>.<name>` (a weapon's own `.lore` line comes first).
- **Client**: the CHRONICLE page (`ChronicleTab`, order 108) with three sections: `TitlesPage` (filters, every title
  by name and rarity, the picked one's crest and, once earned, what earned it, its perks and WEAR / TAKE OFF), `RankingsPage` (the boards, the
  hunter's place and who is next, the rush tiers) and `JournalPage` (a grid per section: creatures and bosses drawn
  as themselves, weapons as their icons, realms as pictures, all dark shapes until known; the page with the creature
  turning, its lore once one is slain, kills, the first day it was met and a boss's forms). `TitleOverlay` plays
  TITLE ACQUIRED (the world dims to the tier's colour, the crest bursts in, the name types out on a band of light,
  perks, worn or how to wear it; titles earned together share one moment) and a small card for each new page; both
  wait while another System moment has the screen, and the hide key skips them. The worn title in the STATUS page
  takes its tier's colour and opens the titles when clicked. Realm pictures (256x144) and icons come from
  `tools/gen_chronicle_assets.py`.
- **Commands** (`/system chronicle ...`, level 2): title grant|revoke|equip <id|all>, title check, count <feat>
  <value>, journal reveal <all|section|page>, journal clear, rankings, info, reset.

## Operator commands: /solo

`/solo` (package `command.solo`, permission level 2 like `/give`; in single player that means cheats on) is the one
place for an operator to change the game: wipe a player, set their numbers, spawn things, give items, move through
gates. `/solo` alone shows the groups as buttons and `/solo help <group>` lists that group's commands, each line
clickable to start typing it. The older `/system ...` commands stay for the mod's own tests and are reachable as
`/solo dev ...` (a redirect to the `/system` node; `SoloCommand` registers at `EventPriority.LOWEST` so every module
has added to `/system` by then).

| Group | Commands | Class |
|---|---|---|
| player | `info`, `awaken [rank] [class]`, `evaluate [rank] [class]`, `bind`, `set level/rank/class/subclass/stat/points/gold/exhaustion`, `add xp/levels/points/gold/stat`, `heal`, `reset <part>`, `wipe system/all` | `SoloProgress`, `SoloWipe` |
| skills | `skill learn/forget <skill>/all [level]`, `skill max`, `quest list/start/complete/fail`, `shadow give <creature> [rank]` | `SoloSkills` |
| world | `spawn creature/boss/gate/cache/crystal`, `gate list/close [all]/break`, `village find/clear/info` | `SoloWorld`, `SoloVillage` |
| dungeons | `dungeon enter/boss/clear/leave/info`, `trial awakening/job/throne/penalty`, `tower climb [floor]/rush <tier>/endless` | `SoloWorld` |
| items | `give item <item> [count] [grade]`, `give armor <set> [grade]`, `give key/rune/tome/license` | `SoloGive` |
| guilds | `guild create <player> <tag> <name>`, `guild level/gold <n>`, `guild hunter <rank> [class]`, `guild heal hunters`, `guild disband`, `raid open <rank>`, `raid make`, `raid start now`, `raid phase barrier/cataclysm` | `SoloGuilds` |
| monarchs | `monarch crown <crown>`, `monarch uncrown`, `monarch call`, `monarch reset`, `monarch jump 1/2/arbiter/throne/<crown>`, `monarch power domain/dread/crown/authority` | `SoloMonarch` |
| rulers | `ruler call [verdict/wings/hand/random]`, `ruler war [ruler]`, `ruler sentence [ruler] on [player]`, `ruler land`, `ruler stop`, `ruler status`, `ruler phase <1-3>`, `ruler move <move>`, `ruler kill`, `ruler next <minutes>`, `ruler bless [players]`, `ruler mote [count]`, `ruler record [player]` | `SoloRulers` |
| light | `light swear`, `light unswear`, `light invest verdict/wings/hand/dawn`, `light divest`, `light call`, `light vigil [next/stop]`, `light chapel [wall]`, `light power sanctum/judgement/seraph/authority`, `light reset` | `SoloLight` |
| association | `association open [contracts/permits/exam/standing/hire/guilds]`, `association broker`, `association post`, `association merit add/set <merit> [players]`, `association exam start/pass/fail [player]`, `association contract [player]`, `association record [player]`, `association reset [players]` | `SoloAssociation` |
| rivals | `rival send [guild/random] [ranked]`, `rival arrive/enter`, `rival finish clear/retreat`, `rival list`, `rival forget`, `rival record [player]`, `hire porter [e/d] [players]`, `hire hunter [rank] [class] [players]`, `hire dismiss [players]`, `hire list [player]` | `SoloRivals` |
| scouting | `scout offer <guild>/all [players]`, `scout send [players]`, `scout talk [player]`, `scout lapse [players]`, `scout list [player]`, `scout reset [players]`, `contract sign <guild> [players]`, `contract expire [players]`, `contract end [players]`, `contract info [player]`, `news ambient [count]`, `news rankup [player]`, `news list`, `news clear` | `SoloScouting` |
| home | `home info`, `home give studio/family/hall/penthouse/none`, `home go`, `home visit <host>`, `home leave`, `home build`, `home rebuild`, `home rest`, `home ready`, `home trophies <count>` | `SoloHome` |

- **Who it is for:** most commands end in optional players (`[players]`, or `[player]` when it takes them somewhere):
  left out, the sender; named, a name or a selector. With several players, one failing is said under their name and
  the rest go ahead (`Solo.each`).
- **Arguments that could be a name:** Brigadier tries a node's arguments in the order they were added and keeps the
  first that parses, and a player's name parses as almost anything. So a number before the players is added before
  them (`SoloGive.counted`, the skill level, the tower floor), and words with a fixed set of values that come before
  the players are literals, not word arguments (ranks and classes in `awaken`/`evaluate`, grades in `give`, shadow
  ranks). `SoloCommandGameTests.aNumberIsALevelNotAName` guards it.
- **Ids:** items, quests and creatures take a resource id; one typed without a namespace is looked for in the mod's
  own after Minecraft's (`Solo.lookup`), and suggestions match by path too ("rune" offers `beyondsolo:rune_stone`).
- **Wipe:** `system` cancels whatever is running for the player (Citadel run, Penalty Zone, job change trial, throne
  trial, red gate, shadows, the awakening, exhaustion, situations), removes every data attachment in the `beyondsolo` namespace but the
  System's own (`SystemAPI.reset` keeps "first join handled"), clears the mod's marks in the persistent data, syncs
  every module and takes back the license and the ascension key. `all` also leaves any cave, empties the inventory and
  ender chest, clears effects and XP and sends the player to where a new player lands (`FirstSpawnHandler.startOver`:
  beside the world's first landing with `spawn.together`, else a new random start). Both then start over like a new
  player: the presystem hint with the awakening trial on, the System at once with it off.
- **Text:** every message is `command.beyondsolo.solo.*` in `assets/beyondsolo_commands/lang` (English and Spanish).
  Command words, ids and the help's usage lines stay as typed.
- Tests: `test/SoloCommandGameTests` (the tree and op-only, awaken/set/add/reset, a number read as a level, give,
  quests, spawning creatures, a boss and a gate, both wipes).

## World generation

`evaluation/VillageInjector` adds `beyondsolo:evaluation_zone` to the five vanilla village house pools when the
server starts (weight from config, default 3). In testing, 7 of 8 plains villages got at least one zone. A big
village can get more than one (a savanna village in testing had three). They all work the same.
The structure file is generated by `tools/gen_evaluation_zone.py`; you can also rebuild it in game and save it with
a structure block. Lithostitched is available if we want datapack-driven injection for modded villages later.

### The mark on the way to the first village

After the awakening the System says "find a village" (`FirstSpawnHandler.systemIntro`), and `spawn/VillageGuide`
marks the nearest village that has an Evaluation Zone. The mark is the player's objective target
(`SystemData.objectiveTarget`, saved and synced with the System, set with `SystemAPI.setObjectiveTarget`); it goes
away with the objective, so the evaluation (or a wipe) takes it.

- **Finding it** works like `/locate`: the placements of the `#minecraft:village` structures are walked ring by ring
  around the player (`RINGS` = 12, about 6,500 blocks in vanilla), and each village met is planned only as far as
  `STRUCTURE_STARTS` (its pieces, no blocks) to see whether one piece was built from `beyondsolo:evaluation_zone`
  (`SinglePoolElement.template` is opened by the access transformer). The crystal is where the zone's template puts it,
  turned the way the piece was (`StructureTemplate.filterBlocks`), so the mark is on the crystal before anything is
  built. The ring after the first find is walked too, since a village one ring out can still be nearer. Walks share
  4 ms a tick, so a long one never stalls the server; what each village holds is remembered until the server stops.
- **When:** as the objective turns to "find a village", and for anyone on that objective or "find the Evaluation Zone"
  with no mark (saves from before; a walk that found nothing tries again after 5 minutes or 600 blocks). Searches look
  from the overworld only, and not at all where the world makes no structures (the game test server).
- **Close by** (64 blocks, chunk loaded) the mark is checked against the world: a crystal a few blocks off takes the
  mark, no crystal at all forgets that village and starts a new walk. Walking into a village without a zone no longer
  switches the objective to "find the Evaluation Zone": the System says that village has none, and how far the marked
  one is (`VillageGuide.enteredVillage`).
- **On the client** (`client/hud/ObjectiveWaypoint`): a gold column of light over the crystal, drawn through hills
  and pulled inside the fog when far (same trick as gate sense); a label with the distance where it stands; on the
  objective chip (`StatusHud`) an arrow that turns with the player and the distance. Everything fades out within 9
  blocks. `client/hud/XaeroWaypoints` puts the same mark on Xaero's minimap and world map as one of the minimap's
  third-party waypoints (never saved, never in the player's own sets), by reflection: without Xaero, or if its API
  moves, it quietly does nothing.

`/solo village find|clear|info` marks the nearest zone now, takes the mark away, or says where it is.

## Languages

The System ships in English (`en_us.json`) and Spanish (`es_es.json`) in every asset namespace (`beyondsolo`,
`beyondsolo_jobs`, `beyondsolo_armory`, ...). Minecraft never falls back from one Spanish to another, so the build
(`generateSpanishVariants` in `build.gradle`) copies each `es_es.json` to `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy`,
`es_ve` and `esan`: one neutral Spanish for Spain and Latin America. Edit `es_es.json` only.

- **Adding text:** add the English line, then the Spanish line with the same key and the same `%s` slots. The word list
  and tone (tú, Title Case names, "Nv.", "Rango %s", "%s de daño") are in `docs/TRANSLATING.md`.
  `test/TranslationGameTests` fails the build's game tests when an English line has no Spanish, the arguments differ,
  or a copy is missing, and names the file and keys.
- **Word order:** Spanish puts most adjectives after the noun, so anything built from parts has a key that orders them:
  `tooltip.beyondsolo.grade_kind` ("Rare Dagger" / "Daga de grado Raro"), `enemy.beyondsolo.named` (affinity +
  creature), `item.beyondsolo.core.with_affinity`. Use `%2$s %1$s` in a translation to swap two arguments.
- **Text made on the server** (pop-ups, chat, item names, entity names) travels as a `Component.translatable(...)` so
  each player reads it in their own language. Don't call `getString()` on the server and send the result: on a
  dedicated server that is always English. Shadow names are stored as data, not text (`ShadowNames`: an invented name
  plus an epithet id, `"Vhalgrim|unbroken"`), and the client turns them into words.
- Left in English on purpose: the `/system ...` testing commands' feedback (`/solo` is translated), config comments, the dev UI gallery, and
  invented proper names (shadow names, roman numerals).

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
blood pop through armor, the pop taking at most 5% from a boss or an elite (and no more than its share however hard it
was pushed), buildup on players, weapon hits, resistances, armor set bonuses, stats feeding combat,
exhaustion resistance, and that every weapon has a Better Combat file. `test/QuestGameTests` covers: quests after the
evaluation, kill progress and rewards, the quest line moving on, rank scaling, the spawner trigger, area objectives,
timeouts and penalties, fleeing, offers, inventory and level objectives, the daily at dawn, death, saving, custom objectives.
`test/DungeonGameTests` covers: every realm in every shape planning a whole dungeon over many seeds (same seed same
plan, one entrance, one boss room and the biggest, nothing overlapping, everything reachable through passages or open
spaces, inside the rock and its slot), the original caves coming out exactly as before realms (fingerprints of the
old planner's layouts), traps going where they work (lockdowns only in rooms that shut, a forced trap finding a room
in every dungeon), gates picking every realm of their rank and every shape of their realm, realms favouring
their bosses and their creatures (three to five kinds, one up close, two at home in the realm, its ruling affinity),
realm and shape being saved (and old caves staying classic), and a dungeon of each shape and of each realm carved,
walked along every passage, walked on foot from the entrance to every room (stepping, jumping a block, dropping no
more than a harmless fall) with a loose rockfall ceiling over every cavern, its hidden chamber sealed off on foot behind a wall that passes for the realm's own and
walkable once the wall is down, and wiped back to rock to the last block; the same for the awakening's sealed door
behind the boss of a dungeon of each shape. A failed walk draws a map of what was reached. Set `EXPLORE` to carve every realm in every one of its shapes (about 80 more dungeons, several minutes).
`test/GateGameTests` covers: the classic cave plan (same seed same cave, spacing, rooms per rank), gate ranks being fully random (every rank turns up, E far more often than S, the weights obeyed), a placed gate registering, carving the cave with a dormant boss, the boss's defeat opening the exit and dropping the cache, entering starting one gate quest and leaving, Trial of the Depths needing a gate, gate monsters' scaling and element, loot by rank, and a closed gate collapsing.
`test/AwakeningGameTests` covers: new players have no System and the crystal ignores them, evaluation after the System is bound, the first D-rank gate (not an E-rank one, not one walked into by someone with the System) hiding the sealed door past its boss's room (laid out as caverns if its own shape has no room there) and its clear calling only players without the System, the door's chamber fitting behind nearly every boss and the temple being short, the whole road (the wall ignoring knocks, breaking once the boss falls, the door in the chamber, the exit turning them back, the door shutting behind them, the tenets, no creatures in the temple, waking in front of the first gate with everything they carried), the sealed gate being personal, the trial holding a dormant Arbiter at the bottom, a fall being refused then granting the System with the inventory intact, slaying the Arbiter (System and title), a fall in the middle of the Arbiter's attack tick ending the fight cleanly (the 0.1.0 crash), and the legendary roll.
`test/NpcGameTests` covers: board slots and the daily refresh, gather hand-in, rank scaling, the 3-request limit,
hunt report-back, right-click opening the window, failing and being refused, the giver dying, escort arrival and
death, parcel delivery, and the nest spawning. Fake players can't take damage, so damage
checks use zombies.
`test/SkillGameTests` covers: starter skills by class, rank moving unlocks earlier, level unlocks and passives, mana and cooldowns, refusing while collapsed, the Rending Arc cone, power scaling, proficiency, passive attributes, tomes, Ember Burst and Hunter's Mark, stealth, Shadowstep, the healer's skills, the job change hooks, the bar and saving, and that every skill has its text and element. Second batch: 8 skills per class, combo chains, the reaction table, water then lightning, air feeding fire, every new skill casting, and the new passives.
`test/ShadowGameTests` covers: only necromancers leave souls, the starting steed, extraction raising a shadow that never fights its master, three refusals crumbling a soul, boss souls becoming named elites, levels and ranks up to marshal, roles from tags, summon, recall and a fallen shadow recovering, and saving the army.
`test/EconomyGameTests` covers: gold from monsters only with the System, buying, rank and gold refusals, daily limits, storage size by rank and expansions (doubling price, maximum), locked slots and pages, shift-click, stack in and sort, saving, selling by grade and sell-all, that buying to sell never pays, the license being issued and kept up to date (and not rewritten by others), and gate clear gold.
`test/GearGameTests` covers: rolls by grade, attributes from stats and enhancement, refine odds and downgrades, the forge charging, refusing and the ward, infusion reactions, 2- and 4-piece set bonuses and Unbroken, boss signature tables (and the brute's set pieces moving to the armory), sell value against refine cost, and boss sets carrying their element.
`test/CraftingGameTests` covers: elites leaving cores with their affinity and material, core chances for ordinary and wild monsters, boss loot gaining a boss core, crafting from inventory then storage with gold, refusing rank/materials/gold without taking anything, plain cores used before affinity cores, infusion crystals needing their affinity, forged gear rolling stats, potions and mana stones, veins dropping their stones, sell prices and every recipe being whole.
`test/PenaltyGameTests` covers: a failed or abandoned daily owing a sentence instead of debuffs, the sentence waiting until the player is free, no zone without the System, the whole sentence (pulled in, the pillar and red ground, creatures rising inside the edge, time running out, home with the sentence served), falling (death cancelled, home collapsed and weak), surges and eruptions, and the daily training's four drills and its full restore.
`test/RuneGameTests` covers: the rune arts being a family of their own (one per creature and boss, two deep runes), a class change keeping them, absorbing teaching then raising, every refusal (no System, rank, class, mastered), sealed stones settling on something that fits, every rune art acting, the guarding ones against blunt blows, engravings (refusals, haste, thrift replacing it, the cast paying the thrifty price), might hitting 1.2 times as hard, drop rules, and selling, crafting and the shop.
`test/KeyGameTests` covers: a held key opening a keyed gate three steps ahead (facing its holder, going home to where it turned, used up, counted), refusals (rank, no System, no room, one at a time, waiting gate), a menu turn taking the same time and walking off stopping it, a cleared cave chaining the next run home to the first turn with the bonus (and the keyed quest paying no stat points), doors sealing behind the party and closing when everyone is out, an unused gate giving its key back seal and all (and an ascendant key's boss coming crowned), a gate that has closed and gone freeing its spot for the next key, records and timing, the re-run prompt's choice of key, boss drop rates, the shop, sell prices and forging, and the wiper putting rock back, removing what was left, forgetting the gate and handing its slot to the next one.
`test/DepthsGameTests` (in the original cave of each gate's rank, so every run tests the same) covers: creature profiles (a blaze immune to fire and drowning, the undead, an affinity on top, shared entries), skill hits landing by rating with their tag and the weakness noted once, weather rolls (same seed same sky, still caves by rank, every condition, the theme's lean), element shifts and the reward bonus, saving the depths and old caves loading without them, hidden chambers being secluded and leaving the rest of the cave as it was, a vault walled off and knocked open, the shrine blessing once, a lair paying when its guardians fall, a trap core shattering and disarming its room, a lockdown sealing the room until both waves fall, and a storm bolt striking where it was marked.
`test/RedGateGameTests` covers: about one gate in sixteen waiting to turn red (always the same answer; never a trial or a keyed gate; a defused one never, a forced one always), what waits inside being one or two ranks up with a master of that rank and only our own frost-touched creatures, the payload's round trip, someone without the System defusing it for good, entering (the gate turning red, arriving by the red way out with the emergency quest, the break clock held, sealing, the way out and the gate turning hunters back, ending), the master's fall (the seal broken, a red cache, the clear paid and on the record, the way out letting them go, the gate closing), the clock running faster, nights bringing raids of angrier creatures after the hunter that go at dawn, the cold biting in the open at night and a fire warming, and the last hunter falling (quest failed, the red fading, an ordinary gate at its last minute before it breaks).
`test/PartyGameTests` covers: an accepted invitation forming a party (and every refusal, declining, invitations being used up), the lead handed on and a party of one ending (leaving, removing, joining another party), parties saved and a damaged save cleaned up, the payloads' round trip, kills shared with a party member nearby (gold and XP; none for a stranger or from far away), no friendly fire (blows, arrows, pets; again once the party is over), a gate holding for a party member on the way and a keyed gate's door shutting once the wait is over, regrouping (every refusal, the gathering broken by a step, landing beside them, the cooldown), new players landing beside the first, a friend without the System still finding their door, and a party going through one sealed door together (one door, its leader's, letting both through and nobody else, shutting behind the last).
`test/SituationGameTests` covers: surviving a far stronger creature turning into Annihilate in the same slot when it
breaks and paying more, fleeing making it withdraw and come back later as a stronger Nemesis (and its death revealing a
secret), the last stand paying on the spot and turning into a second wind, resting being broken by a blow and healing
when quiet (and the gap and one-calm-at-a-time rules), the Untouchable streak moving up a tier and keeping what it paid
when a hit breaks it, a Frenzy fading after a tier counting as done, saving a villager (emeralds, gossip) and hunting
down its killer, a party member's kill of the target counting as the hunter's own, secret quests revealed once (their
titles kept even with another one worn, and no secret for an ordinary kill), and follow-ups waiting their turn and
being retried.
`test/TranslationGameTests` covers: every English line of every namespace having Spanish with the same arguments,
the Latin American copies matching it, and the argument check itself (reordered `%1$s`, `%%`, a stray `%`).
`test/ArmoryGameTests` covers: a set for every class at every rank and a divine one each (counts, levels, grades, sources, perks), level and class gates (refusing, putting back, creative, bound divines), only the set's class drawing out bonuses (spell power, mana efficiency, skill haste, caps), slow drops from creatures, bosses, divine rolls and village requests, set perks changing hits (Tower Guard, Dragon's Blood, Warlord's Fury), and the six divine weapon passives.
`test/BreakGameTests` covers: the clock running through its phases (and saving), waiting while hunters are inside and holding when nobody watches, cleared and trial gates never breaking, waves and the boss coming through with the third, the boss's fall suppressing the break (gate closed, the rest gone, the cache), the System's break quest (forced up to a rank above you, offered beyond that) and its reward, strays pulled back to the gate, Pulse Scan sensing gates in range (nearest first, closed and others' sealed gates left out), and vanilla particles kept out of the camera.
`test/ChronicleGameTests` covers: titles a feature gives being kept and worn (a rarer one replacing one the System
picked, one the hunter picked staying on, taking it off), feats giving their titles at the goal, the title table
(categories, goals, perks), perks following the worn title (attributes on and off, damage to a family, System XP,
gold, damage taken), the journal filling in from kills (counted by family) and a boss's forms, every creature and
boss having a page, the rankings holding every rival and the player with every board sent, and every title, perk,
family, guild and page having its English line.
`test/TowerGameTests` covers: the floor table (kinds, wings, ranks, keepers, checkpoints, the spire's scaling, every name written in English and Spanish), the rush tiers (every boss once, the last ascended, the Sovereign rush and the lord, who may enter which), the records saving, the key (sold from D rank, binding once, every refusal at the door), a climb (the pull, the first hall's creatures being the System's own, the seal off the stair, the next hall on the other pad, out by the exit sigil), a gatekeeper (dormant and unharmed until woken, its cache and the checkpoint), the lord (untouchable until he has risen, the conquest, the Sigil by pity, the title), the spire's record and a fall (death cancelled, home weakened, the record kept), a whole E-rank boss rush with its breathers and clock, and each of the lord's moves landing, his enrage and the blood moon.
`test/GuildGameTests` covers: founding a guild (every refusal: no System, gold, name and tag rules, taken names and tags, one guild at a time; the tidied name, the founder's title, nothing paid for a refusal, the treasury going to the master on disbanding), members (invitations sent, again and used up, declining, officers inviting and removing only plain members, the lead handed on, a master unable to walk out by joining another guild while a member can, a leaving master's successor, the last one out ending it), the treasury and levels (deposits counting as guild XP, the master's withdrawals, several levels at once, what levels give, a member's gate clear, the top level), hiring (the day's recruits, ranks and prices, the roster's cap, hurt hunters staying home, what a raid teaches them, letting go), a guild saved and loaded, what the GUILD page is sent, `/solo wipe`, the raid permit's refusals, and the `/solo guild` and `/solo raid` commands.
`test/RaidGameTests` covers: the muster (only hunters with the System, roles by class, the leader, the countdown, one raid at a time, the lead passing on, the gate sealing once it starts, a failed raid shutting its gate), a raid's cave (lieutenants in their caverns sealing the boss's chamber and counted by the raid's quest, none of them clearing the gate, the boss grown and controlled, its barrier and its pylons breaking only to raiders' blows one at a time, the boss reeling, the cataclysm's lights, the clear and its titles), a guild's raid (the leader bringing its hunters, their bodies stepping in beside the leader in their roles, the light sheltering one and the open floor hurting the other, the best raider of four singled out, the guild's XP, the hunters' levels), strikers hitting harder and vanguards taking less only while a raid runs, a raid waiting while its raiders are offline (and failing once they have all fallen), a raid's quest ending with it (for raiders away too, once they are back), and a raid saved with its gate.
`test/MonarchGameTests` covers: a crown (its powers, the strength, the title, the aura's colour), powers only with a crown (refused when not crowned, never on the skill bar, never from "every skill"), the throne calling a ready hunter (sigil, job change, level), a monarch's shadows becoming regents and the army growing, the crown form never making a monarch untouchable, the domain and dread on the fallen court (allies sheltered and stronger, foes weakened and taking more), each crown's authority striking the court, the whole throne trial run to the end (court, Arbiter, the choice, the coronation, home, titles, the quest), jumping straight to the throne, falling in the hall, logging out at the throne (crowned) or in the court (not), and the `/solo monarch` commands.
`test/LightGameTests` covers: the temple hiding the chapel (a second room, walled off), turning the Envoy away and coming back to swear (the light's awakening, the System, Judgement on a crown key, the title, evaluation keeping it), the powers coming only from the light (no class lists them, the crown keys' order, refused when not sworn), Judgement marking and striking, the sanctum's might, guard and burning, seraph form never making a ruler untouchable, the Blade of Heaven falling, the light calling the ready (the feather, the quest, once), the whole vigil to a mantle, leaving the ring failing it, a mantle or a crown never both, and every model's geometry, skin, glow and animations.
`test/WorldBossGameTests` covers: every kind's model, skins and each animation its moves play, every name the HUD and pop-ups ask for (names, titles, phases, domains, authorities, regalia), a phase not skipped by one huge blow (it stops at the line, the phase begins, nothing heals it), a monarch dying in its last phase, every kind reading far stronger than a new S-rank hunter, monarch armor taking its wearer's aura (grade, tier, level, the violet when unworn or classless), each monarch leaving its own regalia (the missing piece first), and every kind's journal page and kill titles (the rarest worn).
`test/RewardBoxGameTests` covers: a plain box opening into one thing and being used up (only for a hunter with the System, one a second), a blessed box showing the same three offers until one is taken and giving that one (refusing another seed, hand or offer), every roll keeping its promises (three kinds, nothing empty, a seed rolling the same, cursed boxes never the small stuff), the Wardens holding the box's things until the last one falls or taking them away, Hex of Frailty until purified, Greed and Backlash, caches holding boxes by where they stand, the daily training's box, and `/solo box`.
`test/ManaPoisonGameTests` covers: the stages weakening and slowing, only A and S gates being thick enough, the rate by rank in an A gate (a rank below, at the gate's rank, above it) and the mana thinning once the boss is down, purifying crystals growing in rooms of their own and drawing the poisoning out, the burst at 100, poisoned blows losing their share, the purifying draught, and `/solo mana`.
`test/IsleGameTests` covers: the island laid out soundly for any world (nests on land and apart, the camp and its whole circle of light on land, the chamber under the island with the dais, the way in and the cocoon in it), the Association calling only awakened hunters of its rank (a call nobody takes closing, an operator's call reaching any rank, an answer taken back), a whole raid from the call to home (the landing, a heart's blows, the hive opening, down the pit to the queen, the king's birth, the island retaken and every reward), a hunter carried off instead of dying while the raid goes on with its allies (and lost when nobody is left), the island holding, and its titles.
`test/LongSleepGameTests` covers: her bed being the village's nearest free one (not slept in, not another hunter's mother's) or one made near the crystal, her put in it near home (asleep, her look chosen) and put back when she is gone, nothing hurting her, the visit at level 5 completed by its scene (a story quest pointing at her bed that can't be given up), the cure found at level 25 and a lost cure quest given again with no soul lost, souls, dew and the chalice only while the cure is sought and the elixir brewed from them (the waking quest after), the elixir waking her (sitting up that day, the title and stat points, up beside her bed the next), her stew once a day and Home Comfort, a stranger unable to wake her, and `/solo story`.
