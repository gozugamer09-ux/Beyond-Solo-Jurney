package com.beyondsolo.guild;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /guild} (and {@code /gc} for guild chat): everything the GUILD page does, for everyone. No permission needed;
 * the guild itself decides who may do what (the master, officers, members).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GuildCommand {
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        Guild g = p == null ? null : Guilds.of(p);
        List<String> names = new ArrayList<>();
        if (g != null) for (UUID m : g.members()) if (!m.equals(p.getUUID())) names.add(g.name(m));
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> INVITERS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? List.of() : Guilds.invitingTags(p), b);
    };
    private static final SuggestionProvider<CommandSourceStack> RANKS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b);

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("guild")
                .executes(GuildCommand::info)
                .then(Commands.literal("info").executes(GuildCommand::info))
                .then(Commands.literal("create").then(Commands.argument("tag", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, Guilds.found(me(ctx), StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "tag"), me(ctx).getRandom().nextInt(GuildColors.CRESTS),
                                        me(ctx).getRandom().nextInt(GuildColors.PALETTE.length)))))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, Guilds.invite(me(ctx), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept")
                        .executes(ctx -> run(ctx, Guilds.accept(me(ctx), null)))
                        .then(Commands.argument("guild", StringArgumentType.word()).suggests(INVITERS)
                                .executes(ctx -> run(ctx, Guilds.accept(me(ctx), inviting(ctx))))))
                .then(Commands.literal("decline")
                        .executes(ctx -> run(ctx, Guilds.decline(me(ctx), null)))
                        .then(Commands.argument("guild", StringArgumentType.word()).suggests(INVITERS)
                                .executes(ctx -> run(ctx, Guilds.decline(me(ctx), inviting(ctx))))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, Guilds.leave(me(ctx)))))
                .then(Commands.literal("kick").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Guilds.kick(me(ctx), member(ctx))))))
                .then(Commands.literal("promote").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Guilds.promote(me(ctx), member(ctx))))))
                .then(Commands.literal("demote").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Guilds.demote(me(ctx), member(ctx))))))
                .then(Commands.literal("leader").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Guilds.transfer(me(ctx), member(ctx))))))
                .then(Commands.literal("disband").executes(ctx -> run(ctx, Guilds.disband(me(ctx)))))
                .then(Commands.literal("deposit").then(Commands.argument("gold", LongArgumentType.longArg(1))
                        .executes(ctx -> run(ctx, Guilds.deposit(me(ctx), LongArgumentType.getLong(ctx, "gold"))))))
                .then(Commands.literal("withdraw").then(Commands.argument("gold", LongArgumentType.longArg(1))
                        .executes(ctx -> run(ctx, Guilds.withdraw(me(ctx), LongArgumentType.getLong(ctx, "gold"))))))
                .then(Commands.literal("hunters").executes(GuildCommand::hunters))
                .then(Commands.literal("permit").then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                        .executes(ctx -> {
                            PlayerRank rank = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
                            if (rank == null) return run(ctx, Guilds.Outcome.no("permit.level", "?", 0));
                            return run(ctx, Guilds.permit(me(ctx), rank));
                        })))
                .then(Commands.literal("chat").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(GuildCommand::chat))));
        d.register(Commands.literal("gc").then(Commands.argument("message", StringArgumentType.greedyString()).executes(GuildCommand::chat)));
    }

    private static ServerPlayer me(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Guilds.Outcome out) {
        // always said (not command feedback, which a server can turn off): for a hunter without the System it is the only answer
        if (out.ok()) ctx.getSource().sendSystemMessage(Guilds.tag().append(out.message().copy().withStyle(ChatFormatting.GRAY)));
        else ctx.getSource().sendFailure(out.message());
        return out.ok() ? 1 : 0;
    }

    private static int chat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Guilds.Outcome out = Guilds.chat(me(ctx), StringArgumentType.getString(ctx, "message"));
        if (!out.ok()) ctx.getSource().sendFailure(out.message());
        return out.ok() ? 1 : 0;
    }

    /** The guild member with the name typed (any case), or an id that is nobody's. */
    private static UUID member(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Guild g = Guilds.of(me(ctx));
        if (g != null) for (UUID m : g.members()) if (g.name(m).equalsIgnoreCase(name)) return m;
        return new UUID(-1, -1);
    }

    /** The guild of an open invitation by the tag or name typed, or an id that is nobody's. */
    private static UUID inviting(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID id = Guilds.invitingGuild(me(ctx), StringArgumentType.getString(ctx, "guild"));
        return id == null ? new UUID(-1, -1) : id;
    }

    /** The guild in chat: its level, treasury, members and hunters, with buttons for what can be done from here. */
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = me(ctx);
        MinecraftServer server = me.server;
        Guild g = Guilds.of(me);
        List<Component> lines = new ArrayList<>();
        if (g == null) {
            lines.add(Guilds.tag().append(Component.translatable(Guilds.KEY + "info.none", GuildConfig.FOUNDING_COST.get()).withStyle(ChatFormatting.GRAY)));
        } else {
            lines.add(Guilds.tag().append(Guilds.styled(g)).append(Component.literal("  ")).append(
                    Component.translatable(Guilds.KEY + "info.level", g.level(), g.xp(), GuildPerks.xpToNext(g.level())).withStyle(ChatFormatting.YELLOW)));
            lines.add(Component.literal("  ").append(Component.translatable(Guilds.KEY + "info.treasury", g.treasury()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  ")).append(Component.translatable(Guilds.KEY + "info.record", g.gatesCleared(), g.raidsCleared())
                            .withStyle(ChatFormatting.GRAY)));
            lines.add(Component.literal("  ").append(Component.translatable(Guilds.KEY + "info.members", g.size(), GuildPerks.memberCap(g.level()))
                    .withStyle(ChatFormatting.WHITE)));
            for (UUID id : g.members()) {
                GuildRole role = g.role(id);
                boolean online = OnlinePlayers.get(server, id) != null;
                lines.add(Component.literal(role == GuildRole.MASTER ? "    ★ " : role == GuildRole.OFFICER ? "    ◆ " : "      ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(g.name(id)).withStyle(online ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                        .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(role.displayName().copy().withStyle(ChatFormatting.GRAY)));
            }
            lines.add(Component.literal("  ").append(Component.translatable(Guilds.KEY + "info.hunters", g.hunters().size(), GuildPerks.rosterCap(g.level()))
                    .withStyle(ChatFormatting.WHITE)).append(" ").append(Guilds.button("hunters", ChatFormatting.AQUA, "/guild hunters")));
            MutableComponent foot = Component.literal("  ").append(Guilds.button("leave", ChatFormatting.RED, "/guild leave"));
            if (g.isMaster(me.getUUID())) foot.append(" ").append(Guilds.button("disband", ChatFormatting.DARK_RED, "/guild disband"));
            lines.add(foot);
        }
        for (UUID id : Guilds.invitesFor(me)) {
            Guild inv = Guilds.data(server).get(id);
            if (inv == null) continue;
            lines.add(Component.literal("  ").append(Component.translatable(Guilds.KEY + "info.invite", Guilds.styled(inv)).withStyle(ChatFormatting.GRAY))
                    .append(" ").append(Guilds.button("accept", ChatFormatting.GREEN, "/guild accept " + inv.tag()))
                    .append(" ").append(Guilds.button("decline", ChatFormatting.RED, "/guild decline " + inv.tag())));
        }
        for (Component c : lines) ctx.getSource().sendSystemMessage(c);
        return 1;
    }

    /** The guild's hired hunters: class, level, rank, and whether they are hurt or out on a raid. */
    private static int hunters(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = me(ctx);
        Guild g = Guilds.of(me);
        if (g == null) return run(ctx, Guilds.Outcome.no("none"));
        long now = me.server.overworld().getGameTime();
        ctx.getSource().sendSystemMessage(Guilds.tag().append(Component.translatable(Guilds.KEY + "info.hunters", g.hunters().size(),
                GuildPerks.rosterCap(g.level())).withStyle(ChatFormatting.WHITE)));
        if (g.hunters().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("  ").append(Component.translatable(Guilds.KEY + "info.no_hunters").withStyle(ChatFormatting.GRAY)));
        }
        for (GuildHunter h : g.hunters()) {
            PlayerClass cls = h.playerClass();
            MutableComponent line = Component.literal("    ").append(Component.literal(h.name()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(Guilds.KEY + "info.hunter", h.rank().displayName(), cls.displayName(), h.level()).withStyle(ChatFormatting.GRAY));
            if (com.beyondsolo.raid.Raids.hunterBusy(me.server, h.id())) {
                line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable(Guilds.KEY + "info.raiding").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (h.injured(now)) {
                long min = Math.max(1, (h.injuredUntil() - now + 1199) / 1200);
                line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable(Guilds.KEY + "info.injured", min).withStyle(ChatFormatting.RED));
            }
            ctx.getSource().sendSystemMessage(line);
        }
        return 1;
    }

    private GuildCommand() {}
}
