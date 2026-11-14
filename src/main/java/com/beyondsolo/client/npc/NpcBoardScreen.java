package com.beyondsolo.client.npc;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.quest.QuestText;
import com.beyondsolo.network.NpcActionPayload;
import com.beyondsolo.npc.NpcQuestKind;
import com.beyondsolo.npc.NpcService;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A villager's request window: the villager on the left (name, job, rapport, trade), their requests on the right.
 *
 * <p>Motion: the window opens out of a line of light while the villager's portrait circle draws itself; cards
 * slide in one after another. Taking a request stamps it ACCEPTED and folds it away, and the quest appears at the
 * top as yours. Handing one in sends the items (or a seal) flying from the card into the villager, who lights up
 * gold; the window then closes and the quest-complete moment plays.</p>
 */
public class NpcBoardScreen extends Screen {
    private static final float W = 404;
    private static final float H = 246;
    private static final float LEFT_W = 124;
    private static final float CARD_GAP = 6;

    /** One card in the list. Keyed by "slot:N" for an offer and by the quest id for one of yours. */
    private static final class Card {
        final String key;
        Quest quest;
        int slot;
        boolean mine;
        double appearAt;
        double leaveAt = -1;
        double actionAt = -1;
        float y = Float.NaN;
        float height;
        float[] button;

        Card(String key) {
            this.key = key;
        }
    }

    private record Fly(ItemStack stack, ResourceLocation icon, float fromX, float fromY, double startAt, float arc) {}

    private CompoundTag data;
    private final int entityId;
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private final List<Fly> flying = new ArrayList<>();
    private double openedAt = -1;
    private double closeAt = -1;
    private double handedAt = -1;
    private float scroll;
    private float scrollTarget;
    private float listContentH;
    private float[] tradeBtn;
    private final List<double[]> bursts = new ArrayList<>();

    private NpcBoardScreen(CompoundTag data) {
        super(Component.translatable("screen.beyondsolo.npc"));
        this.data = data;
        this.entityId = data.getInt("entity");
        sync(true);
    }

    /** A board arrived from the server: opens the window, or refreshes it if it is already open. */
    public static void receive(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NpcBoardScreen s && s.entityId == data.getInt("entity")) {
            s.data = data;
            s.sync(false);
            return;
        }
        mc.setScreen(new NpcBoardScreen(data));
    }

    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof NpcBoardScreen;
    }

    // ------------------------------------------------------------------ data

    private HolderLookup.Provider provider() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    private Component text(String key) {
        try {
            Component c = Component.Serializer.fromJson(data.getString(key), provider());
            return c == null ? Component.empty() : c;
        } catch (Exception e) {
            return Component.literal(data.getString(key));
        }
    }

    /** Matches the cards to the latest board: new ones slide in, gone ones fold away. */
    private void sync(boolean first) {
        HolderLookup.Provider provider = provider();
        if (provider == null) return;
        double now = Anim.now();
        Map<String, Card> fresh = new LinkedHashMap<>();
        boolean accepting = false;
        for (Card c : cards.values()) if (c.actionAt >= 0 && !c.mine) accepting = true;
        int i = 0;
        // yours first: ready to hand in, then in progress
        List<Quest> mine = new ArrayList<>();
        for (Tag t : data.getList("mine", Tag.TAG_COMPOUND)) mine.add(Quest.load((CompoundTag) t, provider));
        mine.sort((a, b) -> Boolean.compare(b.status() == Quest.Status.READY, a.status() == Quest.Status.READY));
        for (Quest q : mine) {
            String key = q.id().toString();
            Card c = cards.containsKey(key) ? cards.get(key) : new Card(key);
            if (c.appearAt == 0) c.appearAt = first ? 0 : now + (accepting ? 0.45 : 0);
            c.quest = q;
            c.mine = true;
            fresh.put(key, c);
            i++;
        }
        for (Tag t : data.getList("offers", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            String key = "slot:" + o.getInt("slot");
            Card c = cards.containsKey(key) ? cards.get(key) : new Card(key);
            if (c.appearAt == 0) c.appearAt = first ? 0 : now;
            c.quest = Quest.load(o.getCompound("quest"), provider);
            c.slot = o.getInt("slot");
            c.mine = false;
            fresh.put(key, c);
        }
        // cards that are gone keep their place and play until they have folded away
        Map<String, Card> ordered = new LinkedHashMap<>();
        for (Card c : fresh.values()) if (c.mine) ordered.put(c.key, c);
        for (Card c : cards.values()) {
            if (fresh.containsKey(c.key)) {
                if (!c.mine) ordered.put(c.key, fresh.get(c.key));
                continue;
            }
            if (c.leaveAt < 0) c.leaveAt = c.actionAt >= 0 ? Math.max(now, c.actionAt + 0.42) : now;
            ordered.put(c.key, c);
        }
        for (Card c : fresh.values()) ordered.putIfAbsent(c.key, c);
        cards.clear();
        cards.putAll(ordered);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        super.init();
        if (openedAt < 0) {
            openedAt = Anim.now();
            for (Card c : cards.values()) if (c.appearAt == 0) c.appearAt = openedAt;
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.4f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new NpcActionPayload(NpcService.ACTION_CLOSE, entityId, 0, NONE));
        super.removed();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e == null || !e.isAlive() || mc.player == null || e.distanceToSqr(mc.player) > 10 * 10) onClose();
        if (closeAt >= 0 && Anim.now() >= closeAt) onClose();
    }

    private static final UUID NONE = new UUID(0, 0);

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private float left() {
        return Math.round((width - W) / 2f);
    }

    private float top() {
        return Math.round((height - H) / 2f);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Anim.since(openedAt);
        float open = Anim.outCubic(Anim.window(t, 0.05f, 0.4f));
        renderBackground(g, mouseX, mouseY, partialTick);
        float oldAlpha = Gfx.setAlpha(Math.min(1f, t / 0.12f));
        Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC0040912).draw(g);
        Gfx.setAlpha(oldAlpha);

        float x = left();
        float y = top();
        float cx = x + W / 2f;
        float cy = y + H / 2f;
        int accent = QuestCategory.NPC.color();

        // the line of light the window opens out of
        float line = Anim.outCubic(Anim.window(t, 0f, 0.18f));
        if (open < 0.98f && line > 0) {
            float lw = W * line;
            float fade = 1 - Anim.window(t, 0.25f, 0.45f);
            Shapes.pill(cx - lw / 2f, cy - 0.9f, lw, 1.8f).fill(Gfx.alpha(Gfx.mix(accent, 0xFFFFFFFF, 0.6f), fade))
                    .glow(8, Gfx.alpha(accent, 0.8f * fade)).additive().draw(g);
        }
        if (open <= 0.01f) return;

        float shown = H * open;
        g.flush();
        g.enableScissor(0, Math.max(0, Math.round(cy - shown / 2f) - 16), width, Math.min(height, Math.round(cy + shown / 2f) + 16));
        Ui.panel(g, x, y, W, H, 12, accent, 1f);
        Shapes.box(x, y, W, H).radius(12).fill(0x00000000).sheen(Anim.window(t, 0.2f, 1.0f) * 1.6f - 0.3f, t < 1.1f ? 0.14f : 0f).draw(g);
        drawPortrait(g, t, x, y, mouseX, mouseY, partialTick);
        drawList(g, t, x + LEFT_W + 8, y + 10, W - LEFT_W - 18, H - 20, mouseX, mouseY);
        g.flush();
        g.disableScissor();

        drawFlying(g, x, y);
    }

    /** The villager: portrait over a turning ring, name, job, level pips, rapport, trade button. */
    private void drawPortrait(GuiGraphics g, float t, float x, float y, int mouseX, int mouseY, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int accent = QuestCategory.NPC.color();
        float px = x + LEFT_W / 2f + 2;
        float ringY = y + 104;
        float handed = handedAt < 0 ? 0 : 1 - Anim.outCubic(Anim.progress(handedAt, 1.1f));
        int ringColor = Gfx.mix(accent, SystemTheme.GOLD, handed);

        // inner card behind the villager
        Ui.card(g, x + 8, y + 8, LEFT_W - 8, H - 16, 9, accent, 0.25f);
        float rk = Anim.outCubic(Anim.window(t, 0.15f, 0.7f));
        float pulse = Anim.pulse(2.4f);
        Shapes.circle(px, ringY - 30, 44).fill(0x00000000).glow(30, Gfx.alpha(ringColor, (0.16f + 0.08f * pulse + 0.4f * handed) * rk)).draw(g);
        Shapes.ring(px, ringY, 38 * rk, 0.9f).fill(Gfx.alpha(ringColor, 0.7f)).progress(rk).rotation(-0.25f).glow(3, Gfx.alpha(ringColor, 0.5f)).draw(g);
        Shapes.ring(px, ringY, 44, 0.6f).fill(Gfx.alpha(ringColor, 0.45f * rk)).dashes(32, 0.35f).rotation(Anim.loop(22f)).draw(g);
        Shapes.ring(px, ringY, 31, 0.6f).fill(Gfx.alpha(ringColor, 0.35f * rk)).dashes(12, 0.6f).rotation(-Anim.loop(14f)).draw(g);
        // shockwaves when something was handed in
        for (double[] b : bursts) {
            float u = Anim.progress(b[0], 0.8f);
            if (u <= 0 || u >= 1) continue;
            float e = Anim.outCubic(u);
            Shapes.ring(px, ringY - 30, 12 + 46 * e, Anim.lerp(3f, 0.6f, e)).fill(Gfx.alpha(SystemTheme.GOLD, (1 - u) * (1 - u)))
                    .glow(6, Gfx.alpha(SystemTheme.GOLD, 0.5f * (1 - u))).additive().draw(g);
        }

        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e instanceof LivingEntity living && rk > 0.2f) {
            int x1 = Math.round(px - 40);
            int y1 = Math.round(y + 22);
            g.flush();
            float old = Gfx.fade(Math.min(1f, (rk - 0.2f) * 2));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x1 + 80, Math.round(ringY + 10), 40, 0.0625f, mouseX, mouseY, living);
            Gfx.setAlpha(old);
        }

        // name, job, level
        float ty = y + 128;
        float k = Anim.outCubic(Anim.window(t, 0.3f, 0.65f));
        float old = Gfx.fade(k);
        Component name = text("name");
        SysFont.draw(g, name, px, ty, 12f, SystemTheme.TEXT, true, 0.5f);
        String job = text("job").getString();
        float jw = SysFont.width(job, 6.5f, true) + 10;
        Ui.chip(g, px - jw / 2f, ty + 15, 10.5f, job, 6.5f, accent, true);
        int level = data.getInt("level");
        if (level > 0) {
            for (int i = 0; i < 5; i++) {
                float dx = px + (i - 2) * 8;
                boolean on = i < level;
                Shapes.icon(Icons.DIAMOND, dx, ty + 33, 6.5f).fill(on ? Gfx.mix(accent, 0xFFFFFFFF, 0.4f) : 0x40FFFFFF)
                        .glow(on ? 2 : 0, Gfx.alpha(accent, 0.6f)).draw(g);
            }
        }

        // rapport: the villager's reputation of the player (gossip), -100..+150 in vanilla
        int rep = data.getInt("rep");
        float ry = ty + 44;
        SysFont.spaced(g, Component.translatable("npc.beyondsolo.rapport").getString(), x + 18, ry, 5.8f, SystemTheme.TEXT_SOFT, true, 1.2f, 0f);
        String repText = (rep > 0 ? "+" : "") + rep;
        SysFont.draw(g, repText, x + LEFT_W - 8, ry, 6.5f, rep >= 0 ? SystemTheme.GOOD : SystemTheme.DANGER, true, 1f);
        float frac = Mth.clamp((rep + 30) / 130f, 0f, 1f);
        Ui.bar(g, x + 18, ry + 9, LEFT_W - 26, 2.2f, frac, rep >= 0 ? 0xFF4BBF7A : SystemTheme.HP_A, rep >= 0 ? SystemTheme.GOOD : SystemTheme.HP_B);
        Gfx.setAlpha(old);

        // trade
        tradeBtn = null;
        if (data.getBoolean("canTrade")) {
            float bk = Anim.outCubic(Anim.window(t, 0.45f, 0.8f));
            float o2 = Gfx.fade(bk);
            String trade = Component.translatable("npc.beyondsolo.trade").getString();
            tradeBtn = new float[]{x + 18, y + H - 34, LEFT_W - 26, 14};
            button(g, tradeBtn, trade, Icons.HANDSHAKE, SystemTheme.ACCENT, false, mouseX, mouseY, 0);
            SysFont.draw(g, Component.translatable("npc.beyondsolo.trade_hint").getString(), px, y + H - 16, 5.5f, SystemTheme.TEXT_DIM, false, 0.5f);
            Gfx.setAlpha(o2);
        }
    }

    /** The right side: header, then the cards, scrolled if they don't fit. */
    private void drawList(GuiGraphics g, float t, float x, float y, float w, float h, int mouseX, int mouseY) {
        int accent = QuestCategory.NPC.color();
        double now = Anim.now();
        // header
        float hk = Anim.outCubic(Anim.window(t, 0.25f, 0.6f));
        float old = Gfx.fade(hk);
        Shapes.icon(Icons.CHAT, x + 6, y + 6, 9).fill(Gfx.mix(accent, 0xFFFFFFFF, 0.4f)).glow(3, Gfx.alpha(accent, 0.6f)).draw(g);
        SysFont.spaced(g, Component.translatable("npc.beyondsolo.requests").getString(), x + 15, SysFont.centerY(y, 12, 9f), 9f, SystemTheme.TEXT, true, 2.2f, 0f);
        int held = data.getInt("held");
        int max = data.getInt("max");
        String heldText = Component.translatable("npc.beyondsolo.held", held, max).getString();
        float hw = SysFont.width(heldText, 6f, true) + 10;
        Ui.chip(g, x + w - hw, y + 1, 10, heldText, 6f, held >= max ? SystemTheme.DANGER : SystemTheme.TEXT_SOFT, held >= max);
        long refresh = data.getLong("refresh");
        String clock = Component.translatable("npc.beyondsolo.refresh", QuestText.clock(Math.max(0, refresh - (long) (Anim.since(openedAt) * 20)))).getString();
        Shapes.icon(Icons.HOURGLASS, x + w - hw - 10, y + 6, 7).fill(SystemTheme.TEXT_DIM).draw(g);
        SysFont.draw(g, clock, x + w - hw - 16, SysFont.centerY(y + 1, 10, 6f), 6f, SystemTheme.TEXT_DIM, false, 1f);
        Ui.divider(g, x, y + 16, w, Gfx.alpha(accent, 0.5f));
        Gfx.setAlpha(old);

        float listY = y + 22;
        float listH = h - 22;

        // empty states
        boolean any = false;
        for (Card c : cards.values()) if (c.leaveAt < 0) any = true;
        if (!any) {
            float ek = Anim.outCubic(Anim.window(t, 0.4f, 0.8f));
            float o = Gfx.fade(ek);
            boolean shunned = data.getBoolean("shunned");
            ResourceLocation icon = shunned ? Icons.HEART_BREAK : Icons.MOON;
            int color = shunned ? SystemTheme.DANGER : SystemTheme.TEXT_SOFT;
            Shapes.icon(icon, x + w / 2f, listY + listH / 2f - 14, 22).fill(Gfx.alpha(color, 0.8f)).glow(6, Gfx.alpha(color, 0.3f)).draw(g);
            Component msg = Component.translatable(shunned ? "npc.beyondsolo.shunned" : "npc.beyondsolo.empty", text("name"));
            float ly = listY + listH / 2f + 2;
            for (SysFont.Line l : SysFont.wrap(msg, 7.5f, false, w - 40)) {
                SysFont.draw(g, l, x + w / 2f, ly, color, 0.5f);
                ly += 7.5f * SysFont.LINE;
            }
            Gfx.setAlpha(o);
        }

        // layout: cards stack; each eases towards its slot so the list reflows smoothly
        float cursor = 0;
        int index = 0;
        List<Card> gone = new ArrayList<>();
        for (Card c : cards.values()) {
            float ch = cardHeight(c, w);
            float fold = 1;
            if (c.leaveAt >= 0) {
                float u = Anim.progress(c.leaveAt, 0.35f);
                fold = 1 - Anim.inOutCubic(u);
                if (u >= 1) {
                    gone.add(c);
                    continue;
                }
            }
            float appear = Anim.outCubic(Anim.progress(c.appearAt + (c.appearAt <= openedAt + 0.01 ? 0.22 + index * 0.08 : 0), 0.4f));
            float targetY = cursor;
            if (Float.isNaN(c.y)) c.y = targetY;
            c.y = Anim.approach(c.y, targetY, 14f);
            c.height = ch;
            cursor += (ch + CARD_GAP) * fold * Math.max(appear, c.leaveAt >= 0 ? 1 : 0);
            index++;
        }
        for (Card c : gone) cards.remove(c.key);
        listContentH = cursor;
        float maxScroll = Math.max(0, listContentH - listH);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Anim.approach(scroll, scrollTarget, 16f);

        g.flush();
        g.enableScissor(Math.round(x - 4), Math.round(listY - 2), Math.round(x + w + 4), Math.round(listY + listH + 2));
        index = 0;
        for (Card c : cards.values()) {
            float appear = Anim.outCubic(Anim.progress(c.appearAt + (c.appearAt <= openedAt + 0.01 ? 0.22 + index * 0.08 : 0), 0.4f));
            index++;
            if (appear <= 0) {
                c.button = null;
                continue;
            }
            float fold = c.leaveAt >= 0 ? 1 - Anim.inOutCubic(Anim.progress(c.leaveAt, 0.35f)) : 1;
            float cy = listY + c.y - scroll;
            float o = Gfx.fade(appear * fold);
            g.pose().pushPose();
            float slide = (1 - appear) * 26;
            g.pose().translate(x + slide, cy, 0);
            if (fold < 1) {
                g.pose().translate(w / 2f, 0, 0);
                g.pose().scale(1, fold, 1);
                g.pose().translate(-w / 2f, 0, 0);
            }
            drawCard(g, c, w, mouseX - (x + slide), mouseY - cy, now);
            g.pose().popPose();
            if (c.button != null) {
                c.button = new float[]{c.button[0] + x + slide, c.button[1] + cy, c.button[2], c.button[3]};
                if (c.button[1] < listY || c.button[1] + c.button[3] > listY + listH) c.button = null;
            }
            Gfx.setAlpha(o);
        }
        g.flush();
        g.disableScissor();

        // scroll hint
        if (maxScroll > 1) {
            float bar = listH * listH / listContentH;
            float by = listY + (listH - bar) * (scroll / maxScroll);
            Shapes.pill(x + w + 3, by, 1.6f, bar).fill(Gfx.alpha(accent, 0.6f)).draw(g);
        }
    }

    private float cardHeight(Card c, float w) {
        Quest q = c.quest;
        int desc = Math.min(2, SysFont.wrap(q.description(), 6.5f, false, w - 40).size());
        return 24 + desc * 6.5f * SysFont.LINE + 2 + q.objectives().size() * 11 + 20;
    }

    private static int kindColor(NpcQuestKind kind) {
        if (kind == null) return QuestCategory.NPC.color();
        return switch (kind) {
            case GATHER -> 0xFF8BE28B;
            case HUNT -> 0xFFFF8A65;
            case CLEAR -> 0xFFFF5D6C;
            case ESCORT -> 0xFF5FD0FF;
            case DELIVER -> 0xFFFFD166;
        };
    }

    public static ResourceLocation kindIcon(NpcQuestKind kind) {
        if (kind == null) return Icons.CHAT;
        return switch (kind) {
            case GATHER -> Icons.PACKAGE;
            case HUNT -> Icons.SKULL;
            case CLEAR -> Icons.FLAG;
            case ESCORT -> Icons.PATH;
            case DELIVER -> Icons.SEAL;
        };
    }

    /** One request. Drawn at (0, 0) in a translated pose; mouse given in the same space. */
    private void drawCard(GuiGraphics g, Card c, float w, float mx, float my, double now) {
        Quest q = c.quest;
        NpcQuestKind kind = NpcQuestKind.byTemplate(q.template());
        int color = kindColor(kind);
        boolean ready = c.mine && q.status() == Quest.Status.READY;
        float h = c.height;
        boolean hover = mx >= 0 && mx < w && my >= 0 && my < h && c.actionAt < 0;
        float pulse = ready ? Anim.pulse(1.2f) : 0;
        float act = c.actionAt < 0 ? 0 : 1 - Anim.outCubic(Anim.progress(c.actionAt, 0.6f));
        if (c.mine && c.appearAt > openedAt + 0.05) act = Math.max(act, 1 - Anim.outCubic(Anim.progress(c.appearAt, 1.2f)));
        int edge = ready ? SystemTheme.GOLD : color;

        float lift = hover ? -0.8f : 0;
        g.pose().translate(0, lift, 0);
        Shapes.box(0, 0, w, h).radius(8)
                .fillV(Gfx.mix(SystemTheme.CARD_TOP, Gfx.alpha(edge, 0.5f), (ready ? 0.22f : 0.08f) + 0.3f * act), SystemTheme.CARD_BOTTOM)
                .border(hover || ready ? 1f : 0.75f, Gfx.alpha(edge, (hover ? 0.95f : 0.55f) + 0.3f * pulse), Gfx.alpha(edge, 0.2f))
                .glow(hover ? 6 : 2 + 5 * pulse + 8 * act, Gfx.alpha(edge, (hover ? 0.3f : 0.12f) + 0.25f * pulse + 0.4f * act)).draw(g);
        Shapes.pill(3, 6, 1.8f, h - 12).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.4f), color).glow(3, Gfx.alpha(color, 0.5f)).draw(g);

        // header: kind badge, title, chips
        Ui.iconBadge(g, kindIcon(kind), 15, 12, 7.5f, color);
        String kindName = kind == null ? "" : Component.translatable("npc.beyondsolo.kind." + kind.name().toLowerCase()).getString();
        String rank = String.valueOf("EDCBAS".charAt(Mth.clamp(q.rankTier(), 0, 5)));
        float rw = SysFont.width(rank, 6f, true) + 9;
        float kw = SysFont.width(kindName, 5.8f, true) + 9;
        Ui.chip(g, w - 7 - rw, 6, 10, rank, 6f, SystemTheme.TEXT_SOFT, false);
        Ui.chip(g, w - 7 - rw - 3 - kw, 6, 10, kindName, 5.8f, color, true);
        SysFont.title(g, SysFont.fit(q.title().getString(), 8.5f, true, w - 44 - rw - kw), 26, SysFont.centerY(5, 14, 8.5f), 8.5f, SystemTheme.TEXT);

        float cy = 22;
        List<SysFont.Line> desc = SysFont.wrap(q.description(), 6.5f, false, w - 40);
        for (int i = 0; i < Math.min(2, desc.size()); i++) {
            SysFont.Line l = desc.get(i);
            SysFont.draw(g, l, 26, cy, SystemTheme.TEXT_SOFT, 0f);
            cy += 6.5f * SysFont.LINE;
        }
        cy += 2;

        // objectives
        for (QuestObjective o : q.objectives()) {
            boolean done = c.mine && o.done();
            ResourceLocation icon = done ? Icons.CHECK : QuestText.icon(o.type());
            Shapes.icon(icon, 30, cy + 3.5f, 7).fill(done ? SystemTheme.GOOD : Gfx.mix(color, 0xFFFFFFFF, 0.35f)).draw(g);
            ItemStack stack = o.type() == ObjectiveType.COLLECT ? itemFor(o.target()) : ItemStack.EMPTY;
            float lx = 37;
            if (!stack.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(lx, cy - 1, 0);
                g.pose().scale(0.56f, 0.56f, 1);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
                lx += 11;
            }
            String count = c.mine ? o.progress() + "/" + o.required() : "×" + o.required();
            float cw = SysFont.width(count, 6.5f, true);
            SysFont.text(g, SysFont.fit(o.label().getString(), 7f, false, w - lx - cw - 18), lx, cy, 7f, done ? SystemTheme.GOOD : SystemTheme.TEXT);
            SysFont.draw(g, count, w - 9, cy + 0.4f, 6.5f, done ? SystemTheme.GOOD : SystemTheme.TEXT_SOFT, true, 1f);
            if (c.mine && !done) Ui.bar(g, lx, cy + 8, w - lx - 9, 1.6f, o.fraction(), SystemTheme.MP_A, SystemTheme.MP_B);
            cy += 11;
        }

        // footer: what it pays, how long, the button
        float fy = h - 17;
        Ui.divider(g, 10, fy - 2, w - 20, Gfx.alpha(color, 0.3f));
        String limit = q.timeLimit() > 0 && !c.mine ? QuestText.clock(q.timeLimit()) : "";
        if (c.mine && q.deadline() >= 0 && Minecraft.getInstance().level != null) limit = "";
        String pay = QuestText.rewardsLine(q).getString();
        float bx;
        String label;
        int bcolor;
        ResourceLocation bicon;
        boolean enabled = true;
        if (!c.mine) {
            label = Component.translatable("npc.beyondsolo.accept").getString();
            bcolor = SystemTheme.ACCENT;
            bicon = Icons.CHECK;
            enabled = data.getInt("held") < data.getInt("max");
        } else if (ready) {
            label = Component.translatable("npc.beyondsolo.turn_in").getString();
            bcolor = SystemTheme.GOLD;
            bicon = Icons.HANDSHAKE;
        } else {
            label = Component.translatable("npc.beyondsolo.in_progress", Math.round(q.fraction() * 100)).getString();
            bcolor = SystemTheme.TEXT_DIM;
            bicon = Icons.HOURGLASS;
            enabled = false;
        }
        float bw = SysFont.width(label, 7f, true) + 26;
        bx = w - 8 - bw;
        float[] btn = {bx, fy + 1, bw, 13};
        c.button = enabled && c.actionAt < 0 ? btn : null;
        boolean bh = enabled && mx >= btn[0] && mx < btn[0] + btn[2] && my >= btn[1] && my < btn[1] + btn[3];
        button(g, btn, label, bicon, bcolor, ready || bh, enabled ? (int) (mx + 0) : -999, (int) my, ready ? pulse : 0, bh);
        float tx = 10;
        if (!limit.isEmpty()) {
            Shapes.icon(Icons.HOURGLASS, tx + 3, fy + 7.5f, 6.5f).fill(SystemTheme.TEXT_SOFT).draw(g);
            SysFont.draw(g, limit, tx + 8, SysFont.centerY(fy + 1, 13, 6.2f), 6.2f, SystemTheme.TEXT_SOFT, true, 0f);
            tx += SysFont.width(limit, 6.2f, true) + 14;
        }
        SysFont.draw(g, SysFont.fit(pay, 6.2f, true, bx - tx - 6), tx, SysFont.centerY(fy + 1, 13, 6.2f), 6.2f, SystemTheme.GOLD, true, 0f);

        // the ACCEPTED stamp slamming onto a card that was just taken
        if (c.actionAt >= 0 && !c.mine) {
            float u = Anim.window(Anim.since(c.actionAt), 0f, 0.22f);
            float s = Anim.lerp(2.4f, 1f, Anim.inCubic(u));
            String stamp = Component.translatable("npc.beyondsolo.accepted").getString();
            float sw = SysFont.spacedWidth(stamp, 11f, true, 2.4f) + 22;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f, 0);
            g.pose().scale(s, s, 1);
            float o = Gfx.fade(Math.min(1, u * 1.4f));
            Shapes.box(-sw / 2f, -10, sw, 20).radius(5).fill(Gfx.alpha(0xFF0A1A30, 0.85f)).border(1.4f, SystemTheme.ACCENT_BRIGHT)
                    .glow(8, Gfx.alpha(SystemTheme.ACCENT, 0.7f)).draw(g);
            SysFont.spaced(g, stamp, 0, SysFont.centerY(-10, 20, 11f), 11f, SystemTheme.ACCENT_BRIGHT, true, 2.4f, 0.5f);
            Gfx.setAlpha(o);
            g.pose().popPose();
        }
        g.pose().translate(0, -lift, 0);
    }

    private static ItemStack itemFor(String target) {
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            if (id == null) return ItemStack.EMPTY;
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) return new ItemStack(holder);
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static void button(GuiGraphics g, float[] r, String text, ResourceLocation icon, int color, boolean filled, int mouseX, int mouseY, float pulse) {
        button(g, r, text, icon, color, filled, mouseX, mouseY, pulse, Ui.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]));
    }

    private static void button(GuiGraphics g, float[] r, String text, ResourceLocation icon, int color, boolean filled, int mouseX, int mouseY, float pulse, boolean hover) {
        Shapes.pill(r[0], r[1], r[2], r[3]).fillV(Gfx.alpha(color, (filled ? 0.36f : 0.08f) + (hover ? 0.16f : 0) + 0.12f * pulse), Gfx.alpha(color, filled ? 0.16f : 0.04f))
                .border(0.9f, Gfx.alpha(color, hover ? 1f : 0.7f + 0.3f * pulse)).glow((hover ? 6 : 2) + 4 * pulse, Gfx.alpha(color, (hover ? 0.4f : 0.15f) + 0.25f * pulse))
                .sheen(filled ? Anim.loop(2.6f) * 1.6f - 0.3f : -1, 0.2f).draw(g);
        float tw = SysFont.width(text, 7f, true);
        float sx = r[0] + (r[2] - tw - 10) / 2f;
        Shapes.icon(icon, sx + 3.5f, r[1] + r[3] / 2f, 7).fill(hover ? 0xFFFFFFFF : Gfx.mix(color, 0xFFFFFFFF, 0.45f)).draw(g);
        SysFont.draw(g, text, sx + 10, SysFont.centerY(r[1], r[3], 7f), 7f, hover ? 0xFFFFFFFF : Gfx.mix(color, 0xFFFFFFFF, 0.45f), true, 0f);
    }

    /** Items (or a seal) flying from a handed-in card into the villager. */
    private void drawFlying(GuiGraphics g, float x, float y) {
        float tx = x + LEFT_W / 2f + 2;
        float ty = y + 72;
        boolean landed = false;
        for (Fly f : flying) {
            float u = Anim.progress(f.startAt, 0.6f);
            if (u <= 0) continue;
            if (u >= 1) {
                landed = true;
                continue;
            }
            float e = Anim.inOutCubic(u);
            float px = Anim.lerp(f.fromX, tx, e);
            float py = Anim.lerp(f.fromY, ty, e) - (float) Math.sin(u * Math.PI) * f.arc;
            float s = Anim.lerp(1.1f, 0.6f, e);
            for (int i = 1; i <= 4; i++) {
                float tu = Math.max(0, u - i * 0.035f);
                float te = Anim.inOutCubic(tu);
                float qx = Anim.lerp(f.fromX, tx, te);
                float qy = Anim.lerp(f.fromY, ty, te) - (float) Math.sin(tu * Math.PI) * f.arc;
                Shapes.circle(qx, qy, 2.4f - i * 0.4f).fill(Gfx.alpha(0xFFFFF4D0, 0.5f - i * 0.1f)).glow(4, Gfx.alpha(SystemTheme.GOLD, 0.4f - i * 0.08f)).additive().draw(g);
            }
            Shapes.circle(px, py, 9 * s).fill(0x00000000).glow(10, Gfx.alpha(SystemTheme.GOLD, 0.45f)).additive().draw(g);
            if (!f.stack.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(px - 8 * s, py - 8 * s, 50);
                g.pose().scale(s, s, 1);
                g.renderItem(f.stack, 0, 0);
                g.pose().popPose();
            } else {
                Shapes.icon(f.icon, px, py, 14 * s).fill(0xFFFFF0C8).glow(3, Gfx.alpha(SystemTheme.GOLD, 0.8f)).draw(g);
            }
        }
        if (landed && handedAt < 0) {
            handedAt = Anim.now();
            bursts.add(new double[]{handedAt});
            bursts.add(new double[]{handedAt + 0.12});
            sound(SoundEvents.PLAYER_LEVELUP, 1.7f, 0.4f);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closeAt >= 0) return true;
        if (tradeBtn != null && Ui.inside(mouseX, mouseY, tradeBtn[0], tradeBtn[1], tradeBtn[2], tradeBtn[3])) {
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f);
            PacketDistributor.sendToServer(new NpcActionPayload(NpcService.ACTION_TRADE, entityId, 0, NONE));
            return true;
        }
        for (Card c : cards.values()) {
            float[] b = c.button;
            if (b == null || c.leaveAt >= 0 || !Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) continue;
            c.actionAt = Anim.now();
            if (!c.mine) {
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.4f);
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.7f);
                PacketDistributor.sendToServer(new NpcActionPayload(NpcService.ACTION_ACCEPT, entityId, c.slot, NONE));
            } else {
                handIn(c, b);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handIn(Card c, float[] b) {
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.4f);
        sound(SoundEvents.BUNDLE_INSERT, 0.9f, 0.9f);
        double now = Anim.now();
        int i = 0;
        for (QuestObjective o : c.quest.objectives()) {
            if (o.type() != ObjectiveType.COLLECT || !o.consume()) continue;
            ItemStack s = itemFor(o.target());
            if (s.isEmpty()) continue;
            for (int k = 0; k < Math.min(4, Math.max(1, o.required() / 4)); k++) {
                flying.add(new Fly(s, null, b[0] + b[2] / 2f - 30 + (i * 13 % 50), b[1] - 10 - (k * 7 % 18), now + i * 0.07, 30 + (i * 11 % 25)));
                i++;
            }
        }
        if (i == 0) flying.add(new Fly(ItemStack.EMPTY, Icons.SEAL, b[0] + b[2] / 2f, b[1], now, 36));
        closeAt = now + 1.45;
        PacketDistributor.sendToServer(new NpcActionPayload(NpcService.ACTION_TURN_IN, entityId, 0, c.quest.id()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget -= (float) scrollY * 24;
        return true;
    }

    /** Dev (UI test driver): presses the first card's button, or the trade button. */
    public static void press(String what) {
        if (!(Minecraft.getInstance().screen instanceof NpcBoardScreen s)) return;
        for (Card c : s.cards.values()) {
            if (c.button == null || c.leaveAt >= 0) continue;
            boolean ready = c.mine && c.quest.status() == Quest.Status.READY;
            if (what.equals("accept") && !c.mine || what.equals("turnin") && ready) {
                s.mouseClicked(c.button[0] + 2, c.button[1] + 2, 0);
                return;
            }
        }
    }

    /** Dev: scrolls the list. */
    public static void scrollBy(float amount) {
        if (Minecraft.getInstance().screen instanceof NpcBoardScreen s) s.scrollTarget += amount;
    }
}
