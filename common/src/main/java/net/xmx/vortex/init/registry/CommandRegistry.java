package net.xmx.vortex.init.registry;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.commands.CommandSourceStack;
import net.xmx.vortex.command.*;

public class CommandRegistry {

    public static void registerCommon(CommandDispatcher<CommandSourceStack> dispatcher) {
        SpawnClothCommand.register(dispatcher);
        SpawnRopeCommand.register(dispatcher);
        SpawnBoxCommand.register(dispatcher);
        SpawnHingePairCommand.register(dispatcher);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CommandRegistry.registerCommon(dispatcher);
            CommandRegistry.registerClient(dispatcher);
        });
    }
}