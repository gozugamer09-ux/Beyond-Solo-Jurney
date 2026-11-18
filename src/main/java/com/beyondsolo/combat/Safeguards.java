package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.tower.TowerRun;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Two guards against dying for no fair reason (playtest 0.1.6).
 *
 * <ul>
 *   <li><b>One death per death.</b> When hits nest inside hits (a proc inside a blow), every one of them that finds
 *   the player at zero health calls for the death again on its way out: the death message fifteen times over, and
 *   every death hook (quests failing, keyed runs, penalties) run as often. A player who has died, and not got up since,
 *   can't die again.</li>
 *   <li><b>The boss's fall.</b> When a dungeon's boss dies, every hunter fighting it is healed to full, rid of what
 *   still burns or poisons them, and can't be hurt for {@link #BOSS_SHIELD_TICKS}: nothing the fight left behind (a
 *   last swing, a burn, a lingering add) can kill them while the cache comes down. The Crimson Citadel has its own
 *   rules (a fall there is never a death) and is left alone.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Safeguards {
    /** How long the shield after a boss's fall lasts: 6 seconds. */
    public static final int BOSS_SHIELD_TICKS = 120;
    /** Hunters this close to a boss when it falls count as fighting it. */
    public static final double BOSS_RADIUS = 64;
    /** Persistent-data key: game time the boss-fall shield runs out. */
    public static final String SHIELD_KEY = "beyondsolo_boss_shield_until";

    /** Players who have died and not got up since (a respawn makes a new player object, so this empties itself). */
    private static final Set<ServerPlayer> FALLEN = Collections.newSetFromMap(new WeakHashMap<>());

    // ------------------------------------------------------------------ one death per death

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void beforeDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.isDeadOrDying() && FALLEN.contains(p)) event.setCanceled(true);
    }

    /** Only reached by a death nobody refused (lowest priority, cancelled deaths skip it). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void afterDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof ServerPlayer p) {
            FALLEN.add(p);
            return;
        }
        if (dead instanceof DungeonBoss && !dead.getTags().contains(TowerRun.FOE_TAG)) bossFell(level, dead);
    }

    @SubscribeEvent
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) FALLEN.remove(p);
    }

    /** True if this player has already died and not got up since. */
    public static boolean hasFallen(ServerPlayer p) {
        return FALLEN.contains(p) && p.isDeadOrDying();
    }

    // ------------------------------------------------------------------ the boss's fall

    /** Heals and shields every living hunter near a boss that just fell. Returns who was covered. */
    public static List<ServerPlayer> bossFell(ServerLevel level, LivingEntity boss) {
        List<ServerPlayer> out = new ArrayList<>();
        AABB box = boss.getBoundingBox().inflate(BOSS_RADIUS);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, ServerPlayer::isAlive)) {
            if (p.isSpectator()) continue;
            restore(p, BOSS_SHIELD_TICKS);
            out.add(p);
        }
        return out;
    }

    /** Full health, no harmful effects, fire or element buildup, and no harm for {@code ticks}. */
    public static void restore(ServerPlayer p, int ticks) {
        p.setHealth(p.getMaxHealth());
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(e);
        }
        for (MobEffectInstance e : bad) p.removeEffect(e.getEffect());
        for (BuildupType type : Buildups.all()) Buildups.clear(p, type);
        p.clearFire();
        p.setTicksFrozen(0);
        shield(p, ticks);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.6, 0.4, 0);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.6, 0.3, 0.03);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        p.displayClientMessage(Component.translatable("message.beyondsolo.boss_safeguard"), true);
    }

    /** No harm to this player for {@code ticks} (a /kill or the void still gets through). */
    public static void shield(ServerPlayer p, int ticks) {
        CompoundTag tag = p.getPersistentData();
        tag.putLong(SHIELD_KEY, Math.max(tag.getLong(SHIELD_KEY), p.level().getGameTime() + ticks));
    }

    public static boolean shielded(ServerPlayer p) {
        return p.getPersistentData().getLong(SHIELD_KEY) > p.level().getGameTime();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (shielded(p)) event.setCanceled(true);
    }

    private Safeguards() {}
}
