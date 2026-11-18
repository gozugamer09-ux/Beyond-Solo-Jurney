package com.beyondsolo.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.worldboss.WorldBossData.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The world's great threats ({@link WorldBossKind}), one at a time, in the overworld:
 * <ul>
 *   <li>Once an awakened hunter is strong enough ({@code worldbosses.minLevel}), a <b>world boss</b> rises out of the
 *   earth some way from a hunter, every so often. The System calls every hunter in the overworld to it (a pop-up, a
 *   banner, a quest, its mark on the compass), and it roams the land round where it rose until it falls or no hunter
 *   comes for it ({@code worldbosses.abandonMinutes}), when it sinks back into the earth.</li>
 *   <li>Once world bosses have fallen and a hunter is stronger still, the next great threat may be a <b>monarch</b>: a
 *   gate tears open in the sky, and a minute later the monarch comes down through it, lays down its domain and calls
 *   its army.</li>
 *   <li>When it falls, every hunter who hurt it is paid by their share of the damage ({@link #slain}): System XP, gold,
 *   S-rank gear, and from a monarch, maybe a piece of monarch armor ({@link MonarchArmor}).</li>
 * </ul>
 * Everything a client needs goes out as {@link WorldBossPayload}s (the status every two seconds to everyone in the
 * overworld).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class WorldBosses {
    public static final String QUEST_TEMPLATE = BeyondSolo.id("world_boss/hunt").toString();
    public static final String SLAY_KEY = BeyondSolo.id("world_boss_slay").toString();
    /** Persistent-data key on a monarch's soldiers: the event they came with (they crumble when it ends). */
    public static final String ARMY_TAG = "beyondsolo_monarch_army";
    /** How often the status goes out (ticks). */
    static final int STATUS_EVERY = 40;
    /** A hunter must have taken this share of its health off it to be paid. */
    static final float MIN_SHARE = 0.005f;
    /** How long after the world is ready a planned threat waits for a hunter to be about (ticks). */
    private static final int RETRY = 1200;

    // ================================================================ the lie of the land

    /**
     * The ground under a point: the top of the first solid block at or below {@code nearY + 12} (leaves, logs, plants and
     * water passed through), searching 36 blocks down; the heightmap if none is found.
     */
    public static double groundY(ServerLevel level, double x, double z, double nearY) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return nearY;
        int top = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(nearY) + 12);
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(nearY) - 24);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, top, bz);
        for (int y = top; y >= bottom; y--) {
            m.setY(y);
            BlockState s = level.getBlockState(m);
            if (isGround(s)) {
                double h = s.getCollisionShape(level, m).max(net.minecraft.core.Direction.Axis.Y);
                return y + (Double.isFinite(h) && h > 0 ? h : 1);
            }
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** Solid ground a giant stands on: not air, leaves, logs, plants or water. */
    public static boolean isGround(BlockState s) {
        return !s.isAir() && s.blocksMotion() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && s.getFluidState().isEmpty();
    }

    /** How deep the water is at a point (0: dry land). */
    public static int waterDepth(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return 0;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, y, bz);
        int depth = 0;
        while (depth < 16 && !level.getFluidState(m).isEmpty()) {
            depth++;
            m.move(0, -1, 0);
        }
        return depth;
    }

    /** True when a village or a hunter's home is near (great threats don't rise on top of them, or roam into them). */
    public static boolean settled(ServerLevel level, BlockPos at, int villageRadius, int homeRadius) {
        PoiManager poi = level.getPoiManager();
        Predicate<Holder<PoiType>> village = h -> h.is(PoiTypes.MEETING);
        Predicate<Holder<PoiType>> home = h -> h.is(PoiTypes.HOME);
        return poi.findClosest(village, at, villageRadius, PoiManager.Occupancy.ANY).isPresent()
                || poi.findClosest(home, at, homeRadius, PoiManager.Occupancy.ANY).isPresent();
    }

    /**
     * A spot for a great threat between {@code min} and {@code max} blocks from {@code around}: loaded, dry, fairly flat
     * land, away from villages and homes. Null if none is found.
     */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, Vec3 around, int min, int max, RandomSource r) {
        BlockPos fallback = null;
        for (int i = 0; i < 48; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * Math.max(1, max - min);
            int x = Mth.floor(around.x + Math.cos(a) * d), z = Mth.floor(around.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            BlockState s = level.getBlockState(ground);
            if (!s.getFluidState().isEmpty() || waterDepth(level, x, z) > 0) continue;
            if (y <= level.getMinBuildHeight() + 8 || y >= level.getMaxBuildHeight() - 40) continue;
            if (!flat(level, x, z, y, 6, 5)) continue;
            if (settled(level, ground, 96, 48)) continue;
            BlockPos spot = ground.above();
            if (flat(level, x, z, y, 6, 3)) return spot;
            if (fallback == null) fallback = spot;
        }
        return fallback;
    }

    /** No point {@code radius} away in eight directions is more than {@code step} blocks above or below {@code y}. */
    private static boolean flat(ServerLevel level, int x, int z, int y, int radius, int step) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = x + (int) Math.round(Math.cos(a) * radius), pz = z + (int) Math.round(Math.sin(a) * radius);
            if (!level.hasChunk(px >> 4, pz >> 4)) return false;
            if (Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - y) > step) return false;
        }
        return true;
    }

    // ================================================================ the schedule

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        WorldBossData data = WorldBossData.get(server);
        Event ev = data.current();
        if (ev != null) {
            tickEvent(level, data, ev, now);
            return;
        }
        if (now % 20 != 0 || !WorldBossConfig.enabled()) return;
        plan(level, data, now);
    }

    /** With nothing abroad: plan the next great threat once a hunter is ready for one, and bring it when it's time. */
    private static void plan(ServerLevel level, WorldBossData data, long now) {
        List<ServerPlayer> ready = hunters(level, WorldBossConfig.minLevel());
        if (data.nextAt() < 0) {
            if (!ready.isEmpty()) data.setNextAt(now + WorldBossConfig.firstTicks());
            return;
        }
        if (now < data.nextAt()) return;
        if (ready.isEmpty()) {
            data.setNextAt(now + RETRY);
            return;
        }
        RandomSource r = level.random;
        ServerPlayer near = ready.get(r.nextInt(ready.size()));
        boolean monarch = data.worldKills() >= WorldBossConfig.monarchAfterKills()
                && !hunters(level, WorldBossConfig.monarchMinLevel()).isEmpty() && r.nextDouble() < WorldBossConfig.monarchChance();
        WorldBossKind kind = pick(monarch ? WorldBossKind.monarchs() : WorldBossKind.worldBosses(), data.lastKind(), r);
        BlockPos spot = findSpot(level, near.position(), WorldBossConfig.minDistance(), WorldBossConfig.maxDistance(), r);
        if (spot == null) {
            data.setNextAt(now + RETRY);
            return;
        }
        begin(level, kind, spot, false);
    }

    private static WorldBossKind pick(WorldBossKind[] kinds, @Nullable WorldBossKind last, RandomSource r) {
        List<WorldBossKind> fresh = new ArrayList<>();
        for (WorldBossKind k : kinds) if (k != last) fresh.add(k);
        if (fresh.isEmpty()) return kinds[r.nextInt(kinds.length)];
        return fresh.get(r.nextInt(fresh.size()));
    }

    /** Awakened hunters in the overworld of at least {@code level}, not in a gate's passage. */
    static List<ServerPlayer> hunters(ServerLevel level, int minLevel) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !SystemAPI.isAwakened(p) || GateService.inTransit(p)) continue;
            if (SystemAPI.get(p).level() >= minLevel) out.add(p);
        }
        return out;
    }

    // ================================================================ a great threat comes

    /**
     * A great threat comes to {@code at}: a world boss rises there now, a monarch's gate opens in the sky above it.
     * {@code called}: by a seal or a command (it doesn't count against the schedule). Null when one is already abroad.
     */
    @Nullable
    public static Event begin(ServerLevel level, WorldBossKind kind, BlockPos at, boolean called) {
        WorldBossData data = WorldBossData.get(level.getServer());
        if (data.current() != null) return null;
        long now = level.getGameTime();
        Event ev = new Event(UUID.randomUUID(), kind, at, now, kind.monarch() ? now + WorldBossConfig.descentTicks() : 0, called);
        data.setCurrent(ev);
        if (kind.monarch()) {
            announce(level, ev, WorldBossPayload.DESCENT);
        } else {
            spawnBoss(level, ev);
            announce(level, ev, WorldBossPayload.APPEAR);
        }
        return ev;
    }

    /** Brings the event's boss into the world: a world boss climbing out of the earth, a monarch coming down from its gate. */
    @Nullable
    static ColossalBoss spawnBoss(ServerLevel level, Event ev) {
        ColossalBoss boss = WorldBossModule.create(level, ev.kind);
        if (boss == null) return null;
        Vec3 at = Vec3.atBottomCenterOf(ev.home);
        boss.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0);
        boss.setupStats();
        boss.setEventId(ev.id);
        boss.setHome(ev.home);
        boss.prepareEntrance(level, at);
        level.addFreshEntity(boss);
        boss.wake();
        ev.entity = boss.getUUID();
        ev.landAt = 0;
        ev.last = ev.home;
        ev.engaged = level.getGameTime();
        WorldBossData.get(level.getServer()).setDirty();
        return boss;
    }

    /** Tells every hunter in the overworld: a banner, a pop-up with how far and which way, and the System's quest. */
    private static void announce(ServerLevel level, Event ev, int what) {
        WorldBossKind kind = ev.kind;
        for (ServerPlayer p : level.players()) {
            int ticks = ev.descending() ? (int) Math.max(0, ev.landAt - level.getGameTime()) : 0;
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(what, kind.ordinal(), eventKey(ev), -1, ev.home, 1, ticks, 0));
            if (!SystemAPI.hasSystem(p)) continue;
            Component where = whereFrom(p, ev.home);
            Component title = Component.translatable(kind.monarch() ? "worldboss.beyondsolo.notify.monarch" : "worldboss.beyondsolo.notify.world");
            Component body = what == WorldBossPayload.DESCENT
                    ? Component.translatable("worldboss.beyondsolo.notify.descent", kind.fullName(), where, ticks / 20)
                    : Component.translatable("worldboss.beyondsolo.notify.risen", kind.fullName(), where);
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT, title, body, 200));
            giveQuest(p, ev);
        }
        level.playSound(null, ev.home, kind.monarch() ? SoundEvents.END_PORTAL_SPAWN : SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6f, 0.6f);
    }

    /** "112 blocks to the north-east". */
    static Component whereFrom(ServerPlayer p, BlockPos at) {
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        // 0 = south (+z), turning towards west: Minecraft's yaw
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        int octant = Math.floorMod((int) Math.round(yaw / 45.0), 8);
        String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        return Component.translatable("worldboss.beyondsolo.where", dist, Component.translatable("worldboss.beyondsolo.dir." + names[octant]));
    }

    /** The System's call to the hunt: offered to every awakened hunter (it is no shame to let it pass). */
    static void giveQuest(ServerPlayer p, Event ev) {
        if (!SystemAPI.isAwakened(p)) return;
        for (Quest q : QuestEngine.data(p).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && ev.id.toString().equals(q.extra().getString("event"))) return;
        }
        SystemData d = SystemAPI.get(p);
        CompoundTag extra = new CompoundTag();
        extra.putString("event", ev.id.toString());
        Quest q = QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.SURPRISE, 5, d.level())
                .title(Component.translatable(ev.kind.monarch() ? "quest.beyondsolo.world_boss.monarch" : "quest.beyondsolo.world_boss",
                        ev.kind.bossName()))
                .description(Component.translatable(ev.kind.monarch() ? "quest.beyondsolo.world_boss.monarch.desc" : "quest.beyondsolo.world_boss.desc",
                        ev.kind.fullName()))
                .custom(SLAY_KEY, 1, Component.translatable("quest_objective.beyondsolo.world_boss", ev.kind.bossName()))
                .timeLimit(WorldBossConfig.abandonTicks() * 4)
                .fixed().xp(ev.kind.monarch() ? 8000L : 3000L).statPoints(ev.kind.monarch() ? 3 : 2)
                .extra(extra)
                .build();
        QuestEngine.offer(p, q, QuestEngine.OFFER_TICKS);
    }

    // ================================================================ while it is abroad

    private static void tickEvent(ServerLevel level, WorldBossData data, Event ev, long now) {
        if (ev.descending()) {
            if (now >= ev.landAt) {
                // it comes down where its gate is, once that land is loaded (it waits in the gate until then)
                if (level.hasChunk(ev.home.getX() >> 4, ev.home.getZ() >> 4)) {
                    ColossalBoss boss = spawnBoss(level, ev);
                    if (boss == null) {
                        end(level, data, ev, false);
                        return;
                    }
                } else if (now - ev.landAt > WorldBossConfig.abandonTicks()) {
                    withdrawn(level, data, ev);
                    return;
                }
            }
            if (now % STATUS_EVERY == 0) status(level, ev, null, now);
            return;
        }
        ColossalBoss boss = ev.entity == null ? null : level.getEntity(ev.entity) instanceof ColossalBoss b ? b : null;
        if (boss != null && boss.isAlive()) {
            ev.last = boss.blockPosition();
            ev.engaged = Math.max(ev.engaged, boss.lastEngaged());
            if (!boss.leaving() && now - ev.engaged > WorldBossConfig.abandonTicks()) {
                boss.leave();
                for (ServerPlayer p : level.players()) {
                    PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.WITHDRAW, ev.kind.ordinal(), eventKey(ev), boss.getId(),
                            boss.blockPosition(), boss.phase(), 0, 0));
                }
            }
        } else if (boss == null && now - ev.engaged > WorldBossConfig.abandonTicks() + 200) {
            // its land is out of reach and no hunter has come: it is gone (it crumbles when its land loads again)
            withdrawn(level, data, ev);
            return;
        }
        if (now % STATUS_EVERY == 0) status(level, ev, boss, now);
    }

    /** The status to every player in the overworld, and each hunter who has hurt it their share. */
    static void status(ServerLevel level, Event ev, @Nullable ColossalBoss boss, long now) {
        int health = boss == null ? 1000 : Math.round(1000 * boss.getHealth() / Math.max(1, boss.getMaxHealth()));
        int land = ev.descending() ? (int) Math.max(0, ev.landAt - now) : 0;
        BlockPos pos = boss != null ? boss.blockPosition() : ev.descending() ? ev.home : ev.last;
        int phase = boss != null ? boss.phase() : 1;
        WorldBossPayload msg = new WorldBossPayload(WorldBossPayload.STATUS, ev.kind.ordinal(), eventKey(ev), boss != null ? boss.getId() : -1, pos,
                phase, health, land);
        List<Map.Entry<UUID, Float>> ranked = boss == null ? List.of() : ranked(boss);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, msg);
            for (int i = 0; i < ranked.size(); i++) {
                if (!ranked.get(i).getKey().equals(p.getUUID())) continue;
                int share = Math.round(1000 * boss.share(p.getUUID()));
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SHARE, ev.kind.ordinal(), eventKey(ev), boss.getId(), pos, phase,
                        share, i + 1));
            }
        }
    }

    /** Hunters by the damage they have done, most first. */
    static List<Map.Entry<UUID, Float>> ranked(ColossalBoss boss) {
        List<Map.Entry<UUID, Float>> out = new ArrayList<>(boss.dealt().entrySet());
        out.sort(Map.Entry.<UUID, Float>comparingByValue(Comparator.reverseOrder()));
        return out;
    }

    /** A short number naming an event on the wire. */
    public static long eventKey(Event ev) {
        return ev.id.getMostSignificantBits() ^ ev.id.getLeastSignificantBits();
    }

    // ================================================================ phases

    /** A boss began a phase: the world shakes, and every hunter near hears its name. */
    public static void phaseBegan(ServerLevel level, ColossalBoss boss, int phase) {
        Event ev = eventOf(level, boss);
        long key = ev != null ? eventKey(ev) : 0;
        for (ServerPlayer p : boss.watchers(WorldBossFx.RANGE)) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.PHASE, boss.kind().ordinal(), key, boss.getId(), boss.blockPosition(),
                    phase, 0, 0));
        }
        boss.shake(phase >= 3 ? 1.6f : 1.1f, 64);
    }

    /** The event a boss belongs to, if it is the one abroad. */
    @Nullable
    public static Event eventOf(ServerLevel level, ColossalBoss boss) {
        Event ev = WorldBossData.get(level.getServer()).current();
        return ev != null && ev.id.equals(boss.eventId()) ? ev : null;
    }

    // ================================================================ the end

    /** It fell: every hunter who hurt it is paid by their share, and the world hears of it. */
    public static void slain(ServerLevel level, ColossalBoss boss, DamageSource source) {
        WorldBossKind kind = boss.kind();
        WorldBossData data = WorldBossData.get(level.getServer());
        Event ev = eventOf(level, boss);
        data.countKill(kind);
        RandomSource r = level.random;
        List<Map.Entry<UUID, Float>> ranked = ranked(boss);
        UUID top = ranked.isEmpty() ? null : ranked.get(0).getKey();
        List<UUID> paid = new ArrayList<>();
        for (Map.Entry<UUID, Float> e : ranked) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            float share = boss.share(e.getKey());
            if (p == null || share < MIN_SHARE) continue;
            paid.add(p.getUUID());
            long xp = pay(p, boss, share, e.getKey().equals(top), r);
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), ev != null ? eventKey(ev) : 0, boss.getId(),
                    boss.blockPosition(), boss.phase(), Math.round(share * 1000), (int) Math.min(Integer.MAX_VALUE, xp)));
        }
        for (ServerPlayer p : level.players()) {
            if (paid.contains(p.getUUID())) continue;
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), ev != null ? eventKey(ev) : 0, boss.getId(),
                    boss.blockPosition(), boss.phase(), -1, 0));
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 3f, 0.8f);
        if (ev != null) {
            // its army falls with it
            MonarchArmy.disband(level, ev.id);
            data.setCurrent(null);
            data.setNextAt(level.getGameTime() + wait(level.random));
            for (ServerPlayer p : level.players()) {
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.GONE, kind.ordinal(), eventKey(ev), -1, ev.home, 1, 0, 0));
            }
        }
    }

    /** Pays one hunter: XP, gold and loot by their share (the one who did the most gets a little more), and keeps the record. */
    private static long pay(ServerPlayer p, ColossalBoss boss, float share, boolean top, RandomSource r) {
        WorldBossKind kind = boss.kind();
        // a full reward from half the damage up: a party of two gets all of it each
        double cut = 0.4 + 0.6 * Math.min(1.0, share * 2.0);
        // System XP grows with its health, like any kill's (the one who lands the last blow also gets that kill's own)
        long xp = Math.round(boss.getMaxHealth() * (kind.monarch() ? 0.7 : 0.25) * cut);
        long gold = Math.round((kind.monarch() ? 24_000 : 10_000) * cut);
        boolean system = SystemAPI.hasSystem(p);
        if (system) {
            SystemAPI.grantXp(p, xp, BeyondSolo.id("world_boss"));
            Economy.add(p, gold, Economy.Reason.KILL);
        }
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(CraftingModule.GREATER_MANA_STONE.get(), (kind.monarch() ? 3 : 2) + r.nextInt(2) + (share >= 0.2f ? 1 : 0)));
        loot.add(new ItemStack(CraftingModule.SUPREME_HEALING_ELIXIR.get(), 1 + (top ? 1 : 0)));
        if (r.nextFloat() < 0.5f + share) loot.add(GateLoot.weapon(5, r));
        if (kind.monarch()) {
            loot.add(GateLoot.weapon(5, r));
            loot.add(new ItemStack(CraftingModule.ELIXIR_OF_REBIRTH.get()));
        }
        WorldBossRecord record = p.getData(WorldBossModule.RECORD.get());
        record.addSlain(kind, top);
        if (kind.monarch()) {
            ItemStack armor = share >= 0.1f ? MonarchArmor.roll(p, kind, record, r) : ItemStack.EMPTY;
            if (!armor.isEmpty()) loot.add(0, armor);
        } else if (top && r.nextDouble() < WorldBossConfig.sealChance()) {
            loot.add(0, new ItemStack(WorldBossModule.MONARCH_SEAL.get()));
        }
        for (ItemStack s : loot) give(p, s);
        QuestEngine.progress(p, SLAY_KEY, 1);
        WorldBossJournal.earned(p, kind, top);
        return system ? xp : 0;
    }

    /** Into the hunter's pack, or at their feet if it is full. */
    static void give(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            var item = p.drop(stack, false);
            if (item != null) {
                item.setNoPickUpDelay();
                item.setTarget(p.getUUID());
            }
        }
    }

    /** It has gone (sunk into the earth, risen back into its gate). */
    static void left(ServerLevel level, ColossalBoss boss) {
        Event ev = eventOf(level, boss);
        if (ev != null) withdrawn(level, WorldBossData.get(level.getServer()), ev);
    }

    /** The event ends without a kill: no hunter came, or none was left. */
    private static void withdrawn(ServerLevel level, WorldBossData data, Event ev) {
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.WITHDRAW, ev.kind.ordinal(), eventKey(ev), -1, ev.last, 1, 0, 0));
            if (SystemAPI.hasSystem(p)) {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                        Component.translatable(ev.kind.monarch() ? "worldboss.beyondsolo.notify.monarch" : "worldboss.beyondsolo.notify.world"),
                        Component.translatable(ev.kind.monarch() ? "worldboss.beyondsolo.notify.ascended" : "worldboss.beyondsolo.notify.sank",
                                ev.kind.fullName())));
            }
        }
        end(level, data, ev, false);
    }

    /** Clears the event and plans the next one. */
    static void end(ServerLevel level, WorldBossData data, Event ev, boolean slain) {
        MonarchArmy.disband(level, ev.id);
        if (ev.entity != null && level.getEntity(ev.entity) instanceof ColossalBoss b && b.isAlive() && !b.leaving()) b.discard();
        data.setCurrent(null);
        data.setNextAt(level.getGameTime() + wait(level.random));
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.GONE, ev.kind.ordinal(), eventKey(ev), -1, ev.home, 1, 0, 0));
        }
    }

    /** The wait before the next great threat: the interval, give or take a quarter. */
    private static long wait(RandomSource r) {
        int base = WorldBossConfig.intervalTicks();
        return Math.round(base * (0.75 + 0.5 * r.nextDouble()));
    }

    /** Ends whatever is abroad at once, without a kill (commands, tests). */
    public static boolean stop(ServerLevel level) {
        WorldBossData data = WorldBossData.get(level.getServer());
        Event ev = data.current();
        if (ev == null) return false;
        end(level, data, ev, false);
        return true;
    }

    /** The boss of the event abroad, if its land is loaded. */
    @Nullable
    public static ColossalBoss currentBoss(ServerLevel level) {
        Event ev = WorldBossData.get(level.getServer()).current();
        if (ev == null || ev.entity == null) return null;
        return level.getEntity(ev.entity) instanceof ColossalBoss b ? b : null;
    }

    // ================================================================ loading, joining

    /** A boss (or one of a monarch's soldiers) loaded back in after its event ended out of sight crumbles. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity e = event.getEntity();
        Event ev = WorldBossData.get(level.getServer()).current();
        if (e instanceof ColossalBoss boss) {
            if (!event.loadedFromDisk()) return;
            if (ev == null || !ev.id.equals(boss.eventId()) || !boss.getUUID().equals(ev.entity)) event.setCanceled(true);
            return;
        }
        if (event.loadedFromDisk() && e.getPersistentData().hasUUID(ARMY_TAG)
                && (ev == null || !ev.id.equals(e.getPersistentData().getUUID(ARMY_TAG)))) {
            event.setCanceled(true);
        }
    }

    /** A hunter who logs in (or comes back to the overworld) hears of whatever is abroad at once. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) catchUp(p);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTo() == Level.OVERWORLD) catchUp(p);
    }

    private static void catchUp(ServerPlayer p) {
        ServerLevel level = p.server.overworld();
        Event ev = WorldBossData.get(p.server).current();
        if (ev == null || p.level() != level) return;
        ColossalBoss boss = ev.entity == null ? null : level.getEntity(ev.entity) instanceof ColossalBoss b ? b : null;
        int health = boss == null ? 1000 : Math.round(1000 * boss.getHealth() / Math.max(1, boss.getMaxHealth()));
        int land = ev.descending() ? (int) Math.max(0, ev.landAt - level.getGameTime()) : 0;
        PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.STATUS, ev.kind.ordinal(), eventKey(ev), boss != null ? boss.getId() : -1,
                boss != null ? boss.blockPosition() : ev.last, boss != null ? boss.phase() : 1, health, land));
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        MonarchArmy.clear();
    }

    private WorldBosses() {}
}
