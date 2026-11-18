package com.beyondsolo.client.chronicle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.tower.TowerModule;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The journal's pictures: a creature or boss drawn as itself (a dark shape until the hunter has met it), a weapon as
 * its icon (a dark shape until held), a realm as a picture of it (dim until entered).
 */
final class JournalArt {
    /** The shade an unknown page's creature or weapon is drawn in. */
    private static final float[] SHADOW = {0.05f, 0.075f, 0.12f};
    private static final Map<String, LivingEntity> ENTITIES = new HashMap<>();
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    private static @Nullable Level level;

    /** The creature a page shows (made once per world), or null if it has none or can't be made. */
    static @Nullable LivingEntity entity(JournalEntry e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (level != mc.level) {
            ENTITIES.clear();
            level = mc.level;
        }
        if (ENTITIES.containsKey(e.id())) return ENTITIES.get(e.id());
        LivingEntity made = null;
        try {
            if (e.creature() != null) {
                made = EnemyModule.type(e.creature()).create(mc.level);
            } else if (e.boss() != null) {
                GateBossEntity b = ModEntities.GATE_BOSS.get().create(mc.level);
                if (b != null) b.displayAs(e.boss().minRank, e.boss());
                made = b;
            } else if (Journal.ARBITER.equals(e.name())) {
                made = ModEntities.ARBITER.get().create(mc.level);
            } else if (Journal.CITADEL_LORD.equals(e.name())) {
                made = TowerModule.CITADEL_LORD.get().create(mc.level);
            } else if (com.beyondsolo.worldboss.WorldBossJournal.kindOf(e) != null) {
                made = com.beyondsolo.worldboss.WorldBossModule.type(com.beyondsolo.worldboss.WorldBossJournal.kindOf(e)).create(mc.level);
            }
        } catch (RuntimeException ex) {
            BeyondSolo.LOGGER.debug("Journal could not make the creature of {}", e.id(), ex);
            made = null;
        }
        ENTITIES.put(e.id(), made);
        return made;
    }

    /** The rank a boss is shown at: the strongest form met, else its weakest. */
    static PlayerRank bossRank(JournalEntry e, @Nullable ChronicleData.Mark m) {
        BossKind kind = e.boss();
        if (kind == null) return PlayerRank.E;
        PlayerRank shown = kind.minRank;
        if (m != null) {
            for (PlayerRank r : PlayerRank.values()) if ((m.forms & (1 << JournalEntry.formBit(r))) != 0 && kind.livesAt(r)) shown = r;
        }
        return shown;
    }

    /**
     * Draws a creature in the box, turned by {@code turn} (radians), as a dark shape when {@code known} is false.
     * Returns false if it could not be drawn.
     */
    static boolean drawEntity(GuiGraphics g, JournalEntry e, @Nullable ChronicleData.Mark mark, float x0, float y0, float x1, float y1,
                              float fill, float turn, boolean known) {
        LivingEntity entity = entity(e);
        if (entity == null) return false;
        if (entity instanceof GateBossEntity boss && e.boss() != null) boss.displayAs(bossRank(e, mark), e.boss());
        float big = Math.max(entity.getBbHeight(), entity.getBbWidth() * 0.95f);
        int scale = (int) Mth.clamp((y1 - y0) * fill / Math.max(0.4f, big), 3, 80);
        Shapes.flush(g);   // the creature is clipped and maybe tinted: the shapes so far go first
        if (!known) RenderSystem.setShaderColor(SHADOW[0], SHADOW[1], SHADOW[2], 1f);
        try {
            // the box's bottom is where the feet go: lift a little for tall creatures so the head fits
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), scale, 0.0625f,
                    turn, -0.12f, entity);
            return true;
        } catch (RuntimeException ex) {
            ENTITIES.put(e.id(), null);
            return false;
        } finally {
            if (!known) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    static ItemStack item(JournalEntry e) {
        return ITEMS.computeIfAbsent(e.id(), id -> e.item() == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(e.item())));
    }

    /** Draws a weapon's icon centred on (cx, cy), {@code size} pixels across; a dark shape when not known. */
    static void drawItem(GuiGraphics g, JournalEntry e, float cx, float cy, float size, boolean known) {
        ItemStack stack = item(e);
        if (stack.isEmpty()) return;
        float s = size / 16f;
        g.pose().pushPose();
        g.pose().translate(cx - size / 2f, cy - size / 2f, 0);
        g.pose().scale(s, s, 1f);
        Shapes.flush(g);   // before the tint
        if (!known) RenderSystem.setShaderColor(SHADOW[0], SHADOW[1], SHADOW[2], 1f);
        try {
            g.renderItem(stack, 0, 0);
        } finally {
            if (!known) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
    }

    static ResourceLocation picture(JournalEntry e) {
        return BeyondSolo.id("textures/gui/journal/realm/" + e.name() + ".png");
    }

    /** Draws a realm's picture filling the box (cut to fit), dimmed by {@code light} (1 = as it is). */
    static void drawPicture(GuiGraphics g, JournalEntry e, float x, float y, float w, float h, float light) {
        int texW = 256, texH = 144;
        float boxAspect = w / h, texAspect = texW / (float) texH;
        float u = 0, v = 0, uw = texW, vh = texH;
        if (boxAspect > texAspect) {
            vh = texW / boxAspect;
            v = (texH - vh) / 2f;
        } else {
            uw = texH * boxAspect;
            u = (texW - uw) / 2f;
        }
        Shapes.flush(g);   // the picture is blitted straight away: the shapes so far go first
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(light, light, light * 1.04f, Gfx.alpha());
        g.blit(picture(e), Math.round(x), Math.round(y), Math.round(w), Math.round(h), u, v, Math.round(uw), Math.round(vh), texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    static void clear() {
        ENTITIES.clear();
        ITEMS.clear();
        level = null;
    }

    private JournalArt() {}
}
