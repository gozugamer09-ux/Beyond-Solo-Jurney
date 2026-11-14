package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Placeholder QUESTS page: shows the current objective. The quest engine replaces this tab. */
public class QuestsTab implements SystemTab {
    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.quests");
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        SystemData d = ClientSystemData.get();
        String objective = d.objective();
        float listTop = y;
        if (!objective.isEmpty()) {
            var lines = SysFont.wrap(Objectives.description(objective), 8.5f, false, w - 62);
            float cardH = Math.max(52, 34 + lines.size() * 8.5f * SysFont.LINE + 8);
            float pulse = Anim.pulse(2.4f);
            Ui.card(g, x, y, w, cardH, 9, SystemTheme.GOLD, 0.4f + 0.1f * pulse);
            // marker with a turning ring
            float ix = x + 26;
            float iy = y + cardH / 2f;
            Shapes.ring(ix, iy, 17.5f, 1f).fill(Gfx.alpha(SystemTheme.GOLD, 0.75f)).dashes(14, 0.5f).rotation(Anim.loop(16f)).draw(g);
            Shapes.circle(ix, iy, 13).fillV(Gfx.alpha(SystemTheme.GOLD, 0.3f), Gfx.alpha(SystemTheme.GOLD, 0.1f)).border(0.75f, Gfx.alpha(SystemTheme.GOLD, 0.8f))
                    .glow(6 + pulse * 3, Gfx.alpha(SystemTheme.GOLD, 0.25f)).draw(g);
            Shapes.icon(Icons.PIN, ix, iy, 15).fill(0xFFFFF0C8).glow(3, Gfx.alpha(SystemTheme.GOLD, 0.7f)).draw(g);

            float tx = x + 52;
            SysFont.spaced(g, Component.translatable("quests.beyondsolo.current").getString(), tx, y + 9, 6.5f, SystemTheme.GOLD, true, 1.3f, 0f);
            SysFont.title(g, Objectives.title(objective), tx, y + 19, 12, SystemTheme.TEXT);
            float ly = y + 34;
            for (SysFont.Line line : lines) {
                SysFont.draw(g, line, tx, ly, SystemTheme.TEXT_SOFT, 0f);
                ly += 8.5f * SysFont.LINE;
            }
            listTop = y + cardH + 10;
        }

        // empty state for the quest list
        float cy = listTop + (y + h - listTop) / 2f - 6;
        Shapes.icon(Icons.of(com.beyondsolo.notify.SystemNotification.Kind.QUEST), x + w / 2f, cy - 8, 22).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.55f)).draw(g);
        SysFont.draw(g, Component.translatable("quests.beyondsolo.none"), x + w / 2f, cy + 10, 8.5f, SystemTheme.TEXT_DIM, false, 0.5f);
    }
}
