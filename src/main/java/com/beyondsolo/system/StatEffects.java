package com.beyondsolo.system;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Turns stats and levels into real attribute buffs. Each stat point above the base of 10 adds the amount below; the
 * sizes live in {@link Balance}. Modifiers are permanent (saved with the player) so extra max health survives
 * relogging.
 *
 * <p>Other modules can add their own stat-driven bonuses with {@link #register}.
 */
public final class StatEffects {
    public record Bonus(ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation,
                        ToDoubleFunction<SystemData> amount) {}

    private static final List<Bonus> BONUSES = new ArrayList<>();

    static {
        // Levels: max health, and a little more behind every blow (see Balance)
        register("level_health", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE,
                d -> Balance.levelHealth(d.level()));
        register("level_damage", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                d -> Balance.levelDamage(d.level()) - 1);
        // Absorption shields keep their old share of a hunter's health (BalanceHooks tops the shield up to it)
        register("level_absorption", Attributes.MAX_ABSORPTION, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                d -> Balance.legacyScale(d) - 1);
        // STR: melee damage, +10% a point
        register("str_damage", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                d -> above(d, Stat.STR) * Balance.STR_DAMAGE);
        // STR: knockback resistance (you hit hard and stand firm)
        register("str_knockback", Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE,
                d -> Math.min(0.5, above(d, Stat.STR) * 0.005));
        // VIT: max health, +5% a point, and natural armor/toughness (durability)
        register("vit_health", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                d -> above(d, Stat.VIT) * Balance.VIT_HEALTH);
        register("vit_armor", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.VIT) * Balance.VIT_ARMOR);
        register("vit_toughness", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.VIT) * Balance.VIT_TOUGHNESS);
        // AGI: movement and attack speed
        register("agi_speed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                d -> Math.min(1.0, above(d, Stat.AGI) * 0.004));
        register("agi_attack_speed", Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.AGI) * 0.02);
        // SENSE: luck (better loot) and reach; its bow damage is worked out when the arrow lands (BalanceHooks)
        register("sense_luck", Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.SENSE) * 0.1);
        register("sense_reach", Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE,
                d -> Math.min(2.0, above(d, Stat.SENSE) * 0.02));
        // INT feeds max mana (see SystemData#maxMana) and, with the class's other stats, skill power (Balance#power).
        ApothicBonuses.registerAll();
    }

    public static void register(String name, Holder<Attribute> attribute, AttributeModifier.Operation op,
                                ToDoubleFunction<SystemData> amount) {
        register(new Bonus(BeyondSolo.id("stat/" + name), attribute, op, amount));
    }

    public static void register(Bonus bonus) {
        BONUSES.add(bonus);
    }

    /** The bonuses one stat gives (registered as {@code stat/<stat key>_...}), in registration order. For read-outs. */
    public static List<Bonus> bonusesOf(Stat stat) {
        String prefix = "stat/" + stat.key() + "_";
        List<Bonus> out = new ArrayList<>();
        for (Bonus b : BONUSES) if (b.id().getPath().startsWith(prefix)) out.add(b);
        return out;
    }

    static double above(SystemData data, Stat stat) {
        return data.stat(stat) - Stat.BASE;
    }

    /** Recomputes every stat bonus for the player. Call after any stat change, login or respawn. */
    public static void apply(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        for (Bonus b : BONUSES) {
            AttributeInstance inst = player.getAttribute(b.attribute());
            if (inst == null) continue;
            double amount = data.isAwakened() ? b.amount().applyAsDouble(data) : 0;
            inst.removeModifier(b.id());
            if (amount != 0) {
                inst.addPermanentModifier(new AttributeModifier(b.id(), amount, b.operation()));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private StatEffects() {}
}
