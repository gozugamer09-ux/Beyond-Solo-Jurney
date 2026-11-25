package com.beyondsolo.duel;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.DuelPayload;
import net.minecraft.core.registries.Registries;
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
 * Registers the duels with the national-level hunters ({@link Duels}): the challenger, a hunter's duel record
 * ({@code beyondsolo:duels}, kept through death), the duel's message, the titles and the settings
 * ({@code beyondsolo-duels.toml}). The challenge's window rides on the association's messages. Lang lives in
 * {@code assets/beyondsolo_duels/lang}; the four's skins come from {@code tools/gen_duel_assets.py}. Called once from
 * the mod constructor.
 */
public final class DuelModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's duels with the national-level hunters and the challenge standing. Survives death. */
    public static final Supplier<AttachmentType<DuelRecord>> RECORD = ATTACHMENTS.register("duels",
            () -> AttachmentType.serializable(DuelRecord::new).copyOnDeath().build());

    /** A national-level hunter come for a duel: shaped like a person, their own gear and weapon. */
    public static final DeferredHolder<EntityType<?>, EntityType<DuelistEntity>> DUELIST = ENTITY_TYPES.register("national_hunter",
            () -> EntityType.Builder.<DuelistEntity>of(DuelistEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("national_hunter"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(DuelModule::attributes);
        modBus.addListener(DuelModule::payloads);
        titles();
        container.registerConfig(ModConfig.Type.COMMON, DuelConfig.SPEC, "beyondsolo-duels.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DUELIST.get(), DuelistEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef("duelist", TitleCategory.SYSTEM, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.XP, 0.04f)), false));
        Titles.register(new TitleDef("national_level_hunter", TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.06f), new PerkLine(TitlePerk.XP, 0.06f)), false));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DuelPayload.TYPE, DuelPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.duel.DuelClient.onSync(p)));
    }

    private DuelModule() {}
}
