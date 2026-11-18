package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;

/**
 * Secret quests: never announced, never in the log until they are done. The System reveals one the moment the player
 * has done something brave or unusual on their own, completes it on the spot and pays for it. Each one only once.
 */
public final class Secrets {
    /** Slew a creature far stronger than you (a red name). */
    public static final String GIANT_SLAYER = "giant_slayer";
    /** Came through a last stand at the edge of death. */
    public static final String UNBROKEN = "unbroken";
    /** Defended a village through a raid to the end. */
    public static final String VALOR = "valor";
    /** Cleared a gate without taking a single hit. */
    public static final String UNTOUCHED = "untouched";
    /** Cleared, alone, a gate above your rank. */
    public static final String LONE_WOLF = "lone_wolf";
    /** Slew the creature that kept hunting you. */
    public static final String NEMESIS_SLAIN = "nemesis_slain";
    /** Finished the Untouchable streak's last tier. */
    public static final String FLAWLESS = "flawless";
    /** Finished the Frenzy streak's last tier. */
    public static final String UNSTOPPABLE = "unstoppable";
    /** Plundered the Silent City without waking what sleeps in it. */
    public static final String SILENT = "silent";

    public static final List<String> ALL = List.of(GIANT_SLAYER, UNBROKEN, VALOR, UNTOUCHED, LONE_WOLF, NEMESIS_SLAIN, FLAWLESS, UNSTOPPABLE, SILENT);

    /** First guesses for the balancing pass: XP (scaled by rank and level like every quest) and stat points. */
    private static final Map<String, int[]> PAY = Map.of(
            GIANT_SLAYER, new int[]{500, 2},
            UNBROKEN, new int[]{300, 1},
            VALOR, new int[]{450, 2},
            UNTOUCHED, new int[]{450, 2},
            LONE_WOLF, new int[]{600, 3},
            NEMESIS_SLAIN, new int[]{550, 2},
            FLAWLESS, new int[]{400, 2},
            UNSTOPPABLE, new int[]{350, 1},
            SILENT, new int[]{500, 2});

    public static String template(String id) {
        return BeyondSolo.id("secret/" + id).toString();
    }

    /** The title a secret grants: {@code title.beyondsolo.<id>}. */
    public static String title(String id) {
        return TitleDef.PREFIX + id;
    }

    /**
     * The secrets' titles on the chronicle's TITLES page: hidden until earned, given here rather than counted (the
     * Giant Slayer is the chronicle's own, earned by the same kill). Perks are first guesses for the balancing pass.
     */
    public static void registerTitles() {
        given(UNBROKEN, TitleCategory.HUNT, TitleTier.RARE, new PerkLine(TitlePerk.LAST_STAND, 0.08f));
        given(VALOR, TitleCategory.HUNT, TitleTier.EPIC, new PerkLine(TitlePerk.GUARD, 0.06f), new PerkLine(TitlePerk.VITALITY, 0.03f));
        given(UNTOUCHED, TitleCategory.GATES, TitleTier.EPIC, new PerkLine(TitlePerk.SWIFTNESS, 0.05f), new PerkLine(TitlePerk.GUARD, 0.03f));
        given(LONE_WOLF, TitleCategory.GATES, TitleTier.EPIC, new PerkLine(TitlePerk.BOSS_DAMAGE, 0.06f), new PerkLine(TitlePerk.MIGHT, 0.03f));
        given(NEMESIS_SLAIN, TitleCategory.HUNT, TitleTier.EPIC, new PerkLine(TitlePerk.GIANT_DAMAGE, 0.08f), new PerkLine(TitlePerk.VITALITY, 0.03f));
        given(FLAWLESS, TitleCategory.MASTERY, TitleTier.RARE, new PerkLine(TitlePerk.GUARD, 0.04f));
        given(UNSTOPPABLE, TitleCategory.MASTERY, TitleTier.RARE, new PerkLine(TitlePerk.HASTE, 0.05f));
        given(SILENT, TitleCategory.MASTERY, TitleTier.RARE, new PerkLine(TitlePerk.LUCK, 1));
    }

    private static void given(String id, TitleCategory category, TitleTier tier, PerkLine... perks) {
        Titles.register(new TitleDef(id, category, tier, null, 0, List.of(perks), true));
    }

    /**
     * Reveals a secret quest the player has just earned, done and paid. Only once each. Returns false if they had it
     * already (or the System is not theirs yet).
     */
    public static boolean reveal(ServerPlayer player, String id) {
        if (!SituationConfig.enabled() || !SystemAPI.isAwakened(player) || !QuestEngine.data(player).started() || !PAY.containsKey(id)) return false;
        if (QuestEngine.data(player).hasFinished(template(id))) return false;
        boolean revealed = QuestEngine.reveal(player, build(player, id), Component.translatable("secret.beyondsolo." + id + ".deed"));
        if (revealed) FollowUps.data(player).countSecret();
        return revealed;
    }

    /** Whether the player has found this secret. */
    public static boolean found(ServerPlayer player, String id) {
        return QuestEngine.data(player).hasFinished(template(id));
    }

    static Quest build(ServerPlayer player, String id) {
        int[] pay = PAY.get(id);
        QuestBuilder b = QuestBuilder.of(template(id), QuestCategory.SECRET, player)
                .title(Component.translatable("secret.beyondsolo." + id))
                .description(Component.translatable("secret.beyondsolo." + id + ".desc"))
                .line(BeyondSolo.id("secret").toString(), Component.translatable("secret.beyondsolo." + id + ".obj"))
                .xp(pay[0]).statPoints(pay[1]);
        // kept with the hunter's titles; the chronicle decides whether it is worn
        b.rewardTitle(title(id));
        return b.build();
    }

    /** A kill that may be a secret: a creature far stronger than the hunter. */
    static void killed(ServerPlayer killer, LivingEntity dead) {
        if (!(dead instanceof Mob)) return;
        double ratio = Foes.ratio(dead, killer);
        double need = HiddenLevel.has(dead) ? HiddenLevel.Threat.FAR_FROM : OverwhelmingFoe.PLAIN_RATIO;
        if (ratio >= need) reveal(killer, GIANT_SLAYER);
    }

    private Secrets() {}
}
