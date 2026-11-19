package com.beyondsolo.isle;

import com.beyondsolo.BeyondSolo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /isle}: answering the Association's call to Halcyon Isle, for everyone. {@code join} and {@code decline} answer
 * it (the chat buttons run them), {@code leave} withdraws an answer or walks off the island (out of the raid, unpaid),
 * and the bare command says how things stand. Calling a raid is an operator's: {@code /solo isle}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class IsleCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("isle")
                .executes(IsleCommand::status)
                .then(Commands.literal("status").executes(IsleCommand::status))
                .then(Commands.literal("join").executes(ctx -> run(ctx, IsleRaids.join(me(ctx)))))
                .then(Commands.literal("decline").executes(ctx -> run(ctx, IsleRaids.decline(me(ctx)))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, IsleRaids.leave(me(ctx))))));
    }

    private static ServerPlayer me(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** "[Halcyon] " in gold, before every line. */
    static MutableComponent tag() {
        return Component.literal("[").append(Component.translatable(IsleRaids.KEY + "tag")).append("] ").withStyle(ChatFormatting.GOLD);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, IsleRaids.Outcome out) {
        // always said (not command feedback, which a server can turn off)
        if (out.ok()) ctx.getSource().sendSystemMessage(tag().append(out.text().copy().withStyle(ChatFormatting.GRAY)));
        else ctx.getSource().sendFailure(out.text());
        return out.ok() ? 1 : 0;
    }

    /** How the raid stands (or when the Association calls next), and the hunter's own record. */
    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = me(ctx);
        for (Component line : lines(p)) ctx.getSource().sendSystemMessage(line);
        return 1;
    }

    static java.util.List<Component> lines(ServerPlayer p) {
        MinecraftServer server = p.server;
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid();
        long now = IsleRaids.now(server);
        String k = IsleRaids.KEY + "status.";
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (IsleWorld.level(server) == null) {
            out.add(tag().append(Component.translatable(IsleRaids.KEY + "refuse.no_world").withStyle(ChatFormatting.GRAY)));
            return out;
        }
        if (r == null) {
            Component when;
            if (!IsleConfig.enabled()) when = Component.translatable(k + "off");
            else if (data.nextCallAt() < 0) when = Component.translatable(k + "unplanned", IsleConfig.minRank().displayName());
            else when = Component.translatable(k + "next", minutes(data.nextCallAt() - now));
            out.add(tag().append(Component.translatable(k + "none").withStyle(ChatFormatting.GRAY)).append(" ").append(when.copy().withStyle(ChatFormatting.GRAY)));
        } else if (r.phase() == IsleRaid.Phase.MUSTER) {
            out.add(tag().append(Component.translatable(k + "muster", r.joined.size(), r.called.size(), IsleRaids.clock(r.musterEnds - now))
                    .withStyle(ChatFormatting.YELLOW)));
            int you = IsleRaids.you(r, p.getUUID());
            String yours = you == IsleRaids.YOU_JOINED ? "joined" : you == IsleRaids.YOU_DECLINED ? "declined" : you == IsleRaids.YOU_CALLED ? "called" : "uncalled";
            MutableComponent line = tag().append(Component.translatable(k + "you." + yours).withStyle(ChatFormatting.GRAY));
            if (you == IsleRaids.YOU_CALLED || you == IsleRaids.YOU_DECLINED || you == IsleRaids.YOU_NONE && IsleRaids.eligible(p, r.forced)) {
                line.append(" ").append(IsleRaids.button("join", ChatFormatting.GREEN, "/isle join"));
            }
            if (you == IsleRaids.YOU_CALLED) line.append(" ").append(IsleRaids.button("decline", ChatFormatting.RED, "/isle decline"));
            out.add(line);
        } else if (r.phase().landed()) {
            int fighting = 0;
            for (java.util.UUID id : r.joined) if (r.fighting(id)) fighting++;
            int allies = 0;
            for (IsleRaid.Ally a : r.allies) if (!a.fallen()) allies++;
            long left = Math.max(0, r.landedAt + IsleConfig.timeLimitTicks() - now);
            out.add(tag().append(Component.translatable(k + "landed", Component.translatable(IsleRaids.KEY + "phase." + r.phase().id()),
                    r.nestsBurned(), r.nests(), fighting, allies, IsleRaids.clock(left)).withStyle(ChatFormatting.YELLOW)));
            int you = IsleRaids.you(r, p.getUUID());
            if (you == IsleRaids.YOU_FIGHTING) out.add(tag().append(Component.translatable(k + "you.fighting").withStyle(ChatFormatting.GRAY)));
            else if (you == IsleRaids.YOU_FALLEN) out.add(tag().append(Component.translatable(k + "you.fallen").withStyle(ChatFormatting.GRAY)));
            else if (you == IsleRaids.YOU_LEFT) out.add(tag().append(Component.translatable(k + "you.left").withStyle(ChatFormatting.GRAY)));
        } else {
            long wait = (r.phase() == IsleRaid.Phase.CLEARED ? IsleRaids.AFTER_TICKS : IsleRaids.FAILED_TICKS) - (now - r.endedAt);
            out.add(tag().append(Component.translatable(k + r.phase().id(), IsleRaids.clock(wait)).withStyle(ChatFormatting.YELLOW)));
        }
        IsleRecord rec = IsleRaids.record(p);
        out.add(tag().append(Component.translatable(k + "record", rec.raids(), rec.cleared(), rec.nests(), rec.queens(), rec.kings(),
                rec.best() > 0 ? IsleRaids.clock(rec.best()) : "-").withStyle(ChatFormatting.DARK_GRAY)));
        return out;
    }

    /** Whole minutes, at least one. */
    static long minutes(long ticks) {
        return Math.max(1, (ticks + 1199) / 1200);
    }

    private IsleCommand() {}
}
