package com.beyondsolo.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.ShadowActionPayload;
import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.network.ShadowSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the shadow army: the necromancer's army data, the shadow entity, its network messages and tags.
 * Called once from the mod constructor.
 */
public final class ShadowModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);

    /** Every shadow a player has extracted. Survives death. */
    public static final Supplier<AttachmentType<ShadowData>> ARMY = ATTACHMENTS.register("shadow_army",
            () -> AttachmentType.serializable(ShadowData::new).copyOnDeath().build());

    /** A shadow in the world. Its size follows the creature it was ({@link ShadowEntity#getDefaultDimensions}). */
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowEntity>> SHADOW = ENTITY_TYPES.register("shadow",
            () -> EntityType.Builder.<ShadowEntity>of(ShadowEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).eyeHeight(1.7f).clientTrackingRange(10).fireImmune().build("shadow"));

    /** Creatures whose shadows carry their master. */
    public static final TagKey<EntityType<?>> MOUNTS = tag("shadow/mounts");
    /** Creatures whose shadows fight from range with bolts. */
    public static final TagKey<EntityType<?>> CASTERS = tag("shadow/casters");
    /** Creatures that become named elite shadows (dungeon bosses always do). */
    public static final TagKey<EntityType<?>> BOSSES = tag("shadow/bosses");
    /** Creatures that leave no soul to extract. */
    public static final TagKey<EntityType<?>> NO_SOUL = tag("shadow/no_soul");

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, BeyondSolo.id(path));
    }

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ShadowModule::payloads);
        modBus.addListener(ShadowModule::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SHADOW.get(), ShadowEntity.attributes().build());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ShadowSyncPayload.TYPE, ShadowSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.shadow.ShadowClient.onSync(p)));
        r.playToClient(ShadowFxPayload.TYPE, ShadowFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.shadow.ShadowClient.onFx(p)));
        r.playToServer(ShadowActionPayload.TYPE, ShadowActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) ShadowService.action(player, p.action(), p.shadow());
                }));
    }

    private ShadowModule() {}
}
