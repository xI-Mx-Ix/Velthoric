package net.xmx.xbullet.command.xbullet;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
// KORREKTUR HIER: Import hinzufügen
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.resources.ResourceLocation;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class XBulletCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> xbulletCommand = Commands.literal("xbullet")
                .requires(source -> source.hasPermission(2));

        LiteralArgumentBuilder<CommandSourceStack> spawnCommand = Commands.literal("spawn");

        // --- RIGID ---
        spawnCommand.then(Commands.literal("rigid")
                // KORREKTUR HIER: Den richtigen Argument-Typ verwenden
                .then(Commands.argument("objectType", ResourceLocationArgument.id())
                        .suggests(XBulletCommand::suggestRigidObjectTypes)
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .executes(SpawnObjectCommandExecutor::executeRigid)
                        )
                )
        );

        // --- SOFT ---
        // KORREKTUR HIER: Den richtigen Argument-Typ verwenden
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> softBodyTypeArgument = Commands.argument("objectType", ResourceLocationArgument.id())
                .suggests(XBulletCommand::suggestSoftObjectTypes);

        softBodyTypeArgument.then(Commands.argument("position", Vec3Argument.vec3(true))
                .then(Commands.argument("length", FloatArgumentType.floatArg(0.1f))
                        .then(Commands.argument("segments", IntegerArgumentType.integer(1))
                                .then(Commands.argument("radius", FloatArgumentType.floatArg(0.01f))
                                        .executes(SpawnObjectCommandExecutor::executeSoftRope)
                                )
                        )
                )
        );

        spawnCommand.then(Commands.literal("soft")
                .then(softBodyTypeArgument)
        );

        xbulletCommand.then(spawnCommand);

        // --- Andere Befehle ---
        xbulletCommand.then(Commands.literal("count")
                .then(Commands.argument("side", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"server", "client"}, b))
                        .executes(CountPhysicsObjectCommandExecutor::execute)
                )
        );
        xbulletCommand.then(Commands.literal("pause")
                .executes(PauseResumePhysicsCommandExecutor::executePause)
        );
        xbulletCommand.then(Commands.literal("resume")
                .executes(PauseResumePhysicsCommandExecutor::executeResume)
        );

        dispatcher.register(xbulletCommand);
    }

    private static CompletableFuture<Suggestions> suggestRigidObjectTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // KORREKTUR HIER: Die korrekte Suggestion-Methode für ResourceLocations verwenden
        Stream<ResourceLocation> locations = GlobalPhysicsObjectRegistry.getRegisteredTypes().entrySet().stream()
                .filter(entry -> entry.getValue().objectType() == EObjectType.RIGID_BODY)
                .map(entry -> ResourceLocation.parse(entry.getKey()));
        return SharedSuggestionProvider.suggestResource(locations, builder);
    }

    private static CompletableFuture<Suggestions> suggestSoftObjectTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // KORREKTUR HIER: Die korrekte Suggestion-Methode für ResourceLocations verwenden
        Stream<ResourceLocation> locations = GlobalPhysicsObjectRegistry.getRegisteredTypes().entrySet().stream()
                .filter(entry -> entry.getValue().objectType() == EObjectType.SOFT_BODY)
                .map(entry -> ResourceLocation.parse(entry.getKey()));
        return SharedSuggestionProvider.suggestResource(locations, builder);
    }
}