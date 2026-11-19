package com.beyondsolo.reward;

import com.beyondsolo.armory.ArmorCatalog;
import com.beyondsolo.armory.ArmorDrops;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.MonsterCoreItem;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.manapoison.ManaPoisonModule;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.SkillTomeItem;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.reward.BoxReward.Category;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the boxes hold, by the box's rank (E..S) and the opener's class. Everything is rolled from the random source
 * given, so a blessed box's three offers come out the same every time it is opened with the same seed: closing the
 * window and opening the box again shows the same three.
 *
 * <ul>
 *   <li><b>Plain:</b> one thing: gold, remedies, a weapon or a piece of armor for the hunter's class, monster cores,
 *   a skill tome, a sealed rune, a dungeon key of its rank, or (rarely) stat points.</li>
 *   <li><b>Blessed:</b> three offers of different kinds, gear a rank's luck better, and the hunter picks one.</li>
 *   <li><b>Cursed:</b> one thing, never the small stuff: gear a rank's luck better, two and a half times the gold,
 *   two tomes or keys, more stat points. The curse is {@link BoxCurse}'s.</li>
 * </ul>
 * Weights and amounts are first guesses for the balancing pass.
 */
public final class BoxRolls {
    private static final Map<Category, Integer> PLAIN = weights(22, 16, 12, 12, 12, 8, 6, 7, 5);
    private static final Map<Category, Integer> BLESSED = weights(12, 6, 20, 20, 0, 12, 10, 10, 10);
    private static final Map<Category, Integer> CURSED = weights(20, 0, 18, 18, 0, 10, 10, 12, 12);

    /** Weights in {@link Category} order: gold, remedies, weapon, armor, cores, tome, rune, key, stats. */
    private static Map<Category, Integer> weights(int... w) {
        Map<Category, Integer> m = new EnumMap<>(Category.class);
        for (Category c : Category.values()) m.put(c, w[c.ordinal()]);
        return m;
    }

    /** What a plain or cursed box gives. */
    public static BoxReward roll(BoxKind kind, PlayerRank rank, @Nullable PlayerClass cls, RandomSource r) {
        Category c = pick(kind == BoxKind.CURSED ? CURSED : PLAIN, List.of(), r);
        return make(c, kind, rank, cls, r);
    }

    /** A blessed box's three offers, each of a different kind, rolled from its seed. */
    public static List<BoxReward> offers(PlayerRank rank, @Nullable PlayerClass cls, long seed) {
        RandomSource r = new LegacyRandomSource(seed);
        List<Category> taken = new ArrayList<>();
        List<BoxReward> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Category c = pick(BLESSED, taken, r);
            taken.add(c);
            out.add(make(c, BoxKind.BLESSED, rank, cls, r));
        }
        return out;
    }

    private static Category pick(Map<Category, Integer> weights, List<Category> skip, RandomSource r) {
        int total = 0;
        for (Map.Entry<Category, Integer> e : weights.entrySet()) if (!skip.contains(e.getKey())) total += e.getValue();
        if (total <= 0) return Category.GOLD;
        int roll = r.nextInt(total);
        for (Map.Entry<Category, Integer> e : weights.entrySet()) {
            if (skip.contains(e.getKey())) continue;
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return Category.GOLD;
    }

    /** One reward of a kind. Blessed and cursed boxes roll gear as if from a gate a rank higher. */
    static BoxReward make(Category c, BoxKind kind, PlayerRank rank, @Nullable PlayerClass cls, RandomSource r) {
        int t = rank.tier();
        boolean better = kind != BoxKind.PLAIN;
        int gearTier = Math.min(PlayerRank.S.tier(), t + (better ? 1 : 0));
        return switch (c) {
            case GOLD -> {
                double share = 0.25 + r.nextDouble() * 0.35;
                double mult = kind == BoxKind.CURSED ? 2.5 : kind == BoxKind.BLESSED ? 1.6 : 1;
                yield BoxReward.gold(Math.max(10, Math.round(EconomyHooks.GATE_GOLD[t] * share * mult)));
            }
            case REMEDIES -> BoxReward.items(c, remedies(t, kind == BoxKind.BLESSED, r));
            case WEAPON -> BoxReward.items(c, List.of(weapon(gearTier, better ? ItemGrade.MYTHIC : ItemGrade.LEGENDARY, cls, r)));
            case ARMOR -> {
                ItemStack piece = armor(PlayerRank.values()[gearTier], cls, r);
                yield piece.isEmpty() ? BoxReward.items(Category.WEAPON, List.of(weapon(gearTier, ItemGrade.LEGENDARY, cls, r)))
                        : BoxReward.items(c, List.of(piece));
            }
            case CORES -> BoxReward.items(c, List.of(
                    MonsterCoreItem.of(rank, false, Affinity.NONE, 2 + r.nextInt(3)),
                    new ItemStack(CraftingModule.stoneFor(t / 2), 2 + r.nextInt(3))));
            case TOME -> BoxReward.items(c, kind == BoxKind.CURSED ? List.of(SkillTomeItem.unread(), SkillTomeItem.unread()) : List.of(SkillTomeItem.unread()));
            case RUNE -> BoxReward.items(c, List.of(Runes.sealed(rank)));
            case KEY -> {
                ItemStack key = DungeonKeys.key(rank);
                if (kind == BoxKind.CURSED) key.setCount(2);
                yield BoxReward.items(c, List.of(key));
            }
            case STATS -> BoxReward.stats((t >= 3 ? 2 : 1) + (better ? 1 : 0));
        };
    }

    /** A few remedies and draughts for the rank: healing, mana, cures, and from A up a purifying draught. */
    static List<ItemStack> remedies(int t, boolean big, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        DeferredItem<? extends Item> heal = t >= 4 ? CraftingModule.GREATER_HEALING_DRAUGHT : t >= 2 && r.nextBoolean()
                ? CraftingModule.GREATER_HEALING_DRAUGHT : CraftingModule.HEALING_DRAUGHT;
        out.add(new ItemStack(heal.get(), (big ? 3 : 2) + r.nextInt(2)));
        out.add(new ItemStack((t >= 3 ? CraftingModule.GREATER_MANA_DRAUGHT : CraftingModule.MANA_DRAUGHT).get(), 1 + r.nextInt(2)));
        var cures = ModItems.REMEDIES;
        out.add(new ItemStack(cures.get(r.nextInt(cures.size())).get(), 1 + r.nextInt(2)));
        if (t >= PlayerRank.A.tier()) out.add(new ItemStack(ManaPoisonModule.DRAUGHT.get(), big ? 2 : 1));
        if (big && t >= PlayerRank.S.tier() && r.nextFloat() < 0.5f) out.add(new ItemStack(CraftingModule.SUPREME_HEALING_ELIXIR.get()));
        return out;
    }

    /**
     * A weapon for the class (any class's if the grade has none for it), of a grade rolled for a gate of {@code tier},
     * never above {@code cap}.
     */
    static ItemStack weapon(int tier, ItemGrade cap, @Nullable PlayerClass cls, RandomSource r) {
        ItemGrade grade = GateLoot.rollGrade(tier, r);
        if (grade.ordinal() > cap.ordinal()) grade = cap;
        List<DeferredItem<? extends Item>> pool = Arsenal.ofGrade(grade);
        List<DeferredItem<? extends Item>> own = new ArrayList<>();
        for (var item : pool) if (cls != null && item.get() instanceof ArsenalWeapon w && w.favoredClass() == cls) own.add(item);
        List<DeferredItem<? extends Item>> from = !own.isEmpty() ? own : pool;
        ItemStack stack = from.isEmpty() ? new ItemStack(ModItems.KNIGHT_SWORD.get()) : new ItemStack(from.get(r.nextInt(from.size())).get());
        return GearRolls.roll(stack, r);
    }

    /**
     * A piece of a set made for the class (any class's if none fits the rank), rolled. Never a divine set: those stay
     * the monarchs' and the S gates' rarest gift. Empty if there is none.
     */
    static ItemStack armor(PlayerRank rank, @Nullable PlayerClass cls, RandomSource r) {
        List<ArmorSets.Set> sets = new ArrayList<>();
        for (ArmorSets.Set s : ArmorCatalog.classSets()) if (s.tier <= PlayerRank.S.tier() && !s.divine()) sets.add(s);
        List<ArmorSets.Set> own = new ArrayList<>();
        for (ArmorSets.Set s : sets) if (s.playerClass == cls && ArmorDrops.weight(s, rank, cls) > 0) own.add(s);
        ArmorSets.Set set = ArmorDrops.pick(!own.isEmpty() ? own : sets, rank, cls, r);
        return set == null ? ItemStack.EMPTY : ArmorDrops.piece(set, rank.tier(), r);
    }

    private BoxRolls() {}
}
