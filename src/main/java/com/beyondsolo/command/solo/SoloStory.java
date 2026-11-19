package com.beyondsolo.command.solo;

import com.beyondsolo.story.Kin;
import com.beyondsolo.story.KinEntity;
import com.beyondsolo.story.KinRecord;
import com.beyondsolo.story.LongSleep;
import com.beyondsolo.story.StoryConfig;
import com.beyondsolo.story.StoryModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Long Sleep (the hunter's story, {@code story.LongSleep}):
 * <pre>
 * /solo story info [player]                                         how far it has come, home, her bed, the souls
 * /solo story step untold|visit|visited|cure|brewed|awake [players] moves the story there (its quest, the elixir)
 * /solo story souls &lt;amount&gt; [players]                             the Citadel souls gathered for the cure
 * /solo story give dew|chalice|elixir|stew [players]                the story's things
 * /solo story home [player]                                         the nearest Evaluation Crystal's village is home
 * /solo story mother [player]                                       her in her bed now (a bed chosen if needed)
 * /solo story scene visit|waking|meal|reveal [player]               plays a scene
 * /solo story reset [players]                                       from the start: she sleeps again
 * </pre>
 */
final class SoloStory {
    static final List<String> STAGES = List.of("untold", "visit", "visited", "cure", "brewed", "awake");
    static final List<String> THINGS = List.of("dew", "chalice", "elixir", "stew");
    static final List<String> SCENES = List.of("visit", "waking", "meal", "reveal");

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("story")
                .then(Solo.target(Commands.literal("info"), (ctx, p) -> info(p)))
                .then(Commands.literal("step").then(Solo.targets(Solo.word("stage", STAGES::stream), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    int stage = stage(ctx);
                    LongSleep.step(p, stage);
                    return Solo.t("story.step", p.getDisplayName(), stageName(stage));
                })))
                .then(Commands.literal("souls").then(Solo.targets(Commands.argument("amount", IntegerArgumentType.integer(0, 100000)), (ctx, p) -> {
                    LongSleep.setSouls(p, IntegerArgumentType.getInteger(ctx, "amount"));
                    return Solo.t("story.souls", p.getDisplayName(), Kin.record(p).souls(), StoryConfig.souls());
                })))
                .then(Commands.literal("give").then(Solo.targets(Solo.word("thing", THINGS::stream), (ctx, p) -> {
                    ItemStack stack = new ItemStack(thing(ctx));
                    Component name = stack.getHoverName();
                    if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
                    return Solo.t("story.given", p.getDisplayName(), name);
                })))
                .then(Solo.target(Commands.literal("home"), (ctx, p) -> home(p)))
                .then(Solo.target(Commands.literal("mother"), (ctx, p) -> mother(p)))
                .then(Commands.literal("scene").then(Solo.target(Solo.word("scene", SCENES::stream), (ctx, p) -> {
                    String scene = Solo.lower(StringArgumentType.getString(ctx, "scene"));
                    if (!SCENES.contains(scene)) throw Solo.error("unknown_story_scene", scene, String.join(", ", SCENES));
                    Solo.needEvaluated(p);
                    if (!LongSleep.play(p, "long_sleep_" + scene)) throw Solo.error("story_no_mother", p.getDisplayName());
                    return Solo.t("story.scene", scene, p.getDisplayName());
                })))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    LongSleep.reset(p);
                    return Solo.t("story.reset", p.getDisplayName());
                })));
    }

    private static int stage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = Solo.lower(StringArgumentType.getString(ctx, "stage"));
        int i = STAGES.indexOf(name);
        if (i < 0) throw Solo.error("unknown_story_stage", name, String.join(", ", STAGES));
        // "cure" is the stage the cure is sought in
        return i;
    }

    private static Component stageName(int stage) {
        return Solo.t("story.stage." + Math.max(0, Math.min(STAGES.size() - 1, stage)));
    }

    private static Item thing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = Solo.lower(StringArgumentType.getString(ctx, "thing"));
        return switch (name) {
            case "dew" -> StoryModule.ELDER_ROOT_DEW.get();
            case "chalice" -> StoryModule.CRIMSON_CHALICE.get();
            case "elixir" -> StoryModule.ELIXIR.get();
            case "stew" -> StoryModule.HOME_STEW.get();
            default -> throw Solo.error("unknown_story_thing", name, String.join(", ", THINGS));
        };
    }

    private static Component info(ServerPlayer p) {
        KinRecord r = Kin.record(p);
        Component none = Solo.t("story.none");
        Component home = r.anchor() == null ? none : place(r.anchor(), r.anchorDimension() == null ? "" : r.anchorDimension().location().toString());
        Component bed = r.bed() == null ? none : place(r.bed(), r.bedDimension() == null ? "" : r.bedDimension().location().toString());
        KinEntity k = Kin.find(p);
        Component her = k == null ? Solo.t("story.not_loaded") : Solo.t("story.state." + k.state());
        return Solo.t("story.info", p.getDisplayName(), stageName(r.stage()), home, bed, her, r.souls(), StoryConfig.souls(),
                r.wokeDay() == KinRecord.NOT_YET ? none : Component.literal(String.valueOf(r.wokeDay())),
                r.mealDay() == KinRecord.NOT_YET ? none : Component.literal(String.valueOf(r.mealDay())));
    }

    /** A place to click: suggests a teleport there. */
    private static Component place(BlockPos at, String dim) {
        String tp = "/execute in " + dim + " run tp @s " + at.getX() + " " + (at.getY() + 1) + " " + at.getZ();
        return Component.literal(at.toShortString()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp)));
    }

    private static Component home(ServerPlayer p) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        BlockPos crystal = Solo.nearestCrystal(p);
        KinEntity k = Kin.find(p);
        if (k != null) k.discard();
        Kin.evaluatedAt(p, crystal);
        Kin.forgetBed(p);
        return Solo.t("story.home", p.getDisplayName(), crystal.toShortString());
    }

    private static Component mother(ServerPlayer p) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        KinRecord r = Kin.record(p);
        if (r.anchor() == null) throw Solo.error("story_no_home", p.getDisplayName());
        KinEntity k = Kin.bringHome(p);
        if (k == null) throw Solo.error("story_no_bed", p.getDisplayName(), r.anchor().toShortString());
        return Solo.t("story.mother", p.getDisplayName(), place(r.bed(), r.bedDimension().location().toString()));
    }

    private SoloStory() {}
}
