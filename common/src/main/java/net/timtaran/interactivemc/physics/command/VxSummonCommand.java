/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyType;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyRegistry;
import net.timtaran.interactivemc.physics.physics.body.type.VxBody;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Command to summon a physics body.
 *
 * @author xI-Mx-Ix
 */
public final class VxSummonCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vxsummon")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests((context, builder) -> {
                                    var registry = VxBodyRegistry.getInstance();
                                    return SharedSuggestionProvider.suggest(
                                            registry.getRegisteredTypes().values().stream()
                                                    .filter(VxBodyType::isSummonable)
                                                    .map(type -> type.getTypeId().toString()),
                                            builder
                                    );
                                })
                                .then(Commands.argument("position", Vec3Argument.vec3(true))
                                        .executes(VxSummonCommand::execute)
                                )
                                .executes(VxSummonCommand::execute)
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos;

        // Try to get the position argument, default to the command sender's position if not provided
        try {
            pos = Vec3Argument.getVec3(context, "position");
        } catch (IllegalArgumentException e) {
            pos = source.getPosition();
        }

        // Get the type argument (ResourceLocation)
        ResourceLocation typeId = ResourceLocationArgument.getId(context, "type");

        try {
            // Look up the registered body type
            VxBodyType<?> type = VxBodyRegistry.getInstance().getRegistrationData(typeId);

            if (type == null) {
                source.sendFailure(Component.literal("Physics body type not found: " + typeId));
                return 0;
            }

            // Check if this body type can be summoned
            if (!type.isSummonable()) {
                source.sendFailure(Component.literal("Physics body type '" + typeId + "' cannot be summoned."));
                return 0;
            }

            // Get the physics world for this dimension
            VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
            if (world == null) {
                source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
                return 0;
            }

            // Prepare transform for the new body (position + identity rotation)
            VxTransform transform = new VxTransform(new RVec3(pos.x, pos.y, pos.z), Quat.sIdentity());

            // Create the physics body instance
            VxBody body = type.create(world, UUID.randomUUID());
            if (body == null) {
                source.sendFailure(Component.literal("Failed to create an instance of " + typeId));
                return 0;
            }

            // Add the constructed body to the world with activation
            world.getBodyManager().addConstructedBody(body, EActivation.Activate, transform);

            // Notify the command sender of success
            source.sendSuccess(() -> Component.literal(
                    String.format("Successfully summoned physics body '%s' with ID: %s", typeId, body.getPhysicsId())
            ), true);

            return 1; // Command executed successfully

        } catch (Exception e) {
            // Catch any unexpected errors and log them
            source.sendFailure(Component.literal("An unexpected error occurred while executing the command."));
            e.printStackTrace(); // Stacktrace in console
            return 0;
        }
    }
}