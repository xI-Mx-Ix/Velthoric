package net.xmx.xbullet.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.xbullet.command.*;
import net.xmx.xbullet.command.xbullet.XBulletCommand;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        XBulletCommand.register(dispatcher);
        SpawnClothCommand.register(dispatcher);
        SpawnRopeCommand.register(dispatcher);
        TestJointCommand.register(dispatcher);
        SpawnBoxCommand.register(dispatcher);
        SpawnHingePairCommand.register(dispatcher);
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
    }
}
