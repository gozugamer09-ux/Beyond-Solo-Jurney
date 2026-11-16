package com.beyondsolo.gear;

import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Each gate rank's signature loot, dropped into the boss's reward cache on top of the normal loot for the rank:
 * <ul>
 *   <li>a chance at one of its signature weapons (a short weighted table per rank);</li>
 *   <li>refining stones, an infusion crystal of its element, and ward seals from B rank up.</li>
 * </ul>
 * Armor set pieces are not rolled here any more: every boss kind has its own sets, and a piece is a chance per kill
 * ({@code armory.ArmorDrops}). The table still names the brute's set for its rank.
 */
public final class BossLoot {
    /** One line of a boss's weapon table. */
    public record Entry(Supplier<? extends Item> item, int weight) {}

    /** A boss's signature table. */
    public record Table(PlayerRank rank, ArmorSets.Set set, SkillElement element, float weaponChance, List<Entry> weapons) {}

    public static final List<Table> TABLES = List.of(
            new Table(PlayerRank.E, BossSets.STONEHIDE, SkillElement.EARTH, 0.30f, List.of(
                    e(Arsenal.IRONCLAD_WARHAMMER, 5), e(Arsenal.LYCAN_FANG_KNIFE, 5), e(Arsenal.WARCHIEF_CLEAVER, 2))),
            new Table(PlayerRank.D, BossSets.MIREFANG, SkillElement.NATURE, 0.32f, List.of(
                    e(Arsenal.GOBLIN_SHIV, 5), e(Arsenal.NAGA_GLAIVE, 4), e(Arsenal.SERPENTSTING_BOW, 1))),
            new Table(PlayerRank.C, BossSets.RIMEGUARD, SkillElement.WATER, 0.34f, List.of(
                    e(Arsenal.FROSTWARDEN_DAGGER, 4), e(Arsenal.WINTER_BREATH_STAFF, 4), e(Arsenal.FROSTBEAR_GAUNTLETS, 2))),
            new Table(PlayerRank.B, BossSets.GLOOMREAVER, SkillElement.SHADOW, 0.36f, List.of(
                    e(Arsenal.KNIGHTSBANE, 4), e(Arsenal.SWARM_KING_TALONS, 4), e(Arsenal.NIGHTVEIL_KRIS, 2))),
            new Table(PlayerRank.A, BossSets.EMBERWROUGHT, SkillElement.FIRE, 0.38f, List.of(
                    e(Arsenal.EMBER_SAGE_STAFF, 5), e(Arsenal.HELLHOUND_HALBERD, 4), e(Arsenal.HELLSOVEREIGN_FANG, 1))),
            new Table(PlayerRank.S, BossSets.SOVEREIGN, SkillElement.LIGHTNING, 0.40f, List.of(
                    e(Arsenal.CRIMSON_COMMANDER_BLADE, 4), e(Arsenal.IRONBLOOD_CLAYMORE, 4), e(Arsenal.STORMCALLER_EDICT, 1))));

    private static Entry e(DeferredItem<? extends Item> item, int weight) {
        return new Entry(item, weight);
    }

    public static Table table(PlayerRank rank) {
        return TABLES.get(Math.max(0, Math.min(TABLES.size() - 1, rank.tier())));
    }

    /** One random piece of the boss's set, rolled (it may come out a grade higher). */
    public static ItemStack setPiece(Table t, RandomSource r) {
        var pieces = GearModule.SET_PIECES.get(t.set().name);
        ItemStack stack = new ItemStack(pieces.get(r.nextInt(pieces.size())).get());
        return GearRolls.roll(stack, GearRolls.dropGrade(stack, t.rank().tier(), r), r);
    }

    public static ItemStack weapon(Table t, RandomSource r) {
        int total = 0;
        for (Entry e : t.weapons()) total += e.weight();
        int pick = r.nextInt(total);
        for (Entry e : t.weapons()) {
            pick -= e.weight();
            if (pick < 0) return GearRolls.roll(new ItemStack(e.item().get()), r);
        }
        return GearRolls.roll(new ItemStack(t.weapons().get(0).item().get()), r);
    }

    /** The rank's signature drops. A weapon comes first when there is one (what the cache calls out). */
    public static List<ItemStack> roll(PlayerRank rank, RandomSource r) {
        Table t = table(rank);
        int tier = rank.tier();
        List<ItemStack> out = new ArrayList<>();
        if (r.nextFloat() < t.weaponChance()) out.add(weapon(t, r));
        out.add(new ItemStack(GearModule.REFINING_STONE.get(), 2 + tier + r.nextInt(2)));
        if (r.nextFloat() < 0.5f) out.add(new ItemStack(GearModule.CRYSTALS.get(t.element()).get()));
        if (tier >= 3 && r.nextFloat() < 0.4f) out.add(new ItemStack(GearModule.WARD_SEAL.get()));
        return out;
    }

    /**
     * True if this stack is a signature drop worth calling out: one of this rank's weapons, or a piece of a boss's set
     * or a divine set (only a boss's cache holds those).
     */
    public static boolean isSignature(ItemStack stack, PlayerRank rank) {
        Table t = table(rank);
        if (stack.getItem() instanceof SystemArmorItem a && a.set().signature()) return true;
        for (Entry e : t.weapons()) if (stack.is(e.item().get())) return true;
        return false;
    }

    /** Grade label for a table line, for docs and the tooltip. */
    public static ItemGrade gradeOf(Entry e) {
        ItemGrade g = new ItemStack(e.item().get()).get(com.beyondsolo.registry.ModComponents.GRADE.get());
        return g == null ? ItemGrade.COMMON : g;
    }

    private BossLoot() {}
}
