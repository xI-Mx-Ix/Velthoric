/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

public interface IVxTestCommand {
    String getName();
    void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder);
}