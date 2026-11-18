# Beyond Solo Journey

A Minecraft 1.21.1 NeoForge mod and modpack: an RPG "System" with ranks, stats, quests and gate dungeons.

## What's in the game so far

- **You start with nothing.** No System, no windows, no quests: an ordinary player in an ordinary world, with gates
  tearing open around you. Low gates (rank D) open near players who have no System yet.
- **The awakening trial.** The first gate of rank D or higher you walk into without the System hides a **sealed
  door**: a chamber past the boss's hall, walled off with rock that looks like the rest of the cave. Knocking gets you
  nothing. When the boss falls, the wall breaks by itself and a sealed gate opens in the chamber, and the cave's way
  out won't take you while it stands. Behind it lies the **Ashen Threshold**, a temple: a short way down past a stele
  with the Arbiter's three tenets to the hall of the **Hollow Arbiter**, a kneeling, faceless colossus in chains, and
  nothing else alive in there. The door shuts behind you. It cannot reasonably be beaten. When it strikes you down you
  do not die and you lose nothing: the screen goes dark, the first System window opens in the dark, and you wake in
  front of the gate you first went into, with **the System**. That is where the game begins.
- **The hidden way.** The Arbiter can be killed. Whoever manages it still receives the System, with a title, and
  its cache holds one of four **legendary weapons** (Sunderveil, Ashen Oath, Nightglass Fang, Thorn Requiem),
  unbreakable and far beyond anything else in the game.
- **Random first spawn.** New players wake up 800-3000 blocks from world spawn. Once the System is theirs it gives
  them one objective: find a village.
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
  - AGI: move speed, attack speed, dodge chance, bow draw speed, longer and faster-recharging combat rolls
    (an extra roll every 25 points)
  - VIT: max health, natural armor and toughness, healing received, faster fatigue recovery, resistance to every
    element and to exhaustion
  - INT: max mana, mana regen, cooldown reduction, stronger elemental hits
  - SENSE: crit chance, luck, reach
- **Levels and XP** from kills. Each level gives 3 stat points and fully restores you.
- **Pop-up notifications** in three modes (key **N** to cycle): floating in the world next to you, pinned to the
  top of the screen, or hidden with an unread badge. **B** hides whatever is on screen, and pressed again brings the
  latest messages back. They fade out on their own unless you turn on "stay open" in the Log tab.
- **Exhaustion.** Sprinting and fighting fill a fatigue meter, resting drains it. At 100% you collapse and can't
  move, attack, mine or use items for 30-45 seconds, until you sleep, or until you die.
- **Combat** with [Better Combat](https://modrinth.com/mod/better-combat) (attack animations, combos, weapon reach)
  and [Combat Roll](https://modrinth.com/mod/combat-roll) (dodge roll, default key **R**). Every swing and roll costs
  exhaustion; heavy weapons and heavy armor cost more.
- **Weapons:** Hunter's Dagger, Knight's Blade, Steel Greatsword, Hunting Spear (blood), and the Duskforged
  elemental weapons Emberbrand (fire), Venom Fang (poison), Crimson Reaper (crimson) and Bloodletter (blood). Each
  one has a Better Combat moveset (dagger, sword, claymore, spear, scythe, katana).
- **The arsenal and item grades:** 39 more weapons from **common** to **divine** (daggers, longswords, rapiers,
  greatswords, glaives, halberds, great axes, maces, warhammers, gauntlets, claws, staffs and bows). Grades colour the
  name and tooltip, glow in inventory slots and throw a beam of light over dropped gear. Weapons carry traits
  (frostbite and freezing, chain lightning, armor breaker, lifesteal, executioner, mending, rhythm, bulwark,
  backstab, twin, split shot). Three **mythic** weapons and six **divine** weapons (one bound to each class) have a
  right-click signature art, such as Absolute Zero, Heaven's Verdict, Cataclysm Cleave or Dragon's Wrath. Staffs add
  spell power to skill spells. Gate caches roll weapon grades by the gate's rank.
- **Armor sets:** Hunter (light, less exhaustion), Knight (heavy plate, blood resistance), Duskforged (fire, poison
  and crimson resistance). Wearing all four pieces adds a set bonus.
- **The armory: 45 class armor sets.** Every class has a set at every gate rank and a divine one: plain starter
  sets from village requests, and sets of every gate creature and boss (Gloomstalker, Shardshell, Hollow Vanguard,
  Sigilweaver, Brood Empress, Wyrmlord and more). Two pieces give a small buff, four a little more and a perk of
  their own (Tower Guard, Pounce, Storm Sigil, Saint's Grace, Dragon's Blood...), but only for the class the set is
  made for. Each set needs a level (D 10 up to S 50, divine 60) and pieces drop slowly: a creature leaves one a few
  times in a thousand, a boss about every other kill, a divine piece once in a hundred S-rank bosses. Divine sets
  are bound to their class, and each divine weapon has a passive of its own (Ruin, Vanishing Edge, Insatiable,
  Unyielding, Rootbound Life, Astral Quiver).
- **Elements.** Hits fill an element's buildup on the target; when it is full, something happens. It works on
  players the same way: some mobs carry an element (blazes burn, cave spiders and bogged poison, wither skeletons
  and evokers bring crimson, vindicators, ravagers and spiders make you bleed).
  - **Fire:** burning and fire weapons build up to a **Deep Burn**: it never wears off, burns a little every few
    seconds and halves healing. Milk, water and totems don't help: only a **Quenching Elixir** puts it out.
    Fire Resistance pauses it.
  - **Poison:** stacks **Venom** (I to IV; milk, honey or an **Antidote** cure it). A fifth stack turns it into
    **Heavy Venom**, which stops healing and slows you, and only a **Greater Antidote** cures.
  - **Crimson:** a burst of crimson damage and a **Crimson Brand**: 25% more damage taken, half healing, and
    attackers heal from their hits.
  - **Blood:** a blood pop for a fixed 10% of max health that armor doesn't soften. A **Clotting Tonic** stops it.
- **Combat HUD:** your buildups and statuses next to the status capsule, meters over the heads of creatures you
  fight, damage numbers in element colours, and a tinted screen edge while you burn, carry heavy venom or a brand.
- **Gate dungeons: 20 realms in 8 shapes.** Every gate leads to a dungeon of one of 20 realms, several for each
  rank (the Hollows, Mirewood, the Rootbound Den and the Mosswept Ruins at E, up to the Starless Expanse, the
  Emberforge, the Infernal Citadel and the Crimson Abyss at S). Each has its own stone, decorations, lights, fog,
  drifting particles, element, creatures and favourite bosses. It is laid out in one of 8 shapes: winding caverns, a
  labyrinth with dead ends, a gauntlet of great halls through gatehouses, a spire climbed by winding stairs, a
  descent down a deep shaft, islands joined by bridges over a chasm, one vast hollow with camps and a lake, or a hive
  of round cells. The gate's seed picks both.

## Building and running

Needs Java 21.

```
./gradlew build              # jar ends up in build/libs
./gradlew runClient          # play in a dev client
./gradlew runServer          # dev server in runs/server
./gradlew runGameTestServer  # automated in-game tests
```

Operator commands (op level 2; in single player, turn cheats on): everything is under `/solo`, with tab completion.
`/solo` alone shows the groups as clickable buttons. A few: `/solo wipe` (start a player over: `system` or `all`),
`/solo set level 40`, `/solo awaken s mage`, `/solo give item <item> [count] [grade]`, `/solo spawn gate s red`,
`/solo spawn creature <kind> [count] [rank]`, `/solo dungeon enter|boss|clear|leave`, `/solo quest complete all`.
Most take players at the end (`@a`, a name); left out, it's you. See "Operator commands: /solo" in
docs/ARCHITECTURE.md.

Older testing commands (op level 2, also reachable as `/solo dev ...`): `/system evaluate [rank] [class]` (plays the whole evaluation, on the
nearest crystal if there is one), `/system awaken [rank] [class]` (same result, no show), `/system reset`,
`/system xp <n>`, `/system points <n>`, `/system rank <rank>`, `/system stat <stat> <value>`,
`/system exhaustion <percent>`, `/system notify <text>`, `/system buildup <targets> <element> <amount>`,
`/system status <targets> <deep_burn|venom|heavy_venom|crimson_brand> [stacks]`,
`/system trial status|reset|call|enter|arbiter|wake|fall|slay|grant` (the awakening trial; `grant` skips it),
`/system gate spawn [rank] [realm|any] [shape]` (a gate in front of you), `/system gate enter|info|boss|wake|kill|exit`.

Config: `config/beyondsolo-common.toml` (`awakening.required` turns the trial off so players start with
the System, spawn distance, Evaluation Zone weight, announcing evaluations in chat, XP,
exhaustion rates, mob element buildup) and `config/beyondsolo-client.toml` (pop-up mode, stay-open pop-ups, HUD,
sounds, System typeface on or off, damage numbers, enemy meters, screen tints).

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
| Better Combat (+ playerAnimator, Cloth Config) | attack animations and combos; our weapons use its data files |
| Combat Roll | the dodge roll; we hook its roll event and attributes |
| JEI, Jade | compile-only for now; recipe and tooltip plugins later |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code is laid out and where future systems plug in.

## Credits

- Typeface: [Exo 2](https://github.com/NDISCOVER/Exo-2.0) by the Exo 2 Project Authors, SIL Open Font License 1.1.
- Icons: [Phosphor Icons](https://phosphoricons.com), MIT License.

Both licence texts ship inside the jar in `licenses/`. Everything else (the crystal, the magic circle, the rank
emblems, the weapons and armor, the effects) is drawn by code in this repository (`tools/gen_*.py`).
