package com.beyondsolo.ruler;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers the rulers of light ({@link Rulers}): the three rulers and their heralds, the Mote of Radiance, the
 * Blessing of Light, the hunter's record ({@code beyondsolo:rulers}) and the settings ({@code beyondsolo-rulers.toml}).
 * The rulers are great threats of their own tier ({@link com.beyondsolo.worldboss.WorldBossKind.Tier#RULER}): their
 * fights, bars and banners are the world bosses'. Lang lives in {@code assets/beyondsolo_rulers/lang}; models,
 * animations and skins come from {@code tools/gen_ruler_assets.py}. Called once from the mod constructor.
 */
public final class RulerModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's record with the rulers. Survives death. */
    public static final Supplier<AttachmentType<RulerRecord>> RECORD = ATTACHMENTS.register("rulers",
            () -> AttachmentType.serializable(RulerRecord::new).copyOnDeath().build());

    public static final DeferredHolder<EntityType<?>, EntityType<VerdictRuler>> VERDICT = ENTITY_TYPES.register("ruler_verdict",
            () -> EntityType.Builder.<VerdictRuler>of(VerdictRuler::new, MobCategory.MONSTER)
                    .sized(1.5f, 4.2f).eyeHeight(3.7f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ruler_verdict"));
    public static final DeferredHolder<EntityType<?>, EntityType<WingsRuler>> WINGS = ENTITY_TYPES.register("ruler_wings",
            () -> EntityType.Builder.<WingsRuler>of(WingsRuler::new, MobCategory.MONSTER)
                    .sized(1.4f, 4.0f).eyeHeight(3.5f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ruler_wings"));
    public static final DeferredHolder<EntityType<?>, EntityType<HandRuler>> HAND = ENTITY_TYPES.register("ruler_hand",
            () -> EntityType.Builder.<HandRuler>of(HandRuler::new, MobCategory.MONSTER)
                    .sized(1.4f, 4.4f).eyeHeight(3.9f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ruler_hand"));
    /** A ruler's herald: a lesser being of light that comes down with it. */
    public static final DeferredHolder<EntityType<?>, EntityType<HeraldEntity>> HERALD = ENTITY_TYPES.register("ruler_herald",
            () -> EntityType.Builder.<HeraldEntity>of(HeraldEntity::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.6f).eyeHeight(2.25f).fireImmune().clientTrackingRange(10).build("ruler_herald"));

    /** A mote of a ruler's light: a ruler leaves them to the hunters who fought with it, or felled it. Use one to be blessed. */
    public static final DeferredItem<MoteItem> MOTE = ITEMS.register("mote_of_radiance",
            () -> new MoteItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    /** A ruler's favour: harder blows on monarchs and their armies, and a monarch's presence weighs nothing. */
    public static final DeferredHolder<MobEffect, MobEffect> BLESSING = EFFECTS.register("blessing_of_light",
            () -> new CombatStatusEffect(MobEffectCategory.BENEFICIAL, 0xFFE7A0, Set.of(), 0, (e, amp) -> {}));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RulerModule::attributes);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> RulerJournal.register());
        container.registerConfig(ModConfig.Type.COMMON, RulerConfig.SPEC, "beyondsolo-rulers.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VERDICT.get(), RulerBoss.attributes().build());
        event.put(WINGS.get(), RulerBoss.attributes().build());
        event.put(HAND.get(), RulerBoss.attributes().build());
        event.put(HERALD.get(), HeraldEntity.attributes().build());
    }

    private RulerModule() {}
}
