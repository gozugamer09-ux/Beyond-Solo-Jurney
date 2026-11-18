package com.beyondsolo.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.MonarchFxPayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.SkillActions;
import com.beyondsolo.system.Balance;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossFx;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What a monarch's powers leave running in the world ({@link MonarchPowers}): their domains, their crown forms, the
 * marks their dread leaves and the eclipse their army fights under. Server thread only, and nothing here is saved: a
 * domain or a crown form ends when its monarch leaves, falls or changes worlds.
 *
 * <p>A domain follows its monarch. Inside it the monarch and their allies (other hunters, every shadow) hit harder and
 * take less, a Night monarch's shadows mend, foes are worn down once a second by the crown's element, and the domain of
 * a monarch from beyond the gates no longer touches whoever it shelters ({@link #shelters}): the two domains clash.</p>
 */
public final class MonarchDomain {
    private static final ResourceLocation CROWN_SPEED = BeyondSolo.id("monarch/crown_speed");
    private static final ResourceLocation CROWN_STEADY = BeyondSolo.id("monarch/crown_steady");
    /** What a Night monarch's domain mends of their shadows' health each second. */
    static final float SHADOW_MEND = 0.02f;
    /** How much harder shadows fight inside their master's domain. */
    static final float SHADOW_MIGHT = 1.25f;

    /** A domain laid down. */
    private static final class Reign {
        final ServerPlayer owner;
        final Dominion dominion;
        final ServerLevel level;
        final int radius;
        final int skillLevel;
        final long until;
        int ticks;
        /** The monarchs from beyond the gates it has already clashed with. */
        final Set<Integer> clashed = new HashSet<>();

        Reign(ServerPlayer owner, Dominion dominion, int radius, int skillLevel, long until) {
            this.owner = owner;
            this.dominion = dominion;
            this.level = owner.serverLevel();
            this.radius = radius;
            this.skillLevel = skillLevel;
            this.until = until;
        }

        boolean holds(Entity e) {
            return e.level() == level && e.position().distanceToSqr(owner.position()) <= (double) radius * radius;
        }
    }

    /** A crown form taken. */
    private record Form(Dominion dominion, long until, int skillLevel) {}

    private record Dread(long until, float bonus) {}

    private static final Map<UUID, Reign> DOMAINS = new HashMap<>();
    private static final Map<UUID, Form> FORMS = new HashMap<>();
    private static final Map<UUID, Long> ECLIPSES = new HashMap<>();
    private static final Map<LivingEntity, Dread> DREADS = new WeakHashMap<>();

    // ================================================================ laying them down

    /** Lays down a monarch's domain round them for {@code ticks} (a new one replaces the old). */
    public static void open(ServerPlayer p, Dominion d, int ticks, int skillLevel) {
        int radius = MonarchPowers.radius();
        DOMAINS.put(p.getUUID(), new Reign(p, d, radius, skillLevel, p.level().getGameTime() + ticks));
        fx(p, MonarchFxPayload.DOMAIN, d.color(), radius, ticks);
    }

    /** Takes a monarch's crown form for {@code ticks}. */
    public static void crown(ServerPlayer p, Dominion d, int ticks, int skillLevel) {
        FORMS.put(p.getUUID(), new Form(d, p.level().getGameTime() + ticks, skillLevel));
        modifier(p.getAttribute(Attributes.MOVEMENT_SPEED), CROWN_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), CROWN_STEADY, 0.6, AttributeModifier.Operation.ADD_VALUE);
        fx(p, MonarchFxPayload.CROWN, d.color(), d.ordinal(), ticks);
    }

    /** A Night monarch's army fights harder for a while (their authority). */
    public static void eclipse(ServerPlayer p, int ticks) {
        ECLIPSES.put(p.getUUID(), p.level().getGameTime() + ticks);
    }

    /** A creature caught by a monarch's dread takes {@code bonus} percent more from every blow until {@code until}. */
    public static void dread(LivingEntity e, long until, float bonus) {
        Dread old = DREADS.get(e);
        if (old == null || old.until < until || old.bonus < bonus) DREADS.put(e, new Dread(until, Math.max(bonus, old == null ? 0 : old.bonus)));
    }

    /** Ends the monarch's domain, crown form and eclipse (the crown taken back, logging out, falling, another world). */
    public static void end(ServerPlayer p) {
        Reign r = DOMAINS.remove(p.getUUID());
        if (r != null) fx(p, MonarchFxPayload.DOMAIN, r.dominion.color(), r.radius, 0);
        endCrown(p);
        ECLIPSES.remove(p.getUUID());
    }

    private static void endCrown(ServerPlayer p) {
        Form f = FORMS.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CROWN_SPEED);
        AttributeInstance steady = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (steady != null) steady.removeModifier(CROWN_STEADY);
        if (f != null) fx(p, MonarchFxPayload.CROWN, f.dominion.color(), f.dominion.ordinal(), 0);
    }

    // ================================================================ reading them

    public static boolean hasDomain(Player p) {
        return DOMAINS.containsKey(p.getUUID());
    }

    public static boolean inCrown(Player p) {
        return FORMS.containsKey(p.getUUID());
    }

    /** Whether something stands sheltered in a monarch's domain: the monarch, another hunter or a shadow inside it. */
    public static boolean shelters(LivingEntity e) {
        if (DOMAINS.isEmpty() || !(e instanceof Player || e instanceof ShadowEntity)) return false;
        for (Reign r : DOMAINS.values()) if (r.holds(e)) return true;
        return false;
    }

    /** How much harder a hunter hits: inside a monarch's domain, and in their own crown form. */
    public static float might(ServerPlayer p) {
        float m = 1;
        for (Reign r : DOMAINS.values()) {
            if (r.holds(p)) m = Math.max(m, 1 + MonarchPowers.domainMight(r.skillLevel) / 100f);
        }
        Form f = FORMS.get(p.getUUID());
        if (f != null) m *= 1 + MonarchPowers.crownMight(f.skillLevel) / 100f;
        return m;
    }

    /** What a blow on a hunter or a shadow is worth after a domain's shelter and a crown form's guard (1 = untouched). */
    public static float guard(LivingEntity e) {
        float g = 1;
        if (shelters(e)) g *= 1 - MonarchPowers.DOMAIN_GUARD / 100f;
        Form f = FORMS.get(e.getUUID());
        if (f != null && e instanceof Player) g *= 1 - MonarchPowers.crownGuard(f.skillLevel) / 100f;
        return g;
    }

    /** What a blow on a creature caught by dread is worth (1 = untouched). */
    public static float dreadBonus(LivingEntity e) {
        if (DREADS.isEmpty()) return 1;
        Dread d = DREADS.get(e);
        if (d == null) return 1;
        if (d.until < e.level().getGameTime()) {
            DREADS.remove(e);
            return 1;
        }
        return 1 + d.bonus / 100f;
    }

    /** The crown a monarch's blows carry while in crown form, or null. */
    public static @Nullable Dominion formOf(Player p) {
        Form f = FORMS.get(p.getUUID());
        return f == null ? null : f.dominion;
    }

    /** What a shadow's blows are worth inside its master's domain and under their eclipse (1 = nothing). */
    public static float shadowBonus(ShadowEntity shadow) {
        UUID owner = shadow.getOwnerUUID();
        if (owner == null) return 1;
        float b = 1;
        Long eclipse = ECLIPSES.get(owner);
        if (eclipse != null && eclipse >= shadow.level().getGameTime()) b = 1 + MonarchPowers.ECLIPSE_MIGHT / 100f;
        Reign r = DOMAINS.get(owner);
        if (r != null && r.holds(shadow)) b = Math.max(b, SHADOW_MIGHT);
        return b;
    }

    /** Ticks left of a monarch's domain (0: none). Tests, the HUD. */
    public static int domainLeft(Player p) {
        Reign r = DOMAINS.get(p.getUUID());
        return r == null ? 0 : (int) Math.max(0, r.until - p.level().getGameTime());
    }

    // ================================================================ ticking

    static void tick(MinecraftServer server) {
        if (!DOMAINS.isEmpty()) {
            Iterator<Reign> it = DOMAINS.values().iterator();
            List<Reign> ended = new ArrayList<>();
            while (it.hasNext()) {
                Reign r = it.next();
                ServerPlayer p = r.owner;
                if (p.isRemoved() || p.hasDisconnected() || !p.isAlive() || p.level() != r.level || r.level.getGameTime() >= r.until
                        || !MonarchAPI.crowned(p)) {
                    it.remove();
                    ended.add(r);
                    continue;
                }
                r.ticks++;
                if (r.ticks % 20 == 0) second(r);
                if (r.ticks % 5 == 0) ambient(r);
            }
            for (Reign r : ended) fx(r.owner, MonarchFxPayload.DOMAIN, r.dominion.color(), r.radius, 0);
        }
        if (!FORMS.isEmpty()) {
            List<ServerPlayer> over = new ArrayList<>();
            for (Map.Entry<UUID, Form> en : FORMS.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null) continue;
                if (!p.isAlive() || p.level().getGameTime() >= en.getValue().until || !MonarchAPI.crowned(p)) over.add(p);
                else if (p.tickCount % 10 == 0) {
                    p.serverLevel().sendParticles(com.beyondsolo.monarch.ThroneTrial.dust(en.getValue().dominion.color(), 1.0f),
                            p.getX(), p.getY() + p.getBbHeight() + 0.4, p.getZ(), 2, 0.25, 0.1, 0.25, 0);
                }
            }
            for (ServerPlayer p : over) endCrown(p);
            // a form whose monarch is gone is forgotten (their modifiers went with them)
            FORMS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        if (!ECLIPSES.isEmpty() && server.getTickCount() % 100 == 0) {
            long now = server.overworld().getGameTime();
            ECLIPSES.values().removeIf(until -> until < now - 20);
        }
    }

    /** Once a second: the foes inside are worn down, a Night monarch's shadows mend, and other monarchs' domains are met. */
    private static void second(Reign r) {
        ServerPlayer p = r.owner;
        int s = r.ticks / 20;
        AABB box = p.getBoundingBox().inflate(r.radius);
        for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, box, e -> r.holds(e) && foe(p, e))) {
            MonarchPowers.domainPulse(p, r.dominion, r.skillLevel, e, s);
        }
        if (r.dominion == Dominion.NIGHT) {
            for (ShadowEntity sh : r.level.getEntitiesOfClass(ShadowEntity.class, box, sh -> sh.isAlive() && r.holds(sh)
                    && p.getUUID().equals(sh.getOwnerUUID()) && sh.getHealth() < sh.getMaxHealth())) {
                Balance.healRaw(sh, sh.getMaxHealth() * SHADOW_MEND);
            }
        }
        // the monarchs from beyond the gates whose domains reach into this one
        for (MonarchBoss boss : r.level.getEntitiesOfClass(MonarchBoss.class, p.getBoundingBox().inflate(r.radius + MonarchBoss.DOMAIN),
                b -> b.isAlive() && b.landed() && !b.leaving())) {
            if (boss.distanceTo(p) <= r.radius + MonarchBoss.DOMAIN && r.clashed.add(boss.getId())) clash(r, boss);
        }
    }

    /** Two domains meet: where they touch, the light of both, and the monarch who holds theirs is remembered for it. */
    private static void clash(Reign r, MonarchBoss boss) {
        ServerPlayer p = r.owner;
        Vec3 mid = p.position().add(boss.position()).scale(0.5);
        int c = r.dominion.color();
        WorldBossFx.ring(r.level, mid, 0.5f, 9f, 10, c);
        WorldBossFx.burst(r.level, mid.add(0, 2, 0), 5f, c);
        r.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5f, 0.5f);
        r.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
        PacketDistributor.sendToPlayersNear(r.level, null, mid.x, mid.y, mid.z, 96,
                new MonarchFxPayload(MonarchFxPayload.CLASH, p.getId(), c, boss.getId(), 70));
        MonarchAPI.clashed(p);
    }

    /** A few motes on the domain's edge, and its crown's weather inside it. */
    private static void ambient(Reign r) {
        ServerPlayer p = r.owner;
        var rnd = r.level.random;
        int c = r.dominion.color();
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            r.level.sendParticles(ThroneTrial.dust(c, 1.2f), p.getX() + Math.cos(a) * r.radius, p.getY() + 0.3 + rnd.nextDouble() * 1.5,
                    p.getZ() + Math.sin(a) * r.radius, 1, 0, 0.05, 0, 0);
        }
        ParticleOptions weather = switch (r.dominion) {
            case NIGHT -> ParticleTypes.SCULK_SOUL;
            case WINTER -> ParticleTypes.SNOWFLAKE;
            case CINDERS -> ParticleTypes.SMALL_FLAME;
            case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
        };
        double spread = r.radius * 0.45;
        r.level.sendParticles(weather, p.getX(), p.getY() + 1.5, p.getZ(), 4, spread, 1.2, spread, 0.01);
    }

    /** A creature the domain wears down: one that is hostile, or already fighting a hunter or a shadow. */
    static boolean foe(ServerPlayer p, LivingEntity e) {
        if (!SkillActions.canHit(p, e) || e instanceof ShadowEntity) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && (m.getTarget() instanceof Player || m.getTarget() instanceof ShadowEntity);
    }

    // ================================================================ talking to the client

    /** Sends a monarch's moment to everyone who can see them, and to them. */
    public static void fx(ServerPlayer p, int kind, int color, int size, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new MonarchFxPayload(kind, p.getId(), color & 0xFFFFFF, size, ticks));
    }

    /** An authority: named on the screen of everyone near. */
    public static void announce(ServerPlayer p, Dominion d, int ticks) {
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, p.getX(), p.getY(), p.getZ(), 64,
                new MonarchFxPayload(MonarchFxPayload.AUTHORITY, p.getId(), d.color(), d.ordinal(), ticks));
    }

    /** A hunter came into sight of a monarch: what they have running, so it is drawn for them too. */
    static void showTo(ServerPlayer viewer, ServerPlayer monarch) {
        long now = monarch.level().getGameTime();
        Reign r = DOMAINS.get(monarch.getUUID());
        if (r != null) PacketDistributor.sendToPlayer(viewer, new MonarchFxPayload(MonarchFxPayload.DOMAIN, monarch.getId(),
                r.dominion.color(), r.radius, (int) Math.max(1, r.until - now)));
        Form f = FORMS.get(monarch.getUUID());
        if (f != null) PacketDistributor.sendToPlayer(viewer, new MonarchFxPayload(MonarchFxPayload.CROWN, monarch.getId(),
                f.dominion.color(), f.dominion.ordinal(), (int) Math.max(1, f.until - now)));
    }

    static void clear() {
        DOMAINS.clear();
        FORMS.clear();
        ECLIPSES.clear();
        DREADS.clear();
    }

    private static void modifier(@Nullable AttributeInstance inst, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private MonarchDomain() {}
}
