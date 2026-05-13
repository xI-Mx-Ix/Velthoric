/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
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
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Spawns a configurable Jenga tower made of box rigid bodies.
 * <p>
 * Each layer consists of {@code blocksPerLayer} blocks placed side-by-side.
 * Every other layer is rotated 90 degrees. Block dimensions, layer count,
 * and blocks-per-layer are all configurable via command arguments.
 * <p>
 * Usage: {@code /vxtest jenga <position> [layers] [blocksPerLayer] [blockLength] [blockWidth] [blockHeight]}
 *
 * @author xI-Mx-Ix
 */
public final class SpawnJengaTest implements IVxTestCommand {

    @Override
    public String getName() {
        return "jenga";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("position", Vec3Argument.vec3(true))
                // Full config: /vxtest jenga <pos> <layers> <blocksPerLayer> <blockLength> <blockWidth> <blockHeight>
                .then(Commands.argument("layers", IntegerArgumentType.integer(1, 100))
                        .then(Commands.argument("blocksPerLayer", IntegerArgumentType.integer(1, 10))
                                .then(Commands.argument("blockLength", FloatArgumentType.floatArg(0.01f))
                                        .then(Commands.argument("blockWidth", FloatArgumentType.floatArg(0.01f))
                                                .then(Commands.argument("blockHeight", FloatArgumentType.floatArg(0.01f))
                                                        .executes(this::executeFull)
                                                )
                                        )
                                )
                                // Only layers + blocksPerLayer, default block dimensions
                                .executes(this::executeWithCount)
                        )
                        // Only layers, default blocksPerLayer and block dimensions
                        .executes(this::executeWithLayers)
                )
                // Default everything
                .executes(this::executeDefault)
        );
    }

    private int executeDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        return buildTower(context.getSource(), pos, 18, 3, 0.75f, 0.25f, 0.25f);
    }

    private int executeWithLayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        int layers = IntegerArgumentType.getInteger(context, "layers");
        return buildTower(context.getSource(), pos, layers, 3, 0.75f, 0.25f, 0.25f);
    }

    private int executeWithCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        int layers = IntegerArgumentType.getInteger(context, "layers");
        int blocksPerLayer = IntegerArgumentType.getInteger(context, "blocksPerLayer");
        return buildTower(context.getSource(), pos, layers, blocksPerLayer, 0.75f, 0.25f, 0.25f);
    }

    private int executeFull(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        int layers = IntegerArgumentType.getInteger(context, "layers");
        int blocksPerLayer = IntegerArgumentType.getInteger(context, "blocksPerLayer");
        float blockLength = FloatArgumentType.getFloat(context, "blockLength");
        float blockWidth = FloatArgumentType.getFloat(context, "blockWidth");
        float blockHeight = FloatArgumentType.getFloat(context, "blockHeight");
        return buildTower(context.getSource(), pos, layers, blocksPerLayer, blockLength, blockWidth, blockHeight);
    }

    /**
     * Builds a Jenga tower at the specified position.
     * <p>
     * The tower is centered on the given position. Each layer has {@code blocksPerLayer} blocks
     * placed side-by-side along one axis. Every other layer is rotated 90 degrees around Y.
     * A small gap between blocks prevents initial overlap and physics instability.
     *
     * @param source         The command source for feedback messages.
     * @param spawnPos       The base center position of the tower.
     * @param layers         Number of vertical layers.
     * @param blocksPerLayer Number of blocks per layer.
     * @param blockLength    Full length of each block (long axis).
     * @param blockWidth     Full width of each block (short axis).
     * @param blockHeight    Full height of each block.
     * @return 1 on success, 0 on failure.
     */
    private int buildTower(CommandSourceStack source, Vec3 spawnPos,
                           int layers, int blocksPerLayer,
                           float blockLength, float blockWidth, float blockHeight) {

        ServerLevel level = source.getLevel();
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        VxServerBodyManager manager = physicsWorld.getBodyManager();

        // Half extents for the box shape
        float halfLength = blockLength / 2.0f;
        float halfWidth = blockWidth / 2.0f;
        float halfHeight = blockHeight / 2.0f;

        // Small gap between blocks to prevent initial overlap
        float gap = 0.005f;

        // Total width of a layer (blocks placed side-by-side along the short axis)
        float layerSpan = blocksPerLayer * blockWidth + (blocksPerLayer - 1) * gap;
        float layerHalfSpan = layerSpan / 2.0f;

        int spawned = 0;

        for (int layer = 0; layer < layers; layer++) {
            boolean rotated = (layer % 2 == 1);

            // Y position: bottom of first layer sits at spawnPos.y
            double y = spawnPos.y + halfHeight + layer * (blockHeight + gap);

            // Quaternion for this layer: identity for even layers, 90° Y rotation for odd layers
            Quat rotation = rotated
                    ? Quat.sRotation(new com.github.stephengold.joltjni.Vec3(0, 1, 0), (float) (Math.PI / 2.0))
                    : Quat.sIdentity();

            for (int block = 0; block < blocksPerLayer; block++) {
                // Offset along the short axis, centered on 0
                float offset = -layerHalfSpan + halfWidth + block * (blockWidth + gap);

                double x, z;
                if (!rotated) {
                    // Even layer: blocks along X axis, offset along Z
                    x = spawnPos.x;
                    z = spawnPos.z + offset;
                } else {
                    // Odd layer: blocks along Z axis, offset along X
                    x = spawnPos.x + offset;
                    z = spawnPos.z;
                }

                VxTransform transform = new VxTransform(new RVec3(x, y, z), rotation);
                com.github.stephengold.joltjni.Vec3 halfExtents =
                        new com.github.stephengold.joltjni.Vec3(halfLength, halfHeight, halfWidth);

                BoxRigidBody body = manager.createBody(
                        VxRegisteredBodies.BOX,
                        transform,
                        EMotionType.Dynamic,
                        EActivation.DontActivate,
                        box -> {
                            box.setHalfExtents(halfExtents);
                            box.setColor(BoxColor.getRandom());
                        }
                );

                if (body != null) {
                    spawned++;
                }
            }
        }

        int totalSpawned = spawned;
        int totalExpected = layers * blocksPerLayer;
        source.sendSuccess(() -> Component.literal(
                String.format("Jenga tower built: %d/%d blocks, %d layers, %.2f x %.2f x %.2f blocks",
                        totalSpawned, totalExpected, layers, blockLength, blockWidth, blockHeight)
        ), true);
        return 1;
    }
}