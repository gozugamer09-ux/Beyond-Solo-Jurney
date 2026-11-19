package com.beyondsolo.command.solo;

import com.beyondsolo.spawn.VillageGuide;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * The System's mark on the way to the first village ({@code spawn.VillageGuide}):
 * <pre>
 * /solo village find [players]   the nearest village with an Evaluation Zone is marked for them now
 * /solo village clear [players]  their mark is taken away (it comes back while they still look for a village)
 * /solo village info [player]    where their mark is, and how far
 * </pre>
 * The mark is for players on their way to an evaluation: the System, no rank yet.
 */
final class SoloVillage {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("village")
                .then(Solo.targets(Commands.literal("find"), (ctx, p) -> {
                    if (!VillageGuide.wantsMark(p)) throw Solo.error("not_on_the_way", p.getDisplayName());
                    ServerLevel overworld = p.server.overworld();
                    BlockPos from = p.level() == overworld ? p.blockPosition() : overworld.getSharedSpawnPos();
                    Optional<BlockPos> crystal = VillageGuide.findNow(overworld, from);
                    if (crystal.isEmpty()) throw Solo.error("no_zone_found", p.getDisplayName());
                    VillageGuide.point(p, crystal.get(), true);
                    return Solo.t("village.marked", p.getDisplayName(), crystal.get().toShortString(), blocks(from, crystal.get()));
                }))
                .then(Solo.targets(Commands.literal("clear"), (ctx, p) -> {
                    VillageGuide.clear(p);
                    return Solo.t("village.cleared", p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("info"), (ctx, p) -> {
                    GlobalPos mark = VillageGuide.mark(p);
                    if (mark != null) {
                        return Solo.t("village.info", p.getDisplayName(), mark.pos().toShortString(), mark.dimension().location().toString(),
                                blocks(p.blockPosition(), mark.pos()));
                    }
                    return Solo.t(VillageGuide.searching(p) ? "village.info.searching" : "village.info.none", p.getDisplayName());
                })));
    }

    private static int blocks(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Mth.floor(Math.sqrt(dx * dx + dz * dz) + 0.5);
    }

    private SoloVillage() {}
}
