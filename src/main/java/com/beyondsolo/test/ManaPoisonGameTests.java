package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.manapoison.ManaCrystals;
import com.beyondsolo.manapoison.ManaPoisonConfig;
import com.beyondsolo.manapoison.ManaPoisonModule;
import com.beyondsolo.manapoison.ManaPoisoning;
import com.beyondsolo.network.ManaPoisonPayload;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mana poisoning in A and S gates. The game test server has no gate dimension: a gate's cave is carved into the
 * overworld at its slot ({@code GateService.dungeon}), and a hunter is put in it by hand. Test players never tick and
 * are not in the server's player list, so the half-second beat is run with {@link ManaPoisoning#beat}, what their
 * screen is told is heard with {@link ManaPoisoning#listen}, and (being unhurtable) the bites and bursts are checked
 * by the poisoning they leave, not the health they take.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaPoisonGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final float EPS = 0.001f;

    private static ServerPlayer hunter(GameTestHelper helper, String name, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.removeAllEffects();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        ManaPoisoning.clear(p);
        return p;
    }

    /** A gate of that rank in the plot, its cave the original one of its rank (the same every run). */
    private static GateInstance gate(GameTestHelper helper, PlayerRank rank) {
        GateBlockEntity door = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), rank, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(door.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        g.setDungeon(GateRealm.classic(g.rank()), DungeonShape.CAVERNS);
        return g;
    }

    private static GateInstance built(GameTestHelper helper, PlayerRank rank) {
        GateInstance g = gate(helper, rank);
        GateService.buildNow(helper.getLevel().getServer(), g);
        return g;
    }

    private static ServerLevel dungeon(GameTestHelper helper) {
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(d != null, "the gate level exists");
        return d;
    }

    /** Puts the hunter on the floor of the cave's entrance, away from its crystals. */
    private static void inEntrance(GameTestHelper helper, ServerPlayer p, ServerLevel d, GateInstance g) {
        DungeonLayout.Room entrance = null;
        for (DungeonLayout.Room room : g.layout().rooms()) if (room.kind() == DungeonLayout.Kind.ENTRANCE) entrance = room;
        helper.assertTrue(entrance != null, "the cave has an entrance");
        BlockPos floor = DepthsBuild.floorCenter(d, g, entrance);
        p.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        helper.assertTrue(ManaPoisoning.nearestCrystal(p) == null, "no crystal by the entrance");
    }

    /** What the hunter's screen is told from now on. */
    private static List<ManaPoisonPayload> listen(ServerPlayer p) {
        List<ManaPoisonPayload> heard = new ArrayList<>();
        ManaPoisoning.listen(p.getUUID(), heard::add);
        return heard;
    }

    private static void beats(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) ManaPoisoning.beat(p);
    }

    private static int flags(List<ManaPoisonPayload> heard) {
        return heard.isEmpty() ? 0 : heard.get(heard.size() - 1).flags();
    }

    // ================================================================ the stages

    /** Its stages weaken the hunter's blows and slow them; only A and S gates are thick enough. */
    @GameTest(template = ARENA)
    public static void theStagesWeakenAndSlow(GameTestHelper helper) {
        helper.assertTrue(ManaPoisoning.stage(29.9f) == 0 && ManaPoisoning.stage(30) == 1 && ManaPoisoning.stage(60) == 2 && ManaPoisoning.stage(90) == 3, "the stages begin at 30, 60, 90");
        helper.assertTrue(ManaPoisoning.weakening(0) == 0 && ManaPoisoning.weakening(1) > 0 && ManaPoisoning.weakening(2) > ManaPoisoning.weakening(1)
                && ManaPoisoning.weakening(3) > ManaPoisoning.weakening(2), "each stage weakens more");
        ServerPlayer p = hunter(helper, "SlowedHunter", PlayerRank.C);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        ManaPoisoning.set(p, 40);
        helper.assertTrue(speed.getModifier(ManaPoisoning.SPEED) == null, "sickness does not slow");
        ManaPoisoning.set(p, 65);
        AttributeModifier slow = speed.getModifier(ManaPoisoning.SPEED);
        helper.assertTrue(slow != null && Math.abs(slow.amount() + ManaPoisoning.slowing(2)) < EPS, "poisoning slows");
        ManaPoisoning.set(p, 95);
        slow = speed.getModifier(ManaPoisoning.SPEED);
        helper.assertTrue(slow != null && Math.abs(slow.amount() + ManaPoisoning.slowing(3)) < EPS, "collapse slows more");
        ManaPoisoning.clear(p);
        helper.assertTrue(speed.getModifier(ManaPoisoning.SPEED) == null && ManaPoisoning.level(p) == 0, "gone when it is gone");

        GateInstance c = gate(helper, PlayerRank.C);
        helper.assertFalse(ManaPoisoning.dense(c), "a C gate's mana is not thick enough");
        helper.assertTrue(ManaPoisonConfig.minGateRank() == PlayerRank.A, "A and S gates poison");
        helper.succeed();
    }

    /**
     * In an A gate, a B hunter is poisoned at the rate for a rank below, an A hunter slowly, an S hunter not at all;
     * it thins once the boss is down.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void thickManaPoisonsHuntersBelowTheGate(GameTestHelper helper) {
        GateInstance g = built(helper, PlayerRank.A);
        ServerLevel d = dungeon(helper);
        ServerPlayer b = hunter(helper, "BRankInA", PlayerRank.B);
        inEntrance(helper, b, d, g);
        helper.assertTrue(Reevaluation.trueRank(b) == PlayerRank.B, "a B-rank hunter: " + Reevaluation.trueRank(b));
        helper.assertTrue(ManaPoisoning.denseGate(b) == g, "breathing the A gate's mana");
        double rate = ManaPoisonConfig.aboveRate() * (1 - ManaPoisoning.shield(b));
        helper.assertTrue(Math.abs(ManaPoisoning.rate(b, g) - rate) < EPS, "the rate for a rank below: " + ManaPoisoning.rate(b, g));
        List<ManaPoisonPayload> heard = listen(b);
        beats(b, 4);
        helper.assertTrue(Math.abs(ManaPoisoning.level(b) - rate * 2) < EPS, "two seconds of it: " + ManaPoisoning.level(b));
        helper.assertTrue((flags(heard) & ManaPoisoning.DENSE) != 0, "the screen knows the mana is thick");

        ServerPlayer a = hunter(helper, "ARankInA", PlayerRank.A);
        inEntrance(helper, a, d, g);
        double matched = ManaPoisonConfig.matchedRate() * (1 - ManaPoisoning.shield(a));
        helper.assertTrue(Math.abs(ManaPoisoning.rate(a, g) - matched) < EPS && matched < rate, "an A hunter feels it slowly: " + ManaPoisoning.rate(a, g));
        ServerPlayer s = hunter(helper, "SRankInA", PlayerRank.S);
        inEntrance(helper, s, d, g);
        beats(s, 4);
        helper.assertTrue(ManaPoisoning.rate(s, g) == 0 && ManaPoisoning.level(s) == 0, "an S hunter not at all");

        g.setBossDefeated(true);
        float before = ManaPoisoning.level(b);
        beats(b, 2);
        ManaPoisoning.listen(b.getUUID(), null);
        helper.assertTrue(ManaPoisoning.level(b) < before, "it thins once the boss is down: " + ManaPoisoning.level(b));
        helper.assertTrue((flags(heard) & ManaPoisoning.DENSE) == 0, "the mana is thin again");
        helper.succeed();
    }

    /**
     * Purifying crystals grow in an A gate (two, in different rooms) and draw the poisoning out of a hunter resting by
     * one; {@code /solo mana crystals} finds them.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void crystalsGrowAndDrawItOut(GameTestHelper helper) {
        GateInstance g = built(helper, PlayerRank.A);
        ServerLevel d = dungeon(helper);
        List<BlockPos> crystals = ManaCrystals.spots(d, g);
        helper.assertTrue(crystals.size() == ManaPoisonConfig.crystals(PlayerRank.A), "two crystals: " + crystals.size());
        Set<Integer> rooms = new HashSet<>();
        for (DungeonLayout.Room room : ManaCrystals.rooms(g)) {
            helper.assertTrue(room.kind() != DungeonLayout.Kind.ENTRANCE && room.kind() != DungeonLayout.Kind.BOSS, "never the entrance or the boss's hall");
            rooms.add(room.index());
        }
        helper.assertTrue(rooms.size() == crystals.size(), "each in a room of its own");
        for (BlockPos at : crystals) helper.assertTrue(d.getBlockState(at).is(ManaPoisonModule.CRYSTAL.get()), "a crystal stands at " + at.toShortString());

        ServerPlayer p = hunter(helper, "RestingHunter", PlayerRank.B);
        BlockPos at = crystals.get(0);
        p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(at.equals(ManaPoisoning.nearestCrystal(p)), "resting by it");
        ManaPoisoning.set(p, 40);
        List<ManaPoisonPayload> heard = listen(p);
        ManaPoisoning.beat(p);
        ManaPoisoning.listen(p.getUUID(), null);
        float want = 40 - (float) ManaPoisonConfig.crystalPurify() * 0.5f;
        helper.assertTrue(Math.abs(ManaPoisoning.level(p) - want) < EPS, "the crystal draws it out, even in thick mana: " + ManaPoisoning.level(p));
        helper.assertTrue((flags(heard) & ManaPoisoning.PURIFYING) != 0, "the screen shows it being drawn out");
        try {
            helper.assertTrue(run(p, "solo mana crystals") > 0, "/solo mana crystals lists them");
        } catch (CommandSyntaxException e) {
            helper.fail("/solo mana crystals: " + e.getMessage());
        }
        helper.succeed();
    }

    /** At 100 the mana bursts out of the hunter and the poisoning falls back to 75. */
    @GameTest(template = ARENA)
    public static void aFullHunterBursts(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "BurstHunter", PlayerRank.C);
        List<ManaPoisonPayload> heard = listen(p);
        ManaPoisoning.set(p, 95);
        helper.assertTrue((flags(heard) & ManaPoisoning.BURST) == 0, "not yet");
        ManaPoisoning.add(p, 10);
        ManaPoisoning.listen(p.getUUID(), null);
        helper.assertTrue(ManaPoisoning.level(p) == ManaPoisoning.AFTER_BURST, "it falls back: " + ManaPoisoning.level(p));
        helper.assertTrue((flags(heard) & ManaPoisoning.BURST) != 0, "the screen plays the burst");
        helper.succeed();
    }

    /**
     * A poisoned hunter's blows lose their share: a fifth while poisoned. The same creature takes every blow, since
     * what a creature is (its traits, its element, the weather where it stands) bends a blow too.
     */
    @GameTest(template = ARENA)
    public static void poisonedBlowsLoseTheirShare(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "WeakHunter", PlayerRank.C);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        // no lucky blows: Apothic's random critical hits (and dodges) would bend the numbers compared below
        var crit = p.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        var dodge = z.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) dodge.setBaseValue(0);
        float clean = hit(z, p);
        helper.assertTrue(clean > 0 && Math.abs(hit(z, p) - clean) < clean * 0.001f, "a clean blow lands the same twice: " + clean);
        ManaPoisoning.set(p, 65);
        float poisoned = hit(z, p);
        ManaPoisoning.clear(p);
        z.discard();
        float want = clean * (1 - ManaPoisoning.weakening(2));
        helper.assertTrue(Math.abs(poisoned - want) < want * 0.02f, "a fifth less: " + poisoned + " of " + clean);
        helper.succeed();
    }

    /** What one 6-point blow from the hunter takes off the creature. */
    private static float hit(Zombie z, ServerPlayer p) {
        z.setHealth(1000);
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 6f);
        return 1000 - z.getHealth();
    }

    /** A purifying draught washes half of it out and keeps the mana out for a while, even in thick mana. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aDraughtWashesItOutAndKeepsItOut(GameTestHelper helper) {
        GateInstance g = built(helper, PlayerRank.A);
        ServerLevel d = dungeon(helper);
        ServerPlayer p = hunter(helper, "DrinkingHunter", PlayerRank.B);
        inEntrance(helper, p, d, g);
        ManaPoisoning.set(p, 70);
        ItemStack draught = new ItemStack(ManaPoisonModule.DRAUGHT.get(), 2);
        draught.finishUsingItem(d, p);
        helper.assertTrue(draught.getCount() == 1, "one draught drunk");
        helper.assertTrue(Math.abs(ManaPoisoning.level(p) - (70 - ManaPoisonConfig.draught())) < EPS, "half washed out: " + ManaPoisoning.level(p));
        helper.assertTrue(p.hasEffect(ManaPoisonModule.CLEAR_BREATH), "Clear Breath");
        List<ManaPoisonPayload> heard = listen(p);
        float before = ManaPoisoning.level(p);
        beats(p, 2);
        ManaPoisoning.listen(p.getUUID(), null);
        helper.assertTrue(ManaPoisoning.level(p) < before, "the mana stays out and the rest fades: " + ManaPoisoning.level(p));
        helper.assertTrue((flags(heard) & ManaPoisoning.CLEAR) != 0, "the screen shows Clear Breath");
        helper.succeed();
    }

    /** /solo mana sets, adds and clears it, and grows a crystal where the player looks. */
    @GameTest(template = ARENA)
    public static void soloManaCommands(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "ManaOperator", PlayerRank.C);
        run(p, "solo mana set 40");
        helper.assertTrue(Math.abs(ManaPoisoning.level(p) - 40) < EPS, "set to 40");
        run(p, "solo mana add 5");
        helper.assertTrue(Math.abs(ManaPoisoning.level(p) - 45) < EPS, "45 after adding 5");
        helper.assertTrue(run(p, "solo mana info") > 0, "info answers");
        run(p, "solo mana clear");
        helper.assertTrue(ManaPoisoning.level(p) == 0, "cleared");
        run(p, "solo mana crystal");
        BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
        helper.assertTrue(helper.getLevel().getBlockState(at).is(ManaPoisonModule.CRYSTAL.get()), "a crystal two steps ahead");
        helper.succeed();
    }

    /** Runs a command as the player with an operator's permission. */
    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }
}
