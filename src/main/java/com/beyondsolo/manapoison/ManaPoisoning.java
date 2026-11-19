package com.beyondsolo.manapoison;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.ManaPoisonPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.reward.RewardModule;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.BodyParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mana poisoning: the mana in high-rank gates (A and S, {@link ManaPoisonConfig#minGateRank}) is thick enough to
 * poison the hunters who breathe it. It builds up (0..100) faster the further the gate's rank is above the hunter's
 * true rank ({@link Reevaluation#trueRank}): a hunter at the gate's rank feels it only on a long stay, a hunter one rank
 * below within minutes, and a hunter above the gate not at all. Intelligence holds a share of it off, and it thins
 * once the gate's boss is down.
 *
 * <ul>
 *   <li><b>30, Mana Sickness:</b> the hunter's blows lose a tenth.</li>
 *   <li><b>60, Mana Poisoning:</b> a fifth, a tenth of their speed, and it eats a share of their health every 3 s.</li>
 *   <li><b>90, Mana Collapse:</b> a third (near enough), a fifth of their speed, and twice the bite.</li>
 *   <li><b>100, Mana Burst:</b> the mana tears out of them (a fifth of their health) and the poisoning falls to 75.</li>
 * </ul>
 * It goes on its own outside thick mana. Purifying crystals ({@link PurifyingCrystalBlock}, grown into A and S gates
 * by {@link ManaCrystals}) draw it out fast from a hunter resting by them; a purifying draught washes half of it out
 * and keeps the mana out for a while (Clear Breath). Death clears it. A cursed box's Backlash curse uses
 * {@link #add} wherever the hunter stands.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ManaPoisoning {
    public static final float MAX = 100;
    /** Where the stages begin. */
    public static final int SICK = 30, POISONED = 60, COLLAPSE = 90;
    /** Where the poisoning falls back to after a burst. */
    public static final float AFTER_BURST = 75;
    /** The beats (half seconds) between two bites of the poisoning. */
    public static final int DRAIN_BEATS = 6;

    public static final ResourceLocation SPEED = BeyondSolo.id("mana_poisoning");
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BeyondSolo.id("mana_poisoning"));

    /** Sync flags: in thick mana that is poisoning the hunter, being purified by a crystal, under Clear Breath, burst just now. */
    public static final int DENSE = 1, PURIFYING = 2, CLEAR = 4, BURST = 8;

    /** Game tests listening in on what a player's screen is told (test players drop everything sent to them). */
    private static final Map<UUID, Consumer<ManaPoisonPayload>> LISTENERS = new HashMap<>();

    public static ManaPoisonData data(Player p) {
        return p.getData(ManaPoisonModule.DATA);
    }

    public static float level(Player p) {
        return data(p).level;
    }

    /** 0 clean, 1 Mana Sickness, 2 Mana Poisoning, 3 Mana Collapse. */
    public static int stage(float level) {
        return level >= COLLAPSE ? 3 : level >= POISONED ? 2 : level >= SICK ? 1 : 0;
    }

    /** The share of their damage a hunter's blows lose at a stage. */
    public static float weakening(int stage) {
        return switch (stage) {
            case 1 -> 0.10f;
            case 2 -> 0.20f;
            case 3 -> 0.30f;
            default -> 0f;
        };
    }

    /** The share of their speed a hunter loses at a stage. */
    public static double slowing(int stage) {
        return stage >= 3 ? 0.2 : stage == 2 ? 0.1 : 0;
    }

    // ------------------------------------------------------------------ changing it

    /** Adds poisoning (a cursed box's backlash, a command). A full 100 bursts at once. */
    public static void add(ServerPlayer p, float amount) {
        set(p, level(p) + amount);
    }

    public static void set(ServerPlayer p, float level) {
        ManaPoisonData d = data(p);
        d.level = clamp(level);
        int flags = 0;
        if (d.level >= MAX) {
            burst(p, d);
            flags |= BURST;
        }
        settle(p, d);
        sync(p, d, (d.sentFlags < 0 ? 0 : d.sentFlags & ~BURST) | flags);
    }

    public static void clear(ServerPlayer p) {
        set(p, 0);
    }

    /** A purifying draught: washes most of the poisoning out, keeps the mana out for a while, lifts a cursed box's hex. */
    public static void purify(LivingEntity e) {
        if (!(e instanceof ServerPlayer p)) return;
        int ticks = ManaPoisonConfig.clearBreathTicks();
        if (ticks > 0) p.addEffect(new MobEffectInstance(ManaPoisonModule.CLEAR_BREATH, ticks, 0, false, false, true));
        p.removeEffect(RewardModule.FRAILTY);
        set(p, level(p) - ManaPoisonConfig.draught());
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(MAX, v));
    }

    // ------------------------------------------------------------------ the beat

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) beat(p);
    }

    /** One half-second beat for one hunter. Public for tests. */
    public static void beat(ServerPlayer p) {
        ManaPoisonData d = data(p);
        if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
            if (p.isCreative() && d.level > 0) d.level = 0;
            settle(p, d);
            sync(p, d, 0);
            return;
        }
        GateInstance g = ManaPoisonConfig.enabled() ? denseGate(p) : null;
        double rate = g == null || g.bossDefeated() ? 0 : rate(p, g);
        boolean clearBreath = p.hasEffect(ManaPoisonModule.CLEAR_BREATH);
        BlockPos crystal = d.level > 0 || rate > 0 ? nearestCrystal(p) : null;
        int flags = 0;
        float before = d.level;
        if (crystal != null) {
            d.level = clamp(d.level - (float) ManaPoisonConfig.crystalPurify() * 0.5f);
            if (d.level < before) {
                flags |= PURIFYING;
                drawOut(p, crystal);
            }
        } else if (rate > 0 && !clearBreath) {
            d.level = clamp(d.level + (float) rate * 0.5f);
        } else {
            d.level = clamp(d.level - (float) ManaPoisonConfig.decay() * 0.5f);
        }
        if (rate > 0) flags |= DENSE;
        if (clearBreath) flags |= CLEAR;
        if (g != null && rate > 0 && !g.id().equals(d.warnedGate)) {
            d.warnedGate = g.id();
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.mana.dense.title"), Component.translatable("notify.beyondsolo.mana.dense")));
        }
        int stage = stage(d.level);
        if (stage >= 2) {
            if (++d.drainBeat >= DRAIN_BEATS) {
                d.drainBeat = 0;
                double share = stage >= 3 ? ManaPoisonConfig.drainCollapse() : ManaPoisonConfig.drainPoisoned();
                hurt(p, (float) (p.getMaxHealth() * share));
                BodyParticles.send(p, ParticleTypes.WITCH, 1.1, 6, 0.3, 0.45, 0.3, 0.01);
            }
        } else {
            d.drainBeat = 0;
        }
        if (d.level >= MAX) {
            burst(p, d);
            flags |= BURST;
        }
        settle(p, d);
        sync(p, d, flags);
    }

    /**
     * Poisoning a second for this hunter in this gate, before Clear Breath and crystals: by how far the gate's rank is
     * above the hunter's true rank, thicker in S gates, less for a sharp mind.
     */
    public static double rate(ServerPlayer p, GateInstance g) {
        int gap = g.rank().tier() - Reevaluation.trueRank(p).tier();
        if (gap < 0) return 0;
        double r = gap == 0 ? ManaPoisonConfig.matchedRate() : gap == 1 ? ManaPoisonConfig.aboveRate() : ManaPoisonConfig.farAboveRate();
        if (g.rank() == PlayerRank.S) r *= ManaPoisonConfig.sGateMultiplier();
        return r * (1 - shield(p));
    }

    /** The share of the poisoning a hunter's intelligence holds off. */
    public static double shield(Player p) {
        int above = Math.max(0, SystemAPI.get(p).stat(Stat.INT) - Stat.BASE);
        return Math.min(ManaPoisonConfig.intCap(), above / (double) ManaPoisonConfig.intPerPercent() / 100.0);
    }

    /** The built, open cave of a gate thick enough with mana to poison that the hunter stands in, or null. */
    @Nullable
    public static GateInstance denseGate(ServerPlayer p) {
        ServerLevel dungeon = GateService.dungeon(p.server);
        if (dungeon == null || p.level() != dungeon || GateData.slotAt(p.blockPosition()) < 1) return null;
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        if (g == null || !g.built() || g.state() == GateInstance.State.CLOSED || g.isTrial() || g.temple()) return null;
        return dense(g) ? g : null;
    }

    /** Whether a gate's mana is thick enough to poison. */
    public static boolean dense(GateInstance g) {
        return !g.isTrial() && !g.temple() && g.rank().tier() >= ManaPoisonConfig.minGateRank().tier();
    }

    /** The purifying crystal the hunter is resting by, or null. */
    @Nullable
    public static BlockPos nearestCrystal(ServerPlayer p) {
        int r = ManaPoisonConfig.crystalRadius();
        BlockPos at = p.blockPosition();
        ServerLevel level = p.serverLevel();
        BlockPos best = null;
        double bestD = (r + 0.5) * (r + 0.5);
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 3, r))) {
            if (!level.getBlockState(q).is(ManaPoisonModule.CRYSTAL.get())) continue;
            double dd = p.position().distanceToSqr(q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5);
            if (dd <= bestD) {
                bestD = dd;
                best = q.immutable();
            }
        }
        return best;
    }

    /**
     * Motes of the poisoning drawn out of the hunter into the crystal, and a soft chime now and then. Others see them
     * leave the hunter's body; the hunter sees them a step further along the way, since a mote right under a
     * first-person camera shows as a big white star.
     */
    private static void drawOut(ServerPlayer p, BlockPos crystal) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position().add(0, 1.0, 0);
        Vec3 to = Vec3.atCenterOf(crystal).add(0, 0.3, 0);
        Vec3 dir = to.subtract(from);
        double ahead = Math.min(1.3, dir.length() * 0.5);
        Vec3 own = from.add(dir.normalize().scale(ahead));
        Vec3 ownDir = to.subtract(own);
        for (int i = 0; i < 3; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.7, oy = (level.random.nextDouble() - 0.5) * 0.9, oz = (level.random.nextDouble() - 0.5) * 0.7;
            for (ServerPlayer seer : level.players()) {
                if (seer != p) {
                    level.sendParticles(seer, ParticleTypes.END_ROD, false, from.x + ox, from.y + oy, from.z + oz, 0, dir.x, dir.y, dir.z, 0.06);
                } else {
                    level.sendParticles(seer, ParticleTypes.END_ROD, false, own.x + ox * 0.6, own.y + oy * 0.5, own.z + oz * 0.6, 0,
                            ownDir.x, ownDir.y, ownDir.z, 0.06);
                }
            }
        }
        if (p.server.getTickCount() % 40 == 0) {
            level.playSound(null, crystal, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 0.8f + level.random.nextFloat() * 0.4f);
        }
    }

    // ------------------------------------------------------------------ what it does

    /** The mana tears out: a share of the hunter's health, and the poisoning falls back. */
    private static void burst(ServerPlayer p, ManaPoisonData d) {
        d.level = AFTER_BURST;
        d.drainBeat = 0;
        ServerLevel level = p.serverLevel();
        BodyParticles.send(p, ParticleTypes.SOUL_FIRE_FLAME, 1, 40, 0.4, 0.6, 0.4, 0.12);
        BodyParticles.send(p, ParticleTypes.WITCH, 1, 30, 0.5, 0.7, 0.5, 0.05);
        level.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.6f);
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 1.7f);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.mana.burst.title"), Component.translatable("notify.beyondsolo.mana.burst")));
        hurt(p, (float) (p.getMaxHealth() * ManaPoisonConfig.burst()));
    }

    /** The poisoning's own damage: through armour, without knockback or touching the hunter's hit cooldown. */
    private static void hurt(ServerPlayer p, float amount) {
        if (amount <= 0 || !p.isAlive()) return;
        int invulnerable = p.invulnerableTime;
        float lastHurt = p.lastHurt;
        p.hurt(p.damageSources().source(DAMAGE), amount);
        p.invulnerableTime = invulnerable;
        p.lastHurt = lastHurt;
    }

    /** Pop-ups as the stages are reached and when it is gone, and the slowness of the stage. */
    private static void settle(ServerPlayer p, ManaPoisonData d) {
        int stage = stage(d.level);
        if (stage > d.stage) {
            String key = switch (stage) {
                case 1 -> "sick";
                case 2 -> "poisoned";
                default -> "collapse";
            };
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.mana." + key + ".title"), Component.translatable("notify.beyondsolo.mana." + key)));
            d.sick = true;
        }
        d.stage = stage;
        if (d.level <= 0 && d.sick) {
            d.sick = false;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.mana.clean.title"), Component.translatable("notify.beyondsolo.mana.clean")));
        }
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double want = -slowing(stage);
        AttributeModifier have = speed.getModifier(SPEED);
        if (want == 0) {
            if (have != null) speed.removeModifier(SPEED);
        } else if (have == null || have.amount() != want) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A poisoned hunter's blows lose their share. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onHit(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() == p) return;
        float weak = weakening(stage(level(p)));
        if (weak > 0) event.setAmount(event.getAmount() * (1 - weak));
    }

    // ------------------------------------------------------------------ the screen

    private static void sync(ServerPlayer p, ManaPoisonData d, int flags) {
        int tenths = Math.round(d.level * 10);
        if (tenths == d.sentLevel && flags == d.sentFlags) return;
        d.sentLevel = tenths;
        d.sentFlags = flags;
        ManaPoisonPayload payload = new ManaPoisonPayload(tenths, flags);
        PacketDistributor.sendToPlayer(p, payload);
        Consumer<ManaPoisonPayload> listener = LISTENERS.get(p.getUUID());
        if (listener != null) listener.accept(payload);
    }

    /** For game tests: hears what a player's screen is told (null stops listening). */
    public static void listen(UUID player, @Nullable Consumer<ManaPoisonPayload> listener) {
        if (listener == null) LISTENERS.remove(player);
        else LISTENERS.put(player, listener);
    }

    private ManaPoisoning() {}
}
