package com.beyondsolo.ruler;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossData.Event;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The rulers of light ({@link WorldBossKind.Tier#RULER}): the monarchs' enemies since before the gates. One at a time,
 * in its own slot beside the great threat abroad ({@link WorldBossData#ruler()}), a ruler comes down through a rift of
 * light for one of three reasons:
 * <ul>
 *   <li><b>The Eternal War</b> ({@link #WAR}): a monarch has landed, and the light answers it
 *   ({@code rulers.warChance}). The ruler lands near the monarch and goes for it; its blows pass by every hunter who
 *   doesn't stand against it. Hunters who bring the monarch down while it fights are blessed ({@link #monarchFell}):
 *   the Blessing of Light, a Mote of Radiance, the war in their record.</li>
 *   <li><b>A Sentence of Light</b> ({@link #SENTENCE}): a hunter took a crown ({@link MonarchAPI#crowned}). Every so
 *   often ({@code rulers.sentenceMinutes}) a ruler comes down to judge one of them: it hunts them, and only them (and
 *   whoever stands with them by striking it). Felling it overturns the sentence.</li>
 *   <li><b>Called</b> ({@link #CALLED}): by a command.</li>
 * </ul>
 * A ruler is fought and paid like a monarch ({@link #slain}): by each hunter's share of the damage (what the monarch it
 * fought took off it thins every share), with Motes of Radiance besides. When its work is done (the monarch fell, the
 * judged hunter died) it rises back into the sky once no foe stands against it, and it leaves when no hunter comes
 * ({@code rulers.abandonMinutes}). Everything goes out as {@link WorldBossPayload}s (WAR, SENTENCE and BLESSED are the
 * light's own); the client keeps a ruler apart from the great threat abroad by its tier.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Rulers {
    /** Why a ruler came. */
    public static final int CALLED = 0, WAR = 1, SENTENCE = 2;
    /** Persistent-data key on a ruler's heralds: the event they came with (they fade when it ends). */
    public static final String HOST_TAG = "beyondsolo_ruler_host";
    public static final String SENTENCE_QUEST = BeyondSolo.id("ruler/sentence").toString();
    public static final String WAR_QUEST = BeyondSolo.id("ruler/war").toString();
    /** Quest progress: the ruler come to judge the hunter fell. */
    public static final String OVERTURN_KEY = BeyondSolo.id("ruler_overturn").toString();
    /** Quest progress: a monarch fell while the ruler at war with it stood. */
    public static final String WAR_KEY = BeyondSolo.id("ruler_war").toString();
    /** How often the status goes out (ticks). */
    static final int STATUS_EVERY = 40;
    /** A hunter must have taken this share of a ruler's health off it to be paid. */
    static final float MIN_SHARE = 0.005f;
    /** How far from the monarch a ruler at war comes down. */
    private static final int WAR_MIN = 18, WAR_MAX = 30;
    /** How far from the hunter it judges a ruler comes down. */
    private static final int SENTENCE_MIN = 24, SENTENCE_MAX = 40;
    /** How long before a planned sentence tries again when it can't come now (ticks). */
    private static final int RETRY = 1200;

    /** The rulers in the world now (to find a sanctum fast). */
    private static final Set<RulerBoss> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    // ================================================================ friend and foe

    /** Whether {@code e} is of the light: a ruler, or one of their heralds. */
    public static boolean light(@Nullable Entity e) {
        return e instanceof RulerBoss || e instanceof HeraldEntity;
    }

    /** The ruler a being of light answers to: itself, or a herald's ruler (null if that one is gone). */
    @Nullable
    public static RulerBoss rulerOf(@Nullable Entity e) {
        if (e instanceof RulerBoss r) return r;
        if (e instanceof HeraldEntity h) return h.ruler();
        return null;
    }

    static void track(RulerBoss r) {
        TRACKED.add(r);
    }

    /**
     * Whether {@code e} stands in a ruler's sanctum and is not its foe: there a monarch's domain and presence don't reach
     * it, and the light keeps it.
     */
    public static boolean shelters(LivingEntity e) {
        if (TRACKED.isEmpty() || light(e)) return false;
        for (RulerBoss r : TRACKED) {
            if (r.isRemoved() || !r.isAlive() || !r.landed() || r.leaving() || r.level() != e.level()) continue;
            if (Math.abs(e.getY() - r.getY()) > 24) continue;
            if (e.position().subtract(r.position()).horizontalDistanceSqr() > RulerBoss.SANCTUM * RulerBoss.SANCTUM) continue;
            if (!r.isFoe(e)) return true;
        }
        return false;
    }

    /** Whether the hunter carries a ruler's blessing. */
    public static boolean blessed(Player p) {
        return p.hasEffect(RulerModule.BLESSING);
    }

    /**
     * Whether the light leaves a hunter's blows be: {@code e} is a ruler or a herald that does not stand against the
     * hunter. Their area skills, shadows and hired hunters pass it by; a blow of their own still lands (the first is
     * stayed with a warning, {@link RulerHooks}).
     */
    public static boolean spares(@Nullable UUID hunter, @Nullable LivingEntity e) {
        if (!light(e)) return false;
        RulerBoss r = rulerOf(e);
        if (r == null || hunter == null) return true;
        Player p = e.level() instanceof ServerLevel server ? OnlinePlayers.get(server.getServer(), hunter) : e.level().getPlayerByUUID(hunter);
        return !r.holdsAgainst(hunter, p);
    }

    // ================================================================ a ruler comes

    /** A monarch has landed: the light may answer it ({@code rulers.warChance}). */
    public static void monarchLanded(ServerLevel level, MonarchBoss monarch) {
        if (!RulerConfig.enabled() || level.dimension() != Level.OVERWORLD) return;
        WorldBossData data = WorldBossData.get(level.getServer());
        if (data.ruler() != null || level.random.nextDouble() >= RulerConfig.warChance()) return;
        war(level, monarch, null);
    }

    /** A ruler ({@code kind}, or one of them) comes down near the monarch to make war on it. Null if one is abroad. */
    @Nullable
    public static Event war(ServerLevel level, MonarchBoss monarch, @Nullable WorldBossKind kind) {
        WorldBossData data = WorldBossData.get(level.getServer());
        if (data.ruler() != null) return null;
        RandomSource r = level.random;
        BlockPos at = WorldBosses.findSpot(level, monarch.position(), WAR_MIN, WAR_MAX, r);
        if (at == null) at = roughSpot(level, monarch.position(), 22, r);
        return begin(level, kind != null ? kind : pick(r), at, WAR, monarch.getUUID());
    }

    /** A ruler ({@code kind}, or one of them) comes down to judge a crowned hunter. Null if one is abroad. */
    @Nullable
    public static Event sentence(ServerLevel level, ServerPlayer judged, @Nullable WorldBossKind kind) {
        WorldBossData data = WorldBossData.get(level.getServer());
        if (data.ruler() != null) return null;
        RandomSource r = level.random;
        BlockPos at = WorldBosses.findSpot(level, judged.position(), SENTENCE_MIN, SENTENCE_MAX, r);
        if (at == null) at = roughSpot(level, judged.position(), 28, r);
        Event ev = begin(level, kind != null ? kind : pick(r), at, SENTENCE, judged.getUUID());
        if (ev != null) judged.getData(RulerModule.RECORD.get()).addSentence();
        return ev;
    }

    /**
     * A ruler comes to {@code at}: a rift of light opens in the sky above it, and the ruler comes down through it
     * ({@code rulers.descentSeconds}) for {@code reason}, after {@code quarry} (the monarch's entity, the judged hunter).
     * Null when a ruler is already abroad.
     */
    @Nullable
    public static Event begin(ServerLevel level, WorldBossKind kind, BlockPos at, int reason, @Nullable UUID quarry) {
        if (!kind.ruler()) return null;
        WorldBossData data = WorldBossData.get(level.getServer());
        if (data.ruler() != null) return null;
        long now = level.getGameTime();
        Event ev = new Event(UUID.randomUUID(), kind, at, now, now + RulerConfig.descentTicks(), reason == CALLED);
        ev.setReason(reason, quarry);
        data.setRuler(ev);
        announce(level, ev);
        return ev;
    }

    /** Tells every hunter in the overworld why it comes, where, and when; the judged hunter gets their quest. */
    private static void announce(ServerLevel level, Event ev) {
        WorldBossKind kind = ev.kind;
        int ticks = (int) Math.max(0, ev.landAt() - level.getGameTime());
        long key = WorldBosses.eventKey(ev);
        @Nullable WorldBossKind monarch = ev.reason() == WAR ? monarchKind(level, ev) : null;
        ServerPlayer judged = ev.reason() == SENTENCE ? player(level, ev.quarry()) : null;
        for (ServerPlayer p : level.players()) {
            boolean me = judged != null && judged == p;
            WorldBossPayload msg = switch (ev.reason()) {
                case WAR -> new WorldBossPayload(WorldBossPayload.WAR, kind.ordinal(), key, -1, ev.home, 1,
                        monarch != null ? monarch.ordinal() : -1, ticks);
                case SENTENCE -> new WorldBossPayload(WorldBossPayload.SENTENCE, kind.ordinal(), key, -1, ev.home, 1, ticks, me ? 1 : 0);
                default -> new WorldBossPayload(WorldBossPayload.DESCENT, kind.ordinal(), key, -1, ev.home, 1, ticks, 0);
            };
            PacketDistributor.sendToPlayer(p, msg);
            if (!SystemAPI.hasSystem(p)) continue;
            Component where = WorldBosses.whereFrom(p, ev.home);
            Component title = Component.translatable(switch (ev.reason()) {
                case WAR -> "ruler.beyondsolo.notify.war";
                case SENTENCE -> "ruler.beyondsolo.notify.sentence";
                default -> "ruler.beyondsolo.notify.ruler";
            });
            Component body = switch (ev.reason()) {
                case WAR -> Component.translatable("ruler.beyondsolo.notify.war.body", kind.fullName(), where, ticks / 20,
                        monarch != null ? monarch.fullName() : Component.translatable("worldboss.beyondsolo.tier.monarch"));
                case SENTENCE -> me
                        ? Component.translatable("ruler.beyondsolo.notify.sentence.you", kind.fullName(), where, ticks / 20)
                        : Component.translatable("ruler.beyondsolo.notify.sentence.body", kind.fullName(), where, ticks / 20,
                                judged != null ? judged.getDisplayName() : Component.literal("?"));
                default -> Component.translatable("ruler.beyondsolo.notify.descent", kind.fullName(), where, ticks / 20);
            };
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT, title, body, me ? 260 : 200));
            if (ev.reason() == WAR && monarch != null) warQuest(p, ev, monarch);
        }
        if (judged != null) sentenceQuest(judged, ev);
        level.playSound(null, ev.home, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6f, 0.5f);
        level.playSound(null, ev.home, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 6f, 0.6f);
    }

    /** The System's call to the judged hunter: overturn the sentence (fell the ruler). */
    private static void sentenceQuest(ServerPlayer p, Event ev) {
        if (!SystemAPI.isAwakened(p) || hasQuest(p, SENTENCE_QUEST, ev)) return;
        SystemData d = SystemAPI.get(p);
        CompoundTag extra = new CompoundTag();
        extra.putString("event", ev.id.toString());
        Quest q = QuestBuilder.of(SENTENCE_QUEST, QuestCategory.SURPRISE, 5, d.level())
                .title(Component.translatable("quest.beyondsolo.ruler.sentence", ev.kind.bossName()))
                .description(Component.translatable("quest.beyondsolo.ruler.sentence.desc", ev.kind.fullName()))
                .custom(OVERTURN_KEY, 1, Component.translatable("quest_objective.beyondsolo.ruler.sentence", ev.kind.bossName()))
                .timeLimit(RulerConfig.abandonTicks() * 4)
                .fixed().xp(9000L).statPoints(3)
                .extra(extra)
                .build();
        QuestEngine.offer(p, q, QuestEngine.OFFER_TICKS);
    }

    /** The System's call to every hunter: bring the monarch down while the ruler fights it, and leave the light be. */
    private static void warQuest(ServerPlayer p, Event ev, WorldBossKind monarch) {
        if (!SystemAPI.isAwakened(p) || MonarchAPI.crowned(p) || hasQuest(p, WAR_QUEST, ev)) return;
        SystemData d = SystemAPI.get(p);
        CompoundTag extra = new CompoundTag();
        extra.putString("event", ev.id.toString());
        Quest q = QuestBuilder.of(WAR_QUEST, QuestCategory.SURPRISE, 5, d.level())
                .title(Component.translatable("quest.beyondsolo.ruler.war", ev.kind.bossName()))
                .description(Component.translatable("quest.beyondsolo.ruler.war.desc", ev.kind.fullName(), monarch.fullName()))
                .custom(WAR_KEY, 1, Component.translatable("quest_objective.beyondsolo.ruler.war", monarch.bossName()))
                .timeLimit(RulerConfig.abandonTicks() * 4)
                .fixed().xp(4000L).statPoints(2)
                .extra(extra)
                .build();
        QuestEngine.offer(p, q, QuestEngine.OFFER_TICKS);
    }

    private static boolean hasQuest(ServerPlayer p, String template, Event ev) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (template.equals(q.template()) && ev.id.toString().equals(q.extra().getString("event"))) return true;
        }
        return false;
    }

    // ================================================================ while it is abroad

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        WorldBossData data = WorldBossData.get(server);
        Event ev = data.ruler();
        if (ev != null) {
            tickEvent(level, data, ev, now);
            return;
        }
        if (now % 20 != 0 || !RulerConfig.enabled() || !RulerConfig.sentences()) return;
        planSentence(level, data, now);
    }

    /** With no ruler abroad: once a hunter is crowned, the light comes to judge one of the crowned now and then. */
    private static void planSentence(ServerLevel level, WorldBossData data, long now) {
        List<ServerPlayer> crowned = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (MonarchAPI.crowned(p) && SystemAPI.isAwakened(p) && !QuestTriggers.quiet(p) && !GateService.inTransit(p)) crowned.add(p);
        }
        if (data.nextSentenceAt() < 0) {
            if (!crowned.isEmpty()) data.setNextSentenceAt(now + RulerConfig.firstSentenceTicks());
            return;
        }
        if (now < data.nextSentenceAt()) return;
        if (crowned.isEmpty()) {
            data.setNextSentenceAt(now + RETRY);
            return;
        }
        ServerPlayer judged = crowned.get(level.random.nextInt(crowned.size()));
        if (sentence(level, judged, null) == null) data.setNextSentenceAt(now + RETRY);
    }

    private static void tickEvent(ServerLevel level, WorldBossData data, Event ev, long now) {
        if (ev.descending()) {
            // a war with no monarch left to fight is called off before it lands
            if (ev.reason() == WAR && !monarchAbroad(data, ev)) {
                withdrawn(level, data, ev);
                return;
            }
            if (now >= ev.landAt()) {
                // it comes down where its rift is, once that land is loaded (it waits in the rift until then)
                if (level.hasChunk(ev.home.getX() >> 4, ev.home.getZ() >> 4)) {
                    ColossalBoss boss = WorldBosses.spawnBoss(level, ev);
                    if (!(boss instanceof RulerBoss ruler)) {
                        if (boss != null) boss.discard();
                        end(level, data, ev);
                        return;
                    }
                    ruler.setQuarry(ev.reason(), ev.quarry());
                } else if (now - ev.landAt() > RulerConfig.abandonTicks()) {
                    withdrawn(level, data, ev);
                    return;
                }
            }
            if (now % STATUS_EVERY == 0) WorldBosses.status(level, ev, null, now);
            return;
        }
        ColossalBoss boss = ev.entity() == null ? null : level.getEntity(ev.entity()) instanceof ColossalBoss b ? b : null;
        if (boss != null && boss.isAlive()) {
            ev.setLast(boss.blockPosition());
            ev.setEngaged(Math.max(ev.engaged(), boss.lastEngaged()));
            if (!boss.leaving()) {
                if (now - ev.engaged() > RulerConfig.abandonTicks()) {
                    leave(level, ev, boss);
                } else if (boss instanceof RulerBoss ruler) {
                    // its work is done: the monarch it made war on is gone, or the hunter it judged is
                    if (ev.reason() == WAR && !monarchAbroad(data, ev)) ruler.victory();
                    if (ev.reason() == SENTENCE && ruler.quarry() == null) ruler.victory();
                }
            }
        } else if (boss == null && now - ev.engaged() > RulerConfig.abandonTicks() + 200) {
            // its land is out of reach and no hunter has come: it is gone (it fades when its land loads again)
            withdrawn(level, data, ev);
            return;
        }
        if (now % STATUS_EVERY == 0) WorldBosses.status(level, ev, boss, now);
    }

    /** It rises back into the sky now: no hunter came for it. */
    private static void leave(ServerLevel level, Event ev, ColossalBoss boss) {
        boss.leave();
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.WITHDRAW, ev.kind.ordinal(), WorldBosses.eventKey(ev), boss.getId(),
                    boss.blockPosition(), boss.phase(), 0, 0));
        }
    }

    /** The monarch a ruler at war came for is still abroad. */
    private static boolean monarchAbroad(WorldBossData data, Event ev) {
        Event cur = data.current();
        if (cur == null || !cur.kind.monarch()) return false;
        return ev.quarry() == null || cur.entity() == null || ev.quarry().equals(cur.entity());
    }

    @Nullable
    private static WorldBossKind monarchKind(ServerLevel level, Event ev) {
        Event cur = WorldBossData.get(level.getServer()).current();
        return cur != null && cur.kind.monarch() ? cur.kind : null;
    }

    // ================================================================ landing

    /** A ruler landed: the hunters near hear what it came for (the war banner names the monarch). */
    static void landed(ServerLevel level, RulerBoss ruler) {
        Event ev = WorldBosses.eventOf(level, ruler);
        if (ev == null) return;
        if (ev.reason() == WAR) {
            WorldBossKind monarch = monarchKind(level, ev);
            for (ServerPlayer p : ruler.watchers(96)) {
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.WAR, ruler.kind().ordinal(), WorldBosses.eventKey(ev), ruler.getId(),
                        ruler.blockPosition(), 1, monarch != null ? monarch.ordinal() : -1, 0));
            }
        } else if (ev.reason() == SENTENCE) {
            for (ServerPlayer p : ruler.watchers(96)) {
                boolean me = p.getUUID().equals(ev.quarry());
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SENTENCE, ruler.kind().ordinal(), WorldBosses.eventKey(ev),
                        ruler.getId(), ruler.blockPosition(), 1, 0, me ? 1 : 0));
            }
        }
    }

    // ================================================================ the end

    /** A ruler fell: every hunter who hurt it is paid by their share, and a sentence on one of them is overturned. */
    public static void slain(ServerLevel level, ColossalBoss boss, DamageSource source) {
        WorldBossKind kind = boss.kind();
        WorldBossData data = WorldBossData.get(level.getServer());
        Event ev = WorldBosses.eventOf(level, boss);
        data.countKill(kind);
        RandomSource r = level.random;
        List<Map.Entry<UUID, Float>> ranked = WorldBosses.ranked(boss);
        UUID top = ranked.isEmpty() ? null : ranked.get(0).getKey();
        List<UUID> paid = new ArrayList<>();
        for (Map.Entry<UUID, Float> e : ranked) {
            ServerPlayer p = OnlinePlayers.get(level.getServer(), e.getKey());
            float share = boss.share(e.getKey());
            if (p == null || share < MIN_SHARE) continue;
            paid.add(p.getUUID());
            long xp = pay(p, boss, share, e.getKey().equals(top), r);
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), ev != null ? WorldBosses.eventKey(ev) : 0,
                    boss.getId(), boss.blockPosition(), boss.phase(), Math.round(share * 1000), (int) Math.min(Integer.MAX_VALUE, xp)));
        }
        for (ServerPlayer p : level.players()) {
            if (paid.contains(p.getUUID())) continue;
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SLAIN, kind.ordinal(), ev != null ? WorldBosses.eventKey(ev) : 0,
                    boss.getId(), boss.blockPosition(), boss.phase(), -1, 0));
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 3f, 0.8f);
        if (ev != null && ev.reason() == SENTENCE && ev.quarry() != null && paid.contains(ev.quarry())) {
            // the judged hunter stood and won: the sentence is overturned
            ServerPlayer judged = OnlinePlayers.get(level.getServer(), ev.quarry());
            if (judged != null) {
                judged.getData(RulerModule.RECORD.get()).addOverturned();
                QuestEngine.progress(judged, OVERTURN_KEY, 1);
                RulerJournal.overturned(judged);
            }
        }
        if (ev != null) {
            RulerHost.disband(level, ev.id);
            data.setRuler(null);
            scheduleNext(level, data);
            for (ServerPlayer p : level.players()) {
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.GONE, kind.ordinal(), WorldBosses.eventKey(ev), -1, ev.home, 1, 0, 0));
            }
        }
    }

    /** Pays one hunter like a monarch's kill, with motes of the ruler's light besides, and keeps the record. */
    private static long pay(ServerPlayer p, ColossalBoss boss, float share, boolean top, RandomSource r) {
        WorldBossKind kind = boss.kind();
        // a full reward from half the damage up: a party of two gets all of it each
        double cut = 0.4 + 0.6 * Math.min(1.0, share * 2.0);
        long xp = Math.round(boss.getMaxHealth() * 0.7 * cut);
        long gold = Math.round(24_000 * cut);
        boolean system = SystemAPI.hasSystem(p);
        if (system) {
            SystemAPI.grantXp(p, xp, BeyondSolo.id("ruler"));
            Economy.add(p, gold, Economy.Reason.KILL);
        }
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(RulerModule.MOTE.get(), 2 + (top ? 1 : 0) + (share >= 0.2f ? 1 : 0)));
        loot.add(new ItemStack(CraftingModule.GREATER_MANA_STONE.get(), 3 + r.nextInt(2) + (share >= 0.2f ? 1 : 0)));
        loot.add(new ItemStack(CraftingModule.SUPREME_HEALING_ELIXIR.get(), 1 + (top ? 1 : 0)));
        loot.add(GateLoot.weapon(5, r));
        if (r.nextFloat() < 0.5f + share) loot.add(GateLoot.weapon(5, r));
        loot.add(new ItemStack(CraftingModule.ELIXIR_OF_REBIRTH.get()));
        for (ItemStack s : loot) WorldBosses.give(p, s);
        p.getData(RulerModule.RECORD.get()).addSlain(kind, top);
        RulerJournal.earned(p, kind, top);
        return system ? xp : 0;
    }

    /**
     * A monarch fell: if a ruler was at war with it, it has won, and it blesses the hunters who brought the monarch down
     * at its side (a share of the kill, and never a hand raised against it).
     */
    public static void monarchFell(ServerLevel level, ColossalBoss monarch) {
        Event ev = WorldBossData.get(level.getServer()).ruler();
        if (ev == null || ev.reason() != WAR || ev.quarry() == null || !ev.quarry().equals(monarch.getUUID())) return;
        RulerBoss ruler = ev.entity() != null && level.getEntity(ev.entity()) instanceof RulerBoss b && b.isAlive() ? b : null;
        if (ruler == null) return;
        ruler.victory();
        float total = monarch.totalDealt() + monarch.foreignDealt();
        for (Map.Entry<UUID, Float> e : monarch.dealt().entrySet()) {
            ServerPlayer p = OnlinePlayers.get(level.getServer(), e.getKey());
            if (p == null || total <= 0 || e.getValue() / total < MIN_SHARE) continue;
            if (MonarchAPI.crowned(p) || ruler.holdsAgainst(p.getUUID(), p)) continue;
            bless(p, RulerConfig.blessingTicks(), true);
            WorldBosses.give(p, new ItemStack(RulerModule.MOTE.get()));
            p.getData(RulerModule.RECORD.get()).addAlliedWar();
            QuestEngine.progress(p, WAR_KEY, 1);
            RulerJournal.allied(p);
        }
    }

    /**
     * Blesses a hunter: the Blessing of Light for {@code ticks} (harder blows on monarchs and their soldiers, and a
     * monarch's presence weighs nothing). {@code byRuler}: given by a ruler's own hand (the banner, the record).
     */
    public static boolean bless(ServerPlayer p, int ticks, boolean byRuler) {
        if (MonarchAPI.crowned(p)) return false;
        p.addEffect(new MobEffectInstance(RulerModule.BLESSING, ticks, 0, false, true, true));
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.06);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.6f);
        if (byRuler) {
            p.getData(RulerModule.RECORD.get()).addBlessing();
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.BLESSED, WorldBossKind.VERDICT.ordinal(), 0, -1, p.blockPosition(), 1,
                    ticks / 20, 0));
        }
        return true;
    }

    /** The hunter a ruler came to judge died: the sentence is carried out. */
    static void carriedOut(ServerLevel level, ServerPlayer judged) {
        Event ev = WorldBossData.get(level.getServer()).ruler();
        if (ev == null || ev.reason() != SENTENCE || !judged.getUUID().equals(ev.quarry())) return;
        if (ev.entity() != null && level.getEntity(ev.entity()) instanceof RulerBoss ruler && ruler.isAlive()) ruler.victory();
        for (ServerPlayer p : OnlinePlayers.all(level.getServer())) {
            if (!SystemAPI.hasSystem(p)) continue;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("ruler.beyondsolo.notify.sentence"),
                    Component.translatable("ruler.beyondsolo.notify.carried_out", ev.kind.fullName(), judged.getDisplayName())));
        }
        ev.setReason(SENTENCE, null);
        WorldBossData.get(level.getServer()).setDirty();
    }

    /** It has gone back up into the sky. */
    public static void left(ServerLevel level, ColossalBoss boss) {
        WorldBossData data = WorldBossData.get(level.getServer());
        Event ev = WorldBosses.eventOf(level, boss);
        if (ev == null) return;
        for (ServerPlayer p : level.players()) {
            if (!SystemAPI.hasSystem(p)) continue;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("ruler.beyondsolo.notify.ruler"),
                    Component.translatable("ruler.beyondsolo.notify.left", ev.kind.fullName())));
        }
        end(level, data, ev);
    }

    /** The event ends without a kill: no hunter came, its war was over before it landed, or its land stayed out of reach. */
    private static void withdrawn(ServerLevel level, WorldBossData data, Event ev) {
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.WITHDRAW, ev.kind.ordinal(), WorldBosses.eventKey(ev), -1, ev.last(), 1, 0,
                    0));
            if (SystemAPI.hasSystem(p)) {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("ruler.beyondsolo.notify.ruler"),
                        Component.translatable("ruler.beyondsolo.notify.left", ev.kind.fullName())));
            }
        }
        end(level, data, ev);
    }

    /** Clears the ruler's slot: its heralds fade, and the next sentence is planned. */
    private static void end(ServerLevel level, WorldBossData data, Event ev) {
        RulerHost.disband(level, ev.id);
        if (ev.entity() != null && level.getEntity(ev.entity()) instanceof ColossalBoss b && b.isAlive() && !b.leaving()) b.discard();
        data.setRuler(null);
        scheduleNext(level, data);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.GONE, ev.kind.ordinal(), WorldBosses.eventKey(ev), -1, ev.home, 1, 0, 0));
        }
    }

    /** Once one has come, the next sentence waits its interval again (give or take a quarter). */
    private static void scheduleNext(ServerLevel level, WorldBossData data) {
        if (data.nextSentenceAt() < 0) return;
        long wait = Math.round(RulerConfig.sentenceTicks() * (0.75 + 0.5 * level.random.nextDouble()));
        data.setNextSentenceAt(level.getGameTime() + wait);
    }

    /** Ends the ruler abroad at once, without a kill (commands, tests). */
    public static boolean stop(ServerLevel level) {
        WorldBossData data = WorldBossData.get(level.getServer());
        Event ev = data.ruler();
        if (ev == null) return false;
        end(level, data, ev);
        return true;
    }

    /** The ruler abroad, if its land is loaded. */
    @Nullable
    public static RulerBoss current(ServerLevel level) {
        Event ev = WorldBossData.get(level.getServer()).ruler();
        if (ev == null || ev.entity() == null) return null;
        return level.getEntity(ev.entity()) instanceof RulerBoss r ? r : null;
    }

    // ================================================================ joining, loading

    /** A hunter who logs in (or comes back to the overworld) hears of the ruler abroad at once. */
    public static void catchUp(ServerPlayer p) {
        ServerLevel level = p.server.overworld();
        Event ev = WorldBossData.get(p.server).ruler();
        if (ev == null || p.level() != level) return;
        ColossalBoss boss = ev.entity() == null ? null : level.getEntity(ev.entity()) instanceof ColossalBoss b ? b : null;
        int health = boss == null ? 1000 : Math.round(1000 * boss.getHealth() / Math.max(1, boss.getMaxHealth()));
        int land = ev.descending() ? (int) Math.max(0, ev.landAt() - level.getGameTime()) : 0;
        PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.STATUS, ev.kind.ordinal(), WorldBosses.eventKey(ev),
                boss != null ? boss.getId() : -1, boss != null ? boss.blockPosition() : ev.last(), boss != null ? boss.phase() : 1, health, land));
        if (ev.reason() == SENTENCE && p.getUUID().equals(ev.quarry())) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.SENTENCE, ev.kind.ordinal(), WorldBosses.eventKey(ev),
                    boss != null ? boss.getId() : -1, ev.home, 1, land, 1));
        }
    }

    /** A herald loaded back in after its ruler's event ended out of sight fades. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.loadedFromDisk()) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().hasUUID(HOST_TAG)) return;
        Event ev = WorldBossData.get(level.getServer()).ruler();
        if (ev == null || !ev.id.equals(e.getPersistentData().getUUID(HOST_TAG))) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        RulerHost.clear();
        TRACKED.clear();
    }

    // ================================================================ helpers

    /** One of the rulers, at random. */
    public static WorldBossKind pick(RandomSource r) {
        WorldBossKind[] all = WorldBossKind.rulers();
        return all[r.nextInt(all.length)];
    }

    /** A spot about {@code dist} blocks from {@code around} when no good one is found: wherever the ground is. */
    private static BlockPos roughSpot(ServerLevel level, Vec3 around, int dist, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2;
        int x = Mth.floor(around.x + Math.cos(a) * dist), z = Mth.floor(around.z + Math.sin(a) * dist);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            x = Mth.floor(around.x);
            z = Mth.floor(around.z);
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    @Nullable
    private static ServerPlayer player(ServerLevel level, @Nullable UUID id) {
        return OnlinePlayers.get(level.getServer(), id);
    }

    private Rulers() {}
}
