/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.timtaran.interactivemc.physics.command.argument.VxBodyArgument;
import net.timtaran.interactivemc.physics.physics.body.type.VxBody;
import net.timtaran.interactivemc.physics.physics.body.manager.VxRemovalReason;

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