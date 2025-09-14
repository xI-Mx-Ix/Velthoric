/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.xmx.velthoric.command.test.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VxTestCommand {

    private static final Map<String, IVxTestCommand> TESTS = new ConcurrentHashMap<>();

    static {
        registerTest(new SpawnBoxTest());
        registerTest(new SpawnBoxGridTest());
        registerTest(new SpawnClothTest());
        registerTest(new SpawnRopeTest());
        registerTest(new CreateChainedBoxes());
        registerTest(new SpawnMarbleTest());
        registerTest(new SpawnConnectedBoxesTest());
    }

    private static void registerTest(IVxTestCommand test) {
        TESTS.put(test.getName().toLowerCase(), test);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> mainNode = Commands.literal("vxtest")
                .requires(source -> source.hasPermission(2));

        for (IVxTestCommand test : TESTS.values()) {
            LiteralArgumentBuilder<CommandSourceStack> testNode = Commands.literal(test.getName());
            test.registerArguments(testNode);
            mainNode.then(testNode);
        }
        dispatcher.register(mainNode);
    }
}