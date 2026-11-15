package com.beyondsolo.crafting;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Where cores, materials and mana stones come from.
 * <ul>
 *   <li>Gate creatures (anything the enemy roster made) killed by a hunter with the System: a core of the creature's
 *   rank (30% normal, 50% veteran, always for elites, two for champions), carrying its affinity; the gate's own
 *   kinds also leave their material.</li>
 *   <li>Any other hostile monster killed by a hunter: a small chance of an E-rank core, so the first steps pay too.</li>
 *   <li>Bosses: a boss core of the gate's rank, a handful of cores, materials and mana stones, added to the reward
 *   cache through {@link GateLoot.BossLootEvent}. A boss fought outside a gate drops them on the ground.</li>
 *   <li>Mana stones: veins in gate cave walls ({@link ManaVeinBlock}, placed by the dungeon builder).</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class CraftDrops {
    /** Chance of a core by variant: normal, veteran, elite, champion. */
    public static final float[] CORE_CHANCE = {0.3f, 0.5f, 1f, 1f};
    /** Chance of an E-rank core from an ordinary hostile monster. */
    public static final float WILD_CORE_CHANCE = 0.04f;
    /** Chance of the kind's material (times the variant's reward multiplier, capped at 1). */
    public static final float MATERIAL_CHANCE = 0.45f;

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(dead instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !SystemAPI.hasSystem(p)) return;
        List<ItemStack> loot;
        if (dead instanceof GateBossEntity boss) {
            if (GateService.isDungeon(dead.level())) return; // the reward cache holds it
            loot = bossExtras(boss.rank(), false, dead.getRandom());
        } else {
            loot = creatureDrops(dead, dead.getRandom());
        }
        if (loot.isEmpty()) return;
        boolean core = false;
        for (ItemStack s : loot) {
            ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), s);
            item.setDeltaMovement((dead.getRandom().nextDouble() - 0.5) * 0.2, 0.3, (dead.getRandom().nextDouble() - 0.5) * 0.2);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
            core |= s.getItem() instanceof MonsterCoreItem;
        }
        // the core tears free of the body in a flash of its colour
        if (core) {
            PlayerRank rank = rankOf(dead);
            Affinity a = dead instanceof Mob ? EnemyTraits.of(dead).affinity() : Affinity.NONE;
            int color = a != Affinity.NONE ? a.color : rank.color();
            CombatFx.play(dead, CombatFxPayload.Kind.NOVA, color | (2 << 24));
        }
    }

    static PlayerRank rankOf(LivingEntity dead) {
        if (dead instanceof GateBossEntity b) return b.rank();
        PlayerRank r = dead instanceof Mob mob ? Enemies.rankOf(mob) : null;
        return r == null ? PlayerRank.E : r;
    }

    /** What a creature leaves: a core (maybe), its kind's material (maybe). */
    public static List<ItemStack> creatureDrops(LivingEntity dead, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        PlayerRank rank = dead instanceof Mob mob ? Enemies.rankOf(mob) : null;
        if (rank == null) {
            if (r.nextFloat() < WILD_CORE_CHANCE) out.add(MonsterCoreItem.of(PlayerRank.E, false, Affinity.NONE, 1));
            return out;
        }
        EnemyTraits traits = EnemyTraits.of(dead);
        Variant v = traits.variant();
        if (r.nextFloat() < CORE_CHANCE[v.ordinal()]) {
            out.add(MonsterCoreItem.of(rank, false, traits.affinity(), v == Variant.CHAMPION ? 2 : 1));
        }
        if (dead instanceof GateMonsterEntity m) {
            var mat = CraftingModule.MATERIALS.get(m.kind());
            if (mat != null && r.nextFloat() < Math.min(1f, MATERIAL_CHANCE * v.reward)) {
                out.add(new ItemStack(mat.get(), v.special() ? 2 : 1));
            }
        }
        return out;
    }

    /** What a boss adds to its loot: its boss core, cores of its rank, materials and mana stones. */
    public static List<ItemStack> bossExtras(PlayerRank rank, boolean ascended, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        int t = rank.tier();
        out.add(MonsterCoreItem.of(rank, true, Affinity.NONE, ascended ? 2 : 1));
        out.add(MonsterCoreItem.of(rank, false, Affinity.NONE, 2 + r.nextInt(3)));
        var mats = new ArrayList<>(CraftingModule.MATERIALS.values());
        for (int i = 0; i < 2 + r.nextInt(2); i++) out.add(new ItemStack(mats.get(r.nextInt(mats.size())).get(), 1 + r.nextInt(2)));
        out.add(new ItemStack(CraftingModule.stoneFor(t / 2), 2 + r.nextInt(3)));
        return out;
    }

    @SubscribeEvent
    static void onBossLoot(GateLoot.BossLootEvent event) {
        event.loot.addAll(bossExtras(event.rank, event.ascended, event.level.random));
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        CraftCommand.register(event.getDispatcher());
    }

    private CraftDrops() {}
}
