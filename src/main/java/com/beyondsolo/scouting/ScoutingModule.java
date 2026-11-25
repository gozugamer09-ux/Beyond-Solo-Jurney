package com.beyondsolo.scouting;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.NewsPayload;
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
 * Registers the great guilds' scouting ({@link Scouting}) and the hunter news ({@code news.HunterNews}): the scout, a
 * hunter's dealings with the guilds ({@code beyondsolo:scouting}, kept through death), the news' message, the titles and
 * the settings ({@code beyondsolo-scouting.toml}). The windows ride on the association's messages. Lang lives in
 * {@code assets/beyondsolo_scouting/lang}; the scouts' skins come from {@code tools/gen_scout_assets.py}. Called once
 * from the mod constructor.
 */
public final class ScoutingModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's offers from the great guilds and their contract. Survives death. */
    public static final Supplier<AttachmentType<ScoutRecord>> RECORD = ATTACHMENTS.register("scouting",
            () -> AttachmentType.serializable(ScoutRecord::new).copyOnDeath().build());

    /** A great guild's scout: shaped like a person, in a suit, their guild's band on the arm. */
    public static final DeferredHolder<EntityType<?>, EntityType<ScoutEntity>> SCOUT = ENTITY_TYPES.register("guild_scout",
            () -> EntityType.Builder.<ScoutEntity>of(ScoutEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("guild_scout"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(ScoutingModule::attributes);
        modBus.addListener(ScoutingModule::payloads);
        titles();
        container.registerConfig(ModConfig.Type.COMMON, ScoutingConfig.SPEC, "beyondsolo-scouting.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SCOUT.get(), ScoutEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef("not_for_sale", TitleCategory.SYSTEM, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.05f), new PerkLine(TitlePerk.XP, 0.04f)), false));
        Titles.register(new TitleDef("franchise_hunter", TitleCategory.SYSTEM, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.06f)), false));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(NewsPayload.TYPE, NewsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.news.NewsClient.onNews(p)));
    }

    private ScoutingModule() {}
}
