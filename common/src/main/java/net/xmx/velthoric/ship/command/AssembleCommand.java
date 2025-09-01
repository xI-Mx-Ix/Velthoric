package net.xmx.velthoric.ship.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.xmx.velthoric.ship.VxShipAssembler;

public class AssembleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("assemble")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(context -> execute(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "from"),
                                        BlockPosArgument.getLoadedBlockPos(context, "to")
                                ))
                        )
                )
        );
    }

    private static int execute(CommandSourceStack source, BlockPos from, BlockPos to) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BoundingBox sourceBox = BoundingBox.fromCorners(from, to);

        boolean success = VxShipAssembler.assemble(level, sourceBox);

        if (success) {
            source.sendSuccess(() -> Component.literal("Structure assembled into a physics object!"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to assemble structure."));
            return 0;
        }
    }
}