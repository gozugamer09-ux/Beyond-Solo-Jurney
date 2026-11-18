package com.beyondsolo.client.guild;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.guild.GuildColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * How a guild looks: its crest (one of {@link GuildColors#CRESTS} emblems, from the System's own icon set) on a
 * banner-shaped shield in the guild's colour.
 */
public final class GuildArt {
    /** The crests, in the order the founding form offers them. */
    private static final ResourceLocation[] CRESTS = {
            icon("chronicle_sword"), Icons.SHIELD, Icons.CROWN, Icons.FLAME,
            icon("element_lightning"), Icons.MOON, Icons.STAR, Icons.SKULL,
            icon("family_beast"), icon("element_nature"), icon("element_water"), icon("chronicle_castle")};

    private static ResourceLocation icon(String name) {
        return BeyondSolo.id("textures/gui/icon/" + name + ".png");
    }

    public static ResourceLocation crest(int index) {
        return CRESTS[Math.floorMod(index, CRESTS.length)];
    }

    /** A guild's colour as an opaque ARGB colour. */
    public static int color(int index) {
        return 0xFF000000 | GuildColors.color(index);
    }

    /**
     * The guild's emblem: a dark disc rimmed in its colour with the crest in the middle, a slowly turning ring of
     * dashes round it; {@code energy} 0..1 makes it blaze (a guild just founded, a level just gained).
     */
    public static void emblem(GuiGraphics g, int crest, int colorIndex, float cx, float cy, float radius, float energy) {
        int c = color(colorIndex);
        float t = Anim.loop(24f);
        Shapes.circle(cx, cy, radius).fillV(Gfx.mix(0xF0142238, c, 0.18f), 0xF0070D1A)
                .border(Math.max(0.75f, radius * 0.08f), c, Gfx.fade(c, 0.45f))
                .glow(radius * (0.5f + energy * 0.7f), Gfx.alpha(c, 0.3f + 0.4f * energy)).draw(g);
        Shapes.ring(cx, cy, radius * 1.2f, Math.max(0.6f, radius * 0.05f)).fill(Gfx.alpha(c, 0.7f)).dashes(24, 0.5f).rotation(t).draw(g);
        Shapes.ring(cx, cy, radius * 0.78f, Math.max(0.4f, radius * 0.03f)).fill(Gfx.alpha(c, 0.35f)).draw(g);
        Shapes.icon(crest(crest), cx, cy, radius * 1.15f).fillV(Gfx.mix(c, 0xFFFFFFFF, 0.75f), Gfx.mix(c, 0xFFFFFFFF, 0.15f))
                .glow(radius * 0.25f, Gfx.alpha(c, 0.7f)).draw(g);
    }

    /** A small badge of the crest, for rows and chips. */
    public static void badge(GuiGraphics g, int crest, int colorIndex, float cx, float cy, float radius) {
        int c = color(colorIndex);
        Shapes.circle(cx, cy, radius).fillV(Gfx.alpha(c, 0.3f), Gfx.alpha(Gfx.shade(c, -0.5f), 0.45f)).border(0.6f, Gfx.alpha(c, 0.9f)).draw(g);
        Shapes.icon(crest(crest), cx, cy, radius * 1.3f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.55f)).draw(g);
    }

    /** The guild's colour, lightened for text on the dark glass. */
    public static int text(int colorIndex) {
        return Gfx.mix(color(colorIndex), SystemTheme.TEXT, 0.35f);
    }

    private GuildArt() {}
}
