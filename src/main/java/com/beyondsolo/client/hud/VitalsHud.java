package com.beyondsolo.client.hud;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.GradeVisuals;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.system.SystemData;
import com.mojang.math.Axis;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * The System's own version of Minecraft's HUD. While it is on ({@link ClientConfig#SYSTEM_HUD}), the vanilla hotbar,
 * hearts, armour, hunger, air, mount health, jump bar and experience bar are switched off ({@link HudClientEvents})
 * and this draws them instead, with the {@code client.gfx} toolkit.
 *
 * <p>Everything sits where the vanilla pieces were, so muscle memory still works:</p>
 * <ul>
 *   <li>the hotbar at the bottom, as glass tiles, with a highlight that glides to the selected slot and a soft glow
 *       under graded gear;</li>
 *   <li>on the left, health (with armour next to it and absorption in gold) and the System's mana above it;</li>
 *   <li>on the right, hunger (with saturation and what the food in your hand would give), then fatigue, a mount's
 *       health and air, each only while it matters;</li>
 *   <li>between them the experience strip with the level gem on it: the System level once you are awakened, the
 *       vanilla experience level before that (afterwards the vanilla level is the small green number at its end).</li>
 * </ul>
 *
 * <p>The bars lean like the gem's edges, show their numbers, glide to new values, leave a pale trail where a value
 * just dropped, flash when it rises and pulse when it runs low.</p>
 */
public final class VitalsHud {
    /** Half the hotbar's width, as in vanilla. */
    private static final float HALF = 91;
    private static final float SLOT = 20;
    private static final float BAR_H = 7;
    /** One row of bars, gap included. */
    private static final float ROW = 10;
    /** Gap from the middle of the screen to the inner end of a bar, where the level gem sits. */
    private static final float INNER = 13;
    /** How far the bars lean: pixels sideways per pixel up. */
    private static final float LEAN = 0.42f;
    private static final float TEXT = 5.6f;

    private static final int HP_POISON_A = 0xFF4E8A22, HP_POISON_B = 0xFFA6D84C;
    private static final int HP_WITHER_A = 0xFF2B2A30, HP_WITHER_B = 0xFF77737F;
    private static final int HP_FROZEN_A = 0xFF5FB4E0, HP_FROZEN_B = 0xFFCDF2FF;
    private static final int ABSORB_A = 0xFFE0A21E, ABSORB_B = 0xFFFFE07A;
    private static final int FOOD_A = 0xFFC2702A, FOOD_B = 0xFFF4BE5C;
    private static final int FOOD_SICK_A = 0xFF5F6B24, FOOD_SICK_B = 0xFFAABB48;
    private static final int SATURATION = 0xFFFFF0B8;
    private static final int AIR_A = 0xFF2F8FD8, AIR_B = 0xFFA8E6FF;
    private static final int MOUNT_A = 0xFFA4552E, MOUNT_B = 0xFFE9A26C;
    private static final int ARMOR = 0xFFB9C8DA;
    private static final int VANILLA_XP = 0xFF80FF20;
    private static final int VANILLA_XP_A = 0xFF3E9A16, VANILLA_XP_B = 0xFFB6F276;
    private static final int JUMP_A = 0xFFD08A1E, JUMP_B = 0xFFFFE08A;

    /** A bar's animated state. Values are fractions of the bar, 0..1. */
    private static final class Meter {
        float shown = -1;
        /** The pale stretch left behind when the value drops, which drains away after a moment. */
        float trail;
        /** 0..1 while the row slides in or out. */
        float visible;
        float last = -1;
        double dropAt = -10;
        double riseAt = -10;

        /** {@code flashStep}: how big a rise in one frame has to be to flash (keeps steady regeneration calm). */
        void update(float target, boolean show, float flashStep) {
            boolean wasHidden = visible < 0.02f;
            visible = Anim.approach(visible, show ? 1 : 0, show ? 10f : 7f);
            if (!show) return; // keep the last values while the row slides away
            target = Mth.clamp(target, 0, 1);
            if (shown < 0 || wasHidden) {
                shown = trail = last = target;
                return;
            }
            if (target < last - 0.0005f) dropAt = Anim.now();
            else if (target > last + flashStep) riseAt = Anim.now();
            last = target;
            shown = Anim.approach(shown, target, 16f);
            if (trail < shown) trail = shown;
            else if (Anim.since(dropAt) > 0.4f) trail = Anim.approach(trail, shown, 6f);
        }

        float hit() {
            return 1 - Anim.progress(dropAt, 0.45f);
        }

        float heal() {
            return 1 - Anim.progress(riseAt, 0.6f);
        }

        void reset() {
            shown = last = -1;
            trail = visible = 0;
            dropAt = riseAt = -10;
        }
    }

    private static final Meter HP = new Meter();
    private static final Meter MP = new Meter();
    private static final Meter FOOD = new Meter();
    private static final Meter FATIGUE = new Meter();
    private static final Meter MANA_POISON = new Meter();
    private static final Meter MOUNT = new Meter();
    private static final Meter AIR = new Meter();
    private static float armorShown;
    private static float absorbShown;

    private static float selected = -1;
    private static float xpShown = -1;
    private static boolean xpWrap;
    private static int lastLevel = -1;
    private static double levelUpAt = -10;
    private static int lastVanillaLevel = -1;
    private static double vanillaLevelAt = -10;
    private static float jumpShown;

    private static final Matrix4f SHEAR = new Matrix4f();

    // ---------------------------------------------------------------- layout helpers other HUD pieces use

    /** True while this HUD replaces the vanilla one (spectators keep vanilla's spectator bar). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return ClientConfig.SYSTEM_HUD.get() && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
    }

    /**
     * The left edge of the hotbar with its off-hand slot (or the attack indicator, for left-handed players), in GUI
     * pixels, so pieces that sit beside the hotbar (the shadow army counter) can stay clear of it. The same for this
     * HUD and the vanilla one.
     */
    public static float hotbarLeft(int guiWidth) {
        Minecraft mc = Minecraft.getInstance();
        float left = guiWidth / 2f - HALF - 1;
        Player p = mc.player;
        if (p == null) return left;
        boolean offLeft = p.getMainArm().getOpposite() == HumanoidArm.LEFT;
        if (offLeft && !p.getOffhandItem().isEmpty()) left -= 29;
        else if (!offLeft && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) left -= 24;
        return left;
    }

    // ---------------------------------------------------------------- drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || !active()) return;
        SystemData d = ClientSystemData.get();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float cx = w / 2f;
        boolean survival = mc.gameMode.canHurtPlayer();
        float partial = delta.getGameTimeDeltaPartialTick(false);

        hotbar(g, mc, p, cx, h, delta);
        float stripTop = h - 27;
        float gemTop = strip(g, mc, p, d, cx, stripTop, partial);

        // ---- the left column: health, then mana
        float maxHp = Math.max(1, p.getMaxHealth());
        float hp = Mth.clamp(p.getHealth(), 0, maxHp);
        float absorb = p.getAbsorptionAmount();
        float scale = Math.max(maxHp, hp + absorb);
        HP.update(hp / scale, survival, 0.035f);
        absorbShown = Anim.approach(absorbShown, absorb / scale, 12f);
        MP.update(d.maxMana() <= 0 ? 0 : d.mana() / d.maxMana(), survival && d.isAwakened(), 0.04f);

        float leftX = cx - HALF;
        float colW = HALF - INNER;
        float y = h - 37;
        float leftTop = 0;
        if (HP.visible > 0.01f) {
            float old = Gfx.fade(HP.visible);
            healthRow(g, p, leftX, y + (1 - HP.visible) * 6, colW, hp, maxHp, absorb);
            Gfx.setAlpha(old);
            leftTop = h - y;
            y -= ROW * HP.visible;
        }
        if (MP.visible > 0.01f) {
            float old = Gfx.fade(MP.visible);
            float ry = y + (1 - MP.visible) * 6;
            drawBar(g, leftX, ry, colW, false, MP.shown, MP.trail, SystemTheme.MP_A, SystemTheme.MP_B, 0, MP.heal(), 3.2f, 0);
            Shapes.icon(Icons.DROP, leftX - 7, ry + BAR_H / 2f, 8).fillV(0xFFB9E6FF, SystemTheme.MP_B).glow(1.5f, Gfx.alpha(SystemTheme.MP_A, 0.7f)).draw(g);
            // mana rounds down, so the number never promises a cast it can't pay for
            barText(g, number(Mth.floor(d.mana())) + " / " + number(Mth.floor(d.maxMana())), leftX + colW - 4, ry, SystemTheme.TEXT, 1f);
            Gfx.setAlpha(old);
            leftTop = h - y;
            y -= ROW * MP.visible;
        }

        // ---- the right column: hunger, then fatigue, a mount's health and air
        FoodData food = p.getFoodData();
        FOOD.update(food.getFoodLevel() / 20f, survival, 0.04f);
        float fatigue = d.exhaustion() / 100f;
        FATIGUE.update(fatigue, survival && d.isAwakened() && d.exhaustion() > 0.5f, 2f);
        float poison = com.beyondsolo.client.manapoison.ManaPoisonClient.shown();
        MANA_POISON.update(poison / 100f, survival && com.beyondsolo.client.manapoison.ManaPoisonClient.meter(), 2f);
        Entity vehicle = p.getVehicle();
        LivingEntity mount = vehicle instanceof LivingEntity le && le.showVehicleHealth() ? le : null;
        MOUNT.update(mount == null ? 0 : mount.getHealth() / Math.max(1, mount.getMaxHealth()), mount != null, 0.04f);
        int maxAir = Math.max(1, p.getMaxAirSupply());
        int air = Mth.clamp(p.getAirSupply(), 0, maxAir);
        AIR.update(air / (float) maxAir, survival && (p.isEyeInFluid(FluidTags.WATER) || air < maxAir), 2f);

        float rightX = cx + INNER;
        float ry = h - 37;
        float rightTop = 0;
        if (FOOD.visible > 0.01f) {
            float old = Gfx.fade(FOOD.visible);
            hungerRow(g, p, food, rightX, ry + (1 - FOOD.visible) * 6, colW);
            Gfx.setAlpha(old);
            rightTop = h - ry;
            ry -= ROW * FOOD.visible;
        }
        if (FATIGUE.visible > 0.01f) {
            float old = Gfx.fade(FATIGUE.visible);
            float by = ry + (1 - FATIGUE.visible) * 6;
            boolean critical = d.exhaustion() >= 80;
            float pulse = Anim.pulse(0.7f);
            int a = critical ? SystemTheme.HP_A : SystemTheme.FATIGUE_A;
            int b = critical ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, pulse * 0.4f) : SystemTheme.FATIGUE_B;
            drawBar(g, rightX, by, colW, true, FATIGUE.shown, FATIGUE.shown, a, b, critical ? 1 : 0, 0, 2.6f, 0);
            iconRight(g, Icons.FATIGUE, rightX + colW, by, critical ? Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, pulse * 0.4f) : SystemTheme.FATIGUE_B, critical ? pulse : 0);
            barText(g, Math.round(d.exhaustion()) + "%", rightX + 4, by, critical ? 0xFFFFD6D6 : SystemTheme.TEXT, 0f);
            Gfx.setAlpha(old);
            rightTop = h - ry;
            ry -= ROW * FATIGUE.visible;
        }
        if (MANA_POISON.visible > 0.01f) {
            float old = Gfx.fade(MANA_POISON.visible);
            float by = ry + (1 - MANA_POISON.visible) * 6;
            manaPoisonRow(g, rightX, by, colW);
            Gfx.setAlpha(old);
            rightTop = h - ry;
            ry -= ROW * MANA_POISON.visible;
        }
        if (MOUNT.visible > 0.01f) {
            float old = Gfx.fade(MOUNT.visible);
            float by = ry + (1 - MOUNT.visible) * 6;
            float low = MOUNT.shown <= 0.25f ? 1 : 0;
            drawBar(g, rightX, by, colW, true, MOUNT.shown, MOUNT.trail, MOUNT_A, MOUNT_B, low, MOUNT.heal(), 3.2f, 0);
            iconRight(g, Icons.HORSE, rightX + colW, by, MOUNT_B, 0);
            if (mount != null) barText(g, number(Mth.ceil(mount.getHealth())) + " / " + number(Mth.ceil(mount.getMaxHealth())), rightX + 4, by, SystemTheme.TEXT, 0f);
            Gfx.setAlpha(old);
            rightTop = h - ry;
            ry -= ROW * MOUNT.visible;
        }
        if (AIR.visible > 0.01f) {
            float old = Gfx.fade(AIR.visible);
            float by = ry + (1 - AIR.visible) * 6;
            float low = AIR.shown <= 0.3f ? 1 : 0;
            drawBar(g, rightX, by, colW, true, AIR.shown, AIR.shown, AIR_A, AIR_B, low, 0, 1.6f, 10);
            iconRight(g, Icons.BUBBLES, rightX + colW, by, AIR_B, low * Anim.pulse(0.5f));
            barText(g, (air + 19) / 20 + "s", rightX + 4, by, SystemTheme.TEXT, 0f);
            Gfx.setAlpha(old);
            rightTop = h - ry;
            ry -= ROW * AIR.visible;
        }

        // Vanilla moves the selected item's name and the action bar line up by these, and other mods stack on them.
        // The gem needs a little more room when there are no bars around it (creative).
        int left = leftTop > 0 ? Math.round(leftTop) + 12 : 39;
        int right = rightTop > 0 ? Math.round(rightTop) + 12 : 39;
        if (gemTop > 0 && !survival) left = Math.max(left, 68);
        mc.gui.leftHeight = Math.max(mc.gui.leftHeight, left);
        mc.gui.rightHeight = Math.max(mc.gui.rightHeight, right);
    }

    /**
     * Mana poisoning (in the thick mana of high-rank gates): violet, marked every tenth so the stages at 30, 60 and 90
     * can be read; it beats from Mana Poisoning on, flashes as a stage is reached and glows pale while a crystal
     * purifies it.
     */
    private static void manaPoisonRow(GuiGraphics g, float x, float y, float w) {
        int stage = com.beyondsolo.client.manapoison.ManaPoisonClient.stage();
        boolean purifying = com.beyondsolo.client.manapoison.ManaPoisonClient.purifying();
        float pulse = Anim.pulse(stage >= 3 ? 0.6f : 1f);
        float danger = stage >= 2 ? (stage >= 3 ? 1f : 0.5f) : 0f;
        int a = purifying ? 0xFF2C9C93 : com.beyondsolo.client.manapoison.ManaPoisonClient.VIOLET_DEEP;
        int b = purifying ? com.beyondsolo.client.manapoison.ManaPoisonClient.PALE
                : Gfx.mix(com.beyondsolo.client.manapoison.ManaPoisonClient.VIOLET, 0xFFFFFFFF, stage >= 2 ? pulse * 0.25f : 0);
        float flash = 1 - Anim.clamp01(com.beyondsolo.client.manapoison.ManaPoisonClient.sinceStage() / 0.8f);
        drawBar(g, x, y, w, true, MANA_POISON.shown, MANA_POISON.trail, a, b, danger, Math.max(flash, purifying ? 0.35f * Anim.pulse(1.2f) : 0), 2.2f, 10);
        int iconColor = purifying ? com.beyondsolo.client.manapoison.ManaPoisonClient.PALE
                : Gfx.mix(com.beyondsolo.client.manapoison.ManaPoisonClient.VIOLET, 0xFFFFFFFF, 0.3f);
        iconRight(g, Icons.SPIRAL, x + w, y, iconColor, stage >= 2 ? pulse : 0);
        String text = Math.round(com.beyondsolo.client.manapoison.ManaPoisonClient.level()) + "%";
        barText(g, text, x + 4, y, stage >= 2 ? Gfx.mix(0xFFFFFFFF, 0xFFE5C9FF, pulse) : SystemTheme.TEXT, 0f);
    }

    // ---------------------------------------------------------------- hotbar

    private static void hotbar(GuiGraphics g, Minecraft mc, Player p, float cx, int h, DeltaTracker delta) {
        int sel = p.getInventory().selected;
        if (selected < 0 || Math.abs(selected - sel) > 4.5f) selected = sel;
        selected = Anim.approach(selected, sel, 22f);

        float trayX = cx - HALF - 1;
        float trayY = h - 23;
        float trayW = 2 * HALF + 2;
        float trayH = 22;
        Shapes.box(trayX + 1, trayY + 2, trayW - 2, trayH - 1).radius(7).fill(0).glow(9, 0x66000000).draw(g);
        Shapes.box(trayX, trayY, trayW, trayH).radius(7).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.84f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f))
                .border(0.6f, Gfx.alpha(SystemTheme.ACCENT, 0.42f), Gfx.alpha(SystemTheme.ACCENT, 0.12f)).draw(g);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = p.getInventory().items.get(i);
            tile(g, cx - 89 + i * SLOT, h - 20, stack, i, false);
        }
        // the highlight glides between slots
        float hx = cx - 90 + selected * SLOT;
        float hy = h - 21;
        float pulse = Anim.pulse(2.4f);
        Shapes.box(hx, hy, 20, 20).radius(5).fillV(Gfx.alpha(SystemTheme.ACCENT, 0.2f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.1f))
                .border(1f, SystemTheme.ACCENT_BRIGHT, Gfx.alpha(SystemTheme.ACCENT, 0.55f))
                .glow(4 + 1.5f * pulse, Gfx.alpha(SystemTheme.ACCENT, 0.42f)).draw(g);
        Shapes.pill(hx + 6, hy - 0.8f, 8, 1.6f).fill(0xFFFFFFFF).glow(3.5f, Gfx.alpha(SystemTheme.ACCENT, 0.9f)).draw(g);

        int seed = 1;
        for (int i = 0; i < 9; i++) {
            item(g, mc, p, p.getInventory().items.get(i), cx - 88 + i * SLOT, h - 19, delta, seed++);
        }

        // off-hand slot, on the side vanilla puts it
        ItemStack off = p.getOffhandItem();
        boolean offLeft = p.getMainArm().getOpposite() == HumanoidArm.LEFT;
        if (!off.isEmpty()) {
            float ox = offLeft ? cx - HALF - 28 : cx + HALF + 7;
            Shapes.box(ox + 1, trayY + 2, 20, trayH - 1).radius(7).fill(0).glow(8, 0x60000000).draw(g);
            Shapes.box(ox, trayY, 22, trayH).radius(7).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.84f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f))
                    .border(0.6f, Gfx.alpha(SystemTheme.ACCENT, 0.42f), Gfx.alpha(SystemTheme.ACCENT, 0.12f)).draw(g);
            tile(g, ox + 2, h - 20, off, 9, true);
            item(g, mc, p, off, offLeft ? cx - HALF - 26 : cx + HALF + 10, h - 19, delta, seed);
        }

        // vanilla's "hotbar" attack indicator, as a small ring on the far side from the off-hand
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float f = p.getAttackStrengthScale(0f);
            if (f < 1f) {
                float ax = offLeft ? cx + HALF + 15 : cx - HALF - 15;
                float ay = h - 11;
                Shapes.circle(ax, ay, 8).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.85f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f)).draw(g);
                Shapes.ring(ax, ay, 6, 1.8f).fill(0x30FFFFFF).draw(g);
                Shapes.ring(ax, ay, 6, 1.8f).fillAround(SystemTheme.ACCENT_DEEP, SystemTheme.ACCENT_BRIGHT).progress(f).glow(2, Gfx.alpha(SystemTheme.ACCENT, 0.6f)).draw(g);
            }
        }
    }

    /** One hotbar tile; graded gear gets a soft glow in its grade's colour, like in the inventory. */
    private static void tile(GuiGraphics g, float x, float y, ItemStack stack, int index, boolean offhand) {
        ItemGrade grade = ItemGrade.of(stack);
        if (grade != null && grade != ItemGrade.COMMON) {
            int c = 0xFF000000 | GradeVisuals.shimmer(grade, index * 0.6f);
            float pulse = grade.shimmers() ? 0.75f + 0.25f * Anim.pulse(2.2f) : 0.85f;
            Shapes.box(x, y, 18, 18).radius(4).fillV(Gfx.alpha(c, 0.06f * pulse), Gfx.alpha(c, (0.22f + 0.04f * grade.tier()) * pulse))
                    .border(0.6f, Gfx.alpha(c, 0.2f), Gfx.alpha(c, 0.65f * pulse)).draw(g);
            Shapes.pill(x + 4, y + 16.4f, 10, 1.2f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.3f)).glow(2.5f, Gfx.alpha(c, 0.8f * pulse)).draw(g);
        } else {
            Shapes.box(x, y, 18, 18).radius(4).fillV(0x30000000, 0x48000000).border(0.5f, 0x22FFFFFF, 0x10FFFFFF).draw(g);
        }
    }

    /** An item with vanilla's pick-up "pop" and its count and durability bar. */
    private static void item(GuiGraphics g, Minecraft mc, Player p, ItemStack stack, float x, float y, DeltaTracker delta, int seed) {
        if (stack.isEmpty()) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        float pop = stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false);
        if (pop > 0) {
            float s = 1 + pop / 5f;
            g.pose().pushPose();
            g.pose().translate(ix + 8, iy + 12, 0);
            g.pose().scale(1 / s, (s + 1) / 2f, 1);
            g.pose().translate(-(ix + 8), -(iy + 12), 0);
        }
        g.renderItem(p, stack, ix, iy, seed);
        if (pop > 0) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, ix, iy);
    }

    // ---------------------------------------------------------------- experience strip and level gem

    /** Draws the strip above the hotbar and the gem on it. Returns the gem's top edge, or 0 when there is no gem. */
    private static float strip(GuiGraphics g, Minecraft mc, Player p, SystemData d, float cx, float y, float partial) {
        boolean awakened = d.isAwakened();
        boolean vanillaXp = mc.gameMode.hasExperience();
        PlayerRideableJumping jumper = mc.player.jumpableVehicle();
        float sx = cx - HALF;
        float sw = 2 * HALF;
        float sh = 2.6f;

        // level changes: the strip runs to the end and starts over, and the gem pops
        if (awakened && lastLevel >= 0 && d.level() > lastLevel) {
            levelUpAt = Anim.now();
            xpWrap = true;
        }
        lastLevel = awakened ? d.level() : -1;
        int vLevel = p.experienceLevel;
        if (lastVanillaLevel >= 0 && vLevel > lastVanillaLevel) vanillaLevelAt = Anim.now();
        lastVanillaLevel = vLevel;

        float target;
        int a;
        int b;
        if (awakened) {
            target = d.xpToNextLevel() <= 0 ? 1 : Mth.clamp((float) d.xp() / d.xpToNextLevel(), 0, 1);
            a = SystemTheme.XP_A;
            b = SystemTheme.XP_B;
        } else {
            target = p.experienceProgress;
            a = VANILLA_XP_A;
            b = VANILLA_XP_B;
        }
        if (xpShown < 0) xpShown = target;
        if (xpWrap) {
            xpShown = Anim.approach(xpShown, 1.05f, 14f);
            if (xpShown >= 1f) {
                xpShown = 0;
                xpWrap = false;
            }
        } else {
            xpShown = Anim.approach(xpShown, target, 10f);
        }

        boolean showStrip = awakened || vanillaXp || jumper != null;
        if (showStrip) {
            if (jumper != null) {
                float jump = mc.player.getJumpRidingScale();
                jumpShown = Anim.approach(jumpShown, jump, 20f);
                boolean cooling = jumper.getJumpCooldown() > 0;
                Ui.bar(g, sx, y, sw, sh, cooling ? 1 : jumpShown, cooling ? 0xFF4A4F5A : JUMP_A, cooling ? 0xFF6A707C : JUMP_B);
            } else {
                Ui.bar(g, sx, y, sw, sh, Math.min(1, xpShown), a, b);
            }
            float flash = 1 - Anim.progress(levelUpAt, 0.9f);
            if (flash > 0) {
                Shapes.pill(sx, y, sw, sh).fill(Gfx.alpha(0xFFFFFFFF, 0.6f * flash)).glow(6 * flash, Gfx.alpha(SystemTheme.XP_B, 0.7f * flash)).additive().draw(g);
            }
        }

        // the gem: the System level once awakened, vanilla's level before that
        float gemY = y + sh / 2f - 7.5f;
        if (awakened) {
            int color = SystemTheme.rank(d.rank());
            float pop = 1 - Anim.progress(levelUpAt, 0.7f);
            gem(g, cx, gemY, color, String.valueOf(d.level()), Anim.outBack(1 - pop) * pop, pop);
            // vanilla's level, for enchanting, as the small green number at the strip's right end
            if (vanillaXp && vLevel > 0) {
                float fresh = 1 - Anim.progress(vanillaLevelAt, 1.2f);
                String text = String.valueOf(vLevel);
                float size = 6f + fresh;
                float tx = sx + sw + 3;
                float ty = SysFont.centerY(y + sh / 2f - 3, 6, size);
                SysFont.draw(g, text, tx + 0.5f, ty + 0.5f, size, 0xC0000000, true, 0f);
                SysFont.draw(g, text, tx, ty, size, Gfx.mix(VANILLA_XP, 0xFFFFFFFF, fresh * 0.6f), true, 0f);
            }
            return gemY - 9;
        }
        if (vanillaXp && vLevel > 0) {
            float pop = 1 - Anim.progress(vanillaLevelAt, 0.7f);
            gem(g, cx, gemY, VANILLA_XP, String.valueOf(vLevel), Anim.outBack(1 - pop) * pop, pop);
            return gemY - 9;
        }
        return 0;
    }

    /** A diamond with a number in it. {@code pop} 0..1 swells it, {@code energy} 0..1 makes it blaze. */
    private static void gem(GuiGraphics g, float cx, float cy, int color, String text, float pop, float energy) {
        float s = 12.4f * (1 + 0.22f * pop);
        if (energy > 0) {
            float r = 9 + (1 - energy) * 16;
            Shapes.ring(cx, cy, r, 1.6f * energy).fill(Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.4f), energy)).glow(4, Gfx.alpha(color, 0.6f * energy)).additive().draw(g);
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-s / 2f, -s / 2f, s, s).radius(2.4f).fillV(0xF4182A44, 0xF4060C18)
                .border(1f, Gfx.mix(color, 0xFFFFFFFF, 0.25f), Gfx.fade(color, 0.55f))
                .glow(3.5f + 6 * energy, Gfx.alpha(color, 0.38f + 0.45f * energy)).draw(g);
        float inner = s - 4.2f;
        Shapes.box(-inner / 2f, -inner / 2f, inner, inner).radius(1.4f).fill(0).border(0.5f, Gfx.alpha(color, 0.45f), Gfx.alpha(color, 0.15f)).draw(g);
        g.pose().popPose();
        float size = text.length() >= 3 ? 5.6f : 7.2f;
        int textColor = Gfx.mix(color, 0xFFFFFFFF, 0.7f + 0.3f * energy);
        SysFont.draw(g, text, cx, SysFont.centerY(cy - 3.5f, 7, size), size, textColor, true, 0.5f);
    }

    // ---------------------------------------------------------------- the rows

    private static void healthRow(GuiGraphics g, Player p, float x, float y, float w, float hp, float maxHp, float absorb) {
        int a = SystemTheme.HP_A;
        int b = SystemTheme.HP_B;
        if (p.hasEffect(MobEffects.POISON)) {
            a = HP_POISON_A;
            b = HP_POISON_B;
        } else if (p.hasEffect(MobEffects.WITHER)) {
            a = HP_WITHER_A;
            b = HP_WITHER_B;
        } else if (p.isFullyFrozen()) {
            a = HP_FROZEN_A;
            b = HP_FROZEN_B;
        }
        float frac = hp / maxHp;
        float low = frac <= 0.25f ? 1 : frac <= 0.4f ? 0.4f : 0;
        float hit = HP.hit();
        float shake = hit > 0 ? (float) Math.sin(Anim.now() * 70) * 1.3f * hit * hit : 0;
        boolean regen = p.hasEffect(MobEffects.REGENERATION);
        x += shake;
        drawBar(g, x, y, w, false, HP.shown, HP.trail, a, b, low, HP.heal(), regen ? 0.9f : 3.2f, 10);
        // absorption: gold, right after the red
        if (absorbShown > 0.003f) {
            float ix = x + 1.2f;
            float iw = w - 2.4f;
            g.pose().pushPose();
            lean(g, x + w / 2f, y + BAR_H / 2f, -LEAN);
            seg(ix, y + 1.2f, iw, BAR_H - 2.4f, HP.shown, HP.shown + absorbShown, false).fillH(ABSORB_A, ABSORB_B)
                    .glow(2, Gfx.alpha(ABSORB_B, 0.6f)).sheen(Anim.loop(1.8f) * 1.6f - 0.3f, 0.35f).draw(g);
            g.pose().popPose();
        }
        // heart, beating when low
        float beat = low >= 1 ? 1 + 0.18f * (float) Math.pow(Anim.pulse(0.55f), 3) : 1;
        Shapes.icon(Icons.HEART, x - 7, y + BAR_H / 2f, 8 * beat).fillV(Gfx.mix(b, 0xFFFFFFFF, 0.35f), a)
                .glow(1.5f + 2 * low, Gfx.alpha(a, 0.7f)).draw(g);
        // numbers, with absorption as a gold "+n"
        String text = number(Mth.ceil(hp)) + " / " + number(Mth.ceil(maxHp));
        float tx = x + w - 4;
        barText(g, text, tx, y, low >= 1 ? Gfx.mix(0xFFFFFFFF, 0xFFFFC2C2, Anim.pulse(0.55f)) : SystemTheme.TEXT, 1f);
        if (absorb > 0.05f) {
            float tw = SysFont.width(text, TEXT, true);
            barText(g, "+" + number(Mth.ceil(absorb)), tx - tw - 3, y, 0xFFFFE07A, 1f);
        }
        // armour, as a small plate left of the heart
        float armor = p.getArmorValue();
        armorShown = Anim.approach(armorShown, armor > 0 ? 1 : 0, 10f);
        if (armorShown > 0.02f) {
            String value = String.valueOf(Math.round(armor));
            float tw = SysFont.width(value, TEXT, true);
            float cw = tw + 15;
            float chipX = x - 13.5f - cw;
            float old = Gfx.fade(armorShown);
            g.pose().pushPose();
            lean(g, chipX + cw / 2f, y + BAR_H / 2f, -LEAN);
            Shapes.box(chipX, y - 0.5f, cw, BAR_H + 1).radius(2).fillV(0xD0182434, 0xD00A121E)
                    .border(0.6f, Gfx.alpha(ARMOR, 0.6f), Gfx.alpha(ARMOR, 0.2f)).draw(g);
            g.pose().popPose();
            Shapes.icon(Icons.SHIELD, chipX + 6, y + BAR_H / 2f, 6.5f).fillV(0xFFFFFFFF, ARMOR).draw(g);
            barText(g, value, chipX + 11, y, SystemTheme.TEXT, 0f);
            Gfx.setAlpha(old);
        }
    }

    private static void hungerRow(GuiGraphics g, Player p, FoodData food, float x, float y, float w) {
        boolean sick = p.hasEffect(MobEffects.HUNGER);
        int a = sick ? FOOD_SICK_A : FOOD_A;
        int b = sick ? FOOD_SICK_B : FOOD_B;
        int level = food.getFoodLevel();
        float low = level <= 6 ? 1 : 0; // can't sprint
        float shake = low > 0 && Anim.loop(1.6f) < 0.12f ? (float) Math.sin(Anim.now() * 60) * 0.8f : 0;
        drawBar(g, x, y, w, true, FOOD.shown, FOOD.trail, a, b, low * 0.7f, FOOD.heal(), 3.6f, 10);
        float ix = x + 1.2f;
        float iw = w - 2.4f;
        g.pose().pushPose();
        lean(g, x + w / 2f, y + BAR_H / 2f, LEAN);
        // saturation: a bright line along the top of the fill
        float sat = Math.min(food.getSaturationLevel(), level) / 20f;
        if (sat > 0.005f) {
            seg(ix, y + 1.2f, iw, 1.1f, 0, sat, true).fill(Gfx.alpha(SATURATION, 0.9f)).glow(1.5f, Gfx.alpha(SATURATION, 0.6f)).draw(g);
        }
        // what the food in hand would give, as a pulsing ghost
        ItemStack held = p.getMainHandItem();
        FoodProperties props = held.getFoodProperties(p);
        if (props == null) {
            held = p.getOffhandItem();
            props = held.getFoodProperties(p);
        }
        if (props != null && level < 20) {
            float after = Math.min(20, level + props.nutrition()) / 20f;
            float pulse = 0.35f + 0.35f * Anim.pulse(1.1f);
            seg(ix, y + 1.2f, iw, BAR_H - 2.4f, FOOD.shown, after, true).fill(Gfx.alpha(Gfx.mix(b, 0xFFFFFFFF, 0.5f), pulse)).draw(g);
        }
        g.pose().popPose();
        iconRight(g, Icons.FOOD, x + w + shake, y, sick ? FOOD_SICK_B : Gfx.mix(FOOD_B, 0xFFFFFFFF, 0.25f), low * Anim.pulse(0.6f));
        barText(g, level + " / 20", x + 4, y, low > 0 ? Gfx.mix(0xFFFFFFFF, 0xFFFFD0A0, Anim.pulse(0.6f)) : SystemTheme.TEXT, 0f);
    }

    /** The icon beside a bar of the right column. */
    private static void iconRight(GuiGraphics g, ResourceLocation icon, float barRight, float y, int color, float glow) {
        Shapes.icon(icon, barRight + 7, y + BAR_H / 2f, 8).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.3f), color)
                .glow(1.5f + 2.5f * glow, Gfx.alpha(color, 0.6f + 0.3f * glow)).draw(g);
    }

    // ---------------------------------------------------------------- bar drawing

    /**
     * One leaning bar: the dark track, a pale trail where the value just dropped, the fill with its slow shine, faint
     * segment ticks and a flash when the value rises. {@code mirrored} bars lean the other way and fill from their
     * right end, so the two columns mirror each other around the gem.
     *
     * @param danger 0..1 pulses the frame red
     * @param ticks  number of segments marked on the fill (0 = none)
     */
    private static void drawBar(GuiGraphics g, float x, float y, float w, boolean mirrored, float fill, float trail,
                                int colA, int colB, float danger, float heal, float sheenPeriod, int ticks) {
        g.pose().pushPose();
        lean(g, x + w / 2f, y + BAR_H / 2f, mirrored ? LEAN : -LEAN);
        float pulse = Anim.pulse(0.8f);
        Shapes.box(x, y, w, BAR_H).radius(1.8f).fillV(0xC80B1528, 0xD8040A16)
                .border(0.6f, Gfx.mix(Gfx.alpha(SystemTheme.ACCENT, 0.36f), Gfx.alpha(SystemTheme.DANGER, 0.95f), danger * pulse), Gfx.alpha(SystemTheme.ACCENT, 0.1f))
                .glow(danger > 0 ? 2.5f + 4 * danger * pulse : 0, Gfx.alpha(SystemTheme.DANGER, 0.5f * danger)).draw(g);
        float ix = x + 1.2f;
        float iy = y + 1.2f;
        float iw = w - 2.4f;
        float ih = BAR_H - 2.4f;
        if (trail > fill + 0.002f) {
            seg(ix, iy, iw, ih, fill, trail, mirrored).fill(Gfx.alpha(Gfx.mix(colB, 0xFFFFFFFF, 0.7f), 0.9f)).draw(g);
        }
        if (fill > 0.002f) {
            float sheen = Anim.loop(sheenPeriod) * 1.6f - 0.3f;
            seg(ix, iy, iw, ih, 0, fill, mirrored).fillH(mirrored ? colB : colA, mirrored ? colA : colB)
                    .glow(1.6f, Gfx.alpha(colB, 0.45f)).sheen(mirrored ? 1 - sheen : sheen, 0.3f).draw(g);
            // a hairline of light along the top
            seg(ix, iy, iw, 0.7f, 0, fill, mirrored).fill(Gfx.alpha(0xFFFFFFFF, 0.28f)).draw(g);
            if (heal > 0) {
                seg(ix, iy, iw, ih, 0, fill, mirrored).fill(Gfx.alpha(0xFFFFFFFF, 0.45f * heal * heal)).additive().draw(g);
            }
        }
        for (int i = 1; i < ticks; i++) {
            float tx = ix + iw * i / ticks;
            Shapes.box(tx - 0.3f, iy, 0.6f, ih).fill(0x60000000).draw(g);
        }
        g.pose().popPose();
    }

    /** The part of a bar between two fractions, measured from the left, or from the right for mirrored bars. */
    private static Shapes.Shape seg(float x, float y, float w, float h, float from, float to, boolean mirrored) {
        float a = Mth.clamp(from, 0, 1);
        float b = Mth.clamp(to, 0, 1);
        float sw = Math.max(0.05f, w * (b - a));
        float sx = mirrored ? x + w * (1 - b) : x + w * a;
        return Shapes.box(sx, y, sw, h).radius(Math.min(1.1f, Math.min(sw, h) / 2f));
    }

    /** Leans everything drawn next: {@code k} pixels sideways per pixel of height, around (px, py). */
    private static void lean(GuiGraphics g, float px, float py, float k) {
        g.pose().translate(px, py, 0);
        g.pose().last().pose().mul(SHEAR.identity().m10(k));
        g.pose().translate(-px, -py, 0);
    }

    /** A number on a bar, with a dark shadow so it reads on any fill. {@code align}: 0 left, 1 right. */
    private static void barText(GuiGraphics g, String text, float x, float y, int color, float align) {
        float ty = SysFont.centerY(y, BAR_H, TEXT);
        SysFont.draw(g, text, x + 0.5f, ty + 0.5f, TEXT, 0xB0000000, true, align);
        SysFont.draw(g, text, x, ty, TEXT, color, true, align);
    }

    /** Pools run to thousands late in the game: five digits and up are shortened (12.3k). */
    private static String number(int v) {
        if (v >= 100000) return String.format(Locale.ROOT, "%.0fk", v / 1000f);
        if (v >= 10000) return String.format(Locale.ROOT, "%.1fk", v / 1000f);
        return String.valueOf(v);
    }

    public static void reset() {
        HP.reset();
        MP.reset();
        FOOD.reset();
        FATIGUE.reset();
        MANA_POISON.reset();
        MOUNT.reset();
        AIR.reset();
        armorShown = absorbShown = 0;
        selected = -1;
        xpShown = -1;
        xpWrap = false;
        lastLevel = -1;
        lastVanillaLevel = -1;
        levelUpAt = vanillaLevelAt = -10;
        jumpShown = 0;
    }

    private VitalsHud() {}
}
