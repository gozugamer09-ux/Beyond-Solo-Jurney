package com.beyondsolo.awakening;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.awakening.AwakeningData.Stage;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateFeature;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.AwakeningPayload;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.spawn.FirstSpawnHandler;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a player gets the System.
 *
 * <p>Everyone starts without it: no System window, no pop-ups, no quests ({@link SystemAPI#hasSystem}). Gates can
 * still be walked into. The first time a player without the System clears a gate of rank D or higher, a sealed gate
 * tears open next to them once they are back outside, and it does not let them walk away: past
 * {@code awakening.tetherRadius} blocks it pulls them back, and it follows them if they get away some other way.</p>
 *
 * <p>Behind it is the trial ({@link com.beyondsolo.gate.GateTheme#TRIAL}): a long descent that ends in the hall of
 * the {@link ArbiterEntity Hollow Arbiter}. Nobody dies in the trial. Falling on the way down puts the player back
 * on their feet at the last cavern they reached; falling to the Arbiter is what the trial is for: the death is
 * refused, the System binds to them (the awakening plays on screen), and they wake outside with everything they
 * carried. Then the usual road starts: find a village, get evaluated.</p>
 *
 * <p>The hidden way: the Arbiter can be killed. Then its cache holds one random legendary weapon, and the System
 * binds all the same.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class AwakeningService {
    /** Ticks of the awakening on screen (see {@code client.awakening.AwakeningOverlay}). */
    public static final int TELEPORT_TICK = 76;
    public static final int GRANT_TICK = 268;
    public static final int END_TICK = 290;
    public static final String SLAYER_TITLE = "title.beyondsolo.arbiter_slayer";

    private static final ResourceLocation FREEZE = BeyondSolo.id("awakening_freeze");
    private static final String LEGENDARY_TAG = "beyondsolo_legendary_for";

    private record Sequence(long started, boolean slain) {}

    private static final Map<UUID, Sequence> SEQUENCES = new HashMap<>();
    private static final Map<UUID, Long> SHIELDED = new HashMap<>();
    private static final Map<UUID, Long> TETHER_FX = new HashMap<>();

    public static AwakeningData data(Player player) {
        return player.getData(ModAttachments.AWAKENING);
    }

    public static Stage stage(Player player) {
        return data(player).stage();
    }

    public static boolean inSequence(Player player) {
        return SEQUENCES.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ the call

    /** A gate was cleared: players without the System who were in it are called, if it was rank D or higher. */
    public static void gateCleared(GateInstance g, List<ServerPlayer> players) {
        if (g.isTrial() || !Config.AWAKENING_TRIAL.get() || g.rank().tier() < 1) return;
        for (ServerPlayer p : players) {
            if (SystemAPI.hasSystem(p) || p.isSpectator()) continue;
            AwakeningData d = data(p);
            if (d.stage() == Stage.DORMANT) d.setStage(Stage.CALLED);
        }
    }

    /** A player stepped back out of a gate. The sealed gate opens a few seconds later (see the tick). */
    public static void leftGate(ServerPlayer player, GateInstance g) {
        AwakeningData d = data(player);
        if (d.stage() == Stage.CALLED && d.sealedPos() == null) d.setReadyAt(player.level().getGameTime() + 70);
    }

    /** Calls a player now, whatever they have or haven't cleared (commands, tests). */
    public static boolean call(ServerPlayer player) {
        AwakeningData d = data(player);
        if (SystemAPI.hasSystem(player) || d.stage() == Stage.TRIAL || d.stage() == Stage.AWAKENING) return false;
        d.setStage(Stage.CALLED);
        return openSealedGate(player) != null;
    }

    /** Tears the sealed gate open in front of the player. */
    @Nullable
    public static GateBlockEntity openSealedGate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (GateService.isDungeon(level) && level.dimension().equals(GateService.DIMENSION)) return null;
        AwakeningData d = data(player);
        closeSealedGate(player.server, d);
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos best = null;
        for (int attempt = 0; attempt < 24 && best == null; attempt++) {
            // straight ahead first, then fanning out and closer
            float turn = (attempt % 2 == 0 ? 1 : -1) * (attempt / 2) * 0.28f;
            double dist = 8 - (attempt / 8) * 1.5;
            Vec3 at = player.position().add(look.yRot(turn).scale(dist));
            BlockPos column = BlockPos.containing(at);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos p = column.atY(player.blockPosition().getY() + dy);
                if (GateFeature.goodSpot(level, p)) {
                    best = p;
                    break;
                }
            }
        }
        if (best == null) {
            // nowhere good (a cave, the sea): stand it right where they look and make room
            best = BlockPos.containing(player.position().add(look.scale(6))).atY(player.blockPosition().getY());
            for (int y = 0; y < 5; y++) {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.destroyBlock(best.offset(x, y, z), false);
            }
            if (level.getBlockState(best.below()).canBeReplaced()) level.setBlock(best.below(), net.minecraft.world.level.block.Blocks.CALCITE.defaultBlockState(), 3);
        }
        Direction facing = Direction.getNearest(player.getX() - best.getX(), 0, player.getZ() - best.getZ());
        if (facing.getAxis().isVertical()) facing = Direction.NORTH;
        GateBlockEntity gate = GateService.spawnSealedGate(level, best, facing, player);
        d.setSealedGate(gate.gateId(), level.dimension(), best);
        d.setReadyAt(-1);
        PacketDistributor.sendToPlayer(player, new AwakeningPayload(AwakeningPayload.CALLED, 0, best.getX() + 0.5, best.getY() + GateBlockEntity.CENTER_Y, best.getZ() + 0.5));
        return gate;
    }

    private static void closeSealedGate(MinecraftServer server, AwakeningData d) {
        if (d.sealedPos() == null || d.sealedDim() == null) return;
        ServerLevel level = server.getLevel(d.sealedDim());
        if (level != null && level.isLoaded(d.sealedPos()) && level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate && gate.isSealed()) {
            gate.startClosing(level.getGameTime());
            level.playSound(null, d.sealedPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.5f);
        }
        if (d.trial() != null) {
            GateInstance g = GateData.get(server).get(d.trial());
            if (g != null && g.state() == GateInstance.State.OPEN && !g.built()) {
                g.setState(GateInstance.State.CLOSED);
                GateData.get(server).setDirty();
            }
        }
    }

    /** Once a player is called, the gate holds them: a pull past the tether, and it follows those who slip away. */
    private static void tether(ServerPlayer player, AwakeningData d) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (GateService.inTransit(player) || !player.isAlive() || player.isSpectator()) return;
        if (GateService.isDungeon(level) && level.dimension().equals(GateService.DIMENSION)) return;
        if (d.sealedPos() == null) {
            if (d.readyAt() < 0) d.setReadyAt(now + 70);
            if (now >= d.readyAt() && player.onGround()) openSealedGate(player);
            return;
        }
        boolean here = level.dimension().equals(d.sealedDim());
        double dist = here ? Math.sqrt(d.sealedPos().distToCenterSqr(player.position())) : Double.MAX_VALUE;
        int radius = Config.TETHER_RADIUS.get();
        boolean gone = here && level.isLoaded(d.sealedPos()) && !(level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity);
        if (!here || gone || dist > radius + 40) {
            // another dimension, a bed far away, an ender pearl: the gate comes to them
            if (player.onGround() || !here) openSealedGate(player);
            return;
        }
        if (dist <= radius) return;
        if (player.isPassenger()) player.stopRiding();
        Vec3 to = Vec3.atCenterOf(d.sealedPos()).subtract(player.position());
        double pull = Math.min(1.4, 0.45 + (dist - radius) * 0.12);
        Vec3 v = to.multiply(1, 0, 1).normalize().scale(pull);
        player.setDeltaMovement(v.x, Math.max(player.getDeltaMovement().y, 0.28), v.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (TETHER_FX.getOrDefault(player.getUUID(), 0L) <= now) {
            TETHER_FX.put(player.getUUID(), now + 16);
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.7f);
            PacketDistributor.sendToPlayer(player, new AwakeningPayload(AwakeningPayload.TETHER, 0,
                    d.sealedPos().getX() + 0.5, d.sealedPos().getY() + GateBlockEntity.CENTER_Y, d.sealedPos().getZ() + 0.5));
        }
    }

    // ------------------------------------------------------------------ the trial

    /** The owner stepped through the sealed gate. */
    public static void arrived(ServerPlayer player, GateInstance g) {
        if (!player.getUUID().equals(g.owner())) return;
        AwakeningData d = data(player);
        if (d.stage() == Stage.DORMANT || d.stage() == Stage.CALLED) d.setStage(Stage.TRIAL);
        if (d.trial() == null) d.setSealedGate(g.id(), g.gateDimension(), g.gatePos());
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.TRIAL_ARRIVE));
    }

    public static void spawnArbiter(ServerLevel dungeon, GateInstance g) {
        ArbiterEntity arbiter = ModEntities.ARBITER.get().create(dungeon);
        if (arbiter == null) return;
        BlockPos c = g.bossRoomCenter();
        BlockPos from = g.entrance();
        for (DungeonLayout.Tunnel t : g.layout().tunnels()) {
            if (g.layout().rooms().get(t.to()).kind() == DungeonLayout.Kind.BOSS) from = g.world(t.start());
        }
        float yaw = (float) (Mth.atan2(from.getZ() - c.getZ(), from.getX() - c.getX()) * Mth.RAD_TO_DEG) - 90f;
        arbiter.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, yaw, 0f);
        arbiter.setYHeadRot(yaw);
        arbiter.setYBodyRot(yaw);
        arbiter.setGate(g.id());
        dungeon.addFreshEntity(arbiter);
        g.setBoss(arbiter.getUUID());
        g.setBossSpawned(true);
    }

    public static void wakeArbiter(ServerLevel dungeon, GateInstance g, ArbiterEntity arbiter) {
        arbiter.wake();
        for (ServerPlayer p : GateService.playersIn(dungeon, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.theme().ordinal(), arbiter.getId(), 0,
                    arbiter.getDisplayName(), Component.translatable("gate.beyondsolo.arbiter_subtitle")));
        }
    }

    @Nullable
    public static ArbiterEntity arbiter(ServerLevel dungeon, GateInstance g) {
        return g.boss() != null && dungeon.getEntity(g.boss()) instanceof ArbiterEntity a ? a : null;
    }

    /** The trial a player is standing in, if any. */
    @Nullable
    public static GateInstance trialAt(ServerPlayer player) {
        if (!GateService.isDungeon(player.level())) return null;
        GateInstance g = GateData.get(player.server).bySlotPos(player.blockPosition());
        return g != null && g.isTrial() ? g : null;
    }

    private static boolean inArbiterHall(ServerPlayer player, GateInstance g) {
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(room.center());
        double dx = player.getX() - c.getX(), dz = player.getZ() - c.getZ();
        return Math.sqrt(dx * dx + dz * dz) < room.reach() + 6 && Math.abs(player.getY() - c.getY()) < room.ry() + 8;
    }

    /** Nobody dies in the trial: the death is refused, and what happens instead depends on where they fell. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GateInstance g = trialAt(player);
        if (g == null) return;
        event.setCanceled(true);
        player.setHealth(1f);
        if (inSequence(player)) return;
        if (g.bossDefeated()) {
            begin(player, g, true);
        } else if (g.bossAwake() && inArbiterHall(player, g)) {
            begin(player, g, false);
        } else {
            revive(player, g);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (inSequence(player) || SHIELDED.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime()) event.setCanceled(true);
    }

    /** Fell on the way down: back on their feet at the last cavern they reached. */
    public static void revive(ServerPlayer player, GateInstance g) {
        ServerLevel dungeon = player.serverLevel();
        List<DungeonLayout.Room> rooms = g.layout().rooms();
        DungeonLayout.Room room = rooms.get(Mth.clamp(g.checkpoint(), 0, rooms.size() - 1));
        if (room.kind() == DungeonLayout.Kind.BOSS) room = rooms.get(Math.max(0, room.index() - 1));
        BlockPos at = g.world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
        restore(player);
        player.teleportTo(dungeon, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        if (player.distanceToSqr(Vec3.atBottomCenterOf(at)) > 4) player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        player.setDeltaMovement(Vec3.ZERO);
        SHIELDED.put(player.getUUID(), dungeon.getGameTime() + 60);
        dungeon.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.REVIVE));
    }

    private static void restore(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), 14));
        player.clearFire();
        player.setTicksFrozen(0);
        player.removeAllEffects();
        for (var type : Buildups.all()) Buildups.clear(player, type);
        player.fallDistance = 0;
    }

    // ------------------------------------------------------------------ the awakening

    /** Starts the awakening for a player in the trial: they fell to the Arbiter, or killed it. */
    public static void begin(ServerPlayer player, GateInstance g, boolean slain) {
        if (inSequence(player) || SystemAPI.hasSystem(player) && stage(player) == Stage.DONE) return;
        ServerLevel level = player.serverLevel();
        AwakeningData d = data(player);
        d.setStage(Stage.AWAKENING);
        d.setArbiterSlain(slain);
        if (d.trial() == null) d.setSealedGate(g.id(), g.gateDimension(), g.gatePos());
        SEQUENCES.put(player.getUUID(), new Sequence(level.getGameTime(), slain));
        freeze(player, true);
        player.setHealth(Math.max(1f, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        if (!slain) {
            ArbiterEntity arbiter = arbiter(level, g);
            if (arbiter != null) arbiter.passVerdict();
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.6f);
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.AWAKEN, slain ? 1 : 0));
    }

    private static void freeze(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(FREEZE);
            if (on) speed.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(FREEZE);
            if (on) jump.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void tickSequence(ServerPlayer player, Sequence seq) {
        long t = player.level().getGameTime() - seq.started;
        player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
        if (t == TELEPORT_TICK) wakeOutside(player);
        if (t == GRANT_TICK) bind(player, seq.slain);
        if (t >= END_TICK) {
            SEQUENCES.remove(player.getUUID());
            freeze(player, false);
            SHIELDED.put(player.getUUID(), player.level().getGameTime() + 100);
            FirstSpawnHandler.systemIntro(player, 30);
        }
    }

    /** While the screen is black: out of the trial, whole again, with everything they carried. */
    private static void wakeOutside(ServerPlayer player) {
        AwakeningData d = data(player);
        MinecraftServer server = player.server;
        collectLegendary(player);
        GateInstance g = d.trial() == null ? null : GateData.get(server).get(d.trial());
        if (g != null && g.state() == GateInstance.State.OPEN) {
            g.setState(GateInstance.State.CLEARED);
            g.setClearedAt(player.level().getGameTime());
            GateData.get(server).setDirty();
        }
        restore(player);
        if (!GateService.isDungeon(player.level()) || !player.level().dimension().equals(GateService.DIMENSION)) {
            if (player.server instanceof net.minecraft.gametest.framework.GameTestServer) closeSealedGate(server, d);
            return;
        }
        ServerLevel level = d.sealedDim() == null ? null : server.getLevel(d.sealedDim());
        Vec3 to = null;
        float yaw = player.getYRot();
        if (level != null && d.sealedPos() != null) {
            if (level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate) {
                to = gate.front();
                // their back to where the gate was: the world, not the hole, is the first thing they see
                yaw = gate.facing().toYRot();
            } else {
                to = Vec3.atBottomCenterOf(d.sealedPos());
            }
        }
        if (to == null) {
            level = server.overworld();
            BlockPos spawn = player.getRespawnPosition() != null && Level.OVERWORLD.equals(player.getRespawnDimension()) ? player.getRespawnPosition() : level.getSharedSpawnPos();
            to = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.teleportTo(level, to.x, to.y, to.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        closeSealedGate(server, d);
    }

    private static void bind(ServerPlayer player, boolean slain) {
        AwakeningData d = data(player);
        d.setStage(Stage.DONE);
        d.setSealedGate(d.trial(), null, null);
        SystemAPI.grantSystem(player);
        if (slain) SystemAPI.setTitle(player, SLAYER_TITLE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
    }

    /** Finishes an awakening at once (the player logged out halfway, the server stopped). */
    public static void finishNow(ServerPlayer player) {
        AwakeningData d = data(player);
        SEQUENCES.remove(player.getUUID());
        freeze(player, false);
        wakeOutside(player);
        bind(player, d.arbiterSlain());
    }

    // ------------------------------------------------------------------ the hidden way

    public static ItemStack rollLegendary(RandomSource r) {
        return new ItemStack(ModItems.LEGENDARIES.get(r.nextInt(ModItems.LEGENDARIES.size())).get());
    }

    static void arbiterSlain(ServerLevel level, ArbiterEntity arbiter, DamageSource source) {
        GateData gates = GateData.get(level.getServer());
        GateInstance g = arbiter.gateId() == null ? null : gates.get(arbiter.gateId());
        if (g == null || g.bossDefeated()) return;
        g.setBossDefeated(true);
        gates.setDirty();
        ServerPlayer owner = g.owner() == null ? null : level.getServer().getPlayerList().getPlayer(g.owner());
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, AwakeningPayload.of(AwakeningPayload.SLAIN));
        }
        if (owner != null) {
            data(owner).setArbiterSlain(true);
            SHIELDED.put(owner.getUUID(), level.getGameTime() + ArbiterEntity.DEATH_TICKS + 20 * 30);
        }
        BlockPos center = arbiter.blockPosition();
        UUID ownerId = g.owner();
        // once it has crumbled, its cache comes down where it stood
        ServerScheduler.schedule(ArbiterEntity.DEATH_TICKS + 10, () -> {
            BlockPos at = center;
            for (int i = 0; i < 6 && !level.getBlockState(at).isAir(); i++) at = at.above();
            for (int i = 0; i < 8 && level.getBlockState(at.below()).isAir(); i++) at = at.below();
            ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
            Direction facing = p == null ? Direction.NORTH : Direction.getNearest(p.getX() - at.getX(), 0, p.getZ() - at.getZ());
            if (facing.getAxis().isVertical()) facing = Direction.NORTH;
            level.setBlock(at, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
            if (level.getBlockEntity(at) instanceof RewardCacheBlockEntity cache) cache.setupLegendary(ownerId, level.getGameTime());
            BlockPos cachePos = at;
            // it opens by itself if nobody dares touch it
            ServerScheduler.schedule(20 * 5, () -> {
                if (level.getBlockEntity(cachePos) instanceof RewardCacheBlockEntity cache && !cache.isOpen()) cache.open(null);
            });
        });
    }

    /** The legendary cache burst: the weapon is out, and the awakening follows. */
    public static void legendaryOut(ServerLevel level, @Nullable UUID ownerId, ItemEntity item) {
        if (ownerId == null) return;
        item.getPersistentData().putUUID(LEGENDARY_TAG, ownerId);
        item.setTarget(ownerId);
        item.setUnlimitedLifetime();
        ServerScheduler.schedule(90, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(ownerId);
            if (p == null) return;
            GateInstance g = trialAt(p);
            if (g != null) begin(p, g, true);
        });
    }

    /** Whatever of the legendary is still lying in the hall goes with them. */
    private static void collectLegendary(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(80),
                e -> e.getPersistentData().hasUUID(LEGENDARY_TAG) && e.getPersistentData().getUUID(LEGENDARY_TAG).equals(player.getUUID()))) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Sequence seq = SEQUENCES.get(player.getUUID());
        if (seq != null) {
            tickSequence(player, seq);
            return;
        }
        if (player.tickCount % 5 != 0) return;
        AwakeningData d = data(player);
        switch (d.stage()) {
            case CALLED -> tether(player, d);
            case TRIAL -> {
                // out of the trial some other way (a command, the cave closing): the gate takes them back
                if (player.tickCount % 40 == 0 && trialAt(player) == null && !GateService.inTransit(player) && !SystemAPI.hasSystem(player)) {
                    d.setStage(Stage.CALLED);
                    GateInstance g = d.trial() == null ? null : GateData.get(player.server).get(d.trial());
                    if (g == null || g.state() != GateInstance.State.OPEN) d.setSealedGate(null, null, null);
                }
            }
            case DORMANT -> {
                if (SystemAPI.hasSystem(player)) d.setStage(Stage.DONE);
            }
            default -> {}
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AwakeningData d = data(player);
        if (d.stage() == Stage.AWAKENING) {
            finishNow(player);
            FirstSpawnHandler.systemIntro(player, 80);
        }
        if (!Config.AWAKENING_TRIAL.get() && !SystemAPI.hasSystem(player) && d.stage() == Stage.DORMANT) {
            d.setStage(Stage.DONE);
            SystemAPI.grantSystem(player);
        }
        if (!SystemAPI.hasSystem(player) && !d.hinted()) {
            d.setHinted(true);
            ServerScheduler.schedule(100, () -> {
                if (!player.hasDisconnected()) player.sendSystemMessage(Component.translatable("message.beyondsolo.presystem.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            });
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SEQUENCES.containsKey(player.getUUID())) finishNow(player);
        SHIELDED.remove(player.getUUID());
        TETHER_FX.remove(player.getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SEQUENCES.clear();
        SHIELDED.clear();
        TETHER_FX.clear();
    }

    /** Back to the very start (commands, tests): no System, not called. */
    public static void reset(ServerPlayer player) {
        AwakeningData d = data(player);
        closeSealedGate(player.server, d);
        // the trial behind it goes too, built or not, so the next call starts a new one
        GateInstance old = d.trial() == null ? null : GateData.get(player.server).get(d.trial());
        if (old != null && old.state() == GateInstance.State.OPEN) {
            old.setState(GateInstance.State.CLOSED);
            GateData.get(player.server).setDirty();
        }
        SEQUENCES.remove(player.getUUID());
        freeze(player, false);
        d.setStage(Stage.DORMANT);
        d.setSealedGate(null, null, null);
        d.setArbiterSlain(false);
        d.setReadyAt(-1);
    }

    private AwakeningService() {}
}
