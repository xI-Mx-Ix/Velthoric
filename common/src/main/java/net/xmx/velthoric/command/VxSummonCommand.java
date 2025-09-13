/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public final class VxSummonCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vxsummon")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests((context, builder) -> {
                                    var registry = VxObjectRegistry.getInstance();
                                    return SharedSuggestionProvider.suggest(
                                            registry.getRegisteredTypes().values().stream()
                                                    .filter(VxObjectType::isSummonable)
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

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos;
        try {
            pos = Vec3Argument.getVec3(context, "position");
        } catch (IllegalArgumentException e) {
            pos = source.getPosition();
        }
        ResourceLocation typeId = ResourceLocationArgument.getId(context, "type");

        VxObjectType<?> type = VxObjectRegistry.getInstance().getRegistrationData(typeId);

        if (type == null) {
            source.sendFailure(Component.literal("Physics object type not found: " + typeId));
            return 0;
        }

        if (!type.isSummonable()) {
            source.sendFailure(Component.literal("Physics object type '" + typeId + "' cannot be summoned."));
            return 0;
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        VxObjectManager manager = world.getObjectManager();
        VxTransform transform = new VxTransform(new RVec3(pos.x, pos.y, pos.z), Quat.sIdentity());

        VxAbstractBody body = type.create(world, UUID.randomUUID());
        if (body == null) {
            source.sendFailure(Component.literal("Failed to create an instance of " + typeId));
            return 0;
        }
        body.getGameTransform().set(transform);

        manager.addConstructedBody(body, EActivation.Activate);

        source.sendSuccess(() -> Component.literal(String.format("Successfully summoned physics object '%s' with ID: %s", typeId, body.getPhysicsId())), true);
        return 1;
    }
}
