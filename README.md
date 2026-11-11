# Beyond Solo Journey

A Minecraft 1.21.1 NeoForge mod and modpack: an RPG "System" with ranks, stats, quests and gate dungeons.

## What's in the game so far

- **Random first spawn.** New players wake up 800-3000 blocks from world spawn with one objective: find a village.
- **Evaluation Zone.** Villages (plains, desert, savanna, snowy, taiga) can generate a small stone pavilion with an
  Evaluation Crystal. Touch it, stay close for 4 seconds, and the System assigns a **rank (E to S)** and a **class**
  (Warrior, Assassin, Mage, Guardian, Healer, Ranger). Higher ranks start with more bonus stats.
- **System window** (default key **V**): Status, Quests and Log tabs, styled like a holographic window.
- **Stats that do something:**
  - STR: melee damage, crit damage, knockback resistance
  - AGI: move speed, attack speed, dodge chance, bow draw speed
  - VIT: max health, natural armor and toughness, healing received, faster fatigue recovery
  - INT: max mana, mana regen, cooldown reduction
  - SENSE: crit chance, luck, reach
- **Levels and XP** from kills. Each level gives 3 stat points and fully restores you.
- **Pop-up notifications** in three modes (key **N** to cycle, **B** to dismiss):
  floating in the world next to you, pinned to the top of the screen, or hidden with an unread badge.
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

Admin commands for testing (op level 2): `/system awaken [rank] [class]`, `/system reset`, `/system xp <n>`,
`/system points <n>`, `/system rank <rank>`, `/system stat <stat> <value>`, `/system exhaustion <percent>`,
`/system notify <text>`.

Config: `config/beyondsolo-common.toml` (spawn distance, Evaluation Zone weight, XP, exhaustion rates) and
`config/beyondsolo-client.toml` (pop-up mode, HUD, sounds).

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
