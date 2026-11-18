package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Testing and filming the System's situation quests (permission level 2):
 * <ul>
 *   <li>/system situation list — every situation, and how long until it may start again for you</li>
 *   <li>/system situation start &lt;situation&gt; — starts one now, ignoring its cooldown and the gap (it still needs what it
 *   is about: the creature for "foe" or "pack" is the nearest monster, "protect" needs a villager and a monster near,
 *   "cure" a zombie villager, "raid" a raid, "beyond_rank" a gate, a place its structure)</li>
 *   <li>/system situation follow [kind] — brings what is waiting to come back (a nemesis, a revenge) forward to now</li>
 *   <li>/system situation secret &lt;id&gt; — reveals a secret quest</li>
 *   <li>/system situation watch — what the System has noticed about you, and what is waiting</li>
 *   <li>/system situation clear — ends your situations, forgets what was waiting and the cooldowns</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SituationCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("situation")
                        .then(Commands.literal("list").executes(SituationCommand::list))
                        .then(Commands.literal("start").then(Commands.argument("situation", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Situations.all().stream().map(Situation::path), b))
                                .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "situation")))))
                        .then(Commands.literal("follow")
                                .executes(ctx -> follow(ctx, null))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("nemesis", "revenge"), b))
                                        .executes(ctx -> follow(ctx, StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("secret").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Secrets.ALL, b))
                                .executes(ctx -> secret(ctx, StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("watch").executes(SituationCommand::watch))
                        .then(Commands.literal("clear").executes(SituationCommand::clear))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        QuestData data = QuestEngine.data(p);
        long now = QuestEngine.gameTime(p);
        for (Situation s : Situations.all()) {
            long wait = Math.max(0, data.cooldownUntil(s.id()) - now) / 20;
            Quest running = Situations.running(p, s);
            String state = running != null ? "running: " + Run.of(running).stage() : wait > 0 ? "quiet for " + wait + "s" : "ready";
            ctx.getSource().sendSuccess(() -> Component.literal(s.path() + " - " + state), false);
        }
        long gap = Math.max(0, data.cooldownUntil(Situations.GAP_KEY) - now) / 20;
        ctx.getSource().sendSuccess(() -> Component.literal("gap before the next calm one: " + gap + "s"), false);
        return Situations.all().size();
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Situation s = Situations.byId(id);
        if (s == null) {
            ctx.getSource().sendFailure(Component.literal("No situation " + id));
            return 0;
        }
        if (!SystemAPI.isAwakened(p) || !QuestEngine.data(p).started()) {
            ctx.getSource().sendFailure(Component.literal("Awaken first: the System has no quests for you yet"));
            return 0;
        }
        Quest q = make(p, s);
        if (q == null) {
            ctx.getSource().sendFailure(Component.literal(s.path() + " needs what it is about near you (see /help system situation)"));
            return 0;
        }
        QuestData data = QuestEngine.data(p);
        data.setCooldown(s.id(), 0);
        data.setCooldown(Situations.GAP_KEY, 0);
        if (!Situations.begin(p, s, q)) {
            ctx.getSource().sendFailure(Component.literal("Something stands in its way: an emergency, or another situation running"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Started " + s.path()), false);
        return 1;
    }

    /** The first quest of a situation, built from what is around the player (no chance or cooldown). */
    @Nullable
    private static Quest make(ServerPlayer p, Situation s) {
        Mob monster = nearest(p, Mob.class, m -> m instanceof Enemy && !Foes.ownedBy(m, p));
        if (s == Situations.FOE) return monster == null ? null : Situations.FOE.survive(p, monster, Foes.ratio(monster, p));
        if (s == Situations.LAST_STAND) return Situations.LAST_STAND.stand(p);
        if (s == Situations.PACK_LEADER) return monster == null ? null : Situations.PACK_LEADER.head(p, monster, PackLeader.packOf(monster, p));
        if (s == Situations.PROTECT) {
            AbstractVillager v = nearest(p, AbstractVillager.class, LivingEntity::isAlive);
            if (v == null || monster == null) return null;
            monster.setTarget(v);
            return Situations.PROTECT.protect(p, v, monster);
        }
        if (s == Situations.DEFEND) {
            Raid raid = ((ServerLevel) p.level()).getRaidAt(p.blockPosition());
            return raid == null ? null : Situations.DEFEND.defend(p, raid);
        }
        if (s == Situations.BEYOND_RANK) {
            GateInstance g = BeyondRank.gateOf(p);
            return g == null ? null : Situations.BEYOND_RANK.clear(p, g, Math.max(1, g.rank().tier() - SystemAPI.get(p).rank().tier()));
        }
        if (s == Situations.UNTOUCHABLE) return Situations.UNTOUCHABLE.first(p);
        if (s == Situations.FRENZY) return Situations.FRENZY.first(p);
        if (s == Situations.CATCH_BREATH) return Situations.CATCH_BREATH.rest(p);
        if (s == Situations.CURE) {
            ZombieVillager z = nearest(p, ZombieVillager.class, LivingEntity::isAlive);
            return z == null ? null : Situations.CURE.cure(p, z);
        }
        if (s instanceof Places.Place place) {
            StructureStart at = place.at(p);
            if (at == null) return null;
            return place.detectHere(p, at);
        }
        if (s == Situations.REVENGE) {
            if (monster == null) return null;
            CompoundTag killer = new CompoundTag();
            killer.putUUID(Run.TARGET, monster.getUUID());
            killer.putString(Revenge.DIM, p.level().dimension().location().toString());
            killer.putLong(Revenge.POS, monster.blockPosition().asLong());
            Run.setName(killer, Foes.name(monster), p.registryAccess());
            Situations.markTarget(monster);
            return Situations.REVENGE.hunt(p, killer);
        }
        return s.detect(p, Situations.watch(p));
    }

    @Nullable
    private static <T extends LivingEntity> T nearest(ServerPlayer p, Class<T> type, java.util.function.Predicate<T> ok) {
        return p.level().getEntitiesOfClass(type, p.getBoundingBox().inflate(32), ok::test).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    private static int follow(CommandContext<CommandSourceStack> ctx, @Nullable String kind) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String full = kind == null ? null : kind.contains(":") ? kind : BeyondSolo.id(kind).toString();
        int n = FollowUps.hurry(p, full);
        ctx.getSource().sendSuccess(() -> Component.literal(n + " follow-up(s) due now"), false);
        return n;
    }

    private static int secret(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!Secrets.ALL.contains(id)) {
            ctx.getSource().sendFailure(Component.literal("No secret " + id));
            return 0;
        }
        boolean ok = Secrets.reveal(p, id);
        ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Revealed " + id : id + " was already found"), false);
        return ok ? 1 : 0;
    }

    private static int watch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Watch w = Situations.watch(p);
        long now = QuestEngine.gameTime(p);
        SituationData data = FollowUps.data(p);
        ctx.getSource().sendSuccess(() -> Component.literal("clean kills " + w.cleanKills + ", kills in 15s " + w.killsWithin(now, 300)
                + ", last hurt " + Math.max(0, (now - w.lastHurt) / 20) + "s ago, gate " + w.gate + (w.hurtInGate ? " (hurt in it)" : "")), false);
        for (SituationData.FollowUp f : data.followUps()) {
            long in = Math.max(0, f.due() - now) / 20;
            ctx.getSource().sendSuccess(() -> Component.literal("waiting: " + f.kind() + " in " + in + "s"), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("last killer: " + (data.lastKiller() == null ? "none" : data.lastKiller().getString(Run.NAME))
                + ", nemeses slain " + data.nemesesSlain() + ", secrets " + data.secretsFound()), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Situations.clear(p);
        QuestData data = QuestEngine.data(p);
        for (Situation s : Situations.all()) data.setCooldown(s.id(), 0);
        data.setCooldown(Situations.GAP_KEY, 0);
        ctx.getSource().sendSuccess(() -> Component.literal("Situations cleared"), false);
        return 1;
    }

    private SituationCommand() {}
}
