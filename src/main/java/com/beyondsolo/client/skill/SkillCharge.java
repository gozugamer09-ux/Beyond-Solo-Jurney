package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The power gathering while a hunter winds up for a skill ({@code skill.SkillWindup}), drawn round the hand or blade
 * the blow will come from. A light skill only flashes a glint on the blade as the swing starts; a stronger one draws
 * motes into the hand; a heavy one kicks dust off the ground at the start and pulls its element in (embers, droplets,
 * crackling sparks, pebbles lifting, ink...); an ultimate pulls streaks in from all round, drags the dust on the ground
 * towards the hunter, wraps them in their element and swells a light in the hand that bursts as the blow comes.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillCharge {
    /** How big the gathering is: a skill's stature, or 3 for an ultimate. */
    static final int LIGHT = 0, MEDIUM = 1, HEAVY = 2, ULTIMATE = 3;

    private static final class Charge {
        final AbstractClientPlayer player;
        final SkillElement element;
        final int color;
        final String gesture;
        final int weight;
        final int total;
        int age;

        Charge(AbstractClientPlayer player, Skill skill, String gesture, int ticks) {
            this.player = player;
            this.element = skill.element();
            this.color = skill.color();
            this.gesture = gesture;
            this.weight = weight(skill);
            this.total = Math.max(1, ticks);
        }

        /** 0 at the press, 1 at the release. */
        float k() {
            return Math.min(1, age / (float) total);
        }
    }

    private static final Map<AbstractClientPlayer, Charge> CHARGES = new WeakHashMap<>();

    static int weight(Skill skill) {
        return com.beyondsolo.skill.Gestures.heft(skill);
    }

    /** A hunter starts winding up for {@code skill}, to strike in {@code ticks}. */
    static void begin(AbstractClientPlayer player, Skill skill, String gesture, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Charge c = new Charge(player, skill, gesture, ticks);
        CHARGES.put(player, c);
        if (c.weight >= HEAVY) {
            // the stance taken: the ground answers at the hunter's feet
            Vec3 feet = player.position();
            int dust = FxParticles.groundColor(mc.level, feet.x, feet.y, feet.z);
            float r = c.weight == ULTIMATE ? 2.6f : 1.4f;
            FxParticles.shockwave(feet.x, feet.y, feet.z, r, c.weight == ULTIMATE ? 10 : 7, c.weight == ULTIMATE ? c.color : dust, 0.55f);
            FxParticles.smokeRing(feet.x, feet.y + 0.1, feet.z, c.weight == ULTIMATE ? 12 : 7, dust, 0.3f, c.weight == ULTIMATE ? 0.16f : 0.1f);
        }
    }

    /** The blow starts: the gathering bursts into a glint on the blade or a flash in the hand. */
    static void release(AbstractClientPlayer player) {
        Charge c = CHARGES.remove(player);
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc.level == null) return;
        SkillAftermath.vent(player, c.gesture, c.element, c.color, c.weight);
        boolean self = selfView(player);
        Vec3 at = hand(player, c.gesture, self);
        float size = switch (c.weight) {
            case ULTIMATE -> 1.3f;
            case HEAVY -> 0.85f;
            case MEDIUM -> 0.6f;
            default -> 0.45f;
        } * (self ? 0.6f : 1);
        // the glint: a hard four-pointed star, the anime "shing" on the edge about to cut
        FxParticles.add(FxParticles.Tex.FLARE, at.x, at.y, at.z, 0, 0, 0).life(c.weight >= HEAVY ? 6 : 4)
                .size(size * 0.3f, size).color(0xFFFFFF, c.color).alpha(1).fade(0, 0.3f).rot(45);
        if (c.weight >= HEAVY) {
            FxParticles.add(FxParticles.Tex.SHOCK, at.x, at.y, at.z, 0, 0, 0).life(5).size(0.1f, size * 1.2f)
                    .color(0xFFFFFF, c.color).alpha(0.8f).fade(0, 0.3f);
        }
        if (c.weight == ULTIMATE) {
            FxParticles.rays(at.x, at.y, at.z, size * 1.4f, c.color, 8);
            FxParticles.motesBurst(at.x, at.y, at.z, self ? 6 : 14, 0.2f, c.color);
        }
    }

    /** True while the hunter is winding up. */
    static boolean charging(AbstractClientPlayer player) {
        return CHARGES.containsKey(player);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (CHARGES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        for (Charge c : new ArrayList<>(CHARGES.values())) {
            c.age++;
            if (c.age > c.total + 4 || c.player.isRemoved()) {
                CHARGES.remove(c.player);
                continue;
            }
            gather(level, c);
        }
    }

    private static void gather(ClientLevel level, Charge c) {
        if (c.weight == LIGHT) return;
        boolean self = selfView(c.player);
        Vec3 at = hand(c.player, c.gesture, self);
        Vec3 view = c.player.getViewVector(1);
        float k = c.k();
        int col = c.color;
        // motes drawn into the hand, more of them and from further off the bigger the skill
        int motes = c.weight == MEDIUM ? 2 : c.weight == HEAVY ? 3 : 4;
        double reach = c.weight == MEDIUM ? 0.9 : c.weight == HEAVY ? 1.4 : 1.8;
        converge(at, view, self, reach, motes, 5 + c.weight, FxParticles.Tex.HOT, 0.07f, col, false);
        if (c.weight >= HEAVY && c.age % 2 == 0) element(level, c, at, view, self, k);
        if (c.weight == ULTIMATE) {
            // streaks pulled in from all round, and the light in the hand swelling
            converge(at, view, self, 3.6, 2, 8, FxParticles.Tex.SPARK, 0.05f, col, true);
            FxParticles.glow(at.x, at.y, at.z, (0.25f + 0.55f * k) * (self ? 0.6f : 1), col, 3, 0.55f + 0.35f * k);
            if (c.age % 2 == 0) {
                // the dust on the ground dragged towards the hunter
                Vec3 feet = c.player.position();
                int dust = FxParticles.groundColor(level, feet.x, feet.y, feet.z);
                for (int i = 0, n = FxParticles.count(3); i < n; i++) {
                    double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                    double r = 2.6 + FxParticles.RANDOM.nextDouble() * 1.4;
                    double x = feet.x + Math.cos(a) * r, z = feet.z + Math.sin(a) * r;
                    float life = FxParticles.rf(10, 14);
                    FxParticles.add(FxParticles.Tex.SMOKE, x, feet.y + 0.12, z, -Math.cos(a) * r / life, 0.004, -Math.sin(a) * r / life)
                            .frame(FxParticles.RANDOM.nextInt(16)).blend().life(life).size(0.22f, 0.08f).color(dust)
                            .alpha(0.45f).fade(0.2f, 0.6f).drag(1f).spin(FxParticles.rf(-3, 3));
                }
            }
            if (c.age % 3 == 0) aura(c, k);
        }
    }

    /**
     * {@code n} particles flying into {@code at} from up to {@code reach} away, arriving as they fade. Seen from the
     * hunter's own eyes they come from in front, so none fly through the view.
     */
    private static void converge(Vec3 at, Vec3 view, boolean self, double reach, int n, float life, FxParticles.Tex tex,
                                 float size, int rgb, boolean streak) {
        for (int i = 0, k = FxParticles.count(n); i < k; i++) {
            Vec3 d = new Vec3(FxParticles.rnd(1), FxParticles.rnd(1), FxParticles.rnd(1));
            if (d.lengthSqr() < 1.0e-4) continue;
            d = d.normalize();
            if (self && d.dot(view) < 0.1) d = d.add(view.scale(1.2)).normalize();
            double r = reach * FxParticles.rf(0.6f, 1f);
            float l = life * FxParticles.rf(0.8f, 1.2f);
            Vec3 from = at.add(d.scale(r));
            FxParticles.P p = FxParticles.add(tex, from.x, from.y, from.z, -d.x * r / l, -d.y * r / l, -d.z * r / l)
                    .life(l).color(rgb, 0xFFFFFF).alpha(streak ? 0.9f : 1).fade(0.25f, 0.7f).drag(1f);
            if (streak) p.size(size, size * 0.6f).stretch(4f);
            else p.size(size * 0.6f, size * 1.4f).flicker(0.2f);
        }
    }

    /** The skill's own element gathering (heavy skills and ultimates), every other tick. */
    private static void element(ClientLevel level, Charge c, Vec3 at, Vec3 view, boolean self, float k) {
        double reach = c.weight == ULTIMATE ? 1.6 : 1.1;
        switch (c.element) {
            case FIRE -> {
                converge(at, view, self, reach, 2, 7, FxParticles.Tex.EMBER, 0.06f, 0xFF7A2E, false);
                FxParticles.fire(at.x, at.y, at.z, 0.08, 1, 0.1f + 0.1f * k, 0.02f, 0xFFE0A0, 0xFF4A10);
            }
            case WATER -> {
                // droplets lifting off and turning round the hand
                for (int i = 0, n = FxParticles.count(2); i < n; i++) {
                    double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                    double r = reach * 0.7;
                    FxParticles.add(FxParticles.Tex.DROP, at.x + Math.cos(a) * r, at.y - 0.4, at.z + Math.sin(a) * r,
                                    -Math.cos(a) * r / 8, 0.05, -Math.sin(a) * r / 8)
                            .life(8).size(0.05f).color(0xFFFFFF, 0x4FA8FF).alpha(0.95f).fade(0.1f, 0.7f).drag(1f).swirl(0.05f);
                }
            }
            case LIGHTNING -> {
                // static crawling about the hand: short hard sparks that jump and are gone
                for (int i = 0, n = FxParticles.count(2 + c.weight); i < n; i++) {
                    Vec3 d = new Vec3(FxParticles.rnd(1), FxParticles.rnd(1), FxParticles.rnd(1)).normalize().scale(0.18);
                    FxParticles.add(FxParticles.Tex.SPARK, at.x + d.x * 2, at.y + d.y * 2, at.z + d.z * 2, -d.x, -d.y, -d.z)
                            .life(2).size(0.04f).color(0xFFFFFF, 0x9CD8FF).alpha(1).fade(0, 0.5f).drag(1f).stretch(3f);
                }
                if (FxParticles.RANDOM.nextFloat() < 0.4f) FxParticles.flash(at.x, at.y, at.z, 0.25f + 0.25f * k, 0xB8E2FF);
            }
            case AIR -> {
                for (int i = 0, n = FxParticles.count(2); i < n; i++) {
                    double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                    double r = reach;
                    FxParticles.add(FxParticles.Tex.WISP, at.x + Math.cos(a) * r, at.y + FxParticles.rnd(0.4), at.z + Math.sin(a) * r,
                                    -Math.cos(a) * r / 7 - Math.sin(a) * 0.12, 0, -Math.sin(a) * r / 7 + Math.cos(a) * 0.12)
                            .life(7).size(0.12f, 0.05f).color(0xDFFFF6).alpha(0.7f).fade(0.2f, 0.6f).drag(1f).spin(20);
                }
            }
            case EARTH -> {
                // pebbles lifting off the ground round the hunter's feet
                Vec3 feet = c.player.position();
                int dust = FxParticles.groundColor(level, feet.x, feet.y, feet.z);
                for (int i = 0, n = FxParticles.count(2); i < n; i++) {
                    double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                    double r = 0.6 + FxParticles.RANDOM.nextDouble() * (c.weight == ULTIMATE ? 2.2 : 1.2);
                    FxParticles.add(FxParticles.Tex.SHARD, feet.x + Math.cos(a) * r, feet.y + 0.1, feet.z + Math.sin(a) * r,
                                    0, FxParticles.rf(0.03f, 0.06f), 0)
                            .life(FxParticles.rf(14, 22)).size(FxParticles.rf(0.05f, 0.1f)).color(dust).alpha(1)
                            .fade(0.1f, 0.7f).drag(0.9f).spin(FxParticles.rf(-15, 15));
                }
            }
            case SHADOW -> {
                for (int i = 0, n = FxParticles.count(2); i < n; i++) {
                    Vec3 d = new Vec3(FxParticles.rnd(1), FxParticles.rnd(0.6), FxParticles.rnd(1)).normalize();
                    if (self && d.dot(view) < 0.1) d = d.add(view.scale(1.2)).normalize();
                    double r = reach;
                    FxParticles.add(FxParticles.Tex.SMOKE, at.x + d.x * r, at.y + d.y * r, at.z + d.z * r, -d.x * r / 7, -d.y * r / 7, -d.z * r / 7)
                            .frame(FxParticles.RANDOM.nextInt(16)).blend().life(7).size(0.22f, 0.08f).color(0x14081F)
                            .alpha(0.85f).fade(0.1f, 0.6f).drag(1f).spin(FxParticles.rf(-6, 6));
                }
            }
            case LIGHT -> {
                converge(at, view, self, reach, 2, 7, FxParticles.Tex.HOT, 0.08f, 0xFFF0A8, false);
                if (c.age % 4 == 0) FxParticles.rays(at.x, at.y, at.z, 0.35f + 0.45f * k, 0xFFE27A, 5);
            }
            case NATURE -> converge(at, view, self, reach, 2, 8, FxParticles.Tex.LEAF, 0.07f, 0x8CE05A, false);
            case ARCANE -> FxParticles.add(FxParticles.Tex.RIM, at.x, at.y, at.z, 0, 0, 0).life(6)
                    .size(0.5f + 0.4f * (1 - k), 0.08f).color(0xFFFFFF, 0x7F9CFF).alpha(0.8f).fade(0.2f, 0.5f).spin(15);
            case PHYSICAL -> converge(at, view, self, reach, 2, 6, FxParticles.Tex.SPARK, 0.04f, 0xFFE8B0, true);
        }
    }

    /** An ultimate's caster wrapped in their element: it rises off them as the blow gathers. */
    private static void aura(Charge c, float k) {
        Vec3 p = c.player.position();
        double h = c.player.getBbHeight();
        double w = c.player.getBbWidth() * 0.7;
        int n = 2 + Math.round(2 * k);
        switch (c.element) {
            case FIRE -> FxParticles.fire(p.x, p.y + h * 0.3, p.z, w, n, 0.12f, 0.05f, 0xFFE0A0, 0xFF4A10);
            case SHADOW -> FxParticles.wisps(p.x, p.y + h * 0.4, p.z, w, n, 0x2A1450, 0.3f);
            case LIGHTNING -> FxParticles.sparks(p.x, p.y + h * 0.5, p.z, n, 0.15f, 0xB8E2FF, 0);
            case EARTH -> FxParticles.embers(p.x, p.y + 0.2, p.z, w, n, 0xFFB060);
            default -> FxParticles.motes(p.x, p.y + h * 0.3, p.z, w, n, c.color, 0.05f);
        }
    }

    /** True for the local hunter seen from their own eyes. */
    static boolean selfView(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * Where the blow comes from: the blade's edge in front of the hunter for a cut, the hands for a spell, over the
     * head for what is called down, low before the feet for a blow into the ground. From the hunter's own eyes it sits
     * further out and lower, under the crosshair, where the arms are seen.
     */
    static Vec3 hand(AbstractClientPlayer player, String gesture, boolean self) {
        Vec3 view = player.getViewVector(1);
        Vec3 flat = new Vec3(view.x, 0, view.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 pos = player.position();
        double h = player.getBbHeight();
        if (self) {
            return switch (gesture) {
                case "raise", "channel", "summon", "pray", "bow_sky" -> eye.add(view.scale(1.3)).add(0, 0.25, 0);
                case "slam", "stomp", "ground", "chop" -> eye.add(view.scale(1.2)).add(0, -0.6, 0);
                default -> eye.add(view.scale(1.1)).add(right.scale(0.35)).add(0, -0.4, 0);
            };
        }
        return switch (gesture) {
            case "raise", "channel", "summon", "pray", "bow_sky" -> pos.add(0, h + 0.45, 0).add(flat.scale(0.15));
            case "slam", "stomp", "ground" -> pos.add(flat.scale(0.8)).add(0, 0.35, 0);
            case "chop" -> pos.add(0, h + 0.55, 0).add(flat.scale(0.1));
            case "slash", "slash_back", "claw", "sweep", "spin", "throw_side" ->
                    pos.add(flat.scale(0.45)).add(right.scale(gesture.equals("slash_back") ? -0.75 : 0.75)).add(0, h * 0.62, 0);
            case "lunge" -> pos.add(flat.scale(-0.2)).add(right.scale(0.5)).add(0, h * 0.55, 0);
            case "throw" -> pos.add(flat.scale(-0.25)).add(right.scale(0.35)).add(0, h + 0.25, 0);
            case "bow" -> eye.add(flat.scale(0.75)).add(0, -0.15, 0);
            default -> eye.add(view.scale(0.6)).add(right.scale(0.25)).add(0, -0.45, 0);
        };
    }

    @SubscribeEvent
    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        CHARGES.clear();
    }

    private SkillCharge() {}
}
