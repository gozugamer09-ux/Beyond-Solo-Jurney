package com.beyondsolo.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.network.MonarchActionPayload;
import com.beyondsolo.network.MonarchSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.SkillPassives;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.tower.TowerAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The one entry point for the monarch path: whether a hunter is a monarch and of what, calling them to the throne,
 * crowning them and taking the crown back (commands, tests).
 *
 * <p>The road: the Crimson Citadel's lord leaves the Sigil of Dominion; a hunter who holds it, has changed jobs and
 * reached {@link #minLevel} is called (a quest), raises the sigil, and is taken to the Hall of the Empty Throne
 * ({@link ThroneTrial}). Whoever comes out of it crowned picks what they rule ({@link Dominion}) and gets the monarch's
 * powers ({@link MonarchPowers}): a domain, dread, the crown form and their dominion's authority, on the crown keys.</p>
 *
 * <p>A monarch is stronger than any other hunter (more health, harder blows, more mana, no pressure from the monarchs
 * from beyond the gates, whose domains theirs can hold back) and is still a person: no immunity and no second life.</p>
 */
public final class MonarchAPI {
    private static final ResourceLocation HEALTH = BeyondSolo.id("monarch/health");
    private static final ResourceLocation DAMAGE = BeyondSolo.id("monarch/damage");

    public static MonarchData get(Player player) {
        return player.getData(MonarchModule.MONARCH.get());
    }

    public static boolean crowned(Player player) {
        return get(player).crowned();
    }

    /** What the player rules, or null if they are not a monarch. Server side (the client has {@code MonarchClient}). */
    public static @Nullable Dominion dominion(Player player) {
        return get(player).dominion();
    }

    public static int minLevel() {
        return MonarchConfig.MIN_LEVEL.get();
    }

    /** Whether the throne would take this hunter now: the sigil held, the job changed, the level reached, not yet crowned. */
    public static boolean eligible(Player player) {
        SystemData sys = SystemAPI.get(player);
        return sys.isAwakened() && sys.hasSystem() && !crowned(player) && TowerAPI.hasSigil(player)
                && JobAPI.hasChangedJob(player) && sys.level() >= minLevel() && !com.beyondsolo.light.LightAPI.sworn(player);
    }

    /** Calls the hunter to the throne if they are ready and haven't been called: the System's notice and the quest. */
    public static boolean callIfReady(ServerPlayer player) {
        MonarchData data = get(player);
        if (data.called() || !eligible(player)) return false;
        call(player);
        return true;
    }

    /** Calls the hunter to the throne now, whatever they hold or their level (commands, tests). */
    public static void call(ServerPlayer player) {
        get(player).setCalled(true);
        sync(player);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.monarch_called.title"),
                Component.translatable("notify.beyondsolo.monarch_called.body"), 300));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 0.5f);
        if (!QuestEngine.data(player).has(MonarchModule.QUEST_TEMPLATE)) {
            QuestEngine.start(player, MonarchModule.QUEST_TEMPLATE, QuestTemplate.Context.NONE);
        }
    }

    /**
     * The Sigil of Dominion raised: the throne trial if the hunter is ready, otherwise what it waits for. Returns true if
     * a trial is on its way.
     */
    public static boolean raiseSigil(ServerPlayer player) {
        if (crowned(player)) {
            info(player, "monarch_sigil_quiet", dominion(player).title());
            return false;
        }
        // a hunter sworn to the light can never take a crown
        if (com.beyondsolo.light.LightAPI.sworn(player)) {
            info(player, "monarch_sigil_light");
            return false;
        }
        SystemData sys = SystemAPI.get(player);
        if (!sys.isAwakened() || !sys.hasSystem() || !JobAPI.hasChangedJob(player) || sys.level() < minLevel()) {
            info(player, "monarch_sigil_waits", minLevel());
            return false;
        }
        if (!get(player).called()) call(player);
        return ThroneTrial.requestStart(player);
    }

    // ================================================================ the crown

    /**
     * Crowns the hunter: what they rule, the three powers every monarch has and their dominion's authority (another
     * crown's authority is forgotten), the monarch's strength, the crown's title, and their aura in its colour.
     */
    public static void crown(ServerPlayer player, Dominion dominion) {
        // one or the other: a crown sets a hunter free of the light (commands; the throne never takes one sworn to it)
        if (com.beyondsolo.light.LightAPI.sworn(player)) com.beyondsolo.light.LightAPI.unswear(player);
        MonarchData data = get(player);
        data.crown(dominion, player.level().getGameTime());
        data.setCalled(true);
        MonarchPowers.grant(player, dominion);
        SkillPassives.apply(player);
        // the Night's monarch raises shadows: the skills only shadow raisers hold come with it
        com.beyondsolo.skill.SkillAPI.refreshGated(player);
        SystemAPI.setTitle(player, dominion.titleKey());
        com.beyondsolo.worldboss.MonarchArmor.retint(player);
        ShadowService.sync(player);
        sync(player);
    }

    /** Takes the crown back (commands, tests): the powers are forgotten, the strength goes; shadows keep their rank. */
    public static void uncrown(ServerPlayer player) {
        MonarchDomain.end(player);
        get(player).crown(null, 0);
        MonarchPowers.forgetAll(player);
        SkillPassives.apply(player);
        com.beyondsolo.skill.SkillAPI.refreshGated(player);
        com.beyondsolo.worldboss.MonarchArmor.retint(player);
        ShadowService.sync(player);
        sync(player);
    }

    /** Wipes the whole record (commands): uncrowned, never called, no tries. */
    public static void reset(ServerPlayer player) {
        ThroneTrial.cancel(player);
        uncrown(player);
        get(player).reset();
        sync(player);
    }

    /**
     * A monarch's strength, as transient modifiers (put back on login, respawn and every change with the skill passives):
     * a bigger health pool and harder blows. Taken off again from anyone who is not crowned.
     */
    public static void applyBonuses(ServerPlayer player) {
        boolean on = crowned(player);
        set(player.getAttribute(Attributes.MAX_HEALTH), HEALTH, on ? MonarchConfig.HEALTH_BONUS.get() : 0);
        set(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, on ? MonarchConfig.DAMAGE_BONUS.get() : 0);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void set(@Nullable AttributeInstance inst, ResourceLocation id, double amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount > 0) inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The mana a monarch has on top of their own (see {@code SkillPassives}). */
    public static float manaBonus(Player player) {
        return crowned(player) ? MonarchConfig.MANA_BONUS.get().floatValue() : 0;
    }

    /** How many more shadows a monarch can have out at once (more again for the monarch of Night). */
    public static int extraShadows(Player player) {
        Dominion d = dominion(player);
        return d == null ? 0 : d == Dominion.NIGHT ? 8 : 4;
    }

    // ================================================================ the record

    /** A monarch from beyond the gates fell while this monarch fought it. */
    public static void monarchSlain(ServerPlayer player) {
        if (!crowned(player)) return;
        get(player).addMonarchSlain();
        SystemAPI.setTitle(player, MonarchModule.CROWN_AGAINST_CROWN);
    }

    /** Their domain held another monarch's back. */
    static void clashed(ServerPlayer player) {
        MonarchData data = get(player);
        data.addClash();
        SystemAPI.setTitle(player, MonarchModule.DOMAIN_UNBOWED);
    }

    public static void usedAuthority(ServerPlayer player) {
        get(player).addAuthority();
    }

    static void enterTrial(ServerPlayer player) {
        MonarchData data = get(player);
        data.addAttempt();
        data.enterTrial(player.level().dimension().location(), player.position(), player.getYRot());
    }

    static void leaveTrial(ServerPlayer player, boolean failed) {
        MonarchData data = get(player);
        data.leaveTrial();
        if (failed) data.setRetryAt(player.server.overworld().getGameTime() + MonarchConfig.RETRY_MINUTES.get() * 1200L);
    }

    /** When the player may try the throne again (game time; 0 = now). */
    public static void setRetryAt(ServerPlayer player, long at) {
        get(player).setRetryAt(at);
    }

    // ================================================================ network

    /** What the client asked for: a crown key, a crown picked at the throne, or the coronation skipped. */
    public static void handle(ServerPlayer player, MonarchActionPayload p) {
        switch (p.action()) {
            case MonarchActionPayload.POWER -> MonarchPowers.castCrown(player, p.value());
            case MonarchActionPayload.CHOOSE -> ThroneTrial.choose(player, Dominion.byIndex(p.value()));
            case MonarchActionPayload.SKIP -> ThroneTrial.skipCoronation(player);
            default -> {
            }
        }
    }

    public static void sync(ServerPlayer player) {
        MonarchData data = get(player);
        PacketDistributor.sendToPlayer(player, new MonarchSyncPayload(data.dominion() == null ? -1 : data.dominion().ordinal(), data.called(), minLevel()));
    }

    static void info(ServerPlayer p, String key, Object... args) {
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo." + key + ".title"),
                Component.translatable("notify.beyondsolo." + key + ".body", args)));
    }

    private MonarchAPI() {}
}
