/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public final class SpawnBoxCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnbox")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .then(Commands.argument("halfWidth", FloatArgumentType.floatArg(0.01f))
                                        .then(Commands.argument("halfHeight", FloatArgumentType.floatArg(0.01f))
                                                .then(Commands.argument("halfDepth", FloatArgumentType.floatArg(0.01f))
                                                        .executes(SpawnBoxCommand::executeFull)
                                                )
                                        )
                                )
                                .then(Commands.argument("size", FloatArgumentType.floatArg(0.01f))
                                        .executes(SpawnBoxCommand::executeSized)
                                )
                                .executes(SpawnBoxCommand::executeDefault)
                        )
        );

        dispatcher.register(
                Commands.literal("spawnboxgrid")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .then(Commands.argument("gridSizeX", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("gridSizeY", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("gridSizeZ", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("boxSize", FloatArgumentType.floatArg(0.1f))
                                                                .executes(SpawnBoxCommand::executeGrid)
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static int executeGrid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        Vec3 initialPos = Vec3Argument.getVec3(context, "position");
        int gridSizeX = IntegerArgumentType.getInteger(context, "gridSizeX");
        int gridSizeY = IntegerArgumentType.getInteger(context, "gridSizeY");
        int gridSizeZ = IntegerArgumentType.getInteger(context, "gridSizeZ");
        float boxSize = FloatArgumentType.getFloat(context, "boxSize");

        int spawnedCount = 0;
        float halfExtent = boxSize / 2.0f;
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent);

        for (int i = 0; i < gridSizeX; i++) {
            for (int j = 0; j < gridSizeY; j++) {
                for (int k = 0; k < gridSizeZ; k++) {
                    double x = initialPos.x + i * boxSize;
                    double y = initialPos.y + j * boxSize;
                    double z = initialPos.z + k * boxSize;
                    Vec3 spawnPosMc = new Vec3(x, y, z);

                    if (spawn(context.getSource(), serverLevel, spawnPosMc, halfExtents) == 1) {
                        spawnedCount++;
                    }
                }
            }
        }

        final int finalSpawnedCount = spawnedCount;
        source.sendSuccess(() -> Component.literal(String.format("Successfully spawned %d boxes in a %dx%dx%d grid.", finalSpawnedCount, gridSizeX, gridSizeY, gridSizeZ)), true);
        return finalSpawnedCount;
    }


    private static int executeFull(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float halfWidth = FloatArgumentType.getFloat(context, "halfWidth");
        float halfHeight = FloatArgumentType.getFloat(context, "halfHeight");
        float halfDepth = FloatArgumentType.getFloat(context, "halfDepth");
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfWidth, halfHeight, halfDepth);
        return spawn(context.getSource(), context.getSource().getLevel(), Vec3Argument.getVec3(context, "position"), halfExtents);
    }

    private static int executeSized(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float size = FloatArgumentType.getFloat(context, "size");
        float halfExtent = size / 2.0f;
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent);
        return spawn(context.getSource(), context.getSource().getLevel(), Vec3Argument.getVec3(context, "position"), halfExtents);
    }

    private static int executeDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(0.5f, 0.5f, 0.5f);
        return spawn(context.getSource(), context.getSource().getLevel(), Vec3Argument.getVec3(context, "position"), halfExtents);
    }

    private static int spawn(CommandSourceStack source, ServerLevel serverLevel, net.minecraft.world.phys.Vec3 spawnPosMc, com.github.stephengold.joltjni.Vec3 halfExtents) {
        if (halfExtents.getX() <= 0 || halfExtents.getY() <= 0 || halfExtents.getZ() <= 0) {
            source.sendFailure(Component.literal("Box dimensions (halfExtents) must be positive."));
            return 0;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }
        VxObjectManager manager = physicsWorld.getObjectManager();
        VxTransform transform = new VxTransform(new RVec3(spawnPosMc.x, spawnPosMc.y, spawnPosMc.z), Quat.sIdentity());

        BoxRigidBody spawnedObject = manager.createRigidBody(
                VxRegisteredObjects.BOX,
                transform,
                box -> box.setHalfExtents(halfExtents)
        );

        if (spawnedObject != null) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Successfully spawned box (%.2f x %.2f x %.2f) with ID: %s",
                            halfExtents.getX() * 2, halfExtents.getY() * 2, halfExtents.getZ() * 2, spawnedObject.getPhysicsId())
            ), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to register the box. Check logs for details."));
            return 0;
        }
    }
}