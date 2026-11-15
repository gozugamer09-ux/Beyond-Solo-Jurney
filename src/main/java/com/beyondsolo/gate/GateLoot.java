package com.beyondsolo.gate;

import com.beyondsolo.gear.BossLoot;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/** What a reward cache holds, by the gate's rank: better gear, more remedies and more treasure the higher it goes. */
public final class GateLoot {
    public static List<ItemStack> roll(PlayerRank rank, RandomSource r) {
        int t = rank.tier();
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + 3 * t + r.nextInt(4)));
        out.add(new ItemStack(Items.EMERALD, 2 + 2 * t + r.nextInt(3)));
        if (t >= 1) out.add(new ItemStack(Items.GOLD_INGOT, 2 + t + r.nextInt(3)));
        if (t >= 2) out.add(new ItemStack(Items.DIAMOND, t - 1 + r.nextInt(2)));
        if (t >= 4) out.add(new ItemStack(Items.NETHERITE_SCRAP, t - 3));
        if (t >= 3) out.add(new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
        if (t >= 5 && r.nextBoolean()) out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));

        // remedies for the gate's own element, and a little more
        out.add(new ItemStack(t >= 2 ? ModItems.GREATER_ANTIDOTE.get() : ModItems.ANTIDOTE.get(), 1 + r.nextInt(2)));
        out.add(new ItemStack(ModItems.CLOTTING_TONIC.get(), 1 + r.nextInt(2)));
        if (t >= 2) out.add(new ItemStack(ModItems.QUENCHING_ELIXIR.get()));

        // gear
        if (r.nextFloat() < 0.55f + 0.08f * t) out.add(weapon(t, r));
        if (t >= 4 && r.nextFloat() < 0.35f) out.add(weapon(t, r));
        int armorStart = t <= 1 ? 0 : t <= 2 ? 4 : 8;
        int pieces = r.nextFloat() < 0.7f ? 1 + (t >= 4 ? 1 : 0) : 0;
        for (int i = 0; i < pieces; i++) {
            ItemStack armor = new ItemStack(ModItems.ARMOR.get(armorStart + r.nextInt(4)).get());
            out.add(GearRolls.roll(armor, GearRolls.dropGrade(armor, t, r), r));
        }
        // skill tomes: rarer in low gates, almost certain at the top
        if (r.nextFloat() < 0.3f + 0.12f * t) out.add(com.beyondsolo.skill.SkillTomeItem.unread());
        if (t >= 4 && r.nextFloat() < 0.4f) out.add(com.beyondsolo.skill.SkillTomeItem.unread());
        // the boss's own signature loot: its armor set, maybe one of its weapons, forge materials
        out.addAll(0, BossLoot.roll(rank, r));
        return out;
    }

    /** Chance (out of 1000) of each grade, by gate rank E..S: common, rare, epic, legendary, mythic, divine. */
    static final int[][] GRADE_WEIGHTS = {
            {850, 150, 0, 0, 0, 0},
            {600, 350, 50, 0, 0, 0},
            {250, 500, 220, 30, 0, 0},
            {0, 450, 400, 140, 10, 0},
            {0, 200, 465, 310, 20, 5},
            {0, 0, 400, 520, 60, 20},
    };

    /** Rolls a weapon's grade for a gate of this rank. */
    public static ItemGrade rollGrade(int tier, RandomSource r) {
        int[] w = GRADE_WEIGHTS[Math.max(0, Math.min(tier, GRADE_WEIGHTS.length - 1))];
        int roll = r.nextInt(1000);
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) return ItemGrade.values()[i];
        }
        return ItemGrade.COMMON;
    }

    /**
     * A boss's signature loot: its kind's loot table ({@code data/beyondsolo/loot_table/gate_boss/<kind>.json}, rolled
     * with the gate's rank as luck), then whatever {@link BossLootEvent} listeners add. An ascended boss rolls its
     * table twice and always adds one weapon a grade up from the rank's roll.
     */
    public static List<ItemStack> signature(net.minecraft.server.level.ServerLevel level, BossKind boss, PlayerRank rank, boolean ascended,
                                            net.minecraft.core.BlockPos at) {
        List<ItemStack> out = new ArrayList<>();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, boss.lootTable());
        var table = level.getServer().reloadableRegistries().getLootTable(key);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(at))
                .withLuck(rank.tier())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for (int i = 0; i < (ascended ? 2 : 1); i++) out.addAll(table.getRandomItems(params));
        if (ascended) {
            ItemGrade g = rollGrade(rank.tier(), level.random);
            ItemGrade up = ItemGrade.values()[Math.min(ItemGrade.values().length - 1, g.ordinal() + 1)];
            ItemStack w = Arsenal.random(up, level.random);
            if (w != null) out.add(w);
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new BossLootEvent(level, boss, rank, ascended, out));
        return out;
    }

    /**
     * Fired when a boss's signature loot is rolled, so other modules (item grades, set pieces, boss trophies) can add
     * to or change it. {@code loot} is mutable.
     */
    public static final class BossLootEvent extends net.neoforged.bus.api.Event {
        public final net.minecraft.server.level.ServerLevel level;
        public final BossKind boss;
        public final PlayerRank rank;
        public final boolean ascended;
        public final List<ItemStack> loot;

        public BossLootEvent(net.minecraft.server.level.ServerLevel level, BossKind boss, PlayerRank rank, boolean ascended, List<ItemStack> loot) {
            this.level = level;
            this.boss = boss;
            this.rank = rank;
            this.ascended = ascended;
            this.loot = loot;
        }
    }

    public static ItemStack weapon(int tier, RandomSource r) {
        ItemStack stack = Arsenal.random(rollGrade(tier, r), r);
        return GearRolls.roll(stack != null ? stack : new ItemStack(ModItems.KNIGHT_SWORD.get()), r);
    }

    private GateLoot() {}
}
