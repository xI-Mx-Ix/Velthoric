/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public final class SpawnBoxGridTest implements IVxTestCommand {

    @Override
    public String getName() {
        return "spawnBoxGrid";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("position", Vec3Argument.vec3(true))
                .then(Commands.argument("gridSizeX", IntegerArgumentType.integer(1))
                        .then(Commands.argument("gridSizeY", IntegerArgumentType.integer(1))
                                .then(Commands.argument("gridSizeZ", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("boxSize", FloatArgumentType.floatArg(0.1f))
                                                .then(Commands.argument("spacing", FloatArgumentType.floatArg(0f))
                                                        .executes(this::executeGridWithSpacing)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private int executeGridWithSpacing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        Vec3 initialPos = Vec3Argument.getVec3(context, "position");
        int gridSizeX = IntegerArgumentType.getInteger(context, "gridSizeX");
        int gridSizeY = IntegerArgumentType.getInteger(context, "gridSizeY");
        int gridSizeZ = IntegerArgumentType.getInteger(context, "gridSizeZ");
        float boxSize = FloatArgumentType.getFloat(context, "boxSize");
        float spacing = FloatArgumentType.getFloat(context, "spacing");

        int spawnedCount = 0;
        float halfExtent = boxSize / 2.0f;
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent);
        float step = boxSize + spacing;

        for (int i = 0; i < gridSizeX; i++) {
            for (int j = 0; j < gridSizeY; j++) {
                for (int k = 0; k < gridSizeZ; k++) {
                    double x = initialPos.x + i * step;
                    double y = initialPos.y + j * step;
                    double z = initialPos.z + k * step;
                    if (spawn(serverLevel, new Vec3(x, y, z), halfExtents) == 1) {
                        spawnedCount++;
                    }
                }
            }
        }

        int finalSpawnedCount = spawnedCount;
        source.sendSuccess(() -> Component.literal(String.format(
                "Successfully spawned %d boxes in a %dx%dx%d grid with spacing %.2f.",
                finalSpawnedCount, gridSizeX, gridSizeY, gridSizeZ, spacing)), true);
        return spawnedCount;
    }

    private int spawn(ServerLevel serverLevel, Vec3 spawnPosMc, com.github.stephengold.joltjni.Vec3 halfExtents) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) return 0;

        VxBodyManager manager = physicsWorld.getBodyManager();
        VxTransform transform = new VxTransform(new RVec3(spawnPosMc.x, spawnPosMc.y, spawnPosMc.z), Quat.sIdentity());

        BoxRigidBody spawnedBody = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform,
                box -> box.setHalfExtents(halfExtents)
        );
        return spawnedBody != null ? 1 : 0;
    }
}