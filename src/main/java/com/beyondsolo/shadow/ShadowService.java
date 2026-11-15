package com.beyondsolo.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.network.ShadowSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the shadow army does on the server: souls left by fallen creatures, the extraction ritual, calling
 * shadows out and back, XP and ranks, fallen shadows, mounts. Only a player whose subclass
 * {@link Subclass#canRaiseShadows() can raise shadows} (the necromancer) gets any of it.
 *
 * <p>Events are wired in {@link ShadowHooks}. Other modules should read through {@link #canRaise}, {@link #army}
 * and the {@code ShadowEvent}s rather than touching {@link ShadowData}.</p>
 */
public final class ShadowService {
    /** Ticks the extraction ritual takes. */
    public static final int RITUAL_TICKS = 40;
    /** How long a soul lingers (ticks); a boss's lingers longer. */
    public static final int SOUL_TICKS = 20 * 20;
    public static final int BOSS_SOUL_TICKS = 20 * 60;
    public static final int TRIES = 3;
    /** How close the player has to be to a soul to extract it. */
    public static final double REACH = 7;
    /** A fallen shadow rests this long (ticks) before it can be called again; a boss shadow longer. */
    public static final int RECOVER_TICKS = 20 * 30;
    public static final int BOSS_RECOVER_TICKS = 20 * 90;

    /** A soul lingering where a creature fell. */
    public static final class Soul {
        final int id;
        final ResourceKey<Level> dimension;
        final Vec3 pos;
        final UUID owner;
        final ResourceLocation type;
        final ShadowRecord.Role role;
        final ShadowRank rank;
        final float health;
        final float damage;
        final float speed;
        final boolean boss;
        final int bossRank;
        int tries = TRIES;
        long expires;
        /** Game time the running ritual ends, or 0. */
        long ritualEnd;

        Soul(int id, ResourceKey<Level> dimension, Vec3 pos, UUID owner, ResourceLocation type, ShadowRecord.Role role, ShadowRank rank,
             float health, float damage, float speed, boolean boss, int bossRank, long expires) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
            this.owner = owner;
            this.type = type;
            this.role = role;
            this.rank = rank;
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.boss = boss;
            this.bossRank = bossRank;
            this.expires = expires;
        }

        public int id() { return id; }
        public Vec3 pos() { return pos; }
        public ResourceLocation type() { return type; }
        public boolean boss() { return boss; }
        public int tries() { return tries; }
        public boolean inRitual() { return ritualEnd > 0; }
    }

    private static final List<Soul> SOULS = new ArrayList<>();
    private static int nextSoulId = 1;
    /** Players we have seen command shadows (lets game tests' fake players be found). */
    private static final Map<UUID, ServerPlayer> MASTERS = new HashMap<>();
    /** Tests: the next extraction succeeds (true) or fails (false) whatever the odds. */
    private static @Nullable Boolean forced;

    // ================================================================ who and how many

    public static ShadowData army(Player player) {
        return player.getData(ShadowModule.ARMY);
    }

    /** Only a necromancer raises shadows. */
    public static boolean canRaise(Player player) {
        Subclass s = JobAPI.subclass(player);
        return s != null && s.canRaiseShadows();
    }

    public static int intellect(Player player) {
        return SystemAPI.get(player).stat(Stat.INT);
    }

    /** How many shadows the army can hold. */
    public static int capacity(Player player) {
        SystemData d = SystemAPI.get(player);
        return Math.min(300, 10 + d.level() / 2 + Math.max(0, d.stat(Stat.INT) - Stat.BASE) / 2);
    }

    /** How many shadows can stand in the world at once. */
    public static int activeLimit(Player player) {
        SystemData d = SystemAPI.get(player);
        return Math.min(40, 3 + d.level() / 10 + Math.max(0, d.stat(Stat.INT) - Stat.BASE) / 8);
    }

    private static float intMult(int intellect) {
        return 1f + 0.01f * Math.max(0, intellect - Stat.BASE);
    }

    public static float maxHealth(ShadowRecord r, int intellect) {
        return Math.max(4, r.baseHealth() * r.rank().power() * (1 + 0.05f * (r.level() - 1)) * intMult(intellect));
    }

    public static float damage(ShadowRecord r, int intellect) {
        return Math.max(1, r.baseDamage() * r.rank().power() * (1 + 0.05f * (r.level() - 1)) * intMult(intellect));
    }

    public static float speed(ShadowRecord r) {
        if (r.isMount()) return 0.3f + 0.012f * r.rank().ordinal();
        return Mth.clamp(r.baseSpeed(), 0.25f, 0.36f) * (1 + 0.02f * r.rank().ordinal());
    }

    /** Mana to call a shadow out. */
    public static float summonCost(ShadowRecord r) {
        return 3 + 2 * r.rank().ordinal() + (r.boss() ? 10 : 0);
    }

    /**
     * Chance one extraction try works: easy for weak creatures, hard for strong ones, and much harder for bosses.
     * Intelligence and level make it easier.
     */
    public static float chance(Player player, float creatureHealth, boolean boss) {
        SystemData d = SystemAPI.get(player);
        float power = 20 + 2 * d.level() + 3 * Math.max(0, d.stat(Stat.INT) - Stat.BASE);
        float hard = creatureHealth / power;
        if (boss) return Mth.clamp(0.6f - 0.12f * hard, 0.08f, 0.6f);
        return Mth.clamp(0.95f - 0.3f * hard, 0.1f, 0.95f);
    }

    /** Tests and staging: decide the next extraction. */
    public static void forceNext(@Nullable Boolean success) {
        forced = success;
    }

    // ================================================================ souls

    public static List<Soul> souls() {
        return List.copyOf(SOULS);
    }

    /** A creature fell to a necromancer (or one of their shadows): its soul lingers for them. */
    public static @Nullable Soul leaveSoul(ServerPlayer owner, LivingEntity victim) {
        if (!canRaise(owner) || victim instanceof Player || victim instanceof ShadowEntity) return null;
        EntityType<?> type = victim.getType();
        if (type.is(ShadowModule.NO_SOUL)) return null;
        boolean boss = victim instanceof DungeonBoss || type.is(ShadowModule.BOSSES);
        int bossRank = victim instanceof DungeonBoss db ? db.rank().ordinal() : boss ? 3 : -1;
        float hp = victim.getMaxHealth();
        float dmg = victim.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? (float) victim.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : 2 + hp * 0.08f;
        float speed = victim.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)
                ? (float) victim.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) : 0.25f;
        return addSoul(owner, (ServerLevel) victim.level(), victim.position(), type, hp, dmg, speed, boss, bossRank);
    }

    /** Puts a soul down directly (used by {@link #leaveSoul} and the staging command). */
    public static Soul addSoul(ServerPlayer owner, ServerLevel level, Vec3 pos, EntityType<?> type, float hp, float dmg, float speed,
                               boolean boss, int bossRank) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        ShadowRecord.Role role = type.is(ShadowModule.MOUNTS) ? ShadowRecord.Role.MOUNT
                : type.is(ShadowModule.CASTERS) ? ShadowRecord.Role.CASTER : ShadowRecord.Role.WARRIOR;
        ShadowRank rank;
        if (boss) rank = bossRank >= 4 ? ShadowRank.COMMANDER : bossRank >= 2 ? ShadowRank.CAPTAIN : ShadowRank.KNIGHT;
        else rank = hp <= 20 ? ShadowRank.HUSK : hp <= 40 ? ShadowRank.SOLDIER : hp <= 80 ? ShadowRank.VANGUARD : ShadowRank.KNIGHT;
        // bosses keep half their bulk; anything else is capped so a shadow never outgrows its master's army
        float health = boss ? Math.min(400, hp * 0.5f) : Math.min(200, hp);
        float damage = Math.min(boss ? 30 : 20, dmg);
        long now = level.getGameTime();
        Soul s = new Soul(nextSoulId++, level.dimension(), pos, owner.getUUID(), id, role, rank, health, damage, speed, boss, bossRank,
                now + (boss ? BOSS_SOUL_TICKS : SOUL_TICKS));
        SOULS.add(s);
        MASTERS.put(owner.getUUID(), owner);
        soulFx(level, s, ShadowFxPayload.Kind.SOUL, s.tries, id.toString());
        return s;
    }

    private static @Nullable Soul nearestSoul(ServerPlayer p) {
        Soul best = null;
        double bestD = REACH * REACH;
        for (Soul s : SOULS) {
            if (!s.owner.equals(p.getUUID()) || s.dimension != p.level().dimension() || s.inRitual()) continue;
            double d = s.pos.distanceToSqr(p.position());
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Starts the extraction ritual on the nearest soul. False if there is none in reach. */
    public static boolean startExtraction(ServerPlayer p) {
        Soul s = nearestSoul(p);
        if (s == null) return false;
        if (army(p).size() >= capacity(p)) {
            alert(p, "shadow.beyondsolo.army_full", Component.translatable("shadow.beyondsolo.army_full.body", capacity(p)));
            return true;
        }
        ServerLevel level = p.serverLevel();
        s.ritualEnd = level.getGameTime() + RITUAL_TICKS;
        s.expires = Math.max(s.expires, s.ritualEnd + 40);
        soulFx(level, s, ShadowFxPayload.Kind.RITUAL, RITUAL_TICKS, s.type.toString());
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.9f, 0.6f);
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2f, 0.5f);
        return true;
    }

    private static void finishRitual(ServerPlayer p, Soul s) {
        ServerLevel level = p.serverLevel();
        s.ritualEnd = 0;
        boolean ok = forced != null ? forced : p.getRandom().nextFloat() < chance(p, s.boss ? s.health * 2 : s.health, s.boss);
        forced = null;
        if (!ok) {
            s.tries--;
            if (s.tries <= 0) {
                SOULS.remove(s);
                soulFx(level, s, ShadowFxPayload.Kind.CRUMBLE, 0, "");
                level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
            } else {
                soulFx(level, s, ShadowFxPayload.Kind.FAILED, s.tries, "");
                level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.5f);
            }
            return;
        }
        SOULS.remove(s);
        ShadowRecord r = new ShadowRecord(UUID.randomUUID(), s.type, s.role, s.rank, s.health, s.damage, s.speed, level.getGameTime());
        if (s.boss) {
            r.setBoss(s.bossRank);
            r.setName(ShadowNames.boss(p.getRandom()));
        }
        ShadowData army = army(p);
        army.add(r);
        PacketDistributor.sendToPlayersNear(level, null, s.pos.x, s.pos.y, s.pos.z, 64, new ShadowFxPayload(
                ShadowFxPayload.Kind.EXTRACTED.ordinal(), s.id, (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, r.rank().color(),
                s.boss ? 1 : 0, r.displayName().getString()));
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
        // it rises where it fell, if there is room in the field
        if (army.activeCount() < activeLimit(p)) summonAt(p, r, s.pos, false);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable(s.boss ? "shadow.beyondsolo.extracted.boss" : "shadow.beyondsolo.extracted"),
                Component.translatable("shadow.beyondsolo.extracted.body", r.displayName(), r.rank().displayName())));
        sync(p);
    }

    // ================================================================ calling out and back

    /** The raise key: extract a soul in reach, otherwise call the army out. */
    public static void rise(ServerPlayer p) {
        if (!canRaise(p)) {
            alert(p, "shadow.beyondsolo.not_necromancer", Component.translatable("shadow.beyondsolo.not_necromancer.body"));
            return;
        }
        if (startExtraction(p)) return;
        summonAll(p);
    }

    /** Calls out every ready shadow (not the mounts) up to the limit, strongest first. Returns how many came. */
    public static int summonAll(ServerPlayer p) {
        ShadowData army = army(p);
        long now = p.level().getGameTime();
        List<ShadowRecord> ready = new ArrayList<>();
        for (ShadowRecord r : army.shadows()) {
            if (!r.isMount() && !army.isActive(r.id()) && !r.recovering(now)) ready.add(r);
        }
        if (ready.isEmpty()) {
            alert(p, "shadow.beyondsolo.none_ready", Component.translatable(army.size() == 0
                    ? "shadow.beyondsolo.none_ready.empty" : "shadow.beyondsolo.none_ready.body"));
            return 0;
        }
        ready.sort(Comparator.comparingInt((ShadowRecord r) -> r.boss() ? 1 : 0).thenComparingInt(r -> r.rank().ordinal())
                .thenComparingInt(ShadowRecord::level).reversed());
        int room = activeLimit(p) - army.activeCount();
        int n = 0;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        for (ShadowRecord r : ready) {
            if (n >= room) break;
            if (!p.isCreative() && !SystemAPI.consumeMana(p, summonCost(r))) {
                if (n == 0) alert(p, "shadow.beyondsolo.no_mana", Component.translatable("shadow.beyondsolo.no_mana.body"));
                break;
            }
            // a half ring behind the master
            double a = yaw + Math.PI / 2 + (n % 2 == 0 ? 1 : -1) * (0.35 + 0.3 * (n / 2)) + Math.PI;
            double rad = 2.4 + 0.6 * (n / 6);
            Vec3 at = p.position().add(-Math.sin(a) * rad, 0, Math.cos(a) * rad);
            final int delay = n * 3;
            ServerScheduler.schedule(delay, () -> {
                if (!p.isRemoved() && army(p).get(r.id()) != null && PENDING.equals(army(p).entityOf(r.id()))) summonAt(p, r, ground(p.serverLevel(), at), true);
            });
            army.setActive(r.id(), PENDING);
            n++;
        }
        sync(p);
        return n;
    }

    /** Placeholder entity id while a staggered summon is on its way. */
    private static final UUID PENDING = new UUID(0, 0);

    /** Calls one shadow out of the ground at a spot. */
    public static @Nullable ShadowEntity summonAt(ServerPlayer p, ShadowRecord r, Vec3 at, boolean fx) {
        ServerLevel level = p.serverLevel();
        ShadowEntity e = ShadowModule.SHADOW.get().create(level);
        if (e == null) return null;
        MASTERS.put(p.getUUID(), p);
        e.configure(r, p);
        e.moveTo(at.x, at.y, at.z, p.getYRot() + (float) (p.getRandom().nextGaussian() * 20), 0);
        e.yHeadRot = e.yBodyRot = e.getYRot();
        level.addFreshEntity(e);
        army(p).setActive(r.id(), e.getUUID());
        if (fx) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShadowFxPayload(ShadowFxPayload.Kind.SUMMON.ordinal(), e.getId(),
                    (float) at.x, (float) at.y, (float) at.z, r.rank().color(), r.boss() ? 1 : 0, ""));
            PacketDistributor.sendToPlayer(p, new ShadowFxPayload(ShadowFxPayload.Kind.SUMMON.ordinal(), e.getId(),
                    (float) at.x, (float) at.y, (float) at.z, r.rank().color(), r.boss() ? 1 : 0, ""));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        sync(p);
        return e;
    }

    /** Calls one shadow by id (from the Shadows tab). */
    public static boolean summon(ServerPlayer p, UUID recordId) {
        if (!canRaise(p)) return false;
        ShadowData army = army(p);
        ShadowRecord r = army.get(recordId);
        if (r == null || army.isActive(recordId) || r.recovering(p.level().getGameTime())) return false;
        if (army.activeCount() >= activeLimit(p)) {
            alert(p, "shadow.beyondsolo.limit", Component.translatable("shadow.beyondsolo.limit.body", activeLimit(p)));
            return false;
        }
        if (!p.isCreative() && !SystemAPI.consumeMana(p, summonCost(r))) {
            alert(p, "shadow.beyondsolo.no_mana", Component.translatable("shadow.beyondsolo.no_mana.body"));
            return false;
        }
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        summonAt(p, r, ground(p.serverLevel(), p.position().add(look.scale(2.5))), true);
        return true;
    }

    /** Sends every shadow out in the world back into the dark. Returns how many went. */
    public static int recallAll(ServerPlayer p) {
        ShadowData army = army(p);
        int n = 0;
        for (UUID rid : List.copyOf(army.active().keySet())) {
            if (dismiss(p, rid)) n++;
        }
        sync(p);
        return n;
    }

    /** Sends one shadow back into the dark. */
    public static boolean dismiss(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        UUID eid = army.entityOf(recordId);
        if (eid == null) return false;
        army.setInactive(recordId);
        if (p.serverLevel().getEntity(eid) instanceof ShadowEntity e && e.isAlive()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShadowFxPayload(ShadowFxPayload.Kind.RECALL.ordinal(), e.getId(),
                    (float) e.getX(), (float) e.getY(), (float) e.getZ(), e.rank().color(), 0, ""));
            e.startSinking();
        }
        sync(p);
        return true;
    }

    /** Lets a shadow go for good. */
    public static boolean release(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        if (army.get(recordId) == null) return false;
        dismiss(p, recordId);
        army.remove(recordId);
        sync(p);
        return true;
    }

    /** The mount key: off the mount if riding one, otherwise call the strongest ready mount and climb on. */
    public static void mount(ServerPlayer p, @Nullable UUID which) {
        if (!canRaise(p)) {
            alert(p, "shadow.beyondsolo.not_necromancer", Component.translatable("shadow.beyondsolo.not_necromancer.body"));
            return;
        }
        ShadowData army = army(p);
        if (which == null && p.getVehicle() instanceof ShadowEntity riding && p.getUUID().equals(riding.getOwnerUUID())) {
            p.stopRiding();
            if (riding.recordId() != null) dismiss(p, riding.recordId());
            return;
        }
        long now = p.level().getGameTime();
        ShadowRecord pick = null;
        for (ShadowRecord r : army.shadows()) {
            if (!r.isMount() || r.recovering(now)) continue;
            if (which != null && !r.id().equals(which)) continue;
            if (pick == null || r.rank().ordinal() > pick.rank().ordinal()) pick = r;
        }
        if (pick == null) {
            alert(p, "shadow.beyondsolo.no_mount", Component.translatable("shadow.beyondsolo.no_mount.body"));
            return;
        }
        UUID eid = army.entityOf(pick.id());
        if (eid != null && p.serverLevel().getEntity(eid) instanceof ShadowEntity e && e.isAlive() && !e.isSinking()) {
            if (e.distanceToSqr(p) > 6 * 6) slipTo(e, p);
            if (!e.isRising()) p.startRiding(e);
            return;
        }
        if (eid != null) army.setInactive(pick.id());
        if (army.activeCount() >= activeLimit(p)) {
            alert(p, "shadow.beyondsolo.limit", Component.translatable("shadow.beyondsolo.limit.body", activeLimit(p)));
            return;
        }
        if (!p.isCreative() && !SystemAPI.consumeMana(p, summonCost(pick))) {
            alert(p, "shadow.beyondsolo.no_mana", Component.translatable("shadow.beyondsolo.no_mana.body"));
            return;
        }
        ShadowEntity e = summonAt(p, pick, p.position(), true);
        if (e != null) {
            ServerScheduler.schedule(ShadowEntity.MOUNT_RISE_TICKS, () -> {
                if (e.isAlive() && !e.isSinking() && !p.isRemoved() && p.getVehicle() == null && e.distanceToSqr(p) < 6 * 6) p.startRiding(e);
            });
        }
    }

    /** Commands from the client. */
    public static void action(ServerPlayer p, int action, String shadow) {
        MASTERS.put(p.getUUID(), p);
        UUID id = null;
        try {
            if (!shadow.isEmpty()) id = UUID.fromString(shadow);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        switch (action) {
            case com.beyondsolo.network.ShadowActionPayload.RISE -> rise(p);
            case com.beyondsolo.network.ShadowActionPayload.RECALL -> {
                if (canRaise(p)) recallAll(p);
            }
            case com.beyondsolo.network.ShadowActionPayload.MOUNT -> mount(p, null);
            case com.beyondsolo.network.ShadowActionPayload.RIDE -> {
                if (id != null) mount(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.SUMMON -> {
                if (id != null) summon(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.DISMISS -> {
                if (id != null) dismiss(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.RELEASE -> {
                if (id != null) release(p, id);
            }
            default -> {
            }
        }
    }

    // ================================================================ growing

    /** Gives a shadow XP; climbs levels and ranks, with the rank-up moment. */
    public static void addXp(ServerPlayer p, ShadowRecord r, int amount) {
        ShadowRank before = r.rank();
        int levelBefore = r.level();
        int ranks = r.addXp(amount);
        if (ranks > 0) rankedUp(p, r, before);
        if (ranks > 0 || r.level() != levelBefore) {
            ShadowEntity e = entity(p, r.id());
            if (e != null) e.refresh(r, p);
        }
        sync(p);
    }

    /** Raises a shadow one rank straight away (staging). */
    public static void rankUp(ServerPlayer p, ShadowRecord r) {
        if (r.rank().isMax()) return;
        ShadowRank before = r.rank();
        r.setRank(before.next());
        r.setLevel(1);
        r.setXp(0);
        rankedUp(p, r, before);
        ShadowEntity e = entity(p, r.id());
        if (e != null) e.refresh(r, p);
        sync(p);
    }

    private static void rankedUp(ServerPlayer p, ShadowRecord r, ShadowRank before) {
        ShadowEntity e = entity(p, r.id());
        Vec3 at = e != null ? e.position() : p.position();
        ShadowFxPayload fx = new ShadowFxPayload(ShadowFxPayload.Kind.RANK_UP.ordinal(), e != null ? e.getId() : -1,
                (float) at.x, (float) at.y, (float) at.z, r.rank().color(), r.rank().ordinal(), r.displayName().getString());
        if (e != null) PacketDistributor.sendToPlayersTrackingEntity(e, fx);
        PacketDistributor.sendToPlayer(p, fx);
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.7f);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("shadow.beyondsolo.rank_up"),
                Component.translatable("shadow.beyondsolo.rank_up.body", r.displayName(), before.displayName(), r.rank().displayName())));
    }

    /** A creature died: souls, kill counts and XP. */
    static void creatureDied(LivingEntity victim, @Nullable Entity killer) {
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player) return;
        ServerPlayer owner = null;
        ShadowEntity byShadow = null;
        if (killer instanceof ServerPlayer sp) owner = sp;
        else if (killer instanceof ShadowEntity s) {
            byShadow = s;
            owner = s.master();
        }
        if (owner == null || !canRaise(owner)) return;
        if (victim instanceof ShadowEntity) return;
        leaveSoul(owner, victim);
        int xp = Math.max(1, Math.round(victim.getMaxHealth()));
        ShadowData army = army(owner);
        if (byShadow != null && byShadow.recordId() != null) {
            ShadowRecord r = army.get(byShadow.recordId());
            if (r != null) {
                r.addKill();
                addXp(owner, r, xp);
            }
            // the master learns from their army's battles too, at half the usual rate
            if (SystemAPI.isAwakened(owner)) {
                long sys = Math.max(1, Math.round(victim.getMaxHealth() * Config.KILL_XP_MULTIPLIER.get() * 0.5));
                SystemAPI.grantXp(owner, sys, SystemAPI.XP_SOURCE_KILL);
            }
        } else {
            // the army learns a little from every kill its master makes near it
            for (Map.Entry<UUID, UUID> en : army.active().entrySet()) {
                if (level.getEntity(en.getValue()) instanceof ShadowEntity e && e.distanceToSqr(owner) < 32 * 32) {
                    ShadowRecord r = army.get(en.getKey());
                    if (r != null) addXp(owner, r, Math.max(1, xp / 4));
                }
            }
        }
    }

    /** +15% damage for shadows fighting near one of their master's boss shadows. */
    public static float auraBonus(ShadowEntity shadow) {
        if (shadow.isBoss()) return 1f;
        UUID owner = shadow.getOwnerUUID();
        if (owner == null) return 1f;
        for (ShadowEntity s : shadow.level().getEntitiesOfClass(ShadowEntity.class, shadow.getBoundingBox().inflate(12),
                o -> o.isBoss() && owner.equals(o.getOwnerUUID()) && o.isAlive())) {
            return 1.15f;
        }
        return 1f;
    }

    // ================================================================ entity lifecycle

    /** The master behind a shadow's owner id (players in the list first, then fake players seen before). */
    static @Nullable ServerPlayer masterOf(ServerLevel level, UUID id) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
        if (p == null) p = MASTERS.get(id);
        return p != null && !p.isRemoved() && p.level() == level ? p : null;
    }

    /** Whether a shadow in the world is still one its master has called. */
    static boolean owns(ServerPlayer p, ShadowEntity e) {
        if (e.recordId() == null) return false;
        UUID eid = army(p).entityOf(e.recordId());
        return eid != null && eid.equals(e.getUUID());
    }

    /** A shadow in the world fell. */
    static void fallen(ShadowEntity e) {
        ServerPlayer p = e.master();
        if (p == null || e.recordId() == null) return;
        ShadowData army = army(p);
        ShadowRecord r = army.get(e.recordId());
        if (r == null) return;
        if (owns(p, e)) army.setInactive(r.id());
        r.setRecoverUntil(p.level().getGameTime() + (r.boss() ? BOSS_RECOVER_TICKS : RECOVER_TICKS));
        if (r.boss() || r.isMount()) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable("shadow.beyondsolo.fallen"),
                    Component.translatable("shadow.beyondsolo.fallen.body", r.displayName(), (r.boss() ? BOSS_RECOVER_TICKS : RECOVER_TICKS) / 20)));
        }
        sync(p);
    }

    /** A shadow left the world (sank, or its master went away). */
    static void gone(ShadowEntity e) {
        ServerPlayer p = e.master();
        if (p != null && owns(p, e) && e.recordId() != null) {
            army(p).setInactive(e.recordId());
            sync(p);
        }
    }

    static @Nullable ShadowEntity entity(ServerPlayer p, UUID recordId) {
        UUID eid = army(p).entityOf(recordId);
        return eid != null && p.serverLevel().getEntity(eid) instanceof ShadowEntity e ? e : null;
    }

    /** Slips a shadow through the dark to its master's side. */
    static void slipTo(ShadowEntity e, ServerPlayer m) {
        RandomSource r = e.getRandom();
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 2 + r.nextDouble() * 2;
            Vec3 at = ground(m.serverLevel(), m.position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            if (Math.abs(at.y - m.getY()) > 3) continue;
            if (!m.level().noCollision(e, e.getBoundingBox().move(at.subtract(e.position())))) continue;
            ((ServerLevel) e.level()).sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
            e.teleportTo(at.x, at.y, at.z);
            e.getNavigation().stop();
            ((ServerLevel) e.level()).sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 10, 0.3, 0.4, 0.3, 0.01);
            return;
        }
    }

    /** The standing spot at or below/above a point. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return new Vec3(at.x, p.getY(), at.z);
            }
        }
        return at;
    }

    // ================================================================ ticking and players coming and going

    static void tick(net.minecraft.server.MinecraftServer server) {
        Iterator<Soul> it = SOULS.iterator();
        List<Runnable> later = new ArrayList<>();
        while (it.hasNext()) {
            Soul s = it.next();
            ServerLevel level = server.getLevel(s.dimension);
            ServerPlayer owner = level == null ? null : masterOf(level, s.owner);
            if (level == null) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (s.inRitual()) {
                if (owner == null || owner.position().distanceToSqr(s.pos) > (REACH + 3) * (REACH + 3) || !owner.isAlive()) {
                    s.ritualEnd = 0; // broken off
                    soulFx(level, s, ShadowFxPayload.Kind.FAILED, s.tries, "");
                } else if (now >= s.ritualEnd) {
                    later.add(() -> finishRitual(owner, s));
                }
                continue;
            }
            if (now >= s.expires || (owner == null && server.getPlayerList().getPlayer(s.owner) == null && !MASTERS.containsKey(s.owner))) {
                it.remove();
                soulFx(level, s, ShadowFxPayload.Kind.SOUL_GONE, 0, "");
            }
        }
        later.forEach(Runnable::run);
    }

    static void login(ServerPlayer p) {
        MASTERS.put(p.getUUID(), p);
        army(p).clearActive();
        sync(p);
    }

    static void logout(ServerPlayer p) {
        discardAll(p);
        MASTERS.remove(p.getUUID());
        SOULS.removeIf(s -> s.owner.equals(p.getUUID()));
    }

    /** Removes every shadow of a player from the world at once (no sinking). */
    static void discardAll(ServerPlayer p) {
        ShadowData army = army(p);
        for (UUID eid : List.copyOf(army.active().values())) {
            for (ServerLevel level : p.getServer().getAllLevels()) {
                Entity e = level.getEntity(eid);
                if (e instanceof ShadowEntity) {
                    e.ejectPassengers();
                    e.discard();
                }
            }
        }
        army.clearActive();
    }

    /** The master changed dimension: the shadows that were out follow through the dark. */
    static void changedDimension(ServerPlayer p) {
        ShadowData army = army(p);
        List<UUID> out = new ArrayList<>(army.active().keySet());
        discardAll(p);
        if (out.isEmpty() || !canRaise(p)) {
            sync(p);
            return;
        }
        ServerScheduler.schedule(10, () -> {
            if (p.isRemoved()) return;
            int n = 0;
            for (UUID rid : out) {
                ShadowRecord r = army(p).get(rid);
                if (r == null || r.isMount() || army(p).isActive(rid) || army(p).activeCount() >= activeLimit(p)) continue;
                double a = n * 2.1;
                summonAt(p, r, ground(p.serverLevel(), p.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5)), true);
                n++;
            }
            sync(p);
        });
    }

    /** First job change into a necromancer: the army opens, with a steed to start it. */
    static void becameNecromancer(ServerPlayer p) {
        ShadowData army = army(p);
        boolean hasMount = army.shadows().stream().anyMatch(ShadowRecord::isMount);
        if (!hasMount) {
            ShadowRecord steed = new ShadowRecord(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("skeleton_horse"),
                    ShadowRecord.Role.MOUNT, ShadowRank.SOLDIER, 30, 4, 0.3f, p.level().getGameTime());
            steed.setName(Component.translatable("shadow.beyondsolo.starter_steed").getString());
            army.add(steed);
        }
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("shadow.beyondsolo.unlocked"),
                Component.translatable("shadow.beyondsolo.unlocked.body")));
        sync(p);
    }

    /** Clears a player's army entirely (staging). */
    public static void reset(ServerPlayer p) {
        discardAll(p);
        army(p).clear();
        SOULS.removeIf(s -> s.owner.equals(p.getUUID()));
        sync(p);
    }

    /** Puts a shadow straight into the army (staging and tests). */
    public static ShadowRecord give(ServerPlayer p, EntityType<?> type, @Nullable ShadowRank rank, boolean boss) {
        MASTERS.put(p.getUUID(), p);
        ShadowRecord.Role role = type.is(ShadowModule.MOUNTS) ? ShadowRecord.Role.MOUNT
                : type.is(ShadowModule.CASTERS) ? ShadowRecord.Role.CASTER : ShadowRecord.Role.WARRIOR;
        float hp = 20, dmg = 3, speed = 0.25f;
        Entity probe = type.create(p.serverLevel());
        if (probe instanceof LivingEntity le) {
            hp = le.getMaxHealth();
            if (le.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) dmg = (float) le.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            if (le.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) speed = (float) le.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (probe instanceof com.beyondsolo.gate.GateBossEntity) {
                hp = com.beyondsolo.gate.GateTheme.of(com.beyondsolo.system.PlayerRank.C).bossHealth();
                dmg = com.beyondsolo.gate.GateTheme.of(com.beyondsolo.system.PlayerRank.C).bossDamage();
            }
        }
        if (probe != null) probe.discard();
        ShadowRank rk = rank != null ? rank : boss ? ShadowRank.CAPTAIN : ShadowRank.SOLDIER;
        ShadowRecord r = new ShadowRecord(UUID.randomUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(type), role, rk,
                boss ? Math.min(400, hp * 0.5f) : Math.min(200, hp), Math.min(boss ? 30 : 20, dmg), speed, p.level().getGameTime());
        if (boss) {
            r.setBoss(2);
            r.setName(ShadowNames.boss(p.getRandom()));
        }
        army(p).add(r);
        sync(p);
        return r;
    }

    // ================================================================ talking to the client

    public static void sync(ServerPlayer p) {
        ShadowData army = army(p);
        CompoundTag tag = army.serializeNBT(p.registryAccess());
        ListTag active = new ListTag();
        for (UUID id : army.active().keySet()) active.add(StringTag.valueOf(id.toString()));
        tag.put("active", active);
        tag.putInt("cap", capacity(p));
        tag.putInt("limit", activeLimit(p));
        tag.putBoolean("canRaise", canRaise(p));
        tag.putLong("time", p.level().getGameTime());
        PacketDistributor.sendToPlayer(p, new ShadowSyncPayload(tag));
    }

    private static void soulFx(ServerLevel level, Soul s, ShadowFxPayload.Kind kind, int extra, String text) {
        int color = s.boss ? 0xFF4A6A : s.rank.color();
        PacketDistributor.sendToPlayersNear(level, null, s.pos.x, s.pos.y, s.pos.z, 64, new ShadowFxPayload(kind.ordinal(), s.id,
                (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, color, extra, text));
    }

    private static void alert(ServerPlayer p, String title, Component body) {
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable(title), body));
    }

    /** Server stopped: forget the souls. */
    static void clearAll() {
        SOULS.clear();
        MASTERS.clear();
        forced = null;
    }

    static ResourceLocation id(String path) {
        return BeyondSolo.id(path);
    }

    private ShadowService() {}
}
