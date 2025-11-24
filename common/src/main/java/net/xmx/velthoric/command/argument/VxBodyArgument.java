/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A custom Brigadier argument type for selecting physics bodies.
 * <p>
 * Allows commands to target physics objects using a selector syntax similar to entities
 * (e.g., {@code @x[type=velthoric:box, limit=5]}).
 *
 * @author xI-Mx-Ix
 */
public class VxBodyArgument implements ArgumentType<VxBodySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("@x", "@x[limit=1,sort=nearest]", "@x[type=velthoric:box]");

    private VxBodyArgument() {}

    /**
     * Creates a new instance of the argument type.
     *
     * @return The argument instance.
     */
    public static VxBodyArgument instance() {
        return new VxBodyArgument();
    }

    /**
     * Parses the argument from the command string.
     *
     * @param reader The string reader.
     * @return A compiled {@link VxBodySelector}.
     * @throws CommandSyntaxException If the syntax is invalid.
     */
    @Override
    public VxBodySelector parse(StringReader reader) throws CommandSyntaxException {
        return new VxBodySelectorParser(reader).parse();
    }

    /**
     * Provides auto-completion suggestions for the selector.
     *
     * @param context The command context.
     * @param builder The suggestions builder.
     * @return A future containing the suggestions.
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            VxBodySelectorParser parser = new VxBodySelectorParser(reader);
            try {
                parser.parse();
            } catch (CommandSyntaxException ignored) {
                // Ignore syntax errors during suggestion generation to allow partial completion
            }
            return parser.fillSuggestions(builder, b -> {});
        } else {
            return Suggestions.empty();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /**
     * Helper method to retrieve the list of selected bodies from a command context.
     *
     * @param context The command context.
     * @param name    The name of the argument.
     * @return A list of matching {@link VxBody} objects.
     */
    public static List<VxBody> getBodies(CommandContext<CommandSourceStack> context, String name) {
        VxBodySelector selector = context.getArgument(name, VxBodySelector.class);
        return selector.select(context.getSource());
    }
}