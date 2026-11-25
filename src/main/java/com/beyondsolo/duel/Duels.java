package com.beyondsolo.duel;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.network.AssociationSyncPayload;
import com.beyondsolo.network.DuelPayload;
import com.beyondsolo.news.HunterNews;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.party.Parties;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.scouting.Scouting;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duels with the national-level hunters ({@link NationalHunter}): the four S-ranks at the top of the national rankings.
 *
 * <p><b>The challenge.</b> One of them comes for a hunter: to <i>test</i> a new S-rank (once, a few minutes after the
 * rank, {@code duel.testDelayMinutes}), to take back the <i>place</i> a hunter climbed past them on the hunters' board
 * (each once), to settle the <i>score</i> with a hunter who walked out on their guild's contract, or because the hunter
 * <i>asked</i> for it at the association's office (from rank {@code duel.minRank}). One challenge stands at a time. The
 * challenger walks up to the hunter wherever they are in the overworld (else word of it waits at the office) and waits
 * for an answer: <i>Accept</i>, <i>Not now</i> (they come back in {@code duel.returnMinutes}, twice at most) or
 * <i>Decline</i>. A challenge lapses after {@code duel.challengeMinutes}.</p>
 *
 * <p><b>The duel.</b> Fought where it was accepted: a ring of {@code duel.ringRadius} is marked out between them, the
 * challenger takes the far side, a count of three, the bell. Nobody comes between them: no third party's blow lands on
 * the hunter, monsters that wander in are put out, and nothing but the hunter (their blows, arrows, summons) touches
 * the challenger, who fights the hunter and the hunter's own. Nobody dies: a blow that would kill either of them makes
 * them yield. Standing outside the ring for {@code duel.outsideSeconds} forfeits; the time running out
 * ({@code duel.timeLimitSeconds}) is a draw. They fight at their own level, a dungeon boss's strength for it (a test, at
 * the hunter's level if that is lower), and at half health show what they really are.</p>
 *
 * <p><b>After.</b> A win pays a purse ({@code duel.purseGates} S-rank clears' gold), merit and System XP; the first win
 * brings the title Duelist, beating all four the title National-Level Hunter. A beaten national-level hunter stands
 * under the hunter on the hunters' board while the hunter's last duel with them is a win. Every result makes the hunter
 * news. The same one duels a hunter again only after {@code duel.rematchDays}.</p>
 *
 * <p>A duel is kept in memory only: a hunter leaving the game (or the server stopping) calls it off, and the challenge
 * stands.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Duels {
    public static final String KEY = "duel.beyondsolo.";
    /** The count of three before the bell (ticks). */
    public static final int COUNTDOWN = 60;
    /** How long the result stays up once it is over (ticks). */
    public static final int AFTER = 100;
    /** Players this near the ring see the duel: its ring, the challenger's bar. */
    public static final double WATCH = 64;
    /** Times a hunter may put a challenge off. */
    public static final int MAX_LATER = 2;

    private static final Map<UUID, Duel> RUNNING = new HashMap<>();

    public static DuelRecord record(Player p) {
        return p.getData(DuelModule.RECORD);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** The hunter's duel under way (or just over, while its result shows), or null. */
    @Nullable
    public static Duel running(UUID hunter) {
        return RUNNING.get(hunter);
    }

    public static Collection<Duel> all() {
        return Collections.unmodifiableCollection(RUNNING.values());
    }

    static Component t(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    /** A line of the hunter news about duels. */
    static Component nt(String key, Object... args) {
        return Component.translatable("news.beyondsolo.duel." + key, args);
    }

    static Association.Outcome yes(String key, Object... args) {
        return new Association.Outcome(true, t("outcome." + key, args));
    }

    static Association.Outcome no(String key, Object... args) {
        return new Association.Outcome(false, t("outcome." + key, args));
    }

    static String minutes(long ticks) {
        return String.valueOf(Math.max(1, Math.round(ticks / 1200.0)));
    }

    static String json(Component c, HolderLookup.Provider reg) {
        return Component.Serializer.toJson(c, reg);
    }

    /** True if a duel with them is allowed again (the rematch days since the last are over). */
    static boolean rested(DuelRecord r, NationalHunter h, long day) {
        return day - r.lastDay(h) >= DuelConfig.rematchDays();
    }

    /** What a won duel pays. */
    public static long purse() {
        return Math.round(EconomyHooks.GATE_GOLD[PlayerRank.S.tier()] * DuelConfig.purseGates() / 100.0) * 100;
    }

    /** The System XP a won duel brings a hunter of this level. */
    public static long xp(int level) {
        return Math.round(Progression.xpToNext(level) * DuelConfig.xpShare());
    }

    /** The level a duel with them is fought at: theirs, or the hunter's when they only come to test a new S-rank. */
    public static float fightLevel(ServerPlayer p, NationalHunter who, DuelRecord.Reason why) {
        float theirs = who.rival().levelOn(day(p.server));
        return why == DuelRecord.Reason.TEST ? Math.min(theirs, HiddenLevel.hunterLevel(SystemAPI.get(p))) : theirs;
    }

    // ================================================================ challenges

    @SubscribeEvent
    static void onRankChanged(SystemEvent.RankChanged event) {
        if (event.getNewRank() != PlayerRank.S || event.getOldRank() == PlayerRank.S) return;
        rankedS(event.getPlayer());
    }

    @SubscribeEvent
    static void onAwakened(SystemEvent.Awakened event) {
        if (event.getRank() == PlayerRank.S) rankedS(event.getPlayer());
    }

    /** The hunter is rank S: once, one of the four comes to see if it is real (after the guilds' scouts). */
    @Nullable
    public static DuelRecord.Challenge rankedS(ServerPlayer p) {
        DuelRecord r = record(p);
        if (r.rankedSAt == 0) r.rankedSAt = Math.max(1, now(p.server));
        if (r.tested || r.challenge != null || RUNNING.containsKey(p.getUUID())) return null;
        r.tested = true;
        return challenge(p, tester(p), DuelRecord.Reason.TEST, now(p.server) + DuelConfig.testDelayTicks());
    }

    /** Who comes to test a new S-rank: any of the four but the one of the hunter's own guild. */
    static NationalHunter tester(ServerPlayer p) {
        RivalGuild mine = Scouting.contractGuild(p);
        List<NationalHunter> pool = new ArrayList<>();
        for (NationalHunter h : NationalHunter.values()) if (mine == null || h.guild() != mine) pool.add(h);
        return pool.get(p.getRandom().nextInt(pool.size()));
    }

    /** A challenge made (one stands at a time): it opens at {@code openAt}, when the challenger sets out. */
    public static DuelRecord.Challenge challenge(ServerPlayer p, NationalHunter who, DuelRecord.Reason why, long openAt) {
        DuelRecord.Challenge c = new DuelRecord.Challenge(UUID.randomUUID(), who, why, openAt);
        record(p).challenge = c;
        return c;
    }

    /** The hunter climbed past one of them on the hunters' board: they come for their place (each once). */
    @Nullable
    public static DuelRecord.Challenge checkPassed(ServerPlayer p) {
        if (!SystemAPI.isAwakened(p)) return null;
        DuelRecord r = record(p);
        if (r.challenge != null || RUNNING.containsKey(p.getUUID())) return null;
        long day = day(p.server);
        long mine = Rankings.power(HiddenLevel.hunterLevel(SystemAPI.get(p)));
        for (NationalHunter h : NationalHunter.values()) {
            if (r.passed(h) || r.last(h) == DuelRecord.WON || !rested(r, h, day)) continue;
            if (mine <= Rankings.power(h.rival().levelOn(day))) continue;
            r.markPassed(h);
            return challenge(p, h, DuelRecord.Reason.PASSED, now(p.server) + 20 * 30);
        }
        return null;
    }

    /** The hunter walked out on a great guild's contract: its national-level hunter comes to settle it. */
    @Nullable
    public static DuelRecord.Challenge walkedOut(ServerPlayer p, RivalGuild guild) {
        NationalHunter h = NationalHunter.of(guild);
        if (h == null || record(p).challenge != null || RUNNING.containsKey(p.getUUID())) return null;
        return challenge(p, h, DuelRecord.Reason.GRUDGE, now(p.server) + 20 * 45);
    }

    /** The hunter asks one of them for a duel at the association. */
    public static Association.Outcome ask(ServerPlayer p, NationalHunter who) {
        if (!SystemAPI.isAwakened(p)) return no("unregistered");
        PlayerRank min = DuelConfig.minRank();
        if (SystemAPI.get(p).rank().tier() < min.tier()) return no("rank", min.displayName());
        if (RUNNING.containsKey(p.getUUID())) return no("dueling");
        DuelRecord r = record(p);
        if (r.challenge != null) return no("standing", r.challenge.who.displayName());
        long day = day(p.server);
        if (!rested(r, who, day)) return no("rematch", who.displayName(), r.lastDay(who) + DuelConfig.rematchDays() - day);
        challenge(p, who, DuelRecord.Reason.ASKED, now(p.server) + 20 * 8);
        return yes("asked", who.displayName());
    }

    // ================================================================ each second

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!RUNNING.isEmpty()) for (Duel d : new ArrayList<>(RUNNING.values())) tick(server, d);
        int t = server.getTickCount();
        if (t % 20 == 13) for (ServerPlayer p : OnlinePlayers.all(server)) tick(p);
        if (t % 1200 == 613) for (ServerPlayer p : OnlinePlayers.all(server)) minute(p);
    }

    /** Once a minute: an S-rank not yet tested is (those who were S before duels came too), and the board is checked. */
    static void minute(ServerPlayer p) {
        if (!SystemAPI.isAwakened(p)) return;
        DuelRecord r = record(p);
        if (!r.tested && SystemAPI.get(p).rank() == PlayerRank.S) rankedS(p);
        checkPassed(p);
    }

    /** A second of the hunter's challenge: it opens, the challenger sets out, it lapses. */
    public static void tick(ServerPlayer p) {
        DuelRecord r = record(p);
        DuelRecord.Challenge c = r.challenge;
        if (c == null || RUNNING.containsKey(p.getUUID())) return;
        long now = now(p.server);
        if (!c.open(now)) return;
        if (c.lapseAt <= 0) c.lapseAt = now + DuelConfig.challengeTicks();
        if (now >= c.lapseAt) {
            lapse(p, c);
            return;
        }
        if (!c.told) {
            c.told = true;
            boolean coming = canVisit(p) && send(p, c) != null;
            if (coming) c.visited = true;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, t("notify.title", c.who.displayName()),
                    coming ? t("notify.coming." + c.why.key(), c.who.displayName())
                            : t("notify.waiting." + c.why.key(), c.who.displayName(), minutes(c.lapseAt - now))));
            return;
        }
        // a challenger who could not come yet: they come once the hunter is back out in the world
        if (!c.visited && now - c.openAt > 20 * 5 && canVisit(p) && send(p, c) != null) c.visited = true;
    }

    /** True while a challenger can walk up to the hunter: out in the overworld, on their feet, not in a fight. */
    static boolean canVisit(ServerPlayer p) {
        return !RUNNING.containsKey(p.getUUID()) && Scouting.canVisit(p);
    }

    /** The challenge ran out unanswered. */
    static void lapse(ServerPlayer p, DuelRecord.Challenge c) {
        record(p).challenge = null;
        DuelistEntity e = challenger(p);
        if (e != null) e.leave(p);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, t("notify.title", c.who.displayName()),
                t("notify.lapsed", c.who.displayName())));
    }

    // ================================================================ the challenger

    /** The challenger sets out for the hunter from behind them, out of sight; null if there is nowhere to come from. */
    @Nullable
    public static DuelistEntity send(ServerPlayer p, DuelRecord.Challenge c) {
        Vec3 at = Scouting.spot(p.serverLevel(), p, p.getRandom());
        return at == null ? null : spawn(p, c, at);
    }

    /** The challenger at the hunter's side now (an answer given at the office): they step out of the air. */
    @Nullable
    static DuelistEntity sendNear(ServerPlayer p, DuelRecord.Challenge c) {
        ServerLevel level = p.serverLevel();
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        for (int i = 0; i < 12; i++) {
            double a = Math.atan2(look.z, look.x) + (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.5;
            Vec3 want = p.position().add(Math.cos(a) * 7, 0, Math.sin(a) * 7);
            Vec3 at = footing(level, want);
            if (Math.abs(at.y - p.getY()) > 3 || !stand(level, BlockPos.containing(at))) continue;
            DuelistEntity e = spawn(p, c, at);
            if (e != null) SkillFx.play(e, SkillFxPayload.Kind.BLINK, c.who.color, at, 1f, 1);
            return e;
        }
        return null;
    }

    @Nullable
    private static DuelistEntity spawn(ServerPlayer p, DuelRecord.Challenge c, Vec3 at) {
        ServerLevel level = p.serverLevel();
        dismiss(p, false);
        DuelistEntity e = DuelModule.DUELIST.get().create(level);
        if (e == null) return null;
        e.setup(p, c);
        float yaw = (float) (Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * Mth.RAD_TO_DEG) - 90f;
        e.moveTo(at.x, at.y, at.z, yaw, 0);
        e.setYHeadRot(yaw);
        level.addFreshEntity(e);
        return e;
    }

    /** The hunter's challenger for the challenge standing now, near them; or null. */
    @Nullable
    public static DuelistEntity challenger(ServerPlayer p) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null) return null;
        for (DuelistEntity e : p.serverLevel().getEntitiesOfClass(DuelistEntity.class, p.getBoundingBox().inflate(DuelistEntity.LOSE))) {
            if (p.getUUID().equals(e.hunter()) && c.id.equals(e.challenge()) && !e.leaving()) return e;
        }
        return null;
    }

    /** The hunter's challengers go (all of them, or only those not in a duel). */
    static void dismiss(ServerPlayer p, boolean all) {
        List<DuelistEntity> gone = new ArrayList<>();
        for (ServerLevel level : p.server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof DuelistEntity d && p.getUUID().equals(d.hunter())) gone.add(d);
            }
        }
        Duel running = RUNNING.get(p.getUUID());
        for (DuelistEntity d : gone) {
            if (!all && running != null && d.getId() == running.duelistId) continue;
            d.discard();
        }
    }

    /** What the challenger says, in the chat. */
    static void say(ServerPlayer p, Entity e, Component line) {
        p.sendSystemMessage(Component.translatable("npc.beyondsolo.association.says", e.getDisplayName(),
                line.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    /** A hunter speaks to a challenger: the challenge's window (if it is theirs to answer). */
    public static void talk(ServerPlayer p, DuelistEntity e) {
        if (!p.getUUID().equals(e.hunter())) {
            say(p, e, t("say.not_you"));
            return;
        }
        if (RUNNING.containsKey(p.getUUID())) return;
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || !c.id.equals(e.challenge()) || e.leaving()) {
            say(p, e, t("say.done"));
            return;
        }
        open(p, new Association.Session(e.getId(), e.level().dimension(), e.blockPosition(), Association.DUEL), null);
    }

    /** True while the challenge the challenger came with still stands (and is not being fought). */
    static boolean stillChallenging(ServerPlayer p, DuelistEntity e) {
        DuelRecord.Challenge c = record(p).challenge;
        return c != null && c.id.equals(e.challenge()) && !RUNNING.containsKey(p.getUUID());
    }

    /** The challenger reaches the hunter: their challenge, in their own words. */
    static void greet(ServerPlayer p, DuelistEntity e) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null) return;
        say(p, e, t("greet." + c.why.key() + "." + c.who.id, p.getGameProfile().getName(), c.who.guild().displayName()));
        p.displayClientMessage(t("hint").copy().withStyle(ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, e.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.NEUTRAL, 0.8f, 0.8f);
    }

    /** True while the hunter has this challenger's window open. */
    static boolean talking(ServerPlayer p, DuelistEntity e) {
        Association.Session s = Association.session(p);
        return s != null && s.screen() == Association.DUEL && s.entity() == e.getId();
    }

    /** The challenger waited long enough: the challenge stands at the association. */
    static void gaveUp(ServerPlayer p, DuelistEntity e) {
        say(p, e, t("say.gave_up"));
    }

    /** The challenger shows what they really are (half their strength gone). */
    static void unleashing(ServerPlayer p, DuelistEntity e) {
        say(p, e, t("say.unleash." + e.who().id));
        Duel d = RUNNING.get(p.getUUID());
        if (d != null) sync(p.server, d);
    }

    // ================================================================ answering

    /** Opens the challenger's window (or refreshes it), with the answer to the last button. */
    public static void open(ServerPlayer p, Association.Session s, @Nullable Association.Outcome outcome) {
        Association.remember(p, s);
        CompoundTag t = windowTag(p, s);
        if (outcome != null) outcome.write(t, p.registryAccess());
        PacketDistributor.sendToPlayer(p, new AssociationSyncPayload(Association.DUEL, t));
    }

    /** The duel begins: the hunter's association windows close. */
    public static void closeWindows(ServerPlayer p) {
        Association.forget(p);
        CompoundTag t = new CompoundTag();
        t.putBoolean("close", true);
        PacketDistributor.sendToPlayer(p, new AssociationSyncPayload(Association.DUEL, t));
    }

    /** Takes the challenge: the duel begins where the hunter stands (the challenger comes to them if they are not there). */
    public static Association.Outcome accept(ServerPlayer p) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null) return no("none");
        if (RUNNING.containsKey(p.getUUID())) return no("dueling");
        if (!c.open(now(p.server))) return no("not_yet", c.who.displayName());
        if (!canVisit(p)) return no("not_here");
        DuelistEntity e = challenger(p);
        if (e == null || e.distanceTo(p) > 24) {
            if (e != null) e.poof();
            e = sendNear(p, c);
        }
        if (e == null) return no("no_room");
        c.told = true;
        c.visited = true;
        return begin(p, e) == null ? no("no_room") : yes("accepted", c.who.displayName());
    }

    /** Puts it off: the challenger goes, and comes back in a few minutes (twice at most). */
    public static Association.Outcome later(ServerPlayer p) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null) return no("none");
        if (RUNNING.containsKey(p.getUUID())) return no("dueling");
        if (c.later >= MAX_LATER) return no("no_later", c.who.displayName());
        c.later++;
        c.openAt = now(p.server) + DuelConfig.returnTicks();
        c.lapseAt = 0;
        c.told = false;
        c.visited = false;
        DuelistEntity e = challenger(p);
        if (e != null) {
            say(p, e, t("say.later." + c.who.id, minutes(DuelConfig.returnTicks())));
            e.leave(p);
        }
        return yes("later", c.who.displayName(), minutes(DuelConfig.returnTicks()));
    }

    /** Turns it down: the challenger goes, and the news has it (unless the hunter was the one who asked). */
    public static Association.Outcome decline(ServerPlayer p) {
        DuelRecord r = record(p);
        DuelRecord.Challenge c = r.challenge;
        if (c == null) return no("none");
        if (RUNNING.containsKey(p.getUUID())) return no("dueling");
        r.challenge = null;
        r.declined++;
        DuelistEntity e = challenger(p);
        if (e != null) {
            say(p, e, t("say.declined." + c.who.id));
            e.leave(p);
        }
        if (c.why != DuelRecord.Reason.ASKED) {
            HunterNews.post(p.server, HunterNews.DUEL, nt("declined." + c.why.key(), name(p), c.who.displayName(), c.who.guild().displayName()),
                    c.who.color, false);
        }
        return yes("declined", c.who.displayName());
    }

    static Component name(ServerPlayer p) {
        return Component.literal(p.getGameProfile().getName());
    }

    // ================================================================ the duel

    /**
     * The duel begins: the ring is marked out with the hunter near one side and the challenger taking the other, and
     * the count of three starts. Null if there is no challenge to fight (or a duel is under way already).
     */
    @Nullable
    public static Duel begin(ServerPlayer p, DuelistEntity e) {
        if (RUNNING.containsKey(p.getUUID())) return null;
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || !(e.level() instanceof ServerLevel level) || p.level() != level) return null;
        Vec3 from = p.position();
        Vec3 dir = e.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.25) dir = Vec3.directionFromRotation(0, p.getYRot()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
        double radius = DuelConfig.ringRadius();
        double half = Math.max(3, Math.min(radius * 0.5, 6.5));
        Vec3 center = footing(level, from.add(dir.scale(half)));
        Vec3 spot = footing(level, center.add(dir.scale(half)));
        Duel d = new Duel(p.getUUID(), c.who, c.why, level.dimension(), center, radius, fightLevel(p, c.who, c.why));
        d.duelist = e.getUUID();
        d.duelistId = e.getId();
        d.hunterName = p.getGameProfile().getName();
        long now = level.getGameTime();
        d.startedAt = now;
        d.fightAt = now + COUNTDOWN;
        d.endsAt = d.fightAt + DuelConfig.timeLimitTicks();
        RUNNING.put(p.getUUID(), d);
        e.enter(d, spot);
        e.face(p.position());
        closeWindows(p);
        clearRing(level, d);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 0.7f);
        say(p, e, t("say.begin." + c.who.id));
        sync(level.getServer(), d);
        return d;
    }

    /** A tick of a duel: the count, the bell, the ring's rule, the clock. */
    static void tick(MinecraftServer server, Duel d) {
        ServerLevel level = server.getLevel(d.dimension);
        if (d.phase == Duel.Phase.OVER) {
            if (level == null || level.getGameTime() >= d.overAt + AFTER) RUNNING.remove(d.hunter, d);
            return;
        }
        ServerPlayer p = OnlinePlayers.get(server, d.hunter);
        if (level == null || p == null) {
            finish(server, d, Duel.Result.CALLED_OFF);
            return;
        }
        if (p.level() != level) {
            finish(server, d, Duel.Result.FORFEIT);
            return;
        }
        if (!(level.getEntity(d.duelistId) instanceof DuelistEntity e) || !e.isAlive() || !e.getUUID().equals(d.duelist)) {
            finish(server, d, Duel.Result.CALLED_OFF);
            return;
        }
        long now = level.getGameTime();
        if (d.phase == Duel.Phase.COUNTDOWN) {
            long left = d.fightAt - now;
            if (left > 0 && left % 20 == 0) p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.4f);
            if (now >= d.fightAt) {
                d.phase = Duel.Phase.FIGHT;
                e.fight();
                level.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.6f, 1.2f);
                sync(server, d);
            }
        } else {
            if (!d.inside(p.position(), 0.3)) {
                d.outside++;
                if (d.outside == 1 || d.outside % 20 == 0) {
                    long left = Math.max(1, (DuelConfig.outsideTicks() - d.outside + 19) / 20);
                    p.displayClientMessage(t("outside", left).copy().withStyle(ChatFormatting.RED), true);
                    sync(server, d);
                }
                if (d.outside >= DuelConfig.outsideTicks()) {
                    finish(server, d, Duel.Result.FORFEIT);
                    return;
                }
            } else if (d.outside > 0) {
                d.outside = 0;
                sync(server, d);
            }
            if (now >= d.endsAt) {
                finish(server, d, Duel.Result.DRAWN);
                return;
            }
        }
        if (now % 10 == 0) clearRing(level, d);
        if (now % 20 == 0) sync(server, d);
    }

    /** Monsters that wander into the ring are put out of it: nobody comes between them. */
    static void clearRing(ServerLevel level, Duel d) {
        AABB box = new AABB(d.center.x - d.radius - 1, d.center.y - 6, d.center.z - d.radius - 1,
                d.center.x + d.radius + 1, d.center.y + 10, d.center.z + d.radius + 1);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && !(m instanceof DuelistEntity)
                && Parties.culprit(m) == null && d.inside(m.position(), 0.5))) {
            Vec3 out = m.position().subtract(d.center).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : out.normalize();
            Vec3 to = footing(level, d.center.add(out.scale(d.radius + 3)));
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.6, m.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            m.teleportTo(to.x, to.y, to.z);
            m.getNavigation().stop();
        }
    }

    /** The floor under (or a little over) a point: where someone would stand there. */
    public static Vec3 footing(ServerLevel level, Vec3 at) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z), y = Mth.floor(at.y + 0.5);
        for (int dy = 0; dy <= 6; dy++) {
            if (stand(level, new BlockPos(x, y - dy, z))) return new Vec3(at.x, y - dy, at.z);
            if (dy > 0 && dy <= 4 && stand(level, new BlockPos(x, y + dy, z))) return new Vec3(at.x, y + dy, at.z);
        }
        return at;
    }

    private static boolean stand(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** Who the challenger's blows may land on: the hunter, and the hunter's own (summons, shadows) about the ring. */
    static List<LivingEntity> foes(ServerLevel level, ServerPlayer p, Duel d) {
        List<LivingEntity> out = new ArrayList<>();
        if (p.isAlive() && !p.isSpectator()) out.add(p);
        AABB box = new AABB(d.center.x - d.radius - 3, d.center.y - 8, d.center.z - d.radius - 3,
                d.center.x + d.radius + 3, d.center.y + 14, d.center.z + d.radius + 3);
        out.addAll(level.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive()
                && x instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())));
        return out;
    }

    /** Only the hunter (their blows, arrows, summons) may hurt the challenger, and only while they fight. */
    static boolean mayHurtDuelist(DuelistEntity e, DamageSource source) {
        UUID hunter = e.hunter();
        if (hunter == null) return false;
        Duel d = RUNNING.get(hunter);
        if (d == null || !d.fighting() || !e.getUUID().equals(d.duelist)) return false;
        return hunter.equals(Parties.culprit(source.getEntity()));
    }

    /** The challenger is at the end of their strength: they yield. */
    static void challengerYields(ServerPlayer p, DuelistEntity e) {
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || !d.fighting() || !e.getUUID().equals(d.duelist)) return;
        finishLater(p.server, d, Duel.Result.WON);
    }

    /** Nobody comes between them: no blow of a third party lands on the hunter, and nothing lands before the bell. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (RUNNING.isEmpty() || !(event.getEntity() instanceof ServerPlayer p)) return;
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || d.phase == Duel.Phase.OVER && d.settled) return;
        Entity by = event.getSource().getEntity();
        // the hunter's own doing (a fall, fire, their own blast) stays theirs
        if (by == null || by == p) return;
        // it is over: whatever of the challenger's was still on its way does not land
        if (d.phase == Duel.Phase.OVER) {
            if (by.getUUID().equals(d.duelist)) event.setCanceled(true);
            return;
        }
        if (d.phase == Duel.Phase.COUNTDOWN) {
            event.setCanceled(true);
            return;
        }
        if (by.getUUID().equals(d.duelist) || p.getUUID().equals(Parties.culprit(by))) return;
        event.setCanceled(true);
    }

    /** A blow that would kill the hunter makes them yield instead: no death, no totem used up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (RUNNING.isEmpty() || !(event.getEntity() instanceof ServerPlayer p)) return;
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || d.settled || d.phase == Duel.Phase.COUNTDOWN || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (event.getNewDamage() < p.getHealth()) return;
        event.setNewDamage(0);
        if (d.phase == Duel.Phase.FIGHT) finishLater(p.server, d, Duel.Result.LOST);
    }

    /** And should a death get through anyway, it does not happen (the void and /kill still kill, and end the duel). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (RUNNING.isEmpty() || !(event.getEntity() instanceof ServerPlayer p)) return;
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || d.settled) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            finishLater(p.server, d, Duel.Result.CALLED_OFF);
            return;
        }
        event.setCanceled(true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.3f));
        finishLater(p.server, d, d.phase == Duel.Phase.FIGHT ? Duel.Result.LOST : Duel.Result.CALLED_OFF);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Duel d = RUNNING.get(p.getUUID());
        if (d != null && d.phase != Duel.Phase.OVER) finish(p.server, d, Duel.Result.CALLED_OFF);
        dismiss(p, true);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }

    // ================================================================ the end of it

    /** Ends the duel now. */
    public static void finish(MinecraftServer server, Duel d, Duel.Result result) {
        if (d.result != null) return;
        d.result = result;
        d.phase = Duel.Phase.OVER;
        settle(server, d);
    }

    /** Ends it from inside a blow: over at once (nothing more lands), settled on the next tick. */
    static void finishLater(MinecraftServer server, Duel d, Duel.Result result) {
        if (d.result != null) return;
        d.result = result;
        d.phase = Duel.Phase.OVER;
        ServerScheduler.schedule(1, () -> settle(server, d));
    }

    /** What the end of a duel comes to: the challenger yields or stands over the hunter, the record, the rewards, the news. */
    static void settle(MinecraftServer server, Duel d) {
        if (d.settled) return;
        d.settled = true;
        ServerLevel level = server.getLevel(d.dimension);
        d.overAt = level == null ? 0 : level.getGameTime();
        Duel.Result r = d.result;
        ServerPlayer p = OnlinePlayers.get(server, d.hunter);
        DuelistEntity e = level != null && level.getEntity(d.duelistId) instanceof DuelistEntity x && x.getUUID().equals(d.duelist) ? x : null;
        if (e != null) {
            if (r == Duel.Result.CALLED_OFF) e.poof();
            else e.after(r == Duel.Result.WON);
        }
        if (p == null) {
            RUNNING.remove(d.hunter, d);
            return;
        }
        DuelRecord rec = record(p);
        long day = day(server);
        boolean first = false;
        switch (r) {
            case WON -> {
                first = rec.wins(d.who) == 0;
                rec.count(d.who, DuelRecord.WON, day);
            }
            case LOST -> rec.count(d.who, DuelRecord.LOST, day);
            case FORFEIT -> {
                rec.count(d.who, DuelRecord.LOST, day);
                rec.forfeits++;
            }
            case DRAWN -> rec.count(d.who, DuelRecord.DRAWN, day);
            default -> {
            }
        }
        DuelRecord.Challenge c = rec.challenge;
        if (r == Duel.Result.CALLED_OFF) {
            // nobody won: the challenge stands, and they come again
            if (c != null) {
                c.told = false;
                c.visited = false;
                c.openAt = now(server) + 20 * 60;
                c.lapseAt = 0;
            }
        } else {
            rec.challenge = null;
        }
        if (r == Duel.Result.LOST || r == Duel.Result.FORFEIT) {
            // helped back up
            if (p.isAlive() && p.getHealth() < p.getMaxHealth() * 0.3f) p.setHealth(p.getMaxHealth() * 0.3f);
        }
        p.clearFire();
        if (r == Duel.Result.WON) reward(p, d, rec);
        if (r != Duel.Result.CALLED_OFF) {
            news(p, d, r, first);
            tell(p, d, r);
            if (e != null) say(p, e, t("say." + r.name().toLowerCase(java.util.Locale.ROOT) + "." + d.who.id, p.getGameProfile().getName()));
            Rankings.refresh(p);
        }
        sync(server, d);
    }

    /** A won duel: the purse, merit, System XP, and the titles. */
    static void reward(ServerPlayer p, Duel d, DuelRecord rec) {
        long purse = purse();
        if (purse > 0) Economy.add(p, purse, Economy.Reason.DUEL);
        int merit = DuelConfig.merit();
        Association.addMerit(p, merit);
        long xp = xp(SystemAPI.get(p).level());
        if (xp > 0) SystemAPI.grantXp(p, xp, BeyondSolo.id("duel"));
        d.purse = purse;
        d.merit = merit;
        d.xp = xp;
        if (rec.totalWins() == 1) SystemAPI.setTitle(p, "title.beyondsolo.duelist");
        if (!rec.national && rec.beatenAll()) {
            rec.national = true;
            d.national = true;
            SystemAPI.setTitle(p, "title.beyondsolo.national_level_hunter");
            HunterNews.post(p.server, HunterNews.DUEL, nt("national", name(p)), HunterNews.GOLD, true);
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.LEVEL_UP, t("notify.national.title"), t("notify.national")));
        }
    }

    /** The result, as the hunter news has it. */
    static void news(ServerPlayer p, Duel d, Duel.Result r, boolean first) {
        Component me = name(p), them = d.who.displayName(), guild = d.who.guild().displayName();
        int color = d.who.color;
        switch (r) {
            case WON -> HunterNews.post(p.server, HunterNews.DUEL, nt((first ? "won." : "won_again.") + d.why.key(), me, them, guild), color, first);
            case LOST -> HunterNews.post(p.server, HunterNews.DUEL, nt("lost." + d.why.key(), me, them, guild), color, false);
            case DRAWN -> HunterNews.post(p.server, HunterNews.DUEL, nt("drawn", me, them), color, false);
            case FORFEIT -> HunterNews.post(p.server, HunterNews.DUEL, nt("forfeit", me, them), color, false);
            default -> {
            }
        }
    }

    /** The result, to the hunter. */
    static void tell(ServerPlayer p, Duel d, Duel.Result r) {
        String k = r.name().toLowerCase(java.util.Locale.ROOT);
        Component title = t("notify.result." + k, d.who.displayName());
        Component body = r == Duel.Result.WON ? t("notify.result.won.body", gold(d.purse), d.merit, d.xp) : t("notify.result." + k + ".body", d.who.displayName());
        Notifications.send(p, SystemNotification.of(r == Duel.Result.WON ? SystemNotification.Kind.REWARD : SystemNotification.Kind.INFO, title, body));
    }

    static String gold(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount);
    }

    // ================================================================ what the clients see

    /** The duel, to the hunter and everyone near its ring. */
    static void sync(MinecraftServer server, Duel d) {
        ServerLevel level = server.getLevel(d.dimension);
        if (level == null) return;
        CompoundTag t = tag(level, d);
        ServerPlayer p = OnlinePlayers.get(server, d.hunter);
        List<ServerPlayer> to = new ArrayList<>();
        if (p != null) to.add(p);
        for (ServerPlayer w : level.players()) {
            if (w != p && w.position().distanceToSqr(d.center) <= WATCH * WATCH) to.add(w);
        }
        for (ServerPlayer w : to) {
            CompoundTag x = t.copy();
            x.putBoolean("mine", w.getUUID().equals(d.hunter));
            PacketDistributor.sendToPlayer(w, new DuelPayload(x));
        }
    }

    static CompoundTag tag(ServerLevel level, Duel d) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", d.id);
        t.putUUID("hunter", d.hunter);
        t.putString("hunterName", d.hunterName);
        t.putInt("entity", d.duelistId);
        t.putInt("who", d.who.ordinal());
        t.putInt("why", d.why.ordinal());
        t.putInt("phase", d.phase.ordinal());
        t.putDouble("cx", d.center.x);
        t.putDouble("cy", d.center.y);
        t.putDouble("cz", d.center.z);
        t.putDouble("r", d.radius);
        t.putLong("now", level.getGameTime());
        t.putLong("fightAt", d.fightAt);
        t.putLong("endsAt", d.endsAt);
        t.putFloat("level", d.level);
        t.putInt("outside", d.outside);
        t.putInt("outsideMax", DuelConfig.outsideTicks());
        if (d.result != null) {
            t.putInt("result", d.result.ordinal());
            t.putLong("purse", d.purse);
            t.putInt("merit", d.merit);
            t.putLong("xp", d.xp);
            t.putBoolean("national", d.national);
        }
        return t;
    }

    static CompoundTag challengeTag(ServerPlayer p, DuelRecord.Challenge c) {
        long now = now(p.server);
        CompoundTag t = new CompoundTag();
        t.putInt("who", c.who.ordinal());
        t.putInt("why", c.why.ordinal());
        t.putBoolean("open", c.open(now));
        t.putLong("left", c.open(now) ? Math.max(0, (c.lapseAt > 0 ? c.lapseAt : now + DuelConfig.challengeTicks()) - now) : Math.max(0, c.openAt - now));
        t.putInt("later", c.later);
        t.putInt("maxLater", MAX_LATER);
        t.putFloat("level", fightLevel(p, c.who, c.why));
        return t;
    }

    /** The hunter's record against one of them. */
    static CompoundTag recordTag(DuelRecord r, NationalHunter h, long day) {
        CompoundTag t = new CompoundTag();
        t.putInt("who", h.ordinal());
        t.putInt("wins", r.wins(h));
        t.putInt("losses", r.losses(h));
        t.putInt("draws", r.draws(h));
        t.putInt("last", r.last(h));
        t.putLong("wait", Math.max(0, r.lastDay(h) + DuelConfig.rematchDays() - day));
        t.putLong("power", Rankings.power(h.rival().levelOn(day)));
        return t;
    }

    /** The stakes and the rules, as the windows show them. */
    static void stakes(ServerPlayer p, CompoundTag t) {
        t.putLong("purse", purse());
        t.putInt("merit", DuelConfig.merit());
        t.putLong("xp", xp(SystemAPI.get(p).level()));
        t.putInt("radius", DuelConfig.ringRadius());
        t.putInt("seconds", DuelConfig.timeLimitTicks() / 20);
        t.putInt("outsideSeconds", DuelConfig.outsideTicks() / 20);
        t.putLong("power", Rankings.power(HiddenLevel.hunterLevel(SystemAPI.get(p))));
    }

    /** The challenger's window: who, why, their record against the hunter, the stakes and the rules. */
    static CompoundTag windowTag(ServerPlayer p, Association.Session s) {
        CompoundTag t = new CompoundTag();
        t.putInt("entity", s.entity());
        DuelRecord r = record(p);
        DuelRecord.Challenge c = r.challenge;
        Entity e = s.entity() == 0 ? null : p.serverLevel().getEntity(s.entity());
        NationalHunter who = e instanceof DuelistEntity de ? de.who() : c != null ? c.who : NationalHunter.HAN_SEORI;
        t.putString("name", json(who.displayName(), p.registryAccess()));
        t.putInt("who", who.ordinal());
        if (c != null && c.who == who) t.put("challenge", challengeTag(p, c));
        t.put("record", recordTag(r, who, day(p.server)));
        stakes(p, t);
        return t;
    }

    /** The office's DUELS tab: the four, the hunter's record against each, the challenge standing, the stakes. */
    public static CompoundTag officeTag(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        DuelRecord r = record(p);
        long day = day(p.server);
        ListTag hunters = new ListTag();
        for (NationalHunter h : NationalHunter.values()) hunters.add(recordTag(r, h, day));
        t.put("hunters", hunters);
        if (r.challenge != null) t.put("challenge", challengeTag(p, r.challenge));
        t.putBoolean("national", r.national);
        t.putInt("wins", r.totalWins());
        t.putInt("losses", r.totalLosses());
        t.putInt("draws", r.totalDraws());
        t.putInt("minRank", DuelConfig.minRank().ordinal());
        t.putInt("rank", SystemAPI.isAwakened(p) ? SystemAPI.get(p).rank().ordinal() : 0);
        t.putBoolean("dueling", RUNNING.containsKey(p.getUUID()));
        stakes(p, t);
        return t;
    }

    // ================================================================ commands and tests

    /** /solo duel start: a duel with them begins now where the hunter stands (a challenge is made if none stands). */
    @Nullable
    public static Duel startNow(ServerPlayer p, NationalHunter who, DuelRecord.Reason why) {
        if (RUNNING.containsKey(p.getUUID())) return null;
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || c.who != who) c = challenge(p, who, why, now(p.server));
        c.told = true;
        c.visited = true;
        DuelistEntity e = challenger(p);
        if (e == null || e.distanceTo(p) > 24) e = sendNear(p, c);
        return e == null ? null : begin(p, e);
    }

    /** /solo duel start in a test: the challenger set down exactly there. */
    public static Duel startWith(ServerPlayer p, NationalHunter who, DuelRecord.Reason why, Vec3 at) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || c.who != who) c = challenge(p, who, why, now(p.server));
        c.told = true;
        c.visited = true;
        DuelistEntity e = spawn(p, c, at);
        return e == null ? null : begin(p, e);
    }

    /** The challenger of a duel, or null. */
    @Nullable
    public static DuelistEntity duelist(MinecraftServer server, Duel d) {
        ServerLevel level = server.getLevel(d.dimension);
        return level != null && level.getEntity(d.duelistId) instanceof DuelistEntity e && e.getUUID().equals(d.duelist) ? e : null;
    }

    /** /solo duel bell: the count is skipped, they fight now. */
    public static void bell(MinecraftServer server, Duel d) {
        ServerLevel level = server.getLevel(d.dimension);
        if (level == null || d.phase != Duel.Phase.COUNTDOWN) return;
        d.fightAt = level.getGameTime();
        d.endsAt = d.fightAt + DuelConfig.timeLimitTicks();
        tick(server, d);
    }

    /** /solo duel send: the challenge opens now and the challenger sets out for the hunter (as when it opens). */
    public static boolean sendNow(ServerPlayer p) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || RUNNING.containsKey(p.getUUID())) return false;
        dismiss(p, false);
        c.openAt = now(p.server);
        c.lapseAt = 0;
        c.told = false;
        c.visited = false;
        tick(p);
        return challenger(p) != null;
    }

    /** /solo duel lapse: the challenge standing runs out now. */
    public static boolean lapseNow(ServerPlayer p) {
        DuelRecord.Challenge c = record(p).challenge;
        if (c == null || RUNNING.containsKey(p.getUUID())) return false;
        lapse(p, c);
        return true;
    }

    /** /solo duel unleash: the challenger shows what they really are now. */
    public static boolean unleashNow(ServerPlayer p) {
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || d.phase != Duel.Phase.FIGHT) return false;
        DuelistEntity e = duelist(p.server, d);
        if (e == null || e.unleashed()) return false;
        e.beginForTest(DuelistEntity.Move.UNLEASH);
        return true;
    }

    /** /solo duel move: the challenger begins one of their moves now (one of their own style, or the change). */
    public static boolean moveNow(ServerPlayer p, DuelistEntity.Move m) {
        Duel d = RUNNING.get(p.getUUID());
        if (d == null || d.phase != Duel.Phase.FIGHT) return false;
        DuelistEntity e = duelist(p.server, d);
        if (e == null || (m.style != null && m.style != e.who().style)) return false;
        e.beginForTest(m);
        return true;
    }

    /** /solo duel reset: the record forgotten, the challenge gone, the challengers home. */
    public static void reset(ServerPlayer p) {
        wipe(p);
        p.setData(DuelModule.RECORD, new DuelRecord());
    }

    /** /solo wipe: a duel under way is called off and the hunter's challengers go. */
    public static void wipe(ServerPlayer p) {
        Duel d = RUNNING.get(p.getUUID());
        if (d != null && d.phase != Duel.Phase.OVER) finish(p.server, d, Duel.Result.CALLED_OFF);
        RUNNING.remove(p.getUUID());
        dismiss(p, true);
    }

    private Duels() {}
}
