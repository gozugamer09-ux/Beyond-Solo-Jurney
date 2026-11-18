package com.beyondsolo.command.solo;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * What the /solo commands share: their messages ({@code command.beyondsolo.solo.*}), the optional players at the end
 * of a command, and the arguments most of them take (rank, class, stat).
 */
final class Solo {
    /** How far the commands look for a gate or a crystal. */
    static final int RANGE = 128;
    /** The rank arguments: the six ranks, then "random". */
    static final List<String> RANKS = Stream.concat(Arrays.stream(PlayerRank.values()).map(r -> lower(r.name())), Stream.of("random")).toList();
    /** The class arguments: the six classes, then "random". */
    static final List<String> CLASSES = Stream.concat(Arrays.stream(PlayerClass.values()).map(c -> lower(c.name())), Stream.of("random")).toList();

    /** A /solo message. */
    static MutableComponent t(String key, Object... args) {
        return Component.translatable("command.beyondsolo.solo." + key, args);
    }

    /** A /solo error ({@code command.beyondsolo.solo.error.<key>}). */
    static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(t("error." + key, args)).create();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- who a command is for

    /** Does the command for one player and says what it did (null: nothing to say). */
    @FunctionalInterface
    interface Action {
        @Nullable Component run(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException;
    }

    /** The node runs for whoever sent it, or, with players named after it, for each of them. */
    static <T extends ArgumentBuilder<CommandSourceStack, T>> T targets(T node, Action action) {
        return node.executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrException()), action))
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> each(ctx, EntityArgument.getPlayers(ctx, "players"), action)));
    }

    /** Like {@link #targets}, for one player at most (it takes them somewhere). */
    static <T extends ArgumentBuilder<CommandSourceStack, T>> T target(T node, Action action) {
        return node.executes(ctx -> each(ctx, List.of(ctx.getSource().getPlayerOrException()), action))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> each(ctx, List.of(EntityArgument.getPlayer(ctx, "player")), action)));
    }

    /**
     * Runs an action for each player. With one player its error is the command's error; with several, each failure is
     * said under that player's name and the others still go ahead. Returns how many it worked for.
     */
    static int each(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Action action) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        int done = 0;
        for (ServerPlayer p : players) {
            try {
                Component said = action.run(ctx, p);
                if (said != null) src.sendSuccess(() -> said, false);
                done++;
            } catch (CommandSyntaxException e) {
                if (players.size() == 1) throw e;
                src.sendFailure(Component.empty().append(p.getDisplayName()).append(": ").append(message(e.getRawMessage())));
            }
        }
        return done;
    }

    private static Component message(Message m) {
        return ComponentUtils.fromMessage(m);
    }

    // ---------------------------------------------------------------- checks

    /** The player has been evaluated (a rank and class); most System numbers mean nothing before that. */
    static void needEvaluated(ServerPlayer p) throws CommandSyntaxException {
        if (!SystemAPI.isAwakened(p)) throw error("not_evaluated", p.getDisplayName());
    }

    // ---------------------------------------------------------------- arguments

    static RequiredArgumentBuilder<CommandSourceStack, String> word(String name, Supplier<Stream<String>> options) {
        return Commands.argument(name, StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(options.get(), b));
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> rankArg() {
        return word("rank", RANKS::stream);
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> classArg() {
        return word("class", () -> CLASSES.stream().filter(c -> !c.equals("random")));
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> statArg() {
        return word("stat", () -> Arrays.stream(Stat.values()).map(s -> lower(s.name())));
    }

    /** Suggests ids by their whole name or by their path alone ("rune" finds beyondsolo:rune_stone). */
    static CompletableFuture<Suggestions> suggestIds(Stream<ResourceLocation> ids, SuggestionsBuilder b) {
        String typed = lower(b.getRemaining());
        ids.filter(id -> SharedSuggestionProvider.matchesSubStr(typed, id.getPath()) || SharedSuggestionProvider.matchesSubStr(typed, id.toString()))
                .sorted()
                .forEach(id -> b.suggest(id.toString()));
        return b.buildFuture();
    }

    /** The registry entry with this id; a name typed without a namespace is looked for in the mod's own as well. */
    static <T> Optional<T> lookup(Registry<T> registry, ResourceLocation id) {
        Optional<T> found = registry.getOptional(id);
        if (found.isPresent() || !id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) return found;
        return registry.getOptional(BeyondSolo.id(id.getPath()));
    }

    /** The rank named in the "rank" argument ("random": one at random). */
    static PlayerRank rank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return rank(StringArgumentType.getString(ctx, "rank"), ctx.getSource());
    }

    static PlayerRank rank(String name, CommandSourceStack src) throws CommandSyntaxException {
        if (name.equalsIgnoreCase("random")) return PlayerRank.values()[src.getLevel().random.nextInt(PlayerRank.values().length)];
        PlayerRank r = PlayerRank.byName(name);
        if (r == null) throw error("unknown_rank", name);
        return r;
    }

    static PlayerClass playerClass(String name, CommandSourceStack src) throws CommandSyntaxException {
        if (name.equalsIgnoreCase("random")) return PlayerClass.roll(src.getLevel().random);
        PlayerClass c = PlayerClass.byName(name);
        if (c == null) throw error("unknown_class", name);
        return c;
    }

    static Stat stat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "stat");
        for (Stat s : Stat.values()) if (s.name().equalsIgnoreCase(name) || s.key().equalsIgnoreCase(name)) return s;
        throw error("unknown_stat", name);
    }

    // ---------------------------------------------------------------- places

    /** The way the command's sender looks, flattened to one of the four directions. */
    static Direction facing(CommandSourceStack src) {
        return Direction.fromYRot(src.getRotation().y);
    }

    /** The block {@code blocks} ahead of the sender, at their feet's height. */
    static BlockPos ahead(CommandSourceStack src, int blocks) {
        Vec3 at = src.getPosition();
        return BlockPos.containing(at).relative(facing(src), blocks);
    }

    /** The nearest Evaluation Crystal within 8 blocks of the player, or a spot in front of them when there is none. */
    static BlockPos nearestCrystal(ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos crystal = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 4, 8))) {
            if (player.level().getBlockState(pos).is(ModBlocks.EVALUATION_CRYSTAL.get())
                    && (crystal == null || pos.distSqr(origin) < crystal.distSqr(origin))) {
                crystal = pos.immutable();
            }
        }
        return crystal == null ? origin.relative(player.getDirection(), 2) : crystal;
    }

    /** Distance in whole blocks, flat. */
    static int blocks(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x, dz = to.getZ() + 0.5 - from.z;
        return Mth.floor(Math.sqrt(dx * dx + dz * dz));
    }

    private Solo() {}
}
