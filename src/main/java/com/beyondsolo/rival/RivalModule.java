package com.beyondsolo.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.network.RivalSyncPayload;
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
 * Registers the world's other hunters ({@link RivalParties}) and the hands for hire ({@link Hires}): a rival party's
 * hunter and the porter, the hunter's dealings with them ({@code beyondsolo:rivalry}, kept through death), the HUD's
 * message, the titles, and the settings ({@code beyondsolo-rivals.toml}). Lang lives in
 * {@code assets/beyondsolo_rivals/lang}; the porter's pack comes from {@code tools/gen_rival_assets.py}. Called once
 * from the mod constructor.
 */
public final class RivalModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** Races won to a gate's boss for the Gate Racer title. */
    public static final int RACER_WINS = 3;

    /** A hunter's races, trespasses and hired hands (a porter's pack with them). Survives death. */
    public static final Supplier<AttachmentType<RivalRecord>> RECORD = ATTACHMENTS.register("rivalry",
            () -> AttachmentType.serializable(RivalRecord::new).copyOnDeath().build());

    /** One hunter of a rival party: shaped like a player, in their class's armour, their guild's name over their head. */
    public static final DeferredHolder<EntityType<?>, EntityType<RivalHunterEntity>> RIVAL = ENTITY_TYPES.register("rival_hunter",
            () -> EntityType.Builder.<RivalHunterEntity>of(RivalHunterEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("rival_hunter"));

    /** A hired porter: shaped like a player, with a great pack on their back. */
    public static final DeferredHolder<EntityType<?>, EntityType<PorterEntity>> PORTER = ENTITY_TYPES.register("porter",
            () -> EntityType.Builder.<PorterEntity>of(PorterEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("porter"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RivalModule::attributes);
        modBus.addListener(RivalModule::payloads);
        titles();
        container.registerConfig(ModConfig.Type.COMMON, RivalConfig.SPEC, "beyondsolo-rivals.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RIVAL.get(), GuildHunterEntity.attributes().build());
        event.put(PORTER.get(), GuildHunterEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef("gate_racer", TitleCategory.GATES, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.SWIFTNESS, 0.04f), new PerkLine(TitlePerk.BOSS_DAMAGE, 0.04f)), false));
        Titles.register(new TitleDef("claim_jumper", TitleCategory.GATES, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.07f), new PerkLine(TitlePerk.GOLD, 0.04f)), true));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RivalSyncPayload.TYPE, RivalSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.rival.RivalClient.onSync(p)));
    }

    private RivalModule() {}
}
