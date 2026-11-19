package com.beyondsolo.command.solo;

import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationModule;
import com.beyondsolo.association.AssociationNpc;
import com.beyondsolo.association.AssociationPosts;
import com.beyondsolo.association.AssociationRecord;
import com.beyondsolo.association.Broker;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * The Hunter Association: /solo association open [tab] (the office's window where you stand), broker (the broker's),
 * post (an officer and a broker at the nearest Evaluation Zone crystal), merit add|set, exam start|pass|fail,
 * contract (the first contract on the board around the player), record, reset.
 */
final class SoloAssociation {
    static final List<String> TABS = List.of("contracts", "permits", "exam", "standing");

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("association")
                .then(Commands.literal("open")
                        .executes(ctx -> open(ctx, Association.TAB_CONTRACTS))
                        .then(Solo.word("tab", TABS::stream).executes(ctx -> {
                            String tab = Solo.lower(StringArgumentType.getString(ctx, "tab"));
                            int i = TABS.indexOf(tab);
                            if (i < 0) throw Solo.error("unknown_association_tab", tab, String.join(", ", TABS));
                            return open(ctx, i);
                        })))
                .then(Commands.literal("broker").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Solo.needEvaluated(p);
                    Broker.openHere(p);
                    return 1;
                }))
                .then(Commands.literal("post").executes(SoloAssociation::post))
                .then(Commands.literal("merit")
                        .then(Commands.literal("add").then(Solo.targets(Commands.argument("merit", IntegerArgumentType.integer(1, 1_000_000)), (ctx, p) -> {
                            Solo.needEvaluated(p);
                            Association.addMerit(p, IntegerArgumentType.getInteger(ctx, "merit"));
                            return merit(p);
                        })))
                        .then(Commands.literal("set").then(Solo.targets(Commands.argument("merit", IntegerArgumentType.integer(0, 1_000_000)), (ctx, p) -> {
                            Solo.needEvaluated(p);
                            Association.setMerit(p, IntegerArgumentType.getInteger(ctx, "merit"));
                            return merit(p);
                        }))))
                .then(Commands.literal("exam")
                        .then(Solo.target(Commands.literal("start"), (ctx, p) -> examStart(p)))
                        .then(Solo.target(Commands.literal("pass"), (ctx, p) -> {
                            Quest q = exam(p);
                            UUID gate = Association.gateOf(q);
                            QuestEngine.progress(p, Association.examKey(gate), 1);
                            return Solo.t("association.exam.passed", p.getDisplayName());
                        }))
                        .then(Solo.target(Commands.literal("fail"), (ctx, p) -> {
                            QuestEngine.fail(p, exam(p), QuestEvent.FailReason.TIME);
                            return Solo.t("association.exam.failed", p.getDisplayName());
                        })))
                .then(Solo.target(Commands.literal("contract"), (ctx, p) -> contract(p)))
                .then(Solo.target(Commands.literal("record"), (ctx, p) -> record(p)))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    Association.wipe(p);
                    p.removeData(AssociationModule.RECORD);
                    return Solo.t("association.reset", p.getDisplayName());
                })));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, int tab) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Solo.needEvaluated(p);
        Association.openHere(p, tab);
        return 1;
    }

    /** An officer and a broker at the nearest Evaluation Zone crystal (or two blocks ahead, without one). */
    private static int post(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!(p.level() instanceof ServerLevel level)) throw Solo.error("not_overworld");
        BlockPos crystal = Solo.nearestCrystal(p);
        int placed = 0;
        for (AssociationNpc.Role role : AssociationNpc.Role.values()) {
            AssociationNpc there = AssociationPosts.at(level, crystal, role);
            if (there != null) continue;
            if (AssociationPosts.spawn(level, crystal, role) == null) throw Solo.error("no_post_spot", crystal.toShortString());
            placed++;
        }
        int n = placed;
        ctx.getSource().sendSuccess(() -> Solo.t("association.posted", n, crystal.toShortString()), true);
        return 1;
    }

    private static Component merit(ServerPlayer p) {
        AssociationRecord r = Association.record(p);
        return Solo.t("association.merit", p.getDisplayName(), r.merit(), r.standing().displayName());
    }

    /** Starts the exam for the next rank at once: no fee, no wait, whatever the hunter's true rank. */
    private static Component examStart(ServerPlayer p) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        PlayerRank rank = Association.examRank(p);
        if (rank == null) throw Solo.error("association_top", p.getDisplayName());
        if (Association.examQuest(p) != null) throw Solo.error("exam_underway", p.getDisplayName());
        if (p.level().dimension() != Level.OVERWORLD) throw Solo.error("not_overworld");
        GateBlockEntity block = GateSpawner.trySpawnNear(p.serverLevel(), p, rank, false);
        GateInstance g = block == null || block.gateId() == null ? null : GateData.get(p.server).get(block.gateId());
        if (g == null) throw Solo.error("no_gate_spot");
        Association.beginExam(p, g, null);
        return Solo.t("association.exam.started", p.getDisplayName(), rank.displayName(), g.gatePos().toShortString());
    }

    private static Quest exam(ServerPlayer p) throws CommandSyntaxException {
        Quest q = Association.examQuest(p);
        if (q == null || Association.gateOf(q) == null) throw Solo.error("no_exam", p.getDisplayName());
        return q;
    }

    /** The first contract on the board around the player. */
    private static Component contract(ServerPlayer p) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        List<Association.Offer> board = Association.board(p, p.blockPosition());
        if (board.isEmpty()) throw Solo.error("no_contract", p.getDisplayName());
        Association.Session here = new Association.Session(0, p.level().dimension(), p.blockPosition(), Association.OFFICE);
        Association.Outcome o = Association.accept(p, here, board.get(0).gate().id());
        if (!o.ok()) throw new SimpleCommandExceptionType(o.text()).create();
        return o.text();
    }

    private static Component record(ServerPlayer p) {
        AssociationRecord r = Association.record(p);
        long day = QuestEngine.day(p);
        return Solo.t("association.record", p.getDisplayName(), r.merit(), r.standing().displayName(), r.contracts(), r.contractsFailed(),
                r.clears(), r.suppressions(), r.exams(), r.examsFailed(), r.bounties(), r.permitsUsed(day), Association.permitsLeft(p),
                Broker.tipsLeft(p));
    }

    private SoloAssociation() {}
}
