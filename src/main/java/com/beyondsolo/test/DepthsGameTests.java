package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.HitTags;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.DepthsCommand;
import com.beyondsolo.depths.DepthsModule;
import com.beyondsolo.depths.DepthsTestAccess;
import com.beyondsolo.depths.GateDepths;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.depths.HiddenSealBlock;
import com.beyondsolo.depths.TrapCoreBlock;
import com.beyondsolo.depths.TrapKind;
import com.beyondsolo.depths.TrapRooms;
import com.beyondsolo.depths.WeaknessLog;
import com.beyondsolo.depths.WeatherService;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The depths: elemental profiles, gate weather, hidden chambers and trap rooms. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DepthsGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer hunter(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.MAGE);
        QuestTestAccess.markStarted(p);
        WeaknessLog.of(p).clear();
        return p;
    }

    /** A C-rank gate in the arena with its depths forced ("seed" keeps what the seed says), not built yet. */
    private static GateInstance gate(GameTestHelper helper, String weather, String hidden, String trap) {
        GateBlockEntity door = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), PlayerRank.C, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(door.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        // the original cave of its rank, so every run tests the same thing (DungeonGameTests carves every realm and shape)
        g.setDungeon(com.beyondsolo.gate.GateRealm.classic(g.rank()), com.beyondsolo.gate.DungeonShape.CAVERNS);
        DepthsCommand.force(g, weather, hidden, trap);
        return g;
    }

    private static ServerLevel dungeon(GameTestHelper helper) {
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(d != null, "the gate dimension exists");
        return d;
    }

    /**
     * Keeps the chunks around a room loaded (and ticking) for a test, waiting until they are, so creatures that rise in
     * the room can be found at once; returns them to release later.
     */
    private static List<ChunkPos> hold(ServerLevel level, BlockPos center, int radius) {
        List<ChunkPos> out = new ArrayList<>();
        ChunkPos c = new ChunkPos(center);
        int r = Math.max(1, (radius >> 4) + 1);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            level.setChunkForced(c.x + x, c.z + z, true);
            out.add(new ChunkPos(c.x + x, c.z + z));
        }
        TestChunks.ticking(level, out);
        return out;
    }

    /** Which dungeon a gate made here leads to (they are random), for a failure message. */
    private static String where(GateInstance g) {
        return " (" + g.realm().id() + "/" + g.shape().id() + " " + g.rank() + " seed " + g.seed() + ")";
    }

    private static void release(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, false);
    }

    private static List<BlockPos> find(ServerLevel level, BlockPos center, int rx, int ry, int rz, Class<?> block) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz))) {
            if (block.isInstance(level.getBlockState(p).getBlock())) out.add(p.immutable());
        }
        return out;
    }

    private static DungeonLayout.Room trapRoom(GateInstance g) {
        for (var e : g.depths().traps(g).entrySet()) return g.layout().rooms().get(e.getKey());
        throw new IllegalStateException("no trap room");
    }

    private static void killAll(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        BlockPos c = g.world(room.center());
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(c).inflate(room.rx() + 4, room.ry() + 5, room.rz() + 4),
                m -> m.getPersistentData().hasUUID(GateWiper.GATE_ID_TAG))) {
            m.discard();
        }
    }

    // ------------------------------------------------------------------ elemental profiles

    @GameTest(template = ARENA)
    public static void profilesFollowWhatACreatureIs(GameTestHelper helper) {
        ElementalProfile blaze = ElementalProfile.of(EntityType.BLAZE, Affinity.NONE, null);
        helper.assertTrue(blaze.rating(SkillElement.FIRE) == ElementalProfile.Rating.IMMUNE, "a blaze can't burn");
        helper.assertTrue(blaze.rating(SkillElement.WATER) == ElementalProfile.Rating.WEAK_SEVERE, "it drowns: " + blaze.rating(SkillElement.WATER));
        helper.assertTrue(blaze.rating(SkillElement.AIR) == ElementalProfile.Rating.RESIST, "and rides the wind");
        helper.assertTrue(blaze.weaknesses().equals(List.of(SkillElement.WATER)), "its weaknesses: " + blaze.weaknesses());
        helper.assertTrue(blaze.resistances().get(0) == SkillElement.FIRE, "immune first: " + blaze.resistances());

        ElementalProfile zombie = ElementalProfile.of(EntityType.ZOMBIE, Affinity.NONE, null);
        helper.assertTrue(zombie.rating(SkillElement.LIGHT).weak() && zombie.rating(SkillElement.FIRE).weak(), "the undead fear light and fire");
        helper.assertTrue(zombie.rating(SkillElement.SHADOW).resists() && zombie.rating(SkillElement.NATURE).resists(), "and shrug off shadow and rot");
        helper.assertTrue(zombie.rating(SkillElement.PHYSICAL) == ElementalProfile.Rating.NEUTRAL, "a plain blade lands as ever");
        helper.assertTrue(Math.abs(zombie.multiplier(SkillElement.LIGHT) - 1.5f) < 1e-4 && Math.abs(blaze.multiplier(SkillElement.WATER) - 2f) < 1e-4
                && blaze.multiplier(SkillElement.FIRE) == 0f, "weak x1.5, severe x2, immune x0");

        // an affinity on top: a flame zombie no longer fears fire, and its rot resistance stacks
        ElementalProfile flame = ElementalProfile.of(EntityType.ZOMBIE, Affinity.FLAME, null);
        helper.assertTrue(flame.rating(SkillElement.FIRE) == ElementalProfile.Rating.RESIST, "steeped in fire, it resists fire: " + flame.rating(SkillElement.FIRE));
        helper.assertTrue(flame.rating(SkillElement.NATURE) == ElementalProfile.Rating.RESIST_STRONG, "two resistances stack");
        helper.assertTrue(flame.rating(SkillElement.WATER).weak() && flame.rating(SkillElement.LIGHT).weak(), "water and light still hurt it");
        helper.assertTrue(!flame.key().equals(zombie.key()) && flame.key().startsWith("minecraft:zombie"), "its own entry: " + flame.key());
        helper.assertTrue(ElementalProfile.of(EntityType.ZOMBIE, Affinity.NONE, null) == zombie, "profiles are shared");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void skillHitsLandByProfileAndWeaknessesAreNoted(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "depths_mage");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        float light = SkillCombat.strike(p, zombie, SkillElement.LIGHT, 10f);
        helper.assertTrue(Math.abs(light - 15f) < 1e-3, "light lands half again as hard on the undead: " + light);
        HitTags.Tag tag = HitTags.take(zombie);
        helper.assertTrue(tag != null && tag.element() == SkillElement.LIGHT && tag.rating() == ElementalProfile.Rating.WEAK, "the hit is tagged for its number");
        helper.assertTrue(WeaknessLog.of(p).knows("minecraft:zombie", SkillElement.LIGHT), "and the weakness is noted");
        helper.assertTrue(!WeaknessLog.of(p).knows("minecraft:zombie", SkillElement.FIRE), "only what was found");

        Zombie other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        float shadow = SkillCombat.strike(p, other, SkillElement.SHADOW, 10f);
        helper.assertTrue(Math.abs(shadow - 5f) < 1e-3, "shadow is shrugged off: " + shadow);
        helper.assertTrue(WeaknessLog.of(p).size() == 1, "a resisted hit notes nothing");

        float physical = SkillCombat.strike(p, other, SkillElement.PHYSICAL, 10f);
        helper.assertTrue(Math.abs(physical - 10f) < 1e-3, "physical is never rated");
        helper.succeed();
    }

    // ------------------------------------------------------------------ weather

    @GameTest(template = ARENA)
    public static void weatherIsSeededAndShiftsTheElements(GameTestHelper helper) {
        for (long seed = 0; seed < 50; seed++) {
            helper.assertTrue(GateWeather.roll(seed, PlayerRank.B, GateTheme.B) == GateWeather.roll(seed, PlayerRank.B, GateTheme.B), "same seed, same sky");
        }
        Map<GateWeather, Integer> seenE = new EnumMap<>(GateWeather.class);
        Map<GateWeather, Integer> seenS = new EnumMap<>(GateWeather.class);
        int n = 6000;
        for (long seed = 0; seed < n; seed++) {
            seenE.merge(GateWeather.roll(seed * 7919L, PlayerRank.E, GateTheme.E), 1, Integer::sum);
            seenS.merge(GateWeather.roll(seed * 7919L, PlayerRank.S, GateTheme.S), 1, Integer::sum);
        }
        float clearE = seenE.getOrDefault(GateWeather.CLEAR, 0) / (float) n, clearS = seenS.getOrDefault(GateWeather.CLEAR, 0) / (float) n;
        helper.assertTrue(Math.abs(clearE - 0.5f) < 0.04f && Math.abs(clearS - 0.25f) < 0.04f, "half the E caves are still, a quarter of the S: " + clearE + " " + clearS);
        for (GateWeather w : GateWeather.values()) helper.assertTrue(seenS.getOrDefault(w, 0) > 0, "every weather turns up: " + w);
        GateWeather themed = GateWeather.themed(GateTheme.S);
        for (GateWeather w : GateWeather.values()) {
            if (w == GateWeather.CLEAR || w == themed) continue;
            helper.assertTrue(seenS.get(themed) > seenS.get(w) * 2, "an S cave leans to " + themed + " over " + w);
        }

        helper.assertTrue(Math.abs(GateWeather.STORM.factor(SkillElement.LIGHTNING) - 1.3f) < 1e-4, "a storm feeds lightning");
        helper.assertTrue(Math.abs(GateWeather.STORM.factor(SkillElement.EARTH) - 0.9f) < 1e-4, "and shakes earth");
        helper.assertTrue(GateWeather.STORM.factor(SkillElement.FIRE) == 1f && GateWeather.CLEAR.factor(SkillElement.FIRE) == 1f, "the rest as ever");

        GateInstance g = gate(helper, "crimson", "none", "none");
        helper.assertTrue(g.depths().weather(g) == GateWeather.CRIMSON && Math.abs(WeatherService.rewardBonus(g) - 1.25f) < 1e-4, "a forced haze pays a quarter more");
        g.depths().setWeather(GateWeather.CLEAR);
        helper.assertTrue(WeatherService.rewardBonus(g) == 1f, "a still cave pays as ever");
        // out in the world (the arena is no cave) nothing changes
        helper.assertTrue(WeatherService.weatherAt(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1))) == GateWeather.CLEAR, "no weather outside the caves");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void depthsSaveAndLoad(GameTestHelper helper) {
        GateInstance g = gate(helper, "storm", "lair", "lockdown");
        GateDepths d = g.depths();
        CompoundTag saved = d.save();
        GateDepths back = GateDepths.load(saved);
        helper.assertTrue(back.enabled() && back.weather(g) == GateWeather.STORM && back.hidden(g) == HiddenKind.LAIR, "overrides survive a save");
        helper.assertTrue(back.traps(g).equals(d.traps(g)) && back.traps(g).containsValue(TrapKind.LOCKDOWN), "and the forced traps: " + back.traps(g));
        helper.assertTrue(!back.hiddenOpen() && !back.trapTriggered(trapRoom(g).index()), "nothing done yet");
        helper.assertTrue(!GateDepths.legacy().enabled() && GateDepths.legacy().hidden(g) == null && GateDepths.legacy().traps(g).isEmpty(),
                "an old cave keeps its rock: weather only");
        helper.succeed();
    }

    // ------------------------------------------------------------------ hidden chambers

    @GameTest(template = ARENA)
    public static void hiddenChambersAreSecludedAndLeaveTheCaveAsItWas(GameTestHelper helper) {
        int placed = 0, tried = 0;
        for (GateTheme theme : GateTheme.values()) {
            for (long seed = 0; seed < 12; seed++) {
                long s = 99991L * seed + theme.ordinal();
                tried++;
                if (secludedChamber(helper, DungeonLayout.plan(s, theme, false), DungeonLayout.plan(s, theme, true), DungeonLayout.plan(s, theme, true))) placed++;
            }
        }
        helper.assertTrue(placed >= tried * 3 / 4, "most caves have room for one: " + placed + "/" + tried);
        // and in every realm, in every layout it is dug in
        placed = 0;
        tried = 0;
        for (GateRealm realm : GateRealm.values()) {
            if (realm.weight == 0) continue;
            for (DungeonShape shape : realm.shapes()) {
                for (long seed = 0; seed < 4; seed++) {
                    long s = 7919L * seed + realm.ordinal() * 131L + shape.ordinal();
                    GateTheme theme = GateTheme.of(seed % 2 == 0 ? realm.minRank : realm.maxRank);
                    tried++;
                    if (secludedChamber(helper, DungeonLayout.plan(s, theme, realm, shape, false), DungeonLayout.plan(s, theme, realm, shape, true),
                            DungeonLayout.plan(s, theme, realm, shape, true))) placed++;
                }
            }
        }
        helper.assertTrue(placed >= tried * 9 / 10, "nearly every dungeon has room for one: " + placed + "/" + tried);
        helper.succeed();
    }

    /** Checks a dungeon planned without and with its hidden chamber (twice); true if the chamber found room. */
    private static boolean secludedChamber(GameTestHelper helper, DungeonLayout plain, DungeonLayout with, DungeonLayout again) {
        String what = with.realm().id() + "/" + with.shape().id() + ": ";
        helper.assertTrue(with.rooms().equals(again.rooms()), what + "same seed, same chamber");
        helper.assertTrue(with.rooms().subList(0, plain.rooms().size()).equals(plain.rooms())
                && with.tunnels().subList(0, plain.tunnels().size()).equals(plain.tunnels()), what + "the rest of the cave is untouched");
        DungeonLayout.Room h = with.hidden();
        if (h == null) return false;
        DungeonLayout.Tunnel t = with.hiddenTunnel();
        DungeonLayout.Room from = t == null ? null : with.rooms().get(t.from());
        helper.assertTrue(from != null && from.kind() != DungeonLayout.Kind.BOSS && from.shape() != DungeonLayout.Shape.ISLAND,
                what + "it hides off a room on the way (not the boss's, not an island)");
        // the caves of the original kind keep it where it always was: off a cavern
        if (with.shape() == DungeonShape.CAVERNS && with.realm().build == GateRealm.Build.CAVE && !with.realm().corridors) {
            helper.assertTrue(from.kind() == DungeonLayout.Kind.CAVERN, what + "it hides off a cavern");
        }
        helper.assertTrue(with.tunnelsOf(h.index()).size() == 1, what + "one way in");
        int[] bounds = with.bounds();
        helper.assertTrue(bounds[2] - bounds[0] < GateInstance.SLOT_SPACING / 2 && bounds[3] - bounds[1] < GateInstance.SLOT_SPACING, what + "still fits in its slot");
        for (DungeonLayout.Room o : with.rooms()) {
            if (o == h) continue;
            double min = o.reach() + h.reach();
            helper.assertTrue(o.center().distSqr(h.center()) >= min * min, what + "it overlaps no room");
        }
        return true;
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aVaultIsWalledOffAndKnocksOpen(GameTestHelper helper) {
        GateInstance g = gate(helper, "clear", "vault", "none");
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        DungeonLayout.Room room = g.layout().hidden();
        helper.assertTrue(room != null && g.depths().hidden(g) == HiddenKind.VAULT, "the cave has its vault");
        DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
        helper.assertTrue(m != null, "the way in has a mouth");
        BlockPos mouth = BlockPos.containing(m.center());
        int reach = (int) Math.ceil(m.radius() + 3);
        List<BlockPos> wall = find(d, mouth, reach, reach, reach, HiddenSealBlock.class);
        helper.assertTrue(wall.size() >= 4, "a wall stands across it: " + wall.size());
        BlockState look = d.getBlockState(wall.get(0));
        helper.assertTrue(look.getValue(HiddenSealBlock.LOOK).mimic != null, "passing for the cave's rock");
        BlockPos center = g.world(room.center());
        List<BlockPos> caches = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-room.rx() - 1, -room.ry() - 1, -room.rz() - 1), center.offset(room.rx() + 1, room.ry() + 1, room.rz() + 1))) {
            if (d.getBlockEntity(p) instanceof RewardCacheBlockEntity c && c.isHidden()) caches.add(p.immutable());
        }
        helper.assertTrue(caches.size() == 1, "the vault's cache waits inside: " + caches.size());

        ServerPlayer p = hunter(helper, "depths_knocker");
        BlockPos struck = wall.get(0);
        HiddenRooms.knock(p, struck);
        helper.assertTrue(!g.depths().hiddenOpen(), "one knock: it sounds hollow");
        HiddenRooms.knock(p, struck);
        helper.assertTrue(!g.depths().hiddenOpen(), "too soon: a knock needs a moment");
        DepthsTestAccess.resetCooldowns(p);
        HiddenRooms.knock(p, struck);
        DepthsTestAccess.resetCooldowns(p);
        HiddenRooms.knock(p, struck);
        helper.assertTrue(g.depths().hiddenOpen(), "the third knock brings it down");
        helper.succeedWhen(() -> helper.assertTrue(find(d, mouth, reach, reach, reach, HiddenSealBlock.class).isEmpty(), "the wall crumbles away"));
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theShrineBlessesOnce(GameTestHelper helper) {
        GateInstance g = gate(helper, "clear", "shrine", "none");
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        DungeonLayout.Room room = g.layout().hidden();
        helper.assertTrue(room != null, "the cave has its shrine");
        ServerPlayer p = hunter(helper, "depths_pilgrim");
        BlockPos floor = DepthsBuild.floorCenter(d, g, room);
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(!g.depths().hiddenEntered(), "nobody inside yet");
        p.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(!g.depths().hiddenEntered(), "a sealed chamber can't be entered");
        HiddenRooms.open(d, g, p, null);
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(g.depths().hiddenEntered() && HiddenRooms.blessed(g, p), "stepping in brings the blessing");
        helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && p.hasEffect(MobEffects.REGENERATION) && p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "strength, regeneration, resistance");
        p.removeAllEffects();
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(!p.hasEffect(MobEffects.DAMAGE_BOOST), "once a gate");
        helper.assertTrue(g.depths().hiddenDone(), "the shrine has given");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aLairPaysOnceItsGuardiansFall(GameTestHelper helper) {
        GateInstance g = gate(helper, "clear", "lair", "none");
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        DungeonLayout.Room room = g.layout().hidden();
        helper.assertTrue(room != null, "the cave has its lair");
        BlockPos center = g.world(room.center());
        List<ChunkPos> held = hold(d, center, room.reach() + 4);
        ServerPlayer p = hunter(helper, "depths_raider");
        BlockPos floor = DepthsBuild.floorCenter(d, g, room);
        HiddenRooms.open(d, g, p, null);
        p.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        int guardians = DepthsTestAccess.creaturesIn(d, g, room);
        helper.assertTrue(g.depths().hiddenEntered() && guardians >= 3, "its guardians wake: " + guardians + where(g));
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(!g.depths().hiddenDone(), "not while they stand");
        killAll(d, g, room);
        DepthsTestAccess.tickHidden(d, g, List.of(p));
        helper.assertTrue(g.depths().hiddenDone(), "the last one down clears the lair");
        boolean cache = false;
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-room.rx() - 1, -room.ry() - 1, -room.rz() - 1), center.offset(room.rx() + 1, room.ry() + 1, room.rz() + 1))) {
            if (d.getBlockEntity(q) instanceof RewardCacheBlockEntity c && c.isHidden()) cache = true;
        }
        helper.assertTrue(cache, "and its hoard comes down");
        release(d, held);
        helper.succeed();
    }

    // ------------------------------------------------------------------ traps

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aTrapCoreShattersAndDisarmsItsRoom(GameTestHelper helper) {
        GateInstance g = gate(helper, "clear", "none", "spikes");
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        DungeonLayout.Room room = trapRoom(g);
        helper.assertTrue(g.depths().trap(g, room.index()) == TrapKind.SPIKES, "a spike room");
        BlockPos center = g.world(room.center());
        List<BlockPos> cores = find(d, center, room.rx() + 2, room.ry() + 3, room.rz() + 2, TrapCoreBlock.class);
        helper.assertTrue(cores.size() == 1, "its core stands in it: " + cores.size());
        BlockPos core = cores.get(0);
        ServerPlayer p = hunter(helper, "depths_breaker");
        for (int i = 0; i < TrapRooms.CORE_HITS - 1; i++) {
            TrapRooms.hitCore(d, core, p);
            DepthsTestAccess.resetCooldowns(p);
        }
        helper.assertTrue(d.getBlockState(core).getBlock() instanceof TrapCoreBlock && !g.depths().trapDone(room.index()), "it holds a while");
        TrapRooms.hitCore(d, core, p);
        helper.assertTrue(!(d.getBlockState(core).getBlock() instanceof TrapCoreBlock), "then shatters");
        helper.assertTrue(g.depths().trapDone(room.index()), "and the trap is beaten before it ever sprang");
        p.moveTo(center.getX() + 0.5, DepthsBuild.floorCenter(d, g, room).getY() + 1, center.getZ() + 0.5);
        DepthsTestAccess.tickTraps(d, g, List.of(p));
        helper.assertTrue(!g.depths().trapActive(room.index()), "stepping in does nothing now");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aLockdownSealsTheRoomUntilBothWavesFall(GameTestHelper helper) {
        GateInstance g = gate(helper, "clear", "none", "lockdown");
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        DungeonLayout.Room room = trapRoom(g);
        BlockPos center = g.world(room.center());
        List<ChunkPos> held = hold(d, center, room.reach() + 8);
        ServerPlayer p = hunter(helper, "depths_trapped");
        BlockPos floor = DepthsBuild.floorCenter(d, g, room);
        p.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        helper.assertTrue(DepthsTestAccess.inside(g, room, p.position()), "standing in the room");
        DepthsTestAccess.tickTraps(d, g, List.of(p));
        helper.assertTrue(g.depths().trapActive(room.index()) && DepthsTestAccess.wave(g, room.index()) == 1, "it springs: wave one");
        int wards = 0;
        for (DungeonLayout.Tunnel t : g.layout().tunnelsOf(room.index())) {
            DepthsBuild.Mouth m = DepthsBuild.mouth(g, t, room);
            int r = (int) Math.ceil(m.radius() + 3);
            int here = 0;
            for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(m.center()).offset(-r, -r, -r), BlockPos.containing(m.center()).offset(r, r, r))) {
                if (d.getBlockState(q).is(DepthsModule.TRAP_WARD.get())) here++;
            }
            helper.assertTrue(here > 0, "every way out is walled with light");
            wards += here;
        }
        helper.assertTrue(wards > 0 && DepthsTestAccess.creaturesIn(d, g, room) > 0, "and the first wave rises" + where(g));
        final int total = wards;
        helper.startSequence()
                .thenExecute(() -> {
                    killAll(d, g, room);
                    DepthsTestAccess.tickTraps(d, g, List.of(p));
                    helper.assertTrue(DepthsTestAccess.wave(g, room.index()) == 1, "a breath before the second wave");
                })
                .thenIdle(41)
                .thenExecute(() -> {
                    DepthsTestAccess.tickTraps(d, g, List.of(p));
                    helper.assertTrue(DepthsTestAccess.wave(g, room.index()) == 2 && DepthsTestAccess.creaturesIn(d, g, room) > 0, "wave two" + where(g));
                    helper.assertTrue(g.depths().trapActive(room.index()), "still sealed in (" + total + " ward blocks)");
                    killAll(d, g, room);
                    DepthsTestAccess.tickTraps(d, g, List.of(p));
                    helper.assertTrue(g.depths().trapDone(room.index()), "the last wave down: the lockdown lets go");
                    for (DungeonLayout.Tunnel t : g.layout().tunnelsOf(room.index())) {
                        DepthsBuild.Mouth m = DepthsBuild.mouth(g, t, room);
                        int r = (int) Math.ceil(m.radius() + 3);
                        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(m.center()).offset(-r, -r, -r), BlockPos.containing(m.center()).offset(r, r, r))) {
                            helper.assertTrue(!d.getBlockState(q).is(DepthsModule.TRAP_WARD.get()), "its walls fall away");
                        }
                    }
                    release(d, held);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aStormBoltStrikesWhereItWasMarked(GameTestHelper helper) {
        GateInstance g = gate(helper, "storm", "none", "none");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        float before = zombie.getHealth();
        WeatherService.bolt(helper.getLevel(), g, zombie.position(), 2.2f, 6f);
        helper.assertTrue(zombie.getHealth() == before, "marked first, nothing yet");
        helper.runAfterDelay(WeatherService.BOLT_WARNING + 2, () -> {
            helper.assertTrue(zombie.getHealth() < before, "then down it comes: " + zombie.getHealth());
            helper.assertTrue(SkillCombat.aura(zombie) == SkillElement.LIGHTNING, "leaving the creature charged");
            helper.succeed();
        });
    }
}
