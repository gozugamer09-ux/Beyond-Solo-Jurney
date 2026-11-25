package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The CHRONICLE page of the System window: the hunter's record in four sections. TITLES (every title, how far along
 * each one is, and the one worn), RANKINGS (the world's hunters, the Citadel, the boss rush and gates cleared), the
 * JOURNAL (the creatures, bosses, realms and weapons met so far) and the NEWS (the hunter news, {@code news.HunterNews}).
 * The section open last is opened again.
 */
public class ChronicleTab implements SystemTab {
    public enum Section {
        TITLES(Icons.CROWN), RANKINGS(ChronicleIcons.RANKINGS), JOURNAL(ChronicleIcons.JOURNAL), NEWS(Icons.BELL);

        final ResourceLocation icon;

        Section(ResourceLocation icon) {
            this.icon = icon;
        }

        String label() {
            return Component.translatable("chronicle.beyondsolo.section." + name().toLowerCase(Locale.ROOT)).getString();
        }
    }

    /** One section's content. */
    interface Page {
        default void opened() {}

        void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY);

        boolean click(double mouseX, double mouseY, int button);

        default boolean scroll(double mouseX, double mouseY, double amount) {
            return false;
        }
    }

    static final float HEAD = 15;
    private static Section section = Section.TITLES;
    private static Section previous = Section.TITLES;
    private static double switchedAt = -10;

    private final TitlesPage titles = new TitlesPage();
    private final RankingsPage rankings = new RankingsPage();
    private final JournalPage journal = new JournalPage();
    private final NewsPage news = new NewsPage();
    private final List<ChronicleUi.Hit> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("screen.beyondsolo.tab.chronicle");
    }

    @Override
    public boolean batched() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    @Override
    public void onOpened() {
        page(section).opened();
    }

    private Page page(Section s) {
        return switch (s) {
            case TITLES -> titles;
            case RANKINGS -> rankings;
            case JOURNAL -> journal;
            case NEWS -> news;
        };
    }

    /** Opens a section (the driver and the status window use it too). */
    public static void open(Section s) {
        if (s == section) return;
        previous = section;
        section = s;
        switchedAt = Anim.now();
    }

    public static Section section() {
        return section;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        float px = x;
        for (Section s : Section.values()) {
            boolean sel = s == section;
            String label = s.label();
            float pw = ChronicleUi.pillWidth(HEAD - 2, label, true, 6f);
            boolean hover = !sel && Ui.inside(mouseX, mouseY, px, y, pw, HEAD - 2);
            ChronicleUi.pill(g, px, y, HEAD - 2, label, s.icon, SystemTheme.ACCENT, sel, hover, 6f);
            if (fresh(s)) {
                float dx = px + pw - 2.5f, dy = y + 1.5f;
                Shapes.circle(dx, dy, 2.2f).fill(SystemTheme.GOLD).glow(2.5f, Gfx.alpha(SystemTheme.GOLD, 0.4f + 0.3f * Anim.pulse(1.4f))).draw(g);
            }
            Section target = s;
            hits.add(new ChronicleUi.Hit(px, y, pw, HEAD - 2, () -> {
                if (target != section) {
                    open(target);
                    page(target).opened();
                    SystemScreen.playClick();
                }
            }));
            px += pw + 4;
        }
        // what the section holds, on the right
        String summary = summary();
        float room = x + w - px - 6;
        if (room > 30) {
            SysFont.draw(g, SysFont.fit(summary, 5.6f, false, room), x + w - 2, SysFont.centerY(y, HEAD - 2, 5.6f), 5.6f, SystemTheme.TEXT_SOFT, false, 1f);
        }

        float top = y + HEAD + 3;
        float t = Anim.outCubic(Anim.progress(switchedAt, 0.22f));
        float dir = Integer.compare(section.ordinal(), previous.ordinal());
        float old = Gfx.fade(Math.max(0.05f, t));
        g.pose().pushPose();
        g.pose().translate(dir * (1 - t) * 8f, 0, 0);
        page(section).render(g, x, top, w, y + h - top, mouseX, mouseY);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private static boolean fresh(Section s) {
        if (s == Section.RANKINGS) return false;
        if (s == Section.NEWS) return section != Section.NEWS && com.beyondsolo.client.news.NewsClient.unread() > 0;
        for (String id : ChronicleClient.data().fresh()) {
            boolean title = id.startsWith(TitleDef.PREFIX);
            if ((s == Section.TITLES) == title && ChronicleClient.isFresh(id)) return true;
        }
        return false;
    }

    private static String summary() {
        return switch (section) {
            case TITLES -> {
                int earned = 0;
                for (TitleDef d : Titles.all()) if (ChronicleClient.data().hasTitle(d.key())) earned++;
                yield Component.translatable("chronicle.beyondsolo.summary.titles", earned, Titles.all().size()).getString();
            }
            case RANKINGS -> {
                int[] place = RankingsPage.myPlace();
                yield place[0] > 0 ? Component.translatable("chronicle.beyondsolo.summary.rankings", place[0], place[1]).getString()
                        : Component.translatable("chronicle.beyondsolo.summary.rankings_none").getString();
            }
            case JOURNAL -> Component.translatable("chronicle.beyondsolo.summary.journal", ChronicleClient.data().pages(), Journal.size()).getString();
            case NEWS -> Component.translatable("news.beyondsolo.page.summary", com.beyondsolo.client.news.NewsClient.headlines().size()).getString();
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ChronicleUi.click(hits, mouseX, mouseY)) return true;
        return page(section).click(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return page(section).scroll(mouseX, mouseY, scrollY);
    }

    // ------------------------------------------------------------------ UI test driver

    /**
     * {@code chronicle titles [title id]}, {@code chronicle rankings [hunters|citadel|rush|gates] [tier]},
     * {@code chronicle journal [creatures|bosses|realms|arsenal] [page id]}, {@code chronicle news}.
     */
    public static void show(String arg) {
        String[] a = arg.trim().split(" ");
        switch (a[0]) {
            case "titles" -> {
                open(Section.TITLES);
                if (a.length > 1) TitlesPage.show(a[1]);
            }
            case "rankings" -> {
                open(Section.RANKINGS);
                if (a.length > 1) RankingsPage.show(a[1], a.length > 2 ? a[2] : "");
            }
            case "journal" -> {
                open(Section.JOURNAL);
                if (a.length > 1) JournalPage.show(a[1], a.length > 2 ? a[2] : "");
            }
            case "news" -> open(Section.NEWS);
            default -> {}
        }
        switchedAt = -10;
    }
}
