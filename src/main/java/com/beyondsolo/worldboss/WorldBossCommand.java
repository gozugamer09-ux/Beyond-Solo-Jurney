package com.beyondsolo.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ArmorSets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Map;

/**
 * /system worldboss ... testing commands (permission level 2):
 * <pre>
 * worldboss spawn &lt;kind&gt; [here]   a great threat comes some way off (here: 14 blocks ahead of you); a monarch's gate opens
 * worldboss land                 a monarch coming down lands now
 * worldboss phase &lt;n&gt;            the boss abroad jumps to phase n (no transition)
 * worldboss move &lt;name&gt;          the boss abroad uses a move now, on you
 * worldboss hurt &lt;amount&gt;        you hurt the boss abroad (counts towards your share)
 * worldboss kill                 the boss abroad falls (everyone who hurt it is paid)
 * worldboss withdraw             the boss abroad leaves the world
 * worldboss stop                 whatever is abroad is gone at once
 * worldboss status               one line about what is abroad, and when the next comes
 * worldboss next &lt;minutes&gt;       the next great threat comes in that many minutes
 * worldboss armor &lt;set&gt; [n]      a piece of monarch armor (stillness, hunger, storms; piece 0-3)
 * worldboss seal                 a Monarch's Seal
 * worldboss record               your record against great threats
 * </pre>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class WorldBossCommand {
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.literal("no world boss or monarch is abroad (or its land isn't loaded)"));
    private static final SimpleCommandExceptionType BUSY = new SimpleCommandExceptionType(Component.literal("a great threat is already abroad: /system worldboss stop first"));
    private static final SimpleCommandExceptionType NOT_HERE = new SimpleCommandExceptionType(Component.literal("great threats only come to the overworld"));
    private static final SimpleCommandExceptionType NO_ROOM = new SimpleCommandExceptionType(Component.literal("no open land near you for it"));
    private static final SimpleCommandExceptionType BAD = new SimpleCommandExceptionType(Component.literal("unknown kind, move or set"));

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("worldboss")
                        .then(Commands.literal("spawn").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(WorldBossKind.values()).map(k -> k.id), b))
                                .executes(ctx -> spawn(ctx, false))
                                .then(Commands.literal("here").executes(ctx -> spawn(ctx, true)))))
                        .then(Commands.literal("land").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getServer().overworld();
                            WorldBossData.Event ev = WorldBossData.get(level.getServer()).current();
                            if (ev == null || !ev.descending()) throw NONE.create();
                            ev.landAt = level.getGameTime();
                            return 1;
                        }))
                        .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                            ColossalBoss boss = boss(ctx);
                            boss.forcePhase((ServerLevel) boss.level(), IntegerArgumentType.getInteger(ctx, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("move").then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    ColossalBoss boss = WorldBosses.currentBoss(c.getSource().getServer().overworld());
                                    return SharedSuggestionProvider.suggest(boss == null ? java.util.List.of() : boss.moveNames(), b);
                                })
                                .executes(ctx -> {
                                    ColossalBoss boss = boss(ctx);
                                    ColossalBoss.Move m = boss.moveNamed(StringArgumentType.getString(ctx, "name"));
                                    if (m == null) throw BAD.create();
                                    if (ctx.getSource().getEntity() instanceof Player p) boss.setTarget(p);
                                    boss.start(m, boss.getTarget());
                                    return 1;
                                })))
                        .then(Commands.literal("hurt").then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(ctx -> {
                            ColossalBoss boss = boss(ctx);
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            boss.hurt(boss.damageSources().playerAttack(p), FloatArgumentType.getFloat(ctx, "amount"));
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%.0f / %.0f, phase %d, your share %.1f%%",
                                    boss.getHealth(), boss.getMaxHealth(), boss.phase(), boss.share(p.getUUID()) * 100)), false);
                            return 1;
                        })))
                        .then(Commands.literal("kill").executes(ctx -> {
                            ColossalBoss boss = boss(ctx);
                            boss.kill();
                            return 1;
                        }))
                        .then(Commands.literal("withdraw").executes(ctx -> {
                            boss(ctx).leave();
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            if (!WorldBosses.stop(ctx.getSource().getServer().overworld())) throw NONE.create();
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(WorldBossCommand::status))
                        .then(Commands.literal("next").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                            ServerLevel level = ctx.getSource().getServer().overworld();
                            WorldBossData.get(level.getServer()).setNextAt(level.getGameTime() + IntegerArgumentType.getInteger(ctx, "minutes") * 1200L);
                            return 1;
                        })))
                        .then(Commands.literal("armor").then(Commands.argument("set", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"stillness", "hunger", "storms"}, b))
                                .executes(ctx -> armor(ctx, -1))
                                .then(Commands.argument("piece", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> armor(ctx, IntegerArgumentType.getInteger(ctx, "piece"))))))
                        .then(Commands.literal("seal").executes(ctx -> {
                            WorldBosses.give(ctx.getSource().getPlayerOrException(), new ItemStack(WorldBossModule.MONARCH_SEAL.get()));
                            return 1;
                        }))
                        .then(Commands.literal("record").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            WorldBossRecord r = p.getData(WorldBossModule.RECORD.get());
                            StringBuilder sb = new StringBuilder("slain:");
                            for (WorldBossKind k : WorldBossKind.values()) sb.append(' ').append(k.id).append('=').append(r.slain(k));
                            sb.append("; top hunter ").append(r.topHunter()).append("; monarch armor ").append(r.armorPieces())
                                    .append(" (dry ").append(r.dryMonarchs()).append(')');
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
        WorldBossKind kind = WorldBossKind.byId(StringArgumentType.getString(ctx, "kind"));
        if (kind == null) throw BAD.create();
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (p.level().dimension() != Level.OVERWORLD) throw NOT_HERE.create();
        ServerLevel level = (ServerLevel) p.level();
        if (WorldBossData.get(level.getServer()).current() != null) throw BUSY.create();
        BlockPos spot;
        if (here) {
            Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(14));
            int x = Mth.floor(at.x), z = Mth.floor(at.z);
            spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        } else {
            spot = WorldBosses.findSpot(level, p.position(), 40, 70, level.random);
        }
        if (spot == null) throw NO_ROOM.create();
        WorldBosses.begin(level, kind, spot, true);
        BlockPos at = spot;
        ctx.getSource().sendSuccess(() -> Component.literal(kind.id + " comes at " + at.toShortString()), true);
        return 1;
    }

    private static ColossalBoss boss(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ColossalBoss boss = WorldBosses.currentBoss(ctx.getSource().getServer().overworld());
        if (boss == null || !boss.isAlive()) throw NONE.create();
        return boss;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        WorldBossData data = WorldBossData.get(level.getServer());
        WorldBossData.Event ev = data.current();
        long now = level.getGameTime();
        String next = data.nextAt() < 0 ? "not planned (no hunter strong enough yet)" : "in " + Math.max(0, (data.nextAt() - now) / 20) + " s";
        if (ev == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("nothing abroad; next " + next + "; slain: " + data.worldKills() + " world bosses, "
                    + data.monarchKills() + " monarchs"), false);
            return 1;
        }
        ColossalBoss boss = WorldBosses.currentBoss(level);
        StringBuilder sb = new StringBuilder(ev.kind.id).append(" at ").append(ev.last().toShortString());
        if (ev.descending()) sb.append(", landing in ").append(Math.max(0, (ev.landAt() - now) / 20)).append(" s");
        if (boss != null) {
            sb.append(String.format(", %.0f/%.0f, phase %d", boss.getHealth(), boss.getMaxHealth(), boss.phase()));
            for (Map.Entry<java.util.UUID, Float> e : WorldBosses.ranked(boss)) {
                ServerPlayer h = level.getServer().getPlayerList().getPlayer(e.getKey());
                sb.append("; ").append(h != null ? h.getGameProfile().getName() : e.getKey().toString().substring(0, 8))
                        .append(String.format(" %.0f%%", boss.share(e.getKey()) * 100));
            }
        }
        sb.append("; quiet for ").append((now - ev.engaged()) / 20).append(" s");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int armor(CommandContext<CommandSourceStack> ctx, int piece) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "set");
        ArmorSets.Set set = switch (name) {
            case "stillness" -> MonarchArmor.STILLNESS;
            case "hunger" -> MonarchArmor.HUNGER;
            case "storms" -> MonarchArmor.STORMS;
            default -> throw BAD.create();
        };
        ItemStack stack;
        if (piece < 0) {
            stack = MonarchArmor.piece(p, set, p.getRandom());
        } else {
            stack = new ItemStack(WorldBossModule.PIECES.get(set.name).get(piece).get());
            com.beyondsolo.gear.GearRolls.roll(stack, com.beyondsolo.item.ItemGrade.MONARCH, p.getRandom());
            stack.set(WorldBossModule.AURA.get(), MonarchArmor.auraOf(p));
        }
        WorldBosses.give(p, stack);
        return 1;
    }

    private WorldBossCommand() {}
}
