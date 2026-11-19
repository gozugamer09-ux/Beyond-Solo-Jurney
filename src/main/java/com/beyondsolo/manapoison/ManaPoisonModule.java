package com.beyondsolo.manapoison;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import com.beyondsolo.crafting.SystemPotionItem;
import com.beyondsolo.network.ManaPoisonPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers mana poisoning ({@link ManaPoisoning}): a hunter's poisoning ({@code beyondsolo:mana_poisoning}, gone at
 * death), the purifying crystal, the purifying draught, Clear Breath, the meter's message and the settings
 * ({@code beyondsolo-mana.toml}). Lang lives in {@code assets/beyondsolo_mana/lang}; models and textures come from
 * {@code tools/gen_reward_assets.py}. Called once from the mod constructor.
 */
public final class ManaPoisonModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** How poisoned a hunter is. Not kept through death. */
    public static final Supplier<AttachmentType<ManaPoisonData>> DATA = ATTACHMENTS.register("mana_poisoning",
            () -> AttachmentType.serializable(ManaPoisonData::new).build());

    /** A purifying crystal. Unbreakable; grown into high-rank gates ({@link ManaCrystals}). */
    public static final DeferredBlock<PurifyingCrystalBlock> CRYSTAL = BLOCKS.register("purifying_crystal",
            () -> new PurifyingCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));
    public static final DeferredItem<BlockItem> CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem(CRYSTAL, new Item.Properties().rarity(Rarity.EPIC));

    /** Washes out half the poisoning and keeps the mana out for a while (and lifts a cursed box's hex). */
    public static final DeferredItem<SystemPotionItem> DRAUGHT = ITEMS.register("purifying_draught",
            () -> new SystemPotionItem(0x7FE8DA, 24, ManaPoisoning::purify, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    /** A purifying draught's afterglow: the gate's mana does not build up. */
    public static final DeferredHolder<MobEffect, MobEffect> CLEAR_BREATH = EFFECTS.register("clear_breath",
            () -> new CombatStatusEffect(MobEffectCategory.BENEFICIAL, 0x7FE8DA, Set.of(), 0, (e, amp) -> {}));

    public static void init(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(ManaPoisonModule::payloads);
        modBus.addListener(ManaPoisonModule::creativeTab);
        container.registerConfig(ModConfig.Type.COMMON, ManaPoisonConfig.SPEC, "beyondsolo-mana.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ManaPoisonPayload.TYPE, ManaPoisonPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.manapoison.ManaPoisonClient.onSync(p)));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(BeyondSolo.id("combat"))) return;
        event.accept(DRAUGHT.get());
        event.accept(CRYSTAL_ITEM.get());
    }

    private ManaPoisonModule() {}
}
