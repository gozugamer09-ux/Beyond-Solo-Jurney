package com.beyondsolo.shadow;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * /system shadow ... testing commands (permission level 2):
 * info, give &lt;entity&gt; [rank] [boss], soul &lt;entity&gt; [boss] (a soul 3 blocks ahead), next success|fail|random,
 * rise, summon, recall, mount, xp &lt;n&gt;, rankup [n], kill (fells every shadow out), reset.
 */
public final class ShadowCommand {
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown entity type"));
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.literal("No shadows"));

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("shadow")
                        .then(Commands.literal("info").executes(ShadowCommand::info))
                        .then(Commands.literal("give").then(typeArg()
                                .executes(ctx -> give(ctx, null, false))
                                .then(rankArg().executes(ctx -> give(ctx, rank(ctx), false))
                                        .then(Commands.argument("boss", BoolArgumentType.bool())
                                                .executes(ctx -> give(ctx, rank(ctx), BoolArgumentType.getBool(ctx, "boss")))))))
                        .then(Commands.literal("soul").then(typeArg()
                                .executes(ctx -> soul(ctx, false))
                                .then(Commands.argument("boss", BoolArgumentType.bool())
                                        .executes(ctx -> soul(ctx, BoolArgumentType.getBool(ctx, "boss"))))))
                        .then(Commands.literal("next").then(Commands.argument("result", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"success", "fail", "random"}, b))
                                .executes(ctx -> {
                                    String r = StringArgumentType.getString(ctx, "result");
                                    ShadowService.forceNext(ctx.getSource().getPlayerOrException(), r.equals("success") ? Boolean.TRUE : r.equals("fail") ? Boolean.FALSE : null);
                                    return 1;
                                })))
                        .then(Commands.literal("rise").executes(ctx -> {
                            ShadowService.rise(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("summon").executes(ctx -> ShadowService.summonAll(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("recall").executes(ctx -> ShadowService.recallAll(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("mount").executes(ctx -> {
                            ShadowService.mount(ctx.getSource().getPlayerOrException(), null);
                            return 1;
                        }))
                        .then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int n = IntegerArgumentType.getInteger(ctx, "amount");
                                    for (ShadowRecord r : ShadowService.army(p).shadows()) ShadowService.addXp(p, r, n);
                                    return 1;
                                })))
                        .then(Commands.literal("rankup")
                                .executes(ctx -> rankUp(ctx, -1))
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> rankUp(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
                        .then(Commands.literal("kill").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int n = 0;
                            for (var id : java.util.List.copyOf(ShadowService.army(p).active().values())) {
                                if (p.serverLevel().getEntity(id) instanceof ShadowEntity e) {
                                    e.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
                                    n++;
                                }
                            }
                            return n;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ShadowService.reset(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> typeArg() {
        return Commands.argument("entity", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rankArg() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ShadowRank.values()).map(ShadowRank::key), b));
    }

    private static EntityType<?> type(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "entity");
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElseThrow(UNKNOWN::create);
    }

    private static ShadowRank rank(CommandContext<CommandSourceStack> ctx) {
        String s = StringArgumentType.getString(ctx, "rank");
        for (ShadowRank r : ShadowRank.values()) if (r.key().equalsIgnoreCase(s)) return r;
        return ShadowRank.SOLDIER;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ShadowRank rank, boolean boss) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ShadowRecord r = ShadowService.give(p, type(ctx), rank, boss);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave ").append(r.displayName()), false);
        return 1;
    }

    private static int soul(CommandContext<CommandSourceStack> ctx, boolean boss) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        EntityType<?> t = type(ctx);
        float hp = 20, dmg = 3, speed = 0.25f;
        if (t.create(p.serverLevel()) instanceof LivingEntity le) {
            hp = le.getMaxHealth();
            dmg = le.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    ? (float) le.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 3;
            le.discard();
        }
        if (boss) hp = Math.max(hp, 400);
        Vec3 at = ShadowService.ground(p.serverLevel(), p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(3)));
        ShadowService.addSoul(p, p.serverLevel(), at, t, hp, dmg, speed, boss, boss ? 3 : -1);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ShadowData army = ShadowService.army(p);
        StringBuilder sb = new StringBuilder("Army " + army.size() + "/" + ShadowService.capacity(p) + ", out " + army.activeCount()
                + "/" + ShadowService.activeLimit(p) + ", necromancer " + ShadowService.canRaise(p));
        int i = 0;
        for (ShadowRecord r : army.shadows()) {
            sb.append("\n").append(i++).append(": ").append(r.displayName().getString()).append(" ").append(r.rank().key())
                    .append(" L").append(r.level()).append(" ").append(r.xp()).append("/").append(r.xpToNext())
                    .append(army.isActive(r.id()) ? " [out]" : "");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return army.size();
    }

    private static int rankUp(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ShadowData army = ShadowService.army(p);
        if (army.size() == 0) throw NONE.create();
        ShadowRecord r = index >= 0 ? army.byIndex(index) : null;
        if (r == null) {
            // the first one out in the world, otherwise the first one
            for (ShadowRecord s : army.shadows()) if (army.isActive(s.id())) {
                r = s;
                break;
            }
            if (r == null) r = army.byIndex(0);
        }
        ShadowService.rankUp(p, r);
        return 1;
    }

    private ShadowCommand() {}
}
