package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
        if (objective.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("quests.beyondsolo.none"), x + w / 2, y + h / 2 - 4, SystemTheme.TEXT_DIM);
            return;
        }
        SystemTheme.frame(g, x, y, w, 60, 0xFFFFD54F, 200);
        g.drawString(font, Component.translatable("quests.beyondsolo.current"), x + 6, y + 6, 0xFFFFD54F, false);
        g.drawString(font, Objectives.title(objective), x + 6, y + 20, SystemTheme.TEXT, false);
        int ly = y + 33;
        for (FormattedCharSequence line : font.split(Objectives.description(objective), w - 12)) {
            g.drawString(font, line, x + 6, ly, SystemTheme.TEXT_DIM, false);
            ly += 10;
        }
    }
}
