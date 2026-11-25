package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.evaluation.EvaluationCrystalBlockEntity;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

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
 * screen status|skills|quests|log|shadows|storage|shop|forge|craft|gates|citadel|party|guild|chronicle|home|gallery  open a System page (or the drawing test page)
 * chronicle titles [id|category|all]   what the CHRONICLE page shows: a title, or the list filtered by category
 * chronicle rankings [hunters|citadel|rush|gates] [tier]   ... a leaderboard (tier: e..s or sovereign for the rush)
 * chronicle journal [creatures|bosses|realms|arsenal] [page]   ... a journal section and page (page id or bare name)
 * chronicle news                       ... the hunter news
 * shop buy|sell &lt;category&gt; [entry] what the Shop tab shows (category: featured, supplies, remedies, weapons, armor, special)
 * storagebtn &lt;0-3&gt;                   press a storage window button (prev, next, stack in, sort)
 * license                              hold up the hunter license from the inventory
 * forge &lt;slot&gt; [refine|infuse] [element] [ward|noward]  what the Forge tab shows (slot: inventory index, 36-39 armor)
 * forgego                              press the forge's REFINE / INFUSE button
 * craft &lt;recipe&gt; [amount]             what the Craft tab shows
 * craftgo                              press the Craft tab's CRAFT button
 * close                                close the current screen
 * mode floating|pinned|hidden          pop-up mode
 * keep on|off                          "stay open" pop-ups
 * font on|off                          System typeface
 * hide                                 press the hide key
 * cmd &lt;command&gt;                        run a server command as the player (no leading slash)
 * look &lt;yaw&gt; &lt;pitch&gt;                   turn the camera
 * timescale &lt;x&gt;                        slow down / speed up UI animations
 * hud on|off                           show or hide the vanilla HUD and the hand (like F1)
 * fov &lt;30-110&gt;                         field of view (30 looks at far things as if through a lens)
 * guiscale &lt;n&gt;                         GUI scale (0 = auto)
 * crystal idle|scan|reveal            play a state on the Evaluation Crystals nearby (this client only)
 * crystal lock &lt;rank&gt;                  ... the rank is decided (e, d, c, b, a, s)
 * crystal channel &lt;class&gt;              ... the class is being decided
 * shot &lt;name&gt;                          save screenshots/&lt;name&gt;.png
 * wait &lt;ticks&gt;                         pause before the next command
 * press &lt;key&gt; [ticks]                  press a key binding (attack, use, roll, jump, sprint, forward...) and hold it
 * slot &lt;0-8&gt;                           select a hotbar slot
 * view first|third|front              camera mode
 * camera &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;yaw&gt; &lt;pitch&gt;   film from a fixed point, like a camera on a tripod (the player is seen from outside)
 * camera off                           back to the player's own eyes
 * respawn                              respawn after dying
 * npc accept|turnin                    press the first Accept / Hand in button of the open villager window
 * npc scroll &lt;amount&gt;                  scroll the villager window's list
 * inventory                            open the player's inventory
 * mouse &lt;x&gt; &lt;y&gt;                       move the mouse to a GUI position (for tooltips)
 * click &lt;x&gt; &lt;y&gt;                       move the mouse to a GUI position and left-click there (a tab, a button, a row)
 * type &lt;text&gt;                          type into the open screen, as the keyboard would (a text field that has focus)
 * key enter|escape|backspace|tab       press one key on the open screen
 * hover &lt;slot&gt;                         move the mouse over a slot of the open container (menu slot index)
 * lang &lt;code&gt;                          switch the game's language (en_us, es_es...) and wait for the reload
 * reload                               reload the resource packs, as F3+T does, and wait until that is over (run
 *                                      ./gradlew processResources first to see changed models, animations or textures)
 * engrave &lt;skill&gt;                      pick a skill on the open engraving screen (a rune of Haste/Thrift/Might in use)
 * engravego                            press the engraving screen's CARVE button
 * gates &lt;n&gt;                            choose the n-th kind of key on the GATES page
 * gatesgo                              press the GATES page's TURN button
 * homego go|leave|buy|stay             press a HOME page button (go home, back out, buy the next part, stop going)
 * rerun                                press the re-run prompt's key (the accept key)
 * partydemo [alone]                    stand in a made-up party (or, alone, two invitations) on the PARTY page and the HUD
 * partygo &lt;action&gt; [name]              press a PARTY page button (invite, accept, decline, leave, kick, promote, disband, regroup)
 * partyfx start|broken|done|joined     play a regroup moment on the HUD (with the first other member's name)
 * guilddemo [member|raid|none]         stand in a made-up level 6 guild on the GUILD page (led by the player, as a plain
 *                                      member, with a raid mustering, or no guild and two invitations)
 * guildtab hall|members|hunters|raids  which GUILD page shows
 * guildform &lt;name&gt;|&lt;tag&gt;|&lt;crest&gt;|&lt;colour&gt;   fill in the founding form (crest 0-11, colour 0-9)
 * guildfx founded|level                play the guild-founded or level-up moment on the GUILD page
 * raiddemo [muster|countdown|lieutenants|barrier|boss|none]   stand in a made-up B-Rank raid (inside, the HUD only
 *                                      shows in a gate's cave)
 * raidbrief                            open the raid briefing (on a raid that is mustering)
 * raidresults [cleared|failed]         show a made-up raid's results board
 * raidfx barrier|broken|cataclysm|lieutenant   play a raid moment: a banner, or the cataclysm gathering
 * perf &lt;label&gt; [frames] [sync]       time the next frames (200) and log the frame time, the open screen's share and
 *                                      the System shapes and their draw calls a frame (see UiPerf); the next
 *                                      commands wait until it is done
 * </pre>
 *
 * <p>When every command of a file has run, the log says {@code UITEST idle}. The scripts in {@code tools/uitest}
 * start a server and a client on a virtual screen, send these commands and wait for that line.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class UiTestDriver {
    private static void moveMouse(Minecraft mc, double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), guiX * scale, guiY * scale);
        // Xvfb sends no cursor event for a programmatic move, so tell the mouse handler directly.
        try {
            var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, guiX * scale);
            y.setDouble(mc.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException e) {
            BeyondSolo.LOGGER.warn("UITEST can't move the mouse", e);
        }
    }

    public static final boolean ENABLED = Boolean.getBoolean("beyondsolo.uiTest");
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int waitTicks;
    private static int pollTimer;
    private static boolean busy;

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null) return;
        if (++pollTimer % 5 == 0) poll(mc);
        releaseKeys();
        if (reloading != null) {
            if (!reloading.isDone()) return;
            reloading = null;
            waitTicks = 10;   // a few frames with the new resources before anything looks at them
        }
        if (UiPerf.running()) return;   // the next commands wait until the frames being timed are over
        if (waitTicks > 0) {
            waitTicks--;
        } else {
            while (!QUEUE.isEmpty() && waitTicks == 0 && !UiPerf.running()) {
                String line = QUEUE.pollFirst();
                try {
                    run(mc, line);
                } catch (Exception e) {
                    BeyondSolo.LOGGER.error("UITEST failed: {}", line, e);
                }
            }
        }
        // Scripts wait for this line, so it is only written when everything is over: a final "wait" has run out
        // and every pressed key is up again.
        if (busy && QUEUE.isEmpty() && waitTicks == 0 && HELD.isEmpty() && !UiPerf.running()) {
            busy = false;
            BeyondSolo.LOGGER.info("UITEST idle");
        }
    }

    /** Where the "camera" command films from: x, y, z, yaw, pitch. Null films through the player's own eyes. */
    private static double[] tripod;
    private static java.lang.reflect.Method cameraPosition;

    /**
     * Anything a player aims at is hidden behind their own back in the third-person view, so skills are filmed from
     * a fixed point to the side instead. The player stays the camera's owner, so they still move and aim as usual.
     * The game turns the camera first and places it afterwards, so the turn is given here...
     */
    @SubscribeEvent
    static void turnCamera(ViewportEvent.ComputeCameraAngles event) {
        double[] at = tripod;
        if (!ENABLED || at == null) return;
        event.setYaw((float) at[3]);
        event.setPitch((float) at[4]);
    }

    /** ...and the place here: this is the first thing the game asks once it has placed the camera itself. */
    @SubscribeEvent
    static void placeCamera(ViewportEvent.ComputeFov event) {
        double[] at = tripod;
        if (!ENABLED || at == null) return;
        try {
            if (cameraPosition == null) {
                cameraPosition = Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class);
                cameraPosition.setAccessible(true);
            }
            cameraPosition.invoke(event.getCamera(), at[0], at[1], at[2]);
        } catch (ReflectiveOperationException e) {
            BeyondSolo.LOGGER.warn("UITEST can't place the camera", e);
            tripod = null;
        }
    }

    private static final java.util.Map<KeyMapping, Integer> HELD = new java.util.HashMap<>();
    /** A resource reload started by the "reload" command. Nothing else runs until it is over. */
    private static java.util.concurrent.CompletableFuture<Void> reloading;

    private static KeyMapping findKey(Minecraft mc, String name) {
        for (KeyMapping k : mc.options.keyMappings) {
            String id = k.getName();
            if (id.equals(name) || id.endsWith("." + name)) return k;
        }
        BeyondSolo.LOGGER.warn("UITEST no key binding called {}", name);
        return null;
    }

    private static void releaseKeys() {
        HELD.replaceAll((k, t) -> t - 1);
        HELD.entrySet().removeIf(e -> {
            if (e.getValue() > 0) return false;
            e.getKey().setDown(false);
            return true;
        });
    }

    private static void poll(Minecraft mc) {
        Path file = mc.gameDirectory.toPath().resolve("uitest").resolve("cmd.txt");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank() && !line.startsWith("#")) QUEUE.addLast(line.strip());
            }
            Files.delete(file);
            busy = true;
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
                    case "skills" -> SystemScreen.openOn(com.beyondsolo.client.skill.SkillClientEvents.SKILLS_TAB);
                    case "quests" -> SystemScreen.openOn(SystemTabs.QUESTS);
                    case "log" -> SystemScreen.openOn(SystemTabs.LOG);
                    case "shadows" -> SystemScreen.openOn(com.beyondsolo.client.shadow.ShadowClientEvents.SHADOWS_TAB);
                    case "storage" -> SystemScreen.openOn(com.beyondsolo.client.economy.EconomyClientEvents.STORAGE_TAB);
                    case "shop" -> SystemScreen.openOn(com.beyondsolo.client.economy.EconomyClientEvents.SHOP_TAB);
                    case "forge" -> SystemScreen.openOn(com.beyondsolo.client.gear.GearClientEvents.FORGE_TAB);
                    case "craft" -> SystemScreen.openOn(com.beyondsolo.client.crafting.CraftClientEvents.CRAFT_TAB);
                    case "gates" -> SystemScreen.openOn(com.beyondsolo.client.key.KeyClientEvents.GATES_TAB);
                    case "citadel" -> SystemScreen.openOn(com.beyondsolo.client.tower.TowerClientEvents.CITADEL_TAB);
                    case "party" -> SystemScreen.openOn(com.beyondsolo.client.party.PartyClientEvents.PARTY_TAB);
                    case "guild" -> SystemScreen.openOn(com.beyondsolo.client.guild.GuildClientEvents.GUILD_TAB);
                    case "chronicle" -> SystemScreen.openOn(com.beyondsolo.client.chronicle.ChronicleClientEvents.CHRONICLE_TAB);
                    case "home" -> SystemScreen.openOn(com.beyondsolo.client.home.HomeClientEvents.HOME_TAB);
                    default -> {}
                }
                mc.setScreen(arg.equals("gallery") ? new UiGalleryScreen() : new SystemScreen());
            }
            case "close" -> mc.setScreen(null);
            // partydemo [alone]: a made-up party (or invitations) on the PARTY page and the HUD, for screenshots
            case "partydemo" -> com.beyondsolo.client.party.PartyClient.demo(com.beyondsolo.client.party.PartyClient.demoState(arg));
            // partygo <invite|accept|decline|leave|kick|promote|disband|regroup> [player name]: a button of the PARTY page
            case "partyfx" -> {
                var others = com.beyondsolo.client.party.PartyClient.others();
                net.minecraft.network.chat.Component who = net.minecraft.network.chat.Component.literal(others.isEmpty() ? "Kael" : others.get(0).name());
                int kind = switch (arg) {
                    case "broken" -> com.beyondsolo.network.PartyEventPayload.REGROUP_CANCEL;
                    case "done" -> com.beyondsolo.network.PartyEventPayload.REGROUP_DONE;
                    case "joined" -> com.beyondsolo.network.PartyEventPayload.REGROUP_JOINED;
                    default -> com.beyondsolo.network.PartyEventPayload.REGROUP_START;
                };
                net.minecraft.network.chat.Component text = kind == com.beyondsolo.network.PartyEventPayload.REGROUP_CANCEL
                        ? net.minecraft.network.chat.Component.translatable("party.beyondsolo.regroup.moved") : who;
                com.beyondsolo.client.party.PartyClient.onEvent(new com.beyondsolo.network.PartyEventPayload(kind, com.beyondsolo.party.Regroup.CHANNEL_TICKS, text));
            }
            case "guilddemo" -> com.beyondsolo.client.guild.GuildClient.demo(com.beyondsolo.client.guild.GuildDemo.guild(arg));
            case "guildtab" -> com.beyondsolo.client.guild.GuildTab.show(com.beyondsolo.client.guild.GuildTab.Page.valueOf(arg.toUpperCase(java.util.Locale.ROOT)));
            case "guildform" -> {
                String[] a = arg.split("\\|", -1);
                com.beyondsolo.client.guild.GuildTab.fill(a[0].strip(), a.length > 1 ? a[1].strip() : "",
                        a.length > 2 ? Integer.parseInt(a[2].strip()) : 0, a.length > 3 ? Integer.parseInt(a[3].strip()) : 0);
            }
            case "guildfx" -> {
                var gs = com.beyondsolo.client.guild.GuildClient.state();
                String name = gs.getString("name").isEmpty() ? "Nightfall Lanterns" : gs.getString("name");
                boolean founded = arg.equals("founded");
                com.beyondsolo.client.guild.GuildClient.onEvent(new com.beyondsolo.network.GuildEventPayload(
                        founded ? com.beyondsolo.network.GuildEventPayload.FOUNDED : com.beyondsolo.network.GuildEventPayload.LEVEL_UP,
                        founded ? gs.getInt("crest") : Math.max(2, gs.getInt("level")), net.minecraft.network.chat.Component.literal(name)));
            }
            case "raiddemo" -> com.beyondsolo.client.raid.RaidClient.demo(com.beyondsolo.client.guild.GuildDemo.raid(arg));
            case "raidbrief" -> mc.setScreen(new com.beyondsolo.client.raid.RaidBriefingScreen());
            case "raidresults" -> {
                boolean cleared = !arg.equals("failed");
                com.beyondsolo.client.raid.RaidClient.demoResults(com.beyondsolo.client.guild.GuildDemo.results(cleared),
                        net.minecraft.network.chat.Component.translatable(com.beyondsolo.raid.Raids.KEY + (cleared ? "results.cleared" : "results.failed")),
                        com.beyondsolo.client.guild.GuildDemo.dungeon());
            }
            case "raidfx" -> com.beyondsolo.client.raid.RaidClient.onEvent(com.beyondsolo.client.guild.GuildDemo.moment(arg));
            case "partygo" -> {
                String[] a = arg.split(" ");
                com.beyondsolo.client.party.PartyTab.press(com.beyondsolo.client.party.PartyClient.actionNamed(a[0]),
                        com.beyondsolo.client.party.PartyClient.idOf(a.length > 1 ? a[1] : ""));
            }
            case "shop" -> {
                // shop buy|sell <category> [entry]: what the Shop tab shows
                String[] a = arg.split(" ");
                com.beyondsolo.client.economy.ShopTab.show(a[0].equals("sell"),
                        com.beyondsolo.economy.ShopCatalog.Category.valueOf(a.length > 1 ? a[1].toUpperCase() : "FEATURED"), a.length > 2 ? a[2] : "");
            }
            case "forge" -> {
                String[] a = arg.split(" ");
                com.beyondsolo.skill.SkillElement e = null;
                for (String w : a) {
                    if (w.equals("ward")) com.beyondsolo.client.gear.ForgeTab.setWard(true);
                    if (w.equals("noward")) com.beyondsolo.client.gear.ForgeTab.setWard(false);
                    for (var el : com.beyondsolo.skill.SkillElement.values()) if (el.name().equalsIgnoreCase(w)) e = el;
                }
                com.beyondsolo.client.gear.ForgeTab.show(Integer.parseInt(a[0]), a.length > 1 && a[1].equals("infuse"), e);
            }
            case "forgego" -> com.beyondsolo.client.gear.ForgeTab.press();
            case "craft" -> {
                String[] a = arg.split(" ");
                com.beyondsolo.client.crafting.CraftTab.show(a[0], a.length > 1 ? Integer.parseInt(a[1]) : 1);
            }
            case "craftgo" -> com.beyondsolo.client.crafting.CraftTab.press();
            case "engrave" -> com.beyondsolo.client.rune.RuneEngraveScreen.select(arg);
            case "engravego" -> com.beyondsolo.client.rune.RuneEngraveScreen.pressCarve();
            case "gates" -> com.beyondsolo.client.key.GatesTab.show(Integer.parseInt(arg));
            case "gatesgo" -> com.beyondsolo.client.key.GatesTab.press();
            case "homego" -> com.beyondsolo.client.home.HomeTab.press(switch (arg) {
                case "leave" -> com.beyondsolo.network.HomeActionPayload.LEAVE;
                case "buy" -> com.beyondsolo.network.HomeActionPayload.BUY;
                case "stay" -> com.beyondsolo.network.HomeActionPayload.CANCEL;
                default -> com.beyondsolo.network.HomeActionPayload.GO;
            });
            case "rerun" -> com.beyondsolo.client.key.KeyClient.pressRerun();
            case "storagebtn" -> {
                if (mc.screen instanceof com.beyondsolo.client.economy.StorageScreen s) s.press(Integer.parseInt(arg));
            }
            case "license" -> {
                for (net.minecraft.world.item.ItemStack st : mc.player.getInventory().items) {
                    var info = st.get(com.beyondsolo.economy.EconomyModule.LICENSE.get());
                    if (info != null) {
                        com.beyondsolo.client.economy.EconomyClient.openLicense(info);
                        break;
                    }
                }
            }
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
            case "fov" -> mc.options.fov().set(Integer.parseInt(arg));
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
            case "reload" -> {
                reloading = mc.reloadResourcePacks();
                waitTicks = 1;   // ends this tick's run of commands
            }
            case "lang" -> {
                // lang <code>: the game's language (en_us, es_es...), as the options screen sets it, then a reload
                mc.getLanguageManager().setSelected(arg);
                mc.options.languageCode = arg;
                mc.options.save();
                reloading = mc.reloadResourcePacks();
                waitTicks = 1;
            }
            case "press" -> {
                String[] a = arg.split(" ");
                KeyMapping key = findKey(mc, a[0]);
                if (key != null) {
                    KeyMapping.click(key.getKey());
                    key.setDown(true);
                    HELD.put(key, a.length > 1 ? Integer.parseInt(a[1]) : 2);
                }
            }
            case "slot" -> mc.player.getInventory().selected = Integer.parseInt(arg);
            case "view" -> mc.options.setCameraType(switch (arg) {
                case "third" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> CameraType.FIRST_PERSON;
            });
            case "camera" -> {
                if (arg.equals("off")) {
                    tripod = null;
                } else {
                    String[] a = arg.split(" ");
                    double[] at = new double[5];
                    for (int i = 0; i < 5; i++) at[i] = Double.parseDouble(a[i]);
                    tripod = at;
                    // a tripod does not flinch when the player is hurt, or bob when they walk
                    mc.options.damageTiltStrength().set(0.0);
                    mc.options.bobView().set(false);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
            case "respawn" -> mc.player.respawn();
            case "inventory" -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            case "mouse" -> {
                String[] a = arg.split(" ");
                moveMouse(mc, Double.parseDouble(a[0]), Double.parseDouble(a[1]));
            }
            case "click" -> {
                String[] a = arg.split(" ");
                double x = Double.parseDouble(a[0]), y = Double.parseDouble(a[1]);
                moveMouse(mc, x, y);
                if (mc.screen != null) {
                    mc.screen.mouseClicked(x, y, 0);
                    mc.screen.mouseReleased(x, y, 0);
                }
            }
            case "type" -> {
                if (mc.screen != null) for (char c : arg.toCharArray()) mc.screen.charTyped(c, 0);
            }
            case "key" -> {
                int k = switch (arg) {
                    case "enter" -> org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
                    case "escape" -> org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
                    case "backspace" -> org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
                    case "tab" -> org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
                    default -> -1;
                };
                if (mc.screen != null && k >= 0) {
                    mc.screen.keyPressed(k, 0, 0);
                    mc.screen.keyReleased(k, 0, 0);
                }
            }
            case "hover" -> {
                if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
                    var slot = cs.getMenu().slots.get(Integer.parseInt(arg));
                    moveMouse(mc, cs.getGuiLeft() + slot.x + 8, cs.getGuiTop() + slot.y + 8);
                }
            }
            case "chronicle" -> com.beyondsolo.client.chronicle.ChronicleTab.show(arg);
            case "perf" -> {
                // perf <label> [frames] [sync]: time the next frames (see UiPerf)
                String[] a = arg.split(" ");
                UiPerf.start(a[0], a.length > 1 ? Integer.parseInt(a[1]) : 200, a.length > 2 && a[2].equals("sync"));
            }
            case "npc" -> {
                String[] a = arg.split(" ");
                if (a[0].equals("scroll")) com.beyondsolo.client.npc.NpcBoardScreen.scrollBy(Float.parseFloat(a[1]));
                else com.beyondsolo.client.npc.NpcBoardScreen.press(a[0]);
            }
            default -> BeyondSolo.LOGGER.warn("UITEST unknown command: {}", line);
        }
        BeyondSolo.LOGGER.info("UITEST ran: {}", line);
    }

    private UiTestDriver() {}
}
