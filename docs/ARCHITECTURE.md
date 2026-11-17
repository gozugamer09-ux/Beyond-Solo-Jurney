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
| `armory` | The class armor sets: the catalogue (`ArmorCatalog`), their items and ARMORY tab (`ArmoryModule`), set perks (`SetPerks`), the divine weapons' passives (`DivinePassives`), drop rules (`ArmorDrops`), the level and class checks and set pop-ups (`ArmoryEvents`) (see "The armory: class armor sets") |
| `crafting`, `client.crafting` | Monster cores, materials, mana stones and veins, System potions, recipes and the CRAFT tab (see "Cores, materials, potions and crafting") |
| `penalty`, `client.penalty` | The Penalty Zone: sentences for a failed daily training, the Wastes, the survival, its overlay and sky (see "The Penalty Zone and the daily training") |
| `rune`, `client.rune` | Rune stones: inscribed and sealed stones, absorbing, engraving runes, drops, recipes, the stone's composite model, the absorb on screen and in the world (see "Rune stones"); the rune arts themselves are in `skill/RuneArts` |
| `key`, `client.key` | Dungeon keys: turning a key to open a gate on demand, keyed runs (doors that seal, refunds, chains, the run clock and records), drops, recipes, the shop, the GATES tab, the lock of light (see "Dungeon keys and re-runs"); `gate/GateWiper` recycles closed caves |
| `depths`, `client.depths` | What lies off the beaten path of a gate's cave: hidden chambers behind disguised walls, trap rooms, the weather inside a gate, and finding creatures' weaknesses; the creature profiles themselves are `enemy/ElementalProfile` (see "Hidden chambers, traps, weaknesses and gate weather") |
| `breaks`, `client.breaks` | Dungeon breaks: the collapse timer on every uncleared gate, the break itself (waves, the boss coming through, suppression), and gate sensing in Pulse Scan; the break's banners, timer chip, gate compass and world effects (see "Dungeon breaks and gate sensing") |
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
  run it alone after touching a weapon texture). Instead of `item/generated` it builds each weapon as a front and a
  back face plus side walls coloured from the material just inside the outline (`textures/item/held/<name>_side.png`),
  walls drawn first and faces after them so the grade glow blends over the edges. `PROFILES` sets each Better Combat
  kind's size in the hand, in first person and on the ground; the inventory icon stays the flat sprite.
  - Two-handed kinds (claymores, katana, spears, glaive, halberd, hammers, heavy axe, scythes) hold the weapon forward
    at rest: `tools/weapon_stances.py` fits both arms to a forward-pointing weapon and writes the result as idle
    poses (`player_animations/pose_<stance>.json`), which the weapon's Better Combat file names as `attributes.pose`
    on top of its parent. Poses only cover standing and walking; attacks still use Better Combat's swings.
  - Bows have two holds: carried upright at the side at rest, vanilla's drawing hold while pulling.
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

`/system gate spawn [rank] [realm|any] [shape]|near|enter|boss|wake|kill|info|cache [rank]|list` for testing: `spawn`
opens a gate into a realm and shape of your choice, `boss` takes you to the door of the boss's room, `info` names the
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
| `CALLED` | Set by `AwakeningService.gateCleared` when the boss of the cave that hides their door falls with them inside. `REVEAL_DELAY` later (the boss has crumbled, the exit is not open yet) `revealDoors` breaks the wall (no HIDDEN DUNGEON moment, no XP: knocks and Pulse Scan get nothing from this wall) and opens a sealed gate (`GateService.spawnSealedGate`, a `GateInstance` with an `owner`, theme `GateTheme.TRIAL`, `temple` set) for each called player in the chamber (`DepthsBuild.sealedDoor`), with `trialHome` set to the cave's own home. While it stands the cave's exit turns them back (`heldBack`, checked in `GateService.beginExit`). Out of the gate depths without going through (they fell, logged out and the cave closed), the call lapses back to `DORMANT` (`tether`, `leftGate`); a door opened out in the world (`/system trial call` outside a cave, old saves) still pulls them back past `awakening.tetherRadius`. Only the owner can enter |
| `TRIAL` | Inside. The door shuts behind them (`arrived`). A temple trial (`GateInstance.temple`, every trial since 0.1.1) is planned by `DungeonLayout.temple`: the way in, an antechamber with the Arbiter's three tenets on a stele (`DungeonDecor.tenets`, signs read from `trial.beyondsolo.tenet.*`), the hall; no side caves, and no trial wakes packs (`GateService.watch`). Every player death in a trial is cancelled (`LivingDeathEvent`, highest priority), so nothing is dropped: before the Arbiter's hall they stand up again at the last room reached (`GateInstance.checkpoint`), in the hall the awakening begins |
| `AWAKENING` | `begin` freezes the player and plays `AwakeningPayload.AWAKEN` on their client (14.6 s). The server follows the same clock: out of the cave at `TELEPORT_TICK` (the screen is black; to `trialHome`, in front of the gate they first went into, or for a door out in the world, in front of it), System bound at `GRANT_TICK`, released at `END_TICK`, then `FirstSpawnHandler.systemIntro` (welcome pop-up, "find a village"). Logging out halfway finishes it at once |
| `DONE` | Has the System. The evaluation follows as before |

The Hollow Arbiter (`ArbiterEntity`, a `DungeonBoss` like `GateBossEntity`, so the boss bar and intro work for both)
kneels dormant and invulnerable until its challenger is well inside the hall, rises for `INTRO_TICKS`, then uses
three attacks: a cleave across everything in front of it, a sunder that sends a ring along the floor (jump it), and
judgment, marks of light under each player that burst a moment later. Under half health it is unbound: faster,
double rings, three marks. A strike that fells its challenger ends the fight in the middle of an attack
(`passVerdict` clears every ring and mark), so the attacks walk copies and stop at once (`fighting()`); walking the
lists themselves crashed the server in the 0.1.0 playtest. 1500 health, heavy armour, arrows do little. Killing it (`arbiterSlain`) drops a cache
that holds one `ModItems.LEGENDARIES` weapon (`LegendaryWeaponItem`), then the awakening plays with
`arbiterSlain` set, which also gives the title `title.beyondsolo.arbiter_slayer`.

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
  wisps), storm flashes with a far rumble, and the condition's banner and chip under the run clock.
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

## World generation

`evaluation/VillageInjector` adds `beyondsolo:evaluation_zone` to the five vanilla village house pools when the
server starts (weight from config, default 3). In testing, 7 of 8 plains villages got at least one zone. A big
village can get more than one (a savanna village in testing had three). They all work the same.
The structure file is generated by `tools/gen_evaluation_zone.py`; you can also rebuild it in game and save it with
a structure block. Lithostitched is available if we want datapack-driven injection for modded villages later.

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
- Left in English on purpose: operator `/system ...` command feedback, config comments, the dev UI gallery, and
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
`test/TranslationGameTests` covers: every English line of every namespace having Spanish with the same arguments,
the Latin American copies matching it, and the argument check itself (reordered `%1$s`, `%%`, a stray `%`).
`test/ArmoryGameTests` covers: a set for every class at every rank and a divine one each (counts, levels, grades, sources, perks), level and class gates (refusing, putting back, creative, bound divines), only the set's class drawing out bonuses (spell power, mana efficiency, skill haste, caps), slow drops from creatures, bosses, divine rolls and village requests, set perks changing hits (Tower Guard, Dragon's Blood, Warlord's Fury), and the six divine weapon passives.
`test/BreakGameTests` covers: the clock running through its phases (and saving), waiting while hunters are inside and holding when nobody watches, cleared and trial gates never breaking, waves and the boss coming through with the third, the boss's fall suppressing the break (gate closed, the rest gone, the cache), the System's break quest (forced up to a rank above you, offered beyond that) and its reward, strays pulled back to the gate, Pulse Scan sensing gates in range (nearest first, closed and others' sealed gates left out), and vanilla particles kept out of the camera.
