package com.beyondsolo.isle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.IslePayload;
import com.beyondsolo.worldboss.ColossalBoss;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the Halcyon raid ({@link IsleRaids}): the hive's queen and king (great threats of their own tier,
 * {@link com.beyondsolo.worldboss.WorldBossKind.Tier#HIVE}: their fights, bars and banners are the world bosses'), the
 * nests' hearts, the hunter's record ({@code beyondsolo:halcyon}), the raid's message to the HUD and the settings
 * ({@code beyondsolo-isle.toml}). The island's world is {@code data/beyondsolo/dimension/halcyon_isle.json}. Lang lives
 * in {@code assets/beyondsolo_isle/lang}; models, animations and skins come from {@code tools/gen_isle_assets.py}.
 * Called once from the mod constructor.
 */
public final class IsleModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's record of the Halcyon raids, and where to send them back while they are on the island. Survives death. */
    public static final Supplier<AttachmentType<IsleRecord>> RECORD = ATTACHMENTS.register("halcyon",
            () -> AttachmentType.serializable(IsleRecord::new).copyOnDeath().build());

    /** Vessa, Mother of the Hive. */
    public static final DeferredHolder<EntityType<?>, EntityType<QueenBoss>> QUEEN = ENTITY_TYPES.register("hive_queen",
            () -> EntityType.Builder.<QueenBoss>of(QueenBoss::new, MobCategory.MONSTER)
                    .sized(4.2f, 4.6f).eyeHeight(3.9f).fireImmune().clientTrackingRange(16).updateInterval(2).build("hive_queen"));
    /** Azhrak, the Hive King. */
    public static final DeferredHolder<EntityType<?>, EntityType<KingBoss>> KING = ENTITY_TYPES.register("hive_king",
            () -> EntityType.Builder.<KingBoss>of(KingBoss::new, MobCategory.MONSTER)
                    .sized(1.5f, 3.9f).eyeHeight(3.4f).fireImmune().clientTrackingRange(16).updateInterval(2).build("hive_king"));
    /** The beating heart of a nest: it has to burst before the nest burns. */
    public static final DeferredHolder<EntityType<?>, EntityType<NestHeartEntity>> NEST_HEART = ENTITY_TYPES.register("nest_heart",
            () -> EntityType.Builder.<NestHeartEntity>of(NestHeartEntity::new, MobCategory.MISC)
                    .sized(1.8f, 2.2f).eyeHeight(1.2f).fireImmune().clientTrackingRange(12).build("nest_heart"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(IsleModule::attributes);
        modBus.addListener(IsleModule::payloads);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> IsleJournal.register());
        container.registerConfig(ModConfig.Type.COMMON, IsleConfig.SPEC, "beyondsolo-isle.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(QUEEN.get(), bossAttributes(0.2).build());
        event.put(KING.get(), bossAttributes(0.34).build());
        event.put(NEST_HEART.get(), NestHeartEntity.attributes().build());
    }

    /** A boss of the hive: its own numbers are set from its kind as it is made ({@link ColossalBoss#setupStats}). */
    static AttributeSupplier.Builder bossAttributes(double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(IslePayload.TYPE, IslePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.isle.IsleClient.onPayload(p)));
    }

    private IsleModule() {}
}
