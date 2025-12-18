/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command.test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.timtaran.interactivemc.physics.command.VxTestCommand;

/**
 * Interface for implementing a Velthoric test subcommand.
 * <p>
 * Implementations of this interface are automatically registered by {@link VxTestCommand}.
 *
 * @author xI-Mx-Ix
 */
public interface IVxTestCommand {

    /**
     * Gets the name of the subcommand (e.g., "spawnbox").
     *
     * @return The command name.
     */
    String getName();

    /**
     * Registers the arguments and execution logic for this subcommand.
     *
     * @param builder The argument builder to append to.
     */
    void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder);
}