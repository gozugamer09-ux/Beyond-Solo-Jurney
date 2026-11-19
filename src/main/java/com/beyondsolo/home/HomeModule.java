package com.beyondsolo.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import com.beyondsolo.network.HomeActionPayload;
import com.beyondsolo.network.HomeEventPayload;
import com.beyondsolo.network.HomeSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers the hunter's residence ({@link Homes}): the hunter's record of their home ({@code beyondsolo:residence}),
 * the training dummy, the trophy pedestal, Well Rested, the HOME page's messages and the settings
 * ({@code beyondsolo-home.toml}). The homes' world is {@code data/beyondsolo/dimension/residence.json}. Lang lives in
 * {@code assets/beyondsolo_home/lang}; the dummy's model, the pedestal and the icons come from
 * {@code tools/gen_home_assets.py}. Called once from the mod constructor.
 */
public final class HomeModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondSolo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);

    /** A hunter's home: how much of it they own, where they go back to, their rest and their best. Survives death. */
    public static final Supplier<AttachmentType<HomeRecord>> RECORD = ATTACHMENTS.register("residence",
            () -> AttachmentType.serializable(HomeRecord::new).copyOnDeath().build());

    /** A training dummy of the Training Hall. */
    public static final DeferredHolder<EntityType<?>, EntityType<TrainingDummy>> DUMMY = ENTITY_TYPES.register("training_dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC)
                    .sized(0.8f, 2.0f).eyeHeight(1.7f).clientTrackingRange(10).build("training_dummy"));

    /** A pedestal of the trophy hall. Unbreakable: the builder places them. */
    public static final DeferredBlock<TrophyPedestalBlock> PEDESTAL = BLOCKS.register("trophy_pedestal",
            () -> new TrophyPedestalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<TrophyPedestalBlockEntity>> PEDESTAL_ENTITY = BLOCK_ENTITIES.register("trophy_pedestal",
            () -> BlockEntityType.Builder.of(TrophyPedestalBlockEntity::new, PEDESTAL.get()).build(null));

    /** What Well Rested gives (first guesses for the balancing pass). */
    public static final double RESTED_FOCUS = 0.05, RESTED_HASTE = 0.05, RESTED_STAMINA = 0.15;

    /** A night in the hunter's own bed at home: skills a little cheaper and quicker, and slower to tire. */
    public static final DeferredHolder<MobEffect, MobEffect> WELL_RESTED = EFFECTS.register("well_rested",
            () -> new CombatStatusEffect(MobEffectCategory.BENEFICIAL, 0x9FD3FF, Set.of(), 0, (e, amp) -> {})
                    .addAttributeModifier(ModAttributes.MANA_EFFICIENCY, BeyondSolo.id("effect.well_rested.focus"), RESTED_FOCUS,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(ModAttributes.SKILL_HASTE, BeyondSolo.id("effect.well_rested.haste"), RESTED_HASTE,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(ModAttributes.EXHAUSTION_RESISTANCE, BeyondSolo.id("effect.well_rested.stamina"), RESTED_STAMINA,
                            AttributeModifier.Operation.ADD_VALUE));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(HomeModule::attributes);
        modBus.addListener(HomeModule::payloads);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> HomeJournal.register());
        container.registerConfig(ModConfig.Type.COMMON, HomeConfig.SPEC, "beyondsolo-home.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DUMMY.get(), TrainingDummy.attributes().build());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(HomeSyncPayload.TYPE, HomeSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.home.HomeClient.onSync(p)));
        r.playToClient(HomeEventPayload.TYPE, HomeEventPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.home.HomeClient.onEvent(p)));
        r.playToServer(HomeActionPayload.TYPE, HomeActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Homes.handle(player, p);
                }));
    }

    private HomeModule() {}
}
