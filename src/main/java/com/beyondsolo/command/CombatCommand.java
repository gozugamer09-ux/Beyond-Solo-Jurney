package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Combat test commands, under /system (permission level 2):
 * <pre>
 * /system buildup &lt;targets&gt; &lt;fire|poison|crimson|blood&gt; &lt;amount&gt;
 * /system status &lt;targets&gt; &lt;deep_burn|venom|heavy_venom|crimson_brand&gt; [stacks]
 * </pre>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class CombatCommand {
    private static final SimpleCommandExceptionType BAD_VALUE =
            new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.bad_value"));
    private static final String[] ELEMENTS = {"fire", "poison", "crimson", "blood"};
    private static final String[] STATUSES = {"deep_burn", "venom", "heavy_venom", "crimson_brand"};

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("system")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("buildup").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("element", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ELEMENTS, b))
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(CombatCommand::buildup)))))
                .then(Commands.literal("status").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("status", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(STATUSES, b))
                                .executes(ctx -> status(ctx, 1))
                                .then(Commands.argument("stacks", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> status(ctx, IntegerArgumentType.getInteger(ctx, "stacks"))))))));
    }

    private static int buildup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DamageElement element = switch (StringArgumentType.getString(ctx, "element")) {
            case "fire" -> DamageElement.FIRE;
            case "poison" -> DamageElement.POISON;
            case "crimson" -> DamageElement.CRIMSON;
            case "blood" -> DamageElement.BLOOD;
            default -> throw BAD_VALUE.create();
        };
        float amount = FloatArgumentType.getFloat(ctx, "amount");
        int n = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            if (e instanceof LivingEntity living) {
                Statuses.apply(living, element, amount, ctx.getSource().getEntity());
                n++;
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.beyondsolo.buildup"), false);
        return n;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, int stacks) throws CommandSyntaxException {
        String status = StringArgumentType.getString(ctx, "status");
        int n = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            if (!(e instanceof LivingEntity living)) continue;
            Entity source = ctx.getSource().getEntity();
            switch (status) {
                case "deep_burn" -> Statuses.apply(living, DamageElement.FIRE, Statuses.BURN.threshold() * 2, source);
                case "venom" -> {
                    living.removeEffect(ModEffects.VENOM);
                    for (int i = 0; i < stacks; i++) Statuses.addVenomStack(living, source);
                }
                case "heavy_venom" -> Statuses.applyHeavyVenom(living, source);
                case "crimson_brand" -> living.addEffect(new MobEffectInstance(ModEffects.CRIMSON_BRAND, Statuses.BRAND_TICKS), source);
                default -> throw BAD_VALUE.create();
            }
            n++;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.beyondsolo.status"), false);
        return n;
    }

    private CombatCommand() {}
}
