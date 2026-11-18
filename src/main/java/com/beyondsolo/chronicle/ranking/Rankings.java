package com.beyondsolo.chronicle.ranking;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.RankingsPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.RushTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The rankings: the world's hunters (players and the rival roster) by power, by how high they have climbed the
 * Crimson Citadel, by boss rush time per tier, and by gates cleared. A hunter's power is ten times where they stand on
 * the hunter scale ({@code HiddenLevel.hunterLevel}: their level and every stat point they have). Climbing past a rival
 * on the hunters' board is announced.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Rankings {
    public static final String HUNTERS = "hunters";
    public static final String CITADEL = "citadel";
    public static final String RUSH = "rush";
    public static final String GATES = "gates";
    /** How often a player's standing is taken while they play (ticks). */
    public static final int REFRESH = 1200;

    /** One row of a board. */
    public record Row(String name, boolean rival, @Nullable UUID player, PlayerRank rank, int playerClass, String guild, String title,
                      long value, long value2) {
        CompoundTag save(@Nullable UUID me) {
            CompoundTag t = new CompoundTag();
            t.putString("n", name);
            if (rival) t.putBoolean("rv", true);
            if (player != null && player.equals(me)) t.putBoolean("me", true);
            t.putInt("r", rank.ordinal());
            t.putInt("c", playerClass);
            if (!guild.isEmpty()) t.putString("g", guild);
            if (!title.isEmpty()) t.putString("t", title);
            t.putLong("v", value);
            if (value2 != 0) t.putLong("w", value2);
            return t;
        }
    }

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    public static long power(float level) {
        return Math.round(level * 10);
    }

    private static PlayerRank rank(int ordinal) {
        PlayerRank[] all = PlayerRank.values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    // ------------------------------------------------------------------ boards

    public static List<Row> hunters(MinecraftServer server) {
        long day = day(server);
        List<Row> rows = new ArrayList<>();
        for (Rival r : Rivals.ALL) {
            rows.add(new Row(r.name(), true, null, r.rank(), r.playerClass().ordinal(), r.guild(), "", power(r.levelOn(day)), 0));
        }
        for (RankingData.Hunter h : RankingData.get(server).all()) {
            rows.add(new Row(h.name, false, h.id, rank(h.rank), h.playerClass, "", h.title, power(h.level), 0));
        }
        rows.sort(Comparator.comparingLong(Row::value).reversed().thenComparing(Row::rival));
        return rows;
    }

    public static List<Row> citadel(MinecraftServer server) {
        List<Row> rows = new ArrayList<>();
        for (Rival r : Rivals.ALL) {
            if (r.floor() > 0) rows.add(new Row(r.name(), true, null, r.rank(), r.playerClass().ordinal(), r.guild(), "", r.floor(), r.spire()));
        }
        for (RankingData.Hunter h : RankingData.get(server).all()) {
            rows.add(new Row(h.name, false, h.id, rank(h.rank), h.playerClass, "", h.title, h.floor, h.spire));
        }
        rows.sort(Comparator.comparingLong(Row::value).thenComparingLong(Row::value2).reversed().thenComparing(Row::rival));
        return rows;
    }

    /** One rush tier's board: fastest first; players with no time yet at the end. */
    public static List<Row> rush(MinecraftServer server, RushTier tier) {
        List<Row> rows = new ArrayList<>();
        for (Rival r : Rivals.ALL) {
            long t = r.rushTicks(tier);
            if (t > 0) rows.add(new Row(r.name(), true, null, r.rank(), r.playerClass().ordinal(), r.guild(), "", t, 0));
        }
        for (RankingData.Hunter h : RankingData.get(server).all()) {
            long t = h.rush[tier.ordinal()];
            rows.add(new Row(h.name, false, h.id, rank(h.rank), h.playerClass, "", h.title, t, 0));
        }
        rows.sort(Comparator.comparingLong((Row r) -> r.value() < 0 ? Long.MAX_VALUE : r.value()).thenComparing(Row::rival));
        return rows;
    }

    public static List<Row> gates(MinecraftServer server) {
        long day = day(server);
        List<Row> rows = new ArrayList<>();
        for (Rival r : Rivals.ALL) rows.add(new Row(r.name(), true, null, r.rank(), r.playerClass().ordinal(), r.guild(), "", r.gatesOn(day), 0));
        for (RankingData.Hunter h : RankingData.get(server).all()) {
            rows.add(new Row(h.name, false, h.id, rank(h.rank), h.playerClass, "", h.title, h.gates, 0));
        }
        rows.sort(Comparator.comparingLong(Row::value).reversed().thenComparing(Row::rival));
        return rows;
    }

    /** The player's place (1 = first) on the hunters' board, or 0 if they are not on it. */
    public static int place(ServerPlayer p) {
        List<Row> rows = hunters(p.server);
        for (int i = 0; i < rows.size(); i++) if (p.getUUID().equals(rows.get(i).player())) return i + 1;
        return 0;
    }

    /** Every board, for the client. */
    public static CompoundTag boards(ServerPlayer p) {
        MinecraftServer server = p.server;
        UUID me = p.getUUID();
        CompoundTag tag = new CompoundTag();
        tag.put(HUNTERS, list(hunters(server), me));
        tag.put(CITADEL, list(citadel(server), me));
        tag.put(GATES, list(gates(server), me));
        CompoundTag rush = new CompoundTag();
        for (RushTier t : RushTier.values()) rush.put(t.id(), list(rush(server, t), me));
        tag.put(RUSH, rush);
        tag.putLong("day", day(server));
        return tag;
    }

    private static ListTag list(List<Row> rows, UUID me) {
        ListTag out = new ListTag();
        for (Row r : rows) out.add(r.save(me));
        return out;
    }

    /** Takes the player's standing and sends them the boards. */
    public static void send(ServerPlayer p) {
        RankingData.get(p.server).update(p);
        if (!(p instanceof FakePlayer)) PacketDistributor.sendToPlayer(p, new RankingsPayload(boards(p)));
    }

    /** Takes the player's standing; a better place on the hunters' board than ever before is announced. */
    public static void refresh(ServerPlayer p) {
        RankingData data = RankingData.get(p.server);
        RankingData.Hunter h = data.update(p);
        if (h == null) return;
        int place = place(p);
        if (place <= 0) return;
        int best = h.bestPlace;
        if (best == 0 || place < best) data.setBestPlace(h, place);
        if (best == 0 || place >= best || !SystemAPI.isAwakened(p)) return;
        List<Row> rows = hunters(p.server);
        Component body = place == 1 ? Component.translatable("notify.beyondsolo.rankings.first")
                : Component.translatable("notify.beyondsolo.rankings.next", rows.get(place - 2).name(), rows.get(place - 2).value());
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("notify.beyondsolo.rankings.title", place), body));
    }

    @SubscribeEvent
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) refresh(p);
    }

    @SubscribeEvent
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) RankingData.get(p.server).update(p);
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % REFRESH == 600) refresh(p);
    }

    private Rankings() {}
}
