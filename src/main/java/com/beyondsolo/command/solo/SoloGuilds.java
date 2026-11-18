package com.beyondsolo.command.solo;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.Guild;
import com.beyondsolo.guild.GuildColors;
import com.beyondsolo.guild.GuildConfig;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.Guilds;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Guilds and raids: /solo guild (found one outright, set its level and treasury, hand it hunters, heal them, end it)
 * and /solo raid (open a raid gate, make a gate a raid gate, start a raid at once, force the boss's phases).
 */
final class SoloGuilds {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(guild());
        root.then(raid());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guild() {
        return Commands.literal("guild")
                .then(Commands.literal("create").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(SoloGuilds::create)))))
                .then(Commands.literal("level").then(Solo.targets(Commands.argument("level", IntegerArgumentType.integer(1, GuildConfig.MAX_LEVEL)),
                        (ctx, p) -> {
                            Guild g = guildOf(p);
                            Guilds.setLevel(p.server, g, IntegerArgumentType.getInteger(ctx, "level"));
                            return Solo.t("guild.level", g.name(), g.level());
                        })))
                .then(Commands.literal("gold").then(Solo.targets(Commands.argument("gold", LongArgumentType.longArg(0)),
                        (ctx, p) -> {
                            Guild g = guildOf(p);
                            Guilds.setTreasury(p.server, g, LongArgumentType.getLong(ctx, "gold"));
                            return Solo.t("guild.gold", g.name(), g.treasury());
                        })))
                .then(Commands.literal("hunter").then(Solo.rankArg()
                        .then(Solo.targets(Solo.word("class", () -> Solo.CLASSES.stream()), SoloGuilds::hunter))
                        .executes(ctx -> Solo.each(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), SoloGuilds::hunter))))
                .then(Commands.literal("heal").then(Solo.targets(Commands.literal("hunters"), (ctx, p) -> {
                    Guild g = guildOf(p);
                    Guilds.healHunters(p.server, g);
                    return Solo.t("guild.healed", g.name());
                })))
                .then(Solo.targets(Commands.literal("disband"), (ctx, p) -> {
                    Guild g = guildOf(p);
                    String name = g.name();
                    Guilds.dissolveNow(p.server, g);
                    return Solo.t("guild.disbanded", name);
                }));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        Solo.needEvaluated(p);
        // the founding is on the house: the cost is handed over first, and taken back if it doesn't go through
        long cost = GuildConfig.FOUNDING_COST.get();
        Economy.add(p, cost, Economy.Reason.COMMAND);
        Guilds.Outcome out = Guilds.found(p, StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "tag"),
                p.getRandom().nextInt(GuildColors.CRESTS), p.getRandom().nextInt(GuildColors.PALETTE.length));
        if (!out.ok()) {
            Economy.add(p, -cost, Economy.Reason.COMMAND);
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(out.message()).create();
        }
        ctx.getSource().sendSuccess(out::message, true);
        return 1;
    }

    @Nullable
    private static Component hunter(CommandContext<CommandSourceStack> ctx, ServerPlayer p) throws CommandSyntaxException {
        Guild g = guildOf(p);
        PlayerRank rank = Solo.rank(ctx);
        String clsName = has(ctx, "class") ? StringArgumentType.getString(ctx, "class") : "random";
        PlayerClass cls = Solo.playerClass(clsName, ctx.getSource());
        GuildHunter h = Recruits.of(p.getRandom(), rank, cls);
        Guilds.enlist(p.server, g, h);
        return Solo.t("guild.hunter", h.name(), rank.displayName(), cls.displayName(), h.level(), g.name());
    }

    private static boolean has(CommandContext<CommandSourceStack> ctx, String arg) {
        try {
            StringArgumentType.getString(ctx, arg);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Guild guildOf(ServerPlayer p) throws CommandSyntaxException {
        Guild g = Guilds.of(p);
        if (g == null) throw Solo.error("no_guild", p.getDisplayName());
        return g;
    }

    // ---------------------------------------------------------------- raids

    private static LiteralArgumentBuilder<CommandSourceStack> raid() {
        return Commands.literal("raid")
                .then(Commands.literal("open").then(Solo.rankArg().executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (GateService.isDungeon(p.level())) throw Solo.error("gate_here");
                    PlayerRank rank = Solo.rank(ctx);
                    GateInstance g = Raids.open(p, rank);
                    if (g == null) throw Solo.error("spawn_failed");
                    ctx.getSource().sendSuccess(() -> Solo.t("raid.opened", rank.displayName(), Solo.blocks(p.position(), g.gatePos())), true);
                    return 1;
                })))
                .then(Commands.literal("make").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    GateBlockEntity gate = nearestGate(p);
                    if (gate == null) throw Solo.error("no_gate", Solo.RANGE);
                    GateInstance g = GateData.get(p.server).get(gate.gateId());
                    if (g == null || g.isTrial() || g.isKeyed() || g.state() != GateInstance.State.OPEN) throw Solo.error("not_raidable");
                    if (g.isRaid()) throw Solo.error("already_raid");
                    Raids.makeRaid((ServerLevel) gate.getLevel(), gate, g, null);
                    ctx.getSource().sendSuccess(() -> Solo.t("raid.made", g.rank().displayName()), true);
                    return 1;
                }))
                .then(Commands.literal("start").then(Solo.target(Commands.literal("now"), (ctx, p) -> {
                    Raids.Outcome out = Raids.startNow(p);
                    if (!out.ok()) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(out.message()).create();
                    return Solo.t("raid.started", p.getDisplayName());
                })))
                .then(Commands.literal("phase").then(Solo.target(Solo.word("phase", () -> Stream.of("barrier", "cataclysm")), (ctx, p) -> {
                    Raids.Outcome out = Raids.forcePhase(p, StringArgumentType.getString(ctx, "phase"));
                    if (!out.ok()) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(out.message()).create();
                    return out.message();
                })));
    }

    /** The open gate nearest to the player in their world, within {@link Solo#RANGE}. */
    @Nullable
    private static GateBlockEntity nearestGate(ServerPlayer p) {
        GateBlockEntity best = null;
        double bestD = (double) Solo.RANGE * Solo.RANGE;
        for (GateInstance g : GateData.get(p.server).all()) {
            if (g.state() != GateInstance.State.OPEN || !g.gateDimension().equals(p.level().dimension())) continue;
            double d = p.distanceToSqr(Vec3.atCenterOf(g.gatePos()));
            if (d > bestD || !p.level().isLoaded(g.gatePos())) continue;
            if (p.level().getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
                best = gate;
                bestD = d;
            }
        }
        return best;
    }

    private SoloGuilds() {}
}
