package com.beyondsolo.gear;

import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The brute's six signature armor sets, one per gate rank. Pieces only drop from the brute's reward cache (a
 * chance per kill, see {@code armory.ArmorDrops}). Two pieces give stats; four give the set's perk
 * ({@link SetEffects}). Each is made for one class (the armory's catalogue holds the other bosses' sets).
 *
 * <pre>
 * E  Hollow Brute      Stonehide      rare       Guardian  Unbroken: falling under 30% health hardens you (Resistance II, absorption), once a minute
 * D  Mire Ravager      Mirefang       rare       Assassin  Venom Skin: whatever strikes you in melee takes venom buildup
 * C  Azure Colossus    Rimeguard      epic       Guardian  Frost Aegis: your hits carry water; melee attackers may be frostbitten
 * B  Gloom Warlord     Gloomreaver    epic       Assassin  Night's Edge: +30% from behind, your hits carry shadow
 * A  Ember Tyrant      Emberwrought   legendary  Warrior   Living Flame: burns barely touch you, your hits carry fire and burn
 * S  Crimson Overlord  Sovereign      mythic     Warrior   Crimson Dominion: hits brand with crimson, heal 6% of damage, +15% on branded foes
 * </pre>
 */
public final class BossSets {
    public static final ArmorSets.Set STONEHIDE = ArmorSets.register(new ArmorSets.Set("stonehide", 0.12f, ItemGrade.RARE,
            () -> Map.of(Attributes.KNOCKBACK_RESISTANCE, 0.1, Attributes.ARMOR, 2.0),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 2.0), "unbroken", "e")).forClass(PlayerClass.GUARDIAN).tier(0).from("boss:brute");
    public static final ArmorSets.Set MIREFANG = ArmorSets.register(new ArmorSets.Set("mirefang", 0.04f, ItemGrade.RARE,
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.2),
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.3, Attributes.MOVEMENT_SPEED, 0.03), "venom_skin", "d")).forClass(PlayerClass.ASSASSIN).tier(1).from("boss:brute");
    public static final ArmorSets.Set RIMEGUARD = ArmorSets.register(new ArmorSets.Set("rimeguard", 0.1f, ItemGrade.EPIC,
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.15, Attributes.ARMOR_TOUGHNESS, 1.0),
            () -> Map.of(Attributes.ARMOR, 2.0), "frost_aegis", "c")).forClass(PlayerClass.GUARDIAN).tier(2).from("boss:brute");
    public static final ArmorSets.Set GLOOMREAVER = ArmorSets.register(new ArmorSets.Set("gloomreaver", 0.03f, ItemGrade.EPIC,
            () -> Map.of(Attributes.MOVEMENT_SPEED, 0.05, ALObjects.Attributes.CRIT_CHANCE, 0.05),
            () -> Map.of(ALObjects.Attributes.CRIT_DAMAGE, 0.2), "nights_edge", "b")).forClass(PlayerClass.ASSASSIN).tier(3).from("boss:brute");
    public static final ArmorSets.Set EMBERWROUGHT = ArmorSets.register(new ArmorSets.Set("emberwrought", 0.1f, ItemGrade.LEGENDARY,
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.25, ModAttributes.ELEMENTAL_POWER, 0.1),
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.4, ModAttributes.ELEMENTAL_POWER, 0.15), "living_flame", "a")).forClass(PlayerClass.WARRIOR).tier(4).from("boss:brute");
    public static final ArmorSets.Set SOVEREIGN = ArmorSets.register(new ArmorSets.Set("sovereign", 0.1f, ItemGrade.MYTHIC,
            () -> Map.of(Attributes.MAX_HEALTH, 0.1, ModAttributes.CRIMSON_RESISTANCE, 0.25),
            () -> Map.of(ModAttributes.ELEMENTAL_POWER, 0.25, ModAttributes.BLOOD_RESISTANCE, 0.3), "crimson_dominion", "s")).forClass(PlayerClass.WARRIOR).tier(5).from("boss:brute");

    public static final List<ArmorSets.Set> ALL = List.of(STONEHIDE, MIREFANG, RIMEGUARD, GLOOMREAVER, EMBERWROUGHT, SOVEREIGN);

    /** The signature set of a gate rank's boss. */
    public static ArmorSets.Set forRank(PlayerRank rank) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, rank.tier())));
    }

    public static @Nullable PlayerRank bossRank(ArmorSets.Set set) {
        return set.boss == null ? null : PlayerRank.byName(set.boss.toUpperCase());
    }

    static void init() {}

    private BossSets() {}
}
