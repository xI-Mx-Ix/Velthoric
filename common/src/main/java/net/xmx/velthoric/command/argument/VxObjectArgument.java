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
import net.xmx.velthoric.physics.object.type.VxBody;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VxObjectArgument implements ArgumentType<VxObjectSelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("@x", "@x[limit=1,sort=nearest]", "@x[type=velthoric:box]");

    private VxObjectArgument() {}

    public static VxObjectArgument instance() {
        return new VxObjectArgument();
    }

    @Override
    public VxObjectSelector parse(StringReader reader) throws CommandSyntaxException {
        return new VxObjectSelectorParser(reader).parse();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            VxObjectSelectorParser parser = new VxObjectSelectorParser(reader);
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

    public static List<VxBody> getObjects(CommandContext<CommandSourceStack> context, String name) {
        VxObjectSelector selector = context.getArgument(name, VxObjectSelector.class);
        return selector.select(context.getSource());
    }
}