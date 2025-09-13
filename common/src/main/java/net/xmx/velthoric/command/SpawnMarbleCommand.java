/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.builtin.marble.MarbleRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public class SpawnMarbleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnmarble")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", Vec3Argument.vec3(true))
                        .executes(SpawnMarbleCommand::execute)
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.05f))
                                .executes(SpawnMarbleCommand::executeWithRadius)
                        )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return spawnMarble(context, 0.15f);
    }

    private static int executeWithRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float radius = FloatArgumentType.getFloat(context, "radius");
        return spawnMarble(context, radius);
    }

    private static int spawnMarble(CommandContext<CommandSourceStack> context, float radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = Vec3Argument.getVec3(context, "pos");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("The physics system for this dimension is not initialized."));
            return 0;
        }

        VxObjectManager manager = physicsWorld.getObjectManager();
        VxTransform transform = new VxTransform(new RVec3(pos.x(), pos.y(), pos.z()), Quat.sIdentity());

        MarbleRigidBody spawnedMarble = manager.createRigidBody(
                VxRegisteredObjects.MARBLE,
                transform,
                marble -> marble.setRadius(radius)
        );

        if (spawnedMarble != null) {
            source.sendSuccess(() -> Component.literal("Successfully spawned a marble with radius " + radius + "."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn the marble. Check the server logs."));
            return 0;
        }
    }
}
