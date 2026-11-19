package com.beyondsolo.command.solo;

import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.light.Chapel;
import com.beyondsolo.light.LightAPI;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.light.Vigil;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;

/**
 * The light, a hunter's side of it: /solo light swear (sworn at once, with the powers it gives at their level), unswear,
 * invest (a mantle at once: a ruler of light), divest, call (to the Investiture), vigil (begun now, skipped on, or ended),
 * chapel (into the Envoy's chapel of a running awakening trial, or before its wall), power (one of the light's powers
 * now) and reset (the whole record). The rulers of light that come down on the world are /solo ruler ({@link SoloRulers}).
 */
final class SoloLight {
    /** The mantles, by name ("verdict"). */
    static final List<String> MANTLES = Arrays.stream(Mantle.values()).map(Mantle::id).toList();
    /** The powers in crown-key order. */
    static final List<String> POWERS = List.of("sanctum", "judgement", "seraph", "authority");

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> vigil = Commands.literal("vigil");
        vigil.then(Solo.target(Commands.literal("next"), (ctx, p) -> {
            if (!Vigil.keeping(p)) throw Solo.error("no_vigil", p.getDisplayName());
            Vigil.advance(p);
            return Solo.t("light.vigil_next", p.getDisplayName());
        }));
        vigil.then(Solo.target(Commands.literal("stop"), (ctx, p) -> {
            if (!Vigil.keeping(p)) throw Solo.error("no_vigil", p.getDisplayName());
            Vigil.cancel(p);
            return Solo.t("light.vigil_stop", p.getDisplayName());
        }));
        Solo.target(vigil, (ctx, p) -> {
            Solo.needEvaluated(p);
            if (!LightAPI.sworn(p)) throw Solo.error("not_sworn", p.getDisplayName());
            if (Vigil.keeping(p)) throw Solo.error("in_vigil", p.getDisplayName());
            LightAPI.setRetryAt(p, 0);
            if (!Vigil.begin(p, true)) throw Solo.error("vigil_refused", p.getDisplayName());
            return Solo.t("light.vigil", p.getDisplayName());
        });

        LiteralArgumentBuilder<CommandSourceStack> chapel = Commands.literal("chapel");
        chapel.then(Solo.target(Commands.literal("wall"), (ctx, p) -> chapelWall(p)));
        Solo.target(chapel, (ctx, p) -> chapel(p));

        root.then(Commands.literal("light")
                .then(Solo.targets(Commands.literal("swear"), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    if (!SystemAPI.hasSystem(p)) throw Solo.error("no_system", p.getDisplayName());
                    // a crown is taken back by the light's own call (one road or the other)
                    if (com.beyondsolo.monarch.MonarchAPI.crowned(p)) com.beyondsolo.monarch.MonarchAPI.uncrown(p);
                    LightAPI.awoken(p);
                    return Solo.t("light.sworn", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("unswear"), (ctx, p) -> {
                    if (!LightAPI.sworn(p)) throw Solo.error("not_sworn", p.getDisplayName());
                    LightAPI.unswear(p);
                    return Solo.t("light.unsworn", p.getDisplayName());
                }))
                .then(Commands.literal("invest").then(Solo.targets(Solo.word("mantle", MANTLES::stream), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    if (!SystemAPI.hasSystem(p)) throw Solo.error("no_system", p.getDisplayName());
                    Mantle m = mantle(StringArgumentType.getString(ctx, "mantle"));
                    Vigil.cancel(p);
                    LightAPI.invest(p, m);
                    return Solo.t("light.invested", p.getDisplayName(), m.title());
                })))
                .then(Solo.targets(Commands.literal("divest"), (ctx, p) -> {
                    if (!LightAPI.invested(p)) throw Solo.error("not_invested", p.getDisplayName());
                    LightAPI.divest(p);
                    return Solo.t("light.divested", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("call"), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    if (!LightAPI.sworn(p)) throw Solo.error("not_sworn", p.getDisplayName());
                    LightAPI.call(p);
                    return Solo.t("light.called", p.getDisplayName());
                }))
                .then(vigil)
                .then(chapel)
                .then(Commands.literal("power").then(Solo.targets(Solo.word("power", POWERS::stream), (ctx, p) -> {
                    if (!LightAPI.sworn(p)) throw Solo.error("not_sworn", p.getDisplayName());
                    String name = Solo.lower(StringArgumentType.getString(ctx, "power"));
                    int key = POWERS.indexOf(name);
                    if (key < 0) throw Solo.error("unknown_light_power", name);
                    Skill s = RulerPowers.onKey(LightAPI.mantle(p), key);
                    if (s == null || !SkillAPI.knows(p, s)) throw Solo.error("light_power_missing", p.getDisplayName(), name);
                    SystemAPI.restoreMana(p, 99999);
                    SkillAPI.resetCooldowns(p);
                    if (!RulerPowers.castKey(p, key)) throw Solo.error("power_failed", p.getDisplayName(), s.displayName());
                    return Solo.t("light.power", p.getDisplayName(), s.displayName());
                })))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    LightAPI.reset(p);
                    return Solo.t("light.reset", p.getDisplayName());
                })));
    }

    /** The wall of their awakening temple's chapel down, and them before the Envoy. */
    private static net.minecraft.network.chat.Component chapel(net.minecraft.server.level.ServerPlayer p) throws CommandSyntaxException {
        GateInstance g = AwakeningService.trialAt(p);
        if (g == null || !Chapel.has(g) || SystemAPI.hasSystem(p)) throw Solo.error("no_chapel", p.getDisplayName());
        ServerLevel level = p.serverLevel();
        if (!g.depths().hiddenOpen()) {
            DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
            if (m != null) HiddenRooms.openAt(level, BlockPos.containing(m.center()), p);
        }
        DepthsBuild.Door altar = DepthsBuild.chapelAltar(level, g);
        BlockPos stand = altar.pos().relative(altar.facing(), 4);
        // down onto the chapel's floor (its dais may stand a step above it)
        int y = stand.getY() + 2;
        while (y > stand.getY() - 4 && level.getBlockState(new BlockPos(stand.getX(), y - 1, stand.getZ())).isAir()) y--;
        p.teleportTo(level, stand.getX() + 0.5, y, stand.getZ() + 0.5, altar.facing().getOpposite().toYRot(), 0);
        return Solo.t("light.chapel", p.getDisplayName());
    }

    /** Them in the temple's antechamber before the chapel's wall (open or not), looking at it. */
    private static net.minecraft.network.chat.Component chapelWall(net.minecraft.server.level.ServerPlayer p) throws CommandSyntaxException {
        GateInstance g = AwakeningService.trialAt(p);
        DepthsBuild.Mouth m = g == null || !Chapel.has(g) || SystemAPI.hasSystem(p) ? null : DepthsBuild.sealMouth(g);
        if (m == null) throw Solo.error("no_chapel", p.getDisplayName());
        ServerLevel level = p.serverLevel();
        // the mouth's way points out of the antechamber, into the wall
        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(m.dir().x, 0, m.dir().z);
        dir = dir.lengthSqr() < 1.0E-4 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : dir.normalize();
        net.minecraft.world.phys.Vec3 at = m.center().subtract(dir.scale(m.radius() + 3));
        BlockPos stand = BlockPos.containing(at);
        int y = stand.getY() + 2;
        while (y > stand.getY() - 6 && level.getBlockState(new BlockPos(stand.getX(), y - 1, stand.getZ())).isAir()) y--;
        float yaw = (float) (net.minecraft.util.Mth.atan2(dir.z, dir.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90f;
        p.teleportTo(level, at.x, y, at.z, yaw, 0);
        return Solo.t("light.chapel_wall", p.getDisplayName());
    }

    /** The mantle with this name. */
    static Mantle mantle(String name) throws CommandSyntaxException {
        String id = Solo.lower(name);
        for (Mantle m : Mantle.values()) if (m.id().equals(id)) return m;
        throw Solo.error("unknown_mantle", name);
    }

    private SoloLight() {}
}
