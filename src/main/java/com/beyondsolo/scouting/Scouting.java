package com.beyondsolo.scouting;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.network.AssociationSyncPayload;
import com.beyondsolo.news.HunterNews;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The great guilds come for a hunter whose rank goes up. From rank C the hungriest of them, Red Lantern, sends a scout;
 * from B Iron Bastion, from A Sunfall too, and an S-rank has all four at the door, Azure Tide among them
 * ({@link GuildTerms}). Each guild's offer comes a little after the last one's ({@code scouting.firstScoutSeconds},
 * {@code scouting.scoutGapSeconds}): a scout walks up to the hunter wherever they are in the world, or, if they are in a
 * gate, word of it waits for them at the association. An offer is a signing bonus for a contract of
 * {@code scouting.contractWorldDays} days, the guild taking a share of the hunter's gate gold meanwhile, with a perk of its
 * own. It stays on the table for {@code scouting.offerMinutes}.
 *
 * <p><b>The bidding war.</b> A hunter who holds out for more makes the guild think again: with a better offer from
 * another guild on the table it beats it (up to what it will go to at most, {@code scouting.ceiling}); with none, it
 * raises a little and may walk away instead (more likely each time). Turning one guild down sends the others' offers
 * up ({@code scouting.declineRaise}); signing with one has the rest withdraw theirs. Turning every great guild down
 * makes a hunter Not for Sale.</p>
 *
 * <p><b>The contract.</b> The guild takes its share of gate gold; its parties ({@code rival.RivalParties}) stand down
 * for its own hunter and let them into the gates they hold; its perk applies. When it runs its course the guild asks to
 * renew; a hunter may walk out at the office by paying back what is left of the bonus, and another guild that signs
 * them buys them out.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Scouting {
    public static final String KEY = "scouting.beyondsolo.";
    /** Ticks after a rank-up before it makes the news (the evaluation's reveal plays first). */
    static final int NEWS_DELAY = 20 * 12;
    /** How a contract ended. */
    public enum End { RAN_OUT, BOUGHT_OUT, WALKED_OUT, COMMAND }

    static final List<String> SCOUTS = List.of("Celia Moran", "Damon Yeo", "Ilse Verhoef", "Joon Baek", "Marisol Vega", "Felix Arden",
            "Hana Ito", "Rafael Duarte", "Greta Lund", "Owen Sterling", "Mi-rae Shin", "Tomas Kral", "Nadia Farouk", "Elliot Price",
            "Sora Kim", "Lena Hartmann");

    public static ScoutRecord record(Player p) {
        return p.getData(ScoutingModule.RECORD);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    // ================================================================ a rank-up

    @SubscribeEvent
    static void onRankChanged(SystemEvent.RankChanged event) {
        if (event.getNewRank().tier() <= event.getOldRank().tier()) return;
        ServerPlayer p = event.getPlayer();
        if (!SystemAPI.isAwakened(p)) return;
        PlayerRank rank = event.getNewRank();
        ServerScheduler.schedule(NEWS_DELAY, () -> {
            if (!p.isRemoved() || p instanceof FakePlayer) HunterNews.rankUp(p, rank);
        });
        scout(p, rank);
    }

    @SubscribeEvent
    static void onAwakened(SystemEvent.Awakened event) {
        ServerPlayer p = event.getPlayer();
        PlayerRank rank = event.getRank();
        if (rank.tier() < PlayerRank.C.tier()) return;
        ServerScheduler.schedule(NEWS_DELAY, () -> {
            if (!p.isRemoved() || p instanceof FakePlayer) HunterNews.awakened(p, rank);
        });
        scout(p, rank);
    }

    /**
     * The great guilds that want a hunter of this rank (and haven't yet come for them at it) put their offers on the
     * way, one after another. Returns the new offers.
     */
    public static List<ScoutRecord.Offer> scout(ServerPlayer p, PlayerRank rank) {
        ScoutRecord r = record(p);
        List<ScoutRecord.Offer> out = new ArrayList<>();
        List<GuildTerms> want = new ArrayList<>();
        for (GuildTerms t : GuildTerms.interested(rank)) {
            if (r.cameFor[t.guild().ordinal()] >= rank.tier() || r.contractedTo(t.guild()) || r.offerFrom(t.guild()) != null) continue;
            want.add(t);
        }
        if (want.isEmpty()) return out;
        if (r.round == null || rank.tier() > r.round.tier()) {
            r.round = rank;
            r.roundGuilds.clear();
            r.roundDeclined.clear();
        }
        RandomSource rand = p.getRandom();
        long at = now(p.server) + ScoutingConfig.firstScoutTicks();
        for (GuildTerms t : want) {
            ScoutRecord.Offer o = makeOffer(t, rank, at + rand.nextInt(200), false, rand);
            r.offers.add(o);
            r.cameFor[t.guild().ordinal()] = rank.tier();
            r.roundGuilds.add(t.guild());
            out.add(o);
            at += ScoutingConfig.scoutGapTicks();
        }
        return out;
    }

    /** A guild's first offer to a hunter of the rank (a renewal: a little more). */
    static ScoutRecord.Offer makeOffer(GuildTerms t, PlayerRank rank, long openAt, boolean renewal, RandomSource rand) {
        double base = EconomyHooks.GATE_GOLD[rank.tier()] * ScoutingConfig.bonusGates() * t.factor() * (0.9 + 0.2 * rand.nextDouble());
        if (renewal) base *= ScoutingConfig.renewal();
        long bonus = round(base);
        long ceiling = Math.max(bonus, round(bonus * ScoutingConfig.ceiling(rank)));
        return new ScoutRecord.Offer(UUID.randomUUID(), t.guild(), rank, bonus, ceiling, t.cut(), ScoutingConfig.contractDays(), openAt, renewal,
                scoutName(t.guild(), rand), rand.nextInt(ScoutEntity.LOOKS));
    }

    /** One of the guild's scouts: each great guild sends its own people (its own share of the names). */
    static String scoutName(RivalGuild guild, RandomSource rand) {
        int slot = 0;
        for (int i = 0; i < GuildTerms.ALL.size(); i++) if (GuildTerms.ALL.get(i).guild() == guild) slot = i;
        int per = SCOUTS.size() / GuildTerms.ALL.size();
        return SCOUTS.get(slot * per + rand.nextInt(per));
    }

    /** Gold to the nearest hundred. */
    static long round(double gold) {
        return Math.max(0, Math.round(gold / 100.0) * 100);
    }

    // ================================================================ each second

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        for (ServerPlayer p : OnlinePlayers.all(server)) tick(p);
    }

    /** One second of a hunter's dealings: the contract's days, offers coming in, scouts setting out, offers lapsing. */
    public static void tick(ServerPlayer p) {
        ScoutRecord r = record(p);
        MinecraftServer server = p.server;
        long now = now(server);
        if (r.contract != null && day(server) >= r.contract.untilDay()) endContract(p, End.RAN_OUT);
        if (r.offers.isEmpty()) return;
        Iterator<ScoutRecord.Offer> it = r.offers.iterator();
        List<ScoutRecord.Offer> told = new ArrayList<>();
        while (it.hasNext()) {
            ScoutRecord.Offer o = it.next();
            if (!o.open(now)) continue;
            if (o.lapseAt <= 0) o.lapseAt = now + ScoutingConfig.offerTicks();
            if (now >= o.lapseAt) {
                it.remove();
                r.lapsed++;
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, t("notify.title", o.guild.displayName()),
                        t("notify.lapsed", o.guild.displayName())));
                continue;
            }
            if (!o.told) {
                o.told = true;
                told.add(o);
            }
        }
        for (ScoutRecord.Offer o : told) {
            boolean coming = canVisit(p) && sendScout(p, o) != null;
            if (coming) o.visited = true;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.QUEST, t("notify.title", o.guild.displayName()),
                    coming ? t(o.renewal ? "notify.coming.renewal" : "notify.coming", o.scout, o.guild.displayName())
                            : t(o.renewal ? "notify.sent.renewal" : "notify.sent", o.guild.displayName(), gold(o.bonus), minutes(o.lapseAt - now))));
            // a second guild after the same hunter: the news has a bidding war
            if (!o.renewal && r.round == o.rank) {
                List<RivalGuild> open = new ArrayList<>();
                for (ScoutRecord.Offer x : r.offers) if (x.told && !x.renewal && x.rank == o.rank) open.add(x.guild);
                if (open.size() == 2) HunterNews.biddingWar(p, open, o.rank);
            }
        }
        // an offer whose scout could not come yet: they come once the hunter is back in the world
        for (ScoutRecord.Offer o : r.offers) {
            if (o.told && !o.visited && o.open(now) && now - o.openAt > 20 * 5 && canVisit(p) && sendScout(p, o) != null) o.visited = true;
        }
    }

    /** True while a scout can walk up to the hunter: out in the overworld, on their feet, not in a fight. */
    public static boolean canVisit(ServerPlayer p) {
        if (p instanceof FakePlayer || !p.isAlive() || p.isSpectator() || p.isSleeping() || !SystemAPI.isAwakened(p)) return false;
        if (!p.level().dimension().equals(Level.OVERWORLD)) return false;
        // in a fight: a monster hurt them in the last 8 s. The time of that hit is kept with the player while their tick
        // count starts over at each login, so a hit from before they last joined (a time still ahead) does not count.
        int since = p.tickCount - p.getLastHurtByMobTimestamp();
        return p.getLastHurtByMobTimestamp() <= 0 || since < 0 || since > 20 * 8;
    }

    // ================================================================ the scout

    /** A scout sets out for the hunter from behind them, out of sight; null if there is nowhere for one to come from. */
    @Nullable
    public static ScoutEntity sendScout(ServerPlayer p, ScoutRecord.Offer o) {
        ServerLevel level = p.serverLevel();
        Vec3 at = spot(level, p, p.getRandom());
        if (at == null) return null;
        ScoutEntity e = ScoutingModule.SCOUT.get().create(level);
        if (e == null) return null;
        e.setup(p, o);
        float yaw = (float) (Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * Mth.RAD_TO_DEG) - 90f;
        e.moveTo(at.x, at.y, at.z, yaw, 0);
        e.setYHeadRot(yaw);
        level.addFreshEntity(e);
        return e;
    }

    /** Where a scout comes from: behind the hunter, 18 to 24 blocks off on open ground (nearer under a roof). */
    @Nullable
    public static Vec3 spot(ServerLevel level, ServerPlayer p, RandomSource r) {
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        double back = Math.atan2(-look.z, -look.x);
        for (int i = 0; i < 24; i++) {
            double a = back + (r.nextDouble() - 0.5) * Math.PI;
            boolean near = i >= 12;
            double d = near ? 7 + r.nextDouble() * 4 : 18 + r.nextDouble() * 6;
            int x = Mth.floor(p.getX() + Math.cos(a) * d), z = Mth.floor(p.getZ() + Math.sin(a) * d);
            if (!level.isLoaded(new BlockPos(x, p.getBlockY(), z))) continue;
            int y = near ? footing(level, x, p.getBlockY(), z) : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y == Integer.MIN_VALUE || Math.abs(y - p.getY()) > (near ? 3 : 8)) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (stand(level, pos)) return Vec3.atBottomCenterOf(pos);
        }
        return null;
    }

    /** The floor nearest {@code y} at (x, z) within three blocks up or down, or MIN_VALUE. */
    private static int footing(ServerLevel level, int x, int y, int z) {
        for (int dy = 0; dy <= 3; dy++) {
            if (stand(level, new BlockPos(x, y + dy, z))) return y + dy;
            if (dy > 0 && stand(level, new BlockPos(x, y - dy, z))) return y - dy;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean stand(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** What a scout says, in the chat. */
    static void say(ServerPlayer p, Entity scout, Component line) {
        p.sendSystemMessage(Component.translatable("npc.beyondsolo.association.says", scout.getDisplayName(),
                line.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    /** The scout reaches the hunter: hello, and a word on what they are here for. */
    static void greet(ServerPlayer p, ScoutEntity scout, ScoutRecord.Offer o) {
        say(p, scout, t((o.renewal ? "scout.renewal." : "scout.hello.") + o.guild.key(), p.getGameProfile().getName(), o.scout));
        p.displayClientMessage(t("scout.hint").copy().withStyle(ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, scout.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.5f, 1.3f);
    }

    /** The scout waited long enough: the offer stands at the association. */
    static void gaveUp(ServerPlayer p, ScoutEntity scout, ScoutRecord.Offer o) {
        say(p, scout, t("scout.gave_up"));
    }

    /** A hunter speaks to a scout: their offer's window (if it is theirs to answer). */
    public static void talk(ServerPlayer p, ScoutEntity scout) {
        if (!p.getUUID().equals(scout.hunter())) {
            say(p, scout, t("scout.not_you"));
            return;
        }
        ScoutRecord.Offer o = scout.offer() == null ? null : record(p).offer(scout.offer());
        if (o == null) {
            say(p, scout, t("scout.done"));
            return;
        }
        open(p, new Association.Session(scout.getId(), scout.level().dimension(), scout.blockPosition(), Association.SCOUT), null);
    }

    /** True while the hunter has this scout's window open. */
    static boolean talking(ServerPlayer p, ScoutEntity scout) {
        Association.Session s = Association.session(p);
        return s != null && s.screen() == Association.SCOUT && s.entity() == scout.getId();
    }

    /** Opens the scout's window (or refreshes it), with the answer to the last button. */
    public static void open(ServerPlayer p, Association.Session s, @Nullable Association.Outcome outcome) {
        Association.remember(p, s);
        CompoundTag t = scoutTag(p, s);
        if (outcome != null) outcome.write(t, p.registryAccess());
        PacketDistributor.sendToPlayer(p, new AssociationSyncPayload(Association.SCOUT, t));
    }

    // ================================================================ answering

    static Association.Outcome yes(String key, Object... args) {
        return new Association.Outcome(true, t("outcome." + key, args));
    }

    static Association.Outcome no(String key, Object... args) {
        return new Association.Outcome(false, t("outcome." + key, args));
    }

    /** An offer on the table now, or null. */
    @Nullable
    static ScoutRecord.Offer onTable(ServerPlayer p, UUID id) {
        ScoutRecord.Offer o = record(p).offer(id);
        return o == null || !o.open(now(p.server)) ? null : o;
    }

    /** Signs with a guild: the bonus now, the contract from today. The other guilds withdraw. */
    public static Association.Outcome sign(ServerPlayer p, UUID id) {
        if (!SystemAPI.isAwakened(p)) return no("unregistered");
        ScoutRecord.Offer o = onTable(p, id);
        if (o == null) return no("gone");
        ScoutRecord r = record(p);
        RivalGuild from = null;
        if (r.contract != null) {
            from = r.contract.guild();
            endContract(p, End.BOUGHT_OUT);
        }
        long day = day(p.server);
        r.contract = new ScoutRecord.Contract(o.guild, o.rank, o.bonus, o.cut, day, day + o.days, now(p.server));
        r.paid = 0;
        r.signed++;
        r.best = Math.max(r.best, o.bonus);
        r.offers.remove(o);
        Economy.add(p, o.bonus, Economy.Reason.CONTRACT);
        // the rest of the table withdraws
        boolean withdrew = false;
        for (Iterator<ScoutRecord.Offer> it = r.offers.iterator(); it.hasNext(); ) {
            ScoutRecord.Offer x = it.next();
            if (x.open(now(p.server)) || x.rank == o.rank) {
                it.remove();
                withdrew = true;
            }
        }
        if (o.rank == PlayerRank.S && !o.renewal) SystemAPI.setTitle(p, "title.beyondsolo.franchise_hunter");
        HunterNews.signed(p, o.guild, o.bonus, o.rank, o.renewal, from);
        GuildTerms terms = GuildTerms.of(o.guild);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD, t("notify.signed.title", o.guild.displayName()),
                t(withdrew ? "notify.signed.body_withdrew" : "notify.signed.body", gold(o.bonus), Math.round(o.cut * 100), o.days,
                        perk(terms == null ? GuildTerms.Perk.NONE : terms.perk()))));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
        return yes(from != null ? "signed.buyout" : "signed", o.guild.displayName(), gold(o.bonus), from == null ? "" : from.displayName());
    }

    /** Holds out for more (see the class's notes); {@code roll} decides the raise and whether the guild walks. */
    public static Association.Outcome holdOut(ServerPlayer p, UUID id) {
        return holdOut(p, id, p.getRandom().nextDouble(), p.getRandom().nextDouble());
    }

    public static Association.Outcome holdOut(ServerPlayer p, UUID id, double walkRoll, double raiseRoll) {
        if (!SystemAPI.isAwakened(p)) return no("unregistered");
        ScoutRecord.Offer o = onTable(p, id);
        if (o == null) return no("gone");
        if (o.last) return no("final", o.guild.displayName(), gold(o.bonus));
        ScoutRecord r = record(p);
        GuildTerms terms = GuildTerms.of(o.guild);
        // the best of the other guilds' offers on the table
        ScoutRecord.Offer rival = null;
        for (ScoutRecord.Offer x : r.open(now(p.server))) {
            if (x != o && x.guild != o.guild && (rival == null || x.bonus > rival.bonus)) rival = x;
        }
        if (rival != null && rival.bonus >= o.bonus) {
            long want = round(rival.bonus * (1.05 + 0.05 * raiseRoll));
            if (want <= rival.bonus) want = rival.bonus + 100;
            o.raises++;
            r.raised++;
            if (want >= o.ceiling) {
                o.bonus = o.ceiling;
                o.last = true;
                return o.bonus > rival.bonus ? yes("beat_final", o.guild.displayName(), rival.guild.displayName(), gold(o.bonus))
                        : yes("cant_match", o.guild.displayName(), rival.guild.displayName(), gold(o.bonus));
            }
            o.bonus = want;
            return yes("beat", o.guild.displayName(), rival.guild.displayName(), gold(o.bonus));
        }
        // nobody offers more: they may have had enough
        double walk = (terms == null ? 0.2 : terms.patience()) + ScoutingConfig.walkAwayStep() * o.raises;
        if (walkRoll < walk) {
            r.offers.remove(o);
            r.walked++;
            return no("walked", o.guild.displayName());
        }
        double raise = ScoutingConfig.raiseMin() + (ScoutingConfig.raiseMax() - ScoutingConfig.raiseMin()) * raiseRoll;
        long want = Math.max(o.bonus + 100, round(o.bonus * (1 + raise)));
        o.raises++;
        r.raised++;
        if (want >= o.ceiling) {
            o.bonus = o.ceiling;
            o.last = true;
            return yes("raised_final", o.guild.displayName(), gold(o.bonus));
        }
        o.bonus = want;
        return yes("raised", o.guild.displayName(), gold(o.bonus));
    }

    /** Turns a guild down: word gets round, and the others on the table offer a little more. */
    public static Association.Outcome decline(ServerPlayer p, UUID id) {
        ScoutRecord.Offer o = onTable(p, id);
        if (o == null) return no("gone");
        ScoutRecord r = record(p);
        r.offers.remove(o);
        r.declined++;
        if (!o.renewal) r.roundDeclined.add(o.guild);
        boolean raised = false;
        for (ScoutRecord.Offer x : r.open(now(p.server))) {
            if (x.last) continue;
            long want = Math.min(x.ceiling, round(x.bonus * (1 + ScoutingConfig.declineRaise())));
            if (want > x.bonus) {
                x.bonus = want;
                raised = true;
            }
            if (x.bonus >= x.ceiling) x.last = true;
        }
        // every great guild turned down
        boolean all = true;
        for (GuildTerms t : GuildTerms.ALL) all &= r.roundDeclined.contains(t.guild());
        if (all) {
            r.roundDeclined.clear();
            SystemAPI.setTitle(p, "title.beyondsolo.not_for_sale");
            HunterNews.notForSale(p);
            return yes("not_for_sale", o.guild.displayName());
        }
        return yes(raised ? "declined_raised" : "declined", o.guild.displayName());
    }

    /** What walking out on the contract costs now: what is left of the bonus. */
    public static long owed(ScoutRecord.Contract c, long day) {
        long days = Math.max(1, c.untilDay() - c.fromDay());
        return round(c.bonus() * (double) Math.min(days, c.daysLeft(day)) / days);
    }

    /** Walks out on the contract, paying back what is left of the bonus. */
    public static Association.Outcome breakContract(ServerPlayer p) {
        ScoutRecord r = record(p);
        ScoutRecord.Contract c = r.contract;
        if (c == null) return no("no_contract");
        long owe = owed(c, day(p.server));
        if (!Economy.spend(p, owe, Economy.Reason.CONTRACT)) return no("owe", gold(owe));
        endContract(p, End.WALKED_OUT);
        HunterNews.walkedOut(p, c.guild());
        return yes("walked_out", c.guild().displayName(), gold(owe));
    }

    /** The contract is over: run its course (the guild asks to renew), bought out, walked out on, or ended by a command. */
    public static void endContract(ServerPlayer p, End why) {
        ScoutRecord r = record(p);
        ScoutRecord.Contract c = r.contract;
        if (c == null) return;
        r.contract = null;
        r.paid = 0;
        Component guild = c.guild().displayName();
        switch (why) {
            case RAN_OUT -> {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, t("notify.title", guild), t("notify.ran_out", guild)));
                GuildTerms terms = GuildTerms.of(c.guild());
                if (terms != null && SystemAPI.isAwakened(p) && r.offerFrom(c.guild()) == null) {
                    PlayerRank rank = SystemAPI.get(p).rank();
                    r.offers.add(makeOffer(terms, rank, now(p.server) + 20 * 20, true, p.getRandom()));
                }
            }
            case BOUGHT_OUT -> Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, t("notify.title", guild), t("notify.bought_out", guild)));
            case WALKED_OUT -> {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, t("notify.title", guild), t("notify.walked_out", guild)));
                // the guild's national-level hunter comes to settle it ({@code duel.Duels})
                com.beyondsolo.duel.Duels.walkedOut(p, c.guild());
            }
            default -> {
            }
        }
    }

    // ================================================================ the contract's hold

    /** The guild the hunter is under contract with, or null. */
    @Nullable
    public static RivalGuild contractGuild(Player p) {
        ScoutRecord.Contract c = record(p).contract;
        return c == null ? null : c.guild();
    }

    /** True if the hunter is one of the guild's own (under contract with it). */
    public static boolean allied(Player p, RivalGuild guild) {
        return guild != RivalGuild.INDEPENDENT && record(p).contractedTo(guild);
    }

    @Nullable
    static GuildTerms terms(Player p) {
        RivalGuild g = contractGuild(p);
        return g == null ? null : GuildTerms.of(g);
    }

    /** Gold a hunter is paid ({@code Economy.add}): the guild takes its share of gate gold. */
    public static long gold(ServerPlayer p, long amount, Economy.Reason reason) {
        if (reason != Economy.Reason.GATE || amount <= 0) return amount;
        ScoutRecord r = record(p);
        if (r.contract == null) return amount;
        long cut = Math.round(amount * (double) r.contract.cut());
        r.paid += cut;
        return amount - cut;
    }

    /** What a hired hand asks of the hunter at the association (Iron Bastion's own pay less). */
    public static long hireFee(Player p, long fee) {
        GuildTerms t = terms(p);
        if (t == null || t.perk() != GuildTerms.Perk.HIRES) return fee;
        return Math.max(0, Math.round(fee * (1 - ScoutingConfig.hirePerk()) / 10.0) * 10);
    }

    /** Association merit a hunter earns (Sunfall's own earn more). */
    public static long merit(Player p, long amount) {
        GuildTerms t = terms(p);
        if (t == null || t.perk() != GuildTerms.Perk.MERIT || amount <= 0) return amount;
        return Math.round(amount * (1 + ScoutingConfig.meritPerk()));
    }

    /** System XP (Azure Tide's own earn more). */
    @SubscribeEvent
    static void onXp(SystemEvent.XpGain event) {
        GuildTerms t = terms(event.getPlayer());
        if (t == null || t.perk() != GuildTerms.Perk.XP) return;
        event.setAmount(Math.round(event.getAmount() * (1 + ScoutingConfig.xpPerk())));
    }

    // ================================================================ what the windows show

    static Component t(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    static String gold(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount);
    }

    static String minutes(long ticks) {
        return String.valueOf(Math.max(1, Math.round(ticks / 1200.0)));
    }

    /** A perk as a line ("+5% System XP"), or the empty text. */
    public static Component perk(GuildTerms.Perk perk) {
        return switch (perk) {
            case XP -> t("perk.xp", Math.round(ScoutingConfig.xpPerk() * 100));
            case HIRES -> t("perk.hires", Math.round(ScoutingConfig.hirePerk() * 100));
            case MERIT -> t("perk.merit", Math.round(ScoutingConfig.meritPerk() * 100));
            case NONE -> t("perk.none");
        };
    }

    static CompoundTag offerTag(ScoutRecord.Offer o, long now, HolderLookup.Provider reg) {
        CompoundTag c = new CompoundTag();
        c.putUUID("id", o.id);
        c.putInt("guild", o.guild.ordinal());
        c.putInt("rank", o.rank.ordinal());
        c.putLong("bonus", o.bonus);
        c.putLong("first", o.first);
        c.putFloat("cut", o.cut);
        c.putInt("days", o.days);
        c.putInt("raises", o.raises);
        c.putBoolean("last", o.last);
        c.putBoolean("renewal", o.renewal);
        c.putBoolean("open", o.open(now));
        c.putLong("left", o.open(now) ? Math.max(0, o.lapseAt - now) : Math.max(0, o.openAt - now));
        c.putString("scout", o.scout);
        GuildTerms t = GuildTerms.of(o.guild);
        c.putString("perk", Component.Serializer.toJson(perk(t == null ? GuildTerms.Perk.NONE : t.perk()), reg));
        return c;
    }

    static CompoundTag contractTag(ServerPlayer p, ScoutRecord r, HolderLookup.Provider reg) {
        ScoutRecord.Contract c = r.contract;
        CompoundTag t = new CompoundTag();
        if (c == null) return t;
        long day = day(p.server);
        t.putInt("guild", c.guild().ordinal());
        t.putInt("rank", c.rank().ordinal());
        t.putLong("bonus", c.bonus());
        t.putFloat("cut", c.cut());
        t.putLong("daysLeft", c.daysLeft(day));
        t.putLong("days", Math.max(1, c.untilDay() - c.fromDay()));
        t.putLong("paid", r.paid);
        t.putLong("owe", owed(c, day));
        GuildTerms terms = GuildTerms.of(c.guild());
        t.putString("perk", Component.Serializer.toJson(perk(terms == null ? GuildTerms.Perk.NONE : terms.perk()), reg));
        return t;
    }

    /** The offers on the table, the best first, and the contract: what the office's GUILDS tab and the scout's window share. */
    static void fill(ServerPlayer p, CompoundTag t) {
        HolderLookup.Provider reg = p.registryAccess();
        ScoutRecord r = record(p);
        long now = now(p.server);
        List<ScoutRecord.Offer> open = r.open(now);
        open.sort(Comparator.comparingLong((ScoutRecord.Offer o) -> -o.bonus));
        ListTag offers = new ListTag();
        for (ScoutRecord.Offer o : open) offers.add(offerTag(o, now, reg));
        t.put("offers", offers);
        t.putInt("coming", r.offers.size() - open.size());
        t.put("contract", contractTag(p, r, reg));
        t.putInt("signed", r.signed);
        t.putInt("declined", r.declined);
        t.putInt("raised", r.raised);
        t.putLong("best", r.best);
        t.putInt("rank", SystemAPI.get(p).rank().ordinal());
    }

    /** The office's GUILDS tab. */
    public static CompoundTag officeTag(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        fill(p, t);
        ListTag guilds = new ListTag();
        for (GuildTerms g : GuildTerms.ALL) {
            CompoundTag c = new CompoundTag();
            c.putInt("guild", g.guild().ordinal());
            c.putInt("from", g.from().ordinal());
            c.putFloat("cut", g.cut());
            c.putString("perk", Component.Serializer.toJson(perk(g.perk()), p.registryAccess()));
            guilds.add(c);
        }
        t.put("guilds", guilds);
        return t;
    }

    /** The scout's window: the scout, their guild's offer, and the rest of the table. */
    static CompoundTag scoutTag(ServerPlayer p, Association.Session s) {
        CompoundTag t = new CompoundTag();
        t.putInt("entity", s.entity());
        Entity e = p.serverLevel().getEntity(s.entity());
        ScoutEntity scout = e instanceof ScoutEntity se ? se : null;
        t.putString("name", Component.Serializer.toJson(Component.literal(scout == null ? "" : scout.scoutName()), p.registryAccess()));
        t.putInt("guild", scout == null ? RivalGuild.IRON_BASTION.ordinal() : scout.guild().ordinal());
        if (scout != null && scout.offer() != null) t.putUUID("mine", scout.offer());
        fill(p, t);
        return t;
    }

    // ================================================================ commands

    /** /solo scout offer: the guild's offer on the table now, whatever it offered before (the first of a new round if none is under way). */
    public static ScoutRecord.Offer offerNow(ServerPlayer p, GuildTerms terms) {
        ScoutRecord r = record(p);
        r.offers.removeIf(o -> o.guild == terms.guild());
        PlayerRank rank = SystemAPI.isAwakened(p) ? SystemAPI.get(p).rank() : PlayerRank.E;
        if (r.round == null || rank.tier() > r.round.tier()) {
            r.round = rank;
            r.roundGuilds.clear();
            r.roundDeclined.clear();
        }
        ScoutRecord.Offer o = makeOffer(terms, rank, now(p.server), false, p.getRandom());
        r.offers.add(o);
        r.cameFor[terms.guild().ordinal()] = Math.max(r.cameFor[terms.guild().ordinal()], rank.tier());
        r.roundGuilds.add(terms.guild());
        return o;
    }

    /** /solo scout lapse: every offer on the table runs out now. Returns how many. */
    public static int lapseAll(ServerPlayer p) {
        long now = now(p.server);
        int n = 0;
        for (ScoutRecord.Offer o : record(p).offers) {
            if (!o.open(now)) continue;
            o.lapseAt = now;
            n++;
        }
        tick(p);
        return n;
    }

    /** /solo scout reset: every offer and the contract gone, the record clean, the scouts home. */
    public static void reset(ServerPlayer p) {
        wipe(p);
        p.setData(ScoutingModule.RECORD, new ScoutRecord());
    }

    /** /solo contract expire: the contract's last day is today, and it runs its course. */
    public static boolean expire(ServerPlayer p) {
        ScoutRecord r = record(p);
        if (r.contract == null) return false;
        endContract(p, End.RAN_OUT);
        return true;
    }

    /** /solo wipe: their scouts go home. */
    public static void wipe(ServerPlayer p) {
        List<ScoutEntity> scouts = new ArrayList<>();
        for (ServerLevel level : p.server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ScoutEntity s && p.getUUID().equals(s.hunter())) scouts.add(s);
            }
        }
        scouts.forEach(Entity::discard);
    }

    private Scouting() {}
}
