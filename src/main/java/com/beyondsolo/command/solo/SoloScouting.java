package com.beyondsolo.command.solo;

import com.beyondsolo.news.Headline;
import com.beyondsolo.news.HunterNews;
import com.beyondsolo.news.NewsData;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.scouting.GuildTerms;
import com.beyondsolo.scouting.ScoutEntity;
import com.beyondsolo.scouting.ScoutRecord;
import com.beyondsolo.scouting.Scouting;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The great guilds' scouts and contracts ({@code scouting.Scouting}) and the hunter news ({@code news.HunterNews}):
 * <pre>
 * /solo scout offer &lt;guild&gt;|all [players]   that guild's offer (or every guild's that wants the rank) on the table now
 * /solo scout send [players]                 a scout walks up with the best offer on the table
 * /solo scout talk [player]                  the player speaks to the nearest of their scouts (their offer's window)
 * /solo scout lapse [players]                every offer on the table runs out now
 * /solo scout list [player]                  the offers on the table and on their way
 * /solo scout reset [players]                offers, contract and record forgotten; the scouts go home
 * /solo contract sign &lt;guild&gt; [players]      signed with the guild now, for its usual bonus
 * /solo contract expire [players]            the contract runs its course now (the guild asks to renew)
 * /solo contract end [players]               the contract ends now, nothing owed
 * /solo contract info [player]               the contract and the player's dealings with the guilds
 * /solo news ambient [count]                 headlines about the world's other hunters
 * /solo news rankup [player]                 the player's rank in the news, as a rank-up announces it
 * /solo news list                            the last headlines
 * /solo news clear                           every headline forgotten
 * </pre>
 */
final class SoloScouting {
    /** The great guilds a command can name. */
    static final List<String> GUILDS = GuildTerms.ALL.stream().map(t -> t.guild().key()).toList();

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> offer = Commands.literal("offer");
        for (String guild : GUILDS) offer.then(Solo.targets(Commands.literal(guild), (ctx, p) -> offer(p, guild)));
        offer.then(Solo.targets(Commands.literal("all"), (ctx, p) -> offer(p, null)));
        root.then(Commands.literal("scout")
                .then(offer)
                .then(Solo.targets(Commands.literal("send"), (ctx, p) -> send(p)))
                .then(Solo.target(Commands.literal("talk"), (ctx, p) -> talk(p)))
                .then(Solo.targets(Commands.literal("lapse"), (ctx, p) -> Solo.t("scout.lapsed", p.getDisplayName(), Scouting.lapseAll(p))))
                .then(Solo.target(Commands.literal("list"), SoloScouting::list))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    Scouting.reset(p);
                    return Solo.t("scout.reset", p.getDisplayName());
                })));

        LiteralArgumentBuilder<CommandSourceStack> sign = Commands.literal("sign");
        for (String guild : GUILDS) sign.then(Solo.targets(Commands.literal(guild), (ctx, p) -> sign(p, guild)));
        root.then(Commands.literal("contract")
                .then(sign)
                .then(Solo.targets(Commands.literal("expire"), (ctx, p) -> {
                    if (!Scouting.expire(p)) throw Solo.error("no_guild_contract", p.getDisplayName());
                    return Solo.t("contract.expired", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("end"), (ctx, p) -> {
                    ScoutRecord.Contract c = Scouting.record(p).contract();
                    if (c == null) throw Solo.error("no_guild_contract", p.getDisplayName());
                    Scouting.endContract(p, Scouting.End.COMMAND);
                    return Solo.t("contract.ended", p.getDisplayName(), c.guild().displayName());
                }))
                .then(Solo.target(Commands.literal("info"), SoloScouting::info)));

        root.then(Commands.literal("news")
                .then(Commands.literal("ambient")
                        .executes(ctx -> ambient(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> ambient(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Solo.target(Commands.literal("rankup"), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    PlayerRank rank = SystemAPI.get(p).rank();
                    if (rank.tier() < PlayerRank.C.tier()) throw Solo.error("news_rank", p.getDisplayName(), rank.displayName());
                    HunterNews.rankUp(p, rank);
                    return Solo.t("news.posted", 1);
                }))
                .then(Commands.literal("list").executes(SoloScouting::news))
                .then(Commands.literal("clear").executes(ctx -> {
                    HunterNews.clear(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Solo.t("news.cleared"), true);
                    return 1;
                })));
    }

    private static GuildTerms terms(String guild) {
        for (GuildTerms t : GuildTerms.ALL) if (t.guild().key().equals(guild)) return t;
        throw new IllegalArgumentException(guild);
    }

    private static Component offer(ServerPlayer p, String guild) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        List<GuildTerms> which = new ArrayList<>();
        if (guild != null) which.add(terms(guild));
        else which.addAll(GuildTerms.interested(SystemAPI.get(p).rank()));
        if (which.isEmpty()) throw Solo.error("no_guild_wants", p.getDisplayName(), SystemAPI.get(p).rank().displayName());
        for (GuildTerms t : which) Scouting.offerNow(p, t);
        Scouting.tick(p);
        return Solo.t("scout.offered", p.getDisplayName(), which.size());
    }

    private static Component send(ServerPlayer p) throws CommandSyntaxException {
        ScoutRecord r = Scouting.record(p);
        long now = p.server.overworld().getGameTime();
        ScoutRecord.Offer best = null;
        for (ScoutRecord.Offer o : r.open(now)) if (best == null || o.bonus > best.bonus) best = o;
        if (best == null) throw Solo.error("no_offer", p.getDisplayName());
        if (Scouting.sendScout(p, best) == null) throw Solo.error("no_scout_spot");
        best.visited = true;
        return Solo.t("scout.sent", best.scout, best.guild.displayName(), p.getDisplayName());
    }

    /** The player speaks to the nearest of their scouts (as right-clicking them does). */
    private static Component talk(ServerPlayer p) throws CommandSyntaxException {
        ScoutEntity best = null;
        double bestD = 48 * 48;
        for (ScoutEntity e : p.serverLevel().getEntitiesOfClass(ScoutEntity.class, p.getBoundingBox().inflate(48))) {
            double d = e.distanceToSqr(p);
            if (p.getUUID().equals(e.hunter()) && d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) throw Solo.error("no_scout", p.getDisplayName());
        Scouting.talk(p, best);
        return null;
    }

    private static Component list(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        ScoutRecord r = Scouting.record(p);
        if (r.offers().isEmpty()) return Solo.t("scout.none", p.getDisplayName());
        long now = p.server.overworld().getGameTime();
        for (ScoutRecord.Offer o : r.offers()) {
            Component when = o.open(now) ? Solo.t("scout.open", clock(o.lapseAt - now)) : Solo.t("scout.coming", clock(o.openAt - now));
            ctx.getSource().sendSuccess(() -> Solo.t("scout.line", o.guild.displayName(), o.rank.displayName(), gold(o.bonus), gold(o.ceiling),
                    o.raises, o.last ? Solo.t("scout.final") : Component.empty(), when), false);
        }
        return null;
    }

    private static Component sign(ServerPlayer p, String guild) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        GuildTerms t = terms(guild);
        ScoutRecord.Offer o = Scouting.offerNow(p, t);
        long bonus = o.bonus;
        Scouting.sign(p, o.id);
        if (!Scouting.record(p).contractedTo(t.guild())) throw Solo.error("not_signed", p.getDisplayName(), t.guild().displayName());
        return Solo.t("contract.signed", p.getDisplayName(), t.guild().displayName(), gold(bonus));
    }

    private static Component info(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        ScoutRecord r = Scouting.record(p);
        ScoutRecord.Contract c = r.contract();
        MinecraftServer server = p.server;
        long day = server.overworld().getDayTime() / 24000L;
        if (c == null) ctx.getSource().sendSuccess(() -> Solo.t("contract.free", p.getDisplayName()), false);
        else ctx.getSource().sendSuccess(() -> Solo.t("contract.line", p.getDisplayName(), c.guild().displayName(), gold(c.bonus()),
                Math.round(c.cut() * 100), gold(r.paid()), c.daysLeft(day), gold(Scouting.owed(c, day))), false);
        return Solo.t("contract.record", r.signed(), r.declined(), r.raised(), r.lapsed(), r.walked(), gold(r.best()));
    }

    private static int ambient(CommandContext<CommandSourceStack> ctx, int count) {
        MinecraftServer server = ctx.getSource().getServer();
        for (int i = 0; i < count; i++) HunterNews.ambient(server, server.overworld().getRandom());
        ctx.getSource().sendSuccess(() -> Solo.t("news.posted", count), true);
        return count;
    }

    private static int news(CommandContext<CommandSourceStack> ctx) {
        List<Headline> all = NewsData.get(ctx.getSource().getServer()).headlines();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Solo.t("news.none"), false);
            return 0;
        }
        for (int i = Math.max(0, all.size() - 10); i < all.size(); i++) {
            Headline h = all.get(i);
            Component text = Component.Serializer.fromJson(h.text(), ctx.getSource().registryAccess());
            ctx.getSource().sendSuccess(() -> Solo.t("news.line", h.day() + 1, h.kind(), h.breaking() ? Solo.t("news.breaking") : Component.empty(),
                    text == null ? Component.empty() : text), false);
        }
        return all.size();
    }

    static String gold(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount);
    }

    static String clock(long ticks) {
        long s = Math.max(0, ticks / 20);
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private SoloScouting() {}
}
