package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.EnemyFxPayload;
import com.beyondsolo.network.EnemyTraitsPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers the enemy roster: the gate's own creatures ({@link MonsterKind}), their spawn eggs, the element bolt,
 * and the roster's network messages. Called once from the mod constructor.
 */
public final class EnemyModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BeyondSolo.MOD_ID);

    private static final Map<MonsterKind, DeferredHolder<EntityType<?>, EntityType<GateMonsterEntity>>> TYPES = new EnumMap<>(MonsterKind.class);
    private static final Map<MonsterKind, DeferredHolder<Item, DeferredSpawnEggItem>> EGGS = new EnumMap<>(MonsterKind.class);

    public static final DeferredHolder<EntityType<?>, EntityType<ElementOrbEntity>> ORB = ENTITY_TYPES.register("element_orb",
            () -> EntityType.Builder.<ElementOrbEntity>of(ElementOrbEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(2).build("element_orb"));

    static {
        for (MonsterKind kind : MonsterKind.values()) {
            var holder = ENTITY_TYPES.register(kind.id, () -> EntityType.Builder.<GateMonsterEntity>of((t, l) -> new GateMonsterEntity(t, l, kind), MobCategory.MONSTER)
                    .sized(kind.width, kind.height).clientTrackingRange(10).build(kind.id));
            TYPES.put(kind, holder);
            EGGS.put(kind, ITEMS.register(kind.id + "_spawn_egg", () -> new DeferredSpawnEggItem(holder, kind.eggBase, kind.eggSpots, new Item.Properties())));
        }
    }

    public static EntityType<GateMonsterEntity> type(MonsterKind kind) {
        return TYPES.get(kind).get();
    }

    public static void init(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(EnemyModule::attributes);
        modBus.addListener(EnemyModule::payloads);
        modBus.addListener(EnemyModule::tabs);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        for (MonsterKind kind : MonsterKind.values()) event.put(type(kind), GateMonsterEntity.attributes(kind).build());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) for (var egg : EGGS.values()) event.accept(egg.get());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(EnemyFxPayload.TYPE, EnemyFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.enemy.EnemyClient.onFx(p)));
        r.playToClient(EnemyTraitsPayload.TYPE, EnemyTraitsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.enemy.EnemyClient.onTraits(p)));
    }

    private EnemyModule() {}
}
