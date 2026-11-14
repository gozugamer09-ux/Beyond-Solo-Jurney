package com.beyondsolo.client.gfx;

/**
 * One clock and a few curves for every UI animation.
 * {@link #frame()} runs once per rendered frame, so animations are smooth at any frame rate
 * and never depend on the 20-per-second game tick.
 */
public final class Anim {
    private static long lastNanos;
    private static double now;
    private static float dt = 1 / 60f;
    /** Dev only: slows every UI animation down, used when recording previews on a slow machine. */
    private static double timeScale = parseScale();

    private static double parseScale() {
        try {
            return Double.parseDouble(System.getProperty("beyondsolo.timeScale", "1"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Advances the clock. Called from the render loop. */
    public static void frame() {
        long nanos = System.nanoTime();
        if (lastNanos == 0) lastNanos = nanos;
        double real = Math.min(0.25, (nanos - lastNanos) / 1e9);
        lastNanos = nanos;
        dt = (float) (real * timeScale);
        now += dt;
    }

    /** Seconds since the game started, on the UI clock. */
    public static double now() {
        return now;
    }

    /** Seconds since the previous frame. */
    public static float dt() {
        return dt;
    }

    public static void setTimeScale(double scale) {
        timeScale = Math.max(0.01, scale);
    }

    public static double timeScale() {
        return timeScale;
    }

    /** Seconds since {@code start} (a value from {@link #now()}). */
    public static float since(double start) {
        return (float) (now - start);
    }

    /** 0..1 progress of an animation that began at {@code start} and lasts {@code duration} seconds. */
    public static float progress(double start, float duration) {
        return clamp01((float) ((now - start) / duration));
    }

    /** 0..1 progress within a window of a longer timeline. */
    public static float window(float t, float from, float to) {
        return clamp01((t - from) / (to - from));
    }

    /** Moves {@code current} towards {@code target}; higher {@code speed} is snappier. Frame-rate independent. */
    public static float approach(float current, float target, float speed) {
        return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
    }

    /** A repeating 0..1..0 pulse with the given period in seconds. */
    public static float pulse(float period) {
        return 0.5f - 0.5f * (float) Math.cos(now / period * Math.PI * 2);
    }

    /** A repeating 0..1 ramp with the given period in seconds. */
    public static float loop(float period) {
        double v = now / period;
        return (float) (v - Math.floor(v));
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : Math.min(t, 1);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float outCubic(float t) {
        float u = 1 - clamp01(t);
        return 1 - u * u * u;
    }

    public static float outQuint(float t) {
        float u = 1 - clamp01(t);
        return 1 - u * u * u * u * u;
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Overshoots a little past 1 and settles: the "pop" curve. */
    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Bigger overshoot, for impacts. */
    public static float outBackStrong(float t) {
        t = clamp01(t);
        float c1 = 3.2f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    private Anim() {}
}
