package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.fx.Outline;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * A world boss, a monarch or a ruler of light: its GeckoLib model with its glowing parts, and its power drawn the way
 * the series draws it ({@link Aura}, and docs/ARCHITECTURE.md, "Series look").
 * <ul>
 *   <li>A <b>monarch</b> burns: black flame off its outline over a rim of its colour, ink torn off its shoulders, a
 *   pool of ink under its feet once it has landed. In its last phase the flame stands taller with a crown of spikes on
 *   its head; as it dies, black flame takes it and its ink is thrown out over the land.</li>
 *   <li>A <b>world boss</b> shows its element only in its own seams until its last phase opens them: its edges and seams
 *   burn, the flame giant's magma breaks out of it in tongues of fire under black smoke. As it dies they go dark.</li>
 *   <li>A <b>ruler</b> wears a halo of light, walks on a sigil of light, and a pillar of light takes it as it dies.</li>
 * </ul>
 * The breath of each round it (mist, sparks, embers, smoke) is {@link WorldBossWorldFx#breathe}'s.
 */
public class ColossalBossRenderer<T extends ColossalBoss> extends GeoEntityRenderer<T> {
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");

    private final WorldBossKind kind;
    /** Its own outline: a shadow's stand-in may be drawing this very model into the shared one. */
    private final Outline outline = Outline.create();
    /** Its aura in phases I and II, in its last phase, and as it dies (null: none then). */
    @Nullable private final Aura.Style calm, wrath, death;

    public ColossalBossRenderer(EntityRendererProvider.Context context, WorldBossKind kind) {
        super(context, new WorldBossModel<>(kind));
        this.kind = kind;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = switch (kind) {
            case TITAN -> 3.2f;
            case BEHEMOTH -> 2.8f;
            default -> 0.9f;
        };
        this.shadowStrength = 0.8f;
        this.calm = calm(kind);
        this.wrath = wrath(kind);
        this.death = wrath == null ? null : kind.monarch() ? wrath.copy().flame(wrath.flameHeight() * 1.7f).crown(1f).wisps(5f, 1.3f) : wrath;
    }

    /** A monarch's aura while it fights (null: none; a world boss's power shows only in its seams until its last phase). */
    @Nullable
    private static Aura.Style calm(WorldBossKind k) {
        int c = k.color;
        return switch (k) {
            // the frost queen: black flame edged in frost, over the cold mist she breathes
            case STILLNESS -> new Aura.Style().hue(c).rim(0.45f, 0.06f).seams(0.25f).flame(0.3f).crest(1.6f).glow(0.4f).lean(0.5f).wisps(0.6f, 0.7f);
            // the wolf king: black flame on a rim of blood, ink torn off his shoulders
            case HUNGER -> new Aura.Style().hue(c).rim(0.5f, 0.07f).seams(0.25f).flame(0.32f).crest(1.6f).glow(0.5f).wisps(1.4f, 0.8f);
            // the storm lord: black flame, the light in his seams crackling
            case STORMS -> new Aura.Style().hue(c).rim(0.5f, 0.065f).seams(0.35f).flame(0.3f).crest(1.5f).glow(0.45f).wisps(0.8f, 0.7f);
            default -> null;
        };
    }

    /** Its aura in its last phase: a monarch's erupts, a world boss's seams open (null: none). */
    @Nullable
    private static Aura.Style wrath(WorldBossKind k) {
        int c = k.color;
        return switch (k) {
            case STILLNESS -> new Aura.Style().hue(c).rim(0.75f, 0.075f).seams(0.4f).flame(0.55f).crest(1.9f).crown(0.7f).glow(0.6f).lean(0.5f)
                    .wisps(1.6f, 0.9f);
            case HUNGER -> new Aura.Style().hue(c).rim(0.85f, 0.085f).seams(0.4f).flame(0.62f).crest(1.9f).crown(0.9f).glow(0.75f).wisps(3f, 1f);
            case STORMS -> new Aura.Style().hue(c).rim(0.9f, 0.08f).seams(0.75f).flame(0.55f).crest(1.8f).crown(0.6f).glow(0.6f).wisps(1.6f, 0.9f);
            // the mountain's magma breaks out of it: tongues of fire off its ridge, black smoke torn off them
            case TITAN -> new Aura.Style().hue(0xFF5A1E).light().rim(0.6f, 0.05f).seams(0.5f).flame(0.28f).crest(1.9f).glow(0.8f).lean(0.4f)
                    .wisps(2.2f, 1.2f);
            // the beast's wounds open: its edges raw red, dark blood smoking off its back
            case BEHEMOTH -> new Aura.Style().hue(c).rim(0.8f, 0.06f).seams(0.6f).flame(0.15f).crest(1.4f).glow(0.55f).wisps(1.6f, 1f);
            // the hive's royalty: venom along their edges and in their seams
            case QUEEN, KING -> new Aura.Style().hue(c).rim(0.6f, 0.05f).seams(0.45f).flame(0f);
            // a ruler's light is drawn below
            default -> null;
        };
    }

    @Override
    protected float getDeathMaxRotation(T boss) {
        // each falls with its own death animation
        return 0f;
    }

    @Override
    public int getPackedOverlay(T boss, float u, float partialTick) {
        // a great threat doesn't flush red under every blow: with a raid's hunters on it, it never would stop, and it
        // falls with its own death animation
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(false));
    }

    @Override
    public void render(T boss, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // only the boss itself burns: a shadow's stand-in drawn with this renderer is ink and nothing more
        boolean real = boss.level().getEntity(boss.getId()) == boss;
        float dying = boss.deathTime > 0 ? Mth.clamp((boss.deathTime + partial) / 90f, 0f, 1f) : 0f;
        boolean last = boss.phase() >= 3;
        Aura.Style style = !real || boss.isInvisible() || !boss.isAwake() ? null : dying > 0 ? death : last ? wrath : calm;
        MultiBufferSource drawn = buffers;
        if (style != null) {
            // the outline is read off its body as it is drawn (not off the glowing copy its glow layer draws over it)
            RenderType body = getRenderType(boss, getTextureLocation(boss), buffers, partial);
            drawn = outline.reset().tap(buffers, type -> type == body);
        }
        // climbing out of the earth or sinking back into it: drawn sunk into the ground, which hides what is below
        float buried = boss.buried(partial);
        if (buried > 0) {
            pose.pushPose();
            pose.translate(0, -buried, 0);
        }
        super.render(boss, yaw, partial, pose, drawn, light);
        if (buried > 0) pose.popPose();
        if (!real) return;
        float time = (boss.tickCount + partial) % 7200;
        int rgb = boss.fxColor();
        float h = boss.getBbHeight(), w = boss.getBbWidth();

        if (style != null && outline.quads() > 0) {
            // a monarch: black flame takes it as it dies; a world boss's seams go dark
            float alpha = dying <= 0 ? 1f : kind.monarch() ? Math.min(1f, dying * 4f) * (1 - smooth((dying - 0.7f) / 0.3f)) : 1 - smooth(dying);
            Aura.burnWorld(outline, buffers, style, boss, partial, time, alpha, Math.max(0.6f, h / 1.9f), light);
        }

        if (kind.monarch() && boss.isAwake()) {
            // its own pool of ink under its feet once it has landed, the heart of its domain
            float landed = boss.introTicks() > 0 ? Mth.clamp((31 - boss.introTicks()) / 10f, 0, 1) : 1;
            float a = landed * (boss.leaving() ? 0 : 1) * (1 - smooth((dying - 0.75f) / 0.25f));
            Aura.pool(pose, buffers, 0.05f, (last ? 3f : 2.4f) + 1.2f * dying, rgb, 0.9f * a, time, last || dying > 0 ? 1f : 0.55f);
            if (dying > 0 && dying < 0.4f) {
                // its ink thrown out over the land as it falls
                float k = dying / 0.4f;
                Aura.splash(pose, buffers, 0.07f, 1.5f + 7f * (1 - (1 - k) * (1 - k) * (1 - k)), rgb, 0.9f * (1 - k * k), boss.getId() * 37f);
            }
        } else if (kind.ruler() && boss.isAwake()) {
            ruler(boss, pose, buffers, rgb, time, h, dying, last);
        }

        if (dying > 0 && kind.ruler()) {
            // light gathers on it, and a pillar of light takes it
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            float gather = Mth.clamp(dying * 1.6f, 0f, 1f);
            float pillar = Mth.clamp((dying - 0.5f) / 0.5f, 0f, 1f);
            int hot = GateFx.mix(rgb, 0xFFFFFF, 0.55f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.45f, 0, w * 0.6f + h * 0.4f * gather, hot, 0.6f * gather);
            for (int i = 0; i < 24; i++) {
                float u = (time * 0.035f + i / 24f) % 1f;
                float far = 1 - u * u;
                float ang = i * 2.4f + u * 2f;
                float rad = (w * 1.2f + 2f) * far;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(ang) * rad, h * (0.15f + 0.8f * (i % 5) / 5f) * far + h * 0.3f * u,
                        Mth.sin(ang) * rad, 0.12f + 0.02f * h, hot, gather * u);
            }
            if (pillar > 0) {
                VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), w * 0.2f + w * 0.5f * pillar, h * 6f, 0xFFFFFF, Math.min(1f, pillar * 1.4f));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), w * 0.5f + w * 0.9f * pillar, h * 6f, rgb, 0.6f * pillar);
            }
        }
    }

    /**
     * A ruler of light: a halo over its head (a second, fainter, in its last phase), the sigil it walks on once it has
     * landed, a haze of its light round it, and a trail of light above it as it comes down.
     */
    private void ruler(T boss, PoseStack pose, MultiBufferSource buffers, int rgb, float time, float h, float dying, boolean last) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float landed = boss.introTicks() > 0 ? Mth.clamp((31 - boss.introTicks()) / 10f, 0, 1) : 1;
        float a = landed * (1 - dying) * (boss.leaving() ? 0 : 1);
        if (a > 0.01f) {
            float hy = h + 0.35f + 0.08f * Mth.sin(time * 0.07f);
            int white = GateFx.mix(rgb, 0xFFFFFF, 0.6f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, hy, 0.75f, 0, white, 0.9f * a);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, hy - 0.02f, 1.3f, 0, rgb, 0.4f * a);
            if (last) GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, hy + 0.25f, 1.1f, 0, rgb, 0.45f * a);
            float r = 2.6f + (last ? 0.6f : 0);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.06f, r, time * (last ? 2.6f : 1.2f), rgb, 0.6f * a);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.07f, r * 0.6f, -time * 2f, GateFx.mix(rgb, 0xFFFFFF, 0.4f), 0.4f * a);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.08f, r * 1.5f, 0, rgb, 0.35f * a);
        }
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.12f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.55f, 0, h * (last ? 1.0f : 0.8f),
                rgb, (last ? 0.2f : 0.12f) * pulse * (1 - dying));
        if (boss.introTicks() > 31) {
            VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
            GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), 1.2f, MonarchBoss.GATE_HEIGHT * 0.5f, rgb, 0.4f);
        }
    }

    /** 0 below 0, 1 above 1, smooth between. */
    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    @Override
    public boolean shouldShowName(T boss) {
        return false;
    }
}
