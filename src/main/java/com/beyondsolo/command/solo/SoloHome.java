package com.beyondsolo.command.solo;

import com.beyondsolo.home.HomeAdmin;
import com.beyondsolo.home.HomeTier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hunters' homes ({@code home.Homes}):
 * <pre>
 * /solo home info [player]                                   how their home stands
 * /solo home give studio|family|hall|penthouse|none [players] owns that part now, free (none: taken away)
 * /solo home go [player]                                     home at once (no standing still, no wait)
 * /solo home visit &lt;host&gt; [player]                           into the host's home at once, party or not
 * /solo home leave [player]                                  back out into the world
 * /solo home build                                           every home going up is built now
 * /solo home rebuild [player]                                their home is built again from nothing
 * /solo home rest [players]                                  a night's rest now, Well Rested even if they had it today
 * /solo home ready [players]                                 no wait before going home again
 * /solo home trophies &lt;count&gt; [players]                      every great threat of the trophy hall slain that often
 * </pre>
 */
final class SoloHome {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("home")
                .then(Solo.target(Commands.literal("info"), (ctx, p) -> {
                    for (Component line : HomeAdmin.info(p)) ctx.getSource().sendSuccess(() -> line, false);
                    return null;
                }))
                .then(Commands.literal("give").then(Solo.targets(Solo.word("tier", HomeAdmin.TIERS::stream), (ctx, p) -> {
                    String id = Solo.lower(StringArgumentType.getString(ctx, "tier"));
                    if (!HomeAdmin.TIERS.contains(id)) throw Solo.error("unknown_home", id, String.join(", ", HomeAdmin.TIERS));
                    HomeTier tier = HomeTier.byId(id);
                    if (tier != HomeTier.NONE) Solo.needEvaluated(p);
                    HomeAdmin.give(p, tier);
                    return tier == HomeTier.NONE ? Solo.t("home.taken", p.getDisplayName()) : Solo.t("home.given", p.getDisplayName(), tier.displayName());
                })))
                .then(Solo.target(Commands.literal("go"), (ctx, p) -> {
                    if (!HomeAdmin.go(p)) throw Solo.error("no_home", p.getDisplayName());
                    return Solo.t("home.went", p.getDisplayName());
                }))
                .then(Commands.literal("visit").then(Solo.target(Commands.argument("host", EntityArgument.player()), (ctx, p) -> {
                    ServerPlayer host = EntityArgument.getPlayer(ctx, "host");
                    if (!HomeAdmin.visit(p, host.getUUID())) throw Solo.error("no_home", host.getDisplayName());
                    return Solo.t("home.visited", p.getDisplayName(), host.getDisplayName());
                })))
                .then(Solo.target(Commands.literal("leave"), (ctx, p) -> {
                    if (!HomeAdmin.leave(p)) throw Solo.error("not_in_home", p.getDisplayName());
                    return Solo.t("home.left", p.getDisplayName());
                }))
                .then(Commands.literal("build").executes(ctx -> {
                    if (!HomeAdmin.finishBuilds(ctx.getSource().getServer())) throw Solo.error("no_home_world");
                    ctx.getSource().sendSuccess(() -> Solo.t("home.built"), true);
                    return 1;
                }))
                .then(Solo.target(Commands.literal("rebuild"), (ctx, p) -> {
                    if (!HomeAdmin.rebuild(p)) throw Solo.error("no_home", p.getDisplayName());
                    return Solo.t("home.rebuilding", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("rest"), (ctx, p) -> {
                    HomeAdmin.rest(p);
                    return Solo.t("home.rested", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("ready"), (ctx, p) -> {
                    HomeAdmin.ready(p);
                    return Solo.t("home.ready", p.getDisplayName());
                }))
                .then(Commands.literal("trophies").then(Solo.targets(Commands.argument("count", IntegerArgumentType.integer(0, 999)), (ctx, p) -> {
                    int count = IntegerArgumentType.getInteger(ctx, "count");
                    HomeAdmin.trophies(p, count);
                    return Solo.t("home.trophies", p.getDisplayName(), count);
                }))));
    }

    private SoloHome() {}
}
