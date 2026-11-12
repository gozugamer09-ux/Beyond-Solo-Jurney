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
| `combat` | `DamageElement` list and the buildup framework (`Buildups`, `BuildupType`) |
| `client` | Key bindings, client config, the theme colours (`SystemTheme`) |
| `client.gfx` | The drawing toolkit every System screen uses (see "Drawing the System UI") |
| `client.screen`, `client.hud`, `client.notify` | System window and tabs, status HUD, pop-ups |
| `client.evaluation` | The on-screen evaluation: scan read-out and the rank / class / stats reveal |
| `client.render` | World rendering: the crystal's renderer, render types, client-only effects |
| `command` | `/system` admin commands |
| `test` | Game tests (`./gradlew runGameTestServer`) |
| `quest`, `npc`, `gate` | Reserved for the next modules |

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

### Quest engine (set quests + surprise quests)
- Rewards: `SystemAPI.grantXp(player, amount, yourQuestId)`, `addStatPoints`, `setTitle`, `restoreMana`.
- Surprise quest pop-up: `Notifications.send` with `Kind.QUEST` (or `ALERT` for emergencies).
- UI: replace the placeholder Quests tab with `SystemTabs.register(SystemTabs.QUESTS, 100, YourQuestTab::new)`.
- Story steps: `SystemAPI.setObjective(player, id)` with lang keys `objective.beyondsolo.<id>` and `.desc`.
- `ServerScheduler.schedule(ticks, runnable)` for delayed checks.

### NPC quests
- Scale quest difficulty and rewards by `SystemAPI.get(player).rank().tier()` (0 = E ... 5 = S) and `level()`.

### Gates and dungeons
- Use rank tier for gate rank, `grantXp` for clear rewards, pop-ups for "gate opened / cleared".
- GeckoLib is already a dependency for animated gate and boss models.

### Combat (damage types, status, armor and weapons)
- Elements live in `combat/DamageElement` (physical, fire, poison, crimson, blood). Matching damage types are in
  `data/beyondsolo/damage_type/`.
- Status buildups: `Buildups.register(new BuildupType(id, element, threshold, decayPerSecond, resetOnTrigger, onTrigger))`
  then `Buildups.add(target, type, amount, source)` from hits. Works on players and mobs.
- Exhaustion: `SystemAPI.addExhaustion(player, amount)` for new fatigue sources; collapse already handles the lockout.
- Stat-driven bonuses: `StatEffects.register(name, attribute, operation, data -> amount)`.
- Mana: `SystemAPI.consumeMana(player, cost)` returns false if the player can't pay.

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

## Tests

`test/SystemGameTests` covers: the structure has its crystal, evaluation by the crystal, awakening hands out bonus
stats and buffs, XP levels up and stat points raise attributes, no XP before evaluation, exhaustion collapse and
recovery, and the blood buildup pop.
