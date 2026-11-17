package com.beyondsolo.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.skill.RuneArts;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where rune stones come from.
 * <ul>
 *   <li>Gate creatures killed by a hunter with the System: now and then the rune of their kind (a Gloomfang's
 *   Hunter's Howl...), far more often from elites and champions; elites and champions also leave class runes (a
 *   random class skill no higher than their rank) and sealed runes of their rank.</li>
 *   <li>Bosses: their own rune art, a sealed rune of the gate's rank and, in A and S gates, sometimes a deep rune
 *   (rune arts no creature carries), added to the reward cache through {@link GateLoot.BossLootEvent}. A boss fought
 *   outside a gate drops them on the ground.</li>
 *   <li>The System shop sells a sealed rune and the engraving runes; the CRAFT tab makes and fuses sealed runes.</li>
 * </ul>
 * A rune above the creature's rank is rarer: the chance halves for each grade above. Numbers are first guesses;
 * the balancing pass tunes them.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RuneDrops {
    /** Chance a gate creature leaves its kind's rune, by variant: normal, veteran, elite, champion. */
    public static final float[] KIND_RUNE = {0.004f, 0.008f, 0.04f, 0.08f};
    /** Chance of a class rune, by variant. */
    public static final float[] CLASS_RUNE = {0f, 0f, 0.02f, 0.04f};
    /** Chance of a sealed rune of the creature's rank, by variant. */
    public static final float[] SEALED_RUNE = {0f, 0.003f, 0.03f, 0.06f};
    /** Chance a boss leaves its own rune art (ascended bosses more often). */
    public static final float BOSS_RUNE = 0.15f;
    public static final float BOSS_RUNE_ASCENDED = 0.35f;
    /** Chance of a sealed rune of the gate's rank in the boss's cache (always from an ascended boss). */
    public static final float BOSS_SEALED = 0.35f;
    /** Chance of a deep rune from a boss of rank {@link #DEEP_FROM} or higher (twice for an ascended one). */
    public static final float DEEP_RUNE = 0.08f;
    public static final PlayerRank DEEP_FROM = PlayerRank.A;

    /** A rune above the creature's rank is rarer: the chance halves for each grade above. */
    public static float reach(float chance, PlayerRank runeGrade, PlayerRank rank) {
        int above = runeGrade.tier() - rank.tier();
        return above <= 0 ? chance : chance / (1 << Math.min(above, 8));
    }

    /** What runes a creature leaves (usually none). */
    public static List<ItemStack> creatureRunes(LivingEntity dead, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        PlayerRank rank = dead instanceof Mob mob ? Enemies.rankOf(mob) : null;
        if (rank == null) return out;
        int v = EnemyTraits.of(dead).variant().ordinal();
        if (dead instanceof GateMonsterEntity m) {
            Skill art = RuneArts.of(m.kind());
            if (art != null && r.nextFloat() < reach(KIND_RUNE[v], art.grade(), rank)) out.add(Runes.of(art));
        }
        if (r.nextFloat() < CLASS_RUNE[v]) {
            Skill s = classSkill(rank, r);
            if (s != null) out.add(Runes.of(s));
        }
        if (r.nextFloat() < SEALED_RUNE[v]) out.add(Runes.sealed(rank));
        return out;
    }

    /** A random class skill of any class (not a job change or subclass skill), no higher than this rank. */
    public static @Nullable Skill classSkill(PlayerRank rank, RandomSource r) {
        List<Skill> pool = new ArrayList<>();
        for (Skill s : Skills.all()) {
            if (s.owner() != null && s.subclass() == null && !s.advanced() && !s.rune() && s.grade().tier() <= rank.tier()) pool.add(s);
        }
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    /** What runes a boss adds to its loot. */
    public static List<ItemStack> bossRunes(BossKind kind, PlayerRank rank, boolean ascended, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        Skill art = RuneArts.of(kind);
        if (art != null && r.nextFloat() < reach(ascended ? BOSS_RUNE_ASCENDED : BOSS_RUNE, art.grade(), rank)) out.add(Runes.of(art));
        if (ascended || r.nextFloat() < BOSS_SEALED) out.add(Runes.sealed(rank));
        if (rank.tier() >= DEEP_FROM.tier() && r.nextFloat() < DEEP_RUNE * (ascended ? 2 : 1)) {
            List<Skill> deep = RuneArts.deep();
            out.add(Runes.of(deep.get(r.nextInt(deep.size()))));
        }
        return out;
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(dead instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !SystemAPI.hasSystem(p)) return;
        RandomSource r = dead.getRandom();
        List<ItemStack> runes;
        if (dead instanceof GateBossEntity boss) {
            if (GateService.isDungeon(level)) return; // the reward cache holds them
            runes = bossRunes(boss.kind(), boss.rank(), boss.isAscended(), r);
        } else {
            runes = creatureRunes(dead, r);
        }
        if (runes.isEmpty()) return;
        Vec3 at = dead.position().add(0, dead.getBbHeight() * 0.5, 0);
        ItemStack best = runes.get(0);
        for (ItemStack s : runes) {
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, s);
            item.setDeltaMovement((r.nextDouble() - 0.5) * 0.12, 0.32, (r.nextDouble() - 0.5) * 0.12);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
            if (Runes.gradeOf(s).tier() > Runes.gradeOf(best).tier()) best = s;
        }
        // the rune crystallises out of the body in a column of its light
        RuneFx.at(level, RuneFxPayload.Kind.DROPPED, Runes.colorOf(best), at, Math.max(1f, dead.getBbHeight()), 0,
                Runes.glyphOf(best) + 16 * Runes.gradeOf(best).tier());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.4f, 1.2f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.5f, 1.8f);
    }

    @SubscribeEvent
    static void onBossLoot(GateLoot.BossLootEvent event) {
        event.loot.addAll(bossRunes(event.boss, event.rank, event.ascended, event.level.random));
    }

    private RuneDrops() {}
}
