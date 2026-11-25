package com.beyondsolo.combat;

import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.beyondsolo.system.Balance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The mythic weapons' arts: each comes in beats, with its name over the wielder's screen. Hellsovereign's Fang cuts
 * three times in hellfire and keeps burning; Winterborne Requiem freezes everything round it and then shatters what it
 * froze; Stormcaller's Edict calls the storm onto every foe and then its verdict onto the strongest.
 */
final class MythicArts {
    /** Ticks between the Fang's three cuts. */
    static final int REND_GAP = 3;
    /** Ticks from Absolute Zero's strike to the requiem that shatters the ice. */
    static final int REQUIEM_DELAY = 30;
    /** Ticks from the storm's bolts to the verdict's pillar. */
    static final int VERDICT_DELAY = 10;

    /** Hellsovereign's Fang: three cuts of hellfire ahead, then six seconds of hellfire on every strike. */
    static List<Vec3> infernalRend(ServerLevel level, Player p, float power) {
        for (int i = 0; i < 3; i++) {
            boolean last = i == 2;
            ArsenalAbilities.later(p, level, i * REND_GAP, () -> {
                for (LivingEntity e : ArsenalAbilities.cone(p, p.position(), ArsenalAbilities.flatLook(p), 4.5, 70)) {
                    float dmg = Balance.flatDamage(p, 2.5f) + power * 0.25f;
                    if (last) ArsenalHits.artFinisher(Signature.INFERNAL_REND, p, e, dmg);
                    else ArsenalHits.artHit(Signature.INFERNAL_REND, p, e, dmg);
                    Statuses.apply(e, DamageElement.FIRE, 25, p);
                }
                ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f + level.random.nextFloat() * 0.2f);
                ArsenalAbilities.sound(level, p, SoundEvents.BLAZE_SHOOT, 0.7f, 0.6f);
            });
        }
        p.addEffect(new MobEffectInstance(ModEffects.INFERNAL_REND, 6 * 20 + 2 * REND_GAP, 0, false, true, true));
        ArsenalAbilities.sound(level, p, SoundEvents.GHAST_SHOOT, 0.7f, 0.5f);
        return List.of();
    }

    /**
     * Winterborne Requiem: the lance goes into the ground and everything within 8 blocks freezes; a moment later the
     * requiem shatters the ice on whatever it froze. Points: the frozen foes.
     */
    static List<Vec3> absoluteZero(ServerLevel level, Player p, float power) {
        List<Vec3> frozen = new ArrayList<>();
        List<LivingEntity> held = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 8)) {
            ArsenalHits.freeze(e, p, REQUIEM_DELAY + 30);
            ArsenalHits.artHit(Signature.ABSOLUTE_ZERO, p, e, Balance.flatDamage(p, 4f));
            held.add(e);
            if (frozen.size() < 24) frozen.add(e.position());
        }
        ArsenalAbilities.sound(level, p, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.POWDER_SNOW_BREAK, 1.4f, 0.6f);
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
        ArsenalAbilities.later(p, level, REQUIEM_DELAY, () -> {
            List<Vec3> shattered = new ArrayList<>();
            for (LivingEntity e : held) {
                if (!e.isAlive() || !e.hasEffect(ModEffects.FROZEN) || e.distanceTo(p) > 14) continue;
                e.removeEffect(ModEffects.FROZEN);
                ArsenalHits.artFinisher(Signature.ABSOLUTE_ZERO, p, e, Balance.flatDamage(p, 5f) + power * 0.4f);
                if (shattered.size() < 24) shattered.add(e.position());
            }
            for (Vec3 at : shattered) ArsenalAbilities.soundAt(level, at, SoundEvents.GLASS_BREAK, 1.2f, 0.7f + level.random.nextFloat() * 0.3f);
            ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.3f, 0.5f);
            ArsenalAbilities.send(p, Signature.ABSOLUTE_ZERO, SignatureFxPayload.beat(1), shattered);
        });
        return frozen;
    }

    /**
     * Stormcaller's Edict: the storm answers the raised hammer, lightning falls on up to six foes within 12 blocks,
     * and then the verdict: a pillar of thunder on the toughest of them. Points: where the bolts fell.
     */
    static List<Vec3> heavensVerdict(ServerLevel level, Player p, float power) {
        List<LivingEntity> foes = ArsenalHits.foesAround(p, p.position(), 12).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(6).toList();
        List<Vec3> struck = new ArrayList<>();
        for (LivingEntity e : foes) {
            ArsenalHits.artHit(Signature.HEAVENS_VERDICT, p, e, Balance.flatDamage(p, 5f) + power * 0.4f);
            struck.add(e.position());
        }
        if (struck.isEmpty()) struck.add(p.position().add(ArsenalAbilities.flatLook(p).scale(4)));
        ArsenalAbilities.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.9f);
        for (Vec3 at : struck) ArsenalAbilities.soundAt(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 0.8f);
        ArsenalAbilities.later(p, level, VERDICT_DELAY, () -> {
            LivingEntity judged = foes.stream().filter(e -> e.isAlive() && e.distanceTo(p) < 20)
                    .max(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
            if (judged == null) return;
            ArsenalHits.artFinisher(Signature.HEAVENS_VERDICT, p, judged, Balance.flatDamage(p, 7f) + power * 0.4f);
            judged.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, true, true), p);
            ArsenalAbilities.soundAt(level, judged.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8f, 0.6f);
            ArsenalAbilities.soundAt(level, judged.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.4f, 0.7f);
            ArsenalAbilities.send(p, Signature.HEAVENS_VERDICT, SignatureFxPayload.beat(1), List.of(judged.position()));
        });
        return struck;
    }

    private MythicArts() {}
}
