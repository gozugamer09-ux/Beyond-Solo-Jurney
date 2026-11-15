package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Passive skills. Attribute passives are transient modifiers, put back on login, respawn and every change;
 * the rest (mana and health regeneration) run once a second from {@link SkillHooks}.
 */
public final class SkillPassives {
    private record Bonus(Skill skill, Holder<Attribute> attribute, float perLevel, AttributeModifier.Operation op, String key) {
        ResourceLocation id() {
            return BeyondSolo.id("skill/" + skill.path() + "/" + key);
        }
    }

    private static final Bonus[] BONUSES = {
            new Bonus(Skills.UNBROKEN_WILL, ModAttributes.EXHAUSTION_RESISTANCE, 0.03f, AttributeModifier.Operation.ADD_VALUE, "exhaustion"),
            new Bonus(Skills.BATTLE_HARDENED, Attributes.ATTACK_DAMAGE, 0.4f, AttributeModifier.Operation.ADD_VALUE, "attack"),
            new Bonus(Skills.BATTLE_HARDENED, Attributes.ARMOR, 0.3f, AttributeModifier.Operation.ADD_VALUE, "armor"),
            new Bonus(Skills.KEEN_INSTINCT, Attributes.MOVEMENT_SPEED, 0.02f, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "speed"),
            new Bonus(Skills.STONESKIN, Attributes.ARMOR, 0.5f, AttributeModifier.Operation.ADD_VALUE, "armor"),
            new Bonus(Skills.STONESKIN, Attributes.KNOCKBACK_RESISTANCE, 0.04f, AttributeModifier.Operation.ADD_VALUE, "knockback"),
    };

    /** Puts every passive's modifiers back to match the player's skill levels. */
    public static void apply(ServerPlayer player) {
        SkillData data = SkillAPI.get(player);
        for (Bonus b : BONUSES) {
            AttributeInstance inst = player.getAttribute(b.attribute);
            if (inst == null) continue;
            int level = data.level(b.skill.path());
            if (level <= 0) inst.removeModifier(b.id());
            else inst.addOrUpdateTransientModifier(new AttributeModifier(b.id(), b.perLevel * level, b.op));
        }
        int font = data.level(Skills.ARCANE_FONT.path());
        SubclassPassives.apply(player, data);
        SystemAPI.setManaBonus(player, font * 8f + SubclassPassives.manaBonus(data));
    }

    /** Once a second: mana flow and life bloom. */
    static void tickSecond(ServerPlayer player) {
        SkillData data = SkillAPI.get(player);
        int flow = data.level(Skills.MANA_FLOW.path());
        if (flow > 0) SystemAPI.restoreMana(player, SkillActions.manaFlow(flow));
        int bloom = data.level(Skills.LIFE_BLOOM.path());
        if (bloom > 0 && player.getHealth() < player.getMaxHealth() && player.isAlive()) player.heal(SkillActions.lifeBloom(bloom));
        SubclassPassives.tickSecond(player, data);
    }

    private SkillPassives() {}
}
