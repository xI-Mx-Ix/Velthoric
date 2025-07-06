package net.xmx.xbullet.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.xbullet.command.SpawnClothCommand;
import net.xmx.xbullet.command.SpawnRopeCommand;
import net.xmx.xbullet.command.TestJointCommand;
import net.xmx.xbullet.command.xbullet.XBulletCommand;
import net.xmx.xbullet.debug.drawer.command.DebugRendererCommand;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        XBulletCommand.register(dispatcher);
        SpawnClothCommand.register(dispatcher);
        SpawnRopeCommand.register(dispatcher);
        TestJointCommand.register(dispatcher);
        DebugRendererCommand.register(dispatcher);
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
    }
}
