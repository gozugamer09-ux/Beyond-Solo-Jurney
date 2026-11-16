package com.beyondsolo.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Where dungeon keys come from.
 * <ul>
 *   <li>Bosses: now and then a key of the gate's rank, and more rarely a key sealed with the boss's own sigil (it
 *   opens a gate that ends with that boss again; from an ascended boss, often an ascendant one), into the reward
 *   cache through {@link GateLoot.BossLootEvent}. A boss fought outside a gate drops them on the ground.</li>
 *   <li>Gate creatures killed by a hunter with the System: veterans rarely, elites and champions a little more often,
 *   a key of their rank.</li>
 *   <li>The System shop sells E, D and C keys a few a day; the CRAFT tab forges them from a boss core.</li>
 * </ul>
 * Numbers are first guesses; the balancing pass tunes them.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class KeyDrops {
    /** Chance a boss leaves a plain key of its gate's rank. */
    public static final float BOSS_KEY = 0.30f;
    /** Chance a boss leaves a key sealed with its sigil (an ascended boss far more often). */
    public static final float BOSS_SIGIL = 0.08f;
    public static final float BOSS_SIGIL_ASCENDED = 0.40f;
    /** Chance the sigil key of an ascended boss is ascendant too. */
    public static final float ASCENDANT = 0.5f;
    /** Chance a gate creature leaves a key of its rank, by variant: normal, veteran, elite, champion. */
    public static final float[] CREATURE_KEY = {0f, 0.004f, 0.025f, 0.05f};

    /** What keys a boss adds to its loot. */
    public static List<ItemStack> bossKeys(BossKind kind, PlayerRank rank, boolean ascended, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        if (r.nextFloat() < BOSS_KEY) out.add(DungeonKeys.key(rank));
        if (kind.livesAt(rank) && r.nextFloat() < (ascended ? BOSS_SIGIL_ASCENDED : BOSS_SIGIL)) {
            out.add(DungeonKeys.sealed(rank, kind, ascended && r.nextFloat() < ASCENDANT));
        }
        return out;
    }

    /** What key a creature leaves (usually none). */
    public static List<ItemStack> creatureKeys(LivingEntity dead, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        if (!(dead instanceof Mob mob) || !dead.getPersistentData().contains(GateService.MOB_TAG)) return out;
        PlayerRank rank = Enemies.rankOf(mob);
        if (rank == null) return out;
        int v = Math.min(EnemyTraits.of(dead).variant().ordinal(), CREATURE_KEY.length - 1);
        if (r.nextFloat() < CREATURE_KEY[v]) out.add(DungeonKeys.key(rank));
        return out;
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(dead instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !SystemAPI.hasSystem(p)) return;
        RandomSource r = dead.getRandom();
        List<ItemStack> keys;
        if (dead instanceof GateBossEntity boss) {
            if (GateService.isDungeon(level)) return; // the reward cache holds them
            keys = bossKeys(boss.kind(), boss.rank(), boss.isAscended(), r);
        } else {
            keys = creatureKeys(dead, r);
        }
        for (ItemStack s : keys) {
            ItemEntity item = new ItemEntity(level, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), s);
            item.setDeltaMovement((r.nextDouble() - 0.5) * 0.12, 0.3, (r.nextDouble() - 0.5) * 0.12);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }

    @SubscribeEvent
    static void onBossLoot(GateLoot.BossLootEvent event) {
        event.loot.addAll(bossKeys(event.boss, event.rank, event.ascended, event.level.random));
    }

    private KeyDrops() {}
}
