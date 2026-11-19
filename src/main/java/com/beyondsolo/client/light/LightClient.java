package com.beyondsolo.client.light;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.client.skill.SkillOverlay;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.network.LightActionPayload;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.network.LightSyncPayload;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's side of the light: the player's own road (from {@link LightSyncPayload}) and what rulers of light nearby
 * have running (from {@link LightFxPayload}): their sanctums and seraph forms, drawn in the world
 * ({@code WorldBossWorldFx}, {@link SeraphLayer}), and the light's moments on screen ({@link LightHud},
 * {@link CovenantScreen}, {@link MantleScreen}).
 */
public final class LightClient {
    private static boolean sworn;
    private static @Nullable Mantle mantle;
    private static boolean called;
    private static int minLevel = 80;

    /** A sanctum or a seraph form round a ruler, in game time. */
    public static final class Glow {
        public final int entity;
        public final int color;
        /** A sanctum's radius. */
        public final int size;
        public final long since;
        public final long until;
        /** When it was ended early (game time), or -1. */
        public long endedAt = -1;

        Glow(int entity, int color, int size, long since, long until) {
            this.entity = entity;
            this.color = color;
            this.size = size;
            this.since = since;
            this.until = until;
        }

        /** 0..1: grows in over half a second, fades over half a second when it ends. */
        public float strength(double now) {
            float in = (float) Math.min(1, (now - since) / 10.0);
            long end = endedAt >= 0 ? endedAt : until;
            float out = (float) Math.min(1, Math.max(0, (end + 10 - now) / 10.0));
            return Math.max(0, Math.min(in, out));
        }

        /** 0..1 of its time left (1 when it began). */
        public float left(double now) {
            if (endedAt >= 0) return 0;
            return (float) Math.max(0, Math.min(1, (until - now) / Math.max(1, until - since)));
        }

        public boolean over(double now) {
            return now > (endedAt >= 0 ? endedAt : until) + 10;
        }
    }

    private static final Map<Integer, Glow> SANCTUMS = new HashMap<>();
    private static final Map<Integer, Glow> SERAPHS = new HashMap<>();

    // ================================================================ the player's own road

    public static boolean sworn() {
        return sworn;
    }

    /** The mantle they took up, or null before the Investiture. */
    public static @Nullable Mantle mantle() {
        return mantle;
    }

    public static boolean invested() {
        return sworn && mantle != null;
    }

    /** The light has called them to the Investiture. */
    public static boolean called() {
        return called;
    }

    public static int minLevel() {
        return minLevel;
    }

    public static void onSync(LightSyncPayload p) {
        sworn = p.sworn();
        mantle = p.mantle() < 0 ? null : Mantle.byIndex(p.mantle());
        called = p.called();
        minLevel = p.minLevel();
    }

    // ================================================================ the light's moments

    public static void onFx(LightFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        boolean self = mc.player != null && p.entity() == mc.player.getId();
        switch (p.kind()) {
            case LightFxPayload.SANCTUM -> {
                glow(SANCTUMS, p, now);
                if (self && p.ticks() > 0) LightHud.raised(p.color(), false);
            }
            case LightFxPayload.SERAPH -> {
                glow(SERAPHS, p, now);
                if (self && p.ticks() > 0) LightHud.raised(p.color(), true);
            }
            case LightFxPayload.JUDGEMENT -> LightHud.judgement(p);
            case LightFxPayload.AUTHORITY -> LightHud.authority(p);
            case LightFxPayload.CLASH -> LightHud.clash(p);
            case LightFxPayload.COVENANT -> CovenantScreen.open(p.entity(), p.size() == 1);
            case LightFxPayload.CALLED -> LightHud.called();
            case LightFxPayload.VIGIL -> LightHud.vigil(p.size(), p.ticks());
            case LightFxPayload.CHOOSE -> MantleScreen.open(Mantle.byIndex(p.size()), p.ticks());
            case LightFxPayload.INVESTITURE -> {
                MantleScreen.closeIfOpen();
                LightHud.investiture(Mantle.byIndex(p.size()));
            }
            case LightFxPayload.HUM -> LightHud.hum(p.size() == 1);
            case LightFxPayload.ENTER -> LightHud.enter();
            default -> {
            }
        }
    }

    private static void glow(Map<Integer, Glow> map, LightFxPayload p, long now) {
        if (p.ticks() <= 0) {
            Glow g = map.get(p.entity());
            if (g != null && g.endedAt < 0) g.endedAt = Math.min(now, g.until);
            return;
        }
        map.put(p.entity(), new Glow(p.entity(), p.color(), p.size(), now, now + p.ticks()));
    }

    /** Sanctums raised round rulers nearby (some may be fading out). */
    public static Collection<Glow> sanctums() {
        return SANCTUMS.values();
    }

    /** Seraph forms taken by rulers nearby (some may be fading out). */
    public static Collection<Glow> seraphs() {
        return SERAPHS.values();
    }

    /** The seraph form a ruler is in, or null. */
    public static @Nullable Glow seraphOf(int entity) {
        return SERAPHS.get(entity);
    }

    /** The sanctum a ruler holds, or null. */
    public static @Nullable Glow sanctumOf(int entity) {
        return SANCTUMS.get(entity);
    }

    /** How deep the local player stands in a sanctum (0 = in none), and the colour of the strongest one. */
    static int sanctumColorAt(Vec3 at, double now, float[] strength) {
        Minecraft mc = Minecraft.getInstance();
        int color = 0;
        float best = 0;
        for (Glow g : SANCTUMS.values()) {
            Entity owner = mc.level == null ? null : mc.level.getEntity(g.entity);
            if (owner == null || owner.position().distanceToSqr(at) > (double) g.size * g.size) continue;
            float s = g.strength(now);
            if (s > best) {
                best = s;
                color = g.color;
            }
        }
        strength[0] = best;
        return color;
    }

    // ================================================================ the crown keys

    /**
     * A crown key pressed by a hunter sworn to the light: what the client already knows (the power, its cooldown,
     * mana) shows at once; then the server is asked.
     */
    public static void press(int key) {
        Minecraft mc = Minecraft.getInstance();
        if (!sworn || mc.player == null || !ClientSystemData.get().isAwakened()) return;
        Skill s = RulerPowers.onKey(mantle, key);
        if (s == null || !ClientSkills.data().knows(s.path())) {
            LightBar.refusedKey(key);
            return;
        }
        int level = ClientSkills.data().level(s.path());
        if (ClientSkills.cooldownFraction(s, 0) > 0) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.COOLDOWN);
            return;
        }
        if (!mc.player.isCreative() && ClientSystemData.get().mana() < com.beyondsolo.rune.Runes.manaCost(com.beyondsolo.client.rune.RuneClient.data(), s, level)
                * com.beyondsolo.combat.ModAttributes.manaFactor(mc.player)) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.NO_MANA);
            return;
        }
        LightBar.casted(key);
        PacketDistributor.sendToServer(new LightActionPayload(LightActionPayload.POWER, key));
    }

    // ================================================================ upkeep

    /** Forgets what has ended (once a second). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() % 20 != 0) return;
        double now = now(0);
        SANCTUMS.values().removeIf(g -> g.over(now) || mc.level.getEntity(g.entity) == null && now - g.since > 40);
        SERAPHS.values().removeIf(g -> g.over(now) || mc.level.getEntity(g.entity) == null && now - g.since > 40);
    }

    /** Game time, with the frame's part of a tick. */
    public static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + (double) partial;
    }

    /** Another world: what was drawn there is gone. */
    static void clearWorld() {
        SANCTUMS.clear();
        SERAPHS.clear();
        SeraphLayer.clear();
    }

    /** Logging out. */
    static void clear() {
        sworn = false;
        mantle = null;
        called = false;
        clearWorld();
        LightHud.reset();
        LightBar.reset();
        MantleScreen.reset();
    }

    private LightClient() {}
}
