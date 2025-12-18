/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command.test;

import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
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
import net.timtaran.interactivemc.physics.builtin.VxRegisteredBodies;
import net.timtaran.interactivemc.physics.builtin.box.BoxRigidBody;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.constraint.manager.VxConstraintManager;
import net.timtaran.interactivemc.physics.physics.body.manager.VxBodyManager;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

public final class SpawnBoxNet implements IVxTestCommand {

    @Override
    public String getName() {
        return "spawnBoxNet";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("start_position", Vec3Argument.vec3(true))
                .then(Commands.argument("sizeX", IntegerArgumentType.integer(1))
                        .then(Commands.argument("sizeY", IntegerArgumentType.integer(1))
                                .then(Commands.argument("sizeZ", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("boxSize", FloatArgumentType.floatArg(0.05f))
                                                .then(Commands.argument("spacing", FloatArgumentType.floatArg(0.0f))
                                                        .executes(this::execute)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        net.minecraft.world.phys.Vec3 startPos = Vec3Argument.getVec3(context, "start_position");

        int sizeX = IntegerArgumentType.getInteger(context, "sizeX");
        int sizeY = IntegerArgumentType.getInteger(context, "sizeY");
        int sizeZ = IntegerArgumentType.getInteger(context, "sizeZ");

        float boxSize = FloatArgumentType.getFloat(context, "boxSize");
        float spacing = FloatArgumentType.getFloat(context, "spacing");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        physicsWorld.execute(() -> {
            VxBodyManager bodyManager = physicsWorld.getBodyManager();
            VxConstraintManager constraintManager = physicsWorld.getConstraintManager();

            float boxHalfExtent = boxSize / 2.0f;
            Vec3 boxHalfExtents = new Vec3(boxHalfExtent, boxHalfExtent, boxHalfExtent);

            // Distance between the centers of two adjacent boxes
            float step = boxSize + spacing;

            // Distance from the center of a body to the constraint pivot point (midpoint of the gap)
            float constraintOffset = boxHalfExtent + (spacing / 2.0f);

            BoxRigidBody[][][] gridBodies = new BoxRigidBody[sizeX][sizeY][sizeZ];

            // 1. Create all bodies
            for (int x = 0; x < sizeX; ++x) {
                for (int y = 0; y < sizeY; ++y) {
                    for (int z = 0; z < sizeZ; ++z) {
                        RVec3 currentPosition = new RVec3(
                                startPos.x + (x * step),
                                startPos.y + (y * step),
                                startPos.z + (z * step)
                        );

                        BoxRigidBody currentBody = bodyManager.createRigidBody(
                                VxRegisteredBodies.BOX,
                                new VxTransform(currentPosition, Quat.sIdentity()),
                                body -> body.setHalfExtents(boxHalfExtents)
                        );

                        if (currentBody == null) {
                            source.sendFailure(Component.literal("Failed to create a grid segment. Aborting."));
                            return;
                        }

                        gridBodies[x][y][z] = currentBody;
                    }
                }
            }

            // 2. Link bodies with PointConstraints
            for (int x = 0; x < sizeX; ++x) {
                for (int y = 0; y < sizeY; ++y) {
                    for (int z = 0; z < sizeZ; ++z) {
                        BoxRigidBody body1 = gridBodies[x][y][z];

                        // Link along X axis
                        if (x < sizeX - 1) {
                            BoxRigidBody body2 = gridBodies[x + 1][y][z];
                            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                                settings.setSpace(EConstraintSpace.LocalToBodyCom);
                                // Pivot is placed halfway between the two bodies
                                settings.setPoint1(new RVec3(constraintOffset, 0, 0));
                                settings.setPoint2(new RVec3(-constraintOffset, 0, 0));
                                constraintManager.createConstraint(settings, body1.getPhysicsId(), body2.getPhysicsId());
                            }
                        }

                        // Link along Y axis
                        if (y < sizeY - 1) {
                            BoxRigidBody body2 = gridBodies[x][y + 1][z];
                            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                                settings.setSpace(EConstraintSpace.LocalToBodyCom);
                                settings.setPoint1(new RVec3(0, constraintOffset, 0));
                                settings.setPoint2(new RVec3(0, -constraintOffset, 0));
                                constraintManager.createConstraint(settings, body1.getPhysicsId(), body2.getPhysicsId());
                            }
                        }

                        // Link along Z axis
                        if (z < sizeZ - 1) {
                            BoxRigidBody body2 = gridBodies[x][y][z + 1];
                            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                                settings.setSpace(EConstraintSpace.LocalToBodyCom);
                                settings.setPoint1(new RVec3(0, 0, constraintOffset));
                                settings.setPoint2(new RVec3(0, 0, -constraintOffset));
                                constraintManager.createConstraint(settings, body1.getPhysicsId(), body2.getPhysicsId());
                            }
                        }
                    }
                }
            }

            source.sendSuccess(() -> Component.literal(
                    String.format("Successfully created a %d x %d x %d box net (Size: %.2f, Spacing: %.2f).",
                            sizeX, sizeY, sizeZ, boxSize, spacing)), true);
        });

        return 1;
    }
}