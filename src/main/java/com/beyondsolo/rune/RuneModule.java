package com.beyondsolo.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.RuneActionPayload;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.network.RuneSyncPayload;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.RuneArts;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
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

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rune stones, engraving runes, the player's rune data and the rune network messages. Called once from the mod
 * constructor. The rules are in {@link Runes}, the rune arts in {@link RuneArts}, where runes come from in
 * {@link RuneDrops}. Lang lives in {@code assets/beyondsolo_runes/lang}; textures and models come from
 * {@code tools/gen_rune_assets.py}.
 */
public final class RuneModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A rune stone's grade (E..S). Inscribed stones carry it too, so a stone keeps its look and price. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlayerRank>> GRADE = COMPONENTS.registerComponentType("rune_grade",
            b -> b.persistent(Codec.STRING.xmap(s -> {
                        PlayerRank r = PlayerRank.byName(s);
                        return r == null ? PlayerRank.E : r;
                    }, r -> r.name().toLowerCase(Locale.ROOT)))
                    .networkSynchronized(ByteBufCodecs.idMapper(i -> PlayerRank.values()[Math.floorMod(i, PlayerRank.values().length)], Enum::ordinal)));

    /** Per-player engravings and runes absorbed. Survives death. */
    public static final Supplier<AttachmentType<RuneData>> RUNES = ATTACHMENTS.register("runes",
            () -> AttachmentType.serializable(RuneData::new).copyOnDeath().build());

    public static final DeferredItem<RuneStoneItem> RUNE_STONE = ITEMS.register("rune_stone",
            () -> new RuneStoneItem(new Item.Properties().stacksTo(16).fireResistant()));

    /** The engraving runes: haste, thrift, might. */
    public static final Map<Engraving, DeferredItem<EngravingRuneItem>> ENGRAVINGS = new EnumMap<>(Engraving.class);

    static {
        for (Engraving e : Engraving.values()) {
            ENGRAVINGS.put(e, ITEMS.register("rune_of_" + e.id(),
                    () -> new EngravingRuneItem(e, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant())));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("runes", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.runes"))
            .icon(() -> Runes.of(RuneArts.WYRMFIRE_ROAR))
            .withTabsBefore(BeyondSolo.id("crafting"))
            .displayItems((params, out) -> {
                for (PlayerRank r : PlayerRank.values()) out.accept(Runes.sealed(r));
                for (Skill s : Skills.runeArts()) out.accept(Runes.of(s));
                ENGRAVINGS.values().forEach(e -> out.accept(e.get()));
                // one class rune for each class, to try the class lock
                for (PlayerClass c : PlayerClass.values()) {
                    for (Skill s : Skills.forClass(c)) {
                        if (s.owner() == c && !s.advanced()) {
                            out.accept(Runes.of(s));
                            break;
                        }
                    }
                }
            })
            .build());

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RuneModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RuneFxPayload.TYPE, RuneFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.rune.RuneClient.onFx(p)));
        r.playToClient(RuneSyncPayload.TYPE, RuneSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.rune.RuneClient.onSync(p)));
        r.playToServer(RuneActionPayload.TYPE, RuneActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Runes.engrave(player, p.hand(), p.skill());
                }));
    }

    private RuneModule() {}
}
