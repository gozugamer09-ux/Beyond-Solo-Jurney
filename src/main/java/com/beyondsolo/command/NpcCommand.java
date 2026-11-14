package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.npc.NpcQuestKind;
import com.beyondsolo.npc.NpcService;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTestAccess;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/** /system npc ... testing commands for villager requests (permission level 2). */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class NpcCommand {
    private static final SimpleCommandExceptionType NO_VILLAGER =
            new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.npc.no_villager"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.literal("open").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            NpcService.open(p, nearest(p));
                            return 1;
                        }))
                        .then(Commands.literal("reroll").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            NpcService.board(nearest(p), NpcService.today(p)).reroll();
                            return 1;
                        }))
                        .then(Commands.literal("spawn").then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream().map(ResourceLocation::getPath), b))
                                .executes(ctx -> spawn(ctx, 2))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5)).executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(Commands.literal("ready").executes(NpcCommand::ready))
                        .then(Commands.literal("give").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(NpcQuestKind.values()).map(k -> k.name().toLowerCase()), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    AbstractVillager v = nearest(p);
                                    NpcQuestKind kind = NpcQuestKind.valueOf(StringArgumentType.getString(ctx, "kind").toUpperCase());
                                    NpcService.give(p, v, com.beyondsolo.npc.NpcQuests.build(p, v,
                                            new com.beyondsolo.npc.NpcQuests.Plan(kind, p.getRandom().nextLong()), true));
                                    return 1;
                                })))
                        .then(Commands.literal("nest").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            for (Quest q : QuestEngine.data(p).active()) {
                                if (q.template().equals(NpcQuestKind.CLEAR.template()) && q.area() != null) {
                                    q.extra().putBoolean("spawned", true);
                                    NpcService.spawnNest((ServerLevel) p.level(), q.area().center(), q.rankTier(), q.extra().getInt("nest"));
                                }
                            }
                            return 1;
                        }))));
    }

    private static AbstractVillager nearest(ServerPlayer p) throws CommandSyntaxException {
        List<AbstractVillager> list = p.level().getEntitiesOfClass(AbstractVillager.class, p.getBoundingBox().inflate(12));
        return list.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(p))).orElseThrow(NO_VILLAGER::create);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocation.withDefaultNamespace(StringArgumentType.getString(ctx, "profession"));
        VillagerProfession prof = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id).orElse(VillagerProfession.FARMER);
        Villager v = EntityType.VILLAGER.create(p.serverLevel());
        if (v == null) return 0;
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        v.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0);
        v.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.COMMAND, null);
        v.setVillagerData(new VillagerData(VillagerType.PLAINS, prof, level));
        v.setVillagerXp(1);
        p.serverLevel().addFreshEntity(v);
        return 1;
    }

    /** Finishes the objectives of every villager request (giving the items a delivery needs). */
    private static int ready(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int n = 0;
        for (Quest q : List.copyOf(QuestEngine.data(p).active())) {
            if (q.category() != QuestCategory.NPC || q.status() != Quest.Status.ACTIVE) continue;
            for (QuestObjective o : q.objectives()) {
                if (o.type() != ObjectiveType.COLLECT || o.target().startsWith("#")) continue;
                ResourceLocation item = ResourceLocation.tryParse(o.target());
                if (item != null) p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(item), o.required()));
            }
            QuestTestAccess.addProgress(p, q, Integer.MAX_VALUE / 2);
            n++;
        }
        return n;
    }

    private NpcCommand() {}
}
