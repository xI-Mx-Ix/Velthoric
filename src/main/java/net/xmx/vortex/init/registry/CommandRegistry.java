package net.xmx.vortex.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.vortex.command.*;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        SpawnClothCommand.register(dispatcher);
        SpawnRopeCommand.register(dispatcher);
        TestJointCommand.register(dispatcher);
        SpawnBoxCommand.register(dispatcher);
        SpawnHingePairCommand.register(dispatcher);
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
    }
}
