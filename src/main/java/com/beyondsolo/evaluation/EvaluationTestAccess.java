package com.beyondsolo.evaluation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Lets game tests (and other modules) start an evaluation without clicking the block. */
public final class EvaluationTestAccess {
    public static void interact(ServerPlayer player, BlockPos crystal) {
        EvaluationManager.interact(player, crystal);
    }

    private EvaluationTestAccess() {}
}
