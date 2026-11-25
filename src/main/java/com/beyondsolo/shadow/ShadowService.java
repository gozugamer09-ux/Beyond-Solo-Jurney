package com.beyondsolo.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.network.ShadowSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
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
    /** A soul the player looks straight at can be extracted from a little further ({@link SoulAim}). */
    public static final double AIM_REACH = 10;
    /** How far a soul of theirs can lie for the raise key to say "come closer" rather than call the army. */
    public static final double NOTICE = 16;
    /** How far the necromancer can step from a soul before its ritual breaks off. */
    public static final double RITUAL_HOLD = AIM_REACH + 4;
    /** The same pop-up again this soon after (ticks) is not sent: a key held down, or pressed again and again. */
    static final int ALERT_QUIET_TICKS = 30;
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
        /** The shape of the creature that fell (how it moved, its size, its look). */
        final ShadowForm form;
        int tries = TRIES;
        long expires;
        /** Game time the running ritual ends, or 0. */
        long ritualEnd;

        Soul(int id, ResourceKey<Level> dimension, Vec3 pos, UUID owner, ResourceLocation type, ShadowRecord.Role role, ShadowRank rank,
             float health, float damage, float speed, boolean boss, int bossRank, ShadowForm form, long expires) {
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
            this.form = form;
            this.expires = expires;
        }

        public int id() { return id; }
        public UUID owner() { return owner; }
        public Vec3 pos() { return pos; }
        public ResourceLocation type() { return type; }
        public boolean boss() { return boss; }
        /** What the shadow would do (fight, cast, carry). */
        public ShadowRecord.Role role() { return role; }
        /** The rank the shadow would rise at. */
        public ShadowRank rank() { return rank; }
        /** The shape the shadow would take. */
        public ShadowForm form() { return form; }
        /** The health the shadow would be built on (and the essence the soul leaves if it fades). */
        public float health() { return health; }
        public int tries() { return tries; }
        public boolean inRitual() { return ritualEnd > 0; }
    }

    private static final List<Soul> SOULS = new ArrayList<>();
    private static int nextSoulId = 1;
    /** Players we have seen command shadows (lets game tests' fake players be found). */
    private static final Map<UUID, ServerPlayer> MASTERS = new HashMap<>();
    /** Tests and staging: a player's next extraction succeeds (true) or fails (false) whatever the odds. */
    private static final Map<UUID, Boolean> FORCED = new HashMap<>();
    /** The last pop-up each player was sent from here, and when (server ticks): the same one is not sent twice in a row. */
    private static final Map<UUID, Said> SAID = new HashMap<>();

    /** @param count how many pop-ups this player was sent from here in all (for the tests) */
    private record Said(String title, int at, int count) {}

    // ================================================================ who and how many

    public static ShadowData army(Player player) {
        return player.getData(ShadowModule.ARMY);
    }

    /** A necromancer raises shadows, and so does whoever wears the crown of Night. */
    public static boolean canRaise(Player player) {
        Subclass s = JobAPI.subclass(player);
        return s != null && s.canRaiseShadows() || com.beyondsolo.monarch.MonarchAPI.dominion(player) == com.beyondsolo.monarch.Dominion.NIGHT;
    }

    /** The highest rank this master's shadows can climb to: past the marshals for a crowned monarch. */
    public static ShadowRank top(Player player) {
        return ShadowRank.top(com.beyondsolo.monarch.MonarchAPI.crowned(player));
    }

    /** Whether a shadow of this master's can't grow any more. */
    public static boolean maxed(Player player, ShadowRecord r) {
        return r.maxed(top(player));
    }

    public static int intellect(Player player) {
        return SystemAPI.get(player).stat(Stat.INT);
    }

    /** How many shadows the army can hold (half as many again for a monarch). */
    public static int capacity(Player player) {
        SystemData d = SystemAPI.get(player);
        int cap = Math.min(300, 10 + d.level() / 2 + Math.max(0, d.stat(Stat.INT) - Stat.BASE) / 2);
        return com.beyondsolo.monarch.MonarchAPI.crowned(player) ? cap * 3 / 2 : cap;
    }

    /** How many shadows can stand in the world at once (a few more for a monarch, more again for a monarch of Night). */
    public static int activeLimit(Player player) {
        SystemData d = SystemAPI.get(player);
        return Math.min(40, 3 + d.level() / 10 + Math.max(0, d.stat(Stat.INT) - Stat.BASE) / 8)
                + com.beyondsolo.monarch.MonarchAPI.extraShadows(player);
    }

    private static float intMult(int intellect) {
        return 1f + 0.01f * Math.max(0, intellect - Stat.BASE);
    }

    /** A name its master gave it binds a shadow closer: {@link ShadowPromotions#BOND} stronger. */
    private static float bond(ShadowRecord r) {
        return r.bonded() ? ShadowPromotions.BOND : 1f;
    }

    public static float maxHealth(ShadowRecord r, int intellect) {
        return Math.max(4, r.baseHealth() * r.rank().power(r.level()) * intMult(intellect) * bond(r));
    }

    public static float damage(ShadowRecord r, int intellect) {
        return Math.max(1, r.baseDamage() * r.rank().power(r.level()) * intMult(intellect) * bond(r));
    }

    /**
     * A shadow's health beside its master: what its record gives ({@link #maxHealth(ShadowRecord, int)}), grown so it
     * keeps pace with the creatures of its master's level ({@link Balance#summonHealth}). Works on both sides.
     */
    public static float maxHealth(ShadowRecord r, SystemData master) {
        return (float) (maxHealth(r, master.stat(Stat.INT)) * Balance.summonHealth(master));
    }

    /** A shadow's blows beside its master ({@link Balance#summonDamage}). Works on both sides. */
    public static float damage(ShadowRecord r, SystemData master) {
        return (float) (damage(r, master.stat(Stat.INT)) * Balance.summonDamage(master));
    }

    /**
     * The health and blows a creature leaves its soul with, in the sizes shadows are built on (the numbers before the
     * System's power curve): the growth its rank gave it is taken back off and the old, gentler growth put back on
     * (+40% health a rank; a gate boss its old 120 + 108 health and 7 + 3.5 damage a rank). The shadow grows with its
     * master instead. Public for the game tests.
     */
    public static float[] soulSize(LivingEntity victim) {
        float hp = victim.getMaxHealth();
        float dmg = victim.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? (float) victim.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : 2 + hp * 0.08f;
        if (victim instanceof DungeonBoss boss) {
            PlayerRank rank = boss.rank();
            hp = hp / Balance.bossHealth(rank) * 120f * (1 + 0.9f * rank.tier());
            dmg = dmg / Balance.bossDamage(rank) * (7f + 3.5f * rank.tier());
        } else if (victim instanceof Mob mob) {
            PlayerRank rank = Enemies.rankOf(mob);
            hp = (float) (hp / Enemies.healthGrowth(mob)) * (rank == null ? 1f : 1f + 0.4f * rank.tier());
        }
        return new float[]{hp, dmg};
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

    /** Tests and staging: every soul this player left fades at the next tick (into essence, if they are near). */
    public static void fadeSouls(Player player) {
        for (Soul s : SOULS) if (s.owner.equals(player.getUUID()) && !s.inRitual()) s.expires = 0;
    }

    /** Tests and staging: decide a player's next extraction (null: back to the odds). */
    public static void forceNext(Player player, @Nullable Boolean success) {
        if (success == null) FORCED.remove(player.getUUID());
        else FORCED.put(player.getUUID(), success);
    }

    // ================================================================ souls

    public static List<Soul> souls() {
        return List.copyOf(SOULS);
    }

    /**
     * A creature fell to a necromancer (or one of their shadows): its soul lingers for them. Any living creature leaves
     * one, ours, vanilla's or another mod's, but those tagged {@code shadow/no_soul} (a player, a training dummy, a
     * vision): its shadow takes its shape and look ({@link ShadowForm#of}), how it got about, and what it did (steeds
     * carry, archers cast). Bosses (ours, the world's threats, and any tagged {@code shadow/bosses} or {@code c:bosses})
     * rise as named elites.
     */
    public static @Nullable Soul leaveSoul(ServerPlayer owner, LivingEntity victim) {
        if (!canRaise(owner) || victim instanceof Player || victim instanceof ShadowEntity) return null;
        EntityType<?> type = victim.getType();
        if (type.is(ShadowModule.NO_SOUL) || !(victim.level() instanceof ServerLevel level)) return null;
        boolean boss = ShadowForm.isBoss(victim);
        int bossRank = boss ? ShadowForm.bossRank(victim) : -1;
        float[] size = sizeOf(victim);
        float speed = victim.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)
                ? (float) victim.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) : 0.25f;
        return addSoul(owner, level, soulSpot(level, victim, owner), type, size[0], size[1], speed, boss, bossRank,
                ShadowForm.roleOf(type, victim), ShadowForm.of(victim));
    }

    /** {@link #soulSize}, or for a creature whose numbers can't be read the usual way, its health and blows to match. */
    private static float[] sizeOf(LivingEntity victim) {
        try {
            return soulSize(victim);
        } catch (RuntimeException | LinkageError e) {
            float hp = victim.getMaxHealth();
            return new float[]{hp, 2 + hp * 0.08f};
        }
    }

    /**
     * Where a fallen creature's soul lingers: where it fell, or, for one that died in the air (a bat, a ghast shot
     * down), on the ground or the water under it, where its master can reach it. Over nothing at all (the void, the
     * End's sky), at its master's side.
     */
    static Vec3 soulSpot(ServerLevel level, LivingEntity victim, ServerPlayer owner) {
        Vec3 at = victim.position();
        if (victim.onGround() || victim.isInWater() || victim.isInLava()) return at;
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        int floor = level.getMinBuildHeight();
        for (int i = 0; i < 96 && p.getY() > floor; i++) {
            BlockPos below = p.below();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(below);
            if (!state.getCollisionShape(level, below).isEmpty() || !state.getFluidState().isEmpty()) {
                // on the block, or on the water's face
                double top = state.getFluidState().isEmpty() ? below.getY() + state.getCollisionShape(level, below).max(net.minecraft.core.Direction.Axis.Y)
                        : p.getY();
                return new Vec3(at.x, Math.min(at.y, top), at.z);
            }
            p.move(net.minecraft.core.Direction.DOWN);
        }
        return owner.position();
    }

    /** Puts a soul down directly (the staging command and tests): its role and form those of a fresh one of its kind. */
    public static Soul addSoul(ServerPlayer owner, ServerLevel level, Vec3 pos, EntityType<?> type, float hp, float dmg, float speed,
                               boolean boss, int bossRank) {
        return addSoul(owner, level, pos, type, hp, dmg, speed, boss, bossRank, null, null);
    }

    /**
     * Puts a soul down: the shadow it would raise is built on these numbers (see {@link #leaveSoul}), does what
     * {@code role} says and takes {@code form}'s shape (null: those of a fresh one of its kind). A steed walks, whatever
     * it was: its master rides it.
     */
    public static Soul addSoul(ServerPlayer owner, ServerLevel level, Vec3 pos, EntityType<?> type, float hp, float dmg, float speed,
                               boolean boss, int bossRank, @Nullable ShadowRecord.Role role, @Nullable ShadowForm form) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (role == null) role = ShadowForm.probeRole(level, type);
        if (form == null) form = ShadowForm.probe(level, type);
        if (role == ShadowRecord.Role.MOUNT) form = form.withMove(ShadowForm.Movement.WALK);
        // a creature with odd numbers (none, or broken ones) still makes a shadow
        if (!Float.isFinite(hp) || hp <= 0) hp = 20;
        if (!Float.isFinite(dmg) || dmg < 0) dmg = 2;
        if (!Float.isFinite(speed) || speed <= 0) speed = 0.25f;
        ShadowRank rank;
        if (boss) rank = bossRank >= 4 ? ShadowRank.COMMANDER : bossRank >= 2 ? ShadowRank.CAPTAIN : ShadowRank.KNIGHT;
        else rank = hp <= 20 ? ShadowRank.HUSK : hp <= 40 ? ShadowRank.SOLDIER : hp <= 80 ? ShadowRank.VANGUARD : ShadowRank.KNIGHT;
        // bosses keep half their bulk; anything else is capped so a shadow never outgrows its master's army
        float health = boss ? Math.min(400, hp * 0.5f) : Math.min(200, hp);
        float damage = Math.min(boss ? 30 : 20, dmg);
        long now = level.getGameTime();
        Soul s = new Soul(nextSoulId++, level.dimension(), pos, owner.getUUID(), id, role, rank, health, damage, speed, boss, bossRank,
                form, now + (boss ? BOSS_SOUL_TICKS : SOUL_TICKS));
        SOULS.add(s);
        MASTERS.put(owner.getUUID(), owner);
        soulFx(level, s, ShadowFxPayload.Kind.SOUL, s.tries, id.toString());
        // what fell there, drawn in ink in its pool for everyone near, and whose soul it is
        CompoundTag shape = form.save(true);
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(pos) > 64 * 64) continue;
            PacketDistributor.sendToPlayer(near, new com.beyondsolo.network.ShadowSoulPayload(s.id, bossRank, near == owner,
                    (int) Math.max(0, s.expires - now), shape));
        }
        return s;
    }

    /** The souls this player could extract now: theirs, in their world, not already in a ritual. */
    private static List<Soul> soulsFor(ServerPlayer p) {
        List<Soul> list = new ArrayList<>();
        for (Soul s : SOULS) {
            if (s.owner.equals(p.getUUID()) && s.dimension == p.level().dimension() && !s.inRitual()) list.add(s);
        }
        return list;
    }

    /** Which soul the raise key reaches for this player (the one they look at, else the nearest): see {@link SoulAim}. */
    public static SoulAim.Pick<Soul> aim(ServerPlayer p) {
        return SoulAim.pick(p.getEyePosition(), p.getLookAngle(), p.position(), soulsFor(p), Soul::pos);
    }

    /** Whether an extraction ritual of this player's is running in their world. */
    public static boolean inRitual(ServerPlayer p) {
        for (Soul s : SOULS) if (s.owner.equals(p.getUUID()) && s.dimension == p.level().dimension() && s.inRitual()) return true;
        return false;
    }

    /** Starts the extraction ritual on the soul the player reaches ({@link #aim}). False if there is none in reach. */
    public static boolean startExtraction(ServerPlayer p) {
        Soul s = aim(p).soul();
        return s != null && startExtraction(p, s);
    }

    /** Starts the extraction ritual on this soul (true: the key did something, even if only to say the army is full). */
    public static boolean startExtraction(ServerPlayer p, Soul s) {
        if (!SOULS.contains(s) || s.inRitual()) return false;
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

    /**
     * Tests and staging: the ritual on this soul ends now, and works. Returns the shadow it raised (null if the soul is
     * gone or the army is full); it rises where the soul was if there is room in the field.
     */
    public static @Nullable ShadowRecord extractNow(ServerPlayer p, Soul s) {
        ShadowData army = army(p);
        if (!SOULS.contains(s) || army.size() >= capacity(p)) return null;
        int before = army.size();
        FORCED.put(p.getUUID(), true);
        finishRitual(p, s);
        return army.size() > before ? army.byIndex(army.size() - 1) : null;
    }

    private static void finishRitual(ServerPlayer p, Soul s) {
        ServerLevel level = p.serverLevel();
        s.ritualEnd = 0;
        Boolean forced = FORCED.remove(p.getUUID());
        boolean ok = forced != null ? forced : p.getRandom().nextFloat() < chance(p, s.boss ? s.health * 2 : s.health, s.boss);
        if (!ok) {
            s.tries--;
            if (s.tries <= 0) {
                SOULS.remove(s);
                soulFx(level, s, ShadowFxPayload.Kind.CRUMBLE, 0, "");
                level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
                // what is left of it drifts into its master's shadow
                ShadowPromotions.soulFaded(p, level, s);
            } else {
                soulFx(level, s, ShadowFxPayload.Kind.FAILED, s.tries, "");
                level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.5f);
            }
            return;
        }
        SOULS.remove(s);
        ShadowRecord r = new ShadowRecord(UUID.randomUUID(), s.type, s.role, s.rank, s.health, s.damage, s.speed, level.getGameTime());
        r.setForm(s.form);
        if (s.boss) {
            r.setBoss(s.bossRank);
            r.setName(ShadowNames.boss(p.getRandom()));
        }
        ShadowData army = army(p);
        army.add(r);
        PacketDistributor.sendToPlayersNear(level, null, s.pos.x, s.pos.y, s.pos.z, 64, new ShadowFxPayload(
                ShadowFxPayload.Kind.EXTRACTED.ordinal(), s.id, (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, r.rank().color(),
                s.boss ? 1 : 0, r.wireName()));
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
        // it always rises where it fell, so its master sees what answered; with the field already full it kneels a
        // moment, then sinks into its master's shadow to wait
        boolean full = army.used() >= activeLimit(p);
        summonAt(p, r, s.pos, false);
        if (full) {
            UUID rid = r.id();
            ServerScheduler.schedule(RISE_THEN_REST_TICKS, () -> {
                ShadowData now = army(p);
                if (!p.isRemoved() && now.get(rid) != null && now.isActive(rid) && now.used() > activeLimit(p)) dismiss(p, rid);
            });
        }
        Component body = Component.translatable("shadow.beyondsolo.extracted.body", r.displayName(), r.rank().displayName());
        if (full) body = body.copy().append(" ").append(Component.translatable("shadow.beyondsolo.extracted.full", activeLimit(p)));
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable(s.boss ? "shadow.beyondsolo.extracted.boss" : "shadow.beyondsolo.extracted"), body));
        sync(p);
    }

    /** How long (ticks) a shadow risen into a full field stands before it sinks into its master's shadow. */
    public static final int RISE_THEN_REST_TICKS = 70;

    // ================================================================ calling out and back

    /**
     * The raise key: extract the soul the player reaches (the one they look at, else the nearest), otherwise call the
     * army out. While a ritual of theirs runs, or with a soul of theirs plainly meant but out of reach, it never calls
     * the army: the first does nothing more, the second says to come closer.
     */
    public static void rise(ServerPlayer p) {
        if (!canRaise(p)) {
            alert(p, "shadow.beyondsolo.not_necromancer", Component.translatable("shadow.beyondsolo.not_necromancer.body"));
            return;
        }
        SoulAim.Pick<Soul> pick = aim(p);
        if (pick.soul() != null && startExtraction(p, pick.soul())) return;
        if (inRitual(p)) return;
        if (pick.far() != null) {
            Soul s = pick.far();
            int blocks = (int) Math.ceil(s.pos.distanceTo(p.position()));
            tell(p, SystemNotification.Kind.INFO, "shadow.beyondsolo.too_far",
                    Component.translatable("shadow.beyondsolo.too_far.body", typeName(s.type), blocks));
            return;
        }
        summonAll(p);
    }

    /** A creature kind's name, as the player's game shows it. */
    private static Component typeName(ResourceLocation type) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(EntityType::getDescription).orElse(Component.literal(type.getPath()));
    }

    /** Calls out every ready shadow (not the mounts) up to the limit, strongest first. Returns how many came. */
    public static int summonAll(ServerPlayer p) {
        return summonAll(p, false);
    }

    /**
     * A Night monarch's authority (Eclipse of Kings): every resting shadow gets up, those already out are mended, and
     * the army is called out at no cost, up to the limit. Returns how many came out.
     */
    public static int eclipse(ServerPlayer p) {
        ShadowData army = army(p);
        for (ShadowRecord r : army.shadows()) r.setRecoverUntil(0);
        for (Map.Entry<UUID, UUID> en : army.active().entrySet()) {
            if (p.serverLevel().getEntity(en.getValue()) instanceof ShadowEntity e && e.isAlive()) e.setHealth(e.getMaxHealth());
        }
        return army.size() == 0 ? 0 : summonAll(p, true);
    }

    private static int summonAll(ServerPlayer p, boolean free) {
        ShadowData army = army(p);
        long now = p.level().getGameTime();
        List<ShadowRecord> ready = new ArrayList<>();
        for (ShadowRecord r : army.shadows()) {
            // a shadow standing guard stays at its post: it comes back by itself, or when called back from it
            if (!r.isMount() && !r.posted() && !army.isActive(r.id()) && !r.recovering(now)) ready.add(r);
        }
        if (ready.isEmpty()) {
            if (!free) noneReady(p, army, now);
            return 0;
        }
        ready.sort(Comparator.comparingInt((ShadowRecord r) -> r.boss() ? 1 : 0).thenComparingInt(r -> r.rank().ordinal())
                .thenComparingInt(ShadowRecord::level).reversed());
        int room = activeLimit(p) - army.used();
        if (room <= 0) {
            if (!free) alert(p, "shadow.beyondsolo.limit", Component.translatable("shadow.beyondsolo.limit.body", activeLimit(p)));
            return 0;
        }
        int n = 0;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        for (ShadowRecord r : ready) {
            if (n >= room) break;
            if (!free && !p.isCreative() && !SystemAPI.consumeMana(p, summonCost(r))) {
                if (n == 0) alert(p, "shadow.beyondsolo.no_mana", Component.translatable("shadow.beyondsolo.no_mana.body"));
                break;
            }
            // a half ring behind the master
            double a = yaw + Math.PI + (n % 2 == 0 ? 1 : -1) * (0.35 + 0.3 * (n / 2));
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

    /**
     * Why no shadow answered the call, said plainly: an empty army, an army of nothing but its steed (it comes with its
     * own key), shadows still recovering (and when the first is back), or every one already out.
     */
    private static void noneReady(ServerPlayer p, ShadowData army, long now) {
        int fighters = 0, out = 0, resting = 0;
        long soonest = Long.MAX_VALUE;
        for (ShadowRecord r : army.shadows()) {
            if (r.isMount()) continue;
            fighters++;
            if (army.isActive(r.id()) || r.posted()) out++;
            else if (r.recovering(now)) {
                resting++;
                soonest = Math.min(soonest, r.recoverUntil() - now);
            }
        }
        if (army.size() == 0) {
            alert(p, "shadow.beyondsolo.none_ready", Component.translatable("shadow.beyondsolo.none_ready.empty"));
        } else if (fighters == 0) {
            tell(p, SystemNotification.Kind.INFO, "shadow.beyondsolo.none_ready.mount_only",
                    Component.translatable("shadow.beyondsolo.none_ready.mount_only.body", Component.keybind("key.beyondsolo.shadow_mount")));
        } else if (resting > 0) {
            alert(p, "shadow.beyondsolo.none_ready.resting", Component.translatable("shadow.beyondsolo.none_ready.resting.body",
                    resting, Math.max(1, (soonest + 19) / 20)));
        } else {
            tell(p, SystemNotification.Kind.INFO, "shadow.beyondsolo.none_ready.all_out",
                    Component.translatable("shadow.beyondsolo.none_ready.all_out.body", out, Component.keybind("key.beyondsolo.shadow_recall")));
        }
    }

    /** Calls one shadow out of the ground at a spot. */
    public static @Nullable ShadowEntity summonAt(ServerPlayer p, ShadowRecord r, Vec3 at, boolean fx) {
        ServerLevel level = p.serverLevel();
        ShadowEntity e = ShadowModule.SHADOW.get().create(level);
        if (e == null) return null;
        MASTERS.put(p.getUUID(), p);
        ensureForm(level, r);
        e.configure(r, p);
        if (r.postedIn(level.dimension())) e.setPost(r.post());
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
        // called out by name: a guard away at its post leaves the post and comes to its master
        if (r.posted()) r.setPost(null, null);
        if (army.used() >= activeLimit(p)) {
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

    /** Sends every shadow out in the world back into the dark, but those standing guard. Returns how many went. */
    public static int recallAll(ServerPlayer p) {
        ShadowData army = army(p);
        int n = 0;
        for (UUID rid : List.copyOf(army.active().keySet())) {
            ShadowRecord r = army.get(rid);
            if (r != null && r.posted()) continue;
            if (dismiss(p, rid)) n++;
        }
        sync(p);
        return n;
    }

    /** Sends one shadow back into the dark (a guard leaves its post for it). */
    public static boolean dismiss(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        ShadowRecord rec = army.get(recordId);
        UUID eid = army.entityOf(recordId);
        if (eid == null) {
            // a guard away at its post: sent back all the same
            if (rec != null && rec.posted()) {
                rec.setPost(null, null);
                sync(p);
                return true;
            }
            return false;
        }
        if (rec != null) rec.setPost(null, null);
        army.setInactive(recordId);
        if (p.serverLevel().getEntity(eid) instanceof ShadowEntity e && e.isAlive()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShadowFxPayload(ShadowFxPayload.Kind.RECALL.ordinal(), e.getId(),
                    (float) e.getX(), (float) e.getY(), (float) e.getZ(), e.rank().color(), 0, ""));
            e.startSinking();
        }
        sync(p);
        return true;
    }

    /** Lets a shadow go for good; a little of what it grew to stays behind as essence. */
    public static boolean release(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        ShadowRecord r = army.get(recordId);
        if (r == null) return false;
        dismiss(p, recordId);
        army.remove(recordId);
        army.addEssence(ShadowPromotions.releaseEssence(r));
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
        if (army.used() >= activeLimit(p)) {
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
            case com.beyondsolo.network.ShadowActionPayload.POST -> {
                if (id != null) post(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.UNPOST -> {
                if (id != null) unpost(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.PROMOTE -> {
                if (id != null) ShadowPromotions.promote(p, id);
            }
            case com.beyondsolo.network.ShadowActionPayload.LOOK -> {
                if (id != null) sendLook(p, id);
            }
            default -> {
            }
        }
    }

    // ================================================================ growing

    /**
     * Gives a shadow XP; climbs levels and ranks, with the rank-up moment, up to the top of a rank its master has to
     * promote it out of (then the System says it is ready, once).
     */
    public static void addXp(ServerPlayer p, ShadowRecord r, int amount) {
        ShadowRank before = r.rank();
        int levelBefore = r.level();
        boolean waiting = r.ready(top(p));
        int ranks = r.addXp(amount, top(p));
        if (ranks > 0) rankedUp(p, r, before);
        if (!waiting && r.ready(top(p))) ShadowPromotions.becameReady(p, r);
        if (ranks > 0 || r.level() != levelBefore) {
            ShadowEntity e = entity(p, r.id());
            if (e != null) e.refresh(r, p);
        }
        sync(p);
    }

    /** Raises a shadow one rank straight away (staging). */
    public static void rankUp(ServerPlayer p, ShadowRecord r) {
        if (r.rank().ordinal() >= top(p).ordinal()) return;
        if (r.rank().next().byPromotion()) {
            // a rank only a promotion reaches: staged as one, with its moment
            ShadowPromotions.promoted(p, r, true);
            return;
        }
        ShadowRank before = r.rank();
        r.setLevel(before.entryLevel(r.level()));
        r.setRank(before.next());
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
                (float) at.x, (float) at.y, (float) at.z, r.rank().color(), r.rank().ordinal(), r.wireName());
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
        // what struck the last blow; failing that (it burned, fell, drowned, or something else finished it), whoever
        // fought it last, as Minecraft gives the kill to for its experience
        Entity by = killer instanceof ServerPlayer || killer instanceof ShadowEntity ? killer : victim.getKillCredit();
        if (by instanceof ServerPlayer sp) owner = sp;
        else if (by instanceof ShadowEntity s) {
            byShadow = s;
            owner = s.master();
        }
        if (owner == null || !canRaise(owner)) return;
        if (victim instanceof ShadowEntity) return;
        leaveSoul(owner, victim);
        // shadows learn by their own sizes, not by what the System grew the creature to
        int xp = Math.max(1, Math.round(sizeOf(victim)[0]));
        ShadowData army = army(owner);
        if (byShadow != null && byShadow.recordId() != null) {
            ShadowRecord r = army.get(byShadow.recordId());
            if (r != null) {
                r.addKill();
                addXp(owner, r, xp);
            }
            // the master learns from their army's battles too, at half the usual rate
            if (SystemAPI.isAwakened(owner)) {
                long sys = Math.max(1, Math.round(Enemies.killWorth(victim) * Config.KILL_XP_MULTIPLIER.get() * 0.5));
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

    /**
     * +15% damage for shadows fighting near one of their master's boss shadows, +10% near one of its commanders (or
     * anything higher, {@link ShadowPromotions#LEAD}); inside their master's domain (a crowned monarch's) they fight
     * harder still ({@link com.beyondsolo.monarch.MonarchDomain#shadowBonus}). The best of these counts.
     */
    public static float auraBonus(ShadowEntity shadow) {
        float best = Math.max(com.beyondsolo.monarch.MonarchDomain.shadowBonus(shadow), com.beyondsolo.light.LightSanctum.shadowBonus(shadow));
        if (shadow.isBoss()) return best;
        UUID owner = shadow.getOwnerUUID();
        if (owner == null) return best;
        for (ShadowEntity s : shadow.level().getEntitiesOfClass(ShadowEntity.class, shadow.getBoundingBox().inflate(12),
                o -> o != shadow && owner.equals(o.getOwnerUUID()) && o.isAlive()
                        && (o.isBoss() || o.rank().ordinal() >= ShadowRank.COMMANDER.ordinal()))) {
            best = Math.max(best, s.isBoss() ? 1.15f : ShadowPromotions.LEAD);
        }
        return best;
    }

    // ================================================================ entity lifecycle

    /** The master behind a shadow's owner id (players in the list first, then fake players seen before). */
    static @Nullable ServerPlayer masterOf(ServerLevel level, UUID id) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
        if (p == null) p = MASTERS.get(id);
        return p != null && !p.isRemoved() && p.level() == level ? p : null;
    }

    /** Whether this shadow standing in the world is one of this player's, called out by them. */
    public static boolean isOwn(ServerPlayer p, ShadowEntity e) {
        return p.getUUID().equals(e.getOwnerUUID()) && owns(p, e);
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

    /** The shadow of this record standing in the master's world right now, or null. */
    public static @Nullable ShadowEntity entity(ServerPlayer p, UUID recordId) {
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

    // ================================================================ standing guard

    /** Shadows can't stand guard in the System's own worlds (gates, the wastes, trials): only out in the world. */
    public static boolean canGuardIn(ServerLevel level) {
        return !level.dimension().location().getNamespace().equals(com.beyondsolo.BeyondSolo.MOD_ID);
    }

    /** Leaves a shadow that is out to guard the spot where it stands. False if it can't (a mount, a gate's world). */
    public static boolean post(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        ShadowRecord r = army.get(recordId);
        ShadowEntity e = r == null ? null : entity(p, recordId);
        if (r == null || e == null || !e.isAlive() || r.isMount() || !canRaise(p)) return false;
        if (!canGuardIn(p.serverLevel())) {
            alert(p, "shadow.beyondsolo.post.not_here", Component.translatable("shadow.beyondsolo.post.not_here.body"));
            return false;
        }
        Vec3 spot = ground(p.serverLevel(), e.position());
        r.setPost(p.serverLevel().dimension(), spot);
        e.setPost(spot);
        e.ejectPassengers();
        e.getNavigation().stop();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShadowFxPayload(ShadowFxPayload.Kind.SUMMON.ordinal(), e.getId(),
                (float) spot.x, (float) spot.y, (float) spot.z, r.rank().color(), 0, ""));
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("shadow.beyondsolo.post"),
                Component.translatable("shadow.beyondsolo.post.body", r.displayName())));
        sync(p);
        return true;
    }

    /** Calls a guard back from its post: it follows its master again (or rests, if it was away from the world). */
    public static boolean unpost(ServerPlayer p, UUID recordId) {
        ShadowRecord r = army(p).get(recordId);
        if (r == null || !r.posted()) return false;
        r.setPost(null, null);
        ShadowEntity e = entity(p, recordId);
        if (e != null) e.setPost(null);
        sync(p);
        return true;
    }

    /**
     * Umbral Exchange: the master and one of their shadows trade places at once. The shadow is either in the world
     * (any distance in the same world) or a guard away at its post in this world; either way it rises anew where the
     * master stood, as wounded as it was. A guard keeps guarding: its post moves to the spot it now stands on. False
     * if the shadow can't be reached.
     */
    public static boolean exchange(ServerPlayer p, UUID recordId) {
        ShadowData army = army(p);
        ShadowRecord r = army.get(recordId);
        if (r == null || r.recovering(p.level().getGameTime())) return false;
        ServerLevel level = p.serverLevel();
        ShadowEntity e = entity(p, recordId);
        Vec3 there;
        if (e != null && e.isAlive()) there = e.position();
        else if (r.postedIn(level.dimension()) && !army.isActive(recordId)) there = r.post();
        else return false;
        Vec3 here = p.position();
        Vec3 spot = ground(level, here);
        p.stopRiding();
        // the shadow takes the master's place; standing guard, it now guards that spot
        if (r.posted()) r.setPost(level.dimension(), spot);
        // the master steps out of the dark where the shadow stood
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) p.moveTo(there.x, there.y, there.z, p.getYRot(), p.getXRot());
        else p.teleportTo(level, there.x, there.y, there.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        // and the shadow rises out of the dark where they stood: a new body that keeps the old one's wounds (moved
        // instead, the old one would be seen sliding along the ground from one spot to the other)
        ShadowEntity risen = summonAt(p, r, spot, false);
        if (e != null && e.isAlive()) {
            if (risen != null) {
                risen.setHealth(Math.min(risen.getMaxHealth(), e.getHealth()));
                for (net.minecraft.world.effect.MobEffectInstance fx : e.getActiveEffects()) {
                    risen.addEffect(new net.minecraft.world.effect.MobEffectInstance(fx));
                }
            }
            e.ejectPassengers();
            e.discard();
        }
        sync(p);
        return true;
    }

    /** Guards whose post is in their master's world and loaded again stand up at it (every second). */
    private static void reformGuards() {
        for (ServerPlayer p : List.copyOf(MASTERS.values())) {
            if (p.isRemoved() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) || !canRaise(p)) continue;
            ShadowData army = army(p);
            if (army.awayPosts() == 0) continue;
            ServerLevel level = p.serverLevel();
            long now = level.getGameTime();
            for (ShadowRecord r : List.copyOf(army.shadows())) {
                if (!r.postedIn(level.dimension()) || army.isActive(r.id()) || r.recovering(now)) continue;
                net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(r.post());
                if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) continue;
                summonAt(p, r, r.post(), true);
            }
        }
    }

    /**
     * A shadow left the world. Sent away or fallen, that was counted already. Otherwise nobody is near its spot any more
     * (its master walked away from a guard, or jumped far from the ones following them), and Minecraft keeps such a
     * shadow, frozen and out of sight, until someone comes back. It goes back into its master's shadow instead: a guard
     * stays posted and stands up again when its spot is near (see {@link #reformGuards}), and one that was following
     * rises again at its master's side.
     */
    static void left(ShadowEntity e) {
        ServerPlayer p = e.master();
        UUID rid = e.recordId();
        if (p == null || rid == null || !owns(p, e)) return;
        ShadowData army = army(p);
        army.setInactive(rid);
        sync(p);
        if (e.isRemoved()) return;
        // still held, out of sight: let it go once the level is done moving entities about
        ServerScheduler.schedule(1, () -> {
            if (!e.isRemoved()) e.discard();
        });
        ShadowRecord r = army.get(rid);
        if (r == null || r.posted() || r.isMount()) return;
        ServerScheduler.schedule(10, () -> {
            ShadowData now = army(p);
            if (p.isRemoved() || !canRaise(p) || now.get(rid) == null || now.isActive(rid) || r.posted() || now.used() >= activeLimit(p)) return;
            // behind its master, where the army rises when called
            double a = p.getYRot() * Mth.DEG_TO_RAD + Math.PI + p.getRandom().nextGaussian() * 0.35;
            summonAt(p, r, ground(p.serverLevel(), p.position().add(-Math.sin(a) * 2.4, 0, Math.cos(a) * 2.4)), true);
        });
    }

    // ================================================================ ticking and players coming and going

    static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) reformGuards();
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
                if (owner == null || owner.position().distanceToSqr(s.pos) > RITUAL_HOLD * RITUAL_HOLD || !owner.isAlive()) {
                    s.ritualEnd = 0; // broken off
                    soulFx(level, s, ShadowFxPayload.Kind.FAILED, s.tries, "");
                } else if (now >= s.ritualEnd) {
                    later.add(() -> finishRitual(owner, s));
                }
                continue;
            }
            if (now >= s.expires || (owner == null && server.getPlayerList().getPlayer(s.owner) == null && !MASTERS.containsKey(s.owner))) {
                it.remove();
                // left to fade: it drifts into its master's shadow as essence, if the master is near
                if (ShadowPromotions.soulFaded(owner, level, s) == 0) soulFx(level, s, ShadowFxPayload.Kind.SOUL_GONE, 0, "");
            }
        }
        later.forEach(Runnable::run);
    }

    static void login(ServerPlayer p) {
        MASTERS.put(p.getUUID(), p);
        army(p).clearActive();
        for (ShadowRecord r : army(p).shadows()) ensureForm(p.serverLevel(), r);
        sync(p);
    }

    static void logout(ServerPlayer p) {
        discardAll(p);
        MASTERS.remove(p.getUUID());
        SAID.remove(p.getUUID());
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
        List<UUID> out = new ArrayList<>();
        // the guards stay at their posts (they stand there again when their master is back in that world)
        for (UUID rid : army.active().keySet()) {
            ShadowRecord r = army.get(rid);
            if (r != null && !r.posted()) out.add(rid);
        }
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
                if (r == null || r.isMount() || army(p).isActive(rid) || army(p).used() >= activeLimit(p)) continue;
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
            steed.setName(ShadowNames.STARTER_STEED);
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
        SAID.remove(p.getUUID());
        sync(p);
    }

    /** Puts a shadow straight into the army (staging and tests): it has the shape and role of a fresh one of its kind. */
    public static ShadowRecord give(ServerPlayer p, EntityType<?> type, @Nullable ShadowRank rank, boolean boss) {
        MASTERS.put(p.getUUID(), p);
        ShadowRecord.Role role = ShadowForm.probeRole(p.serverLevel(), type);
        float hp = 20, dmg = 3, speed = 0.25f;
        Entity probe = null;
        try {
            probe = type.create(p.serverLevel());
            if (probe instanceof LivingEntity le) {
                hp = le.getMaxHealth();
                if (le.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) dmg = (float) le.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                if (le.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) speed = (float) le.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
                if (probe instanceof com.beyondsolo.gate.GateBossEntity) {
                    // an old C-rank boss's numbers (see soulSize)
                    hp = 120f * (1 + 0.9f * PlayerRank.C.tier());
                    dmg = 7f + 3.5f * PlayerRank.C.tier();
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // a kind that can't be made on its own: plain numbers
        } finally {
            if (probe != null) probe.discard();
        }
        if (!Float.isFinite(hp) || hp <= 0) hp = 20;
        ShadowRank rk = rank != null ? rank : boss ? ShadowRank.CAPTAIN : ShadowRank.SOLDIER;
        ShadowRecord r = new ShadowRecord(UUID.randomUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(type), role, rk,
                boss ? Math.min(400, hp * 0.5f) : Math.min(200, hp), Math.min(boss ? 30 : 20, dmg), speed, p.level().getGameTime());
        ensureForm(p.serverLevel(), r);
        if (boss) {
            r.setBoss(2);
            r.setName(ShadowNames.boss(p.getRandom()));
        }
        army(p).add(r);
        sync(p);
        return r;
    }

    /**
     * Gives a shadow raised before forms were kept the shape of a fresh one of its kind (a steed's walks). Done as its
     * master comes in and before it is called out.
     */
    static void ensureForm(ServerLevel level, ShadowRecord r) {
        if (r.form() != null) return;
        ShadowForm f = ShadowForm.probe(level, r.entityType());
        r.setForm(r.isMount() ? f.withMove(ShadowForm.Movement.WALK) : f);
    }

    // ================================================================ talking to the client

    public static void sync(ServerPlayer p) {
        ShadowData army = army(p);
        // the looks stay behind: the Shadows tab asks for one when it shows that shadow (sendLook)
        CompoundTag tag = army.save(false);
        ListTag active = new ListTag();
        for (UUID id : army.active().keySet()) active.add(StringTag.valueOf(id.toString()));
        tag.put("active", active);
        tag.putInt("cap", capacity(p));
        tag.putInt("limit", activeLimit(p));
        tag.putBoolean("canRaise", canRaise(p));
        tag.putInt("top", top(p).ordinal());
        tag.putInt("slots", ShadowPromotions.nameSlots(p));
        tag.putLong("time", p.level().getGameTime());
        PacketDistributor.sendToPlayer(p, new ShadowSyncPayload(tag));
    }

    /** How one shadow looks, for its master's Shadows tab. */
    static void sendLook(ServerPlayer p, UUID recordId) {
        ShadowRecord r = army(p).get(recordId);
        if (r == null) return;
        ensureForm(p.serverLevel(), r);
        PacketDistributor.sendToPlayer(p, new com.beyondsolo.network.ShadowLookPayload(recordId.toString(), r.form().look()));
    }

    private static void soulFx(ServerLevel level, Soul s, ShadowFxPayload.Kind kind, int extra, String text) {
        int color = s.boss ? 0xFF4A6A : 0x9B7BE0;
        PacketDistributor.sendToPlayersNear(level, null, s.pos.x, s.pos.y, s.pos.z, 64, new ShadowFxPayload(kind.ordinal(), s.id,
                (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, color, extra, text));
    }

    private static void alert(ServerPlayer p, String title, Component body) {
        tell(p, SystemNotification.Kind.ALERT, title, body);
    }

    /**
     * Sends a pop-up titled {@code title} (a lang key), unless the same one went to this player a moment ago: a key held
     * down or pressed again and again gets one answer, not a stack of them.
     */
    private static void tell(ServerPlayer p, SystemNotification.Kind kind, String title, Component body) {
        int tick = p.getServer() == null ? 0 : p.getServer().getTickCount();
        Said last = SAID.get(p.getUUID());
        if (last != null && last.title().equals(title) && tick - last.at() >= 0 && tick - last.at() < ALERT_QUIET_TICKS) return;
        SAID.put(p.getUUID(), new Said(title, tick, last == null ? 1 : last.count() + 1));
        Notifications.send(p, SystemNotification.of(kind, Component.translatable(title), body));
    }

    /** Tests: the title (a lang key) of the last pop-up this player was sent from here, or null. */
    public static @Nullable String lastSaid(Player p) {
        Said s = SAID.get(p.getUUID());
        return s == null ? null : s.title();
    }

    /** Tests: how many pop-ups this player was sent from here. */
    public static int saidCount(Player p) {
        Said s = SAID.get(p.getUUID());
        return s == null ? 0 : s.count();
    }

    /** Server stopped: forget the souls. */
    static void clearAll() {
        SOULS.clear();
        MASTERS.clear();
        FORCED.clear();
        SAID.clear();
        ShadowForm.forget();
    }

    static ResourceLocation id(String path) {
        return BeyondSolo.id(path);
    }

    private ShadowService() {}
}
