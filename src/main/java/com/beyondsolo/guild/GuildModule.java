package com.beyondsolo.guild;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.GuildActionPayload;
import com.beyondsolo.network.GuildEventPayload;
import com.beyondsolo.network.GuildSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Registers the guilds: the hired hunter who fights beside its guild's raiders, the guild's network messages, its
 * settings ({@code beyondsolo-guild.toml}) and the titles guilds and raids give. Called once from the mod constructor.
 */
public final class GuildModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);

    /** A guild's hired hunter in a raid: shaped like a player, in its class's armor. */
    public static final DeferredHolder<EntityType<?>, EntityType<GuildHunterEntity>> HUNTER = ENTITY_TYPES.register("guild_hunter",
            () -> EntityType.Builder.<GuildHunterEntity>of(GuildHunterEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("guild_hunter"));

    public static void init(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(GuildModule::payloads);
        modBus.addListener(GuildModule::attributes);
        titles();
        container.registerConfig(ModConfig.Type.COMMON, GuildConfig.SPEC, "beyondsolo-guild.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(HUNTER.get(), GuildHunterEntity.attributes().build());
    }

    /** First guesses for the balancing pass. */
    private static void titles() {
        Titles.register(new TitleDef("guild_founder", TitleCategory.SYSTEM, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.05f)), false));
        Titles.register(new TitleDef("raid_breaker", TitleCategory.GATES, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.06f), new PerkLine(TitlePerk.GUARD, 0.04f)), false));
        Titles.register(new TitleDef("raid_mvp", TitleCategory.GATES, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.10f), new PerkLine(TitlePerk.MIGHT, 0.05f)), true));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(GuildSyncPayload.TYPE, GuildSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.guild.GuildClient.onSync(p)));
        r.playToClient(GuildEventPayload.TYPE, GuildEventPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.guild.GuildClient.onEvent(p)));
        r.playToServer(GuildActionPayload.TYPE, GuildActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Guilds.handle(player, p);
                }));
    }

    private GuildModule() {}
}
