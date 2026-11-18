package com.beyondsolo.raid;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /raid}: the briefing's buttons in chat. Join the muster at a raid gate, pick a role, leave; the leader starts
 * it and decides whether their guild's hunters come along. No permission needed.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RaidCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("raid")
                .executes(RaidCommand::status)
                .then(Commands.literal("status").executes(RaidCommand::status))
                .then(Commands.literal("join").executes(ctx -> {
                    ServerPlayer me = me(ctx);
                    GateInstance g = Raids.nearest(me);
                    int done = run(ctx, g == null ? Raids.Outcome.no("none_near") : Raids.join(me, g));
                    // as walking into the gate does: the briefing, to pick a role in
                    if (done > 0) Raids.brief(me);
                    return done;
                }))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, Raids.leave(me(ctx)))))
                .then(Commands.literal("role").then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RaidRole.values()).map(RaidRole::id), b))
                        .executes(ctx -> {
                            RaidRole role = RaidRole.byId(StringArgumentType.getString(ctx, "role"));
                            return run(ctx, role == null ? Raids.Outcome.no("role.unknown") : Raids.role(me(ctx), role));
                        })))
                .then(Commands.literal("start").executes(ctx -> run(ctx, Raids.start(me(ctx)))))
                .then(Commands.literal("hunters")
                        .then(Commands.literal("on").executes(ctx -> run(ctx, Raids.bring(me(ctx), true))))
                        .then(Commands.literal("off").executes(ctx -> run(ctx, Raids.bring(me(ctx), false)))))
                .then(Commands.literal("brief").executes(ctx -> {
                    ServerPlayer me = me(ctx);
                    if (Raids.raidOf(me.server, me.getUUID()) == null) return run(ctx, Raids.Outcome.no("not_in"));
                    Raids.brief(me);
                    return 1;
                })));
    }

    private static ServerPlayer me(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** "[Raid] " in front of a chat line. */
    public static MutableComponent tag() {
        return Component.translatable(Raids.KEY + "tag").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Raids.BARRIER))).append(" ");
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Raids.Outcome out) {
        if (out.ok()) ctx.getSource().sendSystemMessage(tag().append(out.message().copy().withStyle(ChatFormatting.GRAY)));
        else ctx.getSource().sendFailure(out.message());
        return out.ok() ? 1 : 0;
    }

    static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(Raids.KEY + "button." + key)).append("]")
                .withStyle(Style.EMPTY.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    /** The raid in chat: the gate, the phase, who leads and who is on it, with buttons for what can be done now. */
    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = me(ctx);
        GateInstance g = Raids.raidOf(me.server, me.getUUID());
        if (g == null) {
            GateInstance near = Raids.nearest(me);
            if (near == null) {
                ctx.getSource().sendSystemMessage(tag().append(Component.translatable(Raids.KEY + "status.none").withStyle(ChatFormatting.GRAY)));
            } else {
                ctx.getSource().sendSystemMessage(tag().append(Component.translatable(Raids.KEY + "status.near", near.rank().displayName())
                        .withStyle(ChatFormatting.GRAY)).append(" ").append(button("join", ChatFormatting.GREEN, "/raid join")));
            }
            return 1;
        }
        CompoundTag t = Raids.syncTag(me.server, com.beyondsolo.gate.GateService.dungeon(me.server), g, me.getUUID());
        RaidState s = g.raid();
        ctx.getSource().sendSystemMessage(tag().append(Component.translatable(Raids.KEY + "status.head", g.rank().displayName(), g.dungeonName(),
                Component.translatable(Raids.KEY + "phase." + s.phase.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendSystemMessage(Component.literal("  ").append(Component.translatable(Raids.KEY + "status.leader", s.leaderName())
                .withStyle(ChatFormatting.GOLD)));
        ListTag raiders = t.getList("raiders", Tag.TAG_COMPOUND);
        for (int i = 0; i < raiders.size(); i++) {
            CompoundTag r = raiders.getCompound(i);
            RaidRole role = RaidRole.byId(r.getString("role"));
            MutableComponent line = Component.literal("    ").append(Component.literal(r.getString("name"))
                    .withStyle(r.getBoolean("online") ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            if (role != null) line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(role.displayName().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(role.color & 0xFFFFFF))));
            if (r.getInt("cls") >= 0 && r.getInt("cls") < PlayerClass.values().length) {
                line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable(Raids.KEY + "status.hunter",
                        PlayerClass.values()[r.getInt("cls")].displayName(), r.getInt("level")).withStyle(ChatFormatting.GRAY));
            }
            String status = r.getString("status");
            if (!status.equals("WAITING") && !status.equals("INSIDE")) {
                line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable(Raids.KEY + "status." + status.toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.RED));
            }
            ctx.getSource().sendSystemMessage(line);
        }
        ListTag hunters = t.getList("hunters", Tag.TAG_COMPOUND);
        if (!hunters.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("  ").append(Component.translatable(Raids.KEY + "status.hunters", hunters.size())
                    .withStyle(ChatFormatting.WHITE)));
        }
        if (s.phase == RaidState.Phase.RUNNING) {
            ctx.getSource().sendSystemMessage(Component.literal("  ").append(Component.translatable(Raids.KEY + "status.inside",
                    t.getInt("lieutenantsLeft"), t.getInt("lieutenants"), Math.round(t.getFloat("bossHp") * 100)).withStyle(ChatFormatting.GRAY)));
        }
        MutableComponent foot = Component.literal("  ");
        if (s.phase == RaidState.Phase.MUSTER || s.phase == RaidState.Phase.COUNTDOWN) {
            foot.append(button("brief", ChatFormatting.AQUA, "/raid brief")).append(" ");
            if (me.getUUID().equals(s.leader) && s.phase == RaidState.Phase.MUSTER) foot.append(button("start", ChatFormatting.GOLD, "/raid start")).append(" ");
            foot.append(button("leave", ChatFormatting.RED, "/raid leave"));
            ctx.getSource().sendSystemMessage(foot);
        }
        return 1;
    }

    /** The ranks in lower case, for suggestions. */
    static List<String> ranks() {
        return Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)).toList();
    }

    private RaidCommand() {}
}
