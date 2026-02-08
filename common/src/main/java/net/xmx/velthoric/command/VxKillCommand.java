/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.xmx.velthoric.command.argument.VxBodyArgument;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.VxRemovalReason;

import java.util.List;

/**
 * A command to remove physics bodies from the world.
 *
 * @author xI-Mx-Ix
 */
public class VxKillCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vxkill")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("selector", VxBodyArgument.instance())
                        .executes(context -> {
                            List<VxBody> bodiesToRemove = VxBodyArgument.getBodies(context, "selector");

                            for (VxBody body : bodiesToRemove) {
                                body.getPhysicsWorld().getBodyManager().removeBody(body.getPhysicsId(), VxRemovalReason.DISCARD);
                            }

                            context.getSource().sendSuccess(() -> Component.literal("Removed " + bodiesToRemove.size() + " physics bodies."), true);
                            return bodiesToRemove.size();
                        })
                )
        );
    }
}