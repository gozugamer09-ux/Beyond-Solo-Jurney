package com.beyondsolo.association;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.network.AssociationSyncPayload;
import net.minecraft.core.registries.Registries;
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
 * Registers the Hunter Association ({@link Association}) and the gate broker ({@link Broker}): the officer and the
 * broker kept at every Evaluation Zone ({@link AssociationPosts}), a hunter's file ({@code beyondsolo:association}),
 * the windows' messages, the titles and the settings ({@code beyondsolo-association.toml}). Lang lives in
 * {@code assets/beyondsolo_association/lang}; the skins come from {@code tools/gen_association_assets.py}. Called once
 * from the mod constructor.
 */
public final class AssociationModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's file at the association. Survives death. */
    public static final Supplier<AttachmentType<AssociationRecord>> RECORD = ATTACHMENTS.register("association",
            () -> AttachmentType.serializable(AssociationRecord::new).copyOnDeath().build());

    /** The officer who keeps an association office. */
    public static final DeferredHolder<EntityType<?>, EntityType<AssociationNpc>> OFFICER = ENTITY_TYPES.register("association_officer",
            () -> EntityType.Builder.<AssociationNpc>of(AssociationNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("association_officer"));
    /** The gate broker who hangs about an office. */
    public static final DeferredHolder<EntityType<?>, EntityType<AssociationNpc>> BROKER = ENTITY_TYPES.register("gate_broker",
            () -> EntityType.Builder.<AssociationNpc>of(AssociationNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("gate_broker"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(AssociationModule::attributes);
        modBus.addListener(AssociationModule::payloads);
        titles();
        container.registerConfig(ModConfig.Type.COMMON, AssociationConfig.SPEC, "beyondsolo-association.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(OFFICER.get(), AssociationNpc.attributes().build());
        event.put(BROKER.get(), AssociationNpc.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef("association_certified", TitleCategory.SYSTEM, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.04f)), false));
        Titles.register(new TitleDef("association_bounty_hunter", TitleCategory.GATES, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.05f), new PerkLine(TitlePerk.CREATURE_DAMAGE, 0.04f)), false));
        Titles.register(new TitleDef("pride_of_the_association", TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.08f), new PerkLine(TitlePerk.XP, 0.05f)), false));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(AssociationSyncPayload.TYPE, AssociationSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.association.AssociationClient.onSync(p)));
        r.playToServer(AssociationActionPayload.TYPE, AssociationActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Association.handle(player, p);
                }));
    }

    private AssociationModule() {}
}
