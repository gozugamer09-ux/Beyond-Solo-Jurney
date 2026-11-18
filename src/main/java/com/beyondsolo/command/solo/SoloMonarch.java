package com.beyondsolo.command.solo;

import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Monarchs: /solo monarch crown (a crown at once, with its powers), uncrown, call (to the empty throne), reset (the
 * whole record), jump (through a running throne trial) and power (a crown's power now). The throne trial itself starts
 * with /solo trial throne ({@link SoloWorld}).
 */
final class SoloMonarch {
    /** The crowns, by name ("night"). */
    static final List<String> CROWNS = Arrays.stream(Dominion.values()).map(Dominion::id).toList();
    /** The powers in crown-key order. */
    static final List<String> POWERS = List.of("domain", "dread", "crown", "authority");
    /** Where a throne trial can be skipped to: a court wave, the Arbiter, the throne, or a crown claimed at once. */
    static final List<String> PARTS = Stream.concat(Stream.of("1", "2", "arbiter", "throne"), CROWNS.stream()).toList();

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("monarch")
                .then(Commands.literal("crown").then(Solo.targets(Solo.word("crown", CROWNS::stream), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    Dominion d = crown(StringArgumentType.getString(ctx, "crown"));
                    MonarchAPI.crown(p, d);
                    return Solo.t("monarch.crowned", p.getDisplayName(), d.title());
                })))
                .then(Solo.targets(Commands.literal("uncrown"), (ctx, p) -> {
                    if (!MonarchAPI.crowned(p)) throw Solo.error("not_monarch", p.getDisplayName());
                    MonarchAPI.uncrown(p);
                    return Solo.t("monarch.uncrowned", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("call"), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    MonarchAPI.call(p);
                    return Solo.t("monarch.called", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    MonarchAPI.reset(p);
                    return Solo.t("monarch.reset", p.getDisplayName());
                }))
                .then(Commands.literal("jump").then(Solo.target(Solo.word("part", PARTS::stream), (ctx, p) -> {
                    String part = Solo.lower(StringArgumentType.getString(ctx, "part"));
                    if (!PARTS.contains(part)) throw Solo.error("unknown_trial_part", part);
                    Dominion d = Dominion.byId(part);
                    if (!ThroneTrial.jump(p, d != null ? "throne" : part, d)) throw Solo.error("not_in_throne", p.getDisplayName());
                    return Solo.t("monarch.jumped", p.getDisplayName(), d != null ? d.title() : Component.literal(part));
                })))
                .then(Commands.literal("power").then(Solo.targets(Solo.word("power", POWERS::stream), (ctx, p) -> {
                    Dominion d = MonarchAPI.dominion(p);
                    if (d == null) throw Solo.error("not_monarch", p.getDisplayName());
                    String name = Solo.lower(StringArgumentType.getString(ctx, "power"));
                    int key = POWERS.indexOf(name);
                    if (key < 0) throw Solo.error("unknown_power", name);
                    Skill s = MonarchPowers.onKey(d, key);
                    if (s == null || !SkillAPI.knows(p, s)) throw Solo.error("not_monarch", p.getDisplayName());
                    SystemAPI.restoreMana(p, 99999);
                    SkillAPI.resetCooldowns(p);
                    if (!MonarchPowers.castCrown(p, key)) throw Solo.error("power_failed", p.getDisplayName(), s.displayName());
                    return Solo.t("monarch.power", p.getDisplayName(), s.displayName());
                }))));
    }

    /** The crown with this name. */
    static Dominion crown(String name) throws CommandSyntaxException {
        Dominion d = Dominion.byId(Solo.lower(name));
        if (d == null) throw Solo.error("unknown_crown", name);
        return d;
    }

    private SoloMonarch() {}
}
