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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    // The player being evaluated watches all of this in first person, so the light show plays on the
    // crystal and on the ground around them, never in their face. The big moments on their own
    // screen are drawn by the client overlay.

    private static void scanEffects(ServerLevel level, Session s) {
        ServerPlayer player = s.player;
        int t = s.tick;
        float progress = t / (float) EvaluationTimeline.SCAN_TICKS;
        Vec3 gem = gem(s.crystal);
        Vec3 feet = player.position();
        DustParticleOptions cyan = dust(EvaluationCrystalBlockEntity.IDLE_COLOR, 0.5f);

        // (the trails of light winding around the crystal are drawn by its renderer)
        // the crystal reads the player: glyphs lift off them and stream into it
        if (t % 2 == 0) glyph(level, player.getRandom(), feet.add(0, 0.9, 0), gem);
        if (t % 4 == 0) groundRing(level, feet, cyan, 1.5, 12, t * 0.08);

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

        if (r == 0) {
            if (s.rank == null) s.rank = PlayerRank.roll(player.getRandom());
            if (s.playerClass == null) s.playerClass = PlayerClass.roll(player.getRandom());
            SystemAPI.awaken(player, s.rank, s.playerClass);
            SystemData data = SystemAPI.get(player);
            List<Integer> stats = new ArrayList<>();
            for (Stat stat : Stat.values()) stats.add(data.stat(stat));
            PacketDistributor.sendToPlayer(player, EvaluationPayload.reveal(s.rank.ordinal(), s.playerClass.ordinal(), stats));
            // The overlay covers the screen, so nothing can hurt the player until it is over.
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EvaluationTimeline.FINISH + 20, 4, false, false, false));
            crystalState(level, s.crystal, EvaluationCrystalBlockEntity.REVEALING, 0);
            level.playSound(null, s.crystal, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 0.8f);
        }
        PlayerRank rank = s.rank;
        PlayerClass playerClass = s.playerClass;
        if (rank == null || playerClass == null) return;
        Vec3 gem = gem(s.crystal);
        Vec3 feet = player.position();

        // (while the rank is decided the crystal draws light in from all around: its renderer does that)
        if (r == EvaluationTimeline.RANK_LOCK) rankBurst(level, s, rank);
        int wave = r - EvaluationTimeline.RANK_LOCK;
        if (wave >= 1 && wave <= 8) {
            // a ring of the rank's colour racing outwards along the ground
            groundRing(level, feet, dust(rank.color(), 0.8f), 1.4 + wave * 0.5, 16 + wave * 3, 0);
        }
        if (r >= EvaluationTimeline.CLASS_START && r < EvaluationTimeline.CLASS_LOCK) {
            // the class's colour circling the player (and winding around the crystal, which its renderer draws)
            if (r % 4 == 0) groundRing(level, feet, dust(playerClass.color(), 0.6f), 1.5, 12, r * 0.08);
            // the crystal answers: glyphs stream back from it into the player
            if (r % 2 == 0) glyph(level, player.getRandom(), gem, feet.add(0, 0.9, 0));
        }
        if (r == EvaluationTimeline.CLASS_START) {
            crystalState(level, s.crystal, EvaluationCrystalBlockEntity.CHANNELING, playerClass.ordinal());
            level.playSound(null, s.crystal, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8f, 1.3f);
        }
        if (r == EvaluationTimeline.CLASS_LOCK) {
            DustParticleOptions color = dust(playerClass.color(), 0.9f);
            crystalState(level, s.crystal, EvaluationCrystalBlockEntity.LOCKED, rank.ordinal());
            level.sendParticles(color, gem.x, gem.y + 0.9, gem.z, 30, 0.3, 0.8, 0.3, 0);
            level.sendParticles(ParticleTypes.ENCHANT, gem.x, gem.y + 0.4, gem.z, 60, 0.6, 0.6, 0.6, 1.0);
            groundRing(level, feet, color, 1.4, 16, 0);
            groundRing(level, feet, color, 2.2, 24, 0);
            level.playSound(null, s.crystal, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 1.0f);
            level.playSound(null, s.crystal, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
        }
    }

    private static void rankBurst(ServerLevel level, Session s, PlayerRank rank) {
        Vec3 gem = gem(s.crystal);
        RandomSource random = s.player.getRandom();
        int tier = rank.tier();
        BlockPos at = s.crystal;

        crystalState(level, s.crystal, EvaluationCrystalBlockEntity.LOCKED, rank.ordinal());
        level.sendParticles(ParticleTypes.FLASH, gem.x, gem.y, gem.z, 1, 0, 0, 0, 0);
        // a fountain of light thrown up from the crystal (the flash, the ring along the floor and the change of
        // colour are drawn by its renderer)
        int rods = 14 + tier * 6;
        for (int k = 0; k < rods; k++) {
            double a = random.nextDouble() * TAU;
            double out = 0.2 + random.nextDouble() * 0.5;
            level.sendParticles(ParticleTypes.END_ROD, gem.x, gem.y + 0.3, gem.z,
                    0, Math.cos(a) * out, 1.0 + random.nextDouble() * 0.8, Math.sin(a) * out, 0.16 + tier * 0.02);
        }

        sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
        sound(level, at, SoundEvents.PLAYER_LEVELUP, 0.9f, 0.7f + tier * 0.08f);
        if (tier >= 2) sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
        if (tier >= 3) {
            level.sendParticles(ParticleTypes.FIREWORK, gem.x, gem.y + 1.0, gem.z, 30 + tier * 10, 0.2, 0.3, 0.2, 0.14);
            sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        }
        if (tier >= 4) {
            // a second, wider fountain
            for (int k = 0; k < 24; k++) {
                double a = k / 24.0 * TAU;
                level.sendParticles(ParticleTypes.END_ROD, gem.x, gem.y, gem.z, 0, Math.cos(a), 0.35, Math.sin(a), 0.22);
            }
            sound(level, at, SoundEvents.TOTEM_USE, 0.7f, 1f);
        }
        if (tier >= 5) {
            // S rank: the sky answers. Each client draws the bolt itself when the crystal locks
            // (see EvaluationCrystalBlockEntity), so it is for show only and counts as nothing in the world.
            Vec3 base = Vec3.atBottomCenterOf(s.crystal);
            DustParticleOptions heavy = dust(rank.color(), 1.0f);
            groundRing(level, base, heavy, 2.6, 30, 0);
            groundRing(level, base, heavy, 3.6, 40, 0.07);
            sound(level, at, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 0.7f);
        }
    }

    /** An enchanting-table glyph that appears near {@code from} and flies into {@code to}. */
    private static void glyph(ServerLevel level, RandomSource random, Vec3 from, Vec3 to) {
        // This particle is given the point it heads for, and its "speed" is where it starts, measured from that point.
        // It dips 1.2 blocks at the very end of its flight (into an enchanting table), so we aim that much higher.
        double aimY = to.y + 1.2;
        level.sendParticles(ParticleTypes.ENCHANT, to.x, aimY, to.z, 0,
                from.x - to.x + (random.nextDouble() - 0.5) * 0.7, from.y - aimY + (random.nextDouble() - 0.5) * 0.9, from.z - to.z + (random.nextDouble() - 0.5) * 0.7, 1.0);
    }

    /** A circle of particles lying on the ground. */
    private static void groundRing(ServerLevel level, Vec3 center, ParticleOptions particle, double radius, int count, double turn) {
        for (int k = 0; k < count; k++) {
            double a = k / (double) count * TAU + turn;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.12, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
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
        return Vec3.atBottomCenterOf(crystal).add(0, EvaluationCrystalBlockEntity.GEM_HEIGHT, 0);
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    private static void sound(ServerLevel level, BlockPos at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void crystalState(ServerLevel level, BlockPos crystal, int state, int param) {
        if (level.getBlockState(crystal).is(ModBlocks.EVALUATION_CRYSTAL.get())) {
            level.blockEvent(crystal, ModBlocks.EVALUATION_CRYSTAL.get(), state, param);
        }
    }

    private EvaluationManager() {}
}
