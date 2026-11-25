package com.beyondsolo.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.SkillActions;
import com.beyondsolo.skill.SkillParticles;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * What a ruler of light's powers leave running in the world ({@link RulerPowers}): their sanctums, their seraph forms
 * and the marks their judgement leaves. Server thread only, and nothing here is saved: a sanctum or a seraph form ends
 * when its ruler leaves, falls or changes worlds.
 *
 * <p>A sanctum follows its ruler. Inside it the ruler and every other hunter (and their shadows) hit harder, take less
 * and mend, foes burn in the light and suffer the mantle's law, and the domain of a monarch from beyond the gates no
 * longer touches whoever it shelters ({@link #shelters}): the light and the monarch's rule clash.</p>
 */
public final class LightSanctum {
    private static final ResourceLocation SERAPH_SPEED = BeyondSolo.id("light/seraph_speed");
    private static final ResourceLocation SERAPH_STEADY = BeyondSolo.id("light/seraph_steady");

    /** A sanctum raised. */
    private static final class Hold {
        final ServerPlayer owner;
        final Mantle mantle;
        final ServerLevel level;
        final int radius;
        final int skillLevel;
        final long until;
        int ticks;
        /** The monarchs from beyond the gates it has already clashed with. */
        final Set<Integer> clashed = new HashSet<>();

        Hold(ServerPlayer owner, Mantle mantle, int radius, int skillLevel, long until) {
            this.owner = owner;
            this.mantle = mantle;
            this.level = owner.serverLevel();
            this.radius = radius;
            this.skillLevel = skillLevel;
            this.until = until;
        }

        boolean holds(Entity e) {
            return e.level() == level && e.position().distanceToSqr(owner.position()) <= (double) radius * radius;
        }

        /**
         * Whether it stands with {@code e}: its ruler, the hunters of their party, and their shadows. A foe who walks
         * in (another hunter in a fight between hunters, their shadows) is held by it, never sheltered or made mightier.
         */
        boolean befriends(Entity e) {
            UUID id = e instanceof ShadowEntity sh ? sh.getOwnerUUID() : e instanceof Player pl ? pl.getUUID() : null;
            if (id == null) return false;
            return id.equals(owner.getUUID()) || owner.getServer() != null && com.beyondsolo.party.Parties.together(owner.getServer(), owner.getUUID(), id);
        }
    }

    /** A seraph form taken. */
    private record Form(int color, long until, int skillLevel) {}

    private record Mark(long until, float bonus) {}

    private static final Map<UUID, Hold> SANCTUMS = new HashMap<>();
    private static final Map<UUID, Form> SERAPHS = new HashMap<>();
    private static final Map<LivingEntity, Mark> JUDGED = new WeakHashMap<>();

    // ================================================================ raising them

    /** Raises a ruler's sanctum round them for {@code ticks} (a new one replaces the old). */
    public static void open(ServerPlayer p, Mantle m, int ticks, int skillLevel) {
        int radius = RulerPowers.radius();
        SANCTUMS.put(p.getUUID(), new Hold(p, m, radius, skillLevel, p.level().getGameTime() + ticks));
        fx(p, LightFxPayload.SANCTUM, m.color(), radius, ticks);
    }

    /** Takes the seraph form for {@code ticks}: wings, a halo, and the light in their blows. */
    public static void seraph(ServerPlayer p, int color, int ticks, int skillLevel) {
        SERAPHS.put(p.getUUID(), new Form(color, p.level().getGameTime() + ticks, skillLevel));
        modifier(p.getAttribute(Attributes.MOVEMENT_SPEED), SERAPH_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), SERAPH_STEADY, 0.5, AttributeModifier.Operation.ADD_VALUE);
        fx(p, LightFxPayload.SERAPH, color, 0, ticks);
    }

    /** A creature judged by the light takes {@code bonus} percent more from every blow until {@code until}. */
    public static void judge(LivingEntity e, long until, float bonus) {
        Mark old = JUDGED.get(e);
        long now = e.level().getGameTime();
        // a stronger mark takes over; a weaker one waits for the stronger to lapse; the same one is drawn out
        if (old == null || old.until < now || bonus > old.bonus || bonus == old.bonus && until > old.until) JUDGED.put(e, new Mark(until, bonus));
    }

    /** Ends the ruler's sanctum and seraph form (the light taken back, logging out, falling, another world). */
    public static void end(ServerPlayer p) {
        Hold h = SANCTUMS.remove(p.getUUID());
        if (h != null) fx(p, LightFxPayload.SANCTUM, h.mantle.color(), h.radius, 0);
        endSeraph(p);
    }

    private static void endSeraph(ServerPlayer p) {
        Form f = SERAPHS.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SERAPH_SPEED);
        AttributeInstance steady = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (steady != null) steady.removeModifier(SERAPH_STEADY);
        if (f != null) fx(p, LightFxPayload.SERAPH, f.color, 0, 0);
    }

    // ================================================================ reading them

    public static boolean hasSanctum(Player p) {
        return SANCTUMS.containsKey(p.getUUID());
    }

    public static boolean inSeraph(Player p) {
        return SERAPHS.containsKey(p.getUUID());
    }

    /** Whether something stands sheltered in a ruler's sanctum: the ruler, their party or their shadows inside it. */
    public static boolean shelters(LivingEntity e) {
        if (SANCTUMS.isEmpty() || !(e instanceof Player || e instanceof ShadowEntity)) return false;
        for (Hold h : SANCTUMS.values()) if (h.holds(e) && h.befriends(e)) return true;
        return false;
    }

    /** How much harder a hunter hits: inside a ruler's sanctum, and in their own seraph form. */
    public static float might(ServerPlayer p) {
        float m = 1;
        for (Hold h : SANCTUMS.values()) {
            if (h.holds(p) && h.befriends(p)) m = Math.max(m, 1 + RulerPowers.sanctumMight(h.skillLevel) / 100f);
        }
        Form f = SERAPHS.get(p.getUUID());
        if (f != null) m *= 1 + RulerPowers.seraphMight(f.skillLevel) / 100f;
        return m;
    }

    /** What a blow on a hunter or a shadow is worth after a sanctum's shelter and a seraph form's guard (1 = untouched). */
    public static float guard(LivingEntity e) {
        float g = 1;
        if (shelters(e)) g *= 1 - RulerPowers.SANCTUM_GUARD / 100f;
        Form f = SERAPHS.get(e.getUUID());
        if (f != null && e instanceof Player) g *= 1 - RulerPowers.seraphGuard(f.skillLevel) / 100f;
        return g;
    }

    /** What a blow on a creature the light has judged is worth (1 = untouched). */
    public static float judgedBonus(LivingEntity e) {
        if (JUDGED.isEmpty()) return 1;
        Mark m = JUDGED.get(e);
        if (m == null) return 1;
        if (m.until < e.level().getGameTime()) {
            JUDGED.remove(e);
            return 1;
        }
        return 1 + m.bonus / 100f;
    }

    /** Whether a creature carries the light's judgement now. */
    public static boolean judged(LivingEntity e) {
        return judgedBonus(e) > 1;
    }

    /** The colour of a ruler's seraph form while they are in it, or -1. */
    public static int seraphColor(Player p) {
        Form f = SERAPHS.get(p.getUUID());
        return f == null ? -1 : f.color;
    }

    /** What a shadow's blows are worth inside a sanctum (1 = nothing): the light stands with its ruler's and their party's. */
    public static float shadowBonus(ShadowEntity shadow) {
        if (SANCTUMS.isEmpty()) return 1;
        float b = 1;
        for (Hold h : SANCTUMS.values()) {
            if (h.holds(shadow) && h.befriends(shadow)) b = Math.max(b, 1 + RulerPowers.sanctumMight(h.skillLevel) / 200f);
        }
        return b;
    }

    /** Ticks left of a ruler's sanctum (0: none). Tests, the HUD. */
    public static int sanctumLeft(Player p) {
        Hold h = SANCTUMS.get(p.getUUID());
        return h == null ? 0 : (int) Math.max(0, h.until - p.level().getGameTime());
    }

    // ================================================================ ticking

    static void tick(MinecraftServer server) {
        if (!SANCTUMS.isEmpty()) {
            Iterator<Hold> it = SANCTUMS.values().iterator();
            List<Hold> ended = new ArrayList<>();
            while (it.hasNext()) {
                Hold h = it.next();
                ServerPlayer p = h.owner;
                if (p.isRemoved() || p.hasDisconnected() || !p.isAlive() || p.level() != h.level || h.level.getGameTime() >= h.until
                        || !LightAPI.invested(p)) {
                    it.remove();
                    ended.add(h);
                    continue;
                }
                h.ticks++;
                if (h.ticks % 20 == 0) second(h);
                if (h.ticks % 5 == 0) ambient(h);
            }
            for (Hold h : ended) fx(h.owner, LightFxPayload.SANCTUM, h.mantle.color(), h.radius, 0);
        }
        if (!SERAPHS.isEmpty()) {
            List<ServerPlayer> over = new ArrayList<>();
            for (Map.Entry<UUID, Form> en : SERAPHS.entrySet()) {
                ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(server, en.getKey());
                if (p == null) continue;
                Form f = en.getValue();
                if (!p.isAlive() || p.level().getGameTime() >= f.until || !LightAPI.sworn(p)) over.add(p);
                else {
                    // wings hold them up: they fall like a feather
                    if (!p.onGround() && p.getDeltaMovement().y < -0.08) {
                        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15, 0, false, false, false));
                    }
                    if (p.tickCount % 8 == 0) {
                        SkillParticles.send(p.serverLevel(), ThroneTrial.dust(f.color, 1.0f), p.getX(), p.getY() + p.getBbHeight() + 0.5, p.getZ(),
                                2, 0.25, 0.05, 0.25, 0);
                        SkillParticles.send(p.serverLevel(), ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.6, 0.4, 0.6, 0.01);
                    }
                }
            }
            for (ServerPlayer p : over) endSeraph(p);
            SERAPHS.keySet().removeIf(id -> com.beyondsolo.util.OnlinePlayers.get(server, id) == null);
        }
    }

    /** Once a second: the foes inside burn, the hunters inside mend, the mantle's law holds, monarchs' domains are met. */
    private static void second(Hold h) {
        ServerPlayer p = h.owner;
        int s = h.ticks / 20;
        AABB box = p.getBoundingBox().inflate(h.radius);
        for (LivingEntity e : h.level.getEntitiesOfClass(LivingEntity.class, box, e -> h.holds(e) && foe(p, e))) {
            RulerPowers.sanctumPulse(p, h.mantle, h.skillLevel, e, s);
        }
        float mend = h.mantle == Mantle.DAWN ? RulerPowers.SANCTUM_MEND * 2 : RulerPowers.SANCTUM_MEND;
        for (LivingEntity ally : h.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && h.holds(e) && h.befriends(e)
                && (e instanceof Player pl && !pl.isSpectator() || e instanceof ShadowEntity))) {
            Balance.regen(ally, ally.getMaxHealth() * mend);
            if (h.mantle == Mantle.WINGS && ally instanceof Player) {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false, true));
                ally.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            }
            if (h.mantle == Mantle.DAWN && ally instanceof Player && s % 2 == 0) cleanse(ally);
        }
        // the monarchs from beyond the gates whose domains reach into this sanctum
        for (MonarchBoss boss : h.level.getEntitiesOfClass(MonarchBoss.class, p.getBoundingBox().inflate(h.radius + MonarchBoss.DOMAIN),
                b -> b.isAlive() && b.landed() && !b.leaving())) {
            if (boss.distanceTo(p) <= h.radius + MonarchBoss.DOMAIN && h.clashed.add(boss.getId())) clash(h, boss);
        }
    }

    /** Dawn's sanctum takes one harmful effect off a hunter. */
    private static void cleanse(LivingEntity e) {
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (!inst.getEffect().value().isBeneficial()) {
                e.removeEffect(inst.getEffect());
                return;
            }
        }
    }

    /** The light meets a monarch's rule: where they touch, the light of both, and the ruler is remembered for it. */
    private static void clash(Hold h, MonarchBoss boss) {
        ServerPlayer p = h.owner;
        Vec3 mid = p.position().add(boss.position()).scale(0.5);
        int c = h.mantle.color();
        WorldBossFx.ring(h.level, mid, 0.5f, 9f, 10, c);
        WorldBossFx.burst(h.level, mid.add(0, 2, 0), 5f, c);
        h.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.5f);
        h.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2f, 0.9f);
        PacketDistributor.sendToPlayersNear(h.level, null, mid.x, mid.y, mid.z, 96,
                new LightFxPayload(LightFxPayload.CLASH, p.getId(), c, boss.getId(), 70));
        LightAPI.clashed(p);
    }

    /** Motes on the sanctum's edge, and its mantle's light inside it. */
    private static void ambient(Hold h) {
        ServerPlayer p = h.owner;
        var rnd = h.level.random;
        int c = h.mantle.color();
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            SkillParticles.send(h.level, ThroneTrial.dust(c, 1.2f), p.getX() + Math.cos(a) * h.radius, p.getY() + 0.3 + rnd.nextDouble() * 1.5,
                    p.getZ() + Math.sin(a) * h.radius, 1, 0, 0.05, 0, 0);
        }
        ParticleOptions weather = switch (h.mantle) {
            case VERDICT -> ParticleTypes.END_ROD;
            case WINGS -> ParticleTypes.WHITE_ASH;
            case HAND -> ParticleTypes.ENCHANT;
            case DAWN -> ParticleTypes.WAX_ON;
        };
        double spread = h.radius * 0.45;
        SkillParticles.send(h.level, weather, p.getX(), p.getY() + 1.5, p.getZ(), 4, spread, 1.2, spread, 0.01);
    }

    /** A creature the sanctum burns: one that is hostile, or already fighting a hunter or a shadow. */
    static boolean foe(ServerPlayer p, LivingEntity e) {
        if (!SkillActions.canHit(p, e) || e instanceof ShadowEntity) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && (m.getTarget() instanceof Player || m.getTarget() instanceof ShadowEntity);
    }

    // ================================================================ talking to the client

    /** Sends a ruler's moment to everyone who can see them, and to them. */
    public static void fx(ServerPlayer p, int kind, int color, int size, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new LightFxPayload(kind, p.getId(), color & 0xFFFFFF, size, ticks));
    }

    /** An authority: named on the screen of everyone near. */
    public static void announce(ServerPlayer p, Mantle m, int ticks) {
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, p.getX(), p.getY(), p.getZ(), 64,
                new LightFxPayload(LightFxPayload.AUTHORITY, p.getId(), m.color(), m.ordinal(), ticks));
    }

    /** A hunter came into sight of a ruler: what they have running, so it is drawn for them too. */
    static void showTo(ServerPlayer viewer, ServerPlayer ruler) {
        long now = ruler.level().getGameTime();
        Hold h = SANCTUMS.get(ruler.getUUID());
        if (h != null) PacketDistributor.sendToPlayer(viewer, new LightFxPayload(LightFxPayload.SANCTUM, ruler.getId(),
                h.mantle.color(), h.radius, (int) Math.max(1, h.until - now)));
        Form f = SERAPHS.get(ruler.getUUID());
        if (f != null) PacketDistributor.sendToPlayer(viewer, new LightFxPayload(LightFxPayload.SERAPH, ruler.getId(),
                f.color, 0, (int) Math.max(1, f.until - now)));
    }

    static void clear() {
        SANCTUMS.clear();
        SERAPHS.clear();
        JUDGED.clear();
    }

    private static void modifier(@Nullable AttributeInstance inst, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private LightSanctum() {}
}
