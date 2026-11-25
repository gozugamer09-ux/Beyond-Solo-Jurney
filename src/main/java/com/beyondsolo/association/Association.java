package com.beyondsolo.association;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.breaks.BreakState;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.network.AssociationSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestGiver;
import com.beyondsolo.quest.situation.Run;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Hunter Association: the office its officer keeps at every Evaluation Zone, and the merit it keeps on hunters.
 *
 * <ul>
 * <li><b>Contracts.</b> The office watches the gates within {@code association.watchRadius} of it and pays a bounty
 * on top of the clear for closing one: more for a gate coming apart, most for one that has broken. A contract is a
 * quest ({@link #CONTRACT}) with a waypoint to the gate; it is done when its holder is in the cave when the boss falls
 * (or has a share in putting the break down), and fails when the gate closes without them.</li>
 * <li><b>Gate permits.</b> For a fee the association grants a hunter the clearing rights to a gate of the rank they
 * ask for (up to {@code association.permitReach} above their own): one tears open near them, and only they and their
 * party may go in. A few a day.</li>
 * <li><b>The rank exam.</b> When the association's measurements show more mana than the license says (the hunter's
 * true rank, {@link Reevaluation#trueRank}, is higher), the hunter may sit the exam for the next rank: a gate of that
 * rank opens for them alone, to be cleared within {@code association.examMinutes}. Passing certifies the rank at once
 * (no crystal milestone needed); failing means waiting a day.</li>
 * <li><b>Merit.</b> Every clear, contract, break put down and exam passed earns merit, which raises the hunter's
 * {@link Standing} and places them on the national merit board beside the world's other hunters.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Association {
    public static final String CONTRACT = BeyondSolo.id("association/contract").toString();
    public static final String PERMIT = BeyondSolo.id("association/permit").toString();
    public static final String EXAM = BeyondSolo.id("association/exam").toString();
    public static final String TIP = BeyondSolo.id("association/tip").toString();

    /** On an association quest: the gate it is about, its rank, the contract's kind, and what it pays. */
    public static final String GATE = "assocGate", RANK = "assocRank", KIND = "assocKind";
    public static final String REWARD_GOLD = "rewardGold", REWARD_MERIT = "rewardMerit";
    /** On a quest with a waypoint: what the waypoint's label calls the place ({@code npc.beyondsolo.waypoint.<kind>}). */
    public static final String WAYPOINT_KIND = "wpKind";

    /** The windows: the office, the broker's, a great guild's scout's ({@code scouting.Scouting}), a national-level hunter's challenge ({@code duel.Duels}). */
    public static final int OFFICE = 0, BROKER = 1, SCOUT = 2, DUEL = 3;
    /** The office's tabs. */
    public static final int TAB_CONTRACTS = 0, TAB_PERMITS = 1, TAB_EXAM = 2, TAB_STANDING = 3, TAB_HIRE = 4, TAB_GUILDS = 5, TAB_DUELS = 6;
    /** Contract kinds: a gate holding, one coming apart, one that has broken. */
    public static final int CLEAR = 0, UNSTABLE = 1, BREAK = 2;
    /** How far a hunter may stray from the one they talk to before the window stops answering. */
    public static final int TALK_RANGE = 10;
    /** A broker's lead is done this near the gate (blocks). */
    public static final int FOUND = 12;
    /** Contracts done for the bounty hunter's title. */
    public static final int BOUNTY_HUNTER = 25;

    /** A hunter at a desk: the officer or broker they talk to (0: opened by a command) and where it stands. */
    public record Session(int entity, ResourceKey<Level> dimension, BlockPos office, int screen) {}

    /** The answer to a button, shown in the window. */
    public record Outcome(boolean ok, Component text) {
        static Outcome yes(String key, Object... args) {
            return new Outcome(true, Component.translatable("association.beyondsolo.outcome." + key, args));
        }

        static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable("association.beyondsolo.outcome." + key, args));
        }

        public void write(CompoundTag t, HolderLookup.Provider reg) {
            t.putBoolean("ok", ok);
            t.putString("outcome", json(text, reg));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static AssociationRecord record(Player p) {
        return p.getData(AssociationModule.RECORD);
    }

    public static String contractKey(UUID gate) {
        return CONTRACT + "/" + gate;
    }

    public static String examKey(UUID gate) {
        return EXAM + "/" + gate;
    }

    public static String tipKey(UUID gate) {
        return TIP + "/" + gate;
    }

    static String json(Component c, HolderLookup.Provider reg) {
        return Component.Serializer.toJson(c, reg);
    }

    static boolean ours(Quest q) {
        String t = q.template();
        return t.equals(CONTRACT) || t.equals(PERMIT) || t.equals(EXAM) || t.equals(TIP);
    }

    @Nullable
    public static UUID gateOf(Quest q) {
        return q.extra().hasUUID(GATE) ? q.extra().getUUID(GATE) : null;
    }

    static PlayerRank rankOf(Quest q) {
        PlayerRank r = PlayerRank.byName(q.extra().getString(RANK));
        return r == null ? PlayerRank.E : r;
    }

    // ------------------------------------------------------------------ the desk

    /** A hunter speaks to the officer. */
    public static void talk(ServerPlayer p, AssociationNpc officer) {
        if (!SystemAPI.isAwakened(p)) {
            say(p, officer, "npc.beyondsolo.association.officer.unregistered");
            return;
        }
        open(p, new Session(officer.getId(), officer.level().dimension(), officer.blockPosition(), OFFICE), -1, null);
    }

    /** What the one at the desk says, in the chat. */
    static void say(ServerPlayer p, Entity npc, String key, Object... args) {
        p.sendSystemMessage(Component.translatable("npc.beyondsolo.association.says", npc.getDisplayName(),
                Component.translatable(key, args).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    /** Opens the office (or refreshes it) on a tab (-1: the one showing), with the answer to the last button. */
    public static void open(ServerPlayer p, Session s, int tab, @Nullable Outcome outcome) {
        SESSIONS.put(p.getUUID(), s);
        CompoundTag t = officeTag(p, s);
        t.putInt("tab", tab);
        if (outcome != null) outcome.write(t, p.registryAccess());
        PacketDistributor.sendToPlayer(p, new AssociationSyncPayload(OFFICE, t));
    }

    /** Opens the office where the player stands, as if at a desk (commands). */
    public static void openHere(ServerPlayer p, int tab) {
        open(p, new Session(0, p.level().dimension(), p.blockPosition(), OFFICE), tab, null);
    }

    /** Opens the office for the gates round {@code office} (the desk in the hunter's home: where they left the world). */
    public static void openAt(ServerPlayer p, ResourceKey<Level> dim, BlockPos office, int tab) {
        open(p, new Session(0, dim, office, OFFICE), tab, null);
    }

    /** The hunter is at that desk now (its window is open). */
    public static void remember(ServerPlayer p, Session s) {
        SESSIONS.put(p.getUUID(), s);
    }

    @Nullable
    public static Session session(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }

    /** The hunter is at no desk now (their window closed for them). */
    public static void forget(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
    }

    /** True while the hunter is still at the desk the session was opened at. */
    static boolean atDesk(ServerPlayer p, Session s) {
        if (s.entity() == 0) return true;
        Entity e = p.serverLevel().getEntity(s.entity());
        boolean desk = s.screen() == SCOUT ? e instanceof com.beyondsolo.scouting.ScoutEntity
                : s.screen() == DUEL ? e instanceof com.beyondsolo.duel.DuelistEntity : e instanceof AssociationNpc;
        return desk && e.isAlive() && p.level().dimension().equals(s.dimension()) && e.distanceToSqr(p) <= TALK_RANGE * TALK_RANGE;
    }

    /** A button in a window. */
    public static void handle(ServerPlayer p, AssociationActionPayload a) {
        Session s = SESSIONS.get(p.getUUID());
        if (a.action() == AssociationActionPayload.CLOSE) {
            SESSIONS.remove(p.getUUID());
            return;
        }
        if (s == null) return;
        if (!atDesk(p, s)) {
            SESSIONS.remove(p.getUUID());
            return;
        }
        CompoundTag args = a.args();
        PlayerRank rank = args.contains("rank") ? PlayerRank.values()[Mth.clamp(args.getInt("rank"), 0, PlayerRank.values().length - 1)] : null;
        UUID gate = args.hasUUID("gate") ? args.getUUID("gate") : null;
        UUID offer = args.hasUUID("offer") ? args.getUUID("offer") : null;
        UUID hire = args.hasUUID("hire") ? args.getUUID("hire") : null;
        com.beyondsolo.duel.NationalHunter who = args.contains("who") ? com.beyondsolo.duel.NationalHunter.byIndex(args.getInt("who")) : null;
        // each desk does its own business: the officer's buttons at the office, the broker's at the broker, a scout's
        // with the scout (the offers on the table may be answered at the office too)
        // (a challenger's window answers its challenge, and nothing else)
        boolean broker = s.screen() == BROKER;
        boolean scout = s.screen() == SCOUT || s.screen() == DUEL;
        boolean duel = s.screen() == DUEL;
        Outcome o = switch (a.action()) {
            case AssociationActionPayload.ACCEPT -> broker || scout || gate == null ? null : accept(p, s, gate);
            case AssociationActionPayload.DROP -> broker || scout || gate == null ? null : drop(p, gate);
            case AssociationActionPayload.PERMIT -> broker || scout || rank == null ? null : permit(p, s, rank);
            case AssociationActionPayload.EXAM -> broker || scout ? null : startExam(p, s);
            case AssociationActionPayload.TIP -> !broker || rank == null ? null : Broker.tip(p, s, rank);
            case AssociationActionPayload.SCOUT -> !broker || gate == null ? null : Broker.scout(p, s, gate);
            // hands for hire ({@code rival.Hires})
            case AssociationActionPayload.HIRE -> broker || scout || offer == null ? null : com.beyondsolo.rival.Hires.hire(p, s.office(), offer);
            case AssociationActionPayload.DISMISS -> broker || scout || hire == null ? null : com.beyondsolo.rival.Hires.dismiss(p, hire);
            // the great guilds' offers ({@code scouting.Scouting})
            case AssociationActionPayload.SIGN -> broker || duel || offer == null ? null : com.beyondsolo.scouting.Scouting.sign(p, offer);
            case AssociationActionPayload.HOLD_OUT -> broker || duel || offer == null ? null : com.beyondsolo.scouting.Scouting.holdOut(p, offer);
            case AssociationActionPayload.DECLINE -> broker || duel || offer == null ? null : com.beyondsolo.scouting.Scouting.decline(p, offer);
            case AssociationActionPayload.BREAK_CONTRACT -> broker || scout ? null : com.beyondsolo.scouting.Scouting.breakContract(p);
            // the national-level hunters' challenges ({@code duel.Duels}): answered to the challenger or at the office
            case AssociationActionPayload.DUEL_ACCEPT -> broker || (scout && !duel) ? null : com.beyondsolo.duel.Duels.accept(p);
            case AssociationActionPayload.DUEL_LATER -> broker || (scout && !duel) ? null : com.beyondsolo.duel.Duels.later(p);
            case AssociationActionPayload.DUEL_DECLINE -> broker || (scout && !duel) ? null : com.beyondsolo.duel.Duels.decline(p);
            case AssociationActionPayload.DUEL_CHALLENGE -> broker || scout || who == null ? null : com.beyondsolo.duel.Duels.ask(p, who);
            default -> null;
        };
        // the duel began: its windows are closed already
        if (com.beyondsolo.duel.Duels.running(p.getUUID()) != null && a.action() == AssociationActionPayload.DUEL_ACCEPT && o != null && o.ok()) return;
        if (s.screen() == BROKER) Broker.open(p, s, o);
        else if (duel) com.beyondsolo.duel.Duels.open(p, s, o);
        else if (scout) com.beyondsolo.scouting.Scouting.open(p, s, o);
        else open(p, s, -1, o);
    }

    @Nullable
    static QuestGiver giver(ServerPlayer p, Session s) {
        if (s.entity() == 0) return null;
        Entity e = p.serverLevel().getEntity(s.entity());
        return e == null ? null : new QuestGiver(e.getUUID(), e.getDisplayName(), e.blockPosition(), s.dimension(), false);
    }

    // ------------------------------------------------------------------ contracts

    /** A gate on an office's board. */
    public record Offer(GateInstance gate, int kind, long gold, int merit, double distance) {}

    /** True for a gate the association would post a contract on: open, a world gate, no one's claim, not a raid. */
    public static boolean contractable(MinecraftServer server, GateInstance g) {
        if (g.state() != GateInstance.State.OPEN || g.isTrial() || g.isKeyed() || g.isRaid()) return false;
        if (g.breakState().phase() == BreakState.Phase.SUPPRESSED || !g.gateDimension().equals(Level.OVERWORLD)) return false;
        // a gate a rival party went into first is theirs to clear
        if (com.beyondsolo.rival.RivalParties.holds(server, g.id())) return false;
        return AssociationData.get(server).claim(g.id()) == null;
    }

    /** What a contract on the gate is now: {@link #CLEAR}, {@link #UNSTABLE} or {@link #BREAK}. */
    public static int kind(GateInstance g) {
        BreakState b = g.breakState();
        if (b.broken()) return BREAK;
        return b.phase() == BreakState.Phase.UNSTABLE || b.phase() == BreakState.Phase.IMMINENT ? UNSTABLE : CLEAR;
    }

    static String kindId(int kind) {
        return kind == BREAK ? "break" : kind == UNSTABLE ? "unstable" : "clear";
    }

    private static double kindBonus(int kind) {
        return kind == BREAK ? AssociationConfig.breakBonus() : kind == UNSTABLE ? AssociationConfig.unstableBonus() : 1.0;
    }

    /** A contract's bounty in gold, for a hunter of that standing. */
    public static long bounty(PlayerRank rank, int kind, Standing standing) {
        return Math.round(EconomyHooks.GATE_GOLD[rank.tier()] * AssociationConfig.contractGold() * kindBonus(kind) * standing.bountyBonus());
    }

    /** A contract's merit. */
    public static int contractMerit(PlayerRank rank, int kind) {
        return (int) Math.round(AssociationConfig.contractMerit(rank) * kindBonus(kind));
    }

    /** The contracts an office at {@code office} posts for this hunter: broken gates first, then those coming apart, then the nearest. */
    public static List<Offer> board(ServerPlayer p, BlockPos office) {
        MinecraftServer server = p.server;
        Standing st = record(p).standing();
        Vec3 at = Vec3.atCenterOf(office);
        int radius = AssociationConfig.watchRadius();
        List<Offer> out = new ArrayList<>();
        for (GateInstance g : GateData.get(server).all()) {
            if (!contractable(server, g)) continue;
            double d = Math.sqrt(GateSpawner.flatDistSqr(at, g.gatePos()));
            if (d > radius) continue;
            int kind = kind(g);
            out.add(new Offer(g, kind, bounty(g.rank(), kind, st), contractMerit(g.rank(), kind), d));
        }
        out.sort(Comparator.comparingInt((Offer o) -> -o.kind()).thenComparingDouble(Offer::distance));
        return out.size() > AssociationConfig.boardSize() ? new ArrayList<>(out.subList(0, AssociationConfig.boardSize())) : out;
    }

    /** The contracts a hunter holds (permits' gates not counted). */
    public static List<Quest> contracts(ServerPlayer p) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(CONTRACT)) out.add(q);
        return out;
    }

    /** The hunter's association quest on that gate (contract, permit, exam or lead), or null. */
    @Nullable
    public static Quest questOn(ServerPlayer p, UUID gate, String template) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (q.template().equals(template) && gate.equals(gateOf(q))) return q;
        }
        return null;
    }

    /** Takes a contract on a gate from the office's board. */
    public static Outcome accept(ServerPlayer p, Session s, UUID gateId) {
        if (!SystemAPI.isAwakened(p)) return Outcome.no("unregistered");
        GateInstance g = GateData.get(p.server).get(gateId);
        if (g == null || !contractable(p.server, g)) return Outcome.no("contract.gone");
        if (questOn(p, gateId, CONTRACT) != null) return Outcome.no("contract.already");
        Standing st = record(p).standing();
        if (contracts(p).size() >= st.contractSlots()) return Outcome.no("contract.full", st.contractSlots());
        if (GateSpawner.flatDistSqr(Vec3.atCenterOf(s.office()), g.gatePos()) > (double) AssociationConfig.watchRadius() * AssociationConfig.watchRadius()) {
            return Outcome.no("contract.gone");
        }
        int kind = kind(g);
        QuestEngine.start(p, contractQuest(p, g, kind, bounty(g.rank(), kind, st), contractMerit(g.rank(), kind), giver(p, s)));
        return Outcome.yes("contract.taken", g.rank().displayName());
    }

    /** The quest of a contract on {@code g}. */
    public static Quest contractQuest(ServerPlayer p, GateInstance g, int kind, long gold, int merit, @Nullable QuestGiver giver) {
        CompoundTag extra = gateExtra(g, kind == BREAK ? "break" : "contract");
        extra.putInt(KIND, kind);
        extra.putLong(REWARD_GOLD, gold);
        extra.putLong(REWARD_MERIT, merit);
        Component rank = g.rank().displayName();
        QuestBuilder b = QuestBuilder.of(CONTRACT, QuestCategory.ASSOCIATION, g.rank().tier(), SystemAPI.get(p).level()).fixed()
                .title(Component.translatable("quest.beyondsolo.association.contract", rank))
                .description(Component.translatable("quest.beyondsolo.association.contract." + kindId(kind) + ".desc", rank,
                        blocks(p, g), GateSpawner.direction(com.beyondsolo.home.Homes.worldPosition(p), g.gatePos())))
                .line(contractKey(g.id()), Component.translatable("quest_objective.beyondsolo.association.close", rank))
                .extra(extra);
        if (giver != null) b.giver(giver);
        return b.build();
    }

    /** The quest data every association quest about a gate carries: the gate, its rank, a waypoint to it. */
    static CompoundTag gateExtra(GateInstance g, String waypoint) {
        CompoundTag extra = new CompoundTag();
        extra.putUUID(GATE, g.id());
        extra.putString(RANK, g.rank().name());
        extra.putString(WAYPOINT_KIND, waypoint);
        Run.setWaypoint(extra, g.gateDimension(), g.gatePos());
        return extra;
    }

    static int blocks(ServerPlayer p, GateInstance g) {
        return Mth.floor(Math.sqrt(GateSpawner.flatDistSqr(com.beyondsolo.home.Homes.worldPosition(p), g.gatePos())) + 0.5);
    }

    /** Gives a contract (or a permit's gate) back. */
    public static Outcome drop(ServerPlayer p, UUID gate) {
        Quest q = questOn(p, gate, CONTRACT);
        if (q == null) q = questOn(p, gate, PERMIT);
        if (q == null) return Outcome.no("contract.none");
        QuestEngine.abandon(p, q.id());
        return Outcome.yes("contract.dropped");
    }

    // ------------------------------------------------------------------ permits

    public static long permitFee(PlayerRank rank) {
        return Math.round(EconomyHooks.GATE_GOLD[rank.tier()] * AssociationConfig.permitFee());
    }

    /** The highest rank a hunter may take a permit for. */
    public static PlayerRank permitTop(ServerPlayer p) {
        int top = Math.min(PlayerRank.values().length - 1, SystemAPI.get(p).rank().tier() + AssociationConfig.permitReach());
        return PlayerRank.values()[top];
    }

    /** Permits left today. */
    public static int permitsLeft(ServerPlayer p) {
        AssociationRecord r = record(p);
        return Math.max(0, r.standing().permitsPerDay() - r.permitsUsed(QuestEngine.day(p)));
    }

    /** Takes a permit: a gate of that rank opens near the hunter, its clearing rights theirs and their party's. */
    public static Outcome permit(ServerPlayer p, Session s, PlayerRank rank) {
        if (!SystemAPI.isAwakened(p)) return Outcome.no("unregistered");
        if (rank.tier() > permitTop(p).tier()) return Outcome.no("permit.rank", permitTop(p).displayName());
        if (permitsLeft(p) <= 0) return Outcome.no("permit.none_left");
        if (!p.level().dimension().equals(Level.OVERWORLD)) return Outcome.no("overworld");
        long fee = permitFee(rank);
        if (Economy.gold(p) < fee) return Outcome.no("gold", fee);
        GateBlockEntity block = GateSpawner.trySpawnNear(p.serverLevel(), p, rank, false);
        GateInstance g = block == null || block.gateId() == null ? null : GateData.get(p.server).get(block.gateId());
        if (g == null) return Outcome.no("no_spot");
        Economy.spend(p, fee, Economy.Reason.ASSOCIATION);
        record(p).usePermit(QuestEngine.day(p));
        grantPermit(p, g, giver(p, s));
        return Outcome.yes("permit.granted", rank.displayName());
    }

    /** The gate's clearing rights are the hunter's (and their party's): the association holds it for them. */
    public static Quest grantPermit(ServerPlayer p, GateInstance g, @Nullable QuestGiver giver) {
        AssociationData.get(p.server).put(g.id(), new AssociationData.Claim(AssociationData.Kind.PERMIT, p.getUUID(),
                p.getGameProfile().getName(), g.rank(), p.server.overworld().getGameTime()));
        Quest q = permitQuest(p, g, giver);
        QuestEngine.start(p, q);
        return q;
    }

    static Quest permitQuest(ServerPlayer p, GateInstance g, @Nullable QuestGiver giver) {
        CompoundTag extra = gateExtra(g, "permit");
        extra.putLong(REWARD_MERIT, contractMerit(g.rank(), CLEAR));
        Component rank = g.rank().displayName();
        QuestBuilder b = QuestBuilder.of(PERMIT, QuestCategory.ASSOCIATION, g.rank().tier(), SystemAPI.get(p).level()).fixed()
                .title(Component.translatable("quest.beyondsolo.association.permit", rank))
                .description(Component.translatable("quest.beyondsolo.association.permit.desc", rank, blocks(p, g),
                        GateSpawner.direction(com.beyondsolo.home.Homes.worldPosition(p), g.gatePos())))
                .line(contractKey(g.id()), Component.translatable("quest_objective.beyondsolo.association.close", rank))
                .extra(extra);
        if (giver != null) b.giver(giver);
        return b.build();
    }

    // ------------------------------------------------------------------ the rank exam

    /** The rank the hunter would sit the exam for, or null at the top. */
    @Nullable
    public static PlayerRank examRank(ServerPlayer p) {
        int t = SystemAPI.get(p).rank().tier();
        return t + 1 < PlayerRank.values().length ? PlayerRank.values()[t + 1] : null;
    }

    public static long examFee(PlayerRank rank) {
        return Math.round(EconomyHooks.GATE_GOLD[rank.tier()] * AssociationConfig.examFee());
    }

    /** The hunter's exam underway, or null. */
    @Nullable
    public static Quest examQuest(ServerPlayer p) {
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(EXAM)) return q;
        return null;
    }

    /** Why the hunter may not sit the exam now ({@code association.beyondsolo.outcome.<key>}), or null if they may. */
    @Nullable
    public static String examBlock(ServerPlayer p) {
        if (!SystemAPI.isAwakened(p)) return "unregistered";
        if (examRank(p) == null) return "exam.top";
        if (examQuest(p) != null) return "exam.underway";
        if (QuestEngine.day(p) < record(p).examFrom) return "exam.retry";
        if (Reevaluation.trueRank(p).tier() <= SystemAPI.get(p).rank().tier()) return "exam.no_change";
        return null;
    }

    /** The exam begins: a gate of the next rank opens near the hunter, for them alone. */
    public static Outcome startExam(ServerPlayer p, @Nullable Session s) {
        String block = examBlock(p);
        if (block != null) return block.equals("exam.retry") ? Outcome.no(block, record(p).examFrom - QuestEngine.day(p)) : Outcome.no(block);
        if (!p.level().dimension().equals(Level.OVERWORLD)) return Outcome.no("overworld");
        PlayerRank rank = examRank(p);
        long fee = examFee(rank);
        if (Economy.gold(p) < fee) return Outcome.no("gold", fee);
        GateBlockEntity blockEntity = GateSpawner.trySpawnNear(p.serverLevel(), p, rank, false);
        GateInstance g = blockEntity == null || blockEntity.gateId() == null ? null : GateData.get(p.server).get(blockEntity.gateId());
        if (g == null) return Outcome.no("no_spot");
        Economy.spend(p, fee, Economy.Reason.ASSOCIATION);
        beginExam(p, g, s == null ? null : giver(p, s));
        return Outcome.yes("exam.started", rank.displayName());
    }

    /** Makes {@code g} the hunter's exam gate and starts the exam's clock. */
    public static Quest beginExam(ServerPlayer p, GateInstance g, @Nullable QuestGiver giver) {
        AssociationData.get(p.server).put(g.id(), new AssociationData.Claim(AssociationData.Kind.EXAM, p.getUUID(),
                p.getGameProfile().getName(), g.rank(), p.server.overworld().getGameTime()));
        CompoundTag extra = gateExtra(g, "exam");
        extra.putLong(REWARD_MERIT, AssociationConfig.examMerit(g.rank()));
        Component rank = g.rank().displayName();
        int minutes = Math.max(1, AssociationConfig.examTicks() / 1200);
        QuestBuilder b = QuestBuilder.of(EXAM, QuestCategory.ASSOCIATION, g.rank().tier(), SystemAPI.get(p).level()).fixed()
                .title(Component.translatable("quest.beyondsolo.association.exam", rank))
                .description(Component.translatable("quest.beyondsolo.association.exam.desc", rank, blocks(p, g),
                        GateSpawner.direction(com.beyondsolo.home.Homes.worldPosition(p), g.gatePos()), minutes))
                .line(examKey(g.id()), Component.translatable("quest_objective.beyondsolo.association.exam", rank))
                .rule(Component.translatable("quest_objective.beyondsolo.association.alone"))
                .timeLimit(AssociationConfig.examTicks())
                .extra(extra);
        if (giver != null) b.giver(giver);
        Quest q = b.build();
        QuestEngine.start(p, q);
        return q;
    }

    /** The exam was passed: the association certifies the rank. */
    static void passed(ServerPlayer p, Quest q) {
        PlayerRank rank = rankOf(q);
        AssociationRecord r = record(p);
        r.exams++;
        if (rank.tier() > SystemAPI.get(p).rank().tier()) SystemAPI.setRank(p, rank);
        if (r.exams == 1) SystemAPI.setTitle(p, "title.beyondsolo.association_certified");
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.association.exam_passed.title"),
                Component.translatable("notify.beyondsolo.association.exam_passed.body", rank.displayName())));
    }

    /** The exam failed: its gate is an ordinary gate again, and the next exam waits. */
    static void examFailed(ServerPlayer p, Quest q) {
        AssociationRecord r = record(p);
        r.examsFailed++;
        int days = AssociationConfig.examRetryDays();
        r.examFrom = QuestEngine.day(p) + days;
        UUID gate = gateOf(q);
        if (gate != null) AssociationData.get(p.server).remove(gate);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.association.exam_failed.title"),
                Component.translatable(days > 0 ? "notify.beyondsolo.association.exam_failed.body" : "notify.beyondsolo.association.exam_failed.body_now", days)));
    }

    // ------------------------------------------------------------------ the gates

    /** Why the association keeps a hunter out of a gate it claimed ({@code message.beyondsolo.association.*}), or null. */
    @Nullable
    public static String refusal(ServerPlayer p, GateInstance g) {
        AssociationData.Claim c = AssociationData.get(p.server).claim(g.id());
        if (c == null || c.holder().equals(p.getUUID())) return null;
        if (c.kind() == AssociationData.Kind.PERMIT && com.beyondsolo.party.Parties.together(p.server, c.holder(), p.getUUID())) return null;
        return c.kind() == AssociationData.Kind.EXAM ? "message.beyondsolo.association.exam_gate" : "message.beyondsolo.association.permit_gate";
    }

    /** A gate's boss fell (from the gate): merit to everyone inside, their contracts and exam done, its claim over. */
    public static void gateCleared(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        AssociationData data = AssociationData.get(level.getServer());
        AssociationData.Claim claim = data.claim(g.id());
        for (ServerPlayer p : players) {
            if (!SystemAPI.isAwakened(p)) continue;
            record(p).clears++;
            addMerit(p, AssociationConfig.clearMerit(g.rank()));
            QuestEngine.progress(p, contractKey(g.id()), 1);
            if (claim != null && claim.kind() == AssociationData.Kind.EXAM && claim.holder().equals(p.getUUID())) {
                QuestEngine.progress(p, examKey(g.id()), 1);
            }
        }
        data.remove(g.id());
    }

    /** A dungeon break was put down (from the breaks): merit to everyone who had a share, their contracts on it done. */
    public static void breakSuppressed(ServerLevel level, GateInstance g, Collection<ServerPlayer> paid) {
        for (ServerPlayer p : paid) {
            if (!SystemAPI.isAwakened(p)) continue;
            record(p).suppressions++;
            addMerit(p, AssociationConfig.breakMerit(g.rank()));
            QuestEngine.progress(p, contractKey(g.id()), 1);
        }
        AssociationData.get(level.getServer()).remove(g.id());
    }

    // ------------------------------------------------------------------ merit

    /** Adds merit; a new standing is said once (and the top one brings its title). */
    public static void addMerit(ServerPlayer p, long amount) {
        if (amount <= 0) return;
        // a guild's contract may bring more of it ({@code scouting.Scouting})
        amount = com.beyondsolo.scouting.Scouting.merit(p, amount);
        AssociationRecord r = record(p);
        r.merit += amount;
        Standing now = Standing.of(r.merit);
        if (now.ordinal() <= r.standing) return;
        r.standing = now.ordinal();
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.association.standing.title", now.displayName()),
                now.perk()));
        if (now == Standing.NATIONAL) SystemAPI.setTitle(p, "title.beyondsolo.pride_of_the_association");
    }

    /**
     * Takes merit away (a gate forced from a rival party that held it). The standing follows the merit down if it
     * drops under the standing's mark (and isn't announced again when it is earned back).
     */
    public static void takeMerit(ServerPlayer p, long amount) {
        if (amount <= 0) return;
        AssociationRecord r = record(p);
        r.merit = Math.max(0, r.merit - amount);
    }

    /** Sets merit outright (commands); the standing follows without a word. */
    public static void setMerit(ServerPlayer p, long merit) {
        AssociationRecord r = record(p);
        r.merit = Math.max(0, merit);
        r.standing = Standing.of(r.merit).ordinal();
    }

    /** A rival's merit on a day of the world: their clears at their rank's rate. */
    public static long rivalMerit(Rival rival, long day) {
        return (long) rival.gatesOn(day) * AssociationConfig.clearMerit(rival.rank());
    }

    /** As {@link #rivalMerit(Rival, long)}, with the gates their parties cleared in this world on top. */
    public static long rivalMerit(MinecraftServer server, Rival rival, long day) {
        return (long) (rival.gatesOn(day) + com.beyondsolo.rival.RivalData.get(server).clears(rival.id())) * AssociationConfig.clearMerit(rival.rank());
    }

    /** One line of the national merit board. */
    public record Row(String name, PlayerRank rank, long merit, boolean player, boolean me, Component guild) {}

    /** The national merit board: the world's hunters and the server's, most merit first. */
    public static List<Row> national(ServerPlayer me) {
        long day = Rankings.day(me.server);
        List<Row> rows = new ArrayList<>();
        for (Rival r : Rivals.ALL) rows.add(new Row(r.name(), r.rank(), rivalMerit(me.server, r, day), false, false, Rivals.guildName(r.guild())));
        List<ServerPlayer> players = new ArrayList<>(me.server.getPlayerList().getPlayers());
        if (!players.contains(me)) players.add(me);
        for (ServerPlayer o : players) {
            if (!SystemAPI.isAwakened(o)) continue;
            rows.add(new Row(o.getGameProfile().getName(), SystemAPI.get(o).rank(), record(o).merit, true, o == me, guildName(o)));
        }
        rows.sort(Comparator.comparingLong((Row r) -> -r.merit()).thenComparing(r -> !r.me()));
        return rows;
    }

    private static Component guildName(ServerPlayer p) {
        com.beyondsolo.guild.Guild g = com.beyondsolo.guild.Guilds.of(p);
        return g == null ? Rivals.guildName(Rivals.NONE) : Component.literal(g.name());
    }

    // ------------------------------------------------------------------ what the office shows

    static CompoundTag officeTag(ServerPlayer p, Session s) {
        HolderLookup.Provider reg = p.registryAccess();
        CompoundTag t = new CompoundTag();
        t.putInt("entity", s.entity());
        Entity e = s.entity() == 0 ? null : p.serverLevel().getEntity(s.entity());
        t.putString("name", json(e != null ? e.getDisplayName() : Component.translatable("association.beyondsolo.office.desk"), reg));
        AssociationRecord r = record(p);
        Standing st = r.standing();
        PlayerRank rank = SystemAPI.get(p).rank();
        t.putInt("rank", rank.ordinal());
        t.putLong("merit", r.merit);
        t.putInt("standing", st.ordinal());
        t.putLong("from", st.merit());
        t.putLong("to", st.next() == null ? -1 : st.next().merit());
        t.putInt("contracts", r.contracts);
        t.putInt("clears", r.clears);
        t.putInt("suppressions", r.suppressions);
        t.putInt("exams", r.exams);
        t.putLong("bounties", r.bounties);
        long now = p.server.overworld().getGameTime();

        ListTag board = new ListTag();
        for (Offer o : board(p, s.office())) {
            CompoundTag c = gateTag(p, o.gate(), s.office(), now, reg);
            c.putInt("kind", o.kind());
            c.putLong("gold", o.gold());
            c.putInt("merit", o.merit());
            c.putBoolean("held", questOn(p, o.gate().id(), CONTRACT) != null);
            board.add(c);
        }
        t.put("board", board);
        ListTag held = new ListTag();
        for (Quest q : QuestEngine.data(p).active()) {
            if (!q.template().equals(CONTRACT) && !q.template().equals(PERMIT)) continue;
            UUID gate = gateOf(q);
            GateInstance g = gate == null ? null : GateData.get(p.server).get(gate);
            if (g == null) continue;
            CompoundTag c = gateTag(p, g, p.blockPosition(), now, reg);
            c.putBoolean("permit", q.template().equals(PERMIT));
            c.putInt("kind", q.extra().getInt(KIND));
            c.putLong("gold", q.extra().getLong(REWARD_GOLD));
            c.putLong("merit", q.extra().getLong(REWARD_MERIT));
            held.add(c);
        }
        t.put("held", held);
        t.putInt("slots", st.contractSlots());
        t.putInt("radius", AssociationConfig.watchRadius());
        t.putInt("holding", contracts(p).size());

        t.putInt("permitsLeft", permitsLeft(p));
        t.putInt("permitsPerDay", st.permitsPerDay());
        t.putInt("permitTop", permitTop(p).ordinal());
        long[] fees = new long[PlayerRank.values().length];
        for (PlayerRank pr : PlayerRank.values()) fees[pr.ordinal()] = permitFee(pr);
        t.putLongArray("permitFees", fees);

        CompoundTag exam = new CompoundTag();
        PlayerRank next = examRank(p);
        exam.putInt("next", next == null ? -1 : next.ordinal());
        if (next != null) {
            exam.putLong("fee", examFee(next));
            exam.putInt("merit", AssociationConfig.examMerit(next));
        }
        String block = examBlock(p);
        exam.putString("block", block == null ? "" : block);
        exam.putLong("wait", Math.max(0, r.examFrom - QuestEngine.day(p)));
        exam.putInt("minutes", AssociationConfig.examTicks() / 1200);
        exam.putInt("passed", r.exams);
        exam.putInt("failed", r.examsFailed);
        Quest q = examQuest(p);
        UUID gate = q == null ? null : gateOf(q);
        GateInstance g = gate == null ? null : GateData.get(p.server).get(gate);
        if (g != null) {
            exam.put("gate", gateTag(p, g, p.blockPosition(), now, reg));
            exam.putLong("deadline", q.deadline());
        }
        t.put("exam", exam);

        ListTag national = new ListTag();
        List<Row> rows = national(p);
        int place = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).me()) place = i + 1;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (i >= 10 && !row.me()) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("place", i + 1);
            c.putString("name", row.name());
            c.putInt("rank", row.rank().ordinal());
            c.putLong("merit", row.merit());
            c.putBoolean("player", row.player());
            c.putBoolean("me", row.me());
            c.putString("guild", json(row.guild(), reg));
            national.add(c);
        }
        t.put("national", national);
        t.putInt("place", place);
        t.putInt("hunters", rows.size());
        // hands for hire, and how the hunter has fared against the other parties
        t.put("hire", com.beyondsolo.rival.Hires.officeTag(p, s.office()));
        // the great guilds' offers and the hunter's contract
        t.put("guilds", com.beyondsolo.scouting.Scouting.officeTag(p));
        // the national-level hunters: the hunter's duels with them and the challenge standing
        t.put("duels", com.beyondsolo.duel.Duels.officeTag(p));
        return t;
    }

    /** A gate as the windows show it: rank, how far and which way from {@code from}, its dungeon's name, its break clock. */
    static CompoundTag gateTag(ServerPlayer p, GateInstance g, BlockPos from, long now, HolderLookup.Provider reg) {
        CompoundTag c = new CompoundTag();
        c.putUUID("gate", g.id());
        c.putInt("rank", g.rank().ordinal());
        Vec3 at = Vec3.atCenterOf(from);
        c.putInt("dist", Mth.floor(Math.sqrt(GateSpawner.flatDistSqr(at, g.gatePos())) + 0.5));
        c.putString("dir", json(GateSpawner.direction(at, g.gatePos()), reg));
        c.putString("realm", json(g.dungeonName(), reg));
        BreakState b = g.breakState();
        c.putLong("left", b.broken() ? 0 : b.clockStarted() && b.phase() != BreakState.Phase.STABLE ? b.left(now) : -1);
        c.putBoolean("broken", b.broken());
        c.putBoolean("scouted", record(p).scouted(g.id()));
        c.putBoolean("above", g.rank().tier() > SystemAPI.get(p).rank().tier());
        return c;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        Quest q = event.getQuest();
        if (!ours(q)) return;
        ServerPlayer p = event.getPlayer();
        AssociationRecord r = record(p);
        long gold = q.extra().getLong(REWARD_GOLD);
        if (gold > 0) {
            Economy.add(p, gold, Economy.Reason.ASSOCIATION);
            r.bounties += gold;
        }
        if (q.template().equals(CONTRACT) || q.template().equals(PERMIT)) {
            r.contracts++;
            if (r.contracts == BOUNTY_HUNTER) SystemAPI.setTitle(p, "title.beyondsolo.association_bounty_hunter");
        }
        if (q.template().equals(EXAM)) passed(p, q);
        addMerit(p, q.extra().getLong(REWARD_MERIT));
    }

    @SubscribeEvent
    static void onFailed(QuestEvent.Failed event) {
        Quest q = event.getQuest();
        if (!ours(q)) return;
        ServerPlayer p = event.getPlayer();
        if (q.template().equals(CONTRACT) || q.template().equals(PERMIT)) record(p).contractsFailed++;
        if (q.template().equals(EXAM)) examFailed(p, q);
        // a permit given back frees its gate for anyone
        UUID gate = gateOf(q);
        if (q.template().equals(PERMIT) && gate != null) {
            AssociationData data = AssociationData.get(p.server);
            AssociationData.Claim c = data.claim(gate);
            if (c != null && c.kind() == AssociationData.Kind.PERMIT && c.holder().equals(p.getUUID())) data.remove(gate);
        }
    }

    /** Dying ends an exam. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Quest q = examQuest(p);
        if (q != null) QuestEngine.fail(p, q, QuestEvent.FailReason.DIED);
    }

    /** Once a second: contracts whose gate closed without the hunter fail, leads found are done. */
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 11) return;
        check(p);
        Session s = SESSIONS.get(p.getUUID());
        if (s != null && !atDesk(p, s)) SESSIONS.remove(p.getUUID());
    }

    /** Checks a hunter's association quests against their gates. */
    public static void check(ServerPlayer p) {
        List<Quest> mine = new ArrayList<>();
        for (Quest q : QuestEngine.data(p).active()) if (ours(q)) mine.add(q);
        if (mine.isEmpty()) return;
        GateData data = GateData.get(p.server);
        for (Quest q : mine) {
            UUID gate = gateOf(q);
            if (gate == null) continue;
            GateInstance g = data.get(gate);
            if (q.template().equals(TIP)) {
                if (g == null || g.state() == GateInstance.State.CLOSED) {
                    QuestEngine.fail(p, q, QuestEvent.FailReason.LOST, Component.translatable("quest.beyondsolo.association.outcome.gone"));
                } else if (p.level().dimension().equals(g.gateDimension()) && GateSpawner.flatDistSqr(p.position(), g.gatePos()) < FOUND * FOUND) {
                    QuestEngine.progress(p, tipKey(gate), 1);
                }
                continue;
            }
            boolean gone = g == null || g.state() != GateInstance.State.OPEN;
            if (!gone && q.template().equals(EXAM) && g.breakState().broken()) gone = true;
            if (gone) QuestEngine.fail(p, q, QuestEvent.FailReason.LOST, Component.translatable("quest.beyondsolo.association.outcome.closed"));
        }
    }

    /** Once a minute: claims on gates no longer open, or broken out into the world, are let go. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 1200 != 600) return;
        AssociationData data = AssociationData.get(server);
        if (data.claims().isEmpty()) return;
        GateData gates = GateData.get(server);
        List<UUID> keep = new ArrayList<>();
        for (UUID id : data.claims().keySet()) {
            GateInstance g = gates.get(id);
            if (g != null && g.state() == GateInstance.State.OPEN && !g.breakState().hasBroken()) keep.add(id);
        }
        data.keepOnly(keep);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        AssociationPosts.clear();
    }

    /** /solo wipe: the hunter's claims go with their record. */
    public static void wipe(ServerPlayer p) {
        AssociationData.get(p.server).forget(p.getUUID());
        SESSIONS.remove(p.getUUID());
    }

    private Association() {}
}
