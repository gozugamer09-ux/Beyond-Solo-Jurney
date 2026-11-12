package com.beyondsolo.evaluation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.network.EvaluationPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the evaluation. First the crystal scans the player for a few seconds (they must stay close),
 * then the reveal plays: the rank is decided, then the class, with light and sound around the player
 * that everyone nearby can see. The matching on-screen overlay is drawn by the client
 * (see EvaluationOverlay); the moments both sides share are in {@link EvaluationTimeline}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class EvaluationManager {
    private static final double MAX_DISTANCE_SQ = 6 * 6;
    private static final double TAU = Math.PI * 2;

    private static final class Session {
        final ServerPlayer player;
        final BlockPos crystal;
        int tick;
        /** Ticks since the reveal began, or -1 while still scanning. */
        int revealTick = -1;
        @Nullable PlayerRank rank;
        @Nullable PlayerClass playerClass;

        Session(ServerPlayer player, BlockPos crystal, @Nullable PlayerRank rank, @Nullable PlayerClass playerClass) {
            this.player = player;
            this.crystal = crystal;
            this.rank = rank;
            this.playerClass = playerClass;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** A player touched the crystal. */
    static void interact(ServerPlayer player, BlockPos crystal) {
        if (SESSIONS.containsKey(player.getUUID())) return;
        SystemData data = SystemAPI.get(player);
        if (data.isAwakened()) {
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.already_evaluated.title"),
                    Component.translatable("notify.beyondsolo.already_evaluated.body",
                            data.rank().displayName(), data.playerClass().displayName())));
            return;
        }
        begin(player, crystal, null, null);
    }

    /**
     * Starts an evaluation at {@code crystal} (any position works; a real crystal there also animates).
     * Pass a rank and/or class to force the result, or null to roll it. Returns false if one is already running.
     */
    public static boolean begin(ServerPlayer player, BlockPos crystal, @Nullable PlayerRank rank, @Nullable PlayerClass playerClass) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        SESSIONS.put(player.getUUID(), new Session(player, crystal.immutable(), rank, playerClass));
        ServerLevel level = player.serverLevel();
        crystalState(level, crystal, EvaluationCrystalBlockEntity.SCANNING, 0);
        level.playSound(null, crystal, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.3f);
        level.playSound(null, crystal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 0.6f);
        PacketDistributor.sendToPlayer(player, EvaluationPayload.start(EvaluationTimeline.SCAN_TICKS));
        return true;
    }

    public static boolean isBeingEvaluated(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            ServerPlayer player = s.player;
            if (player.isRemoved() || player.hasDisconnected()) {
                if (player.level() instanceof ServerLevel level) crystalState(level, s.crystal, EvaluationCrystalBlockEntity.IDLE, 0);
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (s.revealTick < 0) {
                if (player.distanceToSqr(s.crystal.getCenter()) > MAX_DISTANCE_SQ) {
                    it.remove();
                    interrupt(level, s);
                    continue;
                }
                scanEffects(level, s);
                if (++s.tick >= EvaluationTimeline.SCAN_TICKS) s.revealTick = 0;
            }
            if (s.revealTick >= 0) {
                revealStep(level, s);
                if (s.revealTick++ >= EvaluationTimeline.FINISH) {
                    it.remove();
                    finish(level, s);
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    // ---------------------------------------------------------------- scan

    private static void interrupt(ServerLevel level, Session s) {
        crystalState(level, s.crystal, EvaluationCrystalBlockEntity.IDLE, 0);
        level.playSound(null, s.crystal, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1.2f);
        PacketDistributor.sendToPlayer(s.player, EvaluationPayload.cancel());
        Notifications.send(s.player, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.scan_interrupted.title"),
                Component.translatable("notify.beyondsolo.scan_interrupted.body")));
    }

    private static void scanEffects(ServerLevel level, Session s) {
        ServerPlayer player = s.player;
        int t = s.tick;
        float progress = t / (float) EvaluationTimeline.SCAN_TICKS;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        Vec3 gem = gem(s.crystal);
        DustParticleOptions cyan = dust(EvaluationCrystalBlockEntity.IDLE_COLOR, 0.8f);

        if (t % 2 == 0) {
            // two strands of light climbing around the player
            for (int k = 0; k < 2; k++) {
                double a = t * 0.42 + k * Math.PI;
                double h = (t % 40) / 40.0 * 2.0;
                level.sendParticles(ParticleTypes.END_ROD, px + Math.cos(a) * 0.85, py + 0.1 + h, pz + Math.sin(a) * 0.85, 1, 0, 0, 0, 0);
            }
            // a thread of light from the crystal to the player's chest
            Vec3 chest = player.position().add(0, 1.0, 0);
            for (int k = 0; k < 3; k++) {
                Vec3 q = gem.lerp(chest, player.getRandom().nextDouble());
                level.sendParticles(cyan, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
            }
        }
        if (t % 5 == 0) {
            // a turning circle on the ground
            for (int k = 0; k < 10; k++) {
                double a = k / 10.0 * TAU + t * 0.1;
                level.sendParticles(cyan, px + Math.cos(a) * 1.3, py + 0.08, pz + Math.sin(a) * 1.3, 1, 0, 0, 0, 0);
            }
        }
        if (t % 3 == 0) level.sendParticles(ParticleTypes.ENCHANT, gem.x, gem.y + 0.3, gem.z, 5, 0.5, 0.5, 0.5, 0.6);

        // a chime that climbs and speeds up as the scan fills
        int interval = Math.max(3, Math.round(12 - 9 * progress));
        if (t % interval == 0) {
            level.playSound(null, s.crystal, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 0.5f + 1.4f * progress);
        }
    }

    // ---------------------------------------------------------------- reveal

    private static void revealStep(ServerLevel level, Session s) {
        ServerPlayer player = s.player;
        int r = s.revealTick;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        if (r == 0) {
            if (s.rank == null) s.rank = PlayerRank.roll(player.getRandom());
            if (s.playerClass == null) s.playerClass = PlayerClass.roll(player.getRandom());
            SystemAPI.awaken(player, s.rank, s.playerClass);
            SystemData data = SystemAPI.get(player);
            List<Integer> stats = new ArrayList<>();
            for (Stat stat : Stat.values()) stats.add(data.stat(stat));
            PacketDistributor.sendToPlayer(player, EvaluationPayload.reveal(s.rank.ordinal(), s.playerClass.ordinal(), stats));
            // The overlay covers part of the screen, so nothing can hurt the player until it is over.
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EvaluationTimeline.FINISH + 20, 4, false, false, false));
            crystalState(level, s.crystal, EvaluationCrystalBlockEntity.REVEALING, 0);
            level.playSound(null, s.crystal, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 0.8f);
        }
        PlayerRank rank = s.rank;
        PlayerClass playerClass = s.playerClass;
        if (rank == null || playerClass == null) return;

        if (r < EvaluationTimeline.RANK_LOCK && r % 2 == 0) {
            // light gathers on the player from all around while the rank is decided
            for (int k = 0; k < 6; k++) {
                double a = k / 6.0 * TAU + r * 0.3;
                level.sendParticles(ParticleTypes.END_ROD, px + Math.cos(a) * 3.0, py + 0.6 + (k % 3) * 0.5, pz + Math.sin(a) * 3.0,
                        0, -Math.cos(a), 0.02, -Math.sin(a), 0.16);
            }
        }
        if (r == EvaluationTimeline.RANK_LOCK) rankBurst(level, s, rank);
        int wave = r - EvaluationTimeline.RANK_LOCK;
        if (wave >= 1 && wave <= 8) {
            // a ring of the rank's colour racing outwards along the ground
            DustParticleOptions color = dust(rank.color(), 1.5f);
            double radius = 0.6 + wave * 0.55;
            int count = 14 + wave * 3;
            for (int k = 0; k < count; k++) {
                double a = k / (double) count * TAU;
                level.sendParticles(color, px + Math.cos(a) * radius, py + 0.15, pz + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            }
        }
        if (r >= EvaluationTimeline.CLASS_START && r < EvaluationTimeline.CLASS_LOCK + 12) {
            // the class's colour winding up around the player
            DustParticleOptions color = dust(playerClass.color(), 1.1f);
            int n = r - EvaluationTimeline.CLASS_START;
            for (int k = 0; k < 3; k++) {
                double a = n * 0.5 + k * TAU / 3;
                double h = (n % 26) / 26.0 * 2.2;
                level.sendParticles(color, px + Math.cos(a) * 1.0, py + 0.1 + h, pz + Math.sin(a) * 1.0, 1, 0, 0, 0, 0);
            }
        }
        if (r == EvaluationTimeline.CLASS_START) {
            level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.3f);
        }
        if (r == EvaluationTimeline.CLASS_LOCK) {
            level.sendParticles(dust(playerClass.color(), 1.6f), px, py + 1.0, pz, 40, 0.55, 0.8, 0.55, 0);
            level.sendParticles(ParticleTypes.ENCHANT, px, py + 1.4, pz, 40, 0.6, 0.6, 0.6, 1.0);
            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.0f);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        }
    }

    private static void rankBurst(ServerLevel level, Session s, PlayerRank rank) {
        ServerPlayer player = s.player;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        int tier = rank.tier();
        BlockPos at = player.blockPosition();

        crystalState(level, s.crystal, EvaluationCrystalBlockEntity.LOCKED, rank.ordinal());
        // a pillar of the rank's colour and a ring of light thrown outwards
        level.sendParticles(dust(rank.color(), 1.8f), px, py + 1.6, pz, 30 + tier * 10, 0.25, 1.4, 0.25, 0);
        int rods = 20 + tier * 6;
        for (int k = 0; k < rods; k++) {
            double a = k / (double) rods * TAU;
            level.sendParticles(ParticleTypes.END_ROD, px, py + 1.0, pz, 0, Math.cos(a), 0.05, Math.sin(a), 0.22 + tier * 0.03);
        }
        level.sendParticles(ParticleTypes.FLASH, px, py + 1.2, pz, 1, 0, 0, 0, 0);

        sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
        sound(level, at, SoundEvents.PLAYER_LEVELUP, 0.9f, 0.7f + tier * 0.08f);
        if (tier >= 2) sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
        if (tier >= 3) {
            level.sendParticles(ParticleTypes.FIREWORK, px, py + 1.2, pz, 40 + tier * 10, 0.3, 0.5, 0.3, 0.18);
            sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        }
        if (tier >= 4) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py + 1.0, pz, 70, 0.4, 0.6, 0.4, 0.45);
            sound(level, at, SoundEvents.TOTEM_USE, 0.7f, 1f);
        }
        if (tier >= 5) {
            // S rank: the sky answers. The bolt is for show only and harms nothing.
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(s.crystal.above()));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            level.sendParticles(dust(rank.color(), 2.4f), px, py + 1.0, pz, 80, 1.6, 1.0, 1.6, 0);
            sound(level, at, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 0.7f);
        }
    }

    private static void finish(ServerLevel level, Session s) {
        ServerPlayer player = s.player;
        PlayerRank rank = s.rank;
        PlayerClass playerClass = s.playerClass;
        crystalState(level, s.crystal, EvaluationCrystalBlockEntity.IDLE, 0);
        level.playSound(null, s.crystal, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f);
        if (rank == null || playerClass == null) return;

        Notifications.send(player, new SystemNotification(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("notify.beyondsolo.evaluated.title"),
                Component.translatable("notify.beyondsolo.evaluated.body", rank.displayName(), playerClass.displayName()), 200));
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.system_online.title"),
                Component.translatable("notify.beyondsolo.system_online.body",
                        Component.keybind("key.beyondsolo.open_system"), Component.keybind("key.beyondsolo.dismiss")), 260));

        if (Config.ANNOUNCE_AWAKENINGS.get()) {
            Component message = Component.translatable("chat.beyondsolo.awakened", player.getDisplayName(),
                    Component.translatable("chat.beyondsolo.awakened.rank", rank.displayName()).withStyle(style -> style.withColor(TextColor.fromRgb(rank.color())).withBold(true)),
                    playerClass.displayName().copy().withStyle(style -> style.withColor(TextColor.fromRgb(playerClass.color()))))
                    .withStyle(ChatFormatting.GRAY);
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Where the crystal itself floats above its block. */
    private static Vec3 gem(BlockPos crystal) {
        return Vec3.atBottomCenterOf(crystal).add(0, 1.15, 0);
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    private static void sound(ServerLevel level, BlockPos at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void crystalState(ServerLevel level, BlockPos crystal, int state, int param) {
        if (level.getBlockState(crystal).is(ModBlocks.EVALUATION_CRYSTAL.get())) {
            level.blockEvent(crystal, ModBlocks.EVALUATION_CRYSTAL.get(), state, param);
        }
    }

    private EvaluationManager() {}
}
