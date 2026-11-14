package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.evaluation.EvaluationCrystalBlockEntity;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dev-only remote control, so the UI can be checked on a machine without a keyboard or screen.
 * Only active when the JVM is started with -Dbeyondsolo.uiTest=true (./gradlew runClient -PuiTest).
 *
 * <p>Write commands, one per line, to {@code <game dir>/uitest/cmd.txt}. They run in order:</p>
 * <pre>
 * screen status|quests|log|gallery     open a System page (or the drawing test page)
 * close                                close the current screen
 * mode floating|pinned|hidden          pop-up mode
 * keep on|off                          "stay open" pop-ups
 * font on|off                          System typeface
 * hide                                 press the hide key
 * cmd &lt;command&gt;                        run a server command as the player (no leading slash)
 * look &lt;yaw&gt; &lt;pitch&gt;                   turn the camera
 * timescale &lt;x&gt;                        slow down / speed up UI animations
 * hud on|off                           show or hide the vanilla HUD and the hand (like F1)
 * guiscale &lt;n&gt;                         GUI scale (0 = auto)
 * crystal idle|scan|reveal            play a state on the Evaluation Crystals nearby (this client only)
 * crystal lock &lt;rank&gt;                  ... the rank is decided (e, d, c, b, a, s)
 * crystal channel &lt;class&gt;              ... the class is being decided
 * shot &lt;name&gt;                          save screenshots/&lt;name&gt;.png
 * wait &lt;ticks&gt;                         pause before the next command
 * </pre>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class UiTestDriver {
    public static final boolean ENABLED = Boolean.getBoolean("beyondsolo.uiTest");
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int waitTicks;
    private static int pollTimer;

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null) return;
        if (++pollTimer % 5 == 0) poll(mc);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!QUEUE.isEmpty() && waitTicks == 0) {
            String line = QUEUE.pollFirst();
            try {
                run(mc, line);
            } catch (Exception e) {
                BeyondSolo.LOGGER.error("UITEST failed: {}", line, e);
            }
            if (QUEUE.isEmpty()) BeyondSolo.LOGGER.info("UITEST idle");
        }
    }

    private static void poll(Minecraft mc) {
        Path file = mc.gameDirectory.toPath().resolve("uitest").resolve("cmd.txt");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank() && !line.startsWith("#")) QUEUE.addLast(line.strip());
            }
            Files.delete(file);
        } catch (IOException e) {
            BeyondSolo.LOGGER.error("UITEST could not read {}", file, e);
        }
    }

    private static void run(Minecraft mc, String line) {
        String[] parts = line.split(" ", 2);
        String arg = parts.length > 1 ? parts[1].strip() : "";
        switch (parts[0]) {
            case "screen" -> {
                switch (arg) {
                    case "status" -> SystemScreen.openOn(SystemTabs.STATUS);
                    case "quests" -> SystemScreen.openOn(SystemTabs.QUESTS);
                    case "log" -> SystemScreen.openOn(SystemTabs.LOG);
                    default -> {}
                }
                mc.setScreen(arg.equals("gallery") ? new UiGalleryScreen() : new SystemScreen());
            }
            case "close" -> mc.setScreen(null);
            case "mode" -> ClientConfig.NOTIFICATION_MODE.set(ClientConfig.NotificationMode.valueOf(arg.toUpperCase()));
            case "keep" -> ClientConfig.KEEP_POPUPS.set(arg.equals("on"));
            case "font" -> ClientConfig.SYSTEM_FONT.set(arg.equals("on"));
            case "hide" -> ClientEvents.hideKeyPressed();
            case "cmd" -> mc.player.connection.sendCommand(arg);
            case "look" -> {
                String[] a = arg.split(" ");
                mc.player.setYRot(Float.parseFloat(a[0]));
                mc.player.setXRot(Float.parseFloat(a[1]));
            }
            case "timescale" -> Anim.setTimeScale(Double.parseDouble(arg));
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "guiscale" -> {
                mc.options.guiScale().set(Integer.parseInt(arg));
                mc.resizeDisplay();
            }
            case "crystal" -> {
                String[] a = arg.split(" ");
                int state = switch (a[0]) {
                    case "scan" -> EvaluationCrystalBlockEntity.SCANNING;
                    case "reveal" -> EvaluationCrystalBlockEntity.REVEALING;
                    case "lock" -> EvaluationCrystalBlockEntity.LOCKED;
                    case "channel" -> EvaluationCrystalBlockEntity.CHANNELING;
                    default -> EvaluationCrystalBlockEntity.IDLE;
                };
                int param = a.length < 2 ? 0 : state == EvaluationCrystalBlockEntity.CHANNELING
                        ? PlayerClass.valueOf(a[1].toUpperCase()).ordinal() : PlayerRank.valueOf(a[1].toUpperCase()).ordinal();
                BlockPos at = mc.player.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16))) {
                    if (mc.level.getBlockEntity(pos) instanceof EvaluationCrystalBlockEntity crystal) crystal.triggerEvent(state, param);
                }
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), message -> {});
            case "wait" -> waitTicks = Integer.parseInt(arg);
            default -> BeyondSolo.LOGGER.warn("UITEST unknown command: {}", line);
        }
        BeyondSolo.LOGGER.info("UITEST ran: {}", line);
    }

    private UiTestDriver() {}
}
