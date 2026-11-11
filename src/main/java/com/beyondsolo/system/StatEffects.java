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
 * Turns stats into real attribute buffs. Each stat point above the base of 10 adds the amount below.
 * Modifiers are permanent (saved with the player) so extra max health survives relogging.
 *
 * <p>Other modules can add their own stat-driven bonuses with {@link #register}.
 */
public final class StatEffects {
    public record Bonus(ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation,
                        ToDoubleFunction<SystemData> amount) {}

    private static final List<Bonus> BONUSES = new ArrayList<>();

    static {
        // STR: melee damage
        register("str_damage", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.STR) * 0.25);
        // STR: knockback resistance (you hit hard and stand firm)
        register("str_knockback", Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE,
                d -> Math.min(0.5, above(d, Stat.STR) * 0.005));
        // VIT: max health (+1 heart per 4 points) and natural armor/toughness (durability)
        register("vit_health", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.VIT) * 0.5);
        register("vit_armor", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.VIT) * 0.15);
        register("vit_toughness", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.VIT) * 0.05);
        // AGI: movement and attack speed
        register("agi_speed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                d -> Math.min(1.0, above(d, Stat.AGI) * 0.004));
        register("agi_attack_speed", Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.AGI) * 0.02);
        // SENSE: luck (better loot) and reach
        register("sense_luck", Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE,
                d -> above(d, Stat.SENSE) * 0.1);
        register("sense_reach", Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE,
                d -> Math.min(2.0, above(d, Stat.SENSE) * 0.02));
        // INT feeds max mana (see SystemData#maxMana); spells come with the skill system.
        ApothicBonuses.registerAll();
    }

    public static void register(String name, Holder<Attribute> attribute, AttributeModifier.Operation op,
                                ToDoubleFunction<SystemData> amount) {
        register(new Bonus(BeyondSolo.id("stat/" + name), attribute, op, amount));
    }

    public static void register(Bonus bonus) {
        BONUSES.add(bonus);
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
