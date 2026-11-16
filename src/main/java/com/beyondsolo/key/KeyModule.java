package com.beyondsolo.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.network.KeyActionPayload;
import com.beyondsolo.network.KeyFxPayload;
import com.beyondsolo.network.KeyRunPayload;
import com.beyondsolo.network.KeySyncPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Dungeon keys: the six keys (one per rank), the seal a key can carry, the player's run records and the key network
 * messages. Called once from the mod constructor. The rules are in {@link DungeonKeys}, the runs themselves in
 * {@link KeyedRuns}, where keys come from in {@link KeyDrops} and {@link KeyRecipes}. Lang lives in
 * {@code assets/beyondsolo_keys/lang}; textures and models come from {@code tools/gen_key_assets.py}.
 */
public final class KeyModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** The boss a key is bound to (and whether it comes ascended). Plain keys have none. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeySeal>> SEAL = COMPONENTS.registerComponentType("key_seal",
            b -> b.persistent(KeySeal.CODEC).networkSynchronized(KeySeal.STREAM_CODEC));

    /** Per-player keyed-run records. Survive death. */
    public static final Supplier<AttachmentType<KeyRunData>> RUNS = ATTACHMENTS.register("key_runs",
            () -> AttachmentType.serializable(KeyRunData::new).copyOnDeath().build());

    /** The keys, by rank E..S. */
    public static final List<DeferredItem<DungeonKeyItem>> KEYS = new ArrayList<>();

    static {
        for (PlayerRank r : PlayerRank.values()) {
            Rarity rarity = r.tier() >= 4 ? Rarity.EPIC : r.tier() >= 2 ? Rarity.RARE : Rarity.UNCOMMON;
            KEYS.add(ITEMS.register("dungeon_key_" + r.name().toLowerCase(Locale.ROOT),
                    () -> new DungeonKeyItem(r, new Item.Properties().stacksTo(16).rarity(rarity).fireResistant())));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("keys", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.keys"))
            .icon(() -> DungeonKeys.key(PlayerRank.S))
            .withTabsBefore(BeyondSolo.id("runes"))
            .displayItems((params, out) -> {
                for (PlayerRank r : PlayerRank.values()) out.accept(DungeonKeys.key(r));
                // one key sealed to each boss, at the lowest rank it lives at, and one ascendant
                for (BossKind k : BossKind.values()) out.accept(DungeonKeys.sealed(k.minRank, k, false));
                out.accept(DungeonKeys.sealed(PlayerRank.C, BossKind.KNIGHT, true));
            })
            .build());

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(KeyModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(KeyFxPayload.TYPE, KeyFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.key.KeyClient.onFx(p)));
        r.playToClient(KeyRunPayload.TYPE, KeyRunPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.key.KeyClient.onRun(p)));
        r.playToClient(KeySyncPayload.TYPE, KeySyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.key.KeyClient.onSync(p)));
        r.playToServer(KeyActionPayload.TYPE, KeyActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    if (p.action() == KeyActionPayload.TURN) DungeonKeys.turnFromInventory(player, p.slot());
                    else if (p.action() == KeyActionPayload.RERUN) DungeonKeys.rerun(player);
                }));
    }

    private KeyModule() {}
}
