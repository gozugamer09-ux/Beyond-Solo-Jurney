package com.beyondsolo.story;

import com.beyondsolo.gate.GateService;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.ruler.Rulers;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The story's fights ({@link MainStory}): the monarchs that come for the hunter once they wear a crown, and their
 * father at their side.
 *
 * <ul>
 *   <li>In <b>the Vessel of Light</b> Khorvane comes; in <b>the Last War</b> Veyrith, then Thyrax half a minute after
 *   she falls. Each is a great threat like any other ({@link WorldBosses}, called by the story near the hunter when
 *   nothing else is abroad), or the one already abroad if it is the monarch the chapter waits for. One that leaves
 *   unbeaten, or falls far from the hunter, comes again a little later.</li>
 *   <li>Once the monarch has landed near them, their father comes down in a pillar of light and fights beside them
 *   ({@link FatherEntity}); in the Last War the light also comes down to make war on each monarch
 *   ({@link Rulers#war}). He stays for the chapter's last scene, and goes when the story no longer wants him.</li>
 * </ul>
 */
public final class MainFights {
    /** The record's {@link MainRecord#war()} bit of each monarch the story's fights are fought against. */
    static int bit(WorldBossKind kind) {
        return switch (kind) {
            case STILLNESS -> 1;
            case STORMS -> 2;
            case HUNGER -> 4;
            default -> 0;
        };
    }

    /** How near (blocks) a hunter must be to the monarch for their father to come, and for its fall to count. */
    static final int NEAR = 160;
    /** How far the story looks for a monarch of the right kind already abroad (blocks). */
    static final int TAKE_UP = 320;
    /**
     * A monarch that left unbeaten comes again this long after (ticks); Thyrax follows Veyrith this long after; Khorvane
     * comes this long after the throne's truth (a breath between the two).
     */
    static final int RETRY = 20 * 120, NEXT = 20 * 30, FIRST = 20 * 120;
    /**
     * How long after a monarch lands their father comes down (ticks): its landing has its own moments on screen (its
     * domain's banner, then the landing's, 9.2 seconds between them), and his words wait for them. A ruler that comes
     * down to make war on it at its landing has its own banner first, and he waits {@link #RULER_BANNER} longer.
     */
    public static final int ARRIVE_AFTER = 200, RULER_BANNER = 130;
    /** Where the story calls a monarch down (blocks from the hunter). */
    static final int SPOT_MIN = 36, SPOT_MAX = 64;
    /** The sky's light (of 15) a hunter must stand in for a monarch to be called on them: outdoors. */
    static final int OUTDOORS = 8;

    /** Each hunter's father in the world. */
    private static final Map<UUID, UUID> FATHERS = new HashMap<>();
    /** Hunters whose father an operator called down: he stays, whatever the story wants, until sent away. */
    private static final Set<UUID> CALLED = new HashSet<>();
    /** When their father may come down to each monarch abroad that landed (its event's id to the game time). */
    private static final Map<UUID, Long> LANDED = new HashMap<>();

    // ------------------------------------------------------------------ the monarchs

    /** The monarch the hunter's chapter waits for now, or null. */
    @Nullable
    public static WorldBossKind awaited(MainRecord r) {
        if (r.chapter == MainStory.FATHER && (r.war & bit(WorldBossKind.HUNGER)) == 0) return WorldBossKind.HUNGER;
        if (r.chapter != MainStory.LAST_WAR) return null;
        if ((r.war & bit(WorldBossKind.STILLNESS)) == 0) return WorldBossKind.STILLNESS;
        if ((r.war & bit(WorldBossKind.STORMS)) == 0) return WorldBossKind.STORMS;
        return null;
    }

    /** Once a second (from {@link MainStory#tick}): the chapter's monarch called or followed, and their father. */
    static void tick(ServerPlayer p, MainRecord r) {
        if (r.owed == 0) fight(p, r);
        if (wanted(p, r) && father(p) == null && !StoryScenes.playing(p) && p.level().dimension().equals(Level.OVERWORLD)) arrive(p, r);
    }

    private static void fight(ServerPlayer p, MainRecord r) {
        WorldBossKind kind = awaited(r);
        if (kind == null) return;
        ServerLevel overworld = p.server.overworld();
        long now = QuestEngine.gameTime(p);
        WorldBossData.Event cur = WorldBossData.get(p.server).current();
        if (r.fight != null) {
            if (cur != null && cur.id.equals(r.fight)) return;
            // it left unbeaten (or was sent away): it comes again
            r.fight = null;
            r.nextFight = Math.max(r.nextFight, now + RETRY);
            return;
        }
        if (cur != null) {
            // the monarch the chapter waits for is already abroad: that is the one
            if (cur.kind == kind && p.level() == overworld && p.blockPosition().distSqr(cur.home) < (double) TAKE_UP * TAKE_UP) {
                r.fight = cur.id;
            }
            return;
        }
        if (now < r.nextFight || !ready(p)) return;
        BlockPos spot = WorldBosses.findSpot(overworld, p.position(), SPOT_MIN, SPOT_MAX, p.getRandom());
        if (spot == null) {
            r.nextFight = now + 400;
            return;
        }
        WorldBossData.Event ev = WorldBosses.begin(overworld, kind, spot, true);
        if (ev == null) return;
        r.fight = ev.id;
        MainScenes.monarchComes(p, kind, ev.home);
    }

    /**
     * Where a monarch can be called on the hunter: under the overworld's sky (outdoors, not deep in a cave or shut in a
     * house: the sky's light reaches them), not in a gate's passage, the Citadel or a scene.
     */
    static boolean ready(ServerPlayer p) {
        return p.level().dimension().equals(Level.OVERWORLD) && p.isAlive() && !p.isSpectator() && !TowerRun.inRun(p) && !GateService.inTransit(p)
                && !StoryScenes.playing(p) && p.level().getBrightness(LightLayer.SKY, BlockPos.containing(p.getEyePosition())) >= OUTDOORS;
    }

    /**
     * An operator calls the chapter's monarch down on the hunter now (or takes up the one already abroad, if it is
     * that one): "called", or why not: "none" (the chapter waits for no monarch), "busy" (another great threat is
     * abroad), "not_here" (not under the overworld's sky, or in a scene), "no_room" (nowhere near to land).
     */
    public static String callNow(ServerPlayer p) {
        MainRecord r = MainStory.record(p);
        if (awaited(r) == null || r.owed != 0) return "none";
        if (r.fight != null && boss(p, r) != null) return "called";
        r.fight = null;
        r.nextFight = 0;
        if (WorldBossData.get(p.server).current() != null) {
            fight(p, r);
            return r.fight != null ? "called" : "busy";
        }
        if (!ready(p)) return "not_here";
        fight(p, r);
        return r.fight != null ? "called" : "no_room";
    }

    /** The monarch the story called for the hunter, if it is abroad and in the world. */
    @Nullable
    static ColossalBoss boss(ServerPlayer p, MainRecord r) {
        if (r.fight == null) return null;
        ServerLevel overworld = p.server.overworld();
        WorldBossData.Event cur = WorldBossData.get(p.server).current();
        if (cur == null || !cur.id.equals(r.fight)) return null;
        return WorldBosses.currentBoss(overworld);
    }

    /**
     * A monarch landed. True if it is some hunter's story fight: the story answers it (their father, and in the Last War
     * a ruler too), and the light's usual chance to come down to it ({@link Rulers#monarchLanded}) is not taken.
     */
    public static boolean monarchLanded(ServerLevel level, MonarchBoss monarch) {
        UUID id = monarch.eventId();
        if (id == null) return false;
        if (LANDED.size() > 8) LANDED.clear();
        LANDED.put(id, level.getGameTime() + ARRIVE_AFTER);
        for (ServerPlayer p : OnlinePlayers.all(level.getServer())) {
            MainRecord r = MainStory.record(p);
            if (!id.equals(r.fight)) continue;
            // in the Vessel of Light the light answers through him alone; in the Last War a ruler comes down too
            if (r.chapter == MainStory.LAST_WAR && WorldBossData.get(level.getServer()).ruler() == null
                    && Rulers.war(level, monarch, null) != null) {
                LANDED.put(id, level.getGameTime() + ARRIVE_AFTER + RULER_BANNER);
            }
            return true;
        }
        return false;
    }

    /**
     * A monarch fell: for every hunter whose fight it was and who was there (it was hurt by them, or they were near),
     * it counts; for the rest, it comes again.
     */
    public static void monarchFell(ServerLevel level, ColossalBoss boss) {
        UUID id = boss.eventId();
        if (id == null) return;
        LANDED.remove(id);
        for (ServerPlayer p : OnlinePlayers.all(level.getServer())) {
            MainRecord r = MainStory.record(p);
            if (!id.equals(r.fight)) continue;
            r.fight = null;
            long now = QuestEngine.gameTime(p);
            boolean there = boss.share(p.getUUID()) > 0 || p.level() == level && p.distanceToSqr(boss) < (double) NEAR * NEAR;
            if (!there) {
                r.nextFight = now + RETRY;
                continue;
            }
            r.war |= bit(boss.kind());
            if (r.chapter == MainStory.LAST_WAR && awaited(r) != null) {
                r.nextFight = now + NEXT;
                MainScenes.nextMonarch(p, awaited(r));
            }
            MainStory.tick(p);
        }
    }

    // ------------------------------------------------------------------ their father

    /** Whether the story wants the hunter's father at their side now. */
    static boolean wanted(ServerPlayer p, MainRecord r) {
        if (r.chapter != MainStory.FATHER && r.chapter != MainStory.LAST_WAR) return false;
        // the chapter's closing scene is his
        if (r.owed == MainStory.FATHER || r.owed == MainStory.LAST_WAR) return true;
        String scene = StoryScenes.playingId(p);
        if (MainScenes.FATHER_SCENE.equals(scene) || MainScenes.LAST_LIGHT_SCENE.equals(scene)) return true;
        // between the Last War's monarchs he stays
        if (r.chapter == MainStory.LAST_WAR && (r.war & bit(WorldBossKind.STILLNESS)) != 0) return true;
        // the monarch has landed (a moment ago at least), and the hunter is near it
        ColossalBoss boss = boss(p, r);
        return boss instanceof MonarchBoss m && m.landed() && m.isAlive() && boss.level() == p.level()
                && p.distanceToSqr(boss) < (double) NEAR * NEAR && settled(m);
    }

    /** Whether the monarch's landing has had its moment (one whose landing wasn't seen, long since). */
    private static boolean settled(MonarchBoss m) {
        Long at = m.eventId() == null ? null : LANDED.get(m.eventId());
        return at == null || m.level().getGameTime() >= at;
    }

    /** Whether the story still wants {@code f} (his child online, in his world, and wanting him). */
    static boolean keeps(FatherEntity f) {
        if (!(f.level() instanceof ServerLevel level) || f.owner() == null) return false;
        ServerPlayer p = OnlinePlayers.get(level.getServer(), f.owner());
        if (p == null || p.level() != level) return false;
        UUID mine = FATHERS.get(p.getUUID());
        // one father: any other one that finds itself in the world goes
        return f.getUUID().equals(mine) && (CALLED.contains(p.getUUID()) || wanted(p, MainStory.record(p)));
    }

    /** The hunter's father, if he is in the world near them. */
    @Nullable
    public static FatherEntity father(ServerPlayer p) {
        UUID id = FATHERS.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.serverLevel().getEntity(id);
        if (e instanceof FatherEntity f && f.isAlive() && !f.isRemoved()) return f;
        FATHERS.remove(p.getUUID());
        return null;
    }

    /** He comes down in a pillar of light beside the hunter (the first time, with a word or two). */
    static FatherEntity arrive(ServerPlayer p, MainRecord r) {
        FatherEntity f = summon(p, r);
        boolean first = !r.fatherMet;
        r.fatherMet = true;
        MainScenes.fatherArrives(p, first);
        return f;
    }

    /** Puts the hunter's father in the world beside them now (any one already there goes). */
    static FatherEntity summon(ServerPlayer p, MainRecord r) {
        dismiss(p);
        ServerLevel level = p.serverLevel();
        FatherEntity f = StoryModule.FATHER.get().create(level);
        if (f == null) throw new IllegalStateException("no father entity type");
        if (r.look < 0) {
            int mother = Kin.record(p).look();
            r.look = mother >= 0 ? mother : p.getRandom().nextInt(FatherEntity.LOOKS);
        }
        f.setup(p, r.look);
        f.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        Raids.slipTo(f, p);
        level.addFreshEntity(f);
        FATHERS.put(p.getUUID(), f.getUUID());
        FatherEntity.pillar(level, f.position());
        return f;
    }

    /** An operator calls the hunter's father down beside them now; he stays until sent away ({@link #dismiss}). */
    public static FatherEntity call(ServerPlayer p) {
        FatherEntity f = summon(p, MainStory.record(p));
        CALLED.add(p.getUUID());
        return f;
    }

    /** The hunter's father leaves the world at once (no light): the story or an operator sends him away. */
    public static void dismiss(ServerPlayer p) {
        CALLED.remove(p.getUUID());
        FatherEntity f = father(p);
        if (f != null) f.discard();
        FATHERS.remove(p.getUUID());
        // and any that lost track of whose they were
        AABB near = p.getBoundingBox().inflate(96);
        for (FatherEntity other : p.serverLevel().getEntitiesOfClass(FatherEntity.class, near, e -> p.getUUID().equals(e.owner()))) other.discard();
    }

    private MainFights() {}
}
