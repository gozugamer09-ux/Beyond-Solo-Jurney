package com.beyondsolo.raid;

import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.guild.GuildHunterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A pylon of a raid boss's barrier: a crystal on the floor of its chamber pouring light into the boss through a beam.
 * While any stands, nothing touches the boss. It breaks after a number of hits (any blow counts as one, a raider's or a
 * hired hunter's, so it is breaking them together that matters); nothing else hurts it. Never saved with the world:
 * {@link Raids} raises them again if the chamber was unloaded while the barrier stood.
 */
public class RaidPylonEntity extends EndCrystal {
    @Nullable private UUID gateId, bossId;
    private int hits = 4, hitsLeft = 4;
    private long lastHit = -100;

    public RaidPylonEntity(EntityType<? extends EndCrystal> type, Level level) {
        super(type, level);
        setShowBottom(false);
    }

    /** Makes this a pylon of a raid's boss, breaking after {@code hits} hits. */
    public void configure(UUID gate, GateBossEntity boss, int hits) {
        this.gateId = gate;
        this.bossId = boss.getUUID();
        this.hits = Math.max(1, hits);
        this.hitsLeft = this.hits;
        aim(boss);
        label();
    }

    @Nullable public UUID gateId() { return gateId; }
    public int hitsLeft() { return hitsLeft; }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || tickCount % 2 != 0) return;
        Entity boss = bossId == null ? null : server.getEntity(bossId);
        // its boss fell or left, or its barrier is gone (broken, or the raid is over): it goes dark
        if (!(boss instanceof GateBossEntity b) || !b.isAlive() || gateId == null || !Raids.barrierUp(server.getServer(), gateId)) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 10, 0.4, 0.5, 0.4, 0.05);
            discard();
            return;
        }
        aim(b);
        if (tickCount % 6 == 0) {
            // light running up the beam into the boss
            double t = (tickCount % 30) / 30.0;
            double x = getX() + (b.getX() - getX()) * t, y = getY() + 1 + (b.getY() + b.getBbHeight() * 0.6 - getY() - 1) * t, z = getZ() + (b.getZ() - getZ()) * t;
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private void aim(Entity boss) {
        setBeamTarget(BlockPos.containing(boss.getX(), boss.getY() + boss.getBbHeight() * 0.55, boss.getZ()));
    }

    /** "Pylon ■■■□": its name over it shows the hits it has left. */
    private void label() {
        MutableComponent bars = Component.literal(" ");
        for (int i = 0; i < hits; i++) {
            boolean on = i < hitsLeft;
            bars.append(Component.literal(on ? "■" : "□").withStyle(s -> s.withColor(on ? 0xFFC247 : 0x5A6070)));
        }
        setCustomName(Component.translatable(Raids.KEY + "pylon").withStyle(s -> s.withColor(0xE8D4FF)).append(bars));
        setCustomNameVisible(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) return false;
        Entity by = source.getEntity();
        // only raiders and their hunters break it (no explosion, no stray creature)
        if (!(by instanceof Player) && !(by instanceof GuildHunterEntity)) return false;
        long now = level().getGameTime();
        // a sweep or a volley in the same moment is one hit
        if (now - lastHit < 4) return false;
        lastHit = now;
        hitsLeft--;
        ServerLevel server = (ServerLevel) level();
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 12, 0.4, 0.4, 0.4, 0.2);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.6f, 0.6f + 0.1f * hitsLeft);
        if (hitsLeft > 0) {
            label();
            return true;
        }
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 40, 0.6, 0.8, 0.6, 0.25);
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.3, 0.3, 0.3, 0);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2f, 0.6f);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.4f);
        discard();
        if (gateId != null) Raids.pylonBroken(server, gateId);
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Hits", hits);
        tag.putInt("HitsLeft", hitsLeft);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hits = Math.max(1, tag.getInt("Hits"));
        hitsLeft = Math.max(1, tag.getInt("HitsLeft"));
    }
}
