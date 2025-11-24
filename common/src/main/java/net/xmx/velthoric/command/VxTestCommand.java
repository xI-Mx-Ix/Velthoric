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

/**
 * Central registry and dispatcher for Velthoric debug commands.
 * <p>
 * This class handles the registration of the {@code /vxtest} command tree,
 * which is used for developer testing of physics mechanics (spawning boxes, ropes, cloth, etc.).
 *
 * @author xI-Mx-Ix
 */
public final class VxTestCommand {

    // Thread-safe map containing all registered test subcommands
    private static final Map<String, IVxTestCommand> TESTS = new ConcurrentHashMap<>();

    static {
        // Register available test scenarios
        registerTest(new SpawnBoxTest());
        registerTest(new SpawnBoxGridTest());
        registerTest(new SpawnClothTest());
        registerTest(new SpawnRopeTest());
        registerTest(new SpawnChainedBoxes());
        registerTest(new SpawnMarbleTest());
        registerTest(new SpawnBoxNet());
        registerTest(new SpawnRagdollTest());
    }

    /**
     * Helper method to add a test command to the internal map.
     *
     * @param test The test command implementation.
     */
    private static void registerTest(IVxTestCommand test) {
        TESTS.put(test.getName().toLowerCase(), test);
    }

    /**
     * Registers the main command node to the Brigadier dispatcher.
     * <p>
     * The command structure is: {@code /vxtest <testName> [arguments]}.
     * Requires permission level 2 (Game Masters/OPs).
     *
     * @param dispatcher The command dispatcher from the server.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> mainNode = Commands.literal("vxtest")
                .requires(source -> source.hasPermission(2));

        // Iterate through all registered tests and append them as subcommands
        for (IVxTestCommand test : TESTS.values()) {
            LiteralArgumentBuilder<CommandSourceStack> testNode = Commands.literal(test.getName());
            test.registerArguments(testNode);
            mainNode.then(testNode);
        }
        dispatcher.register(mainNode);
    }
}