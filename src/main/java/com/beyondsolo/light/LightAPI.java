package com.beyondsolo.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.network.LightActionPayload;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.network.LightSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillPassives;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The one entry point for the light: whether a hunter is sworn to it, whether they are a ruler of light and of which
 * mantle, the call to the Investiture, and swearing, investing and setting them free again (commands, tests).
 *
 * <p>The road: in a new awakening temple a wall off the antechamber hides the chapel of the Envoy of Dawn
 * ({@link Chapel}). A hunter who finds it and takes the Envoy's covenant ({@link Covenant}) wakes sworn to the light
 * instead of falling to the Arbiter: the System still binds to them, and the light's first power with it (Judgement;
 * Seraph Form comes on the road). Sworn, they can never take a crown. Once they have changed jobs and reached
 * {@link #minLevel} the light calls them to the Investiture ({@link Vigil}): a vigil kept against the dark, and the
 * mantle they take up at its end ({@link Mantle}) makes them a ruler of light, with the light's sanctum and the
 * mantle's authority ({@link RulerPowers}).</p>
 *
 * <p>A ruler of light stands where a monarch stands: stronger than any other hunter (more health, harder blows, more
 * mana, harder blows against the monarchs from beyond the gates, whose domains their sanctum can hold back) and still
 * a person: no immunity and no second life.</p>
 */
public final class LightAPI {
    private static final ResourceLocation HEALTH = BeyondSolo.id("light/health");
    private static final ResourceLocation DAMAGE = BeyondSolo.id("light/damage");

    public static LightData get(Player player) {
        return player.getData(LightModule.LIGHT.get());
    }

    /** Whether they woke sworn to the light. */
    public static boolean sworn(Player player) {
        return get(player).sworn();
    }

    /** Whether they are a ruler of light: sworn, and invested with a mantle. */
    public static boolean invested(Player player) {
        return get(player).invested();
    }

    /** The mantle they took up, or null before the Investiture. Server side (the client has {@code LightClient}). */
    public static @Nullable Mantle mantle(Player player) {
        LightData d = get(player);
        return d.sworn() ? d.mantle() : null;
    }

    public static int minLevel() {
        return LightConfig.minLevel();
    }

    // ================================================================ sworn

    /**
     * Swears the hunter to the light (the covenant taken; commands): the record, the light's powers they are owed now,
     * the title, a third of a ruler's mana. A monarch gives their crown up first.
     */
    public static void swear(ServerPlayer player) {
        if (MonarchAPI.crowned(player)) MonarchAPI.uncrown(player);
        LightData data = get(player);
        if (!data.sworn()) data.swear(true, player.level().getGameTime());
        sync(player);
    }

    /**
     * The System has bound to a hunter who swore to the light ({@code awakening.AwakeningService}): the light's first
     * powers come with it, and the System names them for it.
     */
    public static void awoken(ServerPlayer player) {
        if (!sworn(player)) swear(player);
        List<Skill> given = RulerPowers.grant(player);
        SkillPassives.apply(player);
        SystemAPI.setTitle(player, LightModule.VESSEL);
        sync(player);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.light_sworn.title"),
                Component.translatable("notify.beyondsolo.light_sworn.body", given.isEmpty() ? "" : given.get(0).displayName()), 320));
    }

    /** Sets the hunter free of the light (commands, tests): no longer sworn, every power and the mantle gone. */
    public static void unswear(ServerPlayer player) {
        LightSanctum.end(player);
        Vigil.cancel(player);
        LightData data = get(player);
        data.swear(false, 0);
        RulerPowers.forgetAll(player);
        SkillPassives.apply(player);
        sync(player);
    }

    /** The Envoy was turned away (they can still come back to it before the Arbiter takes them). */
    public static void refused(ServerPlayer player) {
        get(player).setRefused(true);
    }

    // ================================================================ the Investiture

    /** Whether the light would call this hunter to the Investiture now: sworn, the job changed, the level reached, no mantle yet. */
    public static boolean eligible(Player player) {
        SystemData sys = SystemAPI.get(player);
        return sys.isAwakened() && sys.hasSystem() && sworn(player) && !invested(player)
                && JobAPI.hasChangedJob(player) && sys.level() >= minLevel();
    }

    /** Calls the hunter to the Investiture if they are ready and haven't been called: the light's notice, the feather and the quest. */
    public static boolean callIfReady(ServerPlayer player) {
        if (get(player).called() || !eligible(player)) return false;
        call(player);
        return true;
    }

    /** Calls the hunter to the Investiture now, whatever their level (commands, tests). */
    public static void call(ServerPlayer player) {
        get(player).setCalled(true);
        sync(player);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.light_called.title"),
                Component.translatable("notify.beyondsolo.light_called.body"), 300));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1f, 0.8f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.5f);
        PacketDistributor.sendToPlayer(player, new LightFxPayload(LightFxPayload.CALLED, player.getId(), RulerPowers.DAWN_GOLD, 0, 140));
        giveFeather(player);
        if (!QuestEngine.data(player).has(LightModule.QUEST_TEMPLATE)) {
            QuestEngine.start(player, LightModule.QUEST_TEMPLATE, QuestTemplate.Context.NONE);
        }
    }

    /** The Envoy's feather, the vigil's key, if they don't carry one. */
    public static void giveFeather(ServerPlayer player) {
        if (player.getInventory().contains(new ItemStack(LightModule.FEATHER.get()))) return;
        ItemStack feather = new ItemStack(LightModule.FEATHER.get());
        if (!player.getInventory().add(feather)) player.drop(feather, false);
    }

    /**
     * Invests the hunter with a mantle: a ruler of light. Its sanctum and its authority (another mantle's authority is
     * forgotten), the ruler's strength, the mantle's title, and their light in its colour.
     */
    public static void invest(ServerPlayer player, Mantle mantle) {
        if (MonarchAPI.crowned(player)) MonarchAPI.uncrown(player);
        LightData data = get(player);
        if (!data.sworn()) data.swear(true, player.level().getGameTime());
        data.invest(mantle, player.level().getGameTime());
        data.setCalled(true);
        RulerPowers.grant(player);
        SkillPassives.apply(player);
        SystemAPI.setTitle(player, mantle.titleKey());
        QuestEngine.progress(player, LightModule.INVESTITURE_KEY, 1);
        sync(player);
    }

    /** Takes the mantle back (commands, tests): the sanctum and the authority are forgotten; they stay sworn. */
    public static void divest(ServerPlayer player) {
        LightSanctum.end(player);
        get(player).invest(null, 0);
        RulerPowers.grant(player);
        SkillPassives.apply(player);
        sync(player);
    }

    /** Wipes the whole record (commands): never sworn, never called, no tries. */
    public static void reset(ServerPlayer player) {
        unswear(player);
        get(player).reset();
        sync(player);
    }

    // ================================================================ strength

    /**
     * A ruler of light's strength, as transient modifiers (put back on login, respawn and every change with the skill
     * passives): a bigger health pool and harder blows. Taken off again from anyone who is not invested.
     */
    public static void applyBonuses(ServerPlayer player) {
        boolean on = invested(player);
        set(player.getAttribute(Attributes.MAX_HEALTH), HEALTH, on ? LightConfig.healthBonus() : 0);
        set(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, on ? LightConfig.damageBonus() : 0);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void set(@Nullable AttributeInstance inst, ResourceLocation id, double amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount > 0) inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The mana the light gives on top of a hunter's own (a third of it before the Investiture). */
    public static float manaBonus(Player player) {
        if (!sworn(player)) return 0;
        float full = (float) LightConfig.manaBonus();
        return invested(player) ? full : full / 3f;
    }

    // ================================================================ the record

    /** A monarch from beyond the gates fell while this hunter, sworn to the light, fought it. */
    public static void monarchSlain(ServerPlayer player) {
        if (!sworn(player)) return;
        get(player).addMonarchSlain();
        SystemAPI.setTitle(player, LightModule.DAWN_OVER_CROWNS);
    }

    /** Their sanctum held a monarch's domain back. */
    static void clashed(ServerPlayer player) {
        SystemAPI.setTitle(player, LightModule.UNBROKEN_LIGHT);
    }

    public static void usedAuthority(ServerPlayer player) {
        get(player).addAuthority();
    }

    /** Their judgement struck {@code n} foes. */
    public static void judged(ServerPlayer player, int n) {
        if (n > 0) get(player).addJudged(n);
    }

    static void vigilTried(ServerPlayer player) {
        get(player).addAttempt();
    }

    static void vigilFailed(ServerPlayer player) {
        get(player).setRetryAt(player.server.overworld().getGameTime() + LightConfig.retryMinutes() * 1200L);
    }

    /** When the player may keep the vigil again (game time; 0 = now). */
    public static void setRetryAt(ServerPlayer player, long at) {
        get(player).setRetryAt(at);
    }

    // ================================================================ network

    /** What the client asked for: a crown key, the answer to the Envoy, a mantle taken up, or the Investiture skipped. */
    public static void handle(ServerPlayer player, LightActionPayload p) {
        switch (p.action()) {
            case LightActionPayload.POWER -> RulerPowers.castKey(player, p.value());
            case LightActionPayload.COVENANT -> Covenant.answer(player, p.value());
            case LightActionPayload.CHOOSE -> Vigil.choose(player, Mantle.byIndex(p.value()));
            case LightActionPayload.SKIP -> Vigil.skipInvestiture(player);
            default -> {
            }
        }
    }

    public static void sync(ServerPlayer player) {
        LightData data = get(player);
        Mantle m = data.sworn() ? data.mantle() : null;
        PacketDistributor.sendToPlayer(player, new LightSyncPayload(data.sworn(), m == null ? -1 : m.ordinal(), data.called(), minLevel()));
    }

    static void info(ServerPlayer p, String key, Object... args) {
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo." + key + ".title"),
                Component.translatable("notify.beyondsolo." + key + ".body", args)));
    }

    private LightAPI() {}
}
