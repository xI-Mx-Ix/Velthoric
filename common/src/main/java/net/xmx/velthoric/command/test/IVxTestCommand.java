/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Interface for implementing a Velthoric test subcommand.
 * <p>
 * Implementations of this interface are automatically registered by {@link net.xmx.velthoric.command.VxTestCommand}.
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