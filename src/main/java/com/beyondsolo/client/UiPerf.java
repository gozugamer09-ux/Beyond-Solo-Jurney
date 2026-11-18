package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/**
 * Dev-only frame timer behind the UI test driver's {@code perf} command. It times the next frames and writes to the
 * log how long a frame took, how much of that went on drawing the open screen, and how many System shapes and draw
 * calls a frame made, so a page can be measured before and after a change.
 *
 * <p>With {@code sync} the screen's time also waits for the graphics driver to finish its drawing, so it counts what
 * the screen costs the graphics card too. The cloud test machine draws in software (Mesa llvmpipe): compare its numbers
 * with each other, never with a real graphics card.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class UiPerf {
    private static final int WARMUP = 10;

    private static String label;
    private static boolean sync;
    private static int wanted;
    private static int skip;
    private static int count;
    private static long[] frames = new long[0];
    private static long[] screens = new long[0];
    private static long shapes;
    private static long batches;
    private static long lastFrame;
    private static long screenStart;
    private static long screenTime;

    /** Times the next {@code n} frames (after a few to settle) and logs them under {@code name}. */
    public static void start(String name, int n, boolean finish) {
        label = name;
        sync = finish;
        wanted = Math.max(1, n);
        frames = new long[wanted];
        screens = new long[wanted];
        skip = WARMUP;
        count = 0;
        shapes = 0;
        batches = 0;
        lastFrame = 0;
        screenTime = 0;
    }

    public static boolean running() {
        return label != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void frame(RenderFrameEvent.Pre event) {
        if (label == null) return;
        long now = System.nanoTime();
        long drawn = Shapes.drawn();
        long calls = Shapes.batches();
        if (lastFrame != 0) {
            if (skip > 0) {
                skip--;
            } else {
                frames[count] = now - lastFrame;
                screens[count] = screenTime;
                shapes += drawn;
                batches += calls;
                count++;
            }
        }
        lastFrame = now;
        screenTime = 0;
        if (count >= wanted) finish();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void screenPre(ScreenEvent.Render.Pre event) {
        if (label == null) return;
        if (sync) GL11.glFinish();
        screenStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void screenPost(ScreenEvent.Render.Post event) {
        if (label == null) return;
        if (sync) GL11.glFinish();
        screenTime += System.nanoTime() - screenStart;
    }

    private static void finish() {
        long[] f = Arrays.copyOf(frames, count);
        long[] s = Arrays.copyOf(screens, count);
        Arrays.sort(f);
        Arrays.sort(s);
        BeyondSolo.LOGGER.info("UITEST perf {}: {} frames, frame avg {} ms (p50 {}, p90 {}, p99 {}), {} fps; screen avg {} ms (p50 {}, p90 {}){}; "
                        + "{} shapes and {} shape draw calls a frame",
                label, count, ms(avg(f)), ms(f[count / 2]), ms(f[count * 9 / 10]), ms(f[Math.min(count - 1, count * 99 / 100)]),
                String.format("%.1f", 1e9 / avg(f)), ms(avg(s)), ms(s[count / 2]), ms(s[count * 9 / 10]), sync ? " with the driver's work" : "",
                String.format("%.1f", shapes / (double) count), String.format("%.1f", batches / (double) count));
        label = null;
    }

    private static double avg(long[] values) {
        double sum = 0;
        for (long v : values) sum += v;
        return values.length == 0 ? 0 : sum / values.length;
    }

    private static String ms(double nanos) {
        return String.format("%.2f", nanos / 1e6);
    }

    private UiPerf() {}
}
