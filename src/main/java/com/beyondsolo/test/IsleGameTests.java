package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.isle.IsleAdmin;
import com.beyondsolo.isle.IsleData;
import com.beyondsolo.isle.IsleJournal;
import com.beyondsolo.isle.IsleRaid;
import com.beyondsolo.isle.IsleRaids;
import com.beyondsolo.isle.IsleRecord;
import com.beyondsolo.isle.IsleTestAccess;
import com.beyondsolo.isle.IsleWorld;
import com.beyondsolo.isle.KingBoss;
import com.beyondsolo.isle.NestHeartEntity;
import com.beyondsolo.isle.QueenBoss;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.RewardBoxItem;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Halcyon raid: the island laid out soundly for any world, the Association's call and who it reaches, the whole
 * fight from the landing to the king (a nest's heart and its blows, the hive opening, down the pit to the queen, the
 * king's birth, the island retaken and paid for, and home), a hunter carried off instead of dying, and the island
 * holding against picks, blocks and blasts.
 *
 * <p>The game-test server has no datapack dimensions, so the raid takes place on the small island floating over the
 * overworld far down its own lane ({@link IsleWorld#level}). Only one raid is ever on, so each test that runs one is in a
 * batch of its own. Fake players don't load chunks: those tests keep the island's ground loaded themselves.</p>
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IsleGameTests {
    private static final String ARENA = "evaluation_zone";

    /** An awakened hunter of {@code rank} (or a player the System hasn't awakened, for null), standing in the arena. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, @Nullable PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        Economy.reset(p);
        IsleAdmin.resetRecord(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        if (rank != null) {
            SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
            QuestTestAccess.markStarted(p);
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** Every chunk of the island (as far out as its builder lays ground). */
    private static List<ChunkPos> island(IsleWorld.Layout l) {
        int reach = (int) Math.ceil(l.radius() * 1.2) + 4;
        int cx = l.center().getX(), cz = l.center().getZ();
        List<ChunkPos> out = new ArrayList<>();
        for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++) {
            for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++) out.add(new ChunkPos(x, z));
        }
        return out;
    }

    private static void force(ServerLevel level, List<ChunkPos> chunks, boolean on) {
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, on);
    }

    /** Clutches of eggs on the island, and how many things there would hatch (a sniffer egg, frogspawn, a turtle egg on sand). */
    private static int[] eggs(ServerLevel isle, IsleWorld.Layout l) {
        int clutches = 0, bad = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos c = l.center();
        int reach = l.radius() + 6;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                for (int y = l.chamber().getY(); y <= c.getY() + 14; y++) {
                    BlockState s = isle.getBlockState(p.set(c.getX() + x, y, c.getZ() + z));
                    if (s.is(Blocks.SNIFFER_EGG) || s.is(Blocks.FROGSPAWN)) {
                        bad++;
                    } else if (s.is(Blocks.TURTLE_EGG)) {
                        clutches++;
                        if (isle.getBlockState(p.below()).is(BlockTags.SAND)) bad++;
                    }
                }
            }
        }
        return new int[]{clutches, bad};
    }

    /** A clutch of eggs on the chamber's floor, or null. */
    @Nullable
    private static BlockPos chamberEgg(ServerLevel isle, IsleWorld.Layout l) {
        BlockPos c = l.chamber();
        int r = l.chamberRadius();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                BlockPos at = new BlockPos(c.getX() + x, c.getY() + 1, c.getZ() + z);
                if (isle.getBlockState(at).is(Blocks.TURTLE_EGG)) return at;
            }
        }
        return null;
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** The heart beating in a nest of this raid, or null. */
    @Nullable
    private static NestHeartEntity heart(ServerLevel isle, IsleRaid r, BlockPos nest) {
        for (NestHeartEntity h : isle.getEntitiesOfClass(NestHeartEntity.class, new AABB(nest).inflate(4))) {
            if (h.isAlive() && r.id().equals(h.raid())) return h;
        }
        return null;
    }

    @Nullable
    private static Quest quest(ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).all()) if (template.equals(q.template())) return q;
        return null;
    }

    private static QuestObjective objective(Quest q, String target) {
        return q.objectives().stream().filter(o -> o.target().equals(target)).findFirst().orElseThrow();
    }

    private static boolean holdsBlessedBox(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof RewardBoxItem box && box.kind() == BoxKind.BLESSED) return true;
        }
        return false;
    }

    @Nullable
    private static ColossalBoss boss(ServerLevel isle, @Nullable UUID id) {
        return id != null && isle.getEntity(id) instanceof ColossalBoss b ? b : null;
    }

    // ------------------------------------------------------------------ the island

    /**
     * For any world, the island is laid out soundly: every nest on land, clear of the hive, the camp and each other; the
     * camp and the whole circle of light the hunters land on on land; the pit inside the hive; and the queen's chamber
     * deep under the island with her dais, the way in and the king's cocoon in it.
     */
    @GameTest(template = ARENA)
    public static void theIslandIsLaidOutSoundly(GameTestHelper helper) {
        RandomSource rnd = RandomSource.create(20261119L);
        List<String> problems = new ArrayList<>();
        for (int s = 0; s < 48; s++) {
            long seed = rnd.nextLong();
            for (boolean full : new boolean[]{true, false}) {
                for (int n = 1; n <= 8; n++) check(IsleTestAccess.plan(full, seed, n), n, problems);
            }
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
        helper.succeed();
    }

    private static void check(IsleWorld.Layout l, int n, List<String> problems) {
        String at = (l.small() ? "small" : "full") + " island, seed " + l.seed() + ", " + n + " nests: ";
        if (l.nests().size() != n) problems.add(at + l.nests().size() + " nests laid");
        int apart = l.small() ? 6 : 14, clear = l.small() ? 10 : 20;
        BlockPos c = l.center();
        for (int i = 0; i < l.nests().size(); i++) {
            BlockPos a = l.nests().get(i);
            if (!l.land(a.getX(), a.getZ())) problems.add(at + "nest " + i + " in the sea");
            if (flat(a, c) < l.hiveRadius() + 4) problems.add(at + "nest " + i + " against the hive");
            if (flat(a, l.camp()) < clear) problems.add(at + "nest " + i + " in the camp (" + Math.round(flat(a, l.camp())) + ")");
            for (int j = i + 1; j < l.nests().size(); j++) {
                if (flat(a, l.nests().get(j)) < apart) problems.add(at + "nests " + i + " and " + j + " overlap (" + Math.round(flat(a, l.nests().get(j))) + ")");
            }
        }
        BlockPos camp = l.camp(), o = l.circle();
        if (!l.land(camp.getX(), camp.getZ())) problems.add(at + "the camp is in the sea");
        boolean wet = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) wet |= dx * dx + dz * dz <= 10 && !l.land(o.getX() + dx, o.getZ() + dz);
        }
        if (wet) problems.add(at + "the circle of light runs into the sea");
        for (BlockPos ruin : l.ruins()) if (!l.land(ruin.getX(), ruin.getZ())) problems.add(at + "a ruin in the sea");
        if (flat(l.descent(), c) > l.hiveRadius() - 2 || l.descent().getZ() <= c.getZ()) problems.add(at + "the pit is not inside the hive's south side");
        if (l.mouth().getZ() <= l.descent().getZ()) problems.add(at + "the mouth is not south of the pit");
        if (l.chamber().getY() + l.chamberHeight() >= l.base()) problems.add(at + "the chamber breaks through the island's floor");
        for (BlockPos p : List.of(l.dais(), l.arrival(), l.cocoon())) {
            if (!l.inChamber(p) || !l.contains(p)) problems.add(at + p.toShortString() + " is outside the chamber");
        }
        if (l.arrival().getZ() <= l.dais().getZ()) problems.add(at + "the way in is not across the chamber from the queen");
    }

    /** No pick or block of a hunter's changes the island (an operator in creative may), and no blast takes a block of it. */
    @GameTest(template = ARENA)
    public static void theIslandHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "isle_digger", PlayerRank.A);
        BlockPos on = IsleTestAccess.plan(false, 1L, 1).camp();
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(IsleWorld.onIsle(level, on) && !IsleWorld.onIsle(level, p.blockPosition()), "the island's lane is told from the arena");
        helper.assertTrue(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, on, stone, p)).isCanceled(), "a hunter can't dig into the island");
        BlockEvent.EntityPlaceEvent place = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, on), stone, p);
        helper.assertTrue(NeoForge.EVENT_BUS.post(place).isCanceled(), "nor build on it");
        Explosion blast = new Explosion(level, null, on.getX() + 0.5, on.getY() + 0.5, on.getZ() + 0.5, 4f, false, Explosion.BlockInteraction.DESTROY);
        blast.getToBlow().add(on);
        blast.getToBlow().add(on.above());
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, blast, new ArrayList<Entity>()));
        helper.assertTrue(blast.getToBlow().isEmpty(), "a blast takes nothing of it");
        p.setGameMode(GameType.CREATIVE);
        helper.assertTrue(!NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, on, stone, p)).isCanceled(), "an operator in creative may");
        helper.succeed();
    }

    /** The island's titles are in the chronicle: Hive Breaker, Nestburner, and the Kingslayer, kept secret until earned. */
    @GameTest(template = ARENA)
    public static void theIslandsTitlesAreInTheChronicle(GameTestHelper helper) {
        for (String key : List.of(IsleJournal.HIVE_BREAKER, IsleJournal.KINGSLAYER, IsleJournal.NESTBURNER)) {
            helper.assertTrue(Titles.byKey(key) != null, key + " is a title");
        }
        TitleDef king = Titles.byKey(IsleJournal.KINGSLAYER);
        helper.assertTrue(king != null && king.secret(), "the Kingslayer is secret until earned");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the call

    /**
     * The Association calls awakened hunters of its rank (B and up), not weaker ones nor anyone the System hasn't
     * awakened, and they can't answer it on their own. A call nobody takes closes; an operator's call reaches any rank,
     * and an answer can be taken back while the muster is open.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "isle_call")
    public static void theAssociationCallsHuntersOfItsRank(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        IsleAdmin.stop(server);
        IsleTestAccess.shape(1, 0, 0, 3);
        ServerPlayer strong = hunter(helper, "isle_strong", PlayerRank.A);
        ServerPlayer weak = hunter(helper, "isle_weak", PlayerRank.E);
        ServerPlayer plain = hunter(helper, "isle_plain", null);
        IsleRaids.Outcome out = IsleRaids.call(server, false, List.of(strong, weak, plain));
        helper.assertTrue(out.ok(), "a call goes out: " + out.key());
        IsleRaid r = IsleData.get(server).raid();
        helper.assertTrue(r != null && r.phase() == IsleRaid.Phase.MUSTER && r.nests() == 1, "the muster is open");
        helper.assertTrue(r.called().equals(Set.of(strong.getUUID())), "only the A-rank hunter is called: " + r.called().size());
        helper.assertTrue(IsleRaids.join(weak).key().equals("rank") && IsleRaids.join(plain).key().equals("rank"),
                "the others can't answer it");
        helper.assertTrue(IsleRaids.decline(strong).ok() && r.declined().contains(strong.getUUID()), "the hunter lets it pass");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(IsleData.get(server).raid() == null, "a call nobody takes closes"))
                .thenExecute(() -> {
                    IsleRaids.Outcome forced = IsleAdmin.call(server, List.of(weak));
                    IsleRaid f = IsleData.get(server).raid();
                    helper.assertTrue(forced.ok() && f != null && f.called().contains(weak.getUUID()), "an operator's call reaches any rank: " + forced.key());
                    helper.assertTrue(IsleRaids.join(weak).ok() && f.joined().contains(weak.getUUID()), "and the hunter answers");
                    helper.assertTrue(IsleRaids.join(weak).key().equals("already"), "once");
                    helper.assertTrue(IsleRaids.leave(weak).ok() && !f.joined().contains(weak.getUUID()), "and can take it back while the muster is open");
                    helper.assertTrue(IsleAdmin.stop(server) && IsleData.get(server).raid() == null, "the operator calls it off");
                    IsleTestAccess.unshape();
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ the raid

    /**
     * A hunter retakes the island: answers the call and lands with the boats on an island built for them, beats a
     * nest's heart until it bursts (a stray blow or two in the same moment don't count), goes down the pit when the
     * last nest burns and the hive opens, wakes the queen and fells her, fells the king born from her cocoon, and is
     * paid (the record, both titles, merit, gold, a blessed box, the System's quests done) and sent home.
     */
    @GameTest(template = ARENA, timeoutTicks = 1600, batch = "isle_flow")
    public static void aHunterRetakesTheIsland(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel isle = IsleWorld.level(server);
        IsleAdmin.stop(server);
        IsleTestAccess.shape(1, 0, 0, 3);
        ServerPlayer p = hunter(helper, "isle_hunter", PlayerRank.A);
        Vec3 from = p.position();
        IsleRaids.Outcome called = IsleRaids.call(server, false, List.of(p));
        helper.assertTrue(called.ok(), "the Association calls: " + called.key());
        IsleRaid r = IsleData.get(server).raid();
        helper.assertTrue(r != null && r.called().contains(p.getUUID()), "the hunter is called");
        helper.assertTrue(IsleRaids.join(p).ok() && r.joined().contains(p.getUUID()), "and answers");
        IsleWorld.Layout l = IsleTestAccess.layout(server, r);
        BlockPos nest = l.nests().get(0);
        List<ChunkPos> chunks = island(l);
        force(isle, chunks, true);
        int done = QuestEngine.data(p).completedCount();
        long[] gold = new long[1];
        helper.startSequence()
                // everyone called has answered: the boats leave a few seconds later, with the island built for them
                .thenWaitUntil(() -> helper.assertTrue(r.phase() == IsleRaid.Phase.NESTS, "landed: " + r.phase()))
                .thenExecute(() -> {
                    helper.assertTrue(IsleTestAccess.built(server, r.seed(), 1), "the island stands");
                    helper.assertTrue(IsleWorld.onIsle(p.level(), p.blockPosition()) && flat(p.blockPosition(), l.circle()) <= 4,
                            "the hunter lands at the circle of light: " + p.blockPosition().toShortString());
                    helper.assertTrue(IsleRaids.occupied(p), "and is the raid's while there (no trials, no regroup)");
                    IsleRecord rec = IsleAdmin.record(p);
                    helper.assertTrue(rec.raids() == 1 && r.id().equals(rec.raid()), "their record has the raid");
                    Quest q = quest(p, IsleRaids.QUEST_RETAKE);
                    helper.assertTrue(q != null && objective(q, IsleRaids.NEST_KEY).required() == 1, "and the System asks them to retake the island");
                    helper.assertTrue(TestChunks.ticking(isle, chunks), "the island's ground is awake");
                    // the swarm's eggs: nothing that hatches (sniffer eggs did, into sniffers), no turtle egg on sand
                    int[] eggs = eggs(isle, l);
                    helper.assertTrue(eggs[0] > 0 && eggs[1] == 0, "the nests and the chamber hold eggs that never hatch: " + eggs[0]
                            + " clutches, " + eggs[1] + " that would hatch");
                    BlockPos egg = chamberEgg(isle, l);
                    helper.assertTrue(egg != null, "eggs line the chamber's walls");
                    isle.setBlock(egg, Blocks.AIR.defaultBlockState(), 2);
                    IsleTestAccess.relayEggs(server, r);
                    helper.assertTrue(isle.getBlockState(egg).is(Blocks.TURTLE_EGG), "a clutch trampled in a fight is laid again before a landing");
                })
                .thenWaitUntil(() -> helper.assertTrue(heart(isle, r, nest) != null, "the nest's heart beats"))
                .thenExecute(() -> {
                    NestHeartEntity h = heart(isle, r, nest);
                    helper.assertTrue(h != null && h.needed() == 3 && h.blows() == 0 && IsleTestAccess.blowsNeeded(r) == 3, "three blows burst it");
                    helper.assertTrue(!h.hurt(isle.damageSources().generic(), 50f) && h.blows() == 0, "a stray blow does nothing");
                    p.moveTo(nest.getX() + 2.5, nest.getY() + 1, nest.getZ() + 0.5);
                    helper.assertTrue(h.hurt(isle.damageSources().playerAttack(p), 1f) && h.blows() == 1, "a hunter's is one blow");
                    helper.assertTrue(!h.hurt(isle.damageSources().playerAttack(p), 1f) && h.blows() == 1, "two in the same moment are one");
                    helper.assertTrue(IsleTestAccess.blows(r, 0) == 1, "the raid keeps the count");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    NestHeartEntity h = heart(isle, r, nest);
                    helper.assertTrue(h != null && h.hurt(isle.damageSources().playerAttack(p), 1f) && h.blows() == 2, "a second blow");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    NestHeartEntity h = heart(isle, r, nest);
                    helper.assertTrue(h != null && h.hurt(isle.damageSources().playerAttack(p), 1f), "a third");
                    helper.assertTrue(r.burned(0) && r.nestsBurned() == 1 && heart(isle, r, nest) == null, "the heart bursts: the nest burns");
                    helper.assertTrue(IsleAdmin.record(p).nests() == 1, "with the hunter there");
                    Quest q = quest(p, IsleRaids.QUEST_RETAKE);
                    helper.assertTrue(q != null && objective(q, IsleRaids.NEST_KEY).progress() == 1, "the quest counts it");
                    helper.assertTrue(r.phase() == IsleRaid.Phase.HIVE && boss(isle, r.queen()) instanceof QueenBoss queen && !queen.isAwake(),
                            "the last nest opens the hive: the queen waits below, asleep");
                    // down the pit
                    BlockPos d = l.descent();
                    p.moveTo(d.getX() + 0.5, l.mouth().getY() - 2, d.getZ() + 0.5);
                })
                .thenWaitUntil(() -> helper.assertTrue(r.phase() == IsleRaid.Phase.QUEEN, "down the pit: " + r.phase()))
                .thenExecute(() -> {
                    helper.assertTrue(l.inChamber(p.blockPosition()), "the hunter is in the queen's chamber: " + p.blockPosition().toShortString());
                    helper.assertTrue(boss(isle, r.queen()) instanceof QueenBoss q && q.isAwake(), "and she wakes");
                })
                .thenWaitUntil(() -> {
                    ColossalBoss q = boss(isle, r.queen());
                    helper.assertTrue(q != null && q.introTicks() <= 0, "her entrance is over");
                })
                .thenWaitUntil(() -> {
                    ColossalBoss q = boss(isle, r.queen());
                    helper.assertTrue(q != null && q.getTarget() == p, "she goes for the hunter in her chamber");
                })
                .thenExecute(() -> {
                    ColossalBoss q = boss(isle, r.queen());
                    // the rankings' hunters keep the swarm off: their blows land on the royalty at a fraction
                    GuildHunterEntity ally = GuildModule.HUNTER.get().create(isle);
                    float before = q.getHealth();
                    q.hurt(isle.damageSources().mobAttack(ally), 1000f);
                    float lost = before - q.getHealth();
                    helper.assertTrue(lost > 0 && lost < 200, "an ally's blow lands on her at a fraction: " + lost);
                    Buildups.add(q, Statuses.CRIMSON, 1000f, ally);
                    helper.assertTrue(!q.hasEffect(ModEffects.CRIMSON_BRAND), "and carries no brand onto her");
                    Buildups.add(q, Statuses.VENOM, 1000f, p);
                    q.addEffect(new MobEffectInstance(ModEffects.HEAVY_VENOM, 200, 0), p);
                    helper.assertTrue(!q.hasEffect(ModEffects.VENOM) && !q.hasEffect(ModEffects.HEAVY_VENOM), "the venom is her own: none takes hold on her");
                    Buildups.add(q, Statuses.CRIMSON, 1000f, p);
                    helper.assertTrue(q.hasEffect(ModEffects.CRIMSON_BRAND), "a hunter's brand does");
                    q.forcePhase(isle, 3);
                    q.hurt(isle.damageSources().playerAttack(p), q.getMaxHealth() * 3);
                    helper.assertTrue(q.isDeadOrDying(), "the queen falls");
                    helper.assertTrue(r.phase() == IsleRaid.Phase.KING && r.king() == null, "and her cocoon stirs");
                    helper.assertTrue(IsleAdmin.record(p).queens() == 1, "her fall is in the hunter's record");
                    helper.assertTrue(quest(p, IsleRaids.QUEST_RETAKE) == null && QuestEngine.data(p).completedCount() >= done + 1,
                            "the island's quest is done");
                })
                .thenWaitUntil(() -> helper.assertTrue(boss(isle, r.king()) instanceof KingBoss k && k.isAwake(), "the king is born"))
                .thenExecute(() -> helper.assertTrue(quest(p, IsleRaids.QUEST_KING) != null, "and the System calls for him"))
                .thenWaitUntil(() -> {
                    ColossalBoss k = boss(isle, r.king());
                    helper.assertTrue(k != null && k.introTicks() <= 0, "his entrance is over");
                })
                .thenWaitUntil(() -> {
                    ColossalBoss k = boss(isle, r.king());
                    helper.assertTrue(k != null && k.getTarget() == p, "he goes for the hunter in the chamber");
                })
                .thenExecute(() -> {
                    ColossalBoss k = boss(isle, r.king());
                    // a leap at someone on the dais comes down on the dais, not in the roof where the vault is low over it
                    Vec3 onDais = Vec3.atBottomCenterOf(l.dais().above());
                    double landing = k.ground(onDais).y;
                    helper.assertTrue(landing <= l.dais().getY() + 1.01, "a leap at the dais lands on it: " + landing + " for " + onDais.y);
                    // and a king left in the rock overhead is set back down on the chamber's floor
                    k.teleportTo(onDais.x, l.chamber().getY() + l.chamberHeight() + 1, onDais.z);
                })
                .thenWaitUntil(() -> {
                    ColossalBoss k = boss(isle, r.king());
                    helper.assertTrue(k != null && k.getY() < l.chamber().getY() + 4 && !k.isInWall(), "the king is back on the floor: " + (k == null ? "-" : k.getY()));
                })
                .thenExecute(() -> {
                    ColossalBoss k = boss(isle, r.king());
                    gold[0] = Economy.gold(p);
                    k.forcePhase(isle, 3);
                    k.hurt(isle.damageSources().playerAttack(p), k.getMaxHealth() * 3);
                    helper.assertTrue(k.isDeadOrDying(), "the king falls");
                    helper.assertTrue(r.phase() == IsleRaid.Phase.CLEARED, "the island is retaken: " + r.phase());
                    IsleRecord rec = IsleAdmin.record(p);
                    helper.assertTrue(rec.cleared() == 1 && rec.kings() == 1 && rec.best() > 0, "the record counts it, with the time to beat");
                    helper.assertTrue(ChronicleModule.data(p).hasTitle(IsleJournal.HIVE_BREAKER), "the hunter is a Hive Breaker");
                    helper.assertTrue(ChronicleModule.data(p).hasTitle(IsleJournal.KINGSLAYER), "and the Kingslayer, having done the most to him");
                    helper.assertTrue(Association.record(p).merit() >= 400 && Economy.gold(p) > gold[0], "paid merit and gold");
                    helper.assertTrue(holdsBlessedBox(p), "and a blessed box");
                    helper.assertTrue(quest(p, IsleRaids.QUEST_KING) == null && QuestEngine.data(p).completedCount() >= done + 2, "the king's quest is done");
                    List<Mob> left = isle.getEntitiesOfClass(Mob.class, new AABB(l.center()).inflate(l.radius() + 40, 80, l.radius() + 40),
                            m -> m.isAlive() && m.getPersistentData().hasUUID(IsleRaids.TAG) && !(m instanceof ColossalBoss));
                    helper.assertTrue(left.isEmpty(), "the swarm dies with him: " + left.size() + " left");
                    IsleTestAccess.endSoon(r);
                })
                .thenWaitUntil(() -> helper.assertTrue(IsleData.get(server).raid() == null, "the raid is over"))
                .thenExecute(() -> {
                    helper.assertTrue(!IsleWorld.onIsle(p.level(), p.blockPosition()) && p.position().distanceTo(from) < 3,
                            "the hunter is home where they were called from: " + p.blockPosition().toShortString());
                    helper.assertTrue(IsleAdmin.record(p).raid() == null && !IsleRaids.occupied(p), "and free");
                    force(isle, chunks, false);
                    IsleTestAccess.unshape();
                })
                .thenSucceed();
    }

    /**
     * A blow that would kill a hunter on the island doesn't: the medics carry them off it, out of the raid, home and
     * hurt, their quest failed. Allies from the national rankings fight beside the hunters. The raid goes on while
     * anyone is still fighting, and is lost when nobody is.
     */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = "isle_fall")
    public static void aFallenHunterIsCarriedOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel isle = IsleWorld.level(server);
        IsleAdmin.stop(server);
        IsleTestAccess.shape(1, 0, 2, 3);
        ServerPlayer a = hunter(helper, "isle_fall_a", PlayerRank.A);
        ServerPlayer b = hunter(helper, "isle_fall_b", PlayerRank.S);
        Vec3 home = a.position();
        IsleRaids.Outcome called = IsleAdmin.call(server, List.of(a, b));
        IsleRaid r = IsleData.get(server).raid();
        helper.assertTrue(called.ok() && r != null, "a raid is called for them: " + called.key());
        // the ground the boats land on stays awake for the allies stepping in beside them
        List<ChunkPos> camp = new ArrayList<>();
        ChunkPos at = new ChunkPos(IsleTestAccess.layout(server, r).circle());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) camp.add(new ChunkPos(at.x + x, at.z + z));
        force(isle, camp, true);
        helper.assertTrue(TestChunks.ticking(isle, camp), "the camp's ground is awake");
        IsleRaids.Outcome landed = IsleAdmin.land(server, List.of(a, b));
        helper.assertTrue(landed.ok() && r.phase().landed(), "both land: " + landed.key());
        helper.assertTrue(IsleWorld.onIsle(a.level(), a.blockPosition()) && IsleWorld.onIsle(b.level(), b.blockPosition()), "both on the island");
        helper.assertTrue(r.allies().size() == 2, "two hunters of the national rankings come along: " + r.allies().size());
        float max = a.getMaxHealth();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (IsleRaid.Ally ally : r.allies()) {
                        helper.assertTrue(ally.entity() != null && isle.getEntity(ally.entity()) instanceof GuildHunterEntity e && e.isAlive()
                                && IsleWorld.onIsle(isle, e.blockPosition()), ally.hired().name + " steps in beside them");
                    }
                })
                .thenExecute(() -> {
                    // a blow that doesn't kill is only a blow
                    LivingDamageEvent.Pre light = NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(a, new DamageContainer(a.damageSources().generic(), 1f)));
                    helper.assertTrue(light.getNewDamage() > 0 && r.fighting(a.getUUID()), "a light blow leaves the hunter fighting");
                    LivingDamageEvent.Pre fatal = NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(a, new DamageContainer(a.damageSources().generic(), max * 100)));
                    helper.assertTrue(fatal.getNewDamage() == 0f, "the killing blow does nothing");
                })
                .thenWaitUntil(() -> helper.assertTrue(r.fallen().contains(a.getUUID()), "the hunter is carried off"))
                .thenExecute(() -> {
                    helper.assertTrue(!IsleWorld.onIsle(a.level(), a.blockPosition()) && a.position().distanceTo(home) < 3,
                            "home where they were called from: " + a.blockPosition().toShortString());
                    helper.assertTrue(a.isAlive() && a.getHealth() >= 1 && a.getHealth() <= max * IsleTestAccess.carriedHealth() + 1, "hurt: " + a.getHealth());
                    helper.assertTrue(quest(a, IsleRaids.QUEST_RETAKE) == null, "their quest failed");
                    helper.assertTrue(r.phase().landed() && r.fighting(b.getUUID()), "the raid goes on for the other");
                    helper.assertTrue(IsleRaids.leave(b).ok() && r.left().contains(b.getUUID()), "who walks off it");
                    helper.assertTrue(!IsleWorld.onIsle(b.level(), b.blockPosition()), "and is taken home");
                })
                .thenWaitUntil(() -> helper.assertTrue(r.phase() == IsleRaid.Phase.FAILED && r.lost() == IsleRaid.Lost.FALLEN,
                        "with nobody left fighting, the raid is lost: " + r.phase() + " " + r.lost()))
                .thenWaitUntil(() -> helper.assertTrue(IsleData.get(server).raid() == null, "and over"))
                .thenExecute(() -> {
                    force(isle, camp, false);
                    IsleTestAccess.unshape();
                })
                .thenSucceed();
    }
}
