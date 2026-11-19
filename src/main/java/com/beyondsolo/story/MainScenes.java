package com.beyondsolo.story;

import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The scenes of the System's story ({@link MainStory}), told with {@link StoryScenes}: the prologue, the voice in the
 * hunter's head between the early chapters, the truth at the throne with the vision of the King of Night, the
 * monarchs' threats, their father's coming, the talk after the first fight, his last light, and home. The held ones
 * film the hunter wherever they stand, so every camera spot is checked against the walls and hills round them.
 */
public final class MainScenes {
    public static final String PROLOGUE_SCENE = "main_prologue", STATUE_SCENE = "main_statue", ARISE_SCENE = "main_arise",
            VHAROS_SCENE = "main_vharos", TRUTH_SCENE = "main_truth", MONARCH_SCENE = "main_monarch", ARRIVES_SCENE = "main_arrives",
            FATHER_SCENE = "main_father", WAR_SCENE = "main_war", NEXT_SCENE = "main_next", LAST_LIGHT_SCENE = "main_last_light",
            HOME_SCENE = "main_home", END_SCENE = "main_end";
    /** Every scene an operator can play ({@code /solo story main scene}). */
    public static final List<String> PLAYABLE = List.of("prologue", "statue", "arise", "vharos", "truth", "father", "war", "last_light", "home", "end");

    /** The colours the story's speakers are named in. */
    public static final int SYSTEM = 0xFF5FD0FF, FATHER = 0xFFFFE08A, KING = 0xFFB9A2FF, VOICE = 0xFFBFC4CC, VHAROS = 0xFFFF5A6E;

    /** The vision each hunter is being shown. */
    private static final Map<UUID, UUID> VISIONS = new HashMap<>();

    static Component line(String scene, Object n) {
        return Component.translatable("story.beyondsolo.main." + scene + "." + n);
    }

    static Component speaker(String who) {
        return Component.translatable("story.beyondsolo.speaker." + who);
    }

    private static StoryScenes.Scene father(StoryScenes.Scene s, String scene, Object n, int ticks) {
        return s.say(speaker("father"), FATHER, line(scene, n), ticks);
    }

    private static StoryScenes.Scene king(StoryScenes.Scene s, int n, int ticks) {
        return s.say(speaker("king"), KING, line("truth", n), ticks);
    }

    private static StoryScenes.Scene hunter(StoryScenes.Scene s, ServerPlayer p, String scene, Object n, int ticks) {
        return s.say(p.getName(), StoryScenes.HUNTER, line(scene, n), ticks);
    }

    private static int color(WorldBossKind kind) {
        return 0xFF000000 | kind.color;
    }

    // ================================================================ which scene

    /** The scene a finished chapter owes the hunter. */
    static void ending(ServerPlayer p, int chapter) {
        switch (chapter) {
            case MainStory.PLAYER -> StoryScenes.play(p, statue());
            case MainStory.ARISE -> StoryScenes.play(p, arise());
            case MainStory.CITADEL -> StoryScenes.play(p, vharos());
            case MainStory.THRONE -> StoryScenes.play(p, truth(p));
            case MainStory.FATHER -> StoryScenes.play(p, fatherTalk(p));
            case MainStory.LAST_WAR -> StoryScenes.play(p, lastLight(p));
            case MainStory.HOME -> StoryScenes.play(p, end());
            default -> MainStory.advance(p, chapter);
        }
    }

    /** Plays one of the story's scenes for an operator (the scene's own changes to the story only if it is due). */
    public static boolean play(ServerPlayer p, String name) {
        StoryScenes.Scene s = switch (name) {
            case "prologue" -> prologueScene(p);
            case "statue" -> statue();
            case "arise" -> arise();
            case "vharos" -> vharos();
            case "truth" -> truth(p);
            case "father" -> {
                standsBy(p);
                yield fatherTalk(p);
            }
            case "war" -> war(p, p.blockPosition().offset(24, 0, 24));
            case "last_light" -> {
                standsBy(p);
                yield lastLight(p);
            }
            case "home" -> {
                KinEntity k = Kin.find(p);
                yield k == null ? null : home(p, k);
            }
            case "end" -> end();
            default -> null;
        };
        if (s == null) return false;
        StoryScenes.play(p, s);
        return true;
    }

    /** An operator's scene with their father in it: if the story hasn't brought him, he is called down to stay for it. */
    private static void standsBy(ServerPlayer p) {
        if (MainFights.father(p) == null) MainFights.call(p);
    }

    // ================================================================ the prologue

    static void prologue(ServerPlayer p) {
        StoryScenes.play(p, prologueScene(p));
    }

    /** Ten years ago: the gates, their father, their mother, and the System's first words. */
    static StoryScenes.Scene prologueScene(ServerPlayer p) {
        int tier = SystemAPI.get(p).rank().tier();
        String strength = tier <= 1 ? "weak" : tier <= 3 ? "mid" : "strong";
        return new StoryScenes.Scene(PROLOGUE_SCENE).hold()
                .blink(24, null)
                .shot(h -> around(h, 0.3f, 560))
                .narrate(line("prologue", 1), 115)
                .narrate(line("prologue", 2), 135)
                .narrate(line("prologue", 3), 130)
                .narrate(line("prologue", 4), 130)
                .narrate(line("prologue", strength), 120)
                .shot(h -> skyward(h, 200))
                .think(line("prologue", 6), 65)
                .show(h -> sound(h, h.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f))
                .system(line("prologue", 7), 95)
                .system(line("prologue", 8), 95)
                .run(h -> MainStory.advance(h, MainStory.UNTOLD))
                .pause(10);
    }

    // ================================================================ the voice between the early chapters

    /** The end of the first chapter: a hollow-faced statue smiling at them. */
    static StoryScenes.Scene statue() {
        return new StoryScenes.Scene(STATUE_SCENE)
                .narrate(line("statue", 1), 120)
                .narrate(line("statue", 2), 55)
                .show(h -> sound(h, h.position(), SoundEvents.BELL_RESONATE, 0.7f, 0.5f))
                .say(speaker("voice"), VOICE, line("statue", 3), 95)
                .think(line("statue", 4), 70)
                .run(h -> MainStory.advance(h, MainStory.PLAYER));
    }

    /** The job changed: the voice again, and the Citadel. */
    static StoryScenes.Scene arise() {
        return new StoryScenes.Scene(ARISE_SCENE)
                .narrate(line("arise", 1), 110)
                .think(line("arise", 2), 95)
                .show(h -> sound(h, h.position(), SoundEvents.BELL_RESONATE, 0.7f, 0.5f))
                .say(speaker("voice"), VOICE, line("arise", 3), 95)
                .system(line("arise", 4), 105)
                .run(h -> MainStory.advance(h, MainStory.ARISE));
    }

    /** The Crimson King's last words, remembered. */
    static StoryScenes.Scene vharos() {
        return new StoryScenes.Scene(VHAROS_SCENE)
                .narrate(line("vharos", 1), 85)
                .say(speaker("vharos"), VHAROS, line("vharos", 2), 100)
                .say(speaker("vharos"), VHAROS, line("vharos", 3), 115)
                .say(speaker("vharos"), VHAROS, line("vharos", 4), 115)
                .think(line("vharos", 5), 60)
                .run(h -> MainStory.advance(h, MainStory.CITADEL));
    }

    // ================================================================ the truth

    /** The empty throne answered them: Morvath, the first King of Night, tells them what the System was for. */
    static StoryScenes.Scene truth(ServerPlayer p) {
        StoryScenes.Scene s = new StoryScenes.Scene(TRUTH_SCENE).hold()
                .show(h -> sound(h, h.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f))
                .blink(36, null)
                .show(18, MainScenes::summonVision)
                .shot(h -> overShoulder(h, visionAt(h), 220))
                .narrate(line("truth", 1), 115)
                .narrate(line("truth", 2), 105);
        king(s, 3, 80);
        hunter(s, p, "truth", 4, 55);
        king(s, 5, 115);
        s.shot(h -> pushIn(h, visionAt(h), 520));
        king(s, 6, 135);
        king(s, 7, 125);
        king(s, 8, 115);
        king(s, 9, 125);
        hunter(s, p, "truth", 10, 95);
        king(s, 11, 115);
        s.shot(h -> aroundTwo(h, visionAt(h), 0.2f, 420));
        king(s, 12, 140);
        king(s, 13, 130);
        king(s, 14, 110);
        hunter(s, p, "truth", 15, 60);
        s.show(MainScenes::fadeVision)
                .pause(30)
                .blink(30, null)
                .show(15, MainScenes::removeVision)
                .narrate(line("truth", 16), 95)
                .run(h -> {
                    removeVision(h);
                    MainStory.advance(h, MainStory.THRONE);
                })
                .then(MainScenes::removeVision)
                .pause(10);
        return s;
    }

    /** Morvath, a few steps in front of the hunter, facing them; the hunter turned to face him. */
    static void summonVision(ServerPlayer p) {
        removeVision(p);
        ServerLevel level = p.serverLevel();
        VisionEntity v = StoryModule.VISION.get().create(level);
        if (v == null) return;
        Vec3 at = front(p, 3.4);
        v.moveTo(at.x, at.y, at.z, 0, 0);
        v.face(p.getEyePosition());
        level.addFreshEntity(v);
        VISIONS.put(p.getUUID(), v.getUUID());
        turnTo(p, v.getEyePosition());
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 30, 0.4, 0.9, 0.4, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.5, 1.0, 0.5, 0.1);
        sound(p, at, SoundEvents.WITHER_AMBIENT, 0.6f, 0.4f);
    }

    @Nullable
    static VisionEntity vision(ServerPlayer p) {
        UUID id = VISIONS.get(p.getUUID());
        return id != null && p.serverLevel().getEntity(id) instanceof VisionEntity v && v.isAlive() ? v : null;
    }

    /** Where the vision stands (or would). */
    static Vec3 visionAt(ServerPlayer p) {
        VisionEntity v = vision(p);
        return v != null ? v.position() : front(p, 3.4);
    }

    static void fadeVision(ServerPlayer p) {
        VisionEntity v = vision(p);
        if (v == null) return;
        v.fade(60);
        sound(p, v.position(), SoundEvents.SOUL_ESCAPE.value(), 1f, 0.6f);
    }

    static void removeVision(ServerPlayer p) {
        VisionEntity v = vision(p);
        if (v != null) v.discard();
        VISIONS.remove(p.getUUID());
    }

    // ================================================================ the monarchs

    /** The story called a monarch down on the hunter: what it and the System say (the Last War's opening is filmed). */
    static void monarchComes(ServerPlayer p, WorldBossKind kind, BlockPos home) {
        switch (kind) {
            case HUNGER -> StoryScenes.play(p, new StoryScenes.Scene(MONARCH_SCENE)
                    .system(line("hunger", 1), 110)
                    .show(h -> sound(h, h.position(), SoundEvents.WOLF_GROWL, 1f, 0.5f))
                    .say(kind.bossName(), color(kind), line("hunger", 2), 105));
            case STILLNESS -> StoryScenes.play(p, war(p, home));
            default -> StoryScenes.play(p, new StoryScenes.Scene(MONARCH_SCENE).system(line("storms", 3), 100));
        }
    }

    /** The Last War opens: two monarchs speak from the sky. */
    static StoryScenes.Scene war(ServerPlayer p, BlockPos home) {
        Vec3 sky = Vec3.atBottomCenterOf(home).add(0, com.beyondsolo.worldboss.MonarchBoss.GATE_HEIGHT, 0);
        return new StoryScenes.Scene(WAR_SCENE).hold()
                .shot(h -> lookUp(h, sky, 330))
                .show(h -> sound(h, h.position(), SoundEvents.END_PORTAL_SPAWN, 0.8f, 0.6f))
                .system(line("war", 1), 100)
                .say(WorldBossKind.STILLNESS.bossName(), color(WorldBossKind.STILLNESS), line("war", 2), 115)
                .show(h -> sound(h, h.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.7f))
                .say(WorldBossKind.STORMS.bossName(), color(WorldBossKind.STORMS), line("war", 3), 110)
                .think(line("war", 4), 75);
    }

    /** One of the Last War's monarchs fell; the next speaks. */
    static void nextMonarch(ServerPlayer p, @Nullable WorldBossKind next) {
        if (next != WorldBossKind.STORMS || StoryScenes.playing(p)) return;
        StoryScenes.Scene s = new StoryScenes.Scene(NEXT_SCENE)
                .pause(60)
                .show(h -> sound(h, h.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f))
                .say(next.bossName(), color(next), line("storms", 1), 100);
        if (MainFights.father(p) != null) father(s, "storms", 2, 95);
        StoryScenes.play(p, s);
    }

    // ================================================================ their father

    /** He came down beside them: the first time, who he is (almost); after that, a promise kept. */
    static void fatherArrives(ServerPlayer p, boolean first) {
        if (StoryScenes.playing(p)) return;
        StoryScenes.Scene s = new StoryScenes.Scene(ARRIVES_SCENE);
        if (first) {
            s.narrate(line("arrives", 1), 95);
            father(s, "arrives", 2, 95);
            s.think(line("arrives", 3), 60);
            father(s, "arrives", 4, 85);
        } else {
            father(s, "arrives", 5, 80);
        }
        StoryScenes.play(p, s);
    }

    /** After the first monarch: who he is, where he has been, and a promise. */
    static StoryScenes.Scene fatherTalk(ServerPlayer p) {
        boolean awake = Kin.record(p).awake();
        StoryScenes.Scene s = new StoryScenes.Scene(FATHER_SCENE).hold()
                .blink(30, null)
                .show(15, h -> facing(h, FatherEntity.STILL))
                .shot(h -> twoShot(h, fatherAt(h), 1, 300))
                .narrate(line("father", 1), 100);
        hunter(s, p, "father", 2, 70);
        father(s, "father", 3, 100);
        s.shot(h -> overShoulder(h, fatherAt(h), 380));
        father(s, "father", 4, 115);
        father(s, "father", 5, 130);
        hunter(s, p, "father", 6, 70);
        father(s, "father", awake ? "awake" : "asleep", 125);
        s.shot(h -> twoShot(h, fatherAt(h), -1, 330));
        father(s, "father", 8, 130);
        father(s, "father", 9, 120);
        s.show(h -> {
                    FatherEntity f = MainFights.father(h);
                    if (f != null) f.setState(FatherEntity.RISING);
                    if (f != null) FatherEntity.pillar(h.serverLevel(), f.position());
                })
                .shot(h -> rising(h, fatherAt(h), 170))
                .narrate(line("father", 10), 90)
                .think(line("father", 11), 70)
                .run(h -> {
                    MainFights.dismiss(h);
                    MainStory.advance(h, MainStory.FATHER);
                })
                .pause(10);
        return s;
    }

    /** The Last War is won, and his light runs out. */
    static StoryScenes.Scene lastLight(ServerPlayer p) {
        StoryScenes.Scene s = new StoryScenes.Scene(LAST_LIGHT_SCENE).hold()
                .blink(30, null)
                .show(15, h -> facing(h, FatherEntity.KNEELING))
                .shot(h -> aroundTwo(h, fatherAt(h), 0.18f, 560))
                .narrate(line("last_light", 1), 105)
                .narrate(line("last_light", 2), 110);
        hunter(s, p, "last_light", 3, 45);
        father(s, "last_light", 4, 115);
        father(s, "last_light", 5, 125);
        s.shot(h -> overShoulder(h, fatherAt(h).add(0, -0.4, 0), 420));
        father(s, "last_light", 6, 130);
        hunter(s, p, "last_light", 7, 70);
        father(s, "last_light", 8, 120);
        s.show(h -> {
                    FatherEntity f = MainFights.father(h);
                    if (f != null) f.setState(FatherEntity.RISING);
                    sound(h, fatherAt(h), SoundEvents.BEACON_DEACTIVATE, 1f, 0.8f);
                })
                .shot(h -> rising(h, fatherAt(h), 200))
                .narrate(line("last_light", 9), 120)
                .blink(30, null)
                .show(15, MainFights::dismiss)
                .system(line("last_light", 10), 90)
                .run(h -> {
                    MainFights.dismiss(h);
                    MainStory.advance(h, MainStory.LAST_WAR);
                })
                .pause(10);
        return s;
    }

    /** He stands (or kneels) a couple of steps in front of the hunter, facing them; they turn to him. */
    static void facing(ServerPlayer p, byte state) {
        FatherEntity f = MainFights.father(p);
        if (f == null || f.level() != p.level()) f = MainFights.summon(p, MainStory.record(p));
        Vec3 at = front(p, state == FatherEntity.KNEELING ? 1.9 : 2.4);
        f.moveTo(at.x, at.y, at.z, f.getYRot(), 0);
        f.setState(state);
        f.face(p.getEyePosition());
        turnTo(p, f.getEyePosition().add(0, state == FatherEntity.KNEELING ? -0.3 : 0, 0));
    }

    static Vec3 fatherAt(ServerPlayer p) {
        FatherEntity f = MainFights.father(p);
        return f != null ? f.position() : front(p, 2.4);
    }

    // ================================================================ home

    /** Home at last: near their mother, the story's last scene with her. */
    static void homecoming(ServerPlayer p) {
        KinEntity k = Kin.find(p);
        if (k == null || k.level() != p.level() || p.distanceToSqr(k) > 5.5 * 5.5) return;
        homecoming(p, k);
    }

    /** Talking to her (or being near her) while the story waits at home plays its scene. False if it does not. */
    static boolean homecoming(ServerPlayer p, KinEntity k) {
        if (!StoryConfig.mainEnabled()) return false;
        MainRecord r = MainStory.record(p);
        if (r.chapter != MainStory.HOME || r.owed != 0 || StoryScenes.playing(p)) return false;
        StoryScenes.play(p, home(p, k));
        return true;
    }

    /** Telling her: awake, she already knows; asleep, they tell her anyway. */
    static StoryScenes.Scene home(ServerPlayer p, KinEntity k) {
        boolean awake = Kin.record(p).awake() && k.state() != KinEntity.SLEEPING;
        // lying in her bed: the bedside's own shots; sitting up on it or on her feet: the two of them
        boolean inBed = k.state() == KinEntity.SLEEPING || k.state() == KinEntity.STIRRING;
        StoryScenes.Scene s = new StoryScenes.Scene(HOME_SCENE).hold();
        if (inBed) {
            s.show(12, h -> LongSleep.toBedside(h, k))
                    .blink(24, null)
                    .shot(h -> CutscenePayload.glide(LongSleep.shoulder(k, h), LongSleep.wide(k, h), LongSleep.face(k), LongSleep.face(k), 300));
        } else {
            if (k.state() == KinEntity.SITTING) s.show(12, h -> LongSleep.toBedside(h, k));
            s.show(12, h -> turnTo(h, k.getEyePosition()))
                    .blink(24, null)
                    .shot(h -> twoShot(h, k.position(), 1, 320));
        }
        if (awake) {
            s.say(LongSleep.mother(), StoryScenes.MOTHER, line("home", "awake.1"), 110);
            hunter(s, p, "home", "awake.2", 95);
            s.say(LongSleep.mother(), StoryScenes.MOTHER, line("home", "awake.3"), 135)
                    .say(LongSleep.mother(), StoryScenes.MOTHER, line("home", "awake.4"), 110);
            if (inBed) s.shot(h -> LongSleep.turnAround(k, h, 0.18f, 330));
            else s.shot(h -> aroundTwo(h, k.position(), 0.16f, 330));
            s.say(LongSleep.mother(), StoryScenes.MOTHER, line("home", "awake.5"), 100);
        } else {
            s.narrate(line("home", "asleep.1"), 90);
            hunter(s, p, "home", "asleep.2", 110);
            hunter(s, p, "home", "asleep.3", 115);
            if (inBed) s.shot(h -> LongSleep.turnAround(k, h, 0.18f, 260));
            else s.shot(h -> aroundTwo(h, k.position(), 0.16f, 260));
            s.narrate(line("home", "asleep.4"), 100);
        }
        return s.narrate(line("home", 6), 120)
                .run(h -> QuestEngine.progress(h, MainStory.HOME_KEY, 1))
                .pause(15);
    }

    /** The System's last words: its purpose is fulfilled. */
    static StoryScenes.Scene end() {
        return new StoryScenes.Scene(END_SCENE)
                .show(h -> sound(h, h.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.2f))
                .system(line("end", 1), 100)
                .system(line("end", 2), 110)
                .system(line("end", 3), 110)
                .run(h -> MainStory.advance(h, MainStory.HOME));
    }

    // ================================================================ where things stand

    /** A spot {@code dist} in front of the hunter where someone can stand (the nearest the ground allows). */
    static Vec3 front(ServerPlayer p, double dist) {
        Level level = p.level();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        for (double d : new double[]{dist, dist - 0.6, dist + 0.6, dist - 1.1}) {
            for (double turn : new double[]{0, 0.45, -0.45, 0.9, -0.9}) {
                Vec3 dir = flat.yRot((float) turn);
                Vec3 want = p.position().add(dir.scale(d));
                BlockPos at = standable(level, BlockPos.containing(want));
                if (at != null && Math.abs(at.getY() - p.getY()) <= 2.5 && clear(level, p.getEyePosition(), Vec3.atBottomCenterOf(at).add(0, 1.5, 0))) {
                    return new Vec3(want.x, at.getY(), want.z);
                }
            }
        }
        return p.position().add(flat.scale(Math.min(dist, 1.5)));
    }

    /** Where someone can stand in this column, a few blocks up or down from {@code at} (null: nowhere). */
    @Nullable
    static BlockPos standable(Level level, BlockPos at) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = at.offset(0, dy, 0);
            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q).getFluidState().isEmpty()
                    && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                    && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) return q;
        }
        return null;
    }

    /** Whether nothing solid stands between two points. */
    static boolean clear(Level level, Vec3 a, Vec3 b) {
        return LongSleep.clear(level, a, b).distanceTo(b) < 0.05;
    }

    /** Turns the hunter where they stand to look at {@code at}. */
    static void turnTo(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }

    static void sound(ServerPlayer p, Vec3 at, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    // ================================================================ the camera, wherever they stand

    /** A slow turn round the hunter, as wide as the land round them allows; where none fits, a drift past them. */
    static CutscenePayload around(ServerPlayer p, float turns, int ticks) {
        Vec3 center = p.position().add(0, 1.1, 0);
        float start = startFacing(p);
        CutscenePayload orbit = orbitFor(p.level(), center, start, turns, ticks, new double[][]{{4.6, 1.4}, {3.6, 1.2}, {2.8, 1.0}});
        if (orbit != null) return orbit;
        Vec3 eye = p.getEyePosition();
        return CutscenePayload.glide(LongSleep.clear(p.level(), eye, eye.add(back(p).scale(2.4)).add(0, 0.9, 0)),
                LongSleep.clear(p.level(), eye, eye.add(back(p).scale(1.6)).add(0, 2.2, 0)), eye, eye, ticks);
    }

    /** A slow turn round the hunter and someone in front of them. */
    static CutscenePayload aroundTwo(ServerPlayer p, Vec3 other, float turns, int ticks) {
        Vec3 center = p.position().add(other).scale(0.5).add(0, 1.0, 0);
        Vec3 side = side(p.position(), other);
        float start = (float) (Math.atan2(side.z, side.x) / (Math.PI * 2));
        CutscenePayload orbit = orbitFor(p.level(), center, start, turns, ticks, new double[][]{{4.4, 1.5}, {3.5, 1.3}, {2.8, 1.1}});
        return orbit != null ? orbit : twoShot(p, other, 1, ticks);
    }

    @Nullable
    private static CutscenePayload orbitFor(Level level, Vec3 center, float start, float turns, int ticks, double[][] sizes) {
        float[] starts = {start, start + 0.12f, start - 0.12f, start + 0.5f, start + 0.25f, start - 0.25f};
        for (double[] size : sizes) {
            for (float s0 : starts) {
                if (arcClear(level, center, size[0], size[1], s0, turns)) return CutscenePayload.orbit(center, size[0], size[1], s0, turns, ticks);
            }
        }
        return null;
    }

    private static boolean arcClear(Level level, Vec3 center, double radius, double height, float start, float turns) {
        for (int i = 0; i <= 12; i++) {
            double a = (start + turns * i / 12.0) * Math.PI * 2;
            Vec3 at = center.add(Math.cos(a) * radius, height, Math.sin(a) * radius);
            if (!clear(level, center, at)) return false;
            BlockPos b = BlockPos.containing(at);
            if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty() || !level.getBlockState(b).getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** The hunter's facing as an orbit's start (in turns, on the orbit's scale): the camera opens in front of them. */
    private static float startFacing(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        return (float) (Math.atan2(look.z, look.x) / (Math.PI * 2));
    }

    /** Behind the hunter, flat. */
    private static Vec3 back(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(-look.x, 0, -look.z);
        return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, -1) : flat.normalize();
    }

    /** Square to the line from {@code a} to {@code b}, flat (a unit vector). */
    private static Vec3 side(Vec3 a, Vec3 b) {
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        d = d.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : d.normalize();
        return new Vec3(-d.z, 0, d.x);
    }

    /** Low behind the hunter, tilting up from the land in front of them to the sky (under a roof: their face instead). */
    static CutscenePayload skyward(ServerPlayer p, int ticks) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 fwd = back(p).scale(-1);
        Vec3 sky = eye.add(fwd.scale(16)).add(0, 26, 0);
        if (!opensUp(level, eye, sky)) return faceShot(p, ticks);
        Vec3 from = LongSleep.clear(level, eye, eye.add(back(p).scale(2.2)).add(0, 0.2, 0));
        Vec3 to = LongSleep.clear(level, eye, eye.add(back(p).scale(2.8)).add(0, 0.9, 0));
        return CutscenePayload.glide(from, to, eye.add(fwd.scale(10)).add(0, 1, 0), sky, ticks);
    }

    /** Whether the way from {@code eye} up toward a point in the sky is open for a good way (no roof, rock or canopy). */
    static boolean opensUp(Level level, Vec3 eye, Vec3 sky) {
        Vec3 up = sky.subtract(eye);
        return clear(level, eye, eye.add(up.normalize().scale(Math.min(14, up.length()))));
    }

    /** In front of the hunter, easing in on their face: for a moment with no sky or room round them for a wider shot. */
    static CutscenePayload faceShot(ServerPlayer p, int ticks) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 fwd = back(p).scale(-1);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        for (int sign : new int[]{1, -1}) {
            Vec3 from = LongSleep.clear(level, eye, eye.add(fwd.scale(2.3)).add(right.scale(0.75 * sign)).add(0, 0.15, 0));
            if (from.distanceTo(eye) < 1.3) continue;
            return CutscenePayload.glide(from, eye.add(from.subtract(eye).scale(0.7)), eye.add(0, -0.05, 0), eye, ticks);
        }
        return around(p, 0.12f, ticks);
    }

    /** From beside the hunter, looking up at a point in the sky (a gate opening); under a roof, their face instead. */
    static CutscenePayload lookUp(ServerPlayer p, Vec3 sky, int ticks) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        if (!opensUp(level, eye, sky)) return faceShot(p, ticks);
        Vec3 s = side(p.position(), sky);
        Vec3 toSky = new Vec3(sky.x - eye.x, 0, sky.z - eye.z);
        toSky = toSky.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : toSky.normalize();
        Vec3 from = LongSleep.clear(level, eye, eye.add(s.scale(1.8)).add(toSky.scale(-1.6)).add(0, -0.3, 0));
        Vec3 to = LongSleep.clear(level, eye, eye.add(s.scale(2.4)).add(toSky.scale(-2.6)).add(0, 0.4, 0));
        return CutscenePayload.glide(from, to, eye.add(toSky.scale(6)).add(0, 3, 0), sky, ticks);
    }

    /** Two people side on: the hunter and someone in front of them, from one side ({@code side} 1 or -1). */
    static CutscenePayload twoShot(ServerPlayer p, Vec3 other, int side, int ticks) {
        Level level = p.level();
        Vec3 mid = p.position().add(other).scale(0.5).add(0, 1.45, 0);
        Vec3 s = side(p.position(), other).scale(side);
        double gap = Math.max(2.0, p.position().distanceTo(other));
        Vec3 wantFrom = mid.add(s.scale(gap * 1.25 + 1.2)).add(0, 0.35, 0);
        Vec3 wantTo = mid.add(s.scale(gap * 1.05 + 0.9)).add(0, 0.25, 0);
        Vec3 from = LongSleep.clear(level, mid, wantFrom);
        Vec3 to = LongSleep.clear(level, mid, wantTo);
        if (from.distanceTo(mid) < 1.4) {
            // no room on that side: over the hunter's head instead
            return overShoulder(p, other, ticks);
        }
        return CutscenePayload.glide(from, to, mid, mid, ticks);
    }

    /** Over the hunter's shoulder onto someone in front of them, easing in. */
    static CutscenePayload overShoulder(ServerPlayer p, Vec3 other, int ticks) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 face = other.add(0, 1.55, 0);
        Vec3 back = new Vec3(eye.x - face.x, 0, eye.z - face.z);
        back = back.lengthSqr() < 1.0e-4 ? back(p) : back.normalize();
        Vec3 right = new Vec3(-back.z, 0, back.x);
        // a face lower than theirs (someone kneeling): wider and lower, so their head stays out of the way
        double below = Math.max(0, eye.y - face.y);
        double side = 1.0 + below * 0.8, up = 0.45 - below * 0.5;
        for (int sign : new int[]{1, -1}) {
            Vec3 want = eye.add(right.scale(side * sign)).add(back.scale(1.7)).add(0, up, 0);
            Vec3 c = LongSleep.clear(level, face, want);
            if (c.distanceTo(want) < 0.15 && c.distanceTo(eye) > 0.55) {
                Vec3 in = c.add(face.subtract(c).scale(0.12));
                return CutscenePayload.glide(c, in, face, face, ticks);
            }
        }
        Vec3 high = LongSleep.clear(level, face, eye.add(back.scale(0.5)).add(0, 1.2, 0));
        return CutscenePayload.glide(high, high.add(face.subtract(high).scale(0.15)), face, face, ticks);
    }

    /** Slowly in on someone's face from the hunter's side. */
    static CutscenePayload pushIn(ServerPlayer p, Vec3 other, int ticks) {
        Level level = p.level();
        Vec3 face = other.add(0, 1.6, 0);
        Vec3 eye = p.getEyePosition();
        Vec3 toThem = new Vec3(face.x - eye.x, 0, face.z - eye.z);
        toThem = toThem.lengthSqr() < 1.0e-4 ? back(p).scale(-1) : toThem.normalize();
        Vec3 right = new Vec3(-toThem.z, 0, toThem.x);
        Vec3 from = LongSleep.clear(level, face, face.subtract(toThem.scale(2.6)).add(right.scale(0.9)).add(0, -0.2, 0));
        Vec3 to = LongSleep.clear(level, face, face.subtract(toThem.scale(1.7)).add(right.scale(0.6)).add(0, -0.1, 0));
        return CutscenePayload.glide(from, to, face, face.add(0, 0.05, 0), ticks);
    }

    /** Watching someone rise into the sky, from a little way back and below. */
    static CutscenePayload rising(ServerPlayer p, Vec3 other, int ticks) {
        Level level = p.level();
        Vec3 base = other.add(0, 1.2, 0);
        Vec3 s = side(p.position(), other);
        Vec3 from = LongSleep.clear(level, base, base.add(s.scale(3.4)).add(0, -0.4, 0));
        return CutscenePayload.glide(from, from.add(0, 0.8, 0), base, base.add(0, 6, 0), ticks);
    }

    /** Ends the hunter's story scenes' leftovers (a vision left in the world). */
    static void cleanUp(ServerPlayer p) {
        removeVision(p);
    }

    /** For tests: the vision being shown to the hunter, if any. */
    @Nullable
    public static Entity visionOf(ServerPlayer p) {
        return vision(p);
    }

    private MainScenes() {}
}
