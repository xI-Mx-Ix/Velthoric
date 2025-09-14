/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.assembly;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class VxAssembleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vxassemble")
                .requires(source -> source.hasPermission(2))

                .then(Commands.argument("from", BlockPosArgument.blockPos())

                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(VxAssembleCommand::execute)
                        )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {

        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        ServerLevel level = context.getSource().getLevel();

        boolean success = VxShipAssembler.assembleShip(level, from, to);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Ship assembly initiated!"), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to initiate ship assembly. See console for details."));
            return 0;
        }
    }
}