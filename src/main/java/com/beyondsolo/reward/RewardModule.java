package com.beyondsolo.reward;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import com.beyondsolo.network.RewardBoxPayload;
import com.beyondsolo.network.RewardBoxPickPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * Registers the random boxes ({@link RewardBoxes}): the three boxes (plain, blessed, cursed; the rank sits on the
 * stack), the Hex of Frailty a cursed box can leave, the box window's messages and the settings
 * ({@code beyondsolo-boxes.toml}). Lang lives in {@code assets/beyondsolo_boxes/lang}; models and textures come from
 * {@code tools/gen_reward_assets.py}. Called once from the mod constructor.
 */
public final class RewardModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);

    public static final DeferredItem<RewardBoxItem> RANDOM_BOX = box(BoxKind.PLAIN, Rarity.UNCOMMON);
    public static final DeferredItem<RewardBoxItem> BLESSED_BOX = box(BoxKind.BLESSED, Rarity.RARE);
    public static final DeferredItem<RewardBoxItem> CURSED_BOX = box(BoxKind.CURSED, Rarity.EPIC);

    /** The share of max health the Hex of Frailty takes (a first guess for the balancing pass). */
    public static final double FRAILTY_SHARE = 0.15;

    /**
     * A cursed box's hex: a share of the hunter's health, gone for a while. Milk does not lift it; a purifying draught
     * does, and death.
     */
    public static final DeferredHolder<MobEffect, MobEffect> FRAILTY = EFFECTS.register("hex_of_frailty",
            () -> new CombatStatusEffect(MobEffectCategory.HARMFUL, 0x8A2BE2, Set.of(), 0, (e, amp) -> {})
                    .addAttributeModifier(Attributes.MAX_HEALTH, BeyondSolo.id("effect.hex_of_frailty"), -FRAILTY_SHARE,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private static DeferredItem<RewardBoxItem> box(BoxKind kind, Rarity rarity) {
        return ITEMS.register(kind.id(), () -> new RewardBoxItem(kind, new Item.Properties().stacksTo(16).rarity(rarity).fireResistant()));
    }

    /** The item of a kind of box. */
    public static RewardBoxItem box(BoxKind kind) {
        return switch (kind) {
            case PLAIN -> RANDOM_BOX.get();
            case BLESSED -> BLESSED_BOX.get();
            case CURSED -> CURSED_BOX.get();
        };
    }

    public static void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(RewardModule::payloads);
        modBus.addListener(RewardModule::creativeTab);
        container.registerConfig(ModConfig.Type.COMMON, RewardConfig.SPEC, "beyondsolo-boxes.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RewardBoxPayload.TYPE, RewardBoxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.reward.RewardClient.onBox(p)));
        r.playToServer(RewardBoxPickPayload.TYPE, RewardBoxPickPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) RewardBoxes.pick(player, p.hand(), p.seed(), p.index());
                }));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(BeyondSolo.id("combat"))) return;
        for (BoxKind kind : BoxKind.values()) {
            for (PlayerRank rank : PlayerRank.values()) event.accept(RewardBoxItem.of(kind, rank, 1));
        }
    }

    private RewardModule() {}
}
