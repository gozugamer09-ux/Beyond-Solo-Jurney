package com.beyondsolo.raid;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.RaidActionPayload;
import com.beyondsolo.network.RaidEventPayload;
import com.beyondsolo.network.RaidSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the raids: the pylons of a raid boss's barrier, the raid's network messages and its settings
 * ({@code beyondsolo-raid.toml}). Called once from the mod constructor.
 */
public final class RaidModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);

    /** A crystal of a raid boss's barrier, pouring light into it through a beam. */
    public static final DeferredHolder<EntityType<?>, EntityType<RaidPylonEntity>> PYLON = ENTITY_TYPES.register("raid_pylon",
            () -> EntityType.Builder.<RaidPylonEntity>of(RaidPylonEntity::new, MobCategory.MISC)
                    .sized(2.0f, 2.0f).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE).fireImmune().build("raid_pylon"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(RaidModule::payloads);
        container.registerConfig(ModConfig.Type.COMMON, RaidConfig.SPEC, "beyondsolo-raid.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RaidSyncPayload.TYPE, RaidSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.raid.RaidClient.onSync(p)));
        r.playToClient(RaidEventPayload.TYPE, RaidEventPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.raid.RaidClient.onEvent(p)));
        r.playToServer(RaidActionPayload.TYPE, RaidActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Raids.handle(player, p);
                }));
    }

    private RaidModule() {}
}
