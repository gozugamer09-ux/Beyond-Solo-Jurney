package com.beyondsolo.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.home.HomeWorld;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.light.LightAPI;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The System's own story: why it chose the hunter, and what it was made for. Told in chapters, each a story quest
 * that leads through what the game already holds, with scenes between them ({@link MainScenes}):
 *
 * <ol start="0">
 *   <li><b>Prologue</b>, once the System has woken and the hunter is calm: ten years ago the first gate opened; their
 *   father walked into one and never came back; their mother fell into the Long Sleep.</li>
 *   <li><b>The Player</b>: reach level {@link #PLAYER_LEVEL} and clear {@link #PLAYER_GATES} gates. A hollow-faced
 *   statue smiles at them in a vision.</li>
 *   <li><b>Arise</b>: change their job (the Trial of Ascension).</li>
 *   <li><b>The Crimson Citadel</b>: fell Vharos, the Crimson King, who knew a king of night once.</li>
 *   <li><b>The Empty Throne</b>: claim a crown (Monarch Ascension). The truth: Morvath, the first King of Night, made
 *   the Arbiter, and its System, to find whoever would sit on his throne after him; the Arbiter meant to wear them.</li>
 *   <li><b>The Vessel of Light</b>: Khorvane, Monarch of Hunger, comes for the new crown, and their father comes down
 *   in a pillar of light to fight beside them ({@link MainFights}, {@link FatherEntity}).</li>
 *   <li><b>The Last War</b>: Veyrith and then Thyrax come down together for the end of it, the light makes war on
 *   them, and their father gives the last of his light.</li>
 *   <li><b>Home</b>: back to their mother. The System's purpose is fulfilled.</li>
 * </ol>
 * A chapter whose quest was done before the story reached it (a hunter far along already) closes quietly; the throne's
 * truth is told whenever it is reached. Story quests can't be given up; one that goes missing is given again. Every
 * number is a first guess for the balancing pass.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class MainStory {
    /** The chapters ({@link MainRecord#chapter()}): untold, the seven of the story, done. */
    public static final int UNTOLD = 0, PLAYER = 1, ARISE = 2, CITADEL = 3, THRONE = 4, FATHER = 5, LAST_WAR = 6, HOME = 7, DONE = 8;

    /** The chapters' quests. */
    public static final String PLAYER_QUEST = quest("player"), ARISE_QUEST = quest("arise"), CITADEL_QUEST = quest("citadel"),
            THRONE_QUEST = quest("throne"), FATHER_QUEST = quest("father"), WAR_QUEST = quest("last_war"), HOME_QUEST = quest("home");
    private static final String[] QUESTS = {null, PLAYER_QUEST, ARISE_QUEST, CITADEL_QUEST, THRONE_QUEST, FATHER_QUEST, WAR_QUEST, HOME_QUEST, null};

    /** Their objectives. */
    public static final String GATES_KEY = key("gates"), JOB_KEY = key("job"), LORD_KEY = key("lord"), CROWN_KEY = key("crown"),
            HUNGER_KEY = key("hunger"), STILLNESS_KEY = key("stillness"), STORMS_KEY = key("storms"), HOME_KEY = key("home");

    /** The first chapter's goals. */
    public static final int PLAYER_LEVEL = 10, PLAYER_GATES = 3;

    /** The titles: hearing the truth at the throne, and the story's end. */
    public static final String HEIR_ID = "heir_to_the_empty_throne", BEYOND_ID = "beyond_the_system";
    public static final String HEIR = TitleDef.PREFIX + HEIR_ID, BEYOND = TitleDef.PREFIX + BEYOND_ID;

    /** A quest done within this long of being given was done before the story came to it (ticks). */
    static final int AT_ONCE = 40;
    /** How long a hunter has to be calm before the prologue plays (seconds). */
    public static final int PROLOGUE_CALM = 20;

    /** Seconds each hunter has been calm (somewhere a story can be told, nothing hurting them). */
    private static final Map<UUID, Integer> CALM = new HashMap<>();
    /**
     * When each hunter was last hurt (the server's tick count). Not the game's own "last hurt by a mob" time: that one
     * is saved with the player while their tick count starts again at every login, so after logging back in it reads
     * as a hurt still to come for as long as their last session lasted.
     */
    private static final Map<UUID, Integer> HURT = new HashMap<>();
    /** How long after being hurt a hunter is not calm (ticks). */
    static final int SETTLE = 200;

    private static String quest(String name) {
        return BeyondSolo.id("story/main/" + name).toString();
    }

    private static String key(String name) {
        return BeyondSolo.id("main_" + name).toString();
    }

    public static MainRecord record(ServerPlayer p) {
        return p.getData(StoryModule.MAIN.get());
    }

    // ------------------------------------------------------------------ the quests

    public static void registerTemplates() {
        QuestTemplates.register(PLAYER_QUEST, (p, c) -> QuestBuilder.of(PLAYER_QUEST, QuestCategory.STORY, p)
                .text("story.main.player", PLAYER_LEVEL, PLAYER_GATES)
                .reachLevel(PLAYER_LEVEL)
                .custom(GATES_KEY, PLAYER_GATES, Component.translatable("quest_objective.beyondsolo.story.main.gates"))
                .xp(300).statPoints(1)
                .build());
        QuestTemplates.register(ARISE_QUEST, (p, c) -> QuestBuilder.of(ARISE_QUEST, QuestCategory.STORY, p)
                .text("story.main.arise", JobAPI.unlockLevel())
                .custom(JOB_KEY, 1, Component.translatable("quest_objective.beyondsolo.story.main.job"))
                .xp(800).statPoints(2)
                .build());
        QuestTemplates.register(CITADEL_QUEST, (p, c) -> QuestBuilder.of(CITADEL_QUEST, QuestCategory.STORY, p)
                .text("story.main.citadel")
                .custom(LORD_KEY, 1, Component.translatable("quest_objective.beyondsolo.story.main.lord"))
                .xp(2500).statPoints(3)
                .build());
        // a hunter sworn to the light never sits on the throne: their fourth chapter is the light's Investiture
        QuestTemplates.register(THRONE_QUEST, (p, c) -> QuestBuilder.of(THRONE_QUEST, QuestCategory.STORY, p)
                .text(LightAPI.sworn(p) ? "story.main.throne_light" : "story.main.throne", LightAPI.sworn(p) ? LightAPI.minLevel() : MonarchAPI.minLevel())
                .custom(CROWN_KEY, 1, Component.translatable(LightAPI.sworn(p) ? "quest_objective.beyondsolo.story.main.mantle"
                        : "quest_objective.beyondsolo.story.main.crown"))
                .xp(4000).statPoints(3)
                .build());
        QuestTemplates.register(FATHER_QUEST, (p, c) -> QuestBuilder.of(FATHER_QUEST, QuestCategory.STORY, p)
                .text(LightAPI.sworn(p) ? "story.main.father_light" : "story.main.father")
                .custom(HUNGER_KEY, 1, monarchObjective(WorldBossKind.HUNGER))
                .xp(5000).statPoints(3)
                .build());
        QuestTemplates.register(WAR_QUEST, (p, c) -> QuestBuilder.of(WAR_QUEST, QuestCategory.STORY, p)
                .text("story.main.last_war")
                .custom(STILLNESS_KEY, 1, monarchObjective(WorldBossKind.STILLNESS))
                .custom(STORMS_KEY, 1, monarchObjective(WorldBossKind.STORMS))
                .xp(8000).statPoints(5)
                .build());
        QuestTemplates.register(HOME_QUEST, (p, c) -> {
            Quest q = QuestBuilder.of(HOME_QUEST, QuestCategory.STORY, p)
                    .text("story.main.home")
                    .line(HOME_KEY, Component.translatable("quest_objective.beyondsolo.story.main.home"))
                    .xp(2000)
                    .build();
            LongSleep.pointHome(p, q);
            return q;
        });
    }

    private static Component monarchObjective(WorldBossKind kind) {
        return Component.translatable("quest_objective.beyondsolo.story.main.monarch", kind.fullName());
    }

    /** The quest of a chapter, or null (untold, done). */
    @Nullable
    public static String questOf(int chapter) {
        return chapter >= 0 && chapter < QUESTS.length ? QUESTS[chapter] : null;
    }

    /** The chapter a quest belongs to, or 0 if it is none of the story's. */
    static int chapterOf(String template) {
        for (int i = 1; i < QUESTS.length; i++) if (template.equals(QUESTS[i])) return i;
        return 0;
    }

    @Nullable
    static Quest active(ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(template)) return q;
        return null;
    }

    static int objective(Quest q, String key) {
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            if (o.type() == ObjectiveType.CUSTOM && o.target().equals(key)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ the story moving on

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId() + 10) % 20 != 0) return;
        if (!StoryConfig.mainEnabled() || !SystemAPI.isAwakened(p) || !SystemAPI.hasSystem(p)) return;
        tick(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CALM.remove(event.getEntity().getUUID());
        HURT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getNewDamage() > 0) HURT.put(p.getUUID(), p.server.getTickCount());
    }

    /** Whether something hurt the hunter in the last {@link #SETTLE} ticks. */
    static boolean recentlyHurt(ServerPlayer p) {
        Integer at = HURT.get(p.getUUID());
        return p.hurtTime > 0 || at != null && p.server.getTickCount() - at <= SETTLE;
    }

    /** Once a second: the chapter's quest kept and its goals read, its fights, and the scene that is due. */
    public static void tick(ServerPlayer p) {
        if (EvaluationManager.isBeingEvaluated(p)) return;
        MainRecord r = record(p);
        long now = QuestEngine.gameTime(p);
        boolean calm = calm(p) && !recentlyHurt(p);
        int calmFor = calm ? CALM.merge(p.getUUID(), 1, Integer::sum) : 0;
        if (!calm) CALM.remove(p.getUUID());
        if (r.chapter > UNTOLD && r.chapter < DONE && r.owed == 0) {
            keepQuest(p, r, now);
            read(p, r);
        }
        MainFights.tick(p, r);
        if (StoryScenes.playing(p)) return;
        if (r.owed != 0) {
            if (now >= r.owedAt && calm) MainScenes.ending(p, r.owed);
            return;
        }
        if (r.chapter == UNTOLD) {
            if (calmFor >= PROLOGUE_CALM && QuestEngine.data(p).started()) MainScenes.prologue(p);
        } else if (r.chapter == HOME) {
            MainScenes.homecoming(p);
        }
    }

    /** The chapter's quest, given again if it went missing. */
    private static void keepQuest(ServerPlayer p, MainRecord r, long now) {
        String template = questOf(r.chapter);
        if (template == null || QuestEngine.data(p).has(template)) return;
        if (r.given == 0) r.given = now;
        QuestEngine.start(p, template, QuestTemplate.Context.NONE);
    }

    /** What the hunter has done that the chapter asks for, read from their records (so it counts whenever it was done). */
    private static void read(ServerPlayer p, MainRecord r) {
        String template = questOf(r.chapter);
        Quest q = template == null ? null : active(p, template);
        if (q == null) return;
        switch (r.chapter) {
            case PLAYER -> set(p, q, GATES_KEY, Math.min(PLAYER_GATES, Economy.data(p).totalClears()));
            case ARISE -> set(p, q, JOB_KEY, JobAPI.hasChangedJob(p) ? 1 : 0);
            case CITADEL -> set(p, q, LORD_KEY, TowerAPI.data(p).lordKills > 0 ? 1 : 0);
            case THRONE -> set(p, q, CROWN_KEY, MonarchAPI.crowned(p) || LightAPI.invested(p) ? 1 : 0);
            case FATHER -> set(p, q, HUNGER_KEY, r.warWon(2) ? 1 : 0);
            case LAST_WAR -> {
                set(p, q, STILLNESS_KEY, r.warWon(0) ? 1 : 0);
                set(p, q, STORMS_KEY, r.warWon(1) ? 1 : 0);
            }
            default -> {}
        }
    }

    private static void set(ServerPlayer p, Quest q, String key, int value) {
        int i = objective(q, key);
        if (i >= 0 && q.objectives().get(i).progress() != value && value > 0) QuestEngine.setProgress(p, q, i, value);
    }

    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        ServerPlayer p = event.getPlayer();
        int chapter = chapterOf(event.getQuest().template());
        if (chapter == 0) return;
        MainRecord r = record(p);
        if (r.chapter != chapter || r.owed != 0) return;
        long now = QuestEngine.gameTime(p);
        boolean atOnce = r.given != 0 && now - r.given <= AT_ONCE;
        if (atOnce && chapter <= CITADEL) {
            // done before the story came to it: on to the next chapter without its scene
            moveOn(p, r, chapter);
            return;
        }
        r.owed = chapter;
        // after a monarch falls, its death and the dust settle first
        r.owedAt = now + switch (chapter) {
            case FATHER, LAST_WAR -> 110;
            case THRONE -> 60;
            default -> 40;
        };
    }

    /** A chapter is over (its scene told, or skipped): the next one begins. Does nothing if the story is elsewhere. */
    static void advance(ServerPlayer p, int from) {
        MainRecord r = record(p);
        if (r.chapter != from) return;
        moveOn(p, r, from);
        keepQuest(p, r, QuestEngine.gameTime(p));
    }

    private static void moveOn(ServerPlayer p, MainRecord r, int from) {
        long now = QuestEngine.gameTime(p);
        r.owed = 0;
        r.owedAt = 0;
        r.given = 0;
        r.chapter = Math.min(DONE, from + 1);
        switch (from) {
            case THRONE -> {
                SystemAPI.setTitle(p, HEIR);
                r.nextFight = now + MainFights.FIRST;
            }
            case FATHER -> {
                r.fight = null;
                r.nextFight = now + StoryConfig.warDelayTicks();
            }
            case LAST_WAR -> {
                r.fight = null;
                r.fatherGone = true;
            }
            case HOME -> {
                r.endedDay = QuestEngine.day(p);
                SystemAPI.setTitle(p, BEYOND);
            }
            default -> {}
        }
    }

    /** Somewhere a story can be told: the overworld or the hunter's residence; not in a gate, the Citadel or a trial. */
    static boolean calm(ServerPlayer p) {
        Level level = p.level();
        boolean place = level.dimension().equals(Level.OVERWORLD) || HomeWorld.isResidence(level);
        return place && p.isAlive() && !p.isSpectator() && !TowerRun.inRun(p) && !GateService.inTransit(p);
    }

    // ------------------------------------------------------------------ operators and tests

    /** Moves the story to {@code chapter} at once: that chapter's quest given (the Last War from its first monarch). */
    public static void step(ServerPlayer p, int chapter) {
        MainRecord r = record(p);
        for (Quest q : List.copyOf(QuestEngine.data(p).all())) if (chapterOf(q.template()) != 0) QuestEngine.drop(p, q);
        MainFights.dismiss(p);
        int c = Math.max(UNTOLD, Math.min(DONE, chapter));
        r.reset();
        r.chapter = c;
        r.fatherMet = c > FATHER;
        r.fatherGone = c > LAST_WAR;
        if (c == DONE) r.endedDay = QuestEngine.day(p);
        CALM.remove(p.getUUID());
        if (questOf(c) != null) keepQuest(p, r, QuestEngine.gameTime(p));
    }

    /** The story from the start (their father's looks stay). */
    public static void reset(ServerPlayer p) {
        step(p, UNTOLD);
    }

    private MainStory() {}
}
