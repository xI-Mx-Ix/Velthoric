/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.xmx.velthoric.command.argument.VxObjectArgument;
import net.xmx.velthoric.physics.object.VxBody;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;

import java.util.List;

public class VxKillCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vxkill")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("selector", VxObjectArgument.instance())
                        .executes(context -> {
                            List<VxBody> objectsToRemove = VxObjectArgument.getObjects(context, "selector");

                            for (VxBody obj : objectsToRemove) {
                                obj.getWorld().getObjectManager().removeObject(obj.getPhysicsId(), VxRemovalReason.DISCARD);
                            }

                            context.getSource().sendSuccess(() -> Component.literal("Removed " + objectsToRemove.size() + " physics objects."), true);
                            return objectsToRemove.size();
                        })
                )
        );
    }
}