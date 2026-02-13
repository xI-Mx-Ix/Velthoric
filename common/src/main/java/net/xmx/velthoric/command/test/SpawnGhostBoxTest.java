/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.math.VxTransform;

/**
 * A test command to verify selective dynamic physics layers.
 *
 * <p>This command creates a dynamic layer configured to collide only with static
 * geometry (Terrain and Non-Moving) while ignoring all dynamic bodies (Moving).
 * This results in a body that stays on the floor but cannot be hit or moved by
 * other dynamic objects.</p>
 *
 * @author xI-Mx-Ix
 */
public final class SpawnGhostBoxTest implements IVxTestCommand {

    private static short selectiveGhostLayer = -1;

    @Override
    public String getName() {
        return "spawnGhostBox";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("position", Vec3Argument.vec3(true))
                .then(Commands.argument("boxSize", FloatArgumentType.floatArg(0.1f))
                        .executes(this::execute)
                )
        );
    }

    /**
     * Executes the selective ghost box spawn logic.
     *
     * @param context The command context.
     * @return The number of bodies spawned.
     * @throws CommandSyntaxException if the command arguments are invalid.
     */
    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        net.minecraft.world.phys.Vec3 pos = Vec3Argument.getVec3(context, "position");
        float boxSize = FloatArgumentType.getFloat(context, "boxSize");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics world not found for this dimension."));
            return 0;
        }

        // All modifications to Jolt collision tables and BodyInterface must happen on the physics thread.
        physicsWorld.execute(() -> {
            try {
                // Initialize the selective ghost layer if it hasn't been claimed yet.
                if (selectiveGhostLayer == -1) {
                    selectiveGhostLayer = VxPhysicsLayers.claimLayer();

                    // Map it to the moving broad-phase layer as the spawned box is dynamic.
                    VxPhysicsLayers.setBroadPhaseMapping(selectiveGhostLayer, VxPhysicsLayers.BP_MOVING);

                    // Configure selective collisions:
                    // 1. Enable collision with the static floor/terrain.
                    VxPhysicsLayers.setCollision(selectiveGhostLayer, VxPhysicsLayers.TERRAIN, true);

                    // 2. Enable collision with static/kinematic bodies.
                    VxPhysicsLayers.setCollision(selectiveGhostLayer, VxPhysicsLayers.NON_MOVING, true);

                    // 3. Disable collision with dynamic moving objects (the 'ghost' behavior).
                    VxPhysicsLayers.setCollision(selectiveGhostLayer, VxPhysicsLayers.MOVING, false);

                    // 4. Disable collision with other objects on the same ghost layer.
                    VxPhysicsLayers.setCollision(selectiveGhostLayer, selectiveGhostLayer, false);
                }

                VxServerBodyManager manager = physicsWorld.getBodyManager();
                VxTransform transform = new VxTransform(new RVec3(pos.x, pos.y, pos.z), Quat.sIdentity());
                float halfExtent = boxSize / 2.0f;

                // Create the rigid body. Subclasses of VxRigidBody default to the MOVING layer.
                BoxRigidBody ghostBody = manager.createRigidBody(
                        VxRegisteredBodies.BOX,
                        transform,
                        body -> body.setHalfExtents(new Vec3(halfExtent, halfExtent, halfExtent))
                );

                if (ghostBody != null) {
                    // Update the Jolt body to use our claimed selective layer via the BodyInterface.
                    // This moves the body in the broad-phase and applies the new collision rules.
                    int joltId = ghostBody.getBodyId();
                    physicsWorld.getPhysicsSystem().getBodyInterface().setObjectLayer(joltId, selectiveGhostLayer);

                    source.sendSuccess(() -> Component.literal(
                            String.format("Spawned selective ghost box at %.2f, %.2f, %.2f. Layer: %d. It will stay on the floor but ignore dynamic objects.",
                                    pos.x, pos.y, pos.z, selectiveGhostLayer)), true);
                } else {
                    source.sendFailure(Component.literal("Failed to create ghost body."));
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("Error during selective ghost spawn: " + e.getMessage()));
            }
        });

        return 1;
    }
}