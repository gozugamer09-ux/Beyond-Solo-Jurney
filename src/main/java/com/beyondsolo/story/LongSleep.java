package com.beyondsolo.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.situation.Run;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.CitadelLordEntity;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Long Sleep: the hunter's own story. Their mother has slept for years and no healer can wake her; the villagers of
 * the village the hunter was evaluated in keep her in one of their homes ({@link Kin}).
 *
 * <ol>
 *   <li>At {@link StoryConfig#visitLevel} the System sends the hunter to see her (a scene at her bedside).</li>
 *   <li>At {@link StoryConfig#cureLevel} the System finds a cure: the Elixir of Waking, brewed from souls taken in the
 *   Crimson Citadel ({@link #soul}), Elder Root Dew from the hidden chamber of a high-rank gate ({@link #dew}) and
 *   the chalice the Crimson King keeps ({@link #lordFallen}). With all three the System brews it.</li>
 *   <li>The hunter gives it to her (a scene with the camera): she wakes, sits up in bed that day, and from the next on
 *   she is up and about and cooks for them once a day (Home Comfort).</li>
 * </ol>
 * Story quests can't be given up; one that goes missing (a wiped quest log) is given again. Every number is a first
 * guess for the balancing pass.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class LongSleep {
    /** How far the story has come ({@link KinRecord#stage()}). */
    public static final int UNTOLD = 0, VISIT = 1, VISITED = 2, SEEKING = 3, BREWED = 4, AWAKE = 5;

    public static final String VISIT_QUEST = BeyondSolo.id("story/long_sleep/visit").toString();
    public static final String CURE_QUEST = BeyondSolo.id("story/long_sleep/cure").toString();
    public static final String WAKING_QUEST = BeyondSolo.id("story/long_sleep/waking").toString();

    /** The quests' own objectives. */
    public static final String VISITED_KEY = BeyondSolo.id("story_visited").toString();
    public static final String SOULS_KEY = BeyondSolo.id("story_souls").toString();
    public static final String WOKE_KEY = BeyondSolo.id("story_woke").toString();

    /** What waking her earns. */
    public static final String TITLE_ID = "promise_kept";
    public static final String TITLE = TitleDef.PREFIX + TITLE_ID;

    /** The waypoint kind story quests point home with (its label and heart, see {@code client.npc.NpcMarkers}). */
    public static final String HOME = "home";

    /** Scene ids ({@link StoryScenes#playingId}). */
    public static final String VISIT_SCENE = "long_sleep_visit", WAKING_SCENE = "long_sleep_waking", REVEAL_SCENE = "long_sleep_reveal",
            TALK_SCENE = "long_sleep_talk", MEAL_SCENE = "long_sleep_meal";

    // ------------------------------------------------------------------ the quests

    public static void registerTemplates() {
        QuestTemplates.register(VISIT_QUEST, (p, c) -> {
            Quest q = QuestBuilder.of(VISIT_QUEST, QuestCategory.STORY, p)
                    .text("story.long_sleep.visit")
                    .line(VISITED_KEY, Component.translatable("quest_objective.beyondsolo.story.visit"))
                    .xp(150)
                    .build();
            pointHome(p, q);
            return q;
        });
        QuestTemplates.register(CURE_QUEST, (p, c) -> {
            PlayerRank dew = StoryConfig.dewRank();
            QuestBuilder b = QuestBuilder.of(CURE_QUEST, QuestCategory.STORY, p)
                    .text("story.long_sleep.cure", dew.name())
                    .custom(SOULS_KEY, StoryConfig.souls(), Component.translatable("quest_objective.beyondsolo.story.souls"))
                    .objective(ObjectiveType.COLLECT, id(StoryModule.ELDER_ROOT_DEW.get()), 1, false,
                            Component.translatable("quest_objective.beyondsolo.story.dew", dew.name()))
                    .objective(ObjectiveType.COLLECT, id(StoryModule.CRIMSON_CHALICE.get()), 1, false,
                            Component.translatable("quest_objective.beyondsolo.story.chalice"))
                    .xp(1200)
                    .item(new ItemStack(StoryModule.ELIXIR.get()));
            return b.build();
        });
        QuestTemplates.register(WAKING_QUEST, (p, c) -> {
            Quest q = QuestBuilder.of(WAKING_QUEST, QuestCategory.STORY, p)
                    .text("story.long_sleep.waking")
                    .line(WOKE_KEY, Component.translatable("quest_objective.beyondsolo.story.waking"))
                    .xp(2000).statPoints(3).rewardTitle(TITLE)
                    .build();
            pointHome(p, q);
            return q;
        });
    }

    private static String id(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Points a story quest at her bed (the village's crystal until a bed is chosen). */
    static void pointHome(ServerPlayer p, Quest q) {
        KinRecord r = Kin.record(p);
        ResourceKey<Level> dim = r.bed != null && r.bedDim != null ? r.bedDim : r.anchorDim;
        BlockPos at = r.bed != null && r.bedDim != null ? r.bed : r.anchor;
        if (dim == null || at == null) return;
        Run.setWaypoint(q.extra(), dim, at);
        q.extra().putString(Association.WAYPOINT_KIND, HOME);
    }

    /** Her bed changed: the story quests that lead to her point there now. */
    static void pointHome(ServerPlayer p) {
        boolean changed = false;
        for (Quest q : QuestEngine.data(p).active()) {
            if (!q.template().equals(VISIT_QUEST) && !q.template().equals(WAKING_QUEST)) continue;
            pointHome(p, q);
            changed = true;
        }
        if (changed) QuestEngine.sync(p);
    }

    @Nullable
    static Quest active(ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(template)) return q;
        return null;
    }

    private static int objective(Quest q, String key) {
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            if (o.type() == ObjectiveType.CUSTOM && o.target().equals(key)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ the story moving on

    /** Once a second (from {@link Kin}): the next part of the story starts when its time has come. */
    static void tick(ServerPlayer p) {
        if (EvaluationManager.isBeingEvaluated(p) || StoryScenes.playing(p)) return;
        KinRecord r = Kin.record(p);
        if (r.anchor == null || !QuestEngine.data(p).started()) return;
        int level = SystemAPI.get(p).level();
        switch (r.stage) {
            case UNTOLD -> {
                if (level >= StoryConfig.visitLevel()) give(p, r, VISIT_QUEST, VISIT);
            }
            case VISIT -> {
                if (!QuestEngine.data(p).has(VISIT_QUEST)) give(p, r, VISIT_QUEST, VISIT);
            }
            case VISITED -> {
                if (level >= StoryConfig.cureLevel() && calm(p)) reveal(p);
            }
            case SEEKING -> {
                if (!QuestEngine.data(p).has(CURE_QUEST)) give(p, r, CURE_QUEST, SEEKING);
            }
            case BREWED -> {
                if (!QuestEngine.data(p).has(WAKING_QUEST)) give(p, r, WAKING_QUEST, BREWED);
            }
            default -> {}
        }
    }

    private static void give(ServerPlayer p, KinRecord r, String template, int stage) {
        r.stage = stage;
        QuestEngine.start(p, template, QuestTemplate.Context.NONE);
        // the souls are the hunter's record's, not the quest's: a quest given again loses none
        if (template.equals(CURE_QUEST)) syncSouls(p);
    }

    /** Somewhere a story can be told: under the sky of the overworld, not in a gate, the Citadel or a trial. */
    static boolean calm(ServerPlayer p) {
        return p.level().dimension().equals(Level.OVERWORLD) && !TowerRun.inRun(p) && p.isAlive();
    }

    /** The System finds the cure: what it says, what the hunter thinks, and the quest. */
    static void reveal(ServerPlayer p) {
        KinRecord r = Kin.record(p);
        r.stage = SEEKING;
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.story.cure_found.title"),
                Component.translatable("notify.beyondsolo.story.cure_found.body"), 220));
        StoryScenes.play(p, new StoryScenes.Scene(REVEAL_SCENE)
                .pause(70)
                .think(line("reveal", 1), 75)
                .think(line("reveal", 2), 65)
                .run(h -> {
                    KinRecord rec = Kin.record(h);
                    if (rec.stage == SEEKING && !QuestEngine.data(h).has(CURE_QUEST)) give(h, rec, CURE_QUEST, SEEKING);
                }));
    }

    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        ServerPlayer p = event.getPlayer();
        String t = event.getQuest().template();
        KinRecord r = Kin.record(p);
        if (t.equals(VISIT_QUEST)) {
            if (r.stage < VISITED) r.stage = VISITED;
        } else if (t.equals(CURE_QUEST)) {
            // the System takes what it brewed the elixir from (the elixir itself was the quest's reward)
            take(p, StoryModule.ELDER_ROOT_DEW.get());
            take(p, StoryModule.CRIMSON_CHALICE.get());
            if (r.stage < BREWED) r.stage = BREWED;
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                    Component.translatable("notify.beyondsolo.story.brewed.title"),
                    Component.translatable("notify.beyondsolo.story.brewed.body"), 200));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1f, 0.8f);
            ServerScheduler.schedule(20 * 4, () -> {
                if (!p.hasDisconnected() && Kin.record(p).stage == BREWED && !QuestEngine.data(p).has(WAKING_QUEST)) {
                    QuestEngine.start(p, WAKING_QUEST, QuestTemplate.Context.NONE);
                }
            });
        } else if (t.equals(WAKING_QUEST)) {
            wake(p, r);
        }
    }

    /** She is awake (for good): the day is remembered, and she sits up in bed. */
    static void wake(ServerPlayer p, KinRecord r) {
        r.stage = AWAKE;
        if (r.wokeDay == KinRecord.NOT_YET) r.wokeDay = QuestEngine.day(p);
        KinEntity k = Kin.find(p);
        if (k != null && k.state() != KinEntity.STANDING) k.setState(Kin.stateFor(p, r));
    }

    // ------------------------------------------------------------------ operators and tests

    /** Moves the story to {@code stage} at once: that stage's quest given, the elixir in hand when it is brewed. */
    public static void step(ServerPlayer p, int stage) {
        KinRecord r = Kin.record(p);
        for (Quest q : List.copyOf(QuestEngine.data(p).all())) if (q.category() == QuestCategory.STORY) QuestEngine.drop(p, q);
        r.stage = Math.max(UNTOLD, Math.min(AWAKE, stage));
        r.wokeDay = KinRecord.NOT_YET;
        r.mealDay = KinRecord.NOT_YET;
        switch (r.stage) {
            case VISIT -> QuestEngine.start(p, VISIT_QUEST, QuestTemplate.Context.NONE);
            case SEEKING -> {
                QuestEngine.start(p, CURE_QUEST, QuestTemplate.Context.NONE);
                syncSouls(p);
            }
            case BREWED -> {
                if (!holds(p, StoryModule.ELIXIR.get())) {
                    ItemStack elixir = new ItemStack(StoryModule.ELIXIR.get());
                    if (!p.getInventory().add(elixir)) p.drop(elixir, false);
                }
                QuestEngine.start(p, WAKING_QUEST, QuestTemplate.Context.NONE);
            }
            // up and about already, her stew waiting
            case AWAKE -> r.wokeDay = QuestEngine.day(p) - 1;
            default -> {}
        }
        KinEntity k = Kin.find(p);
        if (k != null) k.setState(Kin.stateFor(p, r));
    }

    /** The story from the start: she sleeps again, the souls are gone (home, her bed and her look stay). */
    public static void reset(ServerPlayer p) {
        step(p, UNTOLD);
        Kin.record(p).souls = 0;
    }

    /** Sets the souls gathered for the cure. */
    public static void setSouls(ServerPlayer p, int souls) {
        Kin.record(p).souls = Math.max(0, Math.min(StoryConfig.souls(), souls));
        syncSouls(p);
    }

    /** Plays one of the story's scenes for the hunter (the bedside ones need her loaded). False if she is not. */
    public static boolean play(ServerPlayer p, String scene) {
        KinEntity k = Kin.find(p);
        switch (scene) {
            case VISIT_SCENE, WAKING_SCENE -> {
                if (k == null) return false;
                StoryScenes.play(p, scene.equals(VISIT_SCENE) ? visitScene(p, k) : wakingScene(p, k));
            }
            case MEAL_SCENE -> StoryScenes.play(p, mealScene(p));
            case REVEAL_SCENE -> reveal(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the cure's ingredients

    /** A creature of the Crimson Citadel fell in the hunter's run: its soul, while the System gathers them. */
    public static void soul(ServerPlayer p, LivingEntity dead) {
        if (!StoryConfig.enabled()) return;
        KinRecord r = Kin.record(p);
        int need = StoryConfig.souls();
        if (r.stage != SEEKING || r.souls >= need) return;
        int n = dead instanceof CitadelLordEntity ? StoryConfig.lordSouls() : dead instanceof DungeonBoss ? StoryConfig.keeperSouls() : 1;
        r.souls = Math.min(need, r.souls + n);
        if (dead.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY() + dead.getBbHeight() * 0.6, dead.getZ(),
                    2 + Math.min(n, 14), 0.25, 0.35, 0.25, 0.03);
        }
        syncSouls(p);
    }

    /** The cure quest's soul count, from the hunter's record. */
    static void syncSouls(ServerPlayer p) {
        Quest q = active(p, CURE_QUEST);
        if (q == null) return;
        int i = objective(q, SOULS_KEY);
        if (i >= 0) QuestEngine.setProgress(p, q, i, Kin.record(p).souls);
    }

    /**
     * A hidden chamber's cache opens: Elder Root Dew for every hunter near it who looks for one, when the gate is high
     * enough ({@code cacheRank} is the cache's: one above the gate's).
     */
    public static List<ItemStack> dew(ServerLevel level, BlockPos at, PlayerRank cacheRank) {
        List<ItemStack> out = new ArrayList<>();
        Vec3 c = Vec3.atCenterOf(at);
        for (ServerPlayer p : OnlinePlayers.all(level.getServer())) {
            if (p.level() != level || p.distanceToSqr(c) > 32 * 32) continue;
            if (dewFor(p, cacheRank)) out.add(new ItemStack(StoryModule.ELDER_ROOT_DEW.get()));
        }
        return out;
    }

    /** Whether a cache of that rank holds the dew for the hunter: high enough, and they look for it and have none. */
    public static boolean dewFor(ServerPlayer p, PlayerRank cacheRank) {
        if (!StoryConfig.enabled()) return false;
        int need = Math.min(StoryConfig.dewRank().tier() + 1, PlayerRank.S.tier());
        return cacheRank.tier() >= need && Kin.record(p).stage == SEEKING && !holds(p, StoryModule.ELDER_ROOT_DEW.get());
    }

    /** The Crimson King fell at the top of the hunter's climb: his chalice, while the System looks for it. */
    public static void lordFallen(ServerPlayer p) {
        if (!StoryConfig.enabled() || Kin.record(p).stage != SEEKING || holds(p, StoryModule.CRIMSON_CHALICE.get())) return;
        ItemStack chalice = new ItemStack(StoryModule.CRIMSON_CHALICE.get());
        if (!p.getInventory().add(chalice)) p.drop(chalice, false);
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.story.chalice.title"),
                Component.translatable("notify.beyondsolo.story.chalice.body"), 180));
    }

    static boolean holds(ServerPlayer p, Item item) {
        return p.getInventory().contains(new ItemStack(item));
    }

    private static boolean take(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ talking to her

    /** The hunter came to her: what happens depends on how far the story has come. */
    static void talk(ServerPlayer p, KinEntity k) {
        KinRecord r = Kin.record(p);
        switch (r.stage) {
            case UNTOLD, VISIT -> {
                // the first visit: the System's quest for it, if it has not come yet
                if (active(p, VISIT_QUEST) == null && QuestEngine.data(p).started()) give(p, r, VISIT_QUEST, VISIT);
                StoryScenes.play(p, visitScene(p, k));
            }
            case VISITED -> thought(p, "talk.asleep", 3, 95);
            case SEEKING -> thought(p, "talk.seeking", 2, 100);
            case BREWED -> {
                if (holds(p, StoryModule.ELIXIR.get())) StoryScenes.play(p, wakingScene(p, k));
                else thought(p, "talk.no_elixir", 1, 70);
            }
            default -> {
                long today = QuestEngine.day(p);
                if (k.state() == KinEntity.SITTING || today <= r.wokeDay) motherSays(p, "talk.resting", 2, 95);
                else if (r.mealDay != today) StoryScenes.play(p, mealScene(p));
                else motherSays(p, "talk.awake", 6, 95);
            }
        }
    }

    static Component line(String scene, int n) {
        return Component.translatable("story.beyondsolo." + scene + "." + n);
    }

    static Component mother() {
        return Component.translatable("story.beyondsolo.speaker.mother");
    }

    private static void thought(ServerPlayer p, String key, int count, int ticks) {
        StoryScenes.play(p, new StoryScenes.Scene(TALK_SCENE).think(line(key, 1 + p.getRandom().nextInt(count)), ticks));
    }

    private static void motherSays(ServerPlayer p, String key, int count, int ticks) {
        StoryScenes.play(p, new StoryScenes.Scene(TALK_SCENE).say(mother(), StoryScenes.MOTHER, line(key, 1 + p.getRandom().nextInt(count)), ticks));
    }

    /** Her stew, once a day once she is up and about. */
    static StoryScenes.Scene mealScene(ServerPlayer p) {
        return new StoryScenes.Scene(MEAL_SCENE)
                .say(mother(), StoryScenes.MOTHER, line("meal", 1), 90)
                .run(h -> {
                    KinRecord r = Kin.record(h);
                    long today = QuestEngine.day(h);
                    if (r.mealDay == today) return;
                    r.mealDay = today;
                    ItemStack stew = new ItemStack(StoryModule.HOME_STEW.get());
                    if (!h.getInventory().add(stew)) h.drop(stew, false);
                })
                .show(h -> h.level().playSound(null, h.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.9f))
                .think(line("meal", 2), 60);
    }

    // ------------------------------------------------------------------ the scenes

    /** Her face on the pillow. */
    static Vec3 face(KinEntity k) {
        BlockPos bed = k.bed();
        if (bed == null) return k.getEyePosition();
        Direction f = k.bedFacing();
        return Vec3.atBottomCenterOf(bed).add(f.getStepX() * 0.2, 0.75, f.getStepZ() * 0.2);
    }

    /** The middle of her bed (between pillow and foot). */
    static Vec3 bedMiddle(KinEntity k) {
        BlockPos bed = k.bed();
        if (bed == null) return k.position();
        Direction f = k.bedFacing();
        return Vec3.atBottomCenterOf(bed).add(-f.getStepX() * 0.5, 0.6, -f.getStepZ() * 0.5);
    }

    /** Moves the hunter beside her pillow, facing her (if there is room there). */
    static void toBedside(ServerPlayer p, KinEntity k) {
        BlockPos bed = k.bed();
        if (bed == null || p.level() != k.level()) return;
        BlockPos spot = Kin.besideBed(p.level(), bed);
        if (spot == null) return;
        Vec3 at = Vec3.atBottomCenterOf(spot);
        Vec3 look = face(k).subtract(at.add(0, p.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        p.teleportTo(p.serverLevel(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Across her bed from the hunter (a flat unit vector). */
    static Vec3 across(KinEntity k, ServerPlayer p) {
        Direction f = k.bedFacing();
        Vec3 side = new Vec3(f.getClockWise().getStepX(), 0, f.getClockWise().getStepZ());
        return p.position().subtract(bedMiddle(k)).dot(side) > 0 ? side.scale(-1) : side;
    }

    /** A point to the side of her bed (the side the hunter is not on), {@code out} blocks out and {@code up} up. */
    static Vec3 aside(KinEntity k, ServerPlayer p, double out, double up) {
        return bedMiddle(k).add(across(k, p).scale(out)).add(0, up, 0);
    }

    // ------------------------------------------------------------------ the camera in a small room

    /**
     * Where a camera wanted at {@code want} can be while it looks at {@code at}: there, or pulled in toward {@code at}
     * to just short of the first wall between them. Village rooms are small; a camera outside them films a wall.
     */
    static Vec3 clear(Level level, Vec3 at, Vec3 want) {
        Vec3 d = want.subtract(at);
        double len = d.length();
        if (len < 1.0e-4) return want;
        BlockHitResult hit = level.clip(new ClipContext(at, want, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return want;
        double keep = Math.max(0.45, hit.getLocation().distanceTo(at) - 0.3);
        return at.add(d.scale(Math.min(1, keep / len)));
    }

    /** The first of these camera spots that keeps at least {@code enough} of its distance from her, else the roomiest. */
    static Vec3 roomiest(Level level, Vec3 at, double enough, Vec3... wants) {
        Vec3 best = wants[0];
        double most = -1;
        for (Vec3 want : wants) {
            Vec3 c = clear(level, at, want);
            double room = c.distanceTo(at);
            if (room >= want.distanceTo(at) * enough) return c;
            if (room > most) {
                most = room;
                best = c;
            }
        }
        return best;
    }

    /**
     * Over the hunter's shoulder, looking down at her: where a scene's first shot starts (over one shoulder, else the
     * other, else above their head, as the walls allow; never inside their head); else from near the wide shot.
     */
    static Vec3 shoulder(KinEntity k, ServerPlayer p) {
        Level level = p.level();
        Vec3 face = face(k);
        Vec3 eye = p.getEyePosition();
        Vec3 back = new Vec3(eye.x - face.x, 0, eye.z - face.z);
        back = back.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : back.normalize();
        Vec3 right = new Vec3(-back.z, 0, back.x);
        // never straight behind the head: it would fill the shot
        for (Vec3 want : List.of(eye.add(right.scale(1.0)).add(back.scale(0.65)).add(0, 0.55, 0),
                eye.add(right.scale(-1.0)).add(back.scale(0.65)).add(0, 0.55, 0),
                eye.add(right.scale(0.75)).add(back.scale(0.4)).add(0, 0.5, 0),
                eye.add(right.scale(-0.75)).add(back.scale(0.4)).add(0, 0.5, 0),
                eye.add(back.scale(0.25)).add(0, 1.05, 0))) {
            Vec3 c = clear(level, face, want);
            if (c.distanceTo(want) < 0.15 && c.distanceTo(eye) > 0.6) return c;
        }
        return toward(k, wide(k, p), 0.92);
    }

    /**
     * The wide shot of her bed: across it from the hunter, else from past its foot, else from behind the hunter, else
     * from high over it, whichever the room allows.
     */
    static Vec3 wide(KinEntity k, ServerPlayer p) {
        Vec3 face = face(k);
        Vec3 mid = bedMiddle(k);
        Vec3 side = across(k, p);
        Direction f = k.bedFacing();
        Vec3 toFoot = new Vec3(-f.getStepX(), 0, -f.getStepZ());
        return roomiest(p.level(), face, 0.8,
                mid.add(side.scale(2.6)).add(0, 1.3, 0),
                mid.add(toFoot.scale(2.0)).add(side.scale(0.5)).add(0, 1.3, 0),
                mid.add(side.scale(-2.3)).add(toFoot.scale(0.8)).add(0, 1.5, 0),
                face.add(toFoot.scale(0.7)).add(0, 1.9, 0));
    }

    /** {@code t} of the way from her face to a camera spot (a spot on a clear line stays clear). */
    static Vec3 toward(KinEntity k, Vec3 spot, double t) {
        Vec3 face = face(k);
        return face.add(spot.subtract(face).scale(t));
    }

    /**
     * A slow turn around her bed for the end of the waking, as wide as the room allows (an arc on the far side first,
     * then others); where no arc fits, a slow drift in toward her instead.
     */
    static CutscenePayload turnAround(KinEntity k, ServerPlayer p, float turns, int ticks) {
        Level level = p.level();
        Vec3 center = bedMiddle(k).add(0, 0.4, 0);
        float start = orbitStart(k, p);
        double[][] sizes = {{3.0, 1.2}, {2.4, 1.05}, {1.9, 0.95}, {1.5, 0.85}};
        float[] starts = {start, start - 0.11f, start + 0.25f, start - 0.25f, start + 0.5f};
        for (double[] size : sizes) {
            for (float s0 : starts) {
                if (arcClear(level, center, size[0], size[1], s0, turns)) {
                    return CutscenePayload.orbit(center, size[0], size[1], s0, turns, ticks);
                }
            }
        }
        Vec3 wide = wide(k, p);
        return CutscenePayload.glide(wide, toward(k, wide, 0.7), face(k), face(k), ticks);
    }

    private static boolean arcClear(Level level, Vec3 center, double radius, double height, float start, float turns) {
        for (int i = 0; i <= 10; i++) {
            double a = (start + turns * i / 10.0) * Math.PI * 2;
            Vec3 at = center.add(Math.cos(a) * radius, height, Math.sin(a) * radius);
            if (clear(level, center, at).distanceTo(at) > 0.05) return false;
            // the camera itself not inside a block
            if (!level.getBlockState(BlockPos.containing(at)).getCollisionShape(level, BlockPos.containing(at)).isEmpty()) return false;
        }
        return true;
    }

    /** The first visit: a quiet moment at her bedside. */
    static StoryScenes.Scene visitScene(ServerPlayer p, KinEntity k) {
        return new StoryScenes.Scene(VISIT_SCENE).hold()
                .show(12, h -> toBedside(h, k))
                .blink(24, null)
                .shot(h -> CutscenePayload.glide(shoulder(k, h), wide(k, h), face(k), face(k), 150))
                .narrate(line("visit", 1), 140)
                .narrate(line("visit", 2), 120)
                .shot(h -> {
                    Vec3 wide = wide(k, h);
                    return CutscenePayload.glide(wide, toward(k, wide, 0.68), face(k), face(k), 240);
                })
                .think(line("visit", 3), 85)
                .think(line("visit", 4), 95)
                .think(line("visit", 5), 80)
                .think(line("visit", 6), 60)
                .run(h -> QuestEngine.progress(h, VISITED_KEY, 1))
                .pause(15);
    }

    /** The elixir: she wakes. */
    static StoryScenes.Scene wakingScene(ServerPlayer p, KinEntity k) {
        Component hunter = p.getName();
        StoryScenes.Scene s = new StoryScenes.Scene(WAKING_SCENE).hold()
                .show(12, h -> toBedside(h, k))
                .blink(24, null)
                .shot(h -> CutscenePayload.glide(shoulder(k, h), wide(k, h), face(k), face(k), 140))
                .narrate(line("waking", 1), 115)
                .think(line("waking", 2), 60)
                .show(h -> sound(h, k, SoundEvents.BOTTLE_EMPTY, 0.9f, 1.1f))
                .run(h -> take(h, StoryModule.ELIXIR.get()));
        // the light sinking into her while it is told
        for (int i = 0; i < 6; i++) {
            final int n = i;
            s.show(20 + i * 12, h -> glow(k, n));
        }
        s.narrate(line("waking", 3), 110)
                .shot(h -> {
                    Vec3 wide = wide(k, h);
                    return CutscenePayload.glide(toward(k, wide, 0.62), toward(k, wide, 0.42), face(k), face(k), 260);
                })
                .show(h -> sound(h, k, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f))
                .narrate(line("waking", 4), 115)
                .pause(15)
                .think(line("waking", 5), 45)
                .run(h -> {
                    KinEntity m = Kin.find(h);
                    if (m != null && m.state() == KinEntity.SLEEPING) m.setState(KinEntity.STIRRING);
                })
                .show(h -> sound(h, k, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f))
                .narrate(line("waking", 6), 75)
                .say(mother(), StoryScenes.MOTHER, line("waking", 7), 90)
                .blink(30, h -> wake(h, Kin.record(h)))
                .shot(h -> turnAround(k, h, 0.22f, 420))
                .say(mother(), StoryScenes.MOTHER, line("waking", 8), 120)
                .say(hunter, StoryScenes.HUNTER, line("waking", 9), 100)
                .say(mother(), StoryScenes.MOTHER, line("waking", 10), 115)
                .narrate(line("waking", 11), 95)
                .run(h -> QuestEngine.progress(h, WOKE_KEY, 1))
                .pause(20);
        return s;
    }

    /** Where (in turns) an orbit around her bed starts so it opens on the far side from the hunter. */
    private static float orbitStart(KinEntity k, ServerPlayer p) {
        Vec3 from = aside(k, p, 1, 0).subtract(bedMiddle(k));
        return (float) (Math.atan2(from.z, from.x) / (Math.PI * 2));
    }

    private static void glow(KinEntity k, int n) {
        if (!(k.level() instanceof ServerLevel level)) return;
        Vec3 at = face(k);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6 - n * 0.05, at.z, 6, 0.35, 0.25, 0.35, 0.01);
        level.sendParticles(ParticleTypes.WAX_OFF, at.x, at.y + 0.2, at.z, 4, 0.5, 0.15, 0.5, 0.02);
        if (n % 2 == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.NEUTRAL, 0.5f, 1.4f + n * 0.08f);
    }

    private static void sound(ServerPlayer p, KinEntity k, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Vec3 at = face(k);
        p.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private LongSleep() {}
}
