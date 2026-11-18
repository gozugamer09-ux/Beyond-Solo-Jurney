package com.beyondsolo.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.MonarchActionPayload;
import com.beyondsolo.network.MonarchFxPayload;
import com.beyondsolo.network.MonarchSyncPayload;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestTemplates;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * Registers the monarch path: the hunter's record ({@code beyondsolo:monarch}), the Arbiter Unchained who guards the
 * throne, the network messages, the settings ({@code beyondsolo-monarch.toml}), the throne's quest and the titles a
 * monarch earns. Called once from the mod constructor.
 */
public final class MonarchModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);

    /** Per-player monarch state. Survives death. */
    public static final Supplier<AttachmentType<MonarchData>> MONARCH = ATTACHMENTS.register("monarch",
            () -> AttachmentType.serializable(MonarchData::new).copyOnDeath().build());

    /** The Arbiter Unchained, the guardian of the empty throne (the awakening's giant, its chains broken). */
    public static final DeferredHolder<EntityType<?>, EntityType<ThroneArbiterEntity>> THRONE_ARBITER = ENTITY_TYPES.register("throne_arbiter",
            () -> EntityType.Builder.<ThroneArbiterEntity>of(ThroneArbiterEntity::new, MobCategory.MONSTER)
                    .sized(3.4f, 10.5f).eyeHeight(9.2f).fireImmune().clientTrackingRange(16).build("throne_arbiter"));

    /** The throne's quest: claim the empty throne. */
    public static final String QUEST_TEMPLATE = BeyondSolo.id("monarch/throne").toString();
    /** Quest progress key the throne trial advances when the crown is taken. */
    public static final String THRONE_KEY = BeyondSolo.id("monarch_throne").toString();

    /** Titles: one per crown, and three a monarch earns. */
    public static final String UNBLEMISHED = TitleDef.PREFIX + "unblemished_crown";
    public static final String CROWN_AGAINST_CROWN = TitleDef.PREFIX + "crown_against_crown";
    public static final String DOMAIN_UNBOWED = TitleDef.PREFIX + "domain_unbowed";

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(MonarchModule::payloads);
        modBus.addListener(MonarchModule::attributes);
        container.registerConfig(ModConfig.Type.COMMON, MonarchConfig.SPEC, "beyondsolo-monarch.toml");
        QuestTemplates.register(QUEST_TEMPLATE, (p, c) -> QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.SET, p)
                .fixed()
                .text("monarch_throne")
                .custom(THRONE_KEY, 1, Component.translatable("quest_objective.beyondsolo.monarch_throne"))
                .xp(20000).statPoints(10)
                .build());
        titles();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(THRONE_ARBITER.get(), ThroneArbiterEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        for (Dominion d : Dominion.values()) {
            Titles.register(new TitleDef("monarch_of_" + d.id(), TitleCategory.SYSTEM, TitleTier.MYTHIC, null, 0,
                    List.of(new PerkLine(TitlePerk.MIGHT, 0.10f), new PerkLine(TitlePerk.VITALITY, 0.10f), new PerkLine(TitlePerk.ELEMENTAL, 0.10f)), false));
        }
        Titles.register(new TitleDef("unblemished_crown", TitleCategory.SYSTEM, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.GUARD, 0.08f), new PerkLine(TitlePerk.SWIFTNESS, 0.06f), new PerkLine(TitlePerk.MIGHT, 0.06f)), true));
        Titles.register(new TitleDef("crown_against_crown", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.12f), new PerkLine(TitlePerk.GIANT_DAMAGE, 0.10f), new PerkLine(TitlePerk.MIGHT, 0.06f)), false));
        Titles.register(new TitleDef("domain_unbowed", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.GUARD, 0.10f), new PerkLine(TitlePerk.VITALITY, 0.08f)), true));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(MonarchSyncPayload.TYPE, MonarchSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.monarch.MonarchClient.onSync(p)));
        r.playToClient(MonarchFxPayload.TYPE, MonarchFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.monarch.MonarchClient.onFx(p)));
        r.playToServer(MonarchActionPayload.TYPE, MonarchActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) MonarchAPI.handle(player, p);
                }));
    }

    private MonarchModule() {}
}
