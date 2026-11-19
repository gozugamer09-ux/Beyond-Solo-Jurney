package com.beyondsolo.command.solo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /solo and /solo help: the commands in groups. A group's name runs its page; a command's line, clicked, puts the
 * command in the chat box to finish.
 */
final class SoloHelp {
    /** A command as the help shows it: how it is written (after "/solo ") and what it does ({@code help.<key>}). */
    record Line(String usage, String key) {}

    /** The groups in the order they are shown, each with its commands. */
    static final Map<String, List<Line>> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("player", List.of(
                new Line("info [player]", "info"),
                new Line("awaken [rank] [class] [players]", "awaken"),
                new Line("evaluate [rank] [class] [player]", "evaluate"),
                new Line("bind [players]", "bind"),
                new Line("set level|rank|class|subclass|stat|points|gold|exhaustion <value> [players]", "set"),
                new Line("add xp|levels|points|gold|stat <amount> [players]", "add"),
                new Line("heal [players]", "heal"),
                new Line("reset <part> [players]", "reset"),
                new Line("wipe system|all [players]", "wipe")));
        TOPICS.put("skills", List.of(
                new Line("skill learn <skill>|all [level] [players]", "skill.learn"),
                new Line("skill forget <skill>|all [players]", "skill.forget"),
                new Line("skill max [players]", "skill.max"),
                new Line("quest list [player]", "quest.list"),
                new Line("quest start <quest> [players]", "quest.start"),
                new Line("quest complete <quest>|all [players]", "quest.complete"),
                new Line("quest fail <quest>|all [players]", "quest.fail"),
                new Line("shadow give <creature> [shadow rank] [players]", "shadow.give"),
                new Line("shadow post|unpost [players]", "shadow.post"),
                new Line("shadow posts [player]", "shadow.posts"),
                new Line("shadow list [player]", "shadow.list"),
                new Line("shadow promote <number> [player]", "shadow.promote"),
                new Line("shadow name <number> <name> [player]", "shadow.name"),
                new Line("shadow essence set|add <amount> [players]", "shadow.essence")));
        TOPICS.put("world", List.of(
                new Line("spawn creature <kind>|random [count] [rank] [variant] [affinity]", "spawn.creature"),
                new Line("spawn boss <kind>|random [rank] [ascended]", "spawn.boss"),
                new Line("spawn gate [rank] [normal|red|vault|shrine|lair|breaking] [realm] [shape]", "spawn.gate"),
                new Line("spawn cache [rank]", "spawn.cache"),
                new Line("spawn crystal", "spawn.crystal"),
                new Line("gate list", "gate.list"),
                new Line("gate close [all]", "gate.close"),
                new Line("gate break", "gate.break")));
        TOPICS.put("dungeons", List.of(
                new Line("dungeon enter [player]", "dungeon.enter"),
                new Line("dungeon boss [player]", "dungeon.boss"),
                new Line("dungeon clear [player]", "dungeon.clear"),
                new Line("dungeon leave [players]", "dungeon.leave"),
                new Line("dungeon info [player]", "dungeon.info"),
                new Line("trial awakening|job|throne|penalty [player]", "trial"),
                new Line("tower climb [floor] [player]", "tower.climb"),
                new Line("tower rush <tier> [player]", "tower.rush"),
                new Line("tower endless [player]", "tower.endless")));
        TOPICS.put("items", List.of(
                new Line("give item <item> [count] [grade] [players]", "give.item"),
                new Line("give armor <set> [grade] [players]", "give.armor"),
                new Line("give key <rank> [count] [players]", "give.key"),
                new Line("give rune <skill>|sealed <rank> [count] [players]", "give.rune"),
                new Line("give tome <skill> [count] [players]", "give.tome"),
                new Line("give license [players]", "give.license")));
        TOPICS.put("guilds", List.of(
                new Line("guild create <player> <tag> <name>", "guild.create"),
                new Line("guild level <level> [players]", "guild.level"),
                new Line("guild gold <gold> [players]", "guild.gold"),
                new Line("guild hunter <rank> [class] [players]", "guild.hunter"),
                new Line("guild heal hunters [players]", "guild.heal"),
                new Line("guild disband [players]", "guild.disband"),
                new Line("raid open <rank>", "raid.open"),
                new Line("raid make", "raid.make"),
                new Line("raid start now [player]", "raid.start"),
                new Line("raid phase barrier|cataclysm [player]", "raid.phase")));
        TOPICS.put("monarch", List.of(
                new Line("monarch crown <crown> [players]", "monarch.crown"),
                new Line("monarch uncrown [players]", "monarch.uncrown"),
                new Line("monarch call [players]", "monarch.call"),
                new Line("monarch reset [players]", "monarch.reset"),
                new Line("monarch jump 1|2|arbiter|throne|<crown> [player]", "monarch.jump"),
                new Line("monarch power domain|dread|crown|authority [players]", "monarch.power")));
        TOPICS.put("rulers", List.of(
                new Line("ruler call [verdict|wings|hand|random]", "ruler.call"),
                new Line("ruler war [verdict|wings|hand|random]", "ruler.war"),
                new Line("ruler sentence [<ruler>] on [player]", "ruler.sentence"),
                new Line("ruler land", "ruler.land"),
                new Line("ruler stop", "ruler.stop"),
                new Line("ruler status", "ruler.status"),
                new Line("ruler phase <1-3>", "ruler.phase"),
                new Line("ruler move <move>", "ruler.move"),
                new Line("ruler kill", "ruler.kill"),
                new Line("ruler next <minutes>", "ruler.next"),
                new Line("ruler bless [players]", "ruler.bless"),
                new Line("ruler mote [count]", "ruler.mote"),
                new Line("ruler record [player]", "ruler.record")));
        TOPICS.put("association", List.of(
                new Line("association open [contracts|permits|exam|standing]", "association.open"),
                new Line("association broker", "association.broker"),
                new Line("association post", "association.post"),
                new Line("association merit add|set <merit> [players]", "association.merit"),
                new Line("association exam start|pass|fail [player]", "association.exam"),
                new Line("association contract [player]", "association.contract"),
                new Line("association record [player]", "association.record"),
                new Line("association reset [players]", "association.reset")));
        TOPICS.put("boxes", List.of(
                new Line("box give plain|blessed|cursed [rank] [count] [players]", "box.give"),
                new Line("box curse wardens|frailty|backlash|greed [rank] [players]", "box.curse"),
                new Line("mana set|add <amount> [players]", "mana.set"),
                new Line("mana clear [players]", "mana.clear"),
                new Line("mana crystal", "mana.crystal"),
                new Line("mana crystals [player]", "mana.crystals"),
                new Line("mana info [player]", "mana.info")));
        TOPICS.put("story", List.of(
                new Line("story info [player]", "story.info"),
                new Line("story step untold|visit|visited|cure|brewed|awake [players]", "story.step"),
                new Line("story souls <amount> [players]", "story.souls"),
                new Line("story give dew|chalice|elixir|stew [players]", "story.give"),
                new Line("story home [player]", "story.home"),
                new Line("story mother [player]", "story.mother"),
                new Line("story scene visit|waking|meal|reveal [player]", "story.scene"),
                new Line("story reset [players]", "story.reset"),
                new Line("story main info [player]", "story.main.info"),
                new Line("story main chapter untold|player|arise|citadel|throne|father|last_war|home|done [players]", "story.main.chapter"),
                new Line("story main scene prologue|statue|arise|vharos|truth|father|war|last_light|home|end [player]", "story.main.scene"),
                new Line("story main fight [player]", "story.main.fight"),
                new Line("story main father [player]", "story.main.father"),
                new Line("story main reset [players]", "story.main.reset")));
        TOPICS.put("isle", List.of(
                new Line("isle call [players]", "isle.call"),
                new Line("isle land [players]", "isle.land"),
                new Line("isle launch", "isle.launch"),
                new Line("isle phase beach|nests|hive|queen|king", "isle.phase"),
                new Line("isle win|fail|stop", "isle.end"),
                new Line("isle status", "isle.status"),
                new Line("isle next <minutes>", "isle.next"),
                new Line("isle build", "isle.build"),
                new Line("isle visit [player]", "isle.visit"),
                new Line("isle record [player]", "isle.record"),
                new Line("isle reset [players]", "isle.reset")));
        TOPICS.put("home", List.of(
                new Line("home info [player]", "home.info"),
                new Line("home give studio|family|hall|penthouse|none [players]", "home.give"),
                new Line("home go [player]", "home.go"),
                new Line("home visit <host> [player]", "home.visit"),
                new Line("home leave [player]", "home.leave"),
                new Line("home build", "home.build"),
                new Line("home rebuild [player]", "home.rebuild"),
                new Line("home rest [players]", "home.rest"),
                new Line("home ready [players]", "home.ready"),
                new Line("home trophies <count> [players]", "home.trophies")));
        TOPICS.put("rivals", List.of(
                new Line("rival send [guild|random] [ranked]", "rival.send"),
                new Line("rival arrive", "rival.arrive"),
                new Line("rival enter", "rival.enter"),
                new Line("rival finish clear|retreat", "rival.finish"),
                new Line("rival list", "rival.list"),
                new Line("rival forget", "rival.forget"),
                new Line("rival record [player]", "rival.record"),
                new Line("hire porter [e|d] [players]", "hire.porter"),
                new Line("hire hunter [rank] [class] [players]", "hire.hunter"),
                new Line("hire dismiss [players]", "hire.dismiss"),
                new Line("hire list [player]", "hire.list")));
        TOPICS.put("dev", List.of(
                new Line("dev ...", "dev")));
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.executes(ctx -> {
            ctx.getSource().sendSuccess(SoloHelp::overview, false);
            return 1;
        });
        LiteralArgumentBuilder<CommandSourceStack> help = Commands.literal("help").executes(ctx -> {
            ctx.getSource().sendSuccess(SoloHelp::overview, false);
            return 1;
        });
        for (String topic : TOPICS.keySet()) {
            help.then(Commands.literal(topic).executes(ctx -> {
                ctx.getSource().sendSuccess(() -> page(topic), false);
                return 1;
            }));
        }
        root.then(help);
    }

    /** The groups, each a button that opens its page. */
    static Component overview() {
        MutableComponent out = Solo.t("help.header").withStyle(ChatFormatting.GOLD);
        out.append("\n");
        boolean first = true;
        for (String topic : TOPICS.keySet()) {
            if (!first) out.append("  ");
            first = false;
            String command = "/solo help " + topic;
            out.append(Component.literal("[").append(Solo.t("help.topic." + topic)).append("]")
                    .withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
        }
        out.append("\n").append(Solo.t("help.players").withStyle(ChatFormatting.GRAY));
        return out;
    }

    /** One group's commands: how each is written, and what it does. */
    static Component page(String topic) {
        MutableComponent out = Solo.t("help.page", Solo.t("help.topic." + topic)).withStyle(ChatFormatting.GOLD);
        for (Line line : TOPICS.get(topic)) {
            String start = "/solo " + start(line.usage());
            out.append("\n").append(Component.literal("/solo " + line.usage())
                    .withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, start))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Solo.t("help.click", start)))));
            out.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)).append(Solo.t("help." + line.key()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    /** The fixed words a command starts with ("spawn gate "), up to the first one to fill in or choose. */
    static String start(String usage) {
        StringBuilder out = new StringBuilder();
        for (String word : usage.split(" ")) {
            if (word.contains("<") || word.contains("[") || word.contains("|") || word.contains(".")) break;
            out.append(word).append(' ');
        }
        return out.toString();
    }

    private SoloHelp() {}
}
