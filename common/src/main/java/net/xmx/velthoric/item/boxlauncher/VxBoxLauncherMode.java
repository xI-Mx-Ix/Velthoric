/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxlauncher;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.core.body.manager.VxBodyManager;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The logic implementation for the Box Launcher tool.
 * <p>
 * This mode allows players to spawn and launch box primitives into the physics world.
 * Configuration options allow controlling the rate of fire, launch speed, and box dimensions.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxLauncherMode extends VxToolMode {

    @Override
    public void registerProperties(VxToolConfig config) {
        // Define configurable properties for the UI
        config.addFloat("Speed", 40.0f, 1.0f, 200.0f);
        config.addInt("Count Per Tick", 5, 1, 50);
        config.addFloat("Min Size", 0.4f, 0.1f, 5.0f);
        config.addFloat("Max Size", 1.2f, 0.1f, 5.0f);
    }

    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        // Only execute logic if the primary button (Left Click) is being held
        if (state != ActionState.PRIMARY_ACTIVE) {
            return;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        // Retrieve current configuration values
        int count = config.getInt("Count Per Tick");
        float speed = config.getFloat("Speed");
        float minSize = config.getFloat("Min Size");
        float maxSize = config.getFloat("Max Size");

        // Execute physics operations on the physics thread
        physicsWorld.execute(() -> {
            for (int i = 0; i < count; i++) {
                spawnAndLaunchBox(player, physicsWorld, speed, minSize, maxSize);
            }
        });
    }

    /**
     * Spawns a single box and applies initial velocity.
     *
     * @param player       The player launching the box.
     * @param physicsWorld The physics world instance.
     * @param speed        The launch speed.
     * @param minSize      The minimum box dimension.
     * @param maxSize      The maximum box dimension.
     */
    private void spawnAndLaunchBox(ServerPlayer player, VxPhysicsWorld physicsWorld, float speed, float minSize, float maxSize) {
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        var random = ThreadLocalRandom.current();

        // Calculate spawn position slightly in front of the player
        net.minecraft.world.phys.Vec3 spawnPosMc = eyePos.add(lookVec.scale(1.5f));

        VxTransform transform = new VxTransform(
                new RVec3((float) spawnPosMc.x, (float) spawnPosMc.y, (float) spawnPosMc.z),
                Quat.sIdentity()
        );

        // Generate random dimensions
        float randomWidth = random.nextFloat(minSize, maxSize);
        float randomHeight = random.nextFloat(minSize, maxSize);
        float randomDepth = random.nextFloat(minSize, maxSize);

        Vec3 halfExtents = new Vec3(randomWidth / 2.0f, randomHeight / 2.0f, randomDepth / 2.0f);

        // Calculate velocity vector
        Vec3 launchVelocity = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);
        launchVelocity.scaleInPlace(speed);

        VxBodyManager manager = physicsWorld.getBodyManager();

        // Create the rigid body
        BoxRigidBody spawnedBody = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform,
                box -> {
                    box.setHalfExtents(halfExtents);
                    box.setColor(BoxColor.getRandom());
                }
        );

        // Activate and propel the body
        if (spawnedBody != null) {
            var bodyId = spawnedBody.getBodyId();
            var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();

            bodyInterface.activateBody(bodyId);
            bodyInterface.setLinearVelocity(bodyId, launchVelocity);
        }
    }
}