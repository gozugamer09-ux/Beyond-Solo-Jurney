package com.beyondsolo.client.gfx;

import net.minecraft.util.Mth;

/** Shared drawing state and colour helpers. */
public final class Gfx {
    /** Multiplies the opacity of everything drawn through {@link Shapes} and {@link SysFont}. */
    private static float alpha = 1f;

    public static float alpha() {
        return alpha;
    }

    /** Sets the global opacity and returns the previous value, so callers can restore it. */
    public static float setAlpha(float value) {
        float previous = alpha;
        alpha = Mth.clamp(value, 0f, 1f);
        return previous;
    }

    /** Multiplies the global opacity and returns the previous value. */
    public static float fade(float factor) {
        return setAlpha(alpha * Mth.clamp(factor, 0f, 1f));
    }

    public static int rgb(int color) {
        return color & 0xFFFFFF;
    }

    /** Replaces the alpha channel (0..1). */
    public static int alpha(int color, float a) {
        return (Mth.clamp(Math.round(a * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /** Scales the existing alpha channel. A colour without alpha counts as opaque. */
    public static int fade(int color, float factor) {
        int a = color >>> 24;
        return (Mth.clamp(Math.round(a * factor), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = Math.round(Mth.lerp(t, a >>> 24, b >>> 24));
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Brightens (amount > 0) or darkens (amount < 0) a colour, keeping its alpha. */
    public static int shade(int color, float amount) {
        int target = amount >= 0 ? 0xFFFFFF : 0x000000;
        return (color & 0xFF000000) | (mix(0xFF000000 | color, 0xFF000000 | target, Math.abs(amount)) & 0xFFFFFF);
    }

    private Gfx() {}
}
