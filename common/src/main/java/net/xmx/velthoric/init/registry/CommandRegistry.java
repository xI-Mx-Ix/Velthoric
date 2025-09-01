package net.xmx.velthoric.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.velthoric.command.*;
import net.xmx.velthoric.ship.command.AssembleCommand;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        SpawnClothCommand.register(dispatcher);
        SpawnRopeCommand.register(dispatcher);
        SpawnBoxCommand.register(dispatcher);
        SpawnConnectedBoxesCommand.register(dispatcher);
        CreateRopeCommand.register(dispatcher);
        AssembleCommand.register(dispatcher);
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (Platform.getEnvironment() == Env.CLIENT) {
        }
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CommandRegistry.registerCommon(dispatcher);
            CommandRegistry.registerClient(dispatcher);
        });
    }
}