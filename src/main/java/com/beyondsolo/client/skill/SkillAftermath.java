package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillFeel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a skill leaves behind. Where a heavy blow lands the ground keeps its mark a while: burnt and smouldering under
 * fire, split under earth, wet and steaming under water, crawling with static after lightning, a pool of ink under
 * shadow, a fading seal of light, a ring of runes, torn turf; and the element goes on rising off it (embers and smoke,
 * settling dust, mist, sparks, wisps, motes, leaves) before it fades. An ultimate's mark is wide and lasts, with a
 * column of its element rising off it and what it threw up still falling. A felled creature breathes its last out as
 * a thin column of the element. The hunter's own hand or blade smokes with the power it let go after a heavy blow.
 *
 * <p>All of it is the client's: {@link SkillImpacts} sends the blows here. Drawn with the {@code combat.hitFeedback}
 * option; turned down with the particle setting like every effect.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillAftermath {
    private static final int MAX_SCARS = 20;

    private static final class Scar {
        final Vec3 at;
        final SkillElement element;
        final int color;
        final boolean ultimate;
        final long seed;
        float radius;
        double start;
        float life;
        /** Seconds the element keeps rising off it. */
        float breath;
        int weight;

        Scar(Vec3 at, SkillElement element, int color, int weight, boolean ultimate, float radius, long seed) {
            this.at = at;
            this.element = element;
            this.color = color;
            this.weight = weight;
            this.ultimate = ultimate;
            this.radius = radius;
            this.seed = seed;
            this.start = Anim.now();
            this.life = ultimate ? 7f : weight == SkillFeel.MASSIVE ? 5f : 3.2f;
            this.breath = ultimate ? 4.5f : weight == SkillFeel.MASSIVE ? 3f : 1.6f;
        }

        float age() {
            return (float) (Anim.now() - start);
        }
    }

    /** A felled creature's last breath, rising off where it fell. */
    private static final class Breath {
        final Vec3 at;
        final int color;
        final double height;
        int ticks;

        Breath(Vec3 at, int color, double height, int ticks) {
            this.at = at;
            this.color = color;
            this.height = height;
            this.ticks = ticks;
        }
    }

    /** A hunter's hand or blade smoking after a heavy blow. */
    private static final class Vent {
        final String gesture;
        final SkillElement element;
        final int color;
        int ticks;

        Vent(String gesture, SkillElement element, int color, int ticks) {
            this.gesture = gesture;
            this.element = element;
            this.color = color;
            this.ticks = ticks;
        }
    }

    private static final List<Scar> SCARS = new ArrayList<>();
    private static final List<Breath> BREATHS = new ArrayList<>();
    private static final Map<AbstractClientPlayer, Vent> VENTS = new WeakHashMap<>();

    // ================================================================ what leaves marks

    /** The heaviest blow of a cast landed on {@code e}: the ground under it keeps the mark. */
    static void blow(ClientLevel level, LivingEntity e, SkillElement element, int color, int weight, boolean kill, boolean ultimate) {
        if (!ClientConfig.HIT_FEEDBACK.get()) return;
        if (kill && weight >= SkillFeel.MEDIUM) {
            BREATHS.add(new Breath(e.position(), element.color(), e.getBbHeight(), 24));
            if (BREATHS.size() > 12) BREATHS.remove(0);
        }
        if (weight < SkillFeel.HEAVY) return;
        Vec3 ground = ground(level, e);
        if (ground == null) return;
        float r = (ultimate ? 3.2f : weight == SkillFeel.MASSIVE ? 2.2f : 1.15f) + e.getBbWidth() * 0.5f;
        // a mark already lying there takes the new blow instead of a second one over it
        for (Scar s : SCARS) {
            if (s.element == element && s.at.distanceToSqr(ground) < (s.radius * 0.7) * (s.radius * 0.7)) {
                s.radius = Math.max(s.radius, r);
                s.weight = Math.max(s.weight, weight);
                s.start = Math.max(s.start, Anim.now() - 0.4);
                return;
            }
        }
        Scar s = new Scar(ground, element, color, weight, ultimate, r, FxParticles.RANDOM.nextLong());
        SCARS.add(s);
        if (SCARS.size() > MAX_SCARS) SCARS.remove(0);
        if (ultimate) debris(level, s);
    }

    /** A hunter let go a heavy blow or an ultimate: the hand or blade it came from smokes a moment. */
    static void vent(AbstractClientPlayer player, String gesture, SkillElement element, int color, int weight) {
        if (weight < SkillCharge.HEAVY) return;
        VENTS.put(player, new Vent(gesture, element, color, weight == SkillCharge.ULTIMATE ? 30 : 16));
    }

    /** The top of the ground under the creature (within a few blocks of its feet), or null over a drop. */
    private static Vec3 ground(ClientLevel level, LivingEntity e) {
        Vec3 from = e.position().add(0, 0.5, 0);
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    // ================================================================ the marks on the ground

    /** Draws every mark lying on the ground. Called once per frame with the skills' world effects. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        if (SCARS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = mc.level.getGameTime() + partial;
        Iterator<Scar> it = SCARS.iterator();
        while (it.hasNext()) {
            Scar s = it.next();
            float age = s.age();
            if (age > s.life) {
                it.remove();
                continue;
            }
            // laid down at once, held, then fading out over the last two fifths of its life
            float in = Mth.clamp(age / 0.12f, 0, 1);
            float hold = in * Mth.clamp((s.life - age) / (s.life * 0.4f), 0, 1);
            float hot = Mth.clamp(1 - age / Math.max(0.5f, s.breath), 0, 1);
            Skins.Look look = Skins.Look.of(s.element.ordinal(), s.color);
            pose.pushPose();
            pose.translate(s.at.x - cam.x, s.at.y - cam.y, s.at.z - cam.z);
            float roll = (s.seed & 1023) * 0.35f;
            switch (s.element) {
                case WATER -> Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), new Vec3(0, 0.02, 0), s.radius * 0.75f,
                        0x5A7894, hold * 0.55f, roll);
                case NATURE -> Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), new Vec3(0, 0.02, 0), s.radius * 0.7f,
                        0x4A6A30, hold * 0.5f, roll);
                case AIR -> Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), new Vec3(0, 0.02, 0), s.radius * 0.6f,
                        0xC8C2B8, hold * 0.3f, roll);
                case SHADOW -> {
                    // a pool of ink, darker than a burn, its rim still lit
                    Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), new Vec3(0, 0.02, 0), s.radius * 0.8f, 0x2A1838, hold, roll);
                    Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), new Vec3(0, 0.025, 0), s.radius * 0.55f, 0x1A0A28, hold, roll + 60);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.04, 0), s.radius * 0.72f, 0.08f,
                            look.light(s.color), hold * 0.5f * (0.4f + 0.6f * hot), 0, Math.max(2, s.radius * 1.5f));
                }
                case LIGHT -> {
                    // a seal of light pressed into the ground, its ring and its glow going out
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.04, 0), s.radius * 0.8f, 0.1f,
                            look.light(s.color), hold * 0.7f, time * 0.01f, Math.max(2, s.radius * 1.5f));
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.045, 0), s.radius * 0.55f, 0.06f,
                            look.light(s.color), hold * 0.5f, -time * 0.015f, Math.max(2, s.radius));
                    Skins.decal(pose, buffers, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, 0.03, 0), s.radius * 0.9f, look.light(s.color),
                            hold * 0.35f * (0.3f + 0.7f * hot), roll);
                }
                case ARCANE -> {
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.04, 0), s.radius * 0.78f, 0.07f,
                            look.light(s.color), hold * 0.65f, time * 0.02f, Math.max(2, s.radius * 2));
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.HEX), new Vec3(0, 0.045, 0), s.radius * 0.62f, 0.22f,
                            look.light(s.color), hold * 0.55f, -time * 0.01f, Math.max(2, s.radius * 3));
                }
                default -> {
                    // burnt: fire, earth, lightning, a plain heavy blow
                    SkinFx.scorch(pose, buffers, Vec3.ZERO, s.radius * 0.8f, hold, s.seed);
                    if (s.element == SkillElement.FIRE || s.element == SkillElement.LIGHTNING) {
                        // the burn still glowing at its heart
                        Skins.decal(pose, buffers, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, 0.03, 0), s.radius * 0.6f, look.light(s.color),
                                hold * 0.45f * hot, roll);
                    }
                }
            }
            // the ground split by what fell on it: earth always, the heaviest blows of anything that burns
            boolean split = s.element == SkillElement.EARTH || (s.weight == SkillFeel.MASSIVE
                    && (s.element == SkillElement.FIRE || s.element == SkillElement.PHYSICAL || s.element == SkillElement.LIGHTNING));
            if (split) {
                float glow = (0.35f + 0.65f * hot) * hold;
                SkinFx.cracks(pose, buffers, camera, s.at, Vec3.ZERO, s.seed, s.radius * 1.05f, s.ultimate ? 8 : 6,
                        SkinFx.easeOut(age / 0.18f), glow, 0.12f * hot, look, s.color, time);
            }
            pose.popPose();
        }
        Skins.flush(buffers);
    }

    // ================================================================ what keeps rising

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        if (!SCARS.isEmpty()) for (Scar s : SCARS) breathe(level, s);
        if (!BREATHS.isEmpty()) {
            Iterator<Breath> it = BREATHS.iterator();
            while (it.hasNext()) {
                Breath b = it.next();
                if (--b.ticks < 0) {
                    it.remove();
                    continue;
                }
                if (b.ticks % 2 == 0) {
                    FxParticles.add(FxParticles.Tex.WISP, b.at.x + FxParticles.rnd(0.15), b.at.y + b.height * 0.3, b.at.z + FxParticles.rnd(0.15),
                                    0, FxParticles.rf(0.05f, 0.08f), 0)
                            .life(FxParticles.rf(16, 24)).size(0.1f, 0.22f).color(0xFFFFFF, b.color).alpha(0.55f * b.ticks / 24f + 0.15f)
                            .fade(0.15f, 0.4f).drag(0.97f).swirl(0.01f).spin(FxParticles.rf(-6, 6));
                }
            }
        }
        if (!VENTS.isEmpty()) {
            for (var entry : new ArrayList<>(VENTS.entrySet())) {
                AbstractClientPlayer player = entry.getKey();
                Vent v = entry.getValue();
                if (--v.ticks < 0 || player.isRemoved()) {
                    VENTS.remove(player);
                    continue;
                }
                if (v.ticks % 2 != 0) continue;
                boolean self = SkillCharge.selfView(player);
                Vec3 at = SkillCharge.hand(player, v.gesture, self);
                if (self) continue;
                switch (v.element) {
                    case FIRE -> FxParticles.smoke(at.x, at.y, at.z, 0.08, 1, 0x3A3030, 0.12f, 0.03f);
                    case SHADOW -> FxParticles.wisps(at.x, at.y, at.z, 0.08, 1, 0x2A1450, 0.2f);
                    case LIGHTNING -> FxParticles.sparks(at.x, at.y, at.z, 1, 0.08f, 0xB8E2FF, 0);
                    default -> FxParticles.add(FxParticles.Tex.WISP, at.x, at.y, at.z, FxParticles.rnd(0.01), 0.025, FxParticles.rnd(0.01))
                            .life(14).size(0.06f, 0.16f).color(0xFFFFFF, v.color).alpha(0.5f).fade(0.1f, 0.4f).drag(0.95f).spin(8);
                }
            }
        }
    }

    /** The element going on rising off a mark while it is fresh, less and less. */
    private static void breathe(ClientLevel level, Scar s) {
        float age = s.age();
        if (age > s.breath) return;
        float k = 1 - age / s.breath;
        int every = s.ultimate ? 1 : s.weight == SkillFeel.MASSIVE ? 2 : 3;
        if (level.getGameTime() % every != 0) return;
        double r = s.radius * 0.6;
        double x = s.at.x, y = s.at.y, z = s.at.z;
        int c = s.color;
        switch (s.element) {
            case FIRE -> {
                FxParticles.embers(x, y + 0.15, z, r, 1 + (s.ultimate ? 1 : 0), 0xFF5A1E);
                if (FxParticles.RANDOM.nextFloat() < 0.5f) FxParticles.smoke(x + FxParticles.rnd(r * 0.6), y + 0.2, z + FxParticles.rnd(r * 0.6),
                        0.2, 1, 0x2A2420, 0.22f + 0.2f * k, 0.03f);
                if (k > 0.6f) FxParticles.fire(x + FxParticles.rnd(r * 0.5), y + 0.1, z + FxParticles.rnd(r * 0.5), 0.1, 1, 0.14f * k, 0.03f,
                        0xFFE0A0, 0xFF4A10);
            }
            case EARTH, PHYSICAL -> {
                if (FxParticles.RANDOM.nextFloat() < 0.6f) {
                    int dust = FxParticles.groundColor(level, x, y + 0.2, z);
                    double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2, d = FxParticles.RANDOM.nextDouble() * s.radius;
                    FxParticles.add(FxParticles.Tex.SMOKE, x + Math.cos(a) * d, y + 0.15, z + Math.sin(a) * d, Math.cos(a) * 0.01, 0.006, Math.sin(a) * 0.01)
                            .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(30, 50)).size(0.25f, 0.55f + 0.3f * k).color(dust)
                            .alpha(0.3f * k + 0.1f).fade(0.2f, 0.4f).drag(0.95f).spin(FxParticles.rf(-1, 1));
                }
            }
            case WATER -> {
                // steam off the wet ground, and what was thrown up dripping back
                FxParticles.add(FxParticles.Tex.SMOKE, x + FxParticles.rnd(r), y + 0.1, z + FxParticles.rnd(r), 0, FxParticles.rf(0.015f, 0.03f), 0)
                        .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(24, 40)).size(0.15f, 0.45f).color(0xE8F4FF)
                        .alpha(0.22f * k + 0.05f).fade(0.2f, 0.4f).drag(0.96f).swirl(0.006f);
                if (k > 0.5f && FxParticles.RANDOM.nextFloat() < 0.5f) FxParticles.add(FxParticles.Tex.DROP, x + FxParticles.rnd(r), y + 1.6, z + FxParticles.rnd(r), 0, -0.05, 0)
                        .life(14).size(0.045f).color(0xFFFFFF, 0x4FA8FF).alpha(0.9f).fade(0, 0.8f).gravity(0.04f).stretch(1.6f).collide(0);
            }
            case LIGHTNING -> {
                // static crawling over the burn
                double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2, d = FxParticles.RANDOM.nextDouble() * s.radius * 0.8;
                double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
                for (int i = 0; i < 2; i++) {
                    double b = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                    FxParticles.add(FxParticles.Tex.SPARK, px, y + 0.06, pz, Math.cos(b) * 0.12, 0.02, Math.sin(b) * 0.12)
                            .life(3).size(0.035f).color(0xFFFFFF, 0x9CD8FF).alpha(1).fade(0, 0.5f).drag(0.7f).stretch(3f);
                }
                if (FxParticles.RANDOM.nextFloat() < 0.15f * k + 0.05f) FxParticles.flash(px, y + 0.1, pz, 0.3f, 0xB8E2FF);
            }
            case AIR -> {
                // the dust still turning where the wind struck
                int dust = FxParticles.groundColor(level, x, y + 0.2, z);
                double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2, d = s.radius * 0.7;
                FxParticles.add(FxParticles.Tex.SMOKE, x + Math.cos(a) * d, y + 0.15, z + Math.sin(a) * d,
                                -Math.sin(a) * 0.09, 0.012, Math.cos(a) * 0.09)
                        .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(18, 26)).size(0.18f, 0.35f).color(dust)
                        .alpha(0.3f * k + 0.08f).fade(0.15f, 0.4f).drag(0.98f).swirl(0.03f);
            }
            case SHADOW -> {
                FxParticles.add(FxParticles.Tex.SMOKE, x + FxParticles.rnd(r), y + 0.1, z + FxParticles.rnd(r), 0, FxParticles.rf(0.02f, 0.04f), 0)
                        .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(20, 34)).size(0.12f, 0.38f).color(0x14081F)
                        .alpha(0.6f * k + 0.15f).fade(0.15f, 0.4f).drag(0.95f).swirl(0.01f).spin(FxParticles.rf(-4, 4));
                if (FxParticles.RANDOM.nextFloat() < 0.3f) FxParticles.motes(x, y + 0.1, z, r, 1, 0x9B7BE0, 0.03f);
            }
            case LIGHT -> FxParticles.motes(x, y + 0.1, z, r, 1 + (s.ultimate ? 1 : 0), 0xFFE27A, 0.04f);
            case NATURE -> {
                if (FxParticles.RANDOM.nextFloat() < 0.5f) FxParticles.flutter(FxParticles.Tex.LEAF, x, y + 1.2, z, r, 1, 0.03f, 0x8CE05A, 0.07f);
                FxParticles.motes(x, y + 0.1, z, r, 1, 0xB8FF7A, 0.03f);
            }
            case ARCANE -> {
                FxParticles.motes(x, y + 0.1, z, r, 1, 0x7F9CFF, 0.05f);
                if (FxParticles.RANDOM.nextFloat() < 0.12f) FxParticles.add(FxParticles.Tex.RIM, x, y + 0.08, z, 0, 0, 0).flat().life(12)
                        .size(s.radius * 0.3f, s.radius * 0.9f).color(0xFFFFFF, 0x7F9CFF).alpha(0.5f * k).fade(0, 0.3f).spin(4);
            }
        }
        if (s.ultimate && age < s.breath * 0.8f) {
            // a column of the element standing up off an ultimate's mark
            if (level.getGameTime() % 2 == 0) {
                int tint = switch (s.element) {
                    case FIRE, EARTH, PHYSICAL, LIGHTNING -> 0x2A2420;
                    case SHADOW -> 0x14081F;
                    case WATER -> 0xE8F4FF;
                    default -> SkillWorldFx.mix(c, 0xFFFFFF, 0.6f);
                };
                FxParticles.add(FxParticles.Tex.SMOKE, x + FxParticles.rnd(0.5), y + 0.4, z + FxParticles.rnd(0.5), FxParticles.rnd(0.01),
                                FxParticles.rf(0.06f, 0.1f), FxParticles.rnd(0.01))
                        .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(40, 60)).size(0.4f, 1.3f).color(tint)
                        .alpha(0.35f * k + 0.1f).fade(0.15f, 0.35f).drag(0.97f).swirl(0.008f).spin(FxParticles.rf(-1, 1));
            }
        }
    }

    /** What an ultimate threw up into the air, coming back down over its mark. */
    private static void debris(ClientLevel level, Scar s) {
        int dust = FxParticles.groundColor(level, s.at.x, s.at.y + 0.2, s.at.z);
        int n = FxParticles.count(14);
        for (int i = 0; i < n; i++) {
            double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2, d = FxParticles.RANDOM.nextDouble() * s.radius * 1.2;
            FxParticles.add(FxParticles.Tex.SHARD, s.at.x + Math.cos(a) * d, s.at.y + FxParticles.rf(3.5f, 6f), s.at.z + Math.sin(a) * d,
                            0, FxParticles.rf(-0.05f, 0.05f), 0)
                    .delay(FxParticles.rf(4, 18)).life(FxParticles.rf(26, 36)).size(FxParticles.rf(0.06f, 0.13f))
                    .color(s.element == SkillElement.EARTH || s.element == SkillElement.PHYSICAL ? dust : SkillWorldFx.mix(dust, s.color, 0.3f))
                    .alpha(1).fade(0, 0.7f).gravity(0.035f).drag(0.98f).spin(FxParticles.rf(-20, 20)).collide(0.2f);
        }
        if (s.element == SkillElement.FIRE || s.element == SkillElement.SHADOW) {
            // ash, falling slower
            for (int i = 0; i < n / 2; i++) {
                double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2, d = FxParticles.RANDOM.nextDouble() * s.radius * 1.5;
                FxParticles.add(FxParticles.Tex.EMBER, s.at.x + Math.cos(a) * d, s.at.y + FxParticles.rf(2.5f, 4.5f), s.at.z + Math.sin(a) * d, 0, 0, 0)
                        .delay(FxParticles.rf(6, 30)).life(FxParticles.rf(40, 60)).size(FxParticles.rf(0.03f, 0.05f))
                        .color(s.element == SkillElement.FIRE ? 0xFFB070 : 0x3A2A50).alpha(0.9f).fade(0.1f, 0.7f).gravity(0.004f)
                        .drag(0.96f).swirl(0.02f).flicker(0.3f);
            }
        }
    }

    @SubscribeEvent
    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        SCARS.clear();
        BREATHS.clear();
        VENTS.clear();
    }

    private SkillAftermath() {}
}
