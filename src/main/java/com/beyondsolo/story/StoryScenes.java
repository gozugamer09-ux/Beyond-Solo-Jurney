package com.beyondsolo.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.StoryLinePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Story scenes: lines of dialogue at the bottom of the hunter's screen, now and then a camera that leaves them to
 * film the moment ({@link CutscenePayload}), a blink to black while something changes, and what the scene does to
 * the world at its beats. A scene is built once ({@link Scene}) and played for one hunter; the hide key skips it, and
 * skipping (or leaving the game) still does everything the scene changes in the world ("essential" beats), only
 * without the show. While a scene holds a hunter still nothing can hurt them.
 *
 * <p>Any part of the game can tell a scene: the Long Sleep's visit and waking ({@link LongSleep}) are the first.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class StoryScenes {
    private static final ResourceLocation HOLD = BeyondSolo.id("story_scene_hold");
    /** Colours the lines of the people in a scene are named in. */
    public static final int HUNTER = 0xFF9CC9FF, MOTHER = 0xFFFFB38A;

    private record Beat(int at, Consumer<ServerPlayer> action, boolean essential) {}

    /** A scene being written: lines, shots and beats laid end to end on its own clock (ticks). */
    public static final class Scene {
        private final String id;
        private final List<Beat> beats = new ArrayList<>();
        private final List<Consumer<ServerPlayer>> endings = new ArrayList<>();
        private int cursor;
        private int length;
        private boolean hold;
        private boolean camera;

        public Scene(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** The scene's length so far (ticks). */
        public int length() {
            return Math.max(length, cursor);
        }

        /** Holds the hunter where they stand for the whole scene (and keeps them from harm). */
        public Scene hold() {
            hold = true;
            return this;
        }

        public Scene pause(int ticks) {
            cursor += ticks;
            return this;
        }

        /** Someone says something. */
        public Scene say(Component who, int color, Component text, int ticks) {
            send(StoryLinePayload.line(StoryLinePayload.SPEECH, who, text, ticks, color));
            cursor += ticks;
            return this;
        }

        /** The hunter thinks it. */
        public Scene think(Component text, int ticks) {
            send(StoryLinePayload.line(StoryLinePayload.THOUGHT, Component.empty(), text, ticks, HUNTER));
            cursor += ticks;
            return this;
        }

        /** What happens, quietly told. */
        public Scene narrate(Component text, int ticks) {
            send(StoryLinePayload.line(StoryLinePayload.NARRATION, Component.empty(), text, ticks, 0xFFE8E2D8));
            cursor += ticks;
            return this;
        }

        /** The camera takes a shot from here (the scene's clock does not move: lines run under it). */
        public Scene shot(CutscenePayload shot) {
            camera = true;
            beats.add(new Beat(cursor, p -> PacketDistributor.sendToPlayer(p, shot), false));
            return this;
        }

        /** The camera takes a shot worked out for the hunter at that beat (where they stand, where she lies). */
        public Scene shot(java.util.function.Function<ServerPlayer, CutscenePayload> shot) {
            camera = true;
            beats.add(new Beat(cursor, p -> PacketDistributor.sendToPlayer(p, shot.apply(p)), false));
            return this;
        }

        /** The screen dips to black and back; {@code inTheDark} happens while it is black. */
        public Scene blink(int ticks, @Nullable Consumer<ServerPlayer> inTheDark) {
            send(StoryLinePayload.blink(ticks));
            if (inTheDark != null) beats.add(new Beat(cursor + ticks / 2, inTheDark, true));
            cursor += ticks;
            return this;
        }

        /** Something the scene does to the world (also done when the scene is skipped). */
        public Scene run(Consumer<ServerPlayer> action) {
            beats.add(new Beat(cursor, action, true));
            return this;
        }

        /** A sound, a burst of light: part of the show only (left out when skipped). */
        public Scene show(Consumer<ServerPlayer> action) {
            beats.add(new Beat(cursor, action, false));
            return this;
        }

        /** Part of the show, {@code delay} ticks from here (the scene's clock does not move: it plays under what follows). */
        public Scene show(int delay, Consumer<ServerPlayer> action) {
            beats.add(new Beat(cursor + delay, action, false));
            length = Math.max(length, cursor + delay);
            return this;
        }

        /** Done when the scene is over, however it ends. */
        public Scene then(Consumer<ServerPlayer> action) {
            endings.add(action);
            return this;
        }

        private void send(StoryLinePayload payload) {
            beats.add(new Beat(cursor, p -> PacketDistributor.sendToPlayer(p, payload), false));
            length = Math.max(length, cursor);
        }
    }

    private static final class Running {
        final Scene scene;
        final ServerPlayer player;
        int tick;
        int next;

        Running(Scene scene, ServerPlayer player) {
            this.scene = scene;
            this.player = player;
        }
    }

    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    /** Plays a scene for a hunter (one already playing for them is finished first). */
    public static void play(ServerPlayer p, Scene scene) {
        Running old = RUNNING.remove(p.getUUID());
        if (old != null) finish(old, true);
        scene.beats.sort((a, b) -> Integer.compare(a.at(), b.at()));
        Running r = new Running(scene, p);
        RUNNING.put(p.getUUID(), r);
        PacketDistributor.sendToPlayer(p, StoryLinePayload.begin(scene.hold));
        if (scene.hold) hold(p, true);
        step(r);
    }

    public static boolean playing(ServerPlayer p) {
        return RUNNING.containsKey(p.getUUID());
    }

    /** Which scene is playing for the hunter, or null. */
    @Nullable
    public static String playingId(ServerPlayer p) {
        Running r = RUNNING.get(p.getUUID());
        return r == null ? null : r.scene.id;
    }

    /** The hide key: the rest of the scene happens at once, without the show. */
    public static void skip(ServerPlayer p) {
        Running r = RUNNING.remove(p.getUUID());
        if (r != null) finish(r, true);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (RUNNING.isEmpty()) return;
        for (Running r : List.copyOf(RUNNING.values())) {
            if (r.player.hasDisconnected() || !r.player.isAlive()) {
                RUNNING.remove(r.player.getUUID());
                finish(r, true);
                continue;
            }
            r.tick++;
            step(r);
            if (r.tick >= r.scene.length() && r.next >= r.scene.beats.size()) {
                RUNNING.remove(r.player.getUUID());
                finish(r, false);
            }
        }
    }

    private static void step(Running r) {
        List<Beat> beats = r.scene.beats;
        while (r.next < beats.size() && beats.get(r.next).at() <= r.tick) {
            beats.get(r.next++).action().accept(r.player);
        }
    }

    /** Ends a scene: what it still had to do to the world is done now (all of it when it was cut short). */
    private static void finish(Running r, boolean early) {
        ServerPlayer p = r.player;
        if (early) {
            List<Beat> beats = r.scene.beats;
            while (r.next < beats.size()) {
                Beat b = beats.get(r.next++);
                if (b.essential()) b.action().accept(p);
            }
        }
        if (!p.hasDisconnected()) {
            PacketDistributor.sendToPlayer(p, StoryLinePayload.end());
            if (r.scene.camera) PacketDistributor.sendToPlayer(p, CutscenePayload.stop());
        }
        if (r.scene.hold) hold(p, false);
        for (Consumer<ServerPlayer> end : r.scene.endings) end.accept(p);
    }

    /** Holds a hunter still (no walking, no jumping) for a scene, or lets them go. */
    static void hold(ServerPlayer p, boolean on) {
        for (AttributeInstance a : new AttributeInstance[]{p.getAttribute(Attributes.MOVEMENT_SPEED), p.getAttribute(Attributes.JUMP_STRENGTH)}) {
            if (a == null) continue;
            a.removeModifier(HOLD);
            if (on) a.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (on) p.setDeltaMovement(Vec3.ZERO);
    }

    /** Nothing hurts a hunter held in a scene. */
    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Running r = RUNNING.get(p.getUUID());
        if (r != null && r.scene.hold) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Running r = RUNNING.remove(p.getUUID());
        if (r != null) finish(r, true);
    }

    /** A hunter logging in is never held by a scene that is no longer playing. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !RUNNING.containsKey(p.getUUID())) hold(p, false);
    }

    private StoryScenes() {}
}
