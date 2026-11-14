package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Wires skills into the rest of the game: unlocks on level-ups, passives, buffs on hits, stealth. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SkillHooks {
    // ---- unlocking ----

    @SubscribeEvent
    static void onAwakened(SystemEvent.Awakened event) {
        // a fresh evaluation can change the class: drop what no longer fits, then grant what's unlocked
        SkillAPI.refreshForClass(event.getPlayer());
    }

    @SubscribeEvent
    static void onLevelUp(SystemEvent.LevelUp event) {
        ServerPlayer p = event.getPlayer();
        SkillAPI.grantUnlocked(p);
        // passives grow as the player grows
        int levels = event.getNewLevel() - event.getOldLevel();
        for (Skill s : Skills.known(SkillAPI.get(p))) {
            if (!s.isActive()) SkillAPI.addProficiency(p, s, 4 * levels);
        }
    }

    @SubscribeEvent
    static void onRankChanged(SystemEvent.RankChanged event) {
        SkillAPI.grantUnlocked(event.getPlayer());
    }

    // ---- keeping state alive ----

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SkillPassives.apply(p);
            SkillAPI.sync(p);
            SkillAPI.grantUnlocked(p);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SkillPassives.apply(p);
            SkillAPI.sync(p);
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SkillPassives.apply(p);
            SkillAPI.sync(p);
        }
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0 && SystemAPI.isAwakened(p)) {
            SkillPassives.tickSecond(p);
        }
    }

    // ---- hits ----

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();

        // Arrow Rain never hurts its caster
        if (direct instanceof AbstractArrow arrow && arrow.getTags().contains(SkillActions.RAIN_TAG) && arrow.getOwner() == target) {
            event.setCanceled(true);
            return;
        }

        float amount = event.getAmount();
        long now = target.level().getGameTime();

        // Hunter's Mark: marked creatures take more from everyone
        long[] mark = SkillActions.MARKS.get(target);
        if (mark != null) {
            if (mark[0] > now) amount *= 1 + mark[1] / 100f;
            else SkillActions.MARKS.remove(target);
        }

        if (attacker instanceof ServerPlayer p && target != p) {
            SkillData data = SkillAPI.get(p);
            boolean melee = direct == p;
            if (melee && data.hasBuff(SkillActions.BUFF_SHADOWSTEP, now)) {
                amount *= 1 + SkillActions.shadowstepBonus(Math.max(1, data.level(Skills.SHADOWSTEP.path()))) / 100f;
                p.crit(target);
                SkillAPI.clearBuff(p, SkillActions.BUFF_SHADOWSTEP);
            }
            if (melee && data.hasBuff(SkillActions.BUFF_VENOM_EDGE, now)) {
                int l = Math.max(1, data.level(Skills.VENOM_EDGE.path()));
                Statuses.apply(target, DamageElement.POISON, 14 + 2 * l, p);
            }
            if (data.hasBuff(SkillActions.BUFF_VEIL, now)) {
                amount *= 1 + (60 + 5 * Math.max(1, data.level(Skills.VEIL_OF_NIGHT.path()))) / 100f;
                endStealth(p, SkillActions.BUFF_VEIL);
            }
            if (data.hasBuff(SkillActions.BUFF_SHROUD, now)) endStealth(p, SkillActions.BUFF_SHROUD);
            if (data.hasBuff(SkillActions.BUFF_GAZE, now)) {
                amount *= 1 + SkillActions.weakSpotBonus(Math.max(1, data.level(Skills.APPRAISING_GAZE.path()))) / 100f;
            }
            int eagle = data.level(Skills.EAGLE_EYE.path());
            if (eagle > 0 && direct instanceof AbstractArrow) amount *= 1 + 0.04f * eagle;
        }
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    private static void endStealth(ServerPlayer p, String buff) {
        SkillAPI.clearBuff(p, buff);
        p.removeEffect(MobEffects.INVISIBILITY);
    }

    // ---- stealth ----

    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player p
                && (SkillAPI.hasBuff(p, SkillActions.BUFF_VEIL) || SkillAPI.hasBuff(p, SkillActions.BUFF_SHROUD))) {
            event.setCanceled(true);
        }
    }

    private SkillHooks() {}
}
