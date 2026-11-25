package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.skill.ArtFx;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Locale;

/**
 * The weapon arts. The world side (every art's own wind-up, strike and beats) is {@link ArtFx}; this is the wielder's
 * own screen, drawn the way the series frames a big move:
 *
 * <ul>
 *   <li>Divine arts: black bars close in from the top and bottom for the wind-up, the art's colour gathers at the
 *   edges and the view draws in; the strike is an ink frame (a few frames of black with white rays) and a flash, the
 *   view punching out; the weapon's name and the art's are stamped across the screen.</li>
 *   <li>Mythic arts: the colour at the edges, a white flash frame at the strike, a band with the art's name.</li>
 *   <li>Legendary arts: a small stamp of the art's name low on the screen, a short punch of the view.</li>
 * </ul>
 *
 * <p>Each art also tints the screen its own way (frost creeping in, a storm darkening, a hot band sweeping down with a
 * cleave). A bow's drawn art shows the star or the gale gathering where the arrow points, and how long it still has
 * to wait. The banners show always; everything else follows the screen effects setting.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SignatureFx {
    /** The local player's art on screen. Times are real time ({@link Anim#now}). */
    private static final class Show {
        final Signature sig;
        final double wind;
        final String weapon;
        /** Whether the name was announced (a beat on its own, long after the cast, shows no banner). */
        final boolean banner;
        double strike = -10, beat = -10;
        int beats;
        final float turn = (float) Math.random();

        Show(Signature sig, double wind, String weapon, boolean banner) {
            this.sig = sig;
            this.wind = wind;
            this.weapon = weapon;
            this.banner = banner;
        }

        float sinceWind() {
            return Anim.since(wind);
        }

        float sinceStrike() {
            return strike < wind - 0.001 ? -1 : Anim.since(strike);
        }

        float sinceBeat() {
            return beat < wind - 0.001 ? -1 : Anim.since(beat);
        }

        float windup() {
            return sig.drawn() ? 0 : sig.impactTicks() / 20f;
        }
    }

    private static Show show;

    // ------------------------------------------------------------------ events

    public static void play(SignatureFxPayload p) {
        ArtFx.play(p);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p.entityId() != mc.player.getId()) return;
        Signature sig = p.signature();
        int phase = p.phase();
        double now = Anim.now();
        if (phase == SignatureFxPayload.PASSIVE || phase == SignatureFxPayload.READY) return;
        if (phase == SignatureFxPayload.WINDUP) {
            show = new Show(sig, now, weaponName(mc.player, sig), true);
            return;
        }
        int n = SignatureFxPayload.beatOf(phase);
        if (n == 0) {
            if (show == null || show.sig != sig || show.strike >= show.wind) {
                show = new Show(sig, now - sig.impactTicks() / 20.0, weaponName(mc.player, sig), true);
            }
            show.strike = now;
            return;
        }
        if (show == null || show.sig != sig) {
            show = new Show(sig, now - 1, "", false);
            show.strike = now - 1;
        }
        show.beat = now;
        show.beats = n;
    }

    private static String weaponName(Player p, Signature sig) {
        ItemStack stack = sig.drawn() && p.isUsingItem() ? p.getUseItem() : p.getMainHandItem();
        return stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    public static void tick() {
        ArtFx.tick();
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        ArtFx.render(pose, camera, partial);
    }

    public static void clear() {
        ArtFx.clear();
        show = null;
    }

    /** Whether the art on screen has played out. */
    private static boolean over(Show s) {
        float t = s.sinceWind(), k = s.sinceStrike(), b = s.sinceBeat();
        if (k < 0) {
            // a wind-up with no strike: an art cut short, or a bow held at full draw
            if (s.sig.drawn()) return !(Minecraft.getInstance().player != null && ArtFx.gathering(Minecraft.getInstance().player) >= 1) && t > 0.5f;
            return t > s.windup() + 1.5f;
        }
        return k > 3.5f && (b < 0 || b > 2.5f) && !waitingForBeat(s, k);
    }

    /** Arts whose later beat comes long after the strike keep their show until it does. */
    private static boolean waitingForBeat(Show s, float k) {
        return switch (s.sig) {
            case COLOSSUS_STANCE -> s.beats == 0 && k < 9f;
            case STARFALL -> s.beats < 2 && k < 7f;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ the view

    @SubscribeEvent
    static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || show == null || !ClientConfig.SCREEN_EFFECTS.get()) return;
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        if (scale <= 0) return;
        float m = fovFactor(show);
        if (Math.abs(m - 1) > 1.0e-4f) event.setFOV(event.getFOV() * Mth.lerp(scale, 1f, m));
    }

    /** The view drawing in through the wind-up, punching out at the strike and at a big beat. */
    private static float fovFactor(Show s) {
        ItemGrade grade = s.sig.grade();
        float pull = grade == ItemGrade.DIVINE ? 0.075f : grade == ItemGrade.MYTHIC ? 0.045f : 0.02f;
        float punch = grade == ItemGrade.DIVINE ? 0.08f : grade == ItemGrade.MYTHIC ? 0.05f : 0.028f;
        float t = s.sinceWind(), k = s.sinceStrike(), b = s.sinceBeat();
        float m = 1;
        if (k < 0 && !s.sig.drawn() && s.windup() > 0) m -= pull * Anim.outCubic(Anim.clamp01(t / s.windup()));
        if (k >= 0 && k < 0.45f) m += punch * (1 - Anim.outCubic(Anim.clamp01(k / 0.4f)));
        if (b >= 0 && b < 0.4f && grade != ItemGrade.LEGENDARY) m += punch * 0.6f * (1 - Anim.outCubic(Anim.clamp01(b / 0.35f)));
        return m;
    }

    // ------------------------------------------------------------------ the screen

    /** Drawn in the combat HUD layer. */
    public static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean effects = ClientConfig.SCREEN_EFFECTS.get();
        drawnArt(g, mc, w, h, effects);
        Show s = show;
        if (s == null) return;
        if (over(s)) {
            show = null;
            return;
        }
        ItemGrade grade = s.sig.grade();
        if (effects) {
            tint(g, w, h, s);
            if (grade == ItemGrade.DIVINE) bars(g, w, h, s);
            frames(g, w, h, s);
        }
        if (!s.banner) return;
        float bt = s.sig.drawn() ? s.sinceStrike() : s.sinceWind();
        if (bt < 0) return;
        switch (grade) {
            case DIVINE -> divineBanner(g, w, h, s, bt);
            case MYTHIC -> mythicBanner(g, w, h, s, bt);
            default -> legendaryStamp(g, w, h, s, bt);
        }
    }

    private static int opaque(Signature sig) {
        return 0xFF000000 | sig.color();
    }

    /** Arts of light: their impact frame is white, never ink. */
    private static boolean light(Signature sig) {
        return switch (sig) {
            case COLOSSUS_STANCE, VERDANT_BLESSING, STARFALL, SAINTS_DAWN, SUNDERING_VERDICT, CONSTELLATION -> true;
            default -> false;
        };
    }

    /** Black bars closing in from the top and bottom: for the wind-up and strike, and again for a big beat. */
    private static void bars(GuiGraphics g, int w, int h, Show s) {
        float k = s.sinceStrike(), b = s.sinceBeat();
        float start = s.sig.drawn() ? k : s.sinceWind();
        float main = 0;
        if (start >= 0) {
            float in = Anim.outCubic(Anim.clamp01(start / (s.sig.drawn() ? 0.15f : 0.3f)));
            float hold = k < 0 ? 99 : (s.sig.drawn() ? 0 : s.windup()) + 1.3f;
            main = in * (1 - Anim.window(start, hold, hold + 0.5f));
        }
        float beat = 0;
        if (b >= 0 && (k < 0 || Anim.since(s.beat) < 1.6f) && (s.sig == Signature.COLOSSUS_STANCE || s.sig == Signature.STARFALL || k > 2f)) {
            beat = Anim.outCubic(Anim.clamp01(b / 0.15f)) * (1 - Anim.window(b, 1f, 1.5f));
        }
        float a = Math.max(main, beat);
        if (a <= 0.01f) return;
        float bh = h * 0.085f * a;
        Shapes.box(0, 0, w, bh).fill(0xF2020206).draw(g);
        Shapes.box(0, h - bh, w, bh).fill(0xF2020206).draw(g);
        // a hairline of the art's light along their inner edges
        int c = Gfx.mix(opaque(s.sig), 0xFFFFFFFF, 0.4f);
        Shapes.box(0, bh - 1, w, 1).fillH(0x00000000, Gfx.alpha(c, 0.7f * a)).glow(2, Gfx.alpha(c, 0.35f * a)).draw(g);
        Shapes.box(0, h - bh, w, 1).fillH(Gfx.alpha(c, 0.7f * a), 0x00000000).glow(2, Gfx.alpha(c, 0.35f * a)).draw(g);
    }

    /** The strike's frame: ink (or white, for the arts of light) for a few frames with rays, then a flash. */
    private static void frames(GuiGraphics g, int w, int h, Show s) {
        ItemGrade grade = s.sig.grade();
        if (grade == ItemGrade.LEGENDARY) return;
        float k = s.sinceStrike(), b = s.sinceBeat();
        boolean divine = grade == ItemGrade.DIVINE;
        if (k >= 0) impactFrame(g, w, h, s, k, divine, s.turn);
        if (b >= 0 && divine) impactFrame(g, w, h, s, b, true, s.turn + 0.37f);
    }

    private static void impactFrame(GuiGraphics g, int w, int h, Show s, float age, boolean ink, float turn) {
        float hold = 0.07f;
        boolean bright = light(s.sig);
        if (ink && age < hold) {
            int ground = bright ? 0xEEFFF8EC : 0xE8040208;
            int rays = bright ? Gfx.mix(opaque(s.sig), 0xFF7A5A20, 0.4f) : 0xFFFFFFFF;
            Shapes.box(0, 0, w, h).fill(ground).draw(g);
            Shapes.rays(w / 2f, h / 2f, Math.max(w, h) * 0.85f, 46).rotation(turn).fill(Gfx.alpha(rays, bright ? 0.5f : 0.6f)).draw(g);
            return;
        }
        float f = 1 - Anim.clamp01((age - (ink ? hold : 0)) / (ink ? 0.3f : 0.2f));
        if (f <= 0) return;
        int flash = Gfx.mix(0xFFFFFFFF, opaque(s.sig), 0.25f);
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(flash, (ink ? 0.55f : 0.45f) * f * f)).additive().draw(g);
    }

    /** Each art's own colour on the screen: frost creeping in, a storm darkening, a hot band sweeping down. */
    private static void tint(GuiGraphics g, int w, int h, Show s) {
        float t = s.sinceWind(), k = s.sinceStrike(), b = s.sinceBeat();
        int c = opaque(s.sig);
        ItemGrade grade = s.sig.grade();
        // the art's colour gathering at the edges through the wind-up (divine and mythic)
        if (grade != ItemGrade.LEGENDARY && !s.sig.drawn()) {
            float in = k < 0 ? Anim.clamp01(t / Math.max(0.2f, s.windup())) : 1 - Anim.clamp01(k / 0.7f);
            if (in > 0.01f) Shapes.vignette(0, 0, w, h, 0.5f, 1.2f).fill(Gfx.alpha(c, (grade == ItemGrade.DIVINE ? 0.42f : 0.32f) * in)).draw(g);
        }
        if (k < 0) {
            if (s.sig == Signature.HEAVENS_VERDICT) {
                // the storm gathering darkens the sky
                Shapes.box(0, 0, w, h * 0.5f).fillV(Gfx.alpha(0xFF0A0C18, 0.45f * Anim.clamp01(t / 0.6f)), 0x00000000).draw(g);
            }
            if (s.sig == Signature.DRAGONS_WRATH) {
                Shapes.vignette(0, 0, w, h, 0.35f, 1.05f).fill(Gfx.alpha(0xFF14030A, 0.6f * Anim.clamp01(t / 0.25f))).draw(g);
            }
            return;
        }
        switch (s.sig) {
            case INFERNAL_REND -> {
                float f = 1 - Anim.clamp01(k / 1.2f);
                float flicker = 0.8f + 0.2f * Mth.sin(k * 37f);
                if (f > 0) Shapes.vignette(0, 0, w, h, 0.35f, 1.15f).fill(Gfx.alpha(0xFFFF4A10, 0.6f * f * flicker)).draw(g);
            }
            case ABSOLUTE_ZERO -> {
                float in = Anim.outCubic(Anim.clamp01(k / 0.3f));
                float f = 1 - Anim.clamp01((k - 1.4f) / 1f);
                if (f > 0) {
                    Shapes.vignette(0, 0, w, h, 0.8f - 0.4f * in, 1.1f).fill(Gfx.alpha(0xFFE6F7FF, 0.65f * f)).draw(g);
                    Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF9ED8FF, 0.1f * f)).draw(g);
                }
                if (b >= 0 && b < 0.3f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.35f * (1 - b / 0.3f))).additive().draw(g);
            }
            case HEAVENS_VERDICT -> {
                float f = 1 - Anim.clamp01(k / 1.2f);
                if (f > 0) Shapes.box(0, 0, w, h * 0.5f).fillV(Gfx.alpha(0xFF0A0C18, 0.45f * f), 0x00000000).draw(g);
                if (b >= 0 && b < 0.35f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFD8E8FF, 0.5f * (1 - b / 0.35f))).additive().draw(g);
            }
            case CATACLYSM_CLEAVE -> {
                // a hot band sweeps down the screen with the cleave
                float x = Anim.outCubic(Anim.clamp01(k / 0.22f));
                float band = h * 0.45f;
                float f = 1 - Anim.clamp01(k / 0.6f);
                if (f > 0) Shapes.box(0, -band + x * (h + band), w, band).fillV(0x00000000, Gfx.alpha(0xFFFFB050, 0.4f * f)).additive().draw(g);
                if (b >= 0 && b < 0.9f) {
                    Shapes.box(0, h * 0.55f, w, h * 0.45f).fillV(0x00000000, Gfx.alpha(0xFFFF6A20, 0.35f * (1 - b / 0.9f))).additive().draw(g);
                }
            }
            case DRAGONS_WRATH -> {
                float f = 1 - Anim.clamp01(k / 0.4f);
                if (f > 0) {
                    Shapes.rays(w / 2f, h / 2f, Math.max(w, h) * 0.75f, 40).rotation(s.turn).fill(Gfx.alpha(0xFFFF6A9A, 0.4f * f)).additive().draw(g);
                }
                if (b >= 0 && b < 0.6f) Shapes.vignette(0, 0, w, h, 0.3f, 1.1f).fill(Gfx.alpha(0xFF8A0A2A, 0.6f * (1 - b / 0.6f))).draw(g);
            }
            case DEVOURING_STAR -> {
                // the edges of sight drawn in while the singularity feeds, then a ring of light at the collapse
                float feed = b >= 0 ? 0 : Anim.clamp01((k - 0.15f) / 0.3f);
                if (feed > 0) Shapes.vignette(0, 0, w, h, 0.45f, 1.1f).fill(Gfx.alpha(0xFF07030F, 0.55f * feed)).draw(g);
                if (b >= 0 && b < 0.7f) {
                    float e = Anim.outCubic(b / 0.6f);
                    Shapes.ring(w / 2f, h / 2f, 10 + e * Math.max(w, h) * 0.5f, 2 + 6 * (1 - e)).fill(Gfx.alpha(0xFFF0E8FF, 0.8f * (1 - b / 0.7f)))
                            .glow(10, Gfx.alpha(c, 0.5f * (1 - b / 0.7f))).additive().draw(g);
                }
            }
            case COLOSSUS_STANCE -> {
                float f = 1 - Anim.clamp01(k / 1.2f);
                if (f > 0) Shapes.box(0, h * 0.6f, w, h * 0.4f).fillV(0x00000000, Gfx.alpha(0xFFFFE0A0, 0.35f * f)).additive().draw(g);
                // the bulwark's light at the edge of sight while it stands
                float hold = s.beats == 0 ? Anim.clamp01((k - 0.3f) / 0.4f) * (1 - Anim.clamp01((k - 7.8f) / 0.4f)) : 0;
                if (hold > 0) {
                    float breath = 0.75f + 0.25f * Anim.pulse(1.8f);
                    Shapes.vignette(0, 0, w, h, 0.62f, 1.35f).fill(Gfx.alpha(c, 0.16f * hold * breath)).additive().draw(g);
                }
            }
            case VERDANT_BLESSING -> {
                float f = Math.min(Anim.clamp01(k / 0.4f), 1 - Anim.clamp01((k - 0.8f) / 1.6f));
                if (f > 0) Shapes.box(0, h * 0.4f, w, h * 0.6f).fillV(0x00000000, Gfx.alpha(0xFFB8F08A, 0.32f * f)).additive().draw(g);
            }
            case STARFALL -> {
                if (s.beats >= 2 && b >= 0 && b < 0.9f) {
                    Shapes.rays(w / 2f, -h * 0.2f, Math.max(w, h) * 1.1f, 30).rotation(s.turn).fill(Gfx.alpha(0xFFFFF0A8, 0.45f * (1 - b / 0.9f)))
                            .additive().draw(g);
                }
            }
            default -> {
                // a legendary art: a short edge of its colour at the strike
                float f = 1 - Anim.clamp01(k / 0.5f);
                if (f > 0) Shapes.vignette(0, 0, w, h, 0.55f, 1.2f).fill(Gfx.alpha(c, 0.28f * f)).draw(g);
            }
        }
    }

    // ------------------------------------------------------------------ the names

    private static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /** A divine art: the weapon's name over a dark band, the art's name stamped across it, light lines running out. */
    private static void divineBanner(GuiGraphics g, int w, int h, Show s, float t) {
        float life = 2.0f;
        if (t >= life) return;
        float in = Anim.outCubic(Anim.window(t, 0, 0.25f));
        float out = Anim.window(t, life - 0.45f, life);
        float live = in * (1 - out);
        int c = opaque(s.sig);
        int lightC = Gfx.mix(c, 0xFFFFFFFF, 0.55f);
        float cy = h * 0.3f;
        float old = Gfx.fade(live);
        float bw = w * (0.4f + 0.6f * Anim.outCubic(Anim.window(t, 0, 0.4f)));
        float bh = 50;
        Shapes.box(w / 2f - bw / 2f, cy - bh / 2f, bw / 2f, bh).fillH(0x00000000, Gfx.alpha(0xFF04020A, 0.8f)).draw(g);
        Shapes.box(w / 2f, cy - bh / 2f, bw / 2f, bh).fillH(Gfx.alpha(0xFF04020A, 0.8f), 0x00000000).draw(g);
        float run = Anim.outCubic(Anim.window(t, 0.05f, 0.6f));
        for (float ly : new float[]{cy - bh / 2f, cy + bh / 2f - 1}) {
            float lw = bw * 0.5f * run;
            Shapes.box(w / 2f - lw, ly, lw, 1).fillH(0x00000000, Gfx.alpha(lightC, 0.9f)).glow(3, Gfx.alpha(c, 0.55f)).draw(g);
            Shapes.box(w / 2f, ly, lw, 1).fillH(Gfx.alpha(lightC, 0.9f), 0x00000000).glow(3, Gfx.alpha(c, 0.55f)).draw(g);
        }
        // "DIVINE ART · RUINBRINGER"
        String head = upper(Component.translatable("hud.beyondsolo.signature.divine").getString());
        if (!s.weapon.isEmpty()) head = head + "  ·  " + upper(s.weapon);
        head = SysFont.fit(head, 5.5f, true, w - 60);
        SysFont.spaced(g, head, w / 2f, cy - bh / 2f + 5, 5.5f, Gfx.alpha(lightC, 0.9f), true, 2.2f, 0.5f);
        stampName(g, w, cy + 3, s.sig, t, 18, Gfx.mix(c, 0xFFFFFFFF, 0.85f));
        Gfx.setAlpha(old);
    }

    /** A mythic art: a narrower band with the art's name. */
    private static void mythicBanner(GuiGraphics g, int w, int h, Show s, float t) {
        float life = 1.7f;
        if (t >= life) return;
        float in = Anim.outCubic(Anim.window(t, 0, 0.2f));
        float out = Anim.window(t, life - 0.4f, life);
        int c = opaque(s.sig);
        float cy = h * 0.3f;
        float old = Gfx.fade(in * (1 - out));
        float bw = Math.min(w - 40, 260) * (0.5f + 0.5f * in);
        float bh = 34;
        Shapes.box(w / 2f - bw / 2f, cy - bh / 2f, bw / 2f, bh).fillH(0x00000000, Gfx.alpha(0xFF06030A, 0.72f)).draw(g);
        Shapes.box(w / 2f, cy - bh / 2f, bw / 2f, bh).fillH(Gfx.alpha(0xFF06030A, 0.72f), 0x00000000).draw(g);
        Shapes.box(w / 2f - bw * 0.35f, cy + bh / 2f - 1, bw * 0.7f, 1).fillH(0x00000000, Gfx.alpha(c, 0.9f)).glow(2, Gfx.alpha(c, 0.5f)).draw(g);
        String head = upper(Component.translatable("hud.beyondsolo.signature.mythic").getString());
        SysFont.spaced(g, SysFont.fit(head, 5f, true, w - 60), w / 2f, cy - bh / 2f + 4, 5f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 2.2f, 0.5f);
        stampName(g, w, cy + 3, s.sig, t, 12, 0xFFFFFFFF);
        Gfx.setAlpha(old);
    }

    /** A legendary art: its name stamped small, low on the screen, between two gold rules. */
    private static void legendaryStamp(GuiGraphics g, int w, int h, Show s, float t) {
        float life = 1.3f;
        if (t >= life) return;
        float in = Anim.outCubic(Anim.window(t, 0, 0.15f));
        float out = Anim.window(t, life - 0.35f, life);
        int gold = 0xFF000000 | ItemGrade.LEGENDARY.color();
        float cy = h * 0.68f;
        float old = Gfx.fade(in * (1 - out));
        String name = upper(s.sig.displayName().getString());
        float size = 8.5f;
        float nw = SysFont.spacedWidth(name, size, true, 2.2f);
        float rule = 26 + 20 * in;
        Shapes.box(w / 2f - nw / 2f - 8 - rule, cy, rule, 1).fillH(0x00000000, Gfx.alpha(gold, 0.9f)).draw(g);
        Shapes.box(w / 2f + nw / 2f + 8, cy, rule, 1).fillH(Gfx.alpha(gold, 0.9f), 0x00000000).draw(g);
        String head = upper(Component.translatable("hud.beyondsolo.signature.legendary").getString());
        SysFont.spaced(g, head, w / 2f, cy - 13, 4.5f, Gfx.alpha(Gfx.mix(gold, 0xFFFFFFFF, 0.4f), 0.85f), true, 2f, 0.5f);
        float stamp = Anim.window(t, 0, 0.18f);
        float scale = 1 + 0.35f * (1 - Anim.outCubic(stamp));
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().scale(scale, scale, 1);
        SysFont.spaced(g, name, 0, -size * 0.5f, size, Gfx.mix(gold, 0xFFFFFFFF, 0.7f), true, 2.2f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** The art's name stamped: big, then settling to its size. */
    private static void stampName(GuiGraphics g, int w, float cy, Signature sig, float t, float maxSize, int color) {
        float stamp = Anim.window(t, 0.06f, 0.3f);
        float scale = 1 + 0.6f * (1 - Anim.outCubic(stamp));
        String name = upper(sig.displayName().getString());
        float track = 2.4f * Math.max(0, name.codePointCount(0, name.length()) - 1);
        float unit = SysFont.spacedWidth(name, 10, true, 0) / 10f;
        float size = Math.max(8, Math.min(maxSize, (w - 40 - track) / Math.max(0.01f, unit)));
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().scale(scale, scale, 1);
        float old = Gfx.fade(Math.min(1, stamp * 2));
        SysFont.spaced(g, name, 0, -size * 0.5f, size, color, true, 2.4f, 0.5f);
        Gfx.setAlpha(old);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ a bow's drawn art

    /** The star or the gale gathering where the arrow points, through the archer's own eyes; or how long it must still wait. */
    private static void drawnArt(GuiGraphics g, Minecraft mc, int w, int h, boolean effects) {
        Player p = mc.player;
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof SystemBowItem bow)) return;
        Signature sig = bow.signature();
        if (sig == null || !sig.drawn() || mc.options.hideGui) return;
        float cx = w / 2f, cy = h / 2f;
        if (!ArtFx.drawReady(sig)) {
            String wait = Component.translatable("hud.beyondsolo.signature.recovering", sig.displayName(), ArtFx.drawWait(sig)).getString();
            SysFont.draw(g, wait, cx, cy + 14, 5.5f, 0xB0C8D0E0, false, 0.5f);
            return;
        }
        float gather = ArtFx.gathering(p);
        if (gather <= 0 || !effects || !mc.options.getCameraType().isFirstPerson()) return;
        int c = opaque(sig);
        float spin = (float) (Anim.now() * 0.35);
        if (sig == Signature.STARFALL) {
            Shapes.rays(cx, cy, 5 + 20 * gather, 8).rotation(spin).fill(Gfx.alpha(c, 0.55f * gather)).additive().draw(g);
            Shapes.circle(cx, cy, 1.5f + 2.5f * gather).fill(Gfx.alpha(0xFFFFFFFF, 0.9f * gather)).glow(5 + 9 * gather, Gfx.alpha(c, 0.6f * gather)).additive()
                    .draw(g);
        } else {
            Shapes.ring(cx, cy, 6 + 16 * (1 - gather), 1.4f).dashes(6, 0.45f).rotation(spin * (1 + 2 * gather)).fill(Gfx.alpha(c, 0.75f * gather))
                    .additive().draw(g);
        }
        if (gather >= 1) {
            float pulse = Anim.pulse(0.6f);
            Shapes.ring(cx, cy, 10 + 3 * pulse, 1).fill(Gfx.alpha(0xFFFFFFFF, 0.5f + 0.3f * pulse)).glow(4, Gfx.alpha(c, 0.6f)).additive().draw(g);
        }
    }

    private SignatureFx() {}
}
