package com.beyondsolo.skill;

import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.network.SkillSyncPayload;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one entry point for reading and changing a player's skills. Every write syncs the client, plays the
 * skill moment (acquired / level up) and fires a {@link SkillEvent}.
 *
 * <p>For the job change: {@link #grantAdvanced} teaches the class's advanced skills, {@link #learn} any single
 * one, {@link #forget} takes one away, and {@link #refreshForClass} drops skills that no longer fit after the
 * class changed.</p>
 */
public final class SkillAPI {
    /** Fatigue every cast adds (scaled by the player's exhaustion resistance like everything else). */
    public static final float CAST_EXHAUSTION = 1.5f;

    public static SkillData get(Player player) {
        return player.getData(ModAttachments.SKILLS);
    }

    public static int level(Player player, Skill skill) {
        return get(player).level(skill.path());
    }

    public static boolean knows(Player player, Skill skill) {
        return get(player).knows(skill.path());
    }

    // ---------- learning ----------

    /**
     * Teaches a skill at a level. Returns false if the player already knows it (use {@link #addLevels} for that).
     * Doesn't check class or rank: callers decide what fits (books and level-ups only offer fitting skills).
     */
    public static boolean learn(ServerPlayer player, Skill skill, int level, SkillEvent.Source source) {
        SkillData data = get(player);
        if (data.knows(skill.path())) return false;
        data.learn(skill.path(), Math.max(1, Math.min(Skill.MAX_LEVEL, level)), player.level().getGameTime());
        if (skill.isActive()) {
            for (int i = 0; i < SkillData.SLOTS; i++) {
                if (data.slot(i).isEmpty()) {
                    data.setSlot(i, skill.path());
                    break;
                }
            }
        }
        SkillPassives.apply(player);
        sync(player);
        moment(player, SkillMomentPayload.LEARNED, skill, data.level(skill.path()));
        NeoForge.EVENT_BUS.post(new SkillEvent.Learned(player, skill, source));
        return true;
    }

    /** Raises a known skill by some levels (books, quest rewards). Returns how many levels it actually gained. */
    public static int addLevels(ServerPlayer player, Skill skill, int levels) {
        SkillData.Known k = get(player).get(skill.path());
        if (k == null || levels <= 0) return 0;
        int target = Math.min(Skill.MAX_LEVEL, k.level + levels);
        int gained = target - k.level;
        if (gained <= 0) return 0;
        k.level = target;
        k.proficiency = 0;
        SkillPassives.apply(player);
        sync(player);
        moment(player, SkillMomentPayload.LEVEL_UP, skill, target);
        NeoForge.EVENT_BUS.post(new SkillEvent.LevelUp(player, skill, target));
        return gained;
    }

    /** Adds proficiency to a known skill, levelling it up when the bar fills. */
    public static void addProficiency(ServerPlayer player, Skill skill, int amount) {
        SkillData.Known k = get(player).get(skill.path());
        if (k == null || amount <= 0 || k.level >= Skill.MAX_LEVEL) return;
        k.proficiency += amount;
        boolean levelled = false;
        while (k.level < Skill.MAX_LEVEL && k.proficiency >= Skill.proficiencyToNext(k.level)) {
            k.proficiency -= Skill.proficiencyToNext(k.level);
            k.level++;
            levelled = true;
        }
        if (k.level >= Skill.MAX_LEVEL) k.proficiency = 0;
        if (levelled) {
            SkillPassives.apply(player);
            moment(player, SkillMomentPayload.LEVEL_UP, skill, k.level);
            NeoForge.EVENT_BUS.post(new SkillEvent.LevelUp(player, skill, k.level));
        }
        sync(player);
    }

    /** Takes a skill away (e.g. a class skill after a job change to another class). */
    public static void forget(ServerPlayer player, Skill skill) {
        get(player).forget(skill.path());
        SkillPassives.apply(player);
        sync(player);
    }

    /**
     * Teaches every skill the player's class and rank have unlocked by now (level-gated, not advanced).
     * Called on awakening, level-ups and login. Returns what was learned.
     */
    public static List<Skill> grantUnlocked(ServerPlayer player) {
        SystemData sys = SystemAPI.get(player);
        List<Skill> learned = new ArrayList<>();
        if (!sys.isAwakened()) return learned;
        for (Skill s : Skills.all()) {
            if (!s.fits(sys.playerClass())) continue;
            int at = s.unlockLevel(sys.rank());
            if (at < 1 || at > sys.level() || knows(player, s)) continue;
            if (learn(player, s, 1, SkillEvent.Source.LEVEL)) learned.add(s);
        }
        return learned;
    }

    /** The job change hook: teaches the advanced skills of the player's current class. Returns what was learned. */
    public static List<Skill> grantAdvanced(ServerPlayer player) {
        SystemData sys = SystemAPI.get(player);
        List<Skill> learned = new ArrayList<>();
        for (Skill s : Skills.advancedFor(sys.playerClass())) {
            if (learn(player, s, 1, SkillEvent.Source.JOB_CHANGE)) learned.add(s);
        }
        return learned;
    }

    /** After a class change: forgets class skills of other classes, then grants what the new class has unlocked. */
    public static void refreshForClass(ServerPlayer player) {
        SystemData sys = SystemAPI.get(player);
        for (Skill s : new ArrayList<>(Skills.known(get(player)))) {
            if (!s.fits(sys.playerClass())) get(player).forget(s.path());
        }
        SkillPassives.apply(player);
        sync(player);
        grantUnlocked(player);
    }

    // ---------- the skill bar ----------

    /** Puts an active skill the player knows into a bar slot (an empty path clears the slot). */
    public static boolean assign(ServerPlayer player, int slot, String path) {
        SkillData data = get(player);
        if (slot < 0 || slot >= SkillData.SLOTS) return false;
        if (!path.isEmpty()) {
            Skill s = Skills.get(path);
            if (s == null || !s.isActive() || !data.knows(path)) return false;
        }
        data.setSlot(slot, path);
        sync(player);
        return true;
    }

    /** Casts the skill in a bar slot. Returns true if it went off. */
    public static boolean castSlot(ServerPlayer player, int slot) {
        String path = get(player).slot(slot);
        Skill s = path.isEmpty() ? null : Skills.get(path);
        return s != null && cast(player, s);
    }

    /** Casts a known active skill: checks state, cooldown and mana, runs it, then spends and starts the cooldown. */
    public static boolean cast(ServerPlayer player, Skill skill) {
        SkillData data = get(player);
        SystemData sys = SystemAPI.get(player);
        int level = data.level(skill.path());
        if (!skill.isActive() || level <= 0 || skill.action() == null) return false;
        if (!sys.isAwakened() || sys.isCollapsed() || !player.isAlive() || player.isSpectator() || player.isSleeping()) {
            refuse(player, skill, SkillMomentPayload.BLOCKED);
            return false;
        }
        long now = player.level().getGameTime();
        if (data.cooldownLeft(skill.path(), now) > 0) {
            refuse(player, skill, SkillMomentPayload.COOLDOWN);
            return false;
        }
        float cost = player.isCreative() ? 0 : skill.manaCost(level);
        if (sys.mana() < cost) {
            refuse(player, skill, SkillMomentPayload.NO_MANA);
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new SkillEvent.Cast(player, skill, level)).isCanceled()) {
            refuse(player, skill, SkillMomentPayload.BLOCKED);
            return false;
        }
        if (!skill.action().cast(player, skill, level)) {
            refuse(player, skill, SkillMomentPayload.NO_TARGET);
            return false;
        }
        if (cost > 0) SystemAPI.consumeMana(player, cost);
        data.startCooldown(skill.path(), now, player.isCreative() ? Math.min(20, skill.cooldown(level)) : skill.cooldown(level));
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillFx.play(player, SkillFxPayload.Kind.CAST, skill.color(), 1.2f);
        if (!player.isCreative()) SystemAPI.addExhaustion(player, CAST_EXHAUSTION);
        addProficiency(player, skill, 1);
        sync(player);
        return true;
    }

    /** Clears every cooldown (commands, tests). */
    public static void resetCooldowns(ServerPlayer player) {
        get(player).clearCooldowns();
        sync(player);
    }

    // ---------- buffs other skills read ----------

    public static void setBuff(ServerPlayer player, String key, int ticks) {
        get(player).setBuff(key, player.level().getGameTime() + ticks);
        sync(player);
    }

    public static boolean hasBuff(Player player, String key) {
        return get(player).hasBuff(key, player.level().getGameTime());
    }

    public static void clearBuff(ServerPlayer player, String key) {
        get(player).clearBuff(key);
        sync(player);
    }

    // ---------- misc ----------

    /** Wipes all skills (used by /system reset and tests). */
    public static void reset(ServerPlayer player) {
        get(player).reset();
        SkillPassives.apply(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SkillSyncPayload(get(player).serializeNBT(player.registryAccess())));
    }

    static void moment(ServerPlayer player, int moment, Skill skill, int level) {
        PacketDistributor.sendToPlayer(player, new SkillMomentPayload(moment, skill.path(), level));
    }

    private static void refuse(ServerPlayer player, Skill skill, int reason) {
        moment(player, SkillMomentPayload.REFUSED, skill, reason);
    }

    static @Nullable Skill skill(String path) {
        return Skills.get(path);
    }

    private SkillAPI() {}
}
