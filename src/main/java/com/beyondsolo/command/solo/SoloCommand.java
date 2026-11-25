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
 * /solo village ...                                              the mark on the way to the first village (SoloVillage)
 * /solo give ...                                                 items                  (SoloGive)
 * /solo guild|raid ...                                           guilds and raids       (SoloGuilds)
 * /solo monarch ...                                              monarchs               (SoloMonarch)
 * /solo ruler ...                                                rulers of light        (SoloRulers)
 * /solo light ...                                                a hunter sworn to the light (SoloLight)
 * /solo association ...                                          the Hunter Association (SoloAssociation)
 * /solo rival|hire ...                                           rival parties, hands for hire (SoloRivals)
 * /solo scout|contract|news ...                                  the great guilds' scouts, contracts, the hunter news (SoloScouting)
 * /solo box|mana ...                                             random boxes, mana poisoning (SoloBoxes)
 * /solo story ...                                               the Long Sleep         (SoloStory)
 * /solo isle ...                                                 the Halcyon raid       (SoloIsle)
 * /solo home ...                                                 hunters' homes         (SoloHome)
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
        SoloVillage.register(root);
        SoloGive.register(root);
        SoloGuilds.register(root);
        SoloMonarch.register(root);
        SoloRulers.register(root);
        SoloLight.register(root);
        SoloAssociation.register(root);
        SoloRivals.register(root);
        SoloScouting.register(root);
        SoloBoxes.register(root);
        SoloStory.register(root);
        SoloIsle.register(root);
        SoloHome.register(root);
        CommandNode<CommandSourceStack> system = dispatcher.getRoot().getChild("system");
        if (system != null) root.then(Commands.literal("dev").redirect(system));
        dispatcher.register(root);
    }

    private SoloCommand() {}
}
