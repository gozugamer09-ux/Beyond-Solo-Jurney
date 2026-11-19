package com.beyondsolo.association;

import com.beyondsolo.breaks.BreakState;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.network.AssociationSyncPayload;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestGiver;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The gate broker who hangs about every association post: they deal in what the association doesn't say out loud.
 *
 * <ul>
 * <li><b>Whereabouts.</b> For a price ({@code broker.tipPrice} times a clear's gold) they name a gate of the rank a
 * hunter asks for that the hunter has not heard of, up to {@code broker.tipRange} away: the nearest they know, or, if
 * they know of none, one they swear is about to tear open (and it does). The hunter gets a lead with a waypoint, done
 * once they reach the gate. A few a day.</li>
 * <li><b>Scouting.</b> For less ({@code broker.scoutPrice}) they tell what waits behind a gate the hunter knows of
 * (within {@code broker.scoutRange}): its dungeon and layout, its boss, its element and weather, the rumour of a
 * hidden chamber, how many caverns deep it runs, how long it holds. The report is kept in the hunter's file.</li>
 * </ul>
 */
public final class Broker {
    /** Gates the scouting list shows at most. */
    public static final int SCOUT_LIST = 8;

    /** A hunter speaks to the broker. */
    public static void talk(ServerPlayer p, AssociationNpc broker) {
        if (!SystemAPI.isAwakened(p)) {
            Association.say(p, broker, "npc.beyondsolo.association.broker.unregistered");
            return;
        }
        open(p, new Association.Session(broker.getId(), broker.level().dimension(), broker.blockPosition(), Association.BROKER), null);
    }

    /** Opens the broker's window where the player stands (commands). */
    public static void openHere(ServerPlayer p) {
        open(p, new Association.Session(0, p.level().dimension(), p.blockPosition(), Association.BROKER), null);
    }

    static void open(ServerPlayer p, Association.Session s, @Nullable Association.Outcome outcome) {
        Association.remember(p, s);
        CompoundTag t = tag(p, s);
        if (outcome != null) outcome.write(t, p.registryAccess());
        PacketDistributor.sendToPlayer(p, new AssociationSyncPayload(Association.BROKER, t));
    }

    // ------------------------------------------------------------------ prices

    public static long tipPrice(ServerPlayer p, PlayerRank rank) {
        return Math.round(EconomyHooks.GATE_GOLD[rank.tier()] * AssociationConfig.tipPrice() * Association.record(p).standing().brokerPrice());
    }

    public static long scoutPrice(ServerPlayer p, PlayerRank rank) {
        return Math.round(EconomyHooks.GATE_GOLD[rank.tier()] * AssociationConfig.scoutPrice() * Association.record(p).standing().brokerPrice());
    }

    public static int tipsLeft(ServerPlayer p) {
        return Math.max(0, AssociationConfig.tipsPerDay() - Association.record(p).tipsBought(QuestEngine.day(p)));
    }

    // ------------------------------------------------------------------ whereabouts

    /** The nearest gate of that rank within reach of the broker that the hunter has not heard of, or null. */
    @Nullable
    public static GateInstance unknown(ServerPlayer p, BlockPos broker, PlayerRank rank) {
        Vec3 at = Vec3.atCenterOf(broker);
        double range = AssociationConfig.tipRange();
        GateInstance best = null;
        double bestD = range * range;
        for (GateInstance g : GateData.get(p.server).all()) {
            if (g.rank() != rank || !Association.contractable(p.server, g) || g.breakState().broken()) continue;
            if (GateSpawner.knows(p, g)) continue;
            double d = GateSpawner.flatDistSqr(at, g.gatePos());
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    /** Buys the whereabouts of a gate of that rank. */
    public static Association.Outcome tip(ServerPlayer p, Association.Session s, PlayerRank rank) {
        if (!SystemAPI.isAwakened(p)) return Association.Outcome.no("unregistered");
        if (tipsLeft(p) <= 0) return Association.Outcome.no("tip.none_left");
        long price = tipPrice(p, rank);
        if (Economy.gold(p) < price) return Association.Outcome.no("gold", price);
        GateInstance g = unknown(p, s.office(), rank);
        boolean fresh = g == null;
        if (fresh) {
            // they know of none: but one is about to tear open, they swear, and it does
            if (!p.level().dimension().equals(Level.OVERWORLD)) return Association.Outcome.no("overworld");
            GateBlockEntity block = GateSpawner.trySpawnNear(p.serverLevel(), p, rank, false);
            g = block == null || block.gateId() == null ? null : GateData.get(p.server).get(block.gateId());
            if (g == null) return Association.Outcome.no("no_spot");
        } else {
            GateSpawner.tell(p, g.id());
        }
        Economy.spend(p, price, Economy.Reason.BROKER);
        Association.record(p).buyTip(QuestEngine.day(p));
        QuestEngine.start(p, tipQuest(p, g, Association.giver(p, s)));
        Component dir = GateSpawner.direction(p.position(), g.gatePos());
        return Association.Outcome.yes(fresh ? "tip.fresh" : "tip.sold", rank.displayName(), Association.blocks(p, g), dir);
    }

    static Quest tipQuest(ServerPlayer p, GateInstance g, @Nullable QuestGiver giver) {
        CompoundTag extra = Association.gateExtra(g, "tip");
        Component rank = g.rank().displayName();
        QuestBuilder b = QuestBuilder.of(Association.TIP, QuestCategory.ASSOCIATION, g.rank().tier(), SystemAPI.get(p).level()).fixed()
                .title(Component.translatable("quest.beyondsolo.association.tip", rank))
                .description(Component.translatable("quest.beyondsolo.association.tip.desc", rank, Association.blocks(p, g),
                        GateSpawner.direction(p.position(), g.gatePos())))
                .line(Association.tipKey(g.id()), Component.translatable("quest_objective.beyondsolo.association.find", rank))
                .extra(extra);
        if (giver != null) b.giver(giver);
        return b.build();
    }

    // ------------------------------------------------------------------ scouting

    /** True if the hunter has heard of the gate: seen it, been told of it, or holds a quest on it. */
    public static boolean knowsOf(ServerPlayer p, GateInstance g) {
        if (GateSpawner.knows(p, g)) return true;
        for (Quest q : QuestEngine.data(p).active()) if (g.id().equals(Association.gateOf(q))) return true;
        return false;
    }

    /** The gates the hunter could have scouted: ones they know of within reach, nearest first. */
    public static List<GateInstance> scoutable(ServerPlayer p, BlockPos broker) {
        Vec3 at = Vec3.atCenterOf(broker);
        double range = AssociationConfig.scoutRange();
        List<GateInstance> out = new ArrayList<>();
        for (GateInstance g : GateData.get(p.server).all()) {
            if (g.state() != GateInstance.State.OPEN || g.isTrial() || g.isKeyed() || !g.gateDimension().equals(Level.OVERWORLD)) continue;
            if (g.breakState().hasBroken() || GateSpawner.flatDistSqr(at, g.gatePos()) > range * range || !knowsOf(p, g)) continue;
            out.add(g);
        }
        out.sort(Comparator.comparingDouble(g -> GateSpawner.flatDistSqr(at, g.gatePos())));
        return out;
    }

    /** Buys a scouting report on a gate. */
    public static Association.Outcome scout(ServerPlayer p, Association.Session s, UUID gate) {
        if (!SystemAPI.isAwakened(p)) return Association.Outcome.no("unregistered");
        GateInstance g = GateData.get(p.server).get(gate);
        if (g == null || g.state() != GateInstance.State.OPEN || g.breakState().hasBroken()) return Association.Outcome.no("scout.gone");
        if (!scoutable(p, s.office()).contains(g)) return Association.Outcome.no("scout.unknown");
        AssociationRecord r = Association.record(p);
        if (r.scouted(gate)) return Association.Outcome.no("scout.already");
        long price = scoutPrice(p, g.rank());
        if (!Economy.spend(p, price, Economy.Reason.BROKER)) return Association.Outcome.no("gold", price);
        r.scout(gate);
        return Association.Outcome.yes("scout.done", g.rank().displayName());
    }

    /** What the broker's people found behind a gate. */
    public static CompoundTag report(GateInstance g, long now, HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putString("realm", Association.json(g.dungeonName(), reg));
        t.putString("shape", Association.json(g.shape().displayName(), reg));
        t.putString("boss", Association.json(g.bossName(), reg));
        t.putBoolean("ascended", g.bossAscended());
        t.putString("element", Association.json(g.element().displayName(), reg));
        t.putString("weather", Association.json(g.depths().weather(g).title(), reg));
        HiddenKind hidden = g.depths().hidden(g);
        t.putString("hidden", hidden == null ? "" : Association.json(hidden.title(), reg));
        t.putInt("rooms", g.layout().rooms().size());
        BreakState b = g.breakState();
        t.putLong("left", b.clockStarted() ? b.left(now) : -1);
        return t;
    }

    // ------------------------------------------------------------------ what the window shows

    static CompoundTag tag(ServerPlayer p, Association.Session s) {
        HolderLookup.Provider reg = p.registryAccess();
        CompoundTag t = new CompoundTag();
        t.putInt("entity", s.entity());
        Entity e = s.entity() == 0 ? null : p.serverLevel().getEntity(s.entity());
        t.putString("name", Association.json(e != null ? e.getDisplayName() : Component.translatable("association.beyondsolo.broker.desk"), reg));
        AssociationRecord r = Association.record(p);
        t.putInt("rank", SystemAPI.get(p).rank().ordinal());
        t.putInt("standing", r.standing().ordinal());
        t.putBoolean("discount", r.standing().brokerPrice() < 1.0);
        t.putInt("tipsLeft", tipsLeft(p));
        t.putInt("tipsPerDay", AssociationConfig.tipsPerDay());
        ListTag tips = new ListTag();
        for (PlayerRank rank : PlayerRank.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("rank", rank.ordinal());
            c.putLong("price", tipPrice(p, rank));
            c.putBoolean("known", unknown(p, s.office(), rank) != null);
            tips.add(c);
        }
        t.put("tips", tips);
        long now = p.server.overworld().getGameTime();
        ListTag gates = new ListTag();
        List<GateInstance> list = scoutable(p, s.office());
        for (int i = 0; i < Math.min(SCOUT_LIST, list.size()); i++) {
            GateInstance g = list.get(i);
            CompoundTag c = Association.gateTag(p, g, s.office(), now, reg);
            c.putLong("price", scoutPrice(p, g.rank()));
            if (r.scouted(g.id())) c.put("report", report(g, now, reg));
            gates.add(c);
        }
        t.put("gates", gates);
        t.putInt("scoutRange", AssociationConfig.scoutRange());
        return t;
    }

    private Broker() {}
}
