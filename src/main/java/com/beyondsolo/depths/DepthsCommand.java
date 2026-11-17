package com.beyondsolo.depths;

import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * /system depths ... testing commands (permission level 2):
 * <pre>
 * depths gate &lt;rank&gt; [weather] [hidden] [trap]  a gate opens in front of you with these ("seed" leaves one to the
 *                                            gate's seed; hidden: none|vault|shrine|lair; trap: none or a kind,
 *                                            put in the first cavern a trap can go in)
 * depths weather &lt;weather|seed&gt;               the cave you are in changes weather now
 * depths info                                what the cave you are in has, and how far you have got with it
 * depths hidden                              to the hidden wall (or into the chamber once it is open)
 * depths open                                the hidden wall comes down
 * depths reveal                              Pulse Scan's reveal, from where you stand
 * depths trap                                just outside the next trap room not yet beaten, facing in
 * depths core                                beside the nearest trap core still standing, facing it
 * depths bolt | rock                         a storm bolt / falling rock beside you
 * depths profile                             the elemental profile of the creature you look at
 * depths weaknesses reset                    forgets the weaknesses you have found
 * </pre>
 */
public final class DepthsCommand {
    private static final SimpleCommandExceptionType NOT_INSIDE = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.gate.not_inside"));

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("depths")
                        .then(Commands.literal("gate").then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b))
                                .executes(ctx -> gate(ctx, "seed", "seed", "seed"))
                                .then(weatherArg()
                                        .executes(ctx -> gate(ctx, StringArgumentType.getString(ctx, "weather"), "seed", "seed"))
                                        .then(Commands.argument("hidden", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("seed", "none"),
                                                        Arrays.stream(HiddenKind.values()).map(HiddenKind::id)), b))
                                                .executes(ctx -> gate(ctx, StringArgumentType.getString(ctx, "weather"), StringArgumentType.getString(ctx, "hidden"), "seed"))
                                                .then(Commands.argument("trap", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("seed", "none"),
                                                                Arrays.stream(TrapKind.values()).map(TrapKind::id)), b))
                                                        .executes(ctx -> gate(ctx, StringArgumentType.getString(ctx, "weather"),
                                                                StringArgumentType.getString(ctx, "hidden"), StringArgumentType.getString(ctx, "trap"))))))))
                        .then(Commands.literal("weather").then(weatherArg().executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            String id = StringArgumentType.getString(ctx, "weather");
                            g.depths().setWeather("seed".equals(id) ? null : GateWeather.byId(id));
                            GateData.get(p.server).setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal("weather: ").append(g.depths().weather(g).title()), false);
                            return 1;
                        })))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(info(g)), false);
                            return 1;
                        }))
                        .then(Commands.literal("hidden").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
                            if (m == null) {
                                ctx.getSource().sendFailure(Component.literal("this cave has no hidden chamber"));
                                return 0;
                            }
                            ServerLevel level = p.serverLevel();
                            if (g.depths().hiddenOpen()) {
                                BlockPos in = DepthsBuild.floorCenter(level, g, g.layout().hidden());
                                p.teleportTo(level, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, p.getYRot(), 0);
                            } else {
                                // a few steps back from the wall (within reach of a knock), on the cavern's side, facing it
                                Vec3 at = m.center().subtract(m.dir().scale(3.2));
                                BlockPos floor = DepthsBuild.floorAt(level, Mth.floor(at.x), Mth.floor(at.z), Mth.floor(at.y));
                                float yaw = (float) (Mth.atan2(m.dir().z, m.dir().x) * Mth.RAD_TO_DEG) - 90f;
                                p.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, yaw, 5f);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("open").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            if (g.depths().hidden(g) == null || g.depths().hiddenOpen()) {
                                ctx.getSource().sendFailure(Component.literal("no hidden wall standing here"));
                                return 0;
                            }
                            HiddenRooms.open(p.serverLevel(), g, p, null);
                            return 1;
                        }))
                        .then(Commands.literal("reveal").executes(ctx -> {
                            HiddenRooms.scan(ctx.getSource().getPlayerOrException(), 24);
                            return 1;
                        }))
                        .then(Commands.literal("trap").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            for (Map.Entry<Integer, TrapKind> e : g.depths().traps(g).entrySet()) {
                                if (g.depths().trapDone(e.getKey())) continue;
                                DungeonLayout.Room room = g.layout().rooms().get(e.getKey());
                                ServerLevel level = p.serverLevel();
                                // just outside the room, on the way in from the previous room, facing in
                                DungeonLayout.Tunnel in = null;
                                for (DungeonLayout.Tunnel t : g.layout().tunnels()) if (t.to() == room.index()) in = t;
                                DepthsBuild.Mouth m = in == null ? null : DepthsBuild.mouth(g, in, room);
                                Vec3 at = m != null ? m.center().add(m.dir().scale(2)) : Vec3.atCenterOf(g.world(room.center()));
                                float yaw = m != null ? (float) (Mth.atan2(-m.dir().z, -m.dir().x) * Mth.RAD_TO_DEG) - 90f : p.getYRot();
                                BlockPos floor = DepthsBuild.floorAt(level, Mth.floor(at.x), Mth.floor(at.z), Mth.floor(at.y));
                                p.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, yaw, 0);
                                ctx.getSource().sendSuccess(() -> Component.literal("trap: ").append(e.getValue().title())
                                        .append(" in room " + room.index()), false);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("no trap left in this cave"));
                            return 0;
                        }))
                        .then(Commands.literal("core").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            ServerLevel level = p.serverLevel();
                            BlockPos best = null;
                            for (Map.Entry<Integer, TrapKind> e : g.depths().traps(g).entrySet()) {
                                if (!e.getValue().hasCore() || g.depths().trapDone(e.getKey())) continue;
                                BlockPos core = DepthsBuild.coreSpot(level, g, g.layout().rooms().get(e.getKey()));
                                if (best == null || core.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition())) best = core;
                            }
                            if (best == null) {
                                ctx.getSource().sendFailure(Component.literal("no trap core standing in this cave"));
                                return 0;
                            }
                            // a couple of steps from the core, on the player's side of it, facing it
                            Vec3 c = Vec3.atCenterOf(best);
                            Vec3 away = p.position().subtract(c).multiply(1, 0, 1);
                            away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
                            Vec3 at = c.add(away.scale(2.5));
                            BlockPos floor = DepthsBuild.floorAt(level, Mth.floor(at.x), Mth.floor(at.z), best.getY());
                            float yaw = (float) (Mth.atan2(-away.z, -away.x) * Mth.RAD_TO_DEG) - 90f;
                            p.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, yaw, 25f);
                            return 1;
                        }))
                        .then(Commands.literal("bolt").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            Vec3 at = WeatherService.spotNear(p.serverLevel(), p.position().add(p.getLookAngle().multiply(4, 0, 4)), 1);
                            if (at != null) WeatherService.bolt(p.serverLevel(), g, at, 2.2f, 4f);
                            return 1;
                        }))
                        .then(Commands.literal("rock").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            Vec3 at = WeatherService.spotNear(p.serverLevel(), p.position().add(p.getLookAngle().multiply(4, 0, 4)), 1);
                            if (at != null) WeatherService.rock(p.serverLevel(), g, at, 1.7f, 4f);
                            return 1;
                        }))
                        .then(Commands.literal("profile").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            LivingEntity e = lookedAt(p);
                            if (e == null) {
                                ctx.getSource().sendFailure(Component.literal("look at a creature"));
                                return 0;
                            }
                            ElementalProfile prof = ElementalProfile.of(e);
                            ctx.getSource().sendSuccess(() -> e.getDisplayName().copy().append(": " + prof.describe()), false);
                            return 1;
                        }))
                        .then(Commands.literal("weaknesses").then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            WeaknessLog.of(p).clear();
                            WeaknessLog.sync(p);
                            return 1;
                        })))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> weatherArg() {
        return Commands.argument("weather", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("seed"),
                        Arrays.stream(GateWeather.values()).map(GateWeather::id)), b));
    }

    private static GateInstance inside(ServerPlayer p) throws CommandSyntaxException {
        if (!GateService.isDungeon(p.level())) throw NOT_INSIDE.create();
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        if (g == null) throw NOT_INSIDE.create();
        return g;
    }

    /** A gate in front of the player with its depths forced (before anyone goes in, so the rock is carved to match). */
    private static int gate(CommandContext<CommandSourceStack> ctx, String weather, String hidden, String trap) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        PlayerRank rank = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        if (rank == null) rank = PlayerRank.C;
        Direction look = p.getDirection();
        GateBlockEntity gate = GateService.spawnGate(level, p.blockPosition().relative(look, 5), rank, look.getOpposite(), true);
        GateInstance g = gate.gateId() == null ? null : GateData.get(p.server).get(gate.gateId());
        if (g == null) return 0;
        force(g, weather, hidden, trap);
        GateData.get(p.server).setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(info(g)), false);
        return 1;
    }

    /** Forces a gate's depths ("seed" leaves one as the seed has it). Before the cave is carved. */
    public static void force(GateInstance g, String weather, String hidden, String trap) {
        GateDepths d = g.depths();
        if (!"seed".equals(weather)) d.setWeather(GateWeather.byId(weather));
        if (!"seed".equals(hidden)) {
            d.setHidden("none".equals(hidden) ? null : HiddenKind.byId(hidden));
            g.replan();
        }
        if (!"seed".equals(trap)) {
            d.clearTraps();
            TrapKind kind = TrapKind.byId(trap);
            if (kind != null) {
                for (DungeonLayout.Room room : g.layout().rooms()) {
                    if (room.kind() == DungeonLayout.Kind.CAVERN && room.index() >= 2) {
                        d.setTrap(room.index(), kind);
                        break;
                    }
                }
            }
        }
    }

    /** One line about a cave's depths. */
    static String info(GateInstance g) {
        GateDepths d = g.depths();
        List<String> parts = new ArrayList<>();
        parts.add(g.rank() + " " + g.theme().name().toLowerCase(Locale.ROOT));
        parts.add("weather " + d.weather(g).id());
        HiddenKind h = d.hidden(g);
        parts.add("hidden " + (h == null ? "none" : h.id() + (d.hiddenOpen() ? " (open" + (d.hiddenEntered() ? ", entered" : "")
                + (d.hiddenDone() ? ", done" : "") + ")" : " (sealed)")));
        List<String> traps = new ArrayList<>();
        d.traps(g).forEach((room, kind) -> traps.add(kind.id() + "@" + room + (d.trapDone(room) ? " done" : d.trapTriggered(room) ? " sprung" : "")));
        parts.add("traps " + (traps.isEmpty() ? "none" : String.join(", ", traps)));
        if (!d.enabled()) parts.add("(older cave: weather only)");
        return String.join(" | ", parts);
    }

    /** The living thing the player is looking at, within 24 blocks. */
    static LivingEntity lookedAt(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = eye.add(p.getLookAngle().scale(24));
        AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(24)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, to, box, e -> e instanceof LivingEntity && e != p && e.isAlive(), 24 * 24);
        Entity e = hit == null ? null : hit.getEntity();
        return e instanceof LivingEntity l ? l : null;
    }

    private DepthsCommand() {}
}
