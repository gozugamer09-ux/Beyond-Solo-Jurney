package com.beyondsolo.isle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.job.JobTrial;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.network.IslePayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.RewardBoxItem;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Halcyon raid: every so often the Hunter Association calls the strong hunters ({@link IsleConfig#minRank}) to take
 * back Halcyon Isle, an island resort the swarm overran years ago ({@link IsleWorld}).
 * <ol>
 *   <li><b>The call</b> ({@link IsleRaid.Phase#MUSTER}): a System alert and a chat line to answer it by
 *   ({@code /isle join}, {@code /isle decline}). While it is open the island is built, or set up again.</li>
 *   <li><b>The landing</b> ({@link IsleRaid.Phase#BEACH}): the hunters who answered are taken to the Association's camp on
 *   the south beach together, with the strongest hunters of the national rankings beside them ({@link #ALLY}). The swarm
 *   comes down on the landing in waves.</li>
 *   <li><b>The nests</b> ({@link IsleRaid.Phase#NESTS}): every nest's heart has to burst ({@link NestHeartEntity}); a nest
 *   broods while hunters are near it.</li>
 *   <li><b>The hive</b> ({@link IsleRaid.Phase#HIVE}): the last nest burned, the great hive's mouth tears open. Down its
 *   pit is the queen's chamber.</li>
 *   <li><b>The queen</b> ({@link QueenBoss}), and when she falls <b>the king</b> born of her cocoon ({@link KingBoss}).
 *   Their bars, banners and shares are the great threats' ({@link WorldBosses}).</li>
 * </ol>
 * A hunter who would die on the island is carried off it by the Association's medics instead (half pay if it is won);
 * the raid is lost when none is left, or when time runs out. Retaking it pays everyone who stayed: XP and gold several
 * times an S-rank clear's, the Association's merit, a blessed box, its title.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class IsleRaids {
    static final String KEY = "isle.beyondsolo.";
    /** On every creature of the swarm, the queen and the king: the raid it belongs to. */
    public static final String TAG = "beyondsolo_isle_raid";
    public static final String QUEST_RETAKE = BeyondSolo.id("isle/retake").toString();
    public static final String QUEST_KING = BeyondSolo.id("isle/king").toString();
    public static final String NEST_KEY = BeyondSolo.id("isle_nest").toString();
    public static final String QUEEN_KEY = BeyondSolo.id("isle_queen").toString();
    public static final String KING_KEY = BeyondSolo.id("isle_king").toString();

    /** Everyone called has answered: the boats leave this long after. */
    static final int LAUNCH_DELAY = 20 * 4;
    /** From the landing to the first wave. */
    static final int FIRST_WAVE = 20 * 10;
    /** The least time between waves, and the most (the next comes even if the last one stands). */
    static final int WAVE_GAP = 20 * 10;
    static final int WAVE_MAX = 20 * 70;
    /** From the queen's fall to the king's birth. */
    static final int KING_DELAY = 20 * 7;
    /** How long the hunters stay on a retaken island before the Association takes them home (a lost one: less). */
    static final int AFTER_TICKS = 20 * 45;
    static final int FAILED_TICKS = 20 * 10;
    /** No hunter of the raid on the island this long (all gone offline): it is lost. */
    static final int EMPTY_GRACE = 20 * 60;
    /** A nest broods while a hunter is this near it. */
    static final int NEST_REACH = 30;
    static final int STATUS_EVERY = 40;
    /** A hunter carried off the island keeps this share of their health. */
    static final float CARRIED_HEALTH = 0.25f;
    /** Ticks a hunter crouches in the ring of light at the chamber's south end to climb back up. */
    static final int CLIMB_TICKS = 20;
    /** An S-rank gate's clear XP ({@code GateTheme.clearXp} for S: 300 x 6 x 6). */
    static final long S_CLEAR_XP = 300L * 36;

    /** What a hunter is to the raid, for the HUD. */
    static final int YOU_NONE = 0, YOU_CALLED = 1, YOU_JOINED = 2, YOU_DECLINED = 3, YOU_FIGHTING = 4, YOU_FALLEN = 5, YOU_LEFT = 6;

    /** Game tests let the Association call on its own with this (it never does on the game-test server otherwise). */
    static boolean testCalls;
    @Nullable private static IsleBuilder building;
    @Nullable private static IsleWorld.Layout cached;
    /** Hunters crouching in the ring of light at the chamber's south end, since when. */
    private static final Map<UUID, Long> CLIMBING = new HashMap<>();
    /** When each nest of {@link #broodRaid} last brooded. */
    private static final Map<Integer, Long> BROODED = new HashMap<>();
    @Nullable private static UUID broodRaid;

    /** What a command or a hunter's answer did: done, or why not (lang keys under {@code isle.beyondsolo.ok/refuse}). */
    public record Outcome(boolean ok, String key, Object... args) {
        static Outcome yes(String key, Object... args) {
            return new Outcome(true, key, args);
        }

        static Outcome no(String key, Object... args) {
            return new Outcome(false, key, args);
        }

        public Component text() {
            return Component.translatable(KEY + (ok ? "ok." : "refuse.") + key, args);
        }
    }

    /** What the allies from the national rankings fight for: this raid, beside its hunters. */
    static final GuildHunterEntity.Cause ALLY = new GuildHunterEntity.Cause() {
        @Override
        public boolean keeps(GuildHunterEntity e) {
            if (!(e.level() instanceof ServerLevel level)) return false;
            IsleRaid r = IsleData.get(level.getServer()).raid();
            IsleRaid.Ally a = r == null ? null : ally(r, e.hunterId());
            if (a == null || a.fallen() || !e.getUUID().equals(a.entity)) return false;
            if (r.phase().landed()) return true;
            // after the island is retaken they stay a little to see it
            return r.phase() == IsleRaid.Phase.CLEARED && now(level.getServer()) - r.endedAt < 20 * 15;
        }

        @Override
        public @Nullable ServerPlayer leader(GuildHunterEntity e) {
            if (!(e.level() instanceof ServerLevel level)) return null;
            IsleRaid r = IsleData.get(level.getServer()).raid();
            if (r == null) return null;
            ServerPlayer best = null;
            double bestD = Double.MAX_VALUE;
            for (ServerPlayer p : present(level.getServer(), level, r)) {
                double d = p.distanceToSqr(e);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }

        @Override
        public void fell(ServerLevel level, GuildHunterEntity e) {
            IsleData data = IsleData.get(level.getServer());
            IsleRaid r = data.raid();
            IsleRaid.Ally a = r == null ? null : ally(r, e.hunterId());
            if (a == null || a.fallen()) return;
            a.hired().fallen = true;
            a.entity = null;
            data.setDirty();
            for (ServerPlayer p : present(level.getServer(), level, r)) {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(KEY + "notify.title"),
                        Component.translatable(KEY + "notify.ally_down", a.hired().name)));
            }
        }
    };

    // ================================================================ who and where

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static IsleRecord record(ServerPlayer p) {
        return p.getData(IsleModule.RECORD.get());
    }

    /** The island's layout seed: one island for a world. */
    static long isleSeed(MinecraftServer server) {
        return server.overworld().getSeed() ^ 0x4A1C_10E5_5EEDL;
    }

    /** The island's layout for a raid (planned once and kept). */
    static IsleWorld.Layout layout(ServerLevel isle, IsleRaid r) {
        return layout(isle, r.seed, r.nests());
    }

    static IsleWorld.Layout layout(ServerLevel isle, long seed, int nests) {
        IsleWorld.Layout l = cached;
        if (l == null || l.seed() != seed || l.nests().size() != nests || l.small() == IsleWorld.isIsle(isle)) {
            l = cached = IsleWorld.plan(isle, seed, nests);
        }
        return l;
    }

    /** The raid's number on the wire (the great threats' event key). */
    static long eventKey(IsleRaid r) {
        return WorldBosses.eventKey(r.id);
    }

    /** May the Association call this hunter: awakened, and strong enough (anyone awakened, for a raid a command called). */
    static boolean eligible(ServerPlayer p, boolean forced) {
        if (p.isSpectator() || !SystemAPI.hasSystem(p) || !SystemAPI.isAwakened(p)) return false;
        return forced || SystemAPI.get(p).rank().tier() >= IsleConfig.minRank().tier();
    }

    /** Somewhere the Association can't take them from: a gate, a trial, the Penalty Zone, the Citadel. */
    static boolean blocked(ServerPlayer p) {
        if (!p.isAlive()) return true;
        if (GateService.isDungeon(p.level()) && !(p.server instanceof GameTestServer)) return true;
        return GateService.inTransit(p) || AwakeningService.inSequence(p) || PenaltyZone.busy(p) || RedGates.occupied(p) || JobTrial.inTrial(p)
                || ThroneTrial.inTrial(p) || TowerAPI.inCitadel(p) || EvaluationManager.isBeingEvaluated(p);
    }

    /** True while a hunter is on Halcyon Isle (other features leave them be: no trials, no Penalty Zone, no regroup). */
    public static boolean occupied(Player p) {
        if (!(p instanceof ServerPlayer sp)) return false;
        if (IsleWorld.isIsle(sp.level())) return true;
        if (!IsleWorld.onIsle(sp.level(), sp.blockPosition())) return false;
        IsleRaid r = IsleData.get(sp.server).raid();
        return r != null && r.fighting(sp.getUUID());
    }

    /** The raid's hunters still fighting who are on the island now. */
    static List<ServerPlayer> present(MinecraftServer server, ServerLevel isle, IsleRaid r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : r.joined) {
            if (!r.fighting(id)) continue;
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p != null && p.isAlive() && p.level() == isle && IsleWorld.onIsle(isle, p.blockPosition())) out.add(p);
        }
        return out;
    }

    /** Everyone of the raid (fighting, carried off or not) on the island now. */
    private static List<ServerPlayer> onIsle(MinecraftServer server, ServerLevel isle, IsleRaid r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : r.joined) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p != null && p.level() == isle && IsleWorld.onIsle(isle, p.blockPosition())) out.add(p);
        }
        return out;
    }

    @Nullable
    static IsleRaid.Ally ally(IsleRaid r, @Nullable UUID hunter) {
        if (hunter == null) return null;
        for (IsleRaid.Ally a : r.allies) if (a.hired().hunter.equals(hunter)) return a;
        return null;
    }

    /** Whose side a blow is from: a hunter, an ally of theirs, a hunter's shadow or pet, or a shot of theirs. */
    public static boolean hunterSide(@Nullable Entity e) {
        if (e == null) return false;
        if (e instanceof Player p) return !p.isSpectator();
        if (e instanceof GuildHunterEntity) return true;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return true;
        return e instanceof Projectile pr && pr.getOwner() != e && hunterSide(pr.getOwner());
    }

    /** What a hunter is to the raid ({@link #YOU_NONE} .. {@link #YOU_LEFT}). */
    static int you(IsleRaid r, UUID id) {
        if (r.left.contains(id)) return YOU_LEFT;
        if (r.fallen.contains(id)) return YOU_FALLEN;
        if (r.joined.contains(id)) return r.phase == IsleRaid.Phase.MUSTER ? YOU_JOINED : YOU_FIGHTING;
        if (r.declined.contains(id)) return YOU_DECLINED;
        return r.called.contains(id) ? YOU_CALLED : YOU_NONE;
    }

    // ================================================================ the clock

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return;
        IsleData data = IsleData.get(server);
        long now = now(server);
        if (building != null) {
            try {
                if (building.tick(isle)) built(data);
            } catch (RuntimeException e) {
                BeyondSolo.LOGGER.error("Building Halcyon Isle failed", e);
                building = null;
            }
        }
        IsleRaid r = data.raid;
        if (r == null) {
            if (server.getTickCount() % 20 == 0) schedule(server, data, now);
            return;
        }
        try {
            tickRaid(server, isle, data, r, now);
        } catch (RuntimeException e) {
            // a raid that can't go on lets its hunters go rather than holding them on a broken island
            BeyondSolo.LOGGER.error("Halcyon raid {} failed; sending its hunters home", r.id, e);
            abort(server, isle, data, r);
        }
    }

    private static void built(IsleData data) {
        if (building == null) return;
        IsleWorld.Layout l = building.layout();
        data.setBuilt(l.seed(), l.nests().size());
        building = null;
    }

    /** The Association's next call: its clock runs while a hunter it would call is online and free. */
    private static void schedule(MinecraftServer server, IsleData data, long now) {
        if (!IsleConfig.enabled()) return;
        if (server instanceof GameTestServer && !testCalls) return;
        boolean anyone = false;
        for (ServerPlayer p : OnlinePlayers.all(server)) {
            if (eligible(p, false) && !blocked(p)) {
                anyone = true;
                break;
            }
        }
        if (!anyone) {
            if (data.nextCallAt >= 0) {
                data.nextCallAt += 20;
                data.setDirty();
            }
            return;
        }
        if (data.nextCallAt < 0) {
            data.nextCallAt = now + IsleConfig.firstCallTicks();
            data.setDirty();
            return;
        }
        if (now >= data.nextCallAt) call(server, false, null);
    }

    private static void tickRaid(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, long now) {
        switch (r.phase) {
            case MUSTER -> tickMuster(server, isle, data, r, now);
            case CLEARED, FAILED -> {
                long wait = r.phase == IsleRaid.Phase.CLEARED ? AFTER_TICKS : FAILED_TICKS;
                if (now - r.endedAt >= wait) {
                    end(server, isle, data, r);
                    return;
                }
                if (r.phase == IsleRaid.Phase.CLEARED) {
                    IsleWorld.Layout l = layout(isle, r);
                    for (ServerPlayer p : onIsle(server, isle, r)) climb(p, isle, l, now);
                }
                if (now % 20 == 0) syncAll(server, isle, r, now);
            }
            default -> tickLanded(server, isle, data, r, now);
        }
    }

    // ================================================================ the call

    /**
     * The Association calls: every awakened hunter of its rank online and free (or, from a command, only {@code only},
     * any rank). Returns what happened.
     */
    public static Outcome call(MinecraftServer server, boolean forced, @Nullable Collection<ServerPlayer> only) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        if (data.raid != null) return Outcome.no("under_way");
        long now = now(server);
        List<ServerPlayer> called = new ArrayList<>();
        for (ServerPlayer p : only != null ? only : OnlinePlayers.all(server)) {
            if (eligible(p, forced) && !blocked(p)) called.add(p);
        }
        if (called.isEmpty()) return Outcome.no("nobody");
        IsleRaid r = new IsleRaid(UUID.randomUUID());
        r.forced = forced;
        r.seed = isleSeed(server);
        r.calledAt = now;
        r.phaseAt = now;
        r.musterEnds = now + IsleConfig.musterTicks();
        r.setNests(IsleConfig.nests());
        for (ServerPlayer p : called) {
            r.called.add(p.getUUID());
            r.names.put(p.getUUID(), p.getGameProfile().getName());
        }
        data.raid = r;
        data.called++;
        data.nextCallAt = now + Math.round(IsleConfig.callTicks() * (0.75 + 0.5 * isle.random.nextDouble()));
        data.setDirty();
        prepare(isle, data, r);
        for (ServerPlayer p : called) announce(p, r);
        syncAll(server, isle, r, now);
        return Outcome.yes("called", called.size());
    }

    /** Whether the island is being built now. */
    static boolean isBuilding() {
        return building != null;
    }

    /** Starts building the island from nothing, for a seed and number of nests (whatever stands is built over). */
    static void rebuild(ServerLevel isle, long seed, int nests) {
        building = new IsleBuilder(layout(isle, seed, nests));
    }

    /** Builds the island for a layout at once (whatever was being built is finished as this one). */
    static void buildNow(ServerLevel isle, IsleData data, IsleWorld.Layout l) {
        if (building == null || !building.layout().equals(l)) building = new IsleBuilder(l);
        building.finish(isle);
        built(data);
    }

    /** Starts building the island for a raid if the one standing isn't it. */
    private static void prepare(ServerLevel isle, IsleData data, IsleRaid r) {
        if (data.built(r.seed, r.nests())) return;
        IsleWorld.Layout l = layout(isle, r);
        if (building != null && building.layout().equals(l)) return;
        building = new IsleBuilder(l);
    }

    /** The call reaches a hunter: an alert, a line to answer it by, the banner and a horn. */
    private static void announce(ServerPlayer p, IsleRaid r) {
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(KEY + "notify.call.title"),
                Component.translatable(KEY + "notify.call", clock(IsleConfig.musterTicks()))));
        p.sendSystemMessage(Component.translatable(KEY + "chat.call").withStyle(ChatFormatting.GOLD)
                .append(" ").append(button("join", ChatFormatting.GREEN, "/isle join"))
                .append(" ").append(button("decline", ChatFormatting.RED, "/isle decline")));
        banner(p, "call", IsleConfig.musterTicks(), r.nests());
        p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 0.7f, 0.8f);
    }

    static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(KEY + "button." + key)).append("]")
                .withStyle(Style.EMPTY.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    /** A hunter answers the call (any hunter the Association would call may, while it is open). */
    public static Outcome join(ServerPlayer p) {
        IsleData data = IsleData.get(p.server);
        IsleRaid r = data.raid;
        if (r == null) return Outcome.no("no_call");
        if (r.phase != IsleRaid.Phase.MUSTER) return Outcome.no("too_late");
        UUID id = p.getUUID();
        if (r.joined.contains(id)) return Outcome.no("already");
        if (!r.called.contains(id) && !eligible(p, r.forced)) return Outcome.no("rank", IsleConfig.minRank().displayName());
        if (blocked(p)) return Outcome.no("busy");
        r.called.add(id);
        r.names.put(id, p.getGameProfile().getName());
        r.declined.remove(id);
        r.joined.add(id);
        long now = now(p.server);
        if (allAnswered(p.server, r)) r.musterEnds = Math.min(r.musterEnds, now + LAUNCH_DELAY);
        data.setDirty();
        p.playNotifySound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.MASTER, 0.8f, 0.9f);
        for (UUID other : r.called) {
            ServerPlayer o = other.equals(id) ? null : OnlinePlayers.get(p.server, other);
            if (o != null) o.sendSystemMessage(Component.translatable(KEY + "chat.answered", p.getDisplayName(), r.joined.size()).withStyle(ChatFormatting.GRAY));
        }
        ServerLevel isle = IsleWorld.level(p.server);
        if (isle != null) syncAll(p.server, isle, r, now);
        return Outcome.yes("joined", clock(Math.max(0, r.musterEnds - now)));
    }

    /** A hunter lets the call pass. */
    public static Outcome decline(ServerPlayer p) {
        IsleData data = IsleData.get(p.server);
        IsleRaid r = data.raid;
        UUID id = p.getUUID();
        if (r == null || r.phase != IsleRaid.Phase.MUSTER || !r.called.contains(id)) return Outcome.no("no_call");
        r.joined.remove(id);
        r.declined.add(id);
        long now = now(p.server);
        if (allAnswered(p.server, r)) r.musterEnds = Math.min(r.musterEnds, now + (r.joined.isEmpty() ? 0 : LAUNCH_DELAY));
        data.setDirty();
        clear(p);
        ServerLevel isle = IsleWorld.level(p.server);
        if (isle != null) syncAll(p.server, isle, r, now);
        return Outcome.yes("declined");
    }

    /** Every hunter called who is online has answered, one way or the other. */
    private static boolean allAnswered(MinecraftServer server, IsleRaid r) {
        for (UUID id : r.called) {
            if (r.joined.contains(id) || r.declined.contains(id)) continue;
            if (OnlinePlayers.get(server, id) != null) return false;
        }
        return true;
    }

    /**
     * A hunter leaves: withdraws their answer while the call is open, walks off the island during the raid (out of it,
     * unpaid), or goes home from an island whose raid is over (or that they had no business on).
     */
    public static Outcome leave(ServerPlayer p) {
        IsleData data = IsleData.get(p.server);
        IsleRaid r = data.raid;
        UUID id = p.getUUID();
        boolean here = IsleWorld.onIsle(p.level(), p.blockPosition());
        if (r != null && r.phase == IsleRaid.Phase.MUSTER && r.joined.contains(id)) {
            r.joined.remove(id);
            r.declined.add(id);
            data.setDirty();
            clear(p);
            return Outcome.yes("withdrawn");
        }
        ServerLevel isle = IsleWorld.level(p.server);
        if (r != null && isle != null && r.phase.landed() && r.fighting(id) && here) {
            r.left.add(id);
            failQuests(p, r, QuestEvent.FailReason.ABANDONED, Component.translatable(KEY + "outcome.left"));
            sendHome(p);
            for (ServerPlayer o : present(p.server, isle, r)) {
                o.sendSystemMessage(Component.translatable(KEY + "chat.left", p.getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
            data.setDirty();
            return Outcome.yes("left");
        }
        if (here && !(r != null && r.phase.landed() && r.fighting(id))) {
            sendHome(p);
            return Outcome.yes("home");
        }
        return Outcome.no("not_on");
    }

    // ================================================================ the muster

    private static void tickMuster(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, long now) {
        if (building == null) prepare(isle, data, r);
        if (now >= r.musterEnds) {
            boolean anyone = false;
            for (UUID id : r.joined) {
                ServerPlayer p = OnlinePlayers.get(server, id);
                if (p != null && !blocked(p)) anyone = true;
            }
            if (!anyone) {
                drop(server, data, r);
                return;
            }
            launch(server, isle, data, r, now);
            return;
        }
        if (now % 20 == 0) syncAll(server, isle, r, now);
    }

    /** Nobody answered (or nobody who did can go): the call closes, and nothing more comes of it. */
    private static void drop(MinecraftServer server, IsleData data, IsleRaid r) {
        for (UUID id : r.called) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null) continue;
            clear(p);
            if (r.joined.contains(id) || !r.declined.contains(id)) {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.title"),
                        Component.translatable(KEY + "notify.closed")));
            }
        }
        data.raid = null;
        data.setDirty();
    }

    // ================================================================ the landing

    /** The muster is over: the island is made ready, and the hunters who answered land on it together. */
    static void launch(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, long now) {
        IsleWorld.Layout l = layout(isle, r);
        if (!data.built(r.seed, r.nests())) buildNow(isle, data, l);
        reset(isle, l);
        List<ServerPlayer> going = new ArrayList<>();
        for (UUID id : r.joined) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null || blocked(p)) {
                // away when the boats left: out of it
                r.left.add(id);
                if (p != null) {
                    clear(p);
                    Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.title"),
                            Component.translatable(KEY + "notify.missed")));
                }
                continue;
            }
            going.add(p);
        }
        if (going.isEmpty()) {
            drop(server, data, r);
            return;
        }
        r.blowsNeeded = IsleConfig.nestHits(going.size());
        pickAllies(server, r);
        r.phase = IsleConfig.beachWaves() > 0 ? IsleRaid.Phase.BEACH : IsleRaid.Phase.NESTS;
        r.phaseAt = now;
        r.landedAt = now;
        r.wave = 0;
        r.waveAt = now + FIRST_WAVE;
        r.emptySince = -1;
        BROODED.clear();
        broodRaid = r.id;
        for (int i = 0; i < going.size(); i++) land(going.get(i), isle, l, r, i, going.size());
        for (ServerPlayer p : going) {
            giveRetake(p, r);
            banner(p, "landing", going.size(), r.allies.size());
            if (!r.allies.isEmpty()) {
                String first = r.allies.get(0).hired().name;
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.title"),
                        r.allies.size() == 1 ? Component.translatable(KEY + "notify.ally", first)
                                : Component.translatable(KEY + "notify.allies", first, r.allies.size() - 1)));
            }
        }
        isle.playSound(null, l.circle(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.7f);
        data.setDirty();
        syncAll(server, isle, r, now);
    }

    /** The island set up again for a landing: nests whole, the hive sealed, the cocoon unbroken, nothing left over. */
    static void reset(ServerLevel isle, IsleWorld.Layout l) {
        for (int i = 0; i < l.nests().size(); i++) IsleBuilder.nest(isle, l, i, false);
        IsleBuilder.seal(isle, l, true);
        IsleBuilder.ready(isle, l.chamber(), l.chamberRadius() + 4, true);
        IsleBuilder.cocoon(isle, l, true);
        // the eggs a fight trampled are laid again
        IsleBuilder.chamberEggs(isle, l);
        for (Entity e : isle.getEntities((Entity) null, bounds(l), IsleRaids::leftover)) e.discard();
    }

    /** Something a raid leaves on the island: its swarm, hearts, bosses, allies, and whatever was dropped. */
    private static boolean leftover(Entity e) {
        if (e instanceof Player) return false;
        return e.getPersistentData().hasUUID(TAG) || e instanceof NestHeartEntity || e instanceof HiveBoss || e instanceof ItemEntity
                || e instanceof GuildHunterEntity g && g.cause() == ALLY;
    }

    static AABB bounds(IsleWorld.Layout l) {
        int reach = l.radius() + 64;
        BlockPos c = l.center();
        return new AABB(c.getX() - reach, l.chamber().getY() - 40, c.getZ() - reach, c.getX() + reach, l.center().getY() + 200, c.getZ() + reach);
    }

    /** One hunter lands at the camp's circle of light, beside the others, facing up the island. */
    private static void land(ServerPlayer p, ServerLevel isle, IsleWorld.Layout l, IsleRaid r, int i, int n) {
        IsleRecord rec = record(p);
        if (!r.id.equals(rec.raid) && !IsleWorld.onIsle(p.level(), p.blockPosition())) rec.away(r.id, p.level().dimension(), p.position(), p.getYRot());
        else rec.raid = r.id;
        rec.raids++;
        BlockPos c = l.circle();
        double x = c.getX() + 0.5 + (i - (n - 1) / 2.0) * 1.4;
        double z = c.getZ() + 0.5;
        int y = surface(isle, Mth.floor(x), Mth.floor(z), c.getY() + 6);
        place(p, isle, new Vec3(x, y, z), l.campYaw());
        isle.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 16, 0.3, 0.8, 0.3, 0.05);
    }

    /** Takes a hunter somewhere at once (fake players in tests ignore a teleport within their level). */
    static void place(ServerPlayer p, ServerLevel level, Vec3 to, float yaw) {
        p.stopRiding();
        p.teleportTo(level, to.x, to.y, to.z, yaw, 0f);
        if (p.level() == level && p.distanceToSqr(to) > 4) p.moveTo(to.x, to.y, to.z, yaw, 0f);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    /** Where to stand at a column: the first solid ground at or below {@code fromY} with room above it. */
    static int surface(ServerLevel level, int x, int z, int fromY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = fromY; y > fromY - 24; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (!s.blocksMotion()) continue;
            if (!level.getBlockState(p.set(x, y + 1, z)).blocksMotion() && !level.getBlockState(p.set(x, y + 2, z)).blocksMotion()) return y + 1;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** The strongest hunters of the national rankings answer too: they fight beside the raid. */
    private static void pickAllies(MinecraftServer server, IsleRaid r) {
        r.allies.clear();
        int want = IsleConfig.allies();
        if (want <= 0) return;
        long day = server.overworld().getDayTime() / 24000L;
        List<Rival> pool = new ArrayList<>();
        for (Rival v : Rivals.ALL) if (v.rank() == PlayerRank.S || v.rank() == PlayerRank.A) pool.add(v);
        pool.sort(Comparator.comparingDouble((Rival v) -> v.levelOn(day)).reversed());
        for (Rival v : pool.subList(0, Math.min(want, pool.size()))) {
            UUID guild = UUID.nameUUIDFromBytes(("guild:" + v.guild()).getBytes(StandardCharsets.UTF_8));
            UUID hunter = UUID.nameUUIDFromBytes(("rival:" + v.id()).getBytes(StandardCharsets.UTF_8));
            int level = Math.max(1, Math.round(v.levelOn(day)));
            r.allies.add(new IsleRaid.Ally(v.id(), new RaidState.Hired(guild, hunter, v.name(), v.playerClass(), level, Math.floorMod(v.id().hashCode(), 97))));
        }
    }

    @Nullable
    static Rival rival(String id) {
        for (Rival v : Rivals.ALL) if (v.id().equals(id)) return v;
        return null;
    }

    /** A guild's colour on its hunters' names. */
    static int guildColor(String guild) {
        return switch (guild) {
            case Rivals.AZURE_TIDE -> 0x4FA8FF;
            case Rivals.IRON_BASTION -> 0xB8C2CC;
            case Rivals.SUNFALL -> 0xFFB347;
            case Rivals.RED_LANTERN -> 0xFF5A4F;
            default -> 0xE8E8E8;
        };
    }

    /** The allies' bodies on the island: one that isn't there (or fell out of the world's memory) steps in beside a hunter. */
    private static void ensureAllies(ServerLevel isle, IsleRaid r, List<ServerPlayer> here) {
        if (here.isEmpty()) return;
        int i = 0, n = r.allies.size();
        for (IsleRaid.Ally a : r.allies) {
            i++;
            if (a.fallen()) continue;
            if (a.entity != null && isle.getEntity(a.entity) instanceof GuildHunterEntity e && e.isAlive()) continue;
            ServerPlayer near = here.get(i % here.size());
            GuildHunterEntity e = GuildModule.HUNTER.get().create(isle);
            if (e == null) continue;
            Rival v = rival(a.rival());
            e.serve(ALLY, a.hired(), guildColor(v == null ? "" : v.guild()), v == null ? "?" : v.rank().name());
            float ang = near.getYRot() * Mth.DEG_TO_RAD + Mth.PI + (i - (n + 1) / 2f) * 0.7f;
            double x = near.getX() - Mth.sin(ang) * 2.5, z = near.getZ() + Mth.cos(ang) * 2.5;
            int y = surface(isle, Mth.floor(x), Mth.floor(z), Mth.floor(near.getY()) + 3);
            if (Math.abs(y - near.getY()) > 4) {
                x = near.getX();
                z = near.getZ();
                y = Mth.floor(near.getY());
            }
            e.moveTo(x, y, z, near.getYRot(), 0f);
            e.setYHeadRot(near.getYRot());
            isle.addFreshEntity(e);
            a.entity = e.getUUID();
            SkillFx.play(e, SkillFxPayload.Kind.BLINK, 0xFFE9B0, e.position(), 1f, 1);
        }
    }

    // ================================================================ on the island

    private static void tickLanded(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, long now) {
        IsleWorld.Layout l = layout(isle, r);
        List<ServerPlayer> here = present(server, isle, r);
        boolean open = r.phase == IsleRaid.Phase.HIVE || r.phase == IsleRaid.Phase.QUEEN || r.phase == IsleRaid.Phase.KING;
        if (open) {
            for (ServerPlayer p : here) {
                descend(server, isle, l, r, p, now);
                climb(p, isle, l, now);
            }
        }
        if (r.phase == IsleRaid.Phase.KING && r.king == null && r.queenFellAt >= 0 && now - r.queenFellAt >= KING_DELAY) {
            birth(server, isle, l, r, now);
            data.setDirty();
        }
        if (now % 20 != 0) return;
        data.setDirty();
        // a hunter who left the island some other way is out of the raid
        for (UUID id : List.copyOf(r.joined)) {
            if (!r.fighting(id)) continue;
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null || !p.isAlive()) continue;
            if (p.level() == isle && IsleWorld.onIsle(isle, p.blockPosition())) continue;
            r.left.add(id);
            record(p).back();
            failQuests(p, r, QuestEvent.FailReason.FLED, Component.translatable(KEY + "outcome.left"));
            clear(p);
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(KEY + "notify.title"),
                    Component.translatable(KEY + "notify.walked_off")));
        }
        boolean anyFighting = false;
        for (UUID id : r.joined) {
            if (r.fighting(id)) {
                anyFighting = true;
                break;
            }
        }
        if (!anyFighting) {
            fail(server, isle, data, r, r.fallen.isEmpty() ? IsleRaid.Lost.NOBODY : IsleRaid.Lost.FALLEN);
            return;
        }
        if (here.isEmpty()) {
            if (r.emptySince < 0) r.emptySince = now;
            else if (now - r.emptySince >= EMPTY_GRACE) {
                fail(server, isle, data, r, IsleRaid.Lost.NOBODY);
                return;
            }
        } else {
            r.emptySince = -1;
        }
        if (now - r.landedAt >= IsleConfig.timeLimitTicks()) {
            fail(server, isle, data, r, IsleRaid.Lost.TIME);
            return;
        }
        pruneSwarm(isle, r);
        ensureAllies(isle, r, here);
        if (now % 40 == 0) aggro(isle, r);
        switch (r.phase) {
            case BEACH -> {
                beach(isle, l, r, here, now);
                nests(isle, l, r, here, now);
            }
            case NESTS -> nests(isle, l, r, here, now);
            case QUEEN, KING -> {
                keepBoss(isle, l, r, here, now);
                if (now % STATUS_EVERY == 0) bossStatus(server, isle, l, r);
            }
            default -> {
            }
        }
        syncAll(server, isle, r, now);
    }

    /** Down the pit: a hunter who drops into it is taken to the queen's chamber. The first one there wakes her. */
    private static void descend(MinecraftServer server, ServerLevel isle, IsleWorld.Layout l, IsleRaid r, ServerPlayer p, long now) {
        BlockPos d = l.descent();
        double dx = p.getX() - (d.getX() + 0.5), dz = p.getZ() - (d.getZ() + 0.5);
        int floor = l.mouth().getY();
        if (Math.abs(dx) > 1.6 || Math.abs(dz) > 1.6 || p.getY() > floor - 0.3 || p.getY() < floor - 6) return;
        BlockPos a = l.arrival();
        Vec3 to = new Vec3(a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5);
        isle.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.5, 0.5, 0.5, 0.02);
        place(p, isle, to, 180f);
        isle.sendParticles(ParticleTypes.FALLING_HONEY, to.x, to.y + 2.5, to.z, 24, 0.8, 0.4, 0.8, 0.02);
        isle.playSound(null, BlockPos.containing(to), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.4f, 0.5f);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
        if (r.phase == IsleRaid.Phase.HIVE) {
            r.phase = IsleRaid.Phase.QUEEN;
            r.phaseAt = now;
            keepBoss(isle, l, r, present(server, isle, r), now);
        }
    }

    /** Back up: a hunter crouching in the ring of light at the chamber's south end climbs to the hive above. */
    private static void climb(ServerPlayer p, ServerLevel isle, IsleWorld.Layout l, long now) {
        BlockPos a = l.arrival();
        boolean inRing = Math.abs(p.getX() - (a.getX() + 0.5)) < 1.6 && Math.abs(p.getZ() - (a.getZ() + 0.5)) < 1.6 && Math.abs(p.getY() - (a.getY() + 1)) < 1.5;
        if (!inRing || !p.isShiftKeyDown()) {
            CLIMBING.remove(p.getUUID());
            return;
        }
        long since = CLIMBING.computeIfAbsent(p.getUUID(), k -> now);
        if (now - since < CLIMB_TICKS) return;
        CLIMBING.remove(p.getUUID());
        BlockPos d = l.descent();
        Vec3 to = new Vec3(d.getX() + 0.5, l.mouth().getY() + 1, d.getZ() + 3.5);
        place(p, isle, to, 0f);
        isle.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 14, 0.3, 0.6, 0.3, 0.04);
        isle.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    // ---------------------------------------------------------------- the beach

    private static void beach(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, List<ServerPlayer> here, long now) {
        int waves = IsleConfig.beachWaves();
        if (r.wave < waves) {
            boolean due = r.wave == 0 ? now >= r.waveAt : r.swarm.size() <= 2 && now - r.waveAt >= WAVE_GAP || now - r.waveAt >= WAVE_MAX;
            if (due && !here.isEmpty()) wave(isle, l, r, here, now);
            return;
        }
        if (r.swarm.size() <= 2 || now - r.waveAt >= WAVE_MAX) {
            r.phase = IsleRaid.Phase.NESTS;
            r.phaseAt = now;
            for (ServerPlayer p : here) banner(p, "push", r.nests() - r.nestsBurned(), r.nests());
        }
    }

    /** A wave of the swarm comes down on the landing from the island's heart. */
    private static void wave(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, List<ServerPlayer> here, long now) {
        r.wave++;
        r.waveAt = now;
        int waves = IsleConfig.beachWaves();
        int room = IsleConfig.swarmCap() - r.swarm.size();
        int count = Math.min(room, 4 + 2 * here.size() + 2 * r.wave);
        RandomSource rnd = isle.random;
        PlayerRank rank = IsleConfig.swarmRank();
        boolean last = r.wave >= waves;
        for (int i = 0; i < count; i++) {
            MonsterKind kind = waveKind(r.wave, rnd);
            Variant v = last && i == 0 ? Variant.ELITE : rnd.nextFloat() < 0.08f * r.wave ? Variant.VETERAN : Variant.NORMAL;
            BlockPos at = waveSpot(isle, l, rnd);
            spawn(isle, r.id, r, kind, v, rank, at, nearest(here, at));
        }
        for (ServerPlayer p : here) banner(p, "wave", r.wave, waves);
        isle.playSound(null, l.camp(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6f, 0.6f);
    }

    private static MonsterKind waveKind(int wave, RandomSource rnd) {
        float roll = rnd.nextFloat();
        if (wave >= 3 && roll < 0.14f) return MonsterKind.SHARDBACK;
        if (wave >= 2 && roll < 0.4f) return MonsterKind.HIVE_REAVER;
        return rnd.nextFloat() < 0.6f ? MonsterKind.BROODLING : MonsterKind.HIVE_DRONE;
    }

    /** Somewhere up the island from the camp, on land and out of the nests. */
    private static BlockPos waveSpot(ServerLevel isle, IsleWorld.Layout l, RandomSource rnd) {
        BlockPos c = l.circle();
        int near = l.small() ? 10 : 22, far = l.small() ? 16 : 32;
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians((rnd.nextDouble() - 0.5) * 140);
            double d = near + rnd.nextDouble() * (far - near);
            int x = c.getX() + (int) Math.round(Math.sin(a) * d);
            int z = c.getZ() - (int) Math.round(Math.cos(a) * d);
            if (!l.land(x, z) || inNest(l, x, z)) continue;
            return new BlockPos(x, surface(isle, x, z, l.groundAt(x, z) + 8), z);
        }
        int z = c.getZ() - near;
        return new BlockPos(c.getX(), surface(isle, c.getX(), z, c.getY() + 8), z);
    }

    private static boolean inNest(IsleWorld.Layout l, int x, int z) {
        int reach = l.small() ? 6 : 9;
        for (BlockPos n : l.nests()) {
            int dx = n.getX() - x, dz = n.getZ() - z;
            if (dx * dx + dz * dz <= reach * reach) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the nests

    /** Every nest still standing keeps a beating heart, and broods while a hunter is near. */
    private static void nests(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, List<ServerPlayer> here, long now) {
        if (!r.id.equals(broodRaid)) {
            BROODED.clear();
            broodRaid = r.id;
        }
        for (int i = 0; i < r.nests(); i++) {
            if (r.burned(i)) continue;
            BlockPos n = l.nests().get(i);
            NestHeartEntity heart = heart(isle, l, r, i);
            if (heart == null) continue;
            ServerPlayer near = nearest(here, n);
            if (near == null || near.blockPosition().distSqr(n) > NEST_REACH * NEST_REACH) continue;
            long last = BROODED.getOrDefault(i, -1_000_000L);
            if (now - last < IsleConfig.nestTicks()) continue;
            BROODED.put(i, now);
            if (r.swarm.size() >= IsleConfig.swarmCap()) continue;
            heart.brood();
            int count = 1 + (isle.random.nextFloat() < 0.4f ? 1 : 0);
            for (int k = 0; k < count; k++) spawn(isle, r.id, r, nestKind(isle.random), Variant.NORMAL, IsleConfig.swarmRank(), inNest(isle, l, n), near);
        }
    }

    private static MonsterKind nestKind(RandomSource rnd) {
        float roll = rnd.nextFloat();
        return roll < 0.5f ? MonsterKind.BROODLING : roll < 0.8f ? MonsterKind.HIVE_DRONE : MonsterKind.HIVE_REAVER;
    }

    /** A spot inside a nest's ring, beside its heart. */
    private static BlockPos inNest(ServerLevel isle, IsleWorld.Layout l, BlockPos n) {
        RandomSource rnd = isle.random;
        double a = rnd.nextDouble() * Math.PI * 2;
        double d = 1.6 + rnd.nextDouble() * ((l.small() ? 3 : 5) - 2.2);
        int x = n.getX() + (int) Math.round(Math.sin(a) * d), z = n.getZ() + (int) Math.round(Math.cos(a) * d);
        return new BlockPos(x, surface(isle, x, z, n.getY() + 3), z);
    }

    /** The heart beating in a nest (raised again, with the blows it had taken, if it isn't there), or null if its ground isn't awake. */
    @Nullable
    private static NestHeartEntity heart(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, int i) {
        BlockPos n = l.nests().get(i);
        if (!isle.isPositionEntityTicking(n)) return null;
        NestHeartEntity mine = null;
        for (NestHeartEntity h : isle.getEntitiesOfClass(NestHeartEntity.class, new AABB(n).inflate(4))) {
            boolean ours = r.id.equals(h.raid());
            // a neighbouring nest's heart is that nest's to keep
            if (ours && h.nest() != i && h.nest() >= 0 && h.nest() < r.nests()) continue;
            if (mine == null && ours && h.isAlive()) mine = h;
            else h.discard();
        }
        if (mine != null) return mine;
        NestHeartEntity h = IsleModule.NEST_HEART.get().create(isle);
        if (h == null) return null;
        h.configure(r.id, i, r.blows[i], r.blowsNeeded);
        h.moveTo(n.getX() + 0.5, n.getY() + 1, n.getZ() + 0.5, isle.random.nextFloat() * 360f, 0f);
        isle.addFreshEntity(h);
        return h;
    }

    /** A heart took a blow ({@link NestHeartEntity#hurt}): every third of the way it screams, and its young come to it. */
    static void heartStruck(ServerLevel level, NestHeartEntity heart, Entity by, int blows) {
        IsleData data = IsleData.get(level.getServer());
        IsleRaid r = data.raid();
        if (r == null || !r.id.equals(heart.raid()) || heart.nest() < 0 || heart.nest() >= r.nests()) return;
        r.blows[heart.nest()] = blows;
        int third = Math.max(1, r.blowsNeeded / 3);
        if (blows < r.blowsNeeded && blows % third == 0 && r.swarm.size() < IsleConfig.swarmCap() + 4) {
            heart.brood();
            IsleWorld.Layout l = layout(level, r);
            BlockPos n = l.nests().get(heart.nest());
            LivingEntity target = by instanceof LivingEntity le ? le : null;
            for (int i = 0; i < 2; i++) spawn(level, r.id, r, nestKind(level.random), Variant.NORMAL, IsleConfig.swarmRank(), inNest(level, l, n), target);
            level.playSound(null, n, SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 2f, 0.4f);
        }
        data.setDirty();
    }

    /** A heart burst ({@link NestHeartEntity#burst}): its nest burns. The last one opens the hive. */
    static void nestBurned(ServerLevel level, UUID raid, int nest, @Nullable Entity by) {
        MinecraftServer server = level.getServer();
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid();
        if (r == null || !r.id.equals(raid) || !r.phase.landed() || nest < 0 || nest >= r.nests() || r.burned[nest]) return;
        long now = now(server);
        IsleWorld.Layout l = layout(level, r);
        r.burned[nest] = true;
        r.blows[nest] = r.blowsNeeded;
        IsleBuilder.nest(level, l, nest, true);
        BlockPos n = l.nests().get(nest);
        // its brood burns with it
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(n).inflate(8, 4, 8), m -> isSwarm(m, r) && !(m instanceof ColossalBoss))) {
            m.igniteForSeconds(4);
        }
        int burned = r.nestsBurned();
        for (UUID id : r.joined) {
            if (!r.fighting(id)) continue;
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null) continue;
            QuestEngine.progress(p, NEST_KEY, 1);
            if (p.level() == level && p.blockPosition().distSqr(n) <= NEST_REACH * NEST_REACH) {
                IsleRecord rec = record(p);
                rec.nests++;
                IsleJournal.nestBurned(p, rec);
            }
            if (burned < r.nests()) banner(p, "nest", burned, r.nests());
        }
        level.playSound(null, n, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3f, 0.5f);
        if (burned >= r.nests()) openHive(server, level, l, r, now);
        data.setDirty();
    }

    // ---------------------------------------------------------------- the hive

    /** The last nest has burned: the great hive's mouth tears open, its queen waits on her dais and her guard in the hollow. */
    static void openHive(MinecraftServer server, ServerLevel isle, IsleWorld.Layout l, IsleRaid r, long now) {
        r.phase = IsleRaid.Phase.HIVE;
        r.phaseAt = now;
        IsleBuilder.ready(isle, l.mouth(), 10, true);
        IsleBuilder.seal(isle, l, false);
        IsleBuilder.ready(isle, l.chamber(), l.chamberRadius() + 4, true);
        if (!(r.queen != null && isle.getEntity(r.queen) instanceof QueenBoss q && q.isAlive())) spawnQueen(isle, l, r);
        List<ServerPlayer> here = present(server, isle, r);
        int guards = 2 + here.size() / 2;
        BlockPos d = l.descent();
        double ring = l.small() ? 2.6 : 3.8;
        for (int i = 0; i < guards; i++) {
            double a = Math.PI * 2 * (i + 0.5) / guards;
            int x = d.getX() + (int) Math.round(Math.sin(a) * ring), z = d.getZ() + (int) Math.round(Math.cos(a) * ring);
            spawn(isle, r.id, r, MonsterKind.HIVE_REAVER, Variant.ELITE, IsleConfig.swarmRank(), new BlockPos(x, l.mouth().getY() + 1, z), null);
        }
        for (ServerPlayer p : here) banner(p, "hive", 0, 0);
        isle.playSound(null, l.mouth(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10f, 0.5f);
    }

    private static QueenBoss spawnQueen(ServerLevel isle, IsleWorld.Layout l, IsleRaid r) {
        QueenBoss q = IsleModule.QUEEN.get().create(isle);
        if (q == null) return null;
        BlockPos d = l.dais();
        q.moveTo(d.getX() + 0.5, d.getY() + 1, d.getZ() + 0.5, 0f, 0f);
        q.setYHeadRot(0f);
        q.setYBodyRot(0f);
        q.setupStats();
        q.setEventId(r.id);
        q.setHome(d.above());
        q.getPersistentData().putUUID(TAG, r.id);
        isle.addFreshEntity(q);
        r.queen = q.getUUID();
        return q;
    }

    /**
     * The queen (or the king) while their fight is on: she wakes when a hunter comes down to her, and one who fell out
     * of the world's memory (her ground unloaded and lost) rises again on her dais.
     */
    private static void keepBoss(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, List<ServerPlayer> here, long now) {
        boolean below = false;
        for (ServerPlayer p : here) below |= l.inChamber(p.blockPosition());
        if (r.phase == IsleRaid.Phase.QUEEN) {
            QueenBoss q = r.queen != null && isle.getEntity(r.queen) instanceof QueenBoss b && b.isAlive() ? b : null;
            if (q == null && below && now - r.phaseAt > 60 && isle.isPositionEntityTicking(l.dais())) q = spawnQueen(isle, l, r);
            if (q != null && !q.isAwake() && below) {
                q.wake();
                appear(isle.getServer(), isle, r, q);
            }
            if (q != null) {
                unstick(isle, l, q);
                rally(isle, l, r, q, here);
            }
        } else if (r.phase == IsleRaid.Phase.KING && r.king != null) {
            KingBoss k = isle.getEntity(r.king) instanceof KingBoss b && b.isAlive() ? b : null;
            if (k == null && below && now - r.phaseAt > 60 && isle.isPositionEntityTicking(l.cocoon())) {
                k = spawnKing(isle, l, r);
                if (k != null) k.wakeNow();
            }
            if (k != null) {
                unstick(isle, l, k);
                rally(isle, l, r, k, here);
            }
        }
    }

    /**
     * A royal shut in the rock or left up under the roof (a leap that came down wrong) is set back down on the chamber's
     * floor, toward the middle from where it was: the fight goes on, it doesn't wait in the walls for the clock to run out.
     */
    private static void unstick(ServerLevel isle, IsleWorld.Layout l, HiveBoss royal) {
        if (royal.isDeadOrDying() || !royal.isAwake() || royal.introTicks() > 0 || royal.shifting() || royal.currentMove() != null) return;
        BlockPos c = l.chamber();
        if (royal.walled() < 40 && royal.getY() < c.getY() + 7) return;
        Vec3 mid = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        Vec3 off = new Vec3(royal.getX() - mid.x, 0, royal.getZ() - mid.z);
        double max = l.chamberRadius() * 0.5;
        if (off.length() > max) off = off.normalize().scale(max);
        Vec3 at = mid.add(off);
        royal.teleportTo(at.x, WorldBosses.groundBelow(isle, at.x, at.z, c.getY() + 3), at.z);
        royal.getNavigation().stop();
        royal.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * The chamber is the royal's own: awake, it goes for whoever is in it, seen or not (the nearest hunter in the fight
     * or ally of the rankings), and never stands idle while one is there. The allies in the chamber with nothing else to
     * fight go for it in turn: they came to bring it down, not to wait at the walls.
     */
    private static void rally(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, ColossalBoss royal, List<ServerPlayer> here) {
        if (!royal.isAwake() || royal.introTicks() > 0 || royal.leaving()) return;
        List<GuildHunterEntity> below = new ArrayList<>();
        for (IsleRaid.Ally a : r.allies) {
            if (a.fallen() || a.entity == null || !(isle.getEntity(a.entity) instanceof GuildHunterEntity e) || !e.isAlive()) continue;
            if (l.inChamber(e.blockPosition())) below.add(e);
        }
        LivingEntity t = royal.getTarget();
        if (t == null || !t.isAlive() || !l.inChamber(t.blockPosition())) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (ServerPlayer p : here) {
                if (!r.fighting(p.getUUID()) || p.isCreative() || p.isSpectator() || !l.inChamber(p.blockPosition())) continue;
                double d = p.distanceToSqr(royal);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            for (GuildHunterEntity e : below) {
                double d = e.distanceToSqr(royal);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) royal.setTarget(best);
        }
        for (GuildHunterEntity e : below) {
            LivingEntity theirs = e.getTarget();
            if ((theirs == null || !theirs.isAlive()) && e.canFight(royal)) e.setTarget(royal);
        }
    }

    /** A boss of the hive shows itself: the great threats' banner and bar for every hunter on the island. */
    private static void appear(MinecraftServer server, ServerLevel isle, IsleRaid r, ColossalBoss boss) {
        WorldBossPayload msg = new WorldBossPayload(WorldBossPayload.APPEAR, boss.kind().ordinal(), eventKey(r), boss.getId(), boss.blockPosition(), 1, 0, 0);
        for (ServerPlayer p : onIsle(server, isle, r)) PacketDistributor.sendToPlayer(p, msg);
        bossStatus(server, isle, layout(isle, r), r);
    }

    /** The queen's (or king's) health and each hunter's share, to every hunter on the island. */
    private static void bossStatus(MinecraftServer server, ServerLevel isle, IsleWorld.Layout l, IsleRaid r) {
        boolean king = r.phase == IsleRaid.Phase.KING;
        UUID id = king ? r.king : r.queen;
        if (id == null) return;
        ColossalBoss boss = isle.getEntity(id) instanceof ColossalBoss b ? b : null;
        WorldBossKind kind = king ? WorldBossKind.KING : WorldBossKind.QUEEN;
        int health = boss == null ? 1000 : Math.round(1000 * boss.getHealth() / Math.max(1, boss.getMaxHealth()));
        BlockPos pos = boss != null ? boss.blockPosition() : king ? l.cocoon() : l.dais();
        long key = eventKey(r);
        WorldBossPayload msg = new WorldBossPayload(WorldBossPayload.STATUS, kind.ordinal(), key, boss != null ? boss.getId() : -1, pos,
                boss != null ? boss.phase() : 1, health, 0);
        List<Map.Entry<UUID, Float>> ranked = boss == null ? List.of() : WorldBosses.ranked(boss);
        for (ServerPlayer p : onIsle(server, isle, r)) {
            PacketDistributor.sendToPlayer(p, msg);
            for (int i = 0; i < ranked.size(); i++) {
                if (!ranked.get(i).getKey().equals(p.getUUID())) continue;
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SHARE, kind.ordinal(), key, boss.getId(), pos, boss.phase(),
                        Math.round(1000 * boss.share(p.getUUID())), i + 1));
            }
        }
    }

    /** Seven breaths after the queen falls, her cocoon splits: the king is born. */
    private static void birth(MinecraftServer server, ServerLevel isle, IsleWorld.Layout l, IsleRaid r, long now) {
        IsleBuilder.ready(isle, l.cocoon(), 8, true);
        IsleBuilder.cocoon(isle, l, false);
        KingBoss k = spawnKing(isle, l, r);
        if (k == null) return;
        r.phaseAt = now;
        k.wake();
        BlockPos c = l.cocoon();
        isle.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 1, 0, 0, 0, 0);
        isle.playSound(null, c, SoundEvents.TURTLE_EGG_HATCH, SoundSource.HOSTILE, 6f, 0.4f);
        for (ServerPlayer p : onIsle(server, isle, r)) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.APPEAR, WorldBossKind.KING.ordinal(), eventKey(r), k.getId(), c, 1, 0, 0));
            if (r.fighting(p.getUUID())) giveKing(p, r);
        }
    }

    @Nullable
    private static KingBoss spawnKing(ServerLevel isle, IsleWorld.Layout l, IsleRaid r) {
        KingBoss k = IsleModule.KING.get().create(isle);
        if (k == null) return null;
        BlockPos c = l.cocoon();
        // facing the chamber's middle
        double dx = l.chamber().getX() - c.getX(), dz = l.chamber().getZ() - c.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        k.moveTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, yaw, 0f);
        k.setYHeadRot(yaw);
        k.setYBodyRot(yaw);
        k.setupStats();
        k.setEventId(r.id);
        k.setHome(c.above());
        k.getPersistentData().putUUID(TAG, r.id);
        isle.addFreshEntity(k);
        r.king = k.getUUID();
        return k;
    }

    // ---------------------------------------------------------------- the swarm

    /** One creature of the swarm: an A-rank (by default) creature of the venom, tagged with its raid. */
    @Nullable
    static Mob spawn(ServerLevel level, @Nullable UUID raid, @Nullable IsleRaid r, MonsterKind kind, Variant v, PlayerRank rank, BlockPos at,
                     @Nullable LivingEntity target) {
        Mob m = Enemies.spawn(level, rank, EnemyModule.type(kind), at, new EnemyTraits(Affinity.VENOM, v, 0), -1);
        if (m == null) return null;
        if (raid != null) m.getPersistentData().putUUID(TAG, raid);
        if (r != null) r.swarm.add(m.getUUID());
        if (target != null && target.isAlive()) m.setTarget(target);
        return m;
    }

    static boolean isSwarm(Entity e, IsleRaid r) {
        return e.getPersistentData().hasUUID(TAG) && r.id.equals(e.getPersistentData().getUUID(TAG));
    }

    @Nullable
    private static IsleRaid raidOf(ServerLevel level, Entity e) {
        IsleRaid r = IsleData.get(level.getServer()).raid();
        return r != null && isSwarm(e, r) ? r : null;
    }

    private static void pruneSwarm(ServerLevel isle, IsleRaid r) {
        r.swarm.removeIf(id -> !(isle.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
    }

    /** Idle creatures near an ally go for it. */
    private static void aggro(ServerLevel isle, IsleRaid r) {
        for (IsleRaid.Ally a : r.allies) {
            if (a.fallen() || a.entity == null || !(isle.getEntity(a.entity) instanceof GuildHunterEntity e) || !e.isAlive()) continue;
            for (Mob m : isle.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(10, 4, 10),
                    m -> m.isAlive() && m.getTarget() == null && isSwarm(m, r) && !(m instanceof ColossalBoss) && !(m instanceof NestHeartEntity))) {
                m.setTarget(e);
            }
        }
    }

    @Nullable
    private static ServerPlayer nearest(List<ServerPlayer> players, BlockPos at) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.blockPosition().distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The queen lays: her young crawl out round her ({@link QueenBoss}'s brood). */
    public static void brood(ServerLevel level, LivingEntity mother, int count, @Nullable LivingEntity target) {
        UUID raid = mother.getPersistentData().hasUUID(TAG) ? mother.getPersistentData().getUUID(TAG) : null;
        IsleRaid r = raidOf(level, mother);
        int room = r == null ? count : Math.max(0, Math.min(count, IsleConfig.swarmCap() + 6 - r.swarm.size()));
        for (int i = 0; i < room; i++) {
            BlockPos at = around(level, mother, 2.5, 5);
            MonsterKind kind = level.random.nextFloat() < 0.65f ? MonsterKind.BROODLING : MonsterKind.HIVE_DRONE;
            spawn(level, raid, r, kind, Variant.NORMAL, IsleConfig.swarmRank(), at, target);
        }
    }

    /** The queen calls her guard: S-rank reavers out of the eggs round the chamber ({@link QueenBoss}). */
    public static void royalGuard(ServerLevel level, LivingEntity queen, int count, @Nullable LivingEntity target) {
        UUID raid = queen.getPersistentData().hasUUID(TAG) ? queen.getPersistentData().getUUID(TAG) : null;
        IsleRaid r = raidOf(level, queen);
        for (int i = 0; i < count; i++) {
            BlockPos at = around(level, queen, 4, 8);
            spawn(level, raid, r, MonsterKind.HIVE_REAVER, Variant.ELITE, PlayerRank.S, at, target);
            level.sendParticles(ParticleTypes.FALLING_HONEY, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
        }
    }

    /** A spot on the ground round an entity, {@code min}..{@code max} blocks off. */
    private static BlockPos around(ServerLevel level, Entity e, double min, double max) {
        for (int tries = 0; tries < 8; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = min + level.random.nextDouble() * (max - min);
            int x = Mth.floor(e.getX() + Math.sin(a) * d), z = Mth.floor(e.getZ() + Math.cos(a) * d);
            int y = surface(level, x, z, Mth.floor(e.getY()) + 3);
            if (Math.abs(y - e.getY()) <= 3) return new BlockPos(x, y, z);
        }
        return e.blockPosition();
    }

    /** The nearest creature of the swarm (no boss, no heart) within {@code radius}: the king's next meal. */
    @Nullable
    public static Mob nearestSwarm(ServerLevel level, Vec3 at, double radius) {
        Mob best = null;
        double bestD = radius * radius;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius),
                m -> m.isAlive() && m.getPersistentData().hasUUID(TAG) && !(m instanceof ColossalBoss) && !(m instanceof NestHeartEntity))) {
            double d = m.distanceToSqr(at);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    // ================================================================ the queen and the king fall

    /** A boss of the hive fell ({@link WorldBosses#slain} hands it here): the queen brings the king; the king ends the raid. */
    public static void slain(ServerLevel level, ColossalBoss boss, DamageSource source) {
        MinecraftServer server = level.getServer();
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid();
        if (r == null || boss.eventId() == null || !r.id.equals(boss.eventId()) || !r.phase.landed()) return;
        boolean king = boss.kind() == WorldBossKind.KING;
        if (king ? !boss.getUUID().equals(r.king) : !boss.getUUID().equals(r.queen)) return;
        long now = now(server);
        payBoss(server, level, r, boss);
        if (!king) {
            r.phase = IsleRaid.Phase.KING;
            r.phaseAt = now;
            r.queenFellAt = now;
            IsleWorld.Layout l = layout(level, r);
            for (ServerPlayer p : onIsle(server, level, r)) banner(p, "stirs", KING_DELAY, 0);
            level.playSound(null, l.cocoon(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 5f, 0.5f);
        } else {
            r.kingFellAt = now;
            clear(server, level, data, r, now);
        }
        data.setDirty();
    }

    /** Every hunter of the raid with a part in a kill is paid by their share; everyone fighting on the island counts it. */
    private static void payBoss(MinecraftServer server, ServerLevel level, IsleRaid r, ColossalBoss boss) {
        WorldBossKind kind = boss.kind();
        List<Map.Entry<UUID, Float>> ranked = WorldBosses.ranked(boss);
        UUID top = ranked.isEmpty() ? null : ranked.get(0).getKey();
        RandomSource rnd = level.random;
        long key = eventKey(r);
        Set<UUID> told = new HashSet<>();
        for (UUID id : r.joined) {
            if (!r.paid(id)) continue;
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null) continue;
            float share = boss.share(id);
            boolean here = r.fighting(id) && p.level() == level && IsleWorld.onIsle(level, p.blockPosition());
            if (share < 0.005f && !here) continue;
            long xp = payShare(p, boss, share, id.equals(top), rnd);
            told.add(id);
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), key, boss.getId(), boss.blockPosition(), boss.phase(),
                    Math.round(share * 1000), (int) Math.min(Integer.MAX_VALUE, xp)));
            if (r.fighting(id)) QuestEngine.progress(p, kind == WorldBossKind.KING ? KING_KEY : QUEEN_KEY, 1);
        }
        for (ServerPlayer p : onIsle(server, level, r)) {
            if (told.contains(p.getUUID())) continue;
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), key, boss.getId(), boss.blockPosition(), boss.phase(), -1, 0));
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 3f, 0.8f);
        if (kind == WorldBossKind.KING && top != null) {
            ServerPlayer p = OnlinePlayers.get(server, top);
            if (p != null && SystemAPI.hasSystem(p)) SystemAPI.setTitle(p, IsleJournal.KINGSLAYER);
        }
    }

    /** One hunter's pay for a kill: XP, gold and loot by their share (a hunter on the island gets the least share's at worst). */
    private static long payShare(ServerPlayer p, ColossalBoss boss, float share, boolean top, RandomSource rnd) {
        WorldBossKind kind = boss.kind();
        double cut = 0.4 + 0.6 * Math.min(1.0, share * 2.0);
        long xp = Math.round(boss.getMaxHealth() * 0.25 * cut);
        long gold = Math.round((kind == WorldBossKind.KING ? 12_000 : 8_000) * cut);
        boolean system = SystemAPI.hasSystem(p);
        if (system) {
            SystemAPI.grantXp(p, xp, BeyondSolo.id("isle_raid"));
            Economy.add(p, gold, Economy.Reason.RAID);
        }
        WorldBosses.give(p, new ItemStack(CraftingModule.GREATER_MANA_STONE.get(), 2 + rnd.nextInt(2) + (share >= 0.2f ? 1 : 0)));
        if (top) WorldBosses.give(p, new ItemStack(CraftingModule.SUPREME_HEALING_ELIXIR.get()));
        if (kind == WorldBossKind.KING && rnd.nextFloat() < 0.35f + share) WorldBosses.give(p, GateLoot.greatThreatWeapon(rnd));
        WorldBosses.countSlain(p, kind, top);
        IsleRecord rec = record(p);
        if (kind == WorldBossKind.KING) rec.kings++;
        else rec.queens++;
        return system ? xp : 0;
    }

    // ================================================================ won, lost, over

    /** The king fell: the island is retaken. The swarm dies with him, and everyone who stayed is paid. */
    static void clear(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, long now) {
        if (r.phase.over()) return;
        r.phase = IsleRaid.Phase.CLEARED;
        r.phaseAt = now;
        r.endedAt = now;
        data.won++;
        IsleWorld.Layout l = layout(isle, r);
        for (Entity e : isle.getEntities((Entity) null, bounds(l), e -> isSwarm(e, r) && !(e instanceof ColossalBoss))) {
            isle.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.6, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            e.discard();
        }
        r.swarm.clear();
        long time = now - r.landedAt;
        for (UUID id : r.joined) {
            if (!r.paid(id)) continue;
            boolean fallen = r.fallen.contains(id);
            ServerPlayer p = OnlinePlayers.get(server, id);
            CompoundTag owed = owed(r, id, fallen, time);
            if (p == null) data.unpaid.put(id, owed);
            else payOut(p, owed);
        }
        for (ServerPlayer p : onIsle(server, isle, r)) banner(p, "retaken", (int) Math.min(Integer.MAX_VALUE, time), 0);
        isle.playSound(null, l.chamber(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 4f, 0.7f);
        data.setDirty();
    }

    /** What a hunter is owed for a retaken island (kept for one who was offline when it fell). */
    private static CompoundTag owed(IsleRaid r, UUID id, boolean fallen, long time) {
        CompoundTag t = new CompoundTag();
        t.putUUID("raid", r.id);
        t.putBoolean("fallen", fallen);
        t.putLong("time", time);
        t.putInt("kills", r.kills.getOrDefault(id, 0));
        t.putInt("slain", r.swarmSlain);
        t.putInt("nests", r.nestsBurned());
        int fell = 0;
        for (IsleRaid.Ally a : r.allies) if (a.fallen()) fell++;
        t.putInt("alliesFell", fell);
        t.putInt("allies", r.allies.size());
        return t;
    }

    /** Pays a hunter for the island: XP and gold several times an S-rank clear's, merit, a blessed box, the title. */
    static void payOut(ServerPlayer p, CompoundTag owed) {
        boolean fallen = owed.getBoolean("fallen");
        long time = owed.getLong("time");
        double cut = fallen ? 0.5 : 1.0;
        double scale = IsleConfig.rewardScale();
        long xp = Math.round(S_CLEAR_XP * scale * cut);
        long gold = Math.round(EconomyHooks.GATE_GOLD[5] * scale * cut);
        long merit = fallen ? 200 : 400;
        boolean system = SystemAPI.hasSystem(p);
        if (system) {
            SystemAPI.grantXp(p, xp, BeyondSolo.id("isle_raid"));
            Economy.add(p, gold, Economy.Reason.RAID);
            Association.addMerit(p, merit);
        }
        RandomSource rnd = p.getRandom();
        WorldBosses.give(p, RewardBoxItem.of(BoxKind.BLESSED, PlayerRank.S, 1));
        WorldBosses.give(p, new ItemStack(CraftingModule.GREATER_MANA_STONE.get(), 3 + rnd.nextInt(3)));
        if (!fallen && rnd.nextFloat() < 0.5f) WorldBosses.give(p, GateLoot.greatThreatWeapon(rnd));
        IsleRecord rec = record(p);
        rec.cleared++;
        boolean best = false;
        if (!fallen && (rec.best <= 0 || time < rec.best)) {
            best = rec.best > 0;
            rec.best = time;
        }
        if (system) SystemAPI.setTitle(p, IsleJournal.HIVE_BREAKER);
        CompoundTag t = owed.copy();
        t.putString("kind", "result");
        t.putBoolean("won", true);
        t.putLong("xp", system ? xp : 0);
        t.putLong("gold", system ? gold : 0);
        t.putLong("merit", system ? merit : 0);
        t.putBoolean("best", best);
        PacketDistributor.sendToPlayer(p, new IslePayload(t));
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD, Component.translatable(KEY + "notify.retaken.title"),
                Component.translatable(fallen ? KEY + "notify.retaken.fallen" : KEY + "notify.retaken", xp, gold, merit)));
    }

    /** The raid is lost: its quests fail, the swarm keeps the island, and the hunters are taken home a little after. */
    static void fail(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r, IsleRaid.Lost why) {
        if (r.phase.over() || r.phase == IsleRaid.Phase.MUSTER) return;
        long now = now(server);
        r.phase = IsleRaid.Phase.FAILED;
        r.lost = why;
        r.phaseAt = now;
        r.endedAt = now;
        Component outcome = Component.translatable(KEY + "outcome.lost." + why.name().toLowerCase(Locale.ROOT));
        long time = r.landedAt >= 0 ? now - r.landedAt : 0;
        for (UUID id : r.joined) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null || r.left.contains(id)) continue;
            failQuests(p, r, why == IsleRaid.Lost.TIME ? QuestEvent.FailReason.TIME : QuestEvent.FailReason.LOST, outcome);
            banner(p, "lost", why.ordinal(), 0);
            CompoundTag t = owed(r, id, r.fallen.contains(id), time);
            t.putString("kind", "result");
            t.putBoolean("won", false);
            t.putString("lost", why.name().toLowerCase(Locale.ROOT));
            PacketDistributor.sendToPlayer(p, new IslePayload(t));
        }
        purge(isle, r);
        WorldBossPayload gone = new WorldBossPayload(WorldBossPayload.GONE, WorldBossKind.QUEEN.ordinal(), eventKey(r), -1, layout(isle, r).dais(), 1, 0, 0);
        for (ServerPlayer p : onIsle(server, isle, r)) PacketDistributor.sendToPlayer(p, gone);
        data.setDirty();
    }

    /** The swarm, the hearts and the bosses of a raid go (its allies head home on their own). */
    private static void purge(ServerLevel isle, IsleRaid r) {
        IsleWorld.Layout l = layout(isle, r);
        for (Entity e : isle.getEntities((Entity) null, bounds(l), e -> isSwarm(e, r) || e instanceof NestHeartEntity
                || e instanceof GuildHunterEntity g && g.cause() == ALLY)) {
            if (e instanceof GuildHunterEntity g) g.leave();
            else e.discard();
        }
        r.swarm.clear();
    }

    /** The raid is over: everyone still on the island goes home, and the Association's next call is ahead. */
    static void end(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r) {
        for (ServerPlayer p : OnlinePlayers.all(server)) {
            boolean here = p.level() == isle && IsleWorld.onIsle(isle, p.blockPosition());
            if (here && !p.isCreative() && !p.isSpectator()) sendHome(p);
            else if (r.id.equals(record(p).raid)) record(p).back();
            if (r.called.contains(p.getUUID()) || r.joined.contains(p.getUUID())) clear(p);
        }
        purge(isle, r);
        WorldBossPayload gone = new WorldBossPayload(WorldBossPayload.GONE, WorldBossKind.KING.ordinal(), eventKey(r), -1, BlockPos.ZERO, 1, 0, 0);
        for (ServerPlayer p : OnlinePlayers.all(server)) PacketDistributor.sendToPlayer(p, gone);
        data.raid = null;
        data.setDirty();
        CLIMBING.clear();
        BROODED.clear();
    }

    /** Something broke: everyone goes home, and the raid is let go. */
    private static void abort(MinecraftServer server, ServerLevel isle, IsleData data, IsleRaid r) {
        try {
            for (ServerPlayer p : OnlinePlayers.all(server)) {
                if (r.joined.contains(p.getUUID()) || r.called.contains(p.getUUID())) failQuests(p, r, QuestEvent.FailReason.LOST, null);
            }
            end(server, isle, data, r);
        } catch (RuntimeException e) {
            BeyondSolo.LOGGER.error("Could not end the Halcyon raid cleanly", e);
            data.raid = null;
            data.setDirty();
        }
    }

    /** Ends whatever raid is on at once (commands, tests): no pay, everyone home. */
    public static boolean stop(MinecraftServer server) {
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        ServerLevel isle = IsleWorld.level(server);
        if (r == null || isle == null) return false;
        for (ServerPlayer p : OnlinePlayers.all(server)) {
            if (r.joined.contains(p.getUUID())) failQuests(p, r, QuestEvent.FailReason.LOST, Component.translatable(KEY + "outcome.lost.called_off"));
        }
        end(server, isle, data, r);
        return true;
    }

    /** Sends a hunter home at once: where the Association took them from, or (no such place) to where they respawn. */
    public static void sendHome(ServerPlayer p) {
        IsleRecord rec = record(p);
        ServerLevel to = rec.homeDim == null ? null : p.server.getLevel(rec.homeDim);
        Vec3 at = rec.home;
        float yaw = rec.homeYaw;
        rec.back();
        CLIMBING.remove(p.getUUID());
        if (to != null && at != null && !IsleWorld.onIsle(to, BlockPos.containing(at))) {
            GateService.place(p, new GateService.Home(to.dimension(), at, yaw));
        } else {
            ServerLevel world = p.server.overworld();
            BlockPos spawn = p.getRespawnPosition() != null && p.getRespawnDimension().equals(Level.OVERWORLD) ? p.getRespawnPosition() : world.getSharedSpawnPos();
            world.getChunkAt(spawn);
            BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            place(p, world, Vec3.atBottomCenterOf(top), p.getYRot());
        }
        p.fallDistance = 0;
        clear(p);
    }

    // ================================================================ quests

    private static void giveRetake(ServerPlayer p, IsleRaid r) {
        if (!SystemAPI.isAwakened(p) || hasQuest(p, r, QUEST_RETAKE)) return;
        CompoundTag extra = new CompoundTag();
        extra.putString("raid", r.id.toString());
        Quest q = QuestBuilder.of(QUEST_RETAKE, QuestCategory.ASSOCIATION, 5, SystemAPI.get(p).level())
                .title(Component.translatable("quest.beyondsolo.isle.retake"))
                .description(Component.translatable("quest.beyondsolo.isle.retake.desc", WorldBossKind.QUEEN.fullName()))
                .custom(NEST_KEY, r.nests(), Component.translatable("quest_objective.beyondsolo.isle_nests"))
                .custom(QUEEN_KEY, 1, Component.translatable("quest_objective.beyondsolo.isle_queen", WorldBossKind.QUEEN.bossName()))
                .timeLimit((int) Math.min(Integer.MAX_VALUE, IsleConfig.timeLimitTicks() + 40))
                .fixed().xp(6000L).statPoints(2)
                .extra(extra)
                .build();
        QuestEngine.start(p, q);
        if (r.nestsBurned() > 0) QuestEngine.progress(p, NEST_KEY, r.nestsBurned());
    }

    private static void giveKing(ServerPlayer p, IsleRaid r) {
        if (!SystemAPI.isAwakened(p) || hasQuest(p, r, QUEST_KING)) return;
        CompoundTag extra = new CompoundTag();
        extra.putString("raid", r.id.toString());
        Quest q = QuestBuilder.of(QUEST_KING, QuestCategory.EMERGENCY, 5, SystemAPI.get(p).level())
                .title(Component.translatable("quest.beyondsolo.isle.king", WorldBossKind.KING.bossName()))
                .description(Component.translatable("quest.beyondsolo.isle.king.desc", WorldBossKind.KING.fullName()))
                .custom(KING_KEY, 1, Component.translatable("quest_objective.beyondsolo.isle_king", WorldBossKind.KING.bossName()))
                .fixed().xp(8000L).statPoints(3)
                .extra(extra)
                .build();
        QuestEngine.start(p, q);
    }

    private static boolean hasQuest(ServerPlayer p, IsleRaid r, String template) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (template.equals(q.template()) && r.id.toString().equals(q.extra().getString("raid"))) return true;
        }
        return false;
    }

    /** Fails a hunter's quests for a raid, saying why. */
    static void failQuests(ServerPlayer p, IsleRaid r, QuestEvent.FailReason reason, @Nullable Component outcome) {
        for (Quest q : List.copyOf(QuestEngine.data(p).active())) {
            if ((QUEST_RETAKE.equals(q.template()) || QUEST_KING.equals(q.template())) && r.id.toString().equals(q.extra().getString("raid"))) {
                QuestEngine.fail(p, q, reason, outcome);
            }
        }
    }

    /** A quest for a raid that is over (or that this hunter is no longer on) is let go when they come back. */
    private static void dropStaleQuests(ServerPlayer p, @Nullable IsleRaid r) {
        for (Quest q : List.copyOf(QuestEngine.data(p).all())) {
            if (!QUEST_RETAKE.equals(q.template()) && !QUEST_KING.equals(q.template())) continue;
            boolean live = r != null && r.phase.landed() && r.fighting(p.getUUID()) && r.id.toString().equals(q.extra().getString("raid"));
            if (!live) QuestEngine.drop(p, q);
        }
    }

    // ================================================================ hunters falling, leaving, coming back

    @Nullable
    private static IsleRaid fightingOn(ServerPlayer p) {
        IsleRaid r = IsleData.get(p.server).raid();
        if (r == null || !r.phase.landed() || !r.fighting(p.getUUID()) || !IsleWorld.onIsle(p.level(), p.blockPosition())) return null;
        return r;
    }

    /** A blow that would kill a hunter on the island: the medics carry them off it instead (no death, no totem used). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() < p.getHealth()) return;
        IsleRaid r = fightingOn(p);
        if (r == null) return;
        event.setNewDamage(0);
        UUID id = r.id;
        p.server.execute(() -> carriedOff(p, id));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        IsleRaid r = fightingOn(p);
        if (r == null) return;
        event.setCanceled(true);
        p.setHealth(1f);
        UUID id = r.id;
        p.server.execute(() -> carriedOff(p, id));
    }

    /** Carried off the island hurt: out of the raid (paid half if it is won), home at once. */
    static void carriedOff(ServerPlayer p, UUID raid) {
        MinecraftServer server = p.server;
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid();
        if (r == null || !r.id.equals(raid) || !r.fighting(p.getUUID())) return;
        r.fallen.add(p.getUUID());
        data.setDirty();
        p.setHealth(Math.max(1f, p.getMaxHealth() * CARRIED_HEALTH));
        p.clearFire();
        p.removeEffect(MobEffects.POISON);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 0));
        failQuests(p, r, QuestEvent.FailReason.DIED, Component.translatable(KEY + "outcome.carried"));
        sendHome(p);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(KEY + "notify.title"),
                Component.translatable(KEY + "notify.carried")));
        ServerLevel isle = IsleWorld.level(server);
        if (isle != null) {
            for (ServerPlayer o : present(server, isle, r)) {
                Notifications.send(o, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(KEY + "notify.title"),
                        Component.translatable(KEY + "notify.carried.other", p.getDisplayName())));
            }
        }
    }

    /** A creature of the swarm fell: counted for the raid, and for whoever (or whichever ally) felled it. */
    @SubscribeEvent
    static void onSwarmDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof ColossalBoss || !dead.getPersistentData().hasUUID(TAG)) return;
        IsleRaid r = raidOf(level, dead);
        if (r == null) return;
        r.swarm.remove(dead.getUUID());
        r.swarmSlain++;
        DamageSource src = event.getSource();
        UUID who = ColossalBoss.hunterBehind(src);
        if (who != null && r.joined.contains(who)) {
            r.kills.merge(who, 1, Integer::sum);
        } else if (src.getEntity() instanceof GuildHunterEntity g && g.cause() == ALLY) {
            IsleRaid.Ally a = ally(r, g.hunterId());
            if (a != null) a.kills++;
        }
    }

    /** What a raid left behind (its swarm, a boss) loaded back in after it ended out of sight is gone. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.loadedFromDisk()) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().hasUUID(TAG)) return;
        IsleRaid r = IsleData.get(level.getServer()).raid();
        if (r == null || !isSwarm(e, r) || !r.phase.landed()) {
            event.setCanceled(true);
            return;
        }
        if (e instanceof ColossalBoss b) {
            if (!b.getUUID().equals(r.queen) && !b.getUUID().equals(r.king)) event.setCanceled(true);
            return;
        }
        r.swarm.add(e.getUUID());
    }

    /** Whether a boss of the hive loaded back in belongs to the raid on (for {@link WorldBosses}). */
    public static boolean keeps(ServerLevel level, ColossalBoss boss) {
        IsleRaid r = IsleData.get(level.getServer()).raid();
        return r != null && r.phase.landed() && r.id.equals(boss.eventId()) && (boss.getUUID().equals(r.queen) || boss.getUUID().equals(r.king));
    }

    /**
     * A hunter coming back: one standing on an island they have no raid on is sent home; one owed for an island retaken
     * while they were away is paid; one called hears where the raid is.
     */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        IsleData data = IsleData.get(p.server);
        IsleRaid r = data.raid();
        IsleRecord rec = record(p);
        boolean here = IsleWorld.onIsle(p.level(), p.blockPosition());
        boolean fighting = r != null && r.phase.landed() && r.fighting(p.getUUID());
        boolean lingering = r != null && r.phase.over() && r.id.equals(rec.raid) && r.paid(p.getUUID());
        if (here && !fighting && !lingering && !p.isCreative() && !p.isSpectator()) sendHome(p);
        else if (!here && rec.raid != null && !fighting) rec.back();
        dropStaleQuests(p, r);
        CompoundTag owed = data.unpaid.remove(p.getUUID());
        if (owed != null) {
            data.setDirty();
            payOut(p, owed);
        }
        ServerLevel isle = IsleWorld.level(p.server);
        if (r != null && isle != null && (r.called.contains(p.getUUID()) || r.joined.contains(p.getUUID()))) sync(p, isle, r, now(p.server));
    }

    // ================================================================ the island holds

    /** No digging or building on the island: it stands as it is (creative players may). */
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer().isCreative()) return;
        if (event.getLevel() instanceof Level level && IsleWorld.onIsle(level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.isCreative()) return;
        if (event.getLevel() instanceof Level level && IsleWorld.onIsle(level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onExplode(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        event.getAffectedBlocks().removeIf(pos -> IsleWorld.onIsle(level, pos));
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        building = null;
        cached = null;
        CLIMBING.clear();
        BROODED.clear();
        broodRaid = null;
        testCalls = false;
    }

    // ================================================================ to the HUD

    /** The raid as each hunter called (or on it) sees it. */
    static void syncAll(MinecraftServer server, ServerLevel isle, IsleRaid r, long now) {
        Set<UUID> to = new LinkedHashSet<>(r.called);
        to.addAll(r.joined);
        for (UUID id : to) {
            int you = you(r, id);
            if (you == YOU_DECLINED || you == YOU_LEFT || you == YOU_FALLEN && !r.phase.over()) continue;
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p != null) sync(p, isle, r, now);
        }
    }

    static void sync(ServerPlayer p, ServerLevel isle, IsleRaid r, long now) {
        IsleWorld.Layout l = layout(isle, r);
        CompoundTag t = new CompoundTag();
        t.putString("kind", "state");
        t.putUUID("raid", r.id);
        t.putString("phase", r.phase.id());
        t.putInt("you", you(r, p.getUUID()));
        t.putInt("muster", (int) Math.max(0, r.musterEnds - now));
        t.putInt("called", r.called.size());
        t.putInt("joined", r.joined.size());
        t.putInt("rank", IsleConfig.minRank().ordinal());
        if (r.landedAt >= 0 && r.phase.landed()) t.putInt("left", (int) Math.max(0, IsleConfig.timeLimitTicks() - (now - r.landedAt)));
        t.putInt("wave", r.wave);
        t.putInt("waves", IsleConfig.beachWaves());
        int n = Math.min(r.nests(), l.nests().size());
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPos b = l.nests().get(i);
            xs[i] = b.getX();
            ys[i] = b.getY();
            zs[i] = b.getZ();
        }
        t.putIntArray("nx", xs);
        t.putIntArray("ny", ys);
        t.putIntArray("nz", zs);
        byte[] burned = new byte[n];
        for (int i = 0; i < n; i++) burned[i] = (byte) (r.burned(i) ? 1 : 0);
        t.putByteArray("burned", burned);
        t.putIntArray("blows", r.blows.clone());
        t.putInt("needed", r.blowsNeeded);
        t.putLong("descent", l.descent().asLong());
        t.putLong("mouth", l.mouth().asLong());
        t.putLong("arrival", l.arrival().asLong());
        t.putLong("circle", l.circle().asLong());
        int fighting = 0;
        for (UUID id : r.joined) if (r.fighting(id)) fighting++;
        t.putInt("fighting", fighting);
        int alive = 0;
        for (IsleRaid.Ally a : r.allies) if (!a.fallen()) alive++;
        t.putInt("allies", alive);
        t.putInt("alliesTotal", r.allies.size());
        t.putInt("swarm", r.swarm.size());
        t.putInt("slain", r.swarmSlain);
        t.putInt("kills", r.kills.getOrDefault(p.getUUID(), 0));
        if (r.phase == IsleRaid.Phase.KING && r.king == null && r.queenFellAt >= 0) t.putInt("kingIn", (int) Math.max(0, KING_DELAY - (now - r.queenFellAt)));
        if (r.phase.over()) t.putInt("home", (int) Math.max(0, (r.phase == IsleRaid.Phase.CLEARED ? AFTER_TICKS : FAILED_TICKS) - (now - r.endedAt)));
        t.putString("lost", r.lost.name().toLowerCase(Locale.ROOT));
        PacketDistributor.sendToPlayer(p, new IslePayload(t));
    }

    /** A moment in the middle of a hunter's screen. */
    static void banner(ServerPlayer p, String id, int a, int b) {
        CompoundTag t = new CompoundTag();
        t.putString("kind", "banner");
        t.putString("id", id);
        t.putInt("a", a);
        t.putInt("b", b);
        PacketDistributor.sendToPlayer(p, new IslePayload(t));
    }

    /** The HUD forgets the raid. */
    static void clear(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putString("kind", "clear");
        PacketDistributor.sendToPlayer(p, new IslePayload(t));
    }

    /** m:ss from ticks. */
    static String clock(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private IsleRaids() {}
}
