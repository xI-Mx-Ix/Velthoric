/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.argument;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.google.common.primitives.Doubles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Parses a string representation of a physics object selector (e.g., "@x[type=velthoric:box,limit=1]").
 * This class handles the logic for parsing the selector and its arguments, as well as providing
 * context-aware command suggestions. It is designed to mimic the behavior of Minecraft's vanilla
 * {@link net.minecraft.commands.arguments.selector.EntitySelectorParser}.
 */
public class VxObjectSelectorParser {

    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(Component.literal("Missing selector type"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown selector type '" + obj + "'"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(Component.literal("Expected ']' to end selector options"));
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType((obj) -> Component.literal("Expected value for option '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown option '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_INVALID_BODY_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown body type '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_INVALID_SORT_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown sort type '" + obj + "'"));

    // A list of all valid option keys for providing suggestions.
    private static final List<String> OPTION_KEYS = Arrays.asList("limit", "distance", "type", "bodytype", "sort");

    public static final BiConsumer<Vec3, List<VxAbstractBody>> ORDER_NEAREST_VX = (sourcePos, list) ->
            list.sort((a, b) -> {
                var posA = a.getGameTransform().getTranslation();
                var posB = b.getGameTransform().getTranslation();
                return Doubles.compare(sourcePos.distanceToSqr(posA.x(), posA.y(), posA.z()), sourcePos.distanceToSqr(posB.x(), posB.y(), posB.z()));
            });

    public static final BiConsumer<Vec3, List<VxAbstractBody>> ORDER_FURTHEST_VX = (sourcePos, list) ->
            list.sort((a, b) -> {
                var posA = a.getGameTransform().getTranslation();
                var posB = b.getGameTransform().getTranslation();
                return Doubles.compare(sourcePos.distanceToSqr(posB.x(), posB.y(), posB.z()), sourcePos.distanceToSqr(posA.x(), posA.y(), posA.z()));
            });

    public static final BiConsumer<Vec3, List<VxAbstractBody>> ORDER_RANDOM_VX = (sourcePos, list) -> Collections.shuffle(list);

    private final StringReader reader;
    private int limit = Integer.MAX_VALUE;
    private MinMaxBounds.Doubles distance = MinMaxBounds.Doubles.ANY;
    @Nullable private ResourceLocation type;
    private boolean typeInverse = false;
    @Nullable private EBodyType bodyType;
    private BiConsumer<Vec3, List<VxAbstractBody>> order = (pos, list) -> {};
    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions = (b, c) -> b.buildFuture();

    public VxObjectSelectorParser(StringReader reader) {
        this.reader = reader;
    }

    /**
     * Parses the input from the StringReader into a complete {@link VxObjectSelector}.
     *
     * @return The parsed selector.
     * @throws CommandSyntaxException if the selector syntax is invalid.
     */
    public VxObjectSelector parse() throws CommandSyntaxException {
        this.suggestions = this::suggestSelector;
        if (!reader.canRead() || reader.peek() != '@') {
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader);
        }
        reader.skip();

        this.suggestions = this::suggestSelectorType;
        if (!reader.canRead()) {
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader);
        }
        char selectorChar = reader.read();
        if (selectorChar != 'x') {
            reader.setCursor(reader.getCursor() - 1);
            throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(reader, "@" + selectorChar);
        }

        this.suggestions = this::suggestOpenOptions;
        if (reader.canRead() && reader.peek() == '[') {
            reader.skip();
            this.suggestions = this::suggestOptionsKeyOrClose;
            parseOptions();
        }

        return new VxObjectSelector(limit, distance, type, typeInverse, bodyType, order);
    }

    // Main loop for parsing key-value options within square brackets.
    private void parseOptions() throws CommandSyntaxException {
        this.suggestions = this::suggestOptionsKey;
        reader.skipWhitespace();

        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int cursorBeforeOption = reader.getCursor();
            String optionName = reader.readString();
            this.suggestions = this::suggestEquals;
            reader.skipWhitespace();

            if (!reader.canRead() || reader.peek() != '=') {
                reader.setCursor(cursorBeforeOption);
                throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, optionName);
            }
            reader.skip();
            reader.skipWhitespace();

            this.suggestions = (builder, consumer) -> suggestOptionValues(optionName, builder);
            parseOptionValue(optionName);
            reader.skipWhitespace();

            this.suggestions = this::suggestOptionsNextOrClose;
            if (reader.canRead() && reader.peek() == ',') {
                reader.skip();
                this.suggestions = this::suggestOptionsKey;
            } else {
                break; // Exit loop if no comma is found, expecting ']'
            }
        }

        if (!reader.canRead() || reader.read() != ']') {
            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
        }
        this.suggestions = (b, c) -> Suggestions.empty();
    }

    // Parses the value for a given option key.
    private void parseOptionValue(String name) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        switch (name) {
            case "limit" -> limit = reader.readInt();
            case "distance" -> distance = MinMaxBounds.Doubles.fromReader(reader);
            case "type" -> {
                if (reader.canRead() && reader.peek() == '!') {
                    typeInverse = true;
                    reader.skip();
                }
                type = ResourceLocation.read(reader);
            }
            case "bodytype" -> {
                String bodyTypeName = reader.readString();
                Optional<EBodyType> match = Arrays.stream(EBodyType.values())
                        .filter(e -> e.name().equalsIgnoreCase(bodyTypeName))
                        .findFirst();

                if (match.isPresent()) {
                    bodyType = match.get();
                } else {
                    reader.setCursor(cursor);
                    throw ERROR_INVALID_BODY_TYPE.createWithContext(reader, bodyTypeName);
                }
            }
            case "sort" -> {
                String sortType = reader.readString();
                switch (sortType.toLowerCase()) {
                    case "nearest" -> order = ORDER_NEAREST_VX;
                    case "furthest" -> order = ORDER_FURTHEST_VX;
                    case "random" -> order = ORDER_RANDOM_VX;
                    default -> {
                        reader.setCursor(cursor);
                        throw ERROR_INVALID_SORT_TYPE.createWithContext(reader, sortType);
                    }
                }
            }
            default -> {
                reader.setCursor(cursor);
                throw ERROR_UNKNOWN_OPTION.createWithContext(reader, name);
            }
        }
    }

    /**
     * Fills the suggestion builder with context-aware suggestions.
     * The actual suggestions are provided by the current `suggestions` function,
     * which is updated as parsing progresses.
     *
     * @param builder The suggestions builder.
     * @param consumer A consumer for the builder.
     * @return A future that will complete with the suggestions.
     */
    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        return this.suggestions.apply(builder.createOffset(this.reader.getCursor()), consumer);
    }

    // Provides suggestions for the selector type itself, e.g., "@x".
    private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("@x");
        return builder.buildFuture();
    }

    // Suggests the character 'x' after the '@'.
    private CompletableFuture<Suggestions> suggestSelectorType(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("x");
        return builder.buildFuture();
    }

    // Suggests the opening bracket '[' for options.
    private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        if (!reader.canRead() || reader.peek() != '[') {
            builder.suggest("[");
        }
        return builder.buildFuture();
    }

    // Suggests the available option keys, filtering based on the current input.
    private void suggestOptionKeys(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String key : OPTION_KEYS) {
            if (key.toLowerCase().startsWith(remaining)) {
                builder.suggest(key + "=");
            }
        }
    }

    // Suggests option keys or the closing bracket ']'.
    private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("]");
        suggestOptionKeys(builder);
        return builder.buildFuture();
    }

    // Suggests option keys.
    private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        suggestOptionKeys(builder);
        return builder.buildFuture();
    }

    // Suggests the next option via a comma ',' or the end of options via ']'.
    private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(",");
        builder.suggest("]");
        return builder.buildFuture();
    }

    // Suggests the equals sign '=' after an option key.
    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("=");
        return builder.buildFuture();
    }

    // Provides suggestions for the values of a specific option.
    private CompletableFuture<Suggestions> suggestOptionValues(String option, SuggestionsBuilder builder) {
        switch (option) {
            case "type" -> {
                builder.suggest("!");
                // This now correctly accesses the common registry, which is populated on the client.
                SharedSuggestionProvider.suggestResource(VxObjectRegistry.getInstance().getRegisteredTypes().keySet(), builder);
            }
            case "bodytype" -> SharedSuggestionProvider.suggest(Arrays.stream(EBodyType.values()).map(e -> e.name().toLowerCase()), builder);
            case "sort" -> SharedSuggestionProvider.suggest(new String[]{"nearest", "furthest", "random"}, builder);
        }
        return builder.buildFuture();
    }
}