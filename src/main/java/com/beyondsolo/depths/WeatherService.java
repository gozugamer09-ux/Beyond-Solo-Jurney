package com.beyondsolo.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.network.DepthsFxPayload;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a cave's weather does ({@link GateWeather}). Skill and infusion hits of the favoured element are stronger and
 * the dulled one weaker ({@link #factor}). The hazards, every few seconds for each cave with a party in it: searing
 * heat builds up burn on anyone out of water, the frost gale chills (slow, frost creeping in), spores build up venom,
 * the mana storm brings bolts down on marked spots (creatures caught in one carry lightning after), and tremors shake
 * the cave and drop rocks on marked spots. In a crimson haze the cave's creatures hit harder and heal off their hits
 * ({@link DepthsHooks}); in the darkness they are quicker. The clear pays {@link GateWeather#reward} times more.
 */
public final class WeatherService {
    private static final ResourceLocation DARK_SPEED = BeyondSolo.id("weather_darkness");
    /** Seconds between hazards, by weather (in half-second beats). */
    private static final int HEAT_BEATS = 16, CHILL_BEATS = 22, SPORE_BEATS = 14, BOLT_BEATS = 9, QUAKE_BEATS = 18, DARK_BEATS = 4;
    public static final int BOLT_WARNING = 26, ROCK_WARNING = 24;

    /** The weather of the cave {@code pos} is in (clear anywhere else). */
    public static GateWeather weatherAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel s) || !GateService.isDungeon(s)) return GateWeather.CLEAR;
        GateInstance g = GateData.get(s.getServer()).bySlotPos(pos);
        return g == null ? GateWeather.CLEAR : g.depths().weather(g);
    }

    /** How much a skill or infusion hit of {@code element} on {@code target} does, for the weather where it stands. */
    public static float factor(LivingEntity target, @Nullable SkillElement element) {
        if (element == null || element == SkillElement.PHYSICAL || target.level().isClientSide) return 1f;
        return weatherAt(target.level(), target.blockPosition()).factor(element);
    }

    /** The clear's reward multiplier for a gate's weather. */
    public static float rewardBonus(GateInstance g) {
        return g.depths().weather(g).reward;
    }

    /** Every half second, for a cave with a party in it. {@code tick} is the server's tick count. */
    static void tick(ServerLevel level, GateInstance g, List<ServerPlayer> players, long tick) {
        if (players.isEmpty()) return;
        GateWeather w = g.depths().weather(g);
        int beat = (int) (tick / 10) + g.slot() * 7;
        int tier = g.rank().tier();
        switch (w) {
            case SCORCHING -> {
                if (beat % HEAT_BEATS != 0) return;
                for (ServerPlayer p : players) {
                    if (exempt(p) || p.isInWater()) continue;
                    Statuses.apply(p, DamageElement.FIRE, 8f + 2f * tier, null);
                    toOthers(level, p, ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.35, 0.1, 0.35, 0.01);
                }
            }
            case BLIZZARD -> {
                if (beat % CHILL_BEATS != 0) return;
                for (ServerPlayer p : players) {
                    if (exempt(p)) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 0, true, false, true));
                    // frost creeps in at the edge of the screen, never far enough to freeze
                    p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() - 2, p.getTicksFrozen() + 90));
                    toOthers(level, p, ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
                }
            }
            case MIASMA -> {
                if (beat % SPORE_BEATS != 0) return;
                for (ServerPlayer p : players) {
                    if (exempt(p)) continue;
                    Statuses.apply(p, DamageElement.POISON, 6f + 2f * tier, null);
                    toOthers(level, p, ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1.6, p.getZ(), 8, 0.6, 0.4, 0.6, 0);
                }
            }
            case STORM -> {
                if (beat % BOLT_BEATS != 0) return;
                ServerPlayer target = players.get(level.random.nextInt(players.size()));
                if (exempt(target)) return;
                Vec3 at = spotNear(level, target.position(), 4.5);
                if (at != null) bolt(level, g, at, 2.2f, 4f + 2f * tier);
            }
            case TREMORS -> {
                if (beat % QUAKE_BEATS != 0) return;
                quake(level, g, players, tier);
            }
            case DARKNESS -> {
                if (beat % DARK_BEATS != 0) return;
                quicken(level, g, players);
            }
            default -> {
            }
        }
    }

    /**
     * Particles on a player, shown to everyone but that player: their own view gets the weather's air from the client
     * (no vanilla particles right in front of a first-person camera).
     */
    private static void toOthers(ServerLevel level, ServerPlayer on, ParticleOptions type, double x, double y, double z,
                                 int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer other : level.players()) {
            if (other != on) level.sendParticles(other, type, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** A moment of the weather in the world, for everyone within 64 blocks. */
    private static void fx(ServerLevel level, Vec3 at, DepthsFxPayload payload) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(64))) PacketDistributor.sendToPlayer(p, payload);
    }

    private static boolean exempt(Player p) {
        return p.isCreative() || p.isSpectator() || GateService.inTransit(p);
    }

    /** A bolt of the mana storm: marked on the floor, then down it comes. Public for commands and previews. */
    public static void bolt(ServerLevel level, GateInstance g, Vec3 at, float radius, float damage) {
        int color = GateWeather.STORM.color;
        EnemyFx.telegraphCircle(level, at, radius, BOLT_WARNING, color);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.5f, 1.9f);
        ServerScheduler.schedule(BOLT_WARNING, () -> {
            fx(level, at, new DepthsFxPayload(DepthsFxPayload.BOLT, (float) at.x, (float) at.y, (float) at.z, radius, 0, color));
            level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.4f, 1.3f);
            level.playSound(null, BlockPos.containing(at), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.7f, 1.6f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 3, radius + 1),
                    e -> e.isAlive() && horizontal(e.position(), at) <= radius + e.getBbWidth() / 2)) {
                if (e instanceof Player p && exempt(p)) continue;
                e.invulnerableTime = 0;
                e.hurt(level.damageSources().lightningBolt(), damage);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
                // a creature the storm has struck carries lightning: water on it now makes a surge
                if (!(e instanceof Player) && e.isAlive()) SkillCombat.setAura(e, SkillElement.LIGHTNING);
            }
        });
    }

    /** A quake: the cave shakes and rocks come down on marked spots around each of the party. */
    static void quake(ServerLevel level, GateInstance g, List<ServerPlayer> players, int tier) {
        level.playSound(null, players.get(0).blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.AMBIENT, 1.2f, 0.5f);
        for (ServerPlayer p : players) {
            GateService.shake(level, p, 1.1f, 3);
            if (exempt(p)) continue;
            int rocks = 1 + (tier >= 3 ? 1 : 0);
            for (int i = 0; i < rocks; i++) {
                Vec3 at = spotNear(level, p.position(), i == 0 ? 1.6 : 5);
                if (at != null) rock(level, g, at, 1.7f, 4f + 2f * tier);
            }
        }
    }

    /** One falling rock: dust trickles from the ceiling over a marked spot, then it lands. Public for commands. */
    public static void rock(ServerLevel level, GateInstance g, Vec3 at, float radius, float damage) {
        BlockState stone = g.realm().mainWall();
        EnemyFx.telegraphCircle(level, at, radius, ROCK_WARNING, GateWeather.TREMORS.color);
        double ceiling = ceiling(level, at);
        int id = Block.getId(stone);
        fx(level, at, new DepthsFxPayload(DepthsFxPayload.ROCK_WARN, (float) at.x, (float) ceiling - 0.2f, (float) at.z, radius, 0, id));
        ServerScheduler.schedule(ROCK_WARNING, () -> {
            BlockPos p = BlockPos.containing(at);
            fx(level, at, new DepthsFxPayload(DepthsFxPayload.ROCK, (float) at.x, (float) at.y, (float) at.z, radius, 0, id));
            level.playSound(null, p, stone.getSoundType().getBreakSound(), SoundSource.HOSTILE, 1.0f, 0.8f);
            level.playSound(null, p, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            level.playSound(null, p, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.35f, 1.6f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 3, radius + 1),
                    e -> e.isAlive() && horizontal(e.position(), at) <= radius + e.getBbWidth() / 2)) {
                if (e instanceof Player pl && exempt(pl)) continue;
                e.invulnerableTime = 0;
                e.hurt(level.damageSources().generic(), damage);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, true));
            }
        });
    }

    /** In the darkness the cave's creatures near the party are quicker. */
    private static void quicken(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(28),
                    m -> m.isAlive() && m.getPersistentData().hasUUID(GateWiper.GATE_ID_TAG))) {
                AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null && !speed.hasModifier(DARK_SPEED)) {
                    speed.addTransientModifier(new AttributeModifier(DARK_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
            }
        }
    }

    // ------------------------------------------------------------------ places

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A spot of open floor within {@code spread} of {@code near}, or null. */
    @Nullable
    static Vec3 spotNear(ServerLevel level, Vec3 near, double spread) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = level.random.nextDouble() * spread;
            BlockPos column = BlockPos.containing(near.x + Math.cos(a) * d, near.y + 2, near.z + Math.sin(a) * d);
            for (int dy = 0; dy < 7; dy++) {
                BlockPos q = column.below(dy);
                if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) {
                    return new Vec3(near.x + Math.cos(a) * d, q.getY(), near.z + Math.sin(a) * d);
                }
            }
        }
        return null;
    }

    /** The underside of the rock above a spot (up to 16 blocks). */
    static double ceiling(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 1; i < 16; i++) {
            if (!level.getBlockState(p.above(i)).isAir()) return p.getY() + i;
        }
        return at.y + 8;
    }

    private WeatherService() {}
}
