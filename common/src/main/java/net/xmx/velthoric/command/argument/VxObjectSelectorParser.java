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
import net.xmx.velthoric.physics.object.type.VxBody;
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

public class VxObjectSelectorParser {

    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(Component.literal("Missing selector type"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown selector type '" + obj + "'"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(Component.literal("Expected ']' to end selector options"));
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType((obj) -> Component.literal("Expected value for option '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown option '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_INVALID_BODY_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown body type '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_INVALID_SORT_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown sort type '" + obj + "'"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OBJECT_TYPE = new DynamicCommandExceptionType((obj) -> Component.literal("Unknown object type '" + obj + "'"));

    private static final List<String> OPTION_KEYS = Arrays.asList("limit", "distance", "type", "bodytype", "sort");

    public static final BiConsumer<Vec3, List<VxBody>> ORDER_NEAREST_VX = (sourcePos, list) ->
            list.sort((a, b) -> {
                var posA = a.getTransform().getTranslation();
                var posB = b.getTransform().getTranslation();
                return Doubles.compare(sourcePos.distanceToSqr(posA.x(), posA.y(), posA.z()), sourcePos.distanceToSqr(posB.x(), posB.y(), posB.z()));
            });

    public static final BiConsumer<Vec3, List<VxBody>> ORDER_FURTHEST_VX = (sourcePos, list) ->
            list.sort((a, b) -> {
                var posA = a.getTransform().getTranslation();
                var posB = b.getTransform().getTranslation();
                return Doubles.compare(sourcePos.distanceToSqr(posB.x(), posB.y(), posB.z()), sourcePos.distanceToSqr(posA.x(), posA.y(), posA.z()));
            });

    public static final BiConsumer<Vec3, List<VxBody>> ORDER_RANDOM_VX = (sourcePos, list) -> Collections.shuffle(list);

    private final StringReader reader;
    private int limit = Integer.MAX_VALUE;
    private MinMaxBounds.Doubles distance = MinMaxBounds.Doubles.ANY;
    @Nullable private ResourceLocation type;
    private boolean typeInverse = false;
    @Nullable private EBodyType bodyType;
    private BiConsumer<Vec3, List<VxBody>> order = (pos, list) -> {};
    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions = (b, c) -> b.buildFuture();

    public VxObjectSelectorParser(StringReader reader) {
        this.reader = reader;
    }

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

    private void parseOptions() throws CommandSyntaxException {
        this.suggestions = this::suggestOptionsKey;
        reader.skipWhitespace();

        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int cursorBeforeOption = reader.getCursor();
            String optionName = reader.readString();

            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') {
                reader.setCursor(cursorBeforeOption);
                throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, optionName);
            }
            this.suggestions = this::suggestEquals;
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
                break;
            }
        }

        if (!reader.canRead() || reader.read() != ']') {
            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
        }
        this.suggestions = (b, c) -> b.buildFuture();
    }

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
                int valueStartCursor = reader.getCursor();
                ResourceLocation parsedLocation = ResourceLocation.read(reader);

                if (parsedLocation.getPath().isEmpty()) {
                    reader.setCursor(valueStartCursor);
                    throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, name);
                }

                if (!VxObjectRegistry.getInstance().getRegisteredTypes().containsKey(parsedLocation)) {
                    reader.setCursor(valueStartCursor);
                    throw ERROR_UNKNOWN_OBJECT_TYPE.createWithContext(reader, parsedLocation.toString());
                }

                this.type = parsedLocation;
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

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        return this.suggestions.apply(builder.createOffset(this.reader.getCursor()), consumer);
    }

    private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("@x");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSelectorType(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("x");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("[");
        return builder.buildFuture();
    }

    private void suggestOptionKeys(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String key : OPTION_KEYS) {
            if (key.toLowerCase().startsWith(remaining)) {
                builder.suggest(key + "=");
            }
        }
    }

    private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("]");
        suggestOptionKeys(builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        suggestOptionKeys(builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(",");
        builder.suggest("]");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest("=");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionValues(String option, SuggestionsBuilder builder) {
        switch (option) {
            case "type" -> {
                var keys = VxObjectRegistry.getInstance().getRegisteredTypes().keySet().stream().map(ResourceLocation::toString);
                String remaining = builder.getRemaining();

                if (remaining.isEmpty()) {
                    builder.suggest("!");
                }

                if (remaining.startsWith("!")) {
                    SuggestionsBuilder subBuilder = builder.createOffset(builder.getStart() + 1);
                    SharedSuggestionProvider.suggest(keys, subBuilder);
                    return builder.add(subBuilder).buildFuture();
                } else {
                    SharedSuggestionProvider.suggest(keys, builder);
                }
            }
            case "bodytype" -> SharedSuggestionProvider.suggest(Arrays.stream(EBodyType.values()).map(e -> e.name().toLowerCase()), builder);
            case "sort" -> SharedSuggestionProvider.suggest(new String[]{"nearest", "furthest", "random"}, builder);
        }
        return builder.buildFuture();
    }
}