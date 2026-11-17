package com.beyondsolo.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.DepthsFxPayload;
import com.beyondsolo.network.DepthsPayload;
import com.beyondsolo.network.WeaknessPayload;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The depths of a gate: weather ({@link GateWeather}, {@link WeatherService}), hidden chambers ({@link HiddenRooms}),
 * trap rooms ({@link TrapRooms}) and the weaknesses a player has found ({@link WeaknessLog}). Registers the blocks
 * (the hidden wall, the trap core, the lockdown's wall of light), the weakness log and the network messages. Called
 * once from the mod constructor. Lang lives in {@code assets/beyondsolo_depths/lang}; icons, block models and
 * textures come from {@code tools/gen_depths_assets.py}.
 */
public final class DepthsModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** The plug of rock across the way into a hidden chamber. Unbreakable; knocked down ({@link HiddenRooms#knock}). */
    public static final DeferredBlock<HiddenSealBlock> HIDDEN_SEAL = BLOCKS.register("hidden_seal",
            () -> new HiddenSealBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)));

    /** A trap's heart. Unbreakable; struck to pieces ({@link TrapRooms#hitCore}). */
    public static final DeferredBlock<TrapCoreBlock> TRAP_CORE = BLOCKS.register("trap_core",
            () -> new TrapCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 9)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    /** The lockdown's wall of light across a tunnel. */
    public static final DeferredBlock<TrapWardBlock> TRAP_WARD = BLOCKS.register("trap_ward",
            () -> new TrapWardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 11)
                    .sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isRedstoneConductor((s, l, p) -> false)));

    /** The weaknesses a player has found, by kind of creature. Survives death. */
    public static final Supplier<AttachmentType<WeaknessLog>> WEAKNESSES = ATTACHMENTS.register("weaknesses",
            () -> AttachmentType.serializable(WeaknessLog::new).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(DepthsModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DepthsPayload.TYPE, DepthsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.depths.DepthsClient.on(p)));
        r.playToClient(DepthsFxPayload.TYPE, DepthsFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.depths.DepthsWorldFx.play(p)));
        r.playToClient(WeaknessPayload.TYPE, WeaknessPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.depths.DepthsClient.onWeaknesses(p)));
    }

    private DepthsModule() {}
}
