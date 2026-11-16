package com.beyondsolo.armory;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gear.BossSets;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where armory pieces come from, and why a full set takes a while:
 * <ul>
 *   <li>Gate creatures killed by a hunter with the System leave a piece of one of their kind's sets now and then:
 *   0.6% of the time from a plain one, 1.2% from a veteran, 5% from an elite, 10% from a champion. Sets of the gate's
 *   own rank come up most, one rank either side half as often, and so on; never a set more than one rank above the
 *   gate. Sets made for the killer's class come up 2.5 times as often.</li>
 *   <li>Bosses: a piece of one of their sets, 50% of the time in an E gate up to 75% in an S gate (an ascended boss
 *   always leaves one, sometimes two), added to the reward cache. The brute leaves its rank's own set. A boss
 *   fought outside a gate drops it on the ground.</li>
 *   <li>Divine pieces: 1% from an S-rank boss (3% ascended), 0.5% from an ascended A-rank one. The class is random.</li>
 *   <li>Village requests: the craftsman's starter set as a reward, 12% of the time (25% for clearing a nest or
 *   escorting someone).</li>
 * </ul>
 * Every piece needs its set's level to put on ({@link ArmorSets#levelFor}). Numbers are first guesses: the balancing
 * pass tunes them.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ArmorDrops {
    /** Chance a gate creature leaves a set piece, by variant: normal, veteran, elite, champion. */
    public static final float[] CREATURE = {0.006f, 0.012f, 0.05f, 0.10f};
    /** How much more often sets of the killer's own class come up. */
    public static final float CLASS_WEIGHT = 2.5f;
    /** Chance a boss leaves a set piece, by gate rank E..S. */
    public static final float[] BOSS = {0.5f, 0.55f, 0.6f, 0.65f, 0.7f, 0.75f};
    /** Chance an ascended boss leaves a second piece. */
    public static final float BOSS_SECOND = 0.35f;
    public static final float DIVINE_S = 0.01f;
    public static final float DIVINE_S_ASCENDED = 0.03f;
    public static final float DIVINE_A_ASCENDED = 0.005f;
    public static final float QUEST = 0.12f;
    public static final float QUEST_DANGEROUS = 0.25f;

    /**
     * How often a set comes up from a creature or boss of this rank: halves for each rank between them, and never
     * more than one rank above. Doubled-and-a-half for the killer's own class.
     */
    public static float weight(ArmorSets.Set set, PlayerRank rank, @Nullable PlayerClass cls) {
        int d = set.tier - rank.tier();
        if (d > 1) return 0;
        float w = 1f / (1 << Math.min(8, Math.abs(d)));
        if (cls != null && set.playerClass == cls) w *= CLASS_WEIGHT;
        return w;
    }

    public static @Nullable ArmorSets.Set pick(List<ArmorSets.Set> sets, PlayerRank rank, @Nullable PlayerClass cls, RandomSource r) {
        float total = 0;
        for (ArmorSets.Set s : sets) total += weight(s, rank, cls);
        if (total <= 0) return null;
        float roll = r.nextFloat() * total;
        for (ArmorSets.Set s : sets) {
            roll -= weight(s, rank, cls);
            if (roll < 0) return s;
        }
        for (int i = sets.size() - 1; i >= 0; i--) if (weight(sets.get(i), rank, cls) > 0) return sets.get(i);
        return null;
    }

    /** A random piece of a set, rolled: it may come out a grade higher (see {@link GearRolls#dropGrade}). */
    public static ItemStack piece(ArmorSets.Set set, int gateTier, RandomSource r) {
        List<DeferredItem<SystemArmorItem>> pieces = ArmoryModule.piecesOf(set);
        if (pieces == null || pieces.isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(pieces.get(r.nextInt(pieces.size())).get());
        return GearRolls.roll(stack, GearRolls.dropGrade(stack, gateTier, r), r);
    }

    /** What a gate creature leaves (usually nothing). */
    public static List<ItemStack> creatureDrops(GateMonsterEntity dead, PlayerRank rank, @Nullable PlayerClass killerClass, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        int v = EnemyTraits.of(dead).variant().ordinal();
        if (r.nextFloat() >= CREATURE[Math.min(v, CREATURE.length - 1)]) return out;
        ArmorSets.Set set = pick(ArmorCatalog.forCreature(dead.kind()), rank, killerClass, r);
        if (set != null) {
            ItemStack stack = piece(set, rank.tier(), r);
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    /** What a boss leaves: maybe a piece of one of its sets, very rarely a divine piece. */
    public static List<ItemStack> bossPieces(BossKind kind, PlayerRank rank, boolean ascended, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        int tier = rank.tier();
        int count = ascended ? 1 + (r.nextFloat() < BOSS_SECOND ? 1 : 0) : r.nextFloat() < BOSS[Math.min(tier, BOSS.length - 1)] ? 1 : 0;
        for (int i = 0; i < count; i++) {
            ArmorSets.Set set = kind == BossKind.BRUTE ? BossSets.forRank(rank) : pick(ArmorCatalog.forBoss(kind), rank, null, r);
            if (set == null) continue;
            ItemStack stack = piece(set, tier, r);
            if (!stack.isEmpty()) out.add(stack);
        }
        ItemStack divine = divinePiece(rank, ascended, r);
        if (!divine.isEmpty()) out.add(divine);
        return out;
    }

    public static ItemStack divinePiece(PlayerRank rank, boolean ascended, RandomSource r) {
        float chance = rank == PlayerRank.S ? (ascended ? DIVINE_S_ASCENDED : DIVINE_S) : rank == PlayerRank.A && ascended ? DIVINE_A_ASCENDED : 0;
        if (chance <= 0 || r.nextFloat() >= chance) return ItemStack.EMPTY;
        List<ArmorSets.Set> divine = ArmorCatalog.divineSets();
        return piece(divine.get(r.nextInt(divine.size())), rank.tier(), r);
    }

    /**
     * A village request's armor reward: a piece of the giver's craft's starter set, now and then. Empty most of the
     * time. Called while the request is built, so the reward shows on the offer.
     */
    public static ItemStack questPiece(String profession, boolean dangerous, RandomSource r) {
        if (r.nextFloat() >= (dangerous ? QUEST_DANGEROUS : QUEST)) return ItemStack.EMPTY;
        ArmorSets.Set set = ArmorCatalog.PROFESSIONS.get(profession);
        if (set == null) {
            List<ArmorSets.Set> all = ArmorCatalog.villagerSets();
            set = all.get(r.nextInt(all.size()));
        }
        return piece(set, 0, r);
    }

    // ------------------------------------------------------------------ hooks

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !SystemAPI.hasSystem(p)) return;
        RandomSource r = dead.getRandom();
        List<ItemStack> drops;
        if (dead instanceof GateBossEntity boss) {
            if (GateService.isDungeon(level)) return; // the reward cache holds them
            drops = bossPieces(boss.kind(), boss.rank(), boss.isAscended(), r);
        } else if (dead instanceof GateMonsterEntity m) {
            PlayerRank rank = com.beyondsolo.enemy.Enemies.rankOf((Mob) m);
            if (rank == null) return;
            SystemData d = SystemAPI.get(p);
            drops = creatureDrops(m, rank, d.isAwakened() ? d.playerClass() : null, r);
        } else {
            return;
        }
        if (drops.isEmpty()) return;
        Vec3 at = dead.position().add(0, dead.getBbHeight() * 0.5, 0);
        for (ItemStack s : drops) {
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, s);
            item.setDeltaMovement((r.nextDouble() - 0.5) * 0.1, 0.3, (r.nextDouble() - 0.5) * 0.1);
            item.setDefaultPickUpDelay();
            item.setGlowingTag(true);
            event.getDrops().add(item);
        }
        // a set piece falls with a glint
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 14, 0.25, 0.4, 0.25, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    @SubscribeEvent
    static void onBossLoot(GateLoot.BossLootEvent event) {
        // set pieces lead the cache, so they come out first and get called out
        event.loot.addAll(0, bossPieces(event.boss, event.rank, event.ascended, event.level.random));
    }

    private ArmorDrops() {}
}
