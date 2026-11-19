package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationConfig;
import com.beyondsolo.association.AssociationData;
import com.beyondsolo.association.AssociationModule;
import com.beyondsolo.association.AssociationNpc;
import com.beyondsolo.association.AssociationPosts;
import com.beyondsolo.association.AssociationRecord;
import com.beyondsolo.association.Broker;
import com.beyondsolo.association.Standing;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Hunter Association and the gate broker. Run with {@code ./gradlew runGameTestServer}. The posts never come by
 * themselves on the test server (its plots are Evaluation Zones): these tests put an officer at the plot's crystal by
 * hand, and raise gates in the plot with {@link GateService#spawnGate}. A gate's boss is never fought here: a clear is
 * {@link Association#gateCleared} called as the gate calls it.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssociationGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /** A hunter in the plot at that rank (null: not evaluated), with no gold and a clean association record. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, @Nullable PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.removeData(AssociationModule.RECORD);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        if (rank != null) SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank, BlockPos at) {
        GateBlockEntity block = GateService.spawnGate(helper.getLevel(), helper.absolutePos(at), rank, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(block.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        return g;
    }

    /** The office at the plot's crystal, as a desk opened by a command (no officer to walk away from). */
    private static Association.Session desk(GameTestHelper helper, int screen) {
        return new Association.Session(0, helper.getLevel().dimension(), helper.absolutePos(CRYSTAL), screen);
    }

    /** Puts the hunter's stat total at the threshold of {@code rank} (their true rank, never shown). */
    private static void strengthOf(ServerPlayer p, PlayerRank rank) {
        SystemData d = SystemAPI.get(p);
        int now = Reevaluation.statTotal(d);
        SystemAPI.setStat(p, Stat.STR, d.stat(Stat.STR) + Math.max(0, Reevaluation.threshold(rank) - now));
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    // ================================================================ contracts

    /** An open gate near the office is on its board; the contract on it pays its bounty and merit when the gate is cleared. */
    @GameTest(template = ARENA)
    public static void aContractPaysWhenItsGateIsCleared(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "ContractHunter", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        Association.Session desk = desk(helper, Association.OFFICE);
        helper.assertTrue(Association.board(p, desk.office()).stream().anyMatch(o -> o.gate().id().equals(g.id())), "the open gate is on the board");
        Association.Outcome took = Association.accept(p, desk, g.id());
        helper.assertTrue(took.ok(), "the contract is taken: " + took.text().getString());
        helper.assertTrue(Association.questOn(p, g.id(), Association.CONTRACT) != null, "the contract is in the quest log");
        helper.assertFalse(Association.accept(p, desk, g.id()).ok(), "the same contract can't be taken twice");
        long bounty = Association.bounty(PlayerRank.D, Association.CLEAR, Standing.REGISTERED);
        long before = Economy.gold(p);
        Association.gateCleared(helper.getLevel(), g, List.of(p));
        AssociationRecord r = Association.record(p);
        helper.assertTrue(Association.questOn(p, g.id(), Association.CONTRACT) == null, "the contract is done");
        helper.assertTrue(r.contracts() == 1 && r.clears() == 1, "a contract and a clear on the record");
        helper.assertTrue(Economy.gold(p) - before >= bounty, "the bounty is paid: " + (Economy.gold(p) - before) + " of " + bounty);
        helper.assertTrue(r.bounties() == bounty, "the bounty is on the record: " + r.bounties());
        long merit = AssociationConfig.clearMerit(PlayerRank.D) + Association.contractMerit(PlayerRank.D, Association.CLEAR);
        helper.assertTrue(r.merit() == merit, "merit for the clear and the contract: " + r.merit() + " of " + merit);
        helper.succeed();
    }

    /** A contract fails when its gate closes without the hunter; given back, it fails too. */
    @GameTest(template = ARENA)
    public static void aContractFailsWhenItsGateClosesWithoutTheHunter(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "LateHunter", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        GateInstance other = gate(helper, PlayerRank.E, new BlockPos(1, 2, 5));
        Association.Session desk = desk(helper, Association.OFFICE);
        helper.assertTrue(Association.accept(p, desk, g.id()).ok(), "the first contract is taken");
        helper.assertTrue(Association.accept(p, desk, other.id()).ok(), "the second contract is taken");
        g.setState(GateInstance.State.CLOSED);
        Association.check(p);
        helper.assertTrue(Association.questOn(p, g.id(), Association.CONTRACT) == null, "the contract on the closed gate is over");
        helper.assertTrue(Association.questOn(p, other.id(), Association.CONTRACT) != null, "the other contract still runs");
        helper.assertTrue(Association.drop(p, other.id()).ok(), "a contract can be given back");
        AssociationRecord r = Association.record(p);
        helper.assertTrue(r.contractsFailed() == 2 && r.contracts() == 0, "two failed, none done: " + r.contractsFailed());
        helper.succeed();
    }

    // ================================================================ permits

    /** A permit gate lets in only its holder; given back, it is anyone's again and back on the boards. */
    @GameTest(template = ARENA)
    public static void aPermitGateLetsInOnlyItsHolder(GameTestHelper helper) {
        ServerPlayer holder = hunter(helper, "PermitHolder", PlayerRank.C);
        ServerPlayer other = hunter(helper, "PermitOther", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        Quest q = Association.grantPermit(holder, g, null);
        helper.assertTrue(Association.refusal(holder, g) == null, "the holder may go in");
        helper.assertTrue("message.beyondsolo.association.permit_gate".equals(Association.refusal(other, g)), "another hunter is kept out");
        helper.assertFalse(Association.contractable(holder.server, g), "a held gate is on no board");
        helper.assertFalse(Association.accept(other, desk(helper, Association.OFFICE), g.id()).ok(), "no one takes a contract on it");
        helper.assertTrue(Association.drop(holder, g.id()).ok(), "the permit can be given back");
        helper.assertTrue(QuestEngine.data(holder).find(q.id()) == null || !QuestEngine.data(holder).active().contains(q), "the permit's quest is over");
        helper.assertTrue(AssociationData.get(holder.server).claim(g.id()) == null, "the claim is let go");
        helper.assertTrue(Association.refusal(other, g) == null, "anyone may go in again");
        helper.assertTrue(Association.contractable(holder.server, g), "it is back on the boards");
        helper.succeed();
    }

    // ================================================================ the rank exam

    /** The exam is only offered once the crystal would read a higher rank; passed, it reissues the license at that rank. */
    @GameTest(template = ARENA)
    public static void theExamCertifiesTheRankTheCrystalReads(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Examinee", PlayerRank.E);
        ServerPlayer onlooker = hunter(helper, "Onlooker", PlayerRank.E);
        helper.assertTrue("exam.no_change".equals(Association.examBlock(p)), "no exam while the crystal reads the rank held: " + Association.examBlock(p));
        strengthOf(p, PlayerRank.D);
        helper.assertTrue(Association.examBlock(p) == null, "the exam is open once strength outgrows the license: " + Association.examBlock(p));
        helper.assertTrue(Association.examRank(p) == PlayerRank.D, "the exam is for the next rank");
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        Association.beginExam(p, g, null);
        helper.assertTrue("exam.underway".equals(Association.examBlock(p)), "one exam at a time");
        helper.assertTrue(Association.refusal(p, g) == null, "the examinee may go in");
        helper.assertTrue("message.beyondsolo.association.exam_gate".equals(Association.refusal(onlooker, g)), "no one else may");
        Association.gateCleared(helper.getLevel(), g, List.of(p));
        helper.assertTrue(SystemAPI.get(p).rank() == PlayerRank.D, "the license is reissued at D: " + SystemAPI.get(p).rank());
        AssociationRecord r = Association.record(p);
        helper.assertTrue(r.exams() == 1 && Association.examQuest(p) == null, "the exam is passed and over");
        helper.assertTrue(r.merit() >= AssociationConfig.examMerit(PlayerRank.D), "the exam's merit is paid: " + r.merit());
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.association_certified"), "the first pass brings its title");
        helper.assertTrue(AssociationData.get(p.server).claim(g.id()) == null, "the exam gate is an ordinary gate again");
        helper.succeed();
    }

    /** Dying ends the exam: failed, the gate let go, and the next exam waits. */
    @GameTest(template = ARENA)
    public static void dyingFailsTheExam(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "FallenExaminee", PlayerRank.E);
        strengthOf(p, PlayerRank.D);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        Association.beginExam(p, g, null);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
        AssociationRecord r = Association.record(p);
        helper.assertTrue(Association.examQuest(p) == null && r.examsFailed() == 1, "the exam is failed");
        helper.assertTrue(SystemAPI.get(p).rank() == PlayerRank.E, "the license stays as it was");
        helper.assertTrue(AssociationData.get(p.server).claim(g.id()) == null, "the gate is let go");
        if (AssociationConfig.examRetryDays() > 0) helper.assertTrue("exam.retry".equals(Association.examBlock(p)), "the next exam waits");
        helper.succeed();
    }

    // ================================================================ standing

    /** Merit raises the standing (once, never back), each bringing its perk; the top one brings its title. */
    @GameTest(template = ARENA)
    public static void meritRaisesTheStanding(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "RisingHunter", PlayerRank.C);
        ServerPlayer fresh = hunter(helper, "FreshHunter", PlayerRank.C);
        AssociationRecord r = Association.record(p);
        helper.assertTrue(r.standing() == Standing.REGISTERED, "everyone starts registered");
        Association.addMerit(p, Standing.RECOGNIZED.merit());
        helper.assertTrue(r.standing() == Standing.RECOGNIZED, "recognized at its merit");
        helper.assertTrue(Standing.RECOGNIZED.contractSlots() == Standing.REGISTERED.contractSlots() + 1, "a contract more at once");
        Association.addMerit(p, Standing.NATIONAL.merit());
        helper.assertTrue(r.standing() == Standing.NATIONAL, "national at the top");
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.pride_of_the_association"), "the top standing brings its title");
        helper.assertTrue(Association.bounty(PlayerRank.C, Association.CLEAR, Standing.NATIONAL) > Association.bounty(PlayerRank.C, Association.CLEAR, Standing.REGISTERED),
                "bigger bounties");
        helper.assertTrue(Broker.tipPrice(p, PlayerRank.C) < Broker.tipPrice(fresh, PlayerRank.C), "the broker's goodwill");
        helper.succeed();
    }

    // ================================================================ the broker

    /** The broker sells the whereabouts of a gate the hunter hasn't heard of; reaching it ends the lead. */
    @GameTest(template = ARENA)
    public static void theBrokerSellsAGatesWhereabouts(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "LeadBuyer", PlayerRank.B);
        GateInstance g = gate(helper, PlayerRank.B, new BlockPos(5, 2, 5));
        Association.Session desk = desk(helper, Association.BROKER);
        helper.assertFalse(GateSpawner.knows(p, g), "the hunter hasn't heard of it");
        helper.assertTrue(Broker.unknown(p, desk.office(), PlayerRank.B) == g, "the broker knows of it");
        long price = Broker.tipPrice(p, PlayerRank.B);
        helper.assertFalse(Broker.tip(p, desk, PlayerRank.B).ok(), "no gold, no lead");
        Economy.add(p, price, Economy.Reason.COMMAND);
        Association.Outcome sold = Broker.tip(p, desk, PlayerRank.B);
        helper.assertTrue(sold.ok(), "the lead is sold: " + sold.text().getString());
        helper.assertTrue(Economy.gold(p) == 0, "it cost its price");
        helper.assertTrue(GateSpawner.knows(p, g), "now they know of it");
        helper.assertTrue(Broker.tipsLeft(p) == AssociationConfig.tipsPerDay() - 1, "one lead fewer today");
        helper.assertTrue(Association.questOn(p, g.id(), Association.TIP) != null, "the lead is in the quest log");
        // the hunter stands a few blocks from it: the lead is found
        Association.check(p);
        helper.assertTrue(Association.questOn(p, g.id(), Association.TIP) == null, "reaching the gate ends the lead");
        helper.succeed();
    }

    /** A gate the hunter knows of can be scouted once; the report tells what waits inside. */
    @GameTest(template = ARENA)
    public static void theBrokerScoutsAKnownGate(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "ScoutBuyer", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        Association.Session desk = desk(helper, Association.BROKER);
        helper.assertFalse(Broker.scout(p, desk, g.id()).ok(), "a gate they never heard of can't be scouted");
        GateSpawner.tell(p, g.id());
        helper.assertTrue(Broker.scoutable(p, desk.office()).contains(g), "a known gate is on the broker's list");
        Economy.add(p, Broker.scoutPrice(p, PlayerRank.C), Economy.Reason.COMMAND);
        helper.assertTrue(Broker.scout(p, desk, g.id()).ok(), "the report is bought");
        helper.assertTrue(Association.record(p).scouted(g.id()), "the report is kept");
        helper.assertFalse(Broker.scout(p, desk, g.id()).ok(), "it is bought once");
        CompoundTag report = Broker.report(g, helper.getLevel().getGameTime(), p.registryAccess());
        helper.assertTrue(!report.getString("boss").isEmpty() && !report.getString("realm").isEmpty(), "the report names the dungeon and its boss");
        helper.assertTrue(report.getInt("rooms") > 0, "and how deep it runs");
        helper.succeed();
    }

    // ================================================================ the post and the desk

    /** An officer stands beside the crystal, can't be hurt, and their desk does the office's business (not the broker's). */
    @GameTest(template = ARENA)
    public static void anOfficerKeepsTheDesk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertBlockPresent(ModBlocks.EVALUATION_CRYSTAL.get(), CRYSTAL);
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        AssociationNpc officer = AssociationPosts.spawn(level, crystal, AssociationNpc.Role.OFFICER);
        helper.assertTrue(officer != null, "an officer finds a spot by the crystal");
        BlockPos at = officer.blockPosition();
        helper.assertTrue(Math.max(Math.abs(at.getX() - crystal.getX()), Math.abs(at.getZ() - crystal.getZ())) == 2, "two blocks from the crystal: " + at);
        helper.assertTrue(AssociationPosts.at(level, crystal, AssociationNpc.Role.OFFICER) == officer, "the post knows its officer");
        float health = officer.getHealth();
        officer.hurt(level.damageSources().generic(), 50f);
        helper.assertTrue(officer.isAlive() && officer.getHealth() == health, "an officer can't be hurt");
        ServerPlayer p = hunter(helper, "DeskVisitor", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        Association.talk(p, officer);
        Association.Session s = Association.session(p);
        helper.assertTrue(s != null && s.entity() == officer.getId() && s.screen() == Association.OFFICE, "talking opens the officer's desk");
        CompoundTag tip = new CompoundTag();
        tip.putInt("rank", PlayerRank.C.ordinal());
        Economy.add(p, 1_000_000, Economy.Reason.COMMAND);
        Association.handle(p, new AssociationActionPayload(AssociationActionPayload.TIP, tip));
        helper.assertTrue(Broker.tipsLeft(p) == AssociationConfig.tipsPerDay(), "the officer sells no leads");
        CompoundTag take = new CompoundTag();
        take.putUUID("gate", g.id());
        Association.handle(p, new AssociationActionPayload(AssociationActionPayload.ACCEPT, take));
        helper.assertTrue(Association.questOn(p, g.id(), Association.CONTRACT) != null, "the desk takes a contract");
        Association.handle(p, new AssociationActionPayload(AssociationActionPayload.CLOSE, new CompoundTag()));
        helper.assertTrue(Association.session(p) == null, "closing the window leaves the desk");
        officer.discard();
        helper.succeed();
    }

    /** Two officers at one post: one of them goes. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void aPostKeepsOneOfficer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        AssociationNpc a = AssociationPosts.spawn(level, crystal, AssociationNpc.Role.OFFICER);
        AssociationNpc b = AssociationPosts.spawn(level, crystal, AssociationNpc.Role.OFFICER);
        helper.assertTrue(a != null && b != null, "both stood at the post");
        helper.succeedWhen(() -> {
            int left = (a.isAlive() ? 1 : 0) + (b.isAlive() ? 1 : 0);
            helper.assertTrue(left == 1, "one officer is left, not " + left);
            (a.isAlive() ? a : b).discard();
        });
    }

    // ================================================================ /solo association

    @GameTest(template = ARENA)
    public static void theAssociationCommandsWork(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "AssocOp", PlayerRank.C);
        helper.assertTrue(run(p, "solo association merit set " + Standing.TRUSTED.merit()) == 1, "merit set runs");
        helper.assertTrue(Association.record(p).standing() == Standing.TRUSTED, "the standing follows the merit");
        helper.assertTrue(run(p, "solo association record") == 1, "record runs");
        helper.assertTrue(run(p, "solo association open exam") == 1 && run(p, "solo association broker") == 1, "the windows open");
        helper.assertTrue(run(p, "solo association reset") == 1, "reset runs");
        helper.assertTrue(Association.record(p).merit() == 0, "reset clears the record");
        try {
            run(p, "solo association exam pass");
            helper.fail("passing an exam no one is sitting");
        } catch (CommandSyntaxException expected) {
            // no exam underway
        }
        helper.succeed();
    }
}
