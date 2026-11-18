package com.beyondsolo.chronicle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalCategory;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.title.Feat;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /system chronicle ... testing commands (permission level 2):
 * <ul>
 *   <li>title grant|revoke|equip &lt;id&gt; ({@code all} for every title), title check</li>
 *   <li>count &lt;feat&gt; &lt;value&gt; (sets one the chronicle counts itself)</li>
 *   <li>journal reveal &lt;all|creatures|bosses|realms|arsenal|page id&gt;, journal clear</li>
 *   <li>rankings (prints the hunters' board and sends the boards), info, reset</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ChronicleCommand {
    private static final SimpleCommandExceptionType NO_TITLE = new SimpleCommandExceptionType(Component.literal("No such title"));
    private static final SimpleCommandExceptionType NO_FEAT = new SimpleCommandExceptionType(Component.literal("No such feat (or not one the chronicle counts)"));
    private static final SimpleCommandExceptionType NO_PAGE = new SimpleCommandExceptionType(Component.literal("No such page"));

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        List<String> titles = new ArrayList<>();
        titles.add("all");
        for (TitleDef t : Titles.all()) titles.add(t.id());
        List<String> feats = new ArrayList<>();
        for (Feat f : Feat.values()) if (f.counted()) feats.add(f.id());
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("chronicle")
                        .then(Commands.literal("title")
                                .then(Commands.literal("grant").then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titles, b))
                                        .executes(ctx -> grant(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("revoke").then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titles, b))
                                        .executes(ctx -> revoke(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("equip").then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titles, b))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            TitleDef t = Titles.byId(StringArgumentType.getString(ctx, "id"));
                                            if (t == null) throw NO_TITLE.create();
                                            return TitleService.equip(p, t.key(), true) ? 1 : 0;
                                        })))
                                .then(Commands.literal("check").executes(ctx -> TitleService.check(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("count").then(Commands.argument("feat", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(feats, b))
                                .then(Commands.argument("value", LongArgumentType.longArg(0))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            Feat f = Feat.byId(StringArgumentType.getString(ctx, "feat"));
                                            if (f == null || !f.counted()) throw NO_FEAT.create();
                                            ChronicleModule.data(p).set(f.id(), LongArgumentType.getLong(ctx, "value"));
                                            TitleService.check(p);
                                            TitleService.sync(p);
                                            return 1;
                                        }))))
                        .then(Commands.literal("journal")
                                .then(Commands.literal("reveal").then(Commands.argument("what", StringArgumentType.greedyString())
                                        .executes(ctx -> reveal(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "what")))))
                                .then(Commands.literal("clear").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ChronicleData c = ChronicleModule.data(p);
                                    for (JournalEntry e : Journal.all()) c.forget(e.id());
                                    TitleService.sync(p);
                                    return 1;
                                })))
                        .then(Commands.literal("rankings").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Rankings.send(p);
                            List<Rankings.Row> rows = Rankings.hunters(p.server);
                            for (int i = 0; i < rows.size(); i++) {
                                Rankings.Row r = rows.get(i);
                                String line = "#" + (i + 1) + " " + r.name() + " [" + r.rank() + "] " + r.value() + (r.rival() ? "" : " (player)");
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return rows.size();
                        }))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ChronicleData c = ChronicleModule.data(p);
                            String line = "titles " + c.titles().size() + "/" + Titles.all().size() + ", worn '" + SystemAPI.get(p).title()
                                    + "', pages " + c.pages() + "/" + Journal.size() + ", counters " + c.counters()
                                    + ", place #" + Rankings.place(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            return c.titles().size();
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ChronicleModule.data(p).reset();
                            ChronicleModule.data(p).setMigrated();
                            SystemAPI.equipTitle(p, "");
                            TitleService.applyPerks(p);
                            TitleService.sync(p);
                            return 1;
                        }))));
    }

    private static int grant(ServerPlayer p, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (id.equals("all")) {
            int n = 0;
            for (TitleDef t : Titles.all()) if (TitleService.grant(p, t.key())) n++;
            return n;
        }
        TitleDef t = Titles.byId(id);
        if (t == null) throw NO_TITLE.create();
        return TitleService.grant(p, t.key()) ? 1 : 0;
    }

    private static int revoke(ServerPlayer p, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (id.equals("all")) {
            int n = 0;
            for (String key : new ArrayList<>(ChronicleModule.data(p).titles().keySet())) if (TitleService.revoke(p, key)) n++;
            return n;
        }
        TitleDef t = Titles.byId(id);
        if (t == null) throw NO_TITLE.create();
        return TitleService.revoke(p, t.key()) ? 1 : 0;
    }

    /** Fills pages in: all, a section, or one page (with a kill, and every form of a boss). */
    private static int reveal(ServerPlayer p, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ChronicleData c = ChronicleModule.data(p);
        long now = p.serverLevel().getGameTime();
        List<JournalEntry> pages = new ArrayList<>();
        String w = what.trim().toLowerCase(Locale.ROOT);
        if (w.equals("all")) {
            pages.addAll(Journal.all());
        } else {
            JournalCategory cat = null;
            for (JournalCategory jc : JournalCategory.values()) if (jc.id().equals(w)) cat = jc;
            if (cat != null) pages.addAll(Journal.of(cat));
            else if (Journal.get(w) != null) pages.add(Journal.get(w));
            else throw NO_PAGE.create();
        }
        for (JournalEntry e : pages) {
            c.see(e.id(), now);
            if (e.category() == JournalCategory.CREATURES || e.category() == JournalCategory.BOSSES) c.kill(e.id(), now);
            if (e.boss() != null) {
                for (PlayerRank r : PlayerRank.values()) if (e.boss().livesAt(r)) c.addForm(e.id(), JournalEntry.formBit(r), now);
                c.addForm(e.id(), JournalEntry.ASCENDED_BIT, now);
            }
        }
        TitleService.sync(p);
        return pages.size();
    }

    private ChronicleCommand() {}
}
