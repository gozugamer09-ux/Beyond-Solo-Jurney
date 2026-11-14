# Beyond Solo Journey

A Minecraft 1.21.1 NeoForge mod and modpack: an RPG "System" with ranks, stats, quests and gate dungeons.

## What's in the game so far

- **Random first spawn.** New players wake up 800-3000 blocks from world spawn with one objective: find a village.
- **Evaluation Zone.** Villages (plains, desert, savanna, snowy, taiga) can generate a small stone pavilion with an
  Evaluation Crystal: a shard of light floating over a magic circle. Touch it and stay close while it scans you
  (5 seconds), then the reveal plays on your screen: the **rank (E to S)** is rolled and stamped in, then the
  **class** (Warrior, Assassin, Mage, Guardian, Healer, Ranger), then your starting stats. The crystal itself puts
  on a show for everyone nearby and takes the colour of your rank. Higher ranks start with more bonus stats and get
  a bigger moment (an S rank calls down lightning). The reveal takes about 14 seconds; **B** skips it.
- **System window** (default key **V**): Status, Quests and Log tabs, drawn as a smooth glowing glass panel with its
  own typeface and icons.
- **Stats that do something:**
  - STR: melee damage, crit damage, knockback resistance
  - AGI: move speed, attack speed, dodge chance, bow draw speed
  - VIT: max health, natural armor and toughness, healing received, faster fatigue recovery
  - INT: max mana, mana regen, cooldown reduction
  - SENSE: crit chance, luck, reach
- **Levels and XP** from kills. Each level gives 3 stat points and fully restores you.
- **Pop-up notifications** in three modes (key **N** to cycle): floating in the world next to you, pinned to the
  top of the screen, or hidden with an unread badge. **B** hides whatever is on screen, and pressed again brings the
  latest messages back. They fade out on their own unless you turn on "stay open" in the Log tab.
- **Exhaustion.** Sprinting and fighting fill a fatigue meter, resting drains it. At 100% you collapse and can't
  move, attack, mine or use items for 30-45 seconds, until you sleep, or until you die.
- **Buildup framework** for elemental status (ships with Blood: 100 buildup pops for 10% of max health, both ways).

## Building and running

Needs Java 21.

```
./gradlew build              # jar ends up in build/libs
./gradlew runClient          # play in a dev client
./gradlew runServer          # dev server in runs/server
./gradlew runGameTestServer  # automated in-game tests
```

Admin commands for testing (op level 2): `/system evaluate [rank] [class]` (plays the whole evaluation, on the
nearest crystal if there is one), `/system awaken [rank] [class]` (same result, no show), `/system reset`,
`/system xp <n>`, `/system points <n>`, `/system rank <rank>`, `/system stat <stat> <value>`,
`/system exhaustion <percent>`, `/system notify <text>`.

Config: `config/beyondsolo-common.toml` (spawn distance, Evaluation Zone weight, announcing evaluations in chat, XP,
exhaustion rates) and `config/beyondsolo-client.toml` (pop-up mode, stay-open pop-ups, HUD, sounds, System typeface
on or off).

If the dev client stops on a red error screen that names Prickle ("... not a subtype"), start it again. That
library sometimes loses a start-up race when it runs from Gradle, where every mod is also on the plain class path.
A normal installation loads mods differently, so it should not be affected, but that has not been tested yet.

## Library mods

Versions live in `gradle.properties`, repositories and dependency lines in `build.gradle`.

| Mod | Why |
| --- | --- |
| GeckoLib | animated models for gates, bosses, armor, weapons |
| Curios | extra equipment slots |
| Apothic Attributes (+ Placebo) | crit, dodge, cooldown and other attributes the stats feed |
| AttributeFix (+ Bookshelf, Prickle) | lifts vanilla attribute caps so high stats keep working |
| Lithostitched | datapack structure/worldgen edits for modded villages |
| JEI, Jade | compile-only for now; recipe and tooltip plugins later |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code is laid out and where future systems plug in.

## Credits

- Typeface: [Exo 2](https://github.com/NDISCOVER/Exo-2.0) by the Exo 2 Project Authors, SIL Open Font License 1.1.
- Icons: [Phosphor Icons](https://phosphoricons.com), MIT License.

Both licence texts ship inside the jar in `licenses/`. Everything else (the crystal, the magic circle, the rank
emblems, the effects) is drawn by code in this repository.
