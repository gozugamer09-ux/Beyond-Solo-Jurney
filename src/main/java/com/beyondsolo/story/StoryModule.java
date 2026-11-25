package com.beyondsolo.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import com.beyondsolo.network.StoryLinePayload;
import com.beyondsolo.network.StorySkipPayload;
import com.beyondsolo.system.Balance;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers the hunter's own story: the System's story ({@link MainStory}: its chapters, their father
 * {@link FatherEntity}, the visions it shows {@link VisionEntity}), their mother ({@link KinEntity}, {@link Kin}), the Long Sleep's quests and things
 * ({@link LongSleep}: Elder Root Dew, the Crimson King's chalice, the Elixir of Waking), her home-cooked stew and its
 * Home Comfort, the story's scenes ({@link StoryScenes}), its title and settings ({@code beyondsolo-story.toml}). Lang
 * lives in {@code assets/beyondsolo_story/lang}; skins and icons come from {@code tools/gen_story_assets.py}. Called
 * once from the mod constructor.
 */
public final class StoryModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** The hunter's story: how far it has come, where their mother is. Kept through death. */
    public static final Supplier<AttachmentType<KinRecord>> RECORD = ATTACHMENTS.register("kin",
            () -> AttachmentType.serializable(KinRecord::new).copyOnDeath().build());

    /** The System's own story for the hunter ({@link MainStory}). Kept through death. */
    public static final Supplier<AttachmentType<MainRecord>> MAIN = ATTACHMENTS.register("main_story",
            () -> AttachmentType.serializable(MainRecord::new).copyOnDeath().build());

    /** The hunter's father, the light's vessel, at their side in the story's fights. */
    public static final DeferredHolder<EntityType<?>, EntityType<FatherEntity>> FATHER = ENTITY_TYPES.register("hunters_father",
            () -> EntityType.Builder.<FatherEntity>of(FatherEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("hunters_father"));

    /** What a story scene shows the hunter that is not really there (the King of Night). */
    public static final DeferredHolder<EntityType<?>, EntityType<VisionEntity>> VISION = ENTITY_TYPES.register("story_vision",
            () -> EntityType.Builder.<VisionEntity>of(VisionEntity::new, MobCategory.MISC)
                    .sized(0.7f, 2.2f).eyeHeight(1.95f).clientTrackingRange(10).noSave().build("story_vision"));

    /** The hunter's mother. */
    public static final DeferredHolder<EntityType<?>, EntityType<KinEntity>> MOTHER = ENTITY_TYPES.register("hunters_mother",
            () -> EntityType.Builder.<KinEntity>of(KinEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("hunters_mother"));

    /** The cure's first ingredient: found in the hidden chamber of a high-rank gate. */
    public static final DeferredItem<StoryItem> ELDER_ROOT_DEW = ITEMS.register("elder_root_dew",
            () -> new StoryItem(0xA6E57E, false, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    /** The cure's second: what the Crimson Citadel's lord keeps. */
    public static final DeferredItem<StoryItem> CRIMSON_CHALICE = ITEMS.register("crimson_kings_chalice",
            () -> new StoryItem(0xF0566E, false, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** The cure itself. */
    public static final DeferredItem<StoryItem> ELIXIR = ITEMS.register("elixir_of_waking",
            () -> new StoryItem(0xFFD98A, true, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The share of max health Home Comfort adds. */
    public static final double COMFORT_HEALTH = 0.1;
    /** How long a bowl of her stew keeps the hunter warm. */
    public static final int COMFORT_TICKS = 20 * 60 * 20;

    /** A bowl of her stew inside: more health for a while, and a little of it back now and then. */
    public static final DeferredHolder<MobEffect, MobEffect> HOME_COMFORT = EFFECTS.register("home_comfort",
            () -> new CombatStatusEffect(MobEffectCategory.BENEFICIAL, 0xF2B46B, Set.of(), 100,
                    (e, amp) -> {
                        Balance.regen(e, e.getMaxHealth() * 0.01f);
                    })
                    .addAttributeModifier(Attributes.MAX_HEALTH, BeyondSolo.id("effect.home_comfort"), COMFORT_HEALTH,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** What she cooks once a day once she is awake. */
    public static final DeferredItem<StoryItem> HOME_STEW = ITEMS.register("home_cooked_stew",
            () -> new StoryItem(0xF2B46B, false, new Item.Properties().stacksTo(1)
                    .food(new FoodProperties.Builder().nutrition(10).saturationModifier(1.2f).alwaysEdible()
                            .effect(() -> new MobEffectInstance(HOME_COMFORT, COMFORT_TICKS, 0), 1f)
                            .usingConvertsTo(Items.BOWL).build())));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(StoryModule::attributes);
        modBus.addListener(StoryModule::payloads);
        modBus.addListener(StoryModule::creativeTab);
        titles();
        LongSleep.registerTemplates();
        MainStory.registerTemplates();
        container.registerConfig(ModConfig.Type.COMMON, StoryConfig.SPEC, "beyondsolo-story.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(MOTHER.get(), KinEntity.attributes().build());
        event.put(FATHER.get(), com.beyondsolo.guild.GuildHunterEntity.attributes().build());
        event.put(VISION.get(), VisionEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef(LongSleep.TITLE_ID, TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.VITALITY, 0.06f), new PerkLine(TitlePerk.XP, 0.05f)), false));
        Titles.register(new TitleDef(MainStory.HEIR_ID, TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.MIGHT, 0.04f), new PerkLine(TitlePerk.FOCUS, 0.04f)), false));
        Titles.register(new TitleDef(MainStory.BEYOND_ID, TitleCategory.SYSTEM, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.MIGHT, 0.05f), new PerkLine(TitlePerk.VITALITY, 0.05f), new PerkLine(TitlePerk.XP, 0.05f)), false));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(StoryLinePayload.TYPE, StoryLinePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.story.StoryClient.onLine(p)));
        r.playToServer(StorySkipPayload.TYPE, StorySkipPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) StoryScenes.skip(player);
                }));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(BeyondSolo.id("combat"))) return;
        event.accept(ELDER_ROOT_DEW.get());
        event.accept(CRIMSON_CHALICE.get());
        event.accept(ELIXIR.get());
        event.accept(HOME_STEW.get());
    }

    private StoryModule() {}
}
