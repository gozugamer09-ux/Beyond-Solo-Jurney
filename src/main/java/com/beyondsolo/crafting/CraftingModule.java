package com.beyondsolo.crafting;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ElementCures;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.network.CraftActionPayload;
import com.beyondsolo.network.CraftResultPayload;
import com.beyondsolo.network.CraftStockPayload;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Monster cores, materials, mana stones and their veins, System potions and the CRAFT tab's network messages.
 * Called once from the mod constructor. Lang lives in {@code assets/beyondsolo_crafting/lang}; textures come from
 * {@code tools/gen_crafting_assets.py}.
 */
public final class CraftingModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);

    /** The affinity of the creature a core was taken from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Affinity>> CORE_AFFINITY = COMPONENTS.registerComponentType("core_affinity",
            b -> b.persistent(Codec.STRING.xmap(s -> Affinity.valueOf(s.toUpperCase()), a -> a.name().toLowerCase()))
                    .networkSynchronized(ByteBufCodecs.idMapper(Affinity::byIndex, Enum::ordinal)));

    // ------------------------------------------------------------------ cores

    /** Monster cores E..S. */
    public static final List<DeferredItem<MonsterCoreItem>> CORES = new ArrayList<>();
    /** Boss cores E..S. */
    public static final List<DeferredItem<MonsterCoreItem>> BOSS_CORES = new ArrayList<>();

    static {
        for (PlayerRank rank : PlayerRank.values()) {
            String r = rank.name().toLowerCase();
            Rarity rarity = rank.tier() >= 4 ? Rarity.EPIC : rank.tier() >= 2 ? Rarity.RARE : Rarity.UNCOMMON;
            CORES.add(ITEMS.register("monster_core_" + r, () -> new MonsterCoreItem(rank, false, new Item.Properties().rarity(rarity))));
            BOSS_CORES.add(ITEMS.register("boss_core_" + r, () -> new MonsterCoreItem(rank, true,
                    new Item.Properties().rarity(rank.tier() >= 2 ? Rarity.EPIC : Rarity.RARE).stacksTo(16).fireResistant())));
        }
    }

    // ------------------------------------------------------------------ mana stones and veins

    public static final DeferredItem<ManaStoneItem> MANA_SHARD = ITEMS.register("mana_shard",
            () -> new ManaStoneItem(15, new Item.Properties()));
    public static final DeferredItem<ManaStoneItem> MANA_STONE = ITEMS.register("mana_stone",
            () -> new ManaStoneItem(40, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ManaStoneItem> GREATER_MANA_STONE = ITEMS.register("greater_mana_stone",
            () -> new ManaStoneItem(100, new Item.Properties().rarity(Rarity.RARE)));
    /** Shard, stone, greater stone. */
    public static final List<DeferredItem<ManaStoneItem>> MANA_STONES = List.of(MANA_SHARD, MANA_STONE, GREATER_MANA_STONE);

    public static final DeferredBlock<ManaVeinBlock> LESSER_MANA_VEIN = vein("lesser_mana_vein", 0, 5);
    public static final DeferredBlock<ManaVeinBlock> MANA_VEIN = vein("mana_vein", 1, 7);
    public static final DeferredBlock<ManaVeinBlock> GREATER_MANA_VEIN = vein("greater_mana_vein", 2, 10);
    /** By mana stone size: lesser (shards), plain, greater. */
    public static final List<DeferredBlock<ManaVeinBlock>> VEINS = List.of(LESSER_MANA_VEIN, MANA_VEIN, GREATER_MANA_VEIN);

    static {
        for (var v : VEINS) ITEMS.registerSimpleBlockItem(v);
    }

    // ------------------------------------------------------------------ materials, one per creature kind

    public static final Map<MonsterKind, DeferredItem<CraftMaterialItem>> MATERIALS = new EnumMap<>(MonsterKind.class);
    public static final DeferredItem<CraftMaterialItem> GLOOMFANG_FANG = material(MonsterKind.GLOOMFANG, "gloomfang_fang");
    public static final DeferredItem<CraftMaterialItem> BROOD_SILK = material(MonsterKind.BROODLING, "brood_silk");
    public static final DeferredItem<CraftMaterialItem> SHARD_CARAPACE = material(MonsterKind.SHARDBACK, "shard_carapace");
    public static final DeferredItem<CraftMaterialItem> HOLLOW_ESSENCE = material(MonsterKind.HOLLOW_ACOLYTE, "hollow_essence");
    public static final DeferredItem<CraftMaterialItem> ASHEN_PLATE = material(MonsterKind.ASHEN_SENTINEL, "ashen_plate");
    public static final DeferredItem<CraftMaterialItem> EMBER_CINDER = material(MonsterKind.EMBERWISP, "ember_cinder");

    // ------------------------------------------------------------------ potions

    public static final DeferredItem<SystemPotionItem> HEALING_DRAUGHT = potion("healing_draught", 0xFF5A6E, 24, Rarity.COMMON, e -> {
        e.heal(8);
        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
    });
    public static final DeferredItem<SystemPotionItem> GREATER_HEALING_DRAUGHT = potion("greater_healing_draught", 0xFF3050, 24, Rarity.UNCOMMON, e -> {
        e.heal(e.getMaxHealth() * 0.4f);
        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
    });
    public static final DeferredItem<SystemPotionItem> SUPREME_HEALING_ELIXIR = potion("supreme_healing_elixir", 0xFFC070, 28, Rarity.EPIC, e -> {
        e.heal(e.getMaxHealth());
        e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));
        e.removeEffectsCuredBy(ElementCures.ANTIDOTE);
        e.removeEffectsCuredBy(ElementCures.TONIC);
    });
    public static final DeferredItem<SystemPotionItem> MANA_DRAUGHT = potion("mana_draught", 0x4FA8FF, 24, Rarity.COMMON, e -> {
        if (e instanceof ServerPlayer p) SystemAPI.restoreMana(p, 40);
    });
    public static final DeferredItem<SystemPotionItem> GREATER_MANA_DRAUGHT = potion("greater_mana_draught", 0x7F9CFF, 24, Rarity.UNCOMMON, e -> {
        if (e instanceof ServerPlayer p) SystemAPI.restoreMana(p, SystemAPI.get(p).maxMana() * 0.6f);
    });
    public static final DeferredItem<SystemPotionItem> STAMINA_TONIC = potion("stamina_tonic", 0x9FE870, 20, Rarity.COMMON, e -> {
        if (e instanceof ServerPlayer p) SystemAPI.addExhaustion(p, -40);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
    });
    public static final DeferredItem<SystemPotionItem> DRAUGHT_OF_MIGHT = potion("draught_of_might", 0xFF8A3A, 24, Rarity.UNCOMMON, e ->
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1800, 0)));
    public static final DeferredItem<SystemPotionItem> IRONSKIN_DRAUGHT = potion("ironskin_draught", 0xB8C4D8, 24, Rarity.UNCOMMON, e ->
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1800, 0)));
    public static final List<DeferredItem<SystemPotionItem>> POTIONS = List.of(HEALING_DRAUGHT, GREATER_HEALING_DRAUGHT, SUPREME_HEALING_ELIXIR,
            MANA_DRAUGHT, GREATER_MANA_DRAUGHT, STAMINA_TONIC, DRAUGHT_OF_MIGHT, IRONSKIN_DRAUGHT);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("crafting", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.crafting"))
            .icon(() -> new ItemStack(CORES.get(2).get()))
            .withTabsBefore(BeyondSolo.id("gear"))
            .displayItems((params, out) -> {
                CORES.forEach(c -> out.accept(c.get()));
                BOSS_CORES.forEach(c -> out.accept(c.get()));
                for (Affinity a : Affinity.values()) if (a != Affinity.NONE) out.accept(MonsterCoreItem.of(PlayerRank.C, false, a, 1));
                MANA_STONES.forEach(m -> out.accept(m.get()));
                VEINS.forEach(v -> out.accept(v.get()));
                MATERIALS.values().forEach(m -> out.accept(m.get()));
                POTIONS.forEach(p -> out.accept(p.get()));
                ModItems.REMEDIES.forEach(r -> out.accept(r.get()));
            })
            .build());

    private static DeferredBlock<ManaVeinBlock> vein(String id, int size, int light) {
        return BLOCKS.register(id, () -> new ManaVeinBlock(size, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(3f + size, 3f)
                .requiresCorrectToolForDrops()
                .lightLevel(s -> light)
                .sound(SoundType.AMETHYST)));
    }

    private static DeferredItem<CraftMaterialItem> material(MonsterKind kind, String id) {
        DeferredItem<CraftMaterialItem> item = ITEMS.register(id, () -> new CraftMaterialItem(new Item.Properties()));
        MATERIALS.put(kind, item);
        return item;
    }

    private static DeferredItem<SystemPotionItem> potion(String id, int color, int ticks, Rarity rarity, Consumer<LivingEntity> effect) {
        return ITEMS.register(id, () -> new SystemPotionItem(color, ticks, effect, new Item.Properties().stacksTo(16).rarity(rarity)));
    }

    /** The mana stone a vein of this size gives. */
    public static Item stoneFor(int size) {
        return MANA_STONES.get(Math.max(0, Math.min(2, size))).get();
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(CraftingModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(CraftResultPayload.TYPE, CraftResultPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.crafting.CraftClient.onResult(p)));
        r.playToClient(CraftStockPayload.TYPE, CraftStockPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.crafting.CraftClient.onStock(p)));
        r.playToServer(CraftActionPayload.TYPE, CraftActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    if (p.recipe().isEmpty()) CraftingService.sendStock(player);
                    else CraftingService.craft(player, p.recipe(), p.times());
                }));
    }

    private CraftingModule() {}
}
