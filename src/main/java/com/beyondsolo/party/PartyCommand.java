package com.beyondsolo.party;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /party}: everything the PARTY page does, for everyone (hunters the System has not bound to yet have no
 * System window, so this and the buttons in chat are how they team up). No permission needed.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class PartyCommand {
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        Party party = p == null ? null : Parties.of(p);
        List<String> names = new ArrayList<>();
        if (party != null) for (UUID m : party.members()) if (!m.equals(p.getUUID())) names.add(party.name(m));
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> INVITERS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? List.of() : Parties.inviterNames(p), b);
    };

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("party")
                .executes(PartyCommand::list)
                .then(Commands.literal("list").executes(PartyCommand::list))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, Parties.invite(me(ctx), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept")
                        .executes(ctx -> run(ctx, Parties.accept(me(ctx), null)))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(INVITERS)
                                .executes(ctx -> run(ctx, Parties.accept(me(ctx), inviter(ctx))))))
                .then(Commands.literal("decline")
                        .executes(ctx -> run(ctx, Parties.decline(me(ctx), null)))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(INVITERS)
                                .executes(ctx -> run(ctx, Parties.decline(me(ctx), inviter(ctx))))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, Parties.leave(me(ctx)))))
                .then(Commands.literal("kick").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Parties.kick(me(ctx), member(ctx))))))
                .then(Commands.literal("leader").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Parties.promote(me(ctx), member(ctx))))))
                .then(Commands.literal("disband").executes(ctx -> run(ctx, Parties.disband(me(ctx)))))
                .then(Commands.literal("regroup").then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                        .executes(ctx -> run(ctx, Regroup.start(me(ctx), member(ctx)))))));
    }

    private static ServerPlayer me(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Parties.Outcome out) {
        if (out.ok()) ctx.getSource().sendSuccess(() -> Parties.tag().append(out.message().copy().withStyle(ChatFormatting.GRAY)), false);
        else ctx.getSource().sendFailure(out.message());
        return out.ok() ? 1 : 0;
    }

    /** The party member with the name typed (any case), or an id that is nobody's. */
    private static UUID member(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Party party = Parties.of(me(ctx));
        if (party != null) for (UUID m : party.members()) if (party.name(m).equalsIgnoreCase(name)) return m;
        return new UUID(-1, -1);
    }

    /** Who sent the invitation by the name typed, or an id that is nobody's. */
    private static UUID inviter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID id = Parties.inviterNamed(me(ctx), StringArgumentType.getString(ctx, "name"));
        return id == null ? new UUID(-1, -1) : id;
    }

    /** The party in chat: who is in it, how they are and where, with buttons for what can be done from here. */
    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = me(ctx);
        Party party = Parties.of(me);
        List<Component> lines = new ArrayList<>();
        if (party == null) {
            lines.add(Parties.tag().append(Component.translatable("party.beyondsolo.list.none").withStyle(ChatFormatting.GRAY)));
        } else {
            lines.add(Parties.tag().append(Component.translatable("party.beyondsolo.list.head", party.size(), PartyConfig.MAX_SIZE.get())
                    .withStyle(ChatFormatting.WHITE)));
            for (PartySyncPayload.Member m : Parties.members(me.server, party)) lines.add(memberLine(me, party, m));
            MutableComponent foot = Component.literal("  ").append(Parties.button("leave", ChatFormatting.RED, "/party leave"));
            if (party.isLeader(me.getUUID())) foot.append(" ").append(Parties.button("disband", ChatFormatting.DARK_RED, "/party disband"));
            lines.add(foot);
        }
        for (String from : Parties.inviterNames(me)) {
            lines.add(Component.literal("  ").append(Component.translatable("party.beyondsolo.list.invite", Component.literal(from).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY))
                    .append(" ").append(Parties.button("accept", ChatFormatting.GREEN, "/party accept " + from))
                    .append(" ").append(Parties.button("decline", ChatFormatting.RED, "/party decline " + from)));
        }
        for (Component c : lines) ctx.getSource().sendSystemMessage(c);
        return 1;
    }

    private static Component memberLine(ServerPlayer me, Party party, PartySyncPayload.Member m) {
        boolean leader = party.isLeader(m.id());
        MutableComponent line = Component.literal(leader ? "  ★ " : "    ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(m.name()).withStyle(m.online() ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        if (!m.online()) return line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("ui.beyondsolo.party.place.offline").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY));
        if (m.rank() >= 0 && m.cls() >= 0) {
            line.append(Component.translatable("party.beyondsolo.list.hunter", PlayerRank.values()[m.rank()].displayName(),
                    PlayerClass.values()[m.cls()].displayName(), m.level()).withStyle(ChatFormatting.GRAY));
        } else {
            line.append(Component.translatable(m.system() ? "party.beyondsolo.list.unevaluated" : "party.beyondsolo.list.no_system")
                    .withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.0f/%.0f ♥", Math.ceil(m.health()), m.maxHealth())).withStyle(ChatFormatting.RED))
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(where(me, m));
        if (!m.id().equals(me.getUUID())) {
            ServerPlayer them = OnlinePlayers.get(me.server, m.id());
            if (them != null && Regroup.refusal(me, m.id(), them, true) == null) {
                line.append(" ").append(Parties.button("regroup", ChatFormatting.LIGHT_PURPLE, "/party regroup " + m.name()));
            }
        }
        return line;
    }

    /** "in a D-rank gate", "here", "340 blocks north-east", "in the Nether". */
    private static Component where(ServerPlayer me, PartySyncPayload.Member m) {
        if (m.place() != PartySyncPayload.WORLD) {
            Component place = Component.translatable("ui.beyondsolo.party.place." + Regroup.placeKey(m.place()));
            if (m.place() == PartySyncPayload.GATE && m.placeRank() >= 0) {
                place = Component.translatable("ui.beyondsolo.party.place.gate_rank", PlayerRank.values()[m.placeRank()].displayName());
            }
            return place.copy().withStyle(ChatFormatting.YELLOW);
        }
        if (m.id().equals(me.getUUID())) return Component.translatable("party.beyondsolo.list.you").withStyle(ChatFormatting.DARK_GRAY);
        if (!me.level().dimension().location().equals(m.dim())) {
            return Component.translatable("ui.beyondsolo.party.dim", dimName(m.dim().toString())).withStyle(ChatFormatting.YELLOW);
        }
        double dx = m.x() - me.getX(), dz = m.z() - me.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (dist < 16) return Component.translatable("party.beyondsolo.list.near").withStyle(ChatFormatting.GREEN);
        return Component.translatable("party.beyondsolo.list.away", dist, Component.translatable("npc.beyondsolo.dir." + compass(dx, dz)))
                .withStyle(ChatFormatting.YELLOW);
    }

    /** n, ne, e, se, s, sw, w, nw: which way a point lies (north is -z). */
    public static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(a / 45.0), 8);
        return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[i];
    }

    /** The name of a dimension: the three we know by name, else its id. */
    static Component dimName(@Nullable String id) {
        return switch (id == null ? "" : id) {
            case "minecraft:overworld" -> Component.translatable("ui.beyondsolo.party.dim.overworld");
            case "minecraft:the_nether" -> Component.translatable("ui.beyondsolo.party.dim.nether");
            case "minecraft:the_end" -> Component.translatable("ui.beyondsolo.party.dim.end");
            default -> Component.literal(id == null ? "?" : id);
        };
    }

    private PartyCommand() {}
}
