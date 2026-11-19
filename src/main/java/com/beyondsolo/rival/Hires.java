package com.beyondsolo.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationData;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.economy.HunterData;
import com.beyondsolo.economy.SystemStorage;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hands for hire at the association's office: porters (rank E or D, a great pack on their back) who pick up what falls
 * and carry it, and freelance hunters (up to the hunter's own rank) who fight beside them. Each office has a few of
 * each looking for work every day; a hunter pays up front and the job lasts {@code hire.minutes}, or until they walk
 * out of a gate they cleared together. One porter and {@code hire.maxHunters} hunters at a time. Hired hands go
 * wherever their hunter goes in the world and into gate caves (not into a trial or an exam, which are taken alone),
 * and step out beside them when left behind. A porter hands their pack over when the job ends (into the inventory, the
 * System's storage, then at the hunter's feet); one who falls drops it where they fell.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Hires {
    static final String KEY = "rival.beyondsolo.hire.";
    /** A porter's tag (worn leather). */
    public static final int COLOR = 0xD9B26F;
    /** A freelance hunter's tag (the association's steel). */
    public static final int HUNTER_COLOR = 0x8FB8D8;
    /** A hired hand further than this from their hunter steps out beside them. */
    static final int FAR = 40;

    /** What hired hands work for: their hunter, near them, for as long as the job lasts. */
    public static final GuildHunterEntity.Cause CAUSE = new GuildHunterEntity.Cause() {
        @Override
        public boolean keeps(GuildHunterEntity e) {
            if (!(e.level() instanceof ServerLevel level)) return false;
            ServerPlayer owner = OnlinePlayers.get(level.getServer(), ownerOf(e));
            if (owner == null || owner.level() != e.level() || !allowed(owner)) return false;
            Hire h = record(owner).hire(hireOf(e));
            return h != null && e.getUUID().equals(h.body) && !h.expired(now(level.getServer()));
        }

        @Override
        public @Nullable ServerPlayer leader(GuildHunterEntity e) {
            if (!(e.level() instanceof ServerLevel level)) return null;
            ServerPlayer owner = OnlinePlayers.get(level.getServer(), ownerOf(e));
            return owner != null && owner.level() == e.level() && owner.isAlive() ? owner : null;
        }

        @Override
        public void fell(ServerLevel level, GuildHunterEntity e) {
            Hires.fell(level, e);
        }
    };

    /** An offer on an office's list today. */
    public record Offer(UUID id, Hire.Kind kind, String name, PlayerClass cls, int level, int skin, long fee) {
        public PlayerRank rank() {
            return Balance.rankFor(level);
        }
    }

    public static RivalRecord record(Player p) {
        return p.getData(RivalModule.RECORD);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    @Nullable
    static UUID ownerOf(GuildHunterEntity e) {
        return e instanceof PorterEntity p ? p.owner() : e.guildId();
    }

    @Nullable
    static UUID hireOf(GuildHunterEntity e) {
        return e instanceof PorterEntity p ? p.hire() : e.hunterId();
    }

    // ================================================================ the office's list

    /**
     * Who is looking for work at the office at {@code office} today, for this hunter: two porters (an E and a D) and
     * {@code hire.offers} freelance hunters of the hunter's rank or one below. The same faces all day; the ones hired
     * today are gone from it.
     */
    public static List<Offer> offers(ServerPlayer p, BlockPos office) {
        long day = QuestEngine.day(p);
        RivalRecord rec = record(p);
        RandomSource r = RandomSource.create(office.asLong() * 31 + day * 0x9E3779B97F4A7C15L ^ p.getUUID().getLeastSignificantBits());
        PlayerRank top = SystemAPI.isAwakened(p) ? SystemAPI.get(p).rank() : PlayerRank.E;
        List<Offer> out = new ArrayList<>();
        out.add(roll(r, Hire.Kind.PORTER, PlayerRank.E, null));
        out.add(roll(r, Hire.Kind.PORTER, PlayerRank.D, null));
        for (int i = 0; i < RivalConfig.offers(); i++) {
            int tier = Math.max(0, top.tier() - (r.nextInt(3) == 0 ? 1 : 0));
            out.add(roll(r, Hire.Kind.HUNTER, PlayerRank.values()[tier], null));
        }
        out.removeIf(o -> rec.taken(day, o.id()));
        return out;
    }

    /** One hand looking for work: of the rank, mostly near its start, their class as it falls (or {@code cls}). */
    static Offer roll(RandomSource r, Hire.Kind kind, PlayerRank rank, @Nullable PlayerClass cls) {
        int from = Recruits.bandStart(rank), to = Recruits.bandEnd(rank);
        int level = Math.min(to, from + (int) Math.floor(Math.pow(r.nextDouble(), 1.5) * (to - from + 1)));
        PlayerClass[] all = PlayerClass.values();
        PlayerClass c = all[r.nextInt(all.length)];
        if (cls != null) c = cls;
        return new Offer(new UUID(r.nextLong(), r.nextLong()), kind, Recruits.name(r), c, Math.max(1, level),
                r.nextInt(GuildHunter.SKINS.length), fee(kind, level));
    }

    /** What a hand of this level asks for a job: a porter their rank's fee; a hunter a share of their rank's clear, more deeper into it. */
    public static long fee(Hire.Kind kind, int level) {
        PlayerRank rank = Balance.rankFor(level);
        if (kind == Hire.Kind.PORTER) return RivalConfig.porterFee(rank);
        double base = EconomyHooks.GATE_GOLD[rank.tier()] * RivalConfig.hunterFee();
        return Math.round(base * (1 + 0.03 * Math.max(0, level - Recruits.bandStart(rank))) / 10.0) * 10;
    }

    // ================================================================ hiring and letting go

    static Association.Outcome yes(String key, Object... args) {
        return new Association.Outcome(true, Component.translatable(KEY + "outcome." + key, args));
    }

    static Association.Outcome no(String key, Object... args) {
        return new Association.Outcome(false, Component.translatable(KEY + "outcome." + key, args));
    }

    /** Hires an offer off the office's list: paid now, on the job at once. */
    public static Association.Outcome hire(ServerPlayer p, BlockPos office, UUID offerId) {
        if (!SystemAPI.isAwakened(p)) return no("unregistered");
        Offer o = null;
        for (Offer x : offers(p, office)) if (x.id().equals(offerId)) o = x;
        if (o == null) return no("gone");
        RivalRecord rec = record(p);
        if (o.kind() == Hire.Kind.PORTER && rec.porter() != null) return no("porter_already");
        if (o.kind() == Hire.Kind.HUNTER && rec.hunters() >= RivalConfig.maxHunters()) return no("hunters_full", RivalConfig.maxHunters());
        if (o.kind() == Hire.Kind.HUNTER && o.rank().tier() > SystemAPI.get(p).rank().tier()) return no("rank");
        if (!Economy.spend(p, o.fee(), Economy.Reason.HIRE)) return no("gold", o.fee());
        rec.take(QuestEngine.day(p), o.id());
        Hire h = take(p, o);
        return yes(h.porter() ? "porter" : "hunter", h.name, RivalConfig.hireTicks() / 1200);
    }

    /** Puts an offer on the job for {@code p} (paid or not: commands hire for nothing). */
    public static Hire take(ServerPlayer p, Offer o) {
        long now = now(p.server);
        Hire h = new Hire(o.id(), o.kind(), o.name(), o.cls(), o.level(), o.skin(), o.fee(), now, now + RivalConfig.hireTicks(), RivalConfig.porterSlots());
        record(p).hires.add(h);
        if (allowed(p)) spawn(p, h);
        return h;
    }

    /** A hand of the rank for nothing (commands, tests); null if the hunter has as many of the kind as they may. */
    @Nullable
    public static Hire hireFree(ServerPlayer p, Hire.Kind kind, PlayerRank rank, @Nullable PlayerClass cls) {
        RivalRecord rec = record(p);
        if (kind == Hire.Kind.PORTER ? rec.porter() != null : rec.hunters() >= Math.max(1, RivalConfig.maxHunters())) return null;
        Offer o = roll(p.getRandom(), kind, rank, cls);
        return take(p, new Offer(o.id(), kind, o.name(), o.cls(), o.level(), o.skin(), 0));
    }

    /** Lets a hand go before the job is over (no refund). */
    public static Association.Outcome dismiss(ServerPlayer p, UUID hire) {
        Hire h = record(p).hire(hire);
        if (h == null) return no("gone");
        end(p, h, "dismissed");
        return yes("dismissed", h.name);
    }

    /** The job is over: they go, and a porter hands over the pack. */
    static void end(ServerPlayer p, Hire h, String why) {
        RivalRecord rec = record(p);
        if (!rec.hires.remove(h)) return;
        Entity body = body(p.server, h);
        if (body instanceof GuildHunterEntity e) e.leave();
        int handed = h.porter() ? handOver(p, h) : 0;
        Component text = Component.translatable(KEY + "notify.end." + why, h.name);
        if (h.porter() && handed > 0) text = text.copy().append(" ").append(Component.translatable(KEY + "notify.handed", handed));
        else if (!h.porter() && h.kills > 0) text = text.copy().append(" ").append(Component.translatable(KEY + "notify.kills", h.kills));
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.end.title"), text));
    }

    /** Everything in a porter's pack to their hunter: the inventory first, then the System's storage, then at their feet. */
    static int handOver(ServerPlayer p, Hire h) {
        int stacks = 0;
        HunterData data = Economy.data(p);
        int cap = SystemStorage.capacity(p);
        boolean stored = false;
        for (int i = 0; i < h.pack.size(); i++) {
            ItemStack s = h.pack.get(i);
            h.pack.set(i, ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            stacks++;
            p.getInventory().add(s);
            if (!s.isEmpty() && SystemAPI.hasSystem(p)) {
                int before = s.getCount();
                SystemStorage.insert(data.storage(), cap, s);
                stored |= s.getCount() != before;
            }
            if (!s.isEmpty()) p.drop(s.copy(), false);
        }
        if (stored) Economy.sync(p);
        return stacks;
    }

    /** A hired hand fell: the job is over (no refund), and a porter's pack lies where they fell. */
    static void fell(ServerLevel level, GuildHunterEntity e) {
        ServerPlayer p = OnlinePlayers.get(level.getServer(), ownerOf(e));
        if (p == null) return;
        Hire h = record(p).hire(hireOf(e));
        if (h == null) return;
        if (h.porter()) {
            for (int i = 0; i < h.pack.size(); i++) {
                ItemStack s = h.pack.get(i);
                h.pack.set(i, ItemStack.EMPTY);
                if (s.isEmpty()) continue;
                ItemEntity item = new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), s);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
        h.body = null;
        end(p, h, h.porter() ? "fell_porter" : "fell");
    }

    /** A gate's boss fell with them inside ({@code GateService.bossDefeated}): their hands' job ends once they walk out. */
    public static void gateCleared(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (!p.hasData(RivalModule.RECORD)) continue;
            for (Hire h : record(p).hires) h.done = true;
        }
    }

    /** Lets every hand go, a porter's pack handed over (the System taken back from the hunter: {@code /solo wipe}). */
    public static void wipe(ServerPlayer p) {
        if (!p.hasData(RivalModule.RECORD)) return;
        for (Hire h : List.copyOf(record(p).hires)) {
            Entity body = body(p.server, h);
            if (body != null) body.discard();
            if (h.porter()) handOver(p, h);
        }
        record(p).hires.clear();
    }

    /** The office's hiring tab: today's list, the hands on the job, and the hunter's record against the other parties. */
    public static CompoundTag officeTag(ServerPlayer p, BlockPos office) {
        CompoundTag t = new CompoundTag();
        RivalRecord rec = record(p);
        ListTag list = new ListTag();
        for (Offer o : offers(p, office)) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", o.id());
            c.putInt("kind", o.kind().ordinal());
            c.putString("name", o.name());
            c.putInt("class", o.cls().ordinal());
            c.putInt("level", o.level());
            c.putInt("rank", o.rank().ordinal());
            c.putInt("skin", o.skin());
            c.putLong("fee", o.fee());
            c.putInt("slots", o.kind() == Hire.Kind.PORTER ? RivalConfig.porterSlots() : 0);
            list.add(c);
        }
        t.put("offers", list);
        long now = now(p.server);
        ListTag hired = new ListTag();
        for (Hire h : rec.hires) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", h.id);
            c.putInt("kind", h.kind.ordinal());
            c.putString("name", h.name);
            c.putInt("class", h.cls.ordinal());
            c.putInt("level", h.level);
            c.putInt("rank", h.rank().ordinal());
            c.putInt("skin", h.skin);
            c.putLong("left", Math.max(0, h.until - now));
            c.putBoolean("done", h.done);
            c.putInt("kills", h.kills);
            c.putInt("carried", h.carried());
            c.putInt("slots", h.pack.size());
            hired.add(c);
        }
        t.put("hired", hired);
        t.putInt("hunters", rec.hunters());
        t.putInt("maxHunters", RivalConfig.maxHunters());
        t.putBoolean("porter", rec.porter() != null);
        t.putInt("minutes", RivalConfig.hireTicks() / 1200);
        t.putInt("raceWins", rec.raceWins);
        t.putInt("raceLosses", rec.raceLosses);
        t.putInt("trespasses", rec.trespasses);
        return t;
    }

    // ================================================================ their bodies

    /** Where hired hands may go with their hunter: the world, and gate caves that aren't a trial or an exam. */
    static boolean allowed(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || GateService.inTransit(p)) return false;
        GateInstance cave = caveOf(p);
        if (cave != null) {
            if (cave.isTrial()) return false;
            AssociationData.Claim c = AssociationData.get(p.server).claim(cave.id());
            return c == null || c.kind() != AssociationData.Kind.EXAM;
        }
        return p.level().dimension().equals(Level.OVERWORLD);
    }

    /** The gate cave the hunter stands in, or null. */
    @Nullable
    static GateInstance caveOf(ServerPlayer p) {
        return GateService.isDungeon(p.level()) ? GateData.get(p.server).bySlotPos(p.blockPosition()) : null;
    }

    /** A hand's body, wherever it is loaded, or null. */
    @Nullable
    static Entity body(MinecraftServer server, Hire h) {
        if (h.body == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(h.body);
            if (e != null && e.isAlive() && !e.isRemoved()) return e;
        }
        return null;
    }

    /** The hand steps out of the air beside their hunter. */
    static void spawn(ServerPlayer p, Hire h) {
        ServerLevel level = p.serverLevel();
        GuildHunterEntity e;
        if (h.porter()) {
            PorterEntity porter = RivalModule.PORTER.get().create(level);
            if (porter == null) return;
            porter.setup(p, h);
            e = porter;
        } else {
            e = GuildModule.HUNTER.get().create(level);
            if (e == null) return;
            e.serve(CAUSE, new RaidState.Hired(p.getUUID(), h.id, h.name, h.cls, h.level, h.skin), HUNTER_COLOR, "");
            e.setCustomName(Component.literal("[").append(Component.translatable(KEY + "tag")).append("] ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(HUNTER_COLOR)))
                    .append(Component.literal(h.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))));
        }
        e.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
        level.addFreshEntity(e);
        h.body = e.getUUID();
        Raids.slipTo(e, p);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 5) return;
        long now = now(server);
        for (ServerPlayer p : OnlinePlayers.all(server)) {
            if (!p.hasData(RivalModule.RECORD)) continue;
            RivalRecord rec = record(p);
            if (rec.hires.isEmpty()) continue;
            boolean inCave = caveOf(p) != null;
            for (Hire h : List.copyOf(rec.hires)) {
                if (h.expired(now)) end(p, h, "time");
                else if (h.done && !inCave) end(p, h, "done");
                else if (allowed(p)) {
                    Entity body = body(server, h);
                    if (body != null && body.level() == p.level() && body.distanceToSqr(p) < FAR * FAR) continue;
                    if (body != null) body.discard();
                    spawn(p, h);
                }
            }
        }
    }

    /** A hired hunter's kills, for the end of the job. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof GuildHunterEntity e) || e.cause() != CAUSE) return;
        if (!(e.level() instanceof ServerLevel level)) return;
        ServerPlayer p = OnlinePlayers.get(level.getServer(), ownerOf(e));
        Hire h = p == null ? null : record(p).hire(hireOf(e));
        if (h != null) h.kills++;
    }

    private Hires() {}
}
