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

public class VxBodyArgument implements ArgumentType<VxBodySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("@x", "@x[limit=1,sort=nearest]", "@x[type=velthoric:box]");

    private VxBodyArgument() {}

    public static VxBodyArgument instance() {
        return new VxBodyArgument();
    }

    @Override
    public VxBodySelector parse(StringReader reader) throws CommandSyntaxException {
        return new VxBodySelectorParser(reader).parse();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            VxBodySelectorParser parser = new VxBodySelectorParser(reader);
            try {
                parser.parse();
            } catch (CommandSyntaxException ignored) {
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

    public static List<VxBody> getBodies(CommandContext<CommandSourceStack> context, String name) {
        VxBodySelector selector = context.getArgument(name, VxBodySelector.class);
        return selector.select(context.getSource());
    }
}