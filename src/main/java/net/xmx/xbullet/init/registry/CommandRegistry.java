package net.xmx.xbullet.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.xbullet.command.SpawnClothCommand;
import net.xmx.xbullet.command.xbullet.XBulletCommand;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        XBulletCommand.register(dispatcher);
        SpawnClothCommand.register(dispatcher);
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
    }
}
