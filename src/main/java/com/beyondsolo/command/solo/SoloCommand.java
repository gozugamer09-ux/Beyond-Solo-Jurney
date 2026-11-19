package com.beyondsolo.command.solo;

import com.beyondsolo.BeyondSolo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /solo: every command the mod gives an operator (permission level 2, the same as /give), with tab completion.
 * Players at the end of a command can be left out (the sender) or named: a name or a selector like {@code @a}.
 *
 * <pre>
 * /solo                         the help: the groups below, each clickable
 * /solo info|awaken|evaluate|bind|set|add|heal|reset|wipe ...    the player's System   (SoloProgress, SoloWipe)
 * /solo skill|quest|shadow ...                                   what they know and do  (SoloSkills)
 * /solo spawn|gate|dungeon|trial|tower ...                        the world             (SoloWorld)
 * /solo give ...                                                 items                  (SoloGive)
 * /solo guild|raid ...                                           guilds and raids       (SoloGuilds)
 * /solo monarch ...                                              monarchs               (SoloMonarch)
 * /solo ruler ...                                                rulers of light        (SoloRulers)
 * /solo association ...                                          the Hunter Association (SoloAssociation)
 * /solo box|mana ...                                             random boxes, mana poisoning (SoloBoxes)
 * /solo dev ...                                                  the older /system testing commands
 * </pre>
 *
 * <p>Registered last ({@link EventPriority#LOWEST}) so that {@code /solo dev} can point at the whole /system tree
 * every module has added to by then.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SoloCommand {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("solo").requires(src -> src.hasPermission(2));
        SoloHelp.register(root);
        SoloProgress.register(root);
        SoloWipe.register(root);
        SoloSkills.register(root);
        SoloWorld.register(root);
        SoloGive.register(root);
        SoloGuilds.register(root);
        SoloMonarch.register(root);
        SoloRulers.register(root);
        SoloAssociation.register(root);
        SoloBoxes.register(root);
        SoloStory.register(root);
        CommandNode<CommandSourceStack> system = dispatcher.getRoot().getChild("system");
        if (system != null) root.then(Commands.literal("dev").redirect(system));
        dispatcher.register(root);
    }

    private SoloCommand() {}
}
