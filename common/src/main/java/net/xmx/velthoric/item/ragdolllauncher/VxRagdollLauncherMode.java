/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.ragdolllauncher;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.physics.ragdoll.VxRagdollManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * The logic implementation for the Ragdoll Launcher tool.
 * <p>
 * This mode spawns complex humanoid ragdoll structures and launches them
 * directly towards the point the player is looking at.
 *
 * @author xI-Mx-Ix
 */
public class VxRagdollLauncherMode extends VxToolMode {

    @Override
    public void registerProperties(VxToolConfig config) {
        config.addFloat("Launch Speed", 30.0f, 1.0f, 100.0f);
        config.addInt("Count Per Tick", 1, 1, 5);
    }

    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        // Execute launch logic only when the primary action is active
        if (state != ActionState.PRIMARY_ACTIVE) {
            return;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        float speed = config.getFloat("Launch Speed");
        int count = config.getInt("Count Per Tick");

        physicsWorld.execute(() -> {
            // Instantiate the manager to handle physics body creation
            VxRagdollManager ragdollManager = new VxRagdollManager(physicsWorld);

            for (int i = 0; i < count; i++) {
                spawnAndLaunchRagdoll(player, ragdollManager, speed);
            }
        });
    }

    /**
     * Calculates the exact spawn position and velocity vector based on the player's
     * camera orientation and delegates the creation to the manager.
     *
     * @param player         The player launching the ragdoll.
     * @param ragdollManager The manager handling ragdoll creation.
     * @param speed          The magnitude of the launch velocity.
     */
    private void spawnAndLaunchRagdoll(ServerPlayer player, VxRagdollManager ragdollManager, float speed) {
        // Retrieve the precise eye position and look vector from the player
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();

        // Calculate a spawn position slightly in front of the camera to prevent self-collision.
        // Scaling the look vector ensures the spawn point aligns with the crosshair.
        net.minecraft.world.phys.Vec3 spawnPosMc = eyePos.add(lookVec.scale(2.0f));

        // Convert Minecraft coordinates to Jolt physics coordinates (Double precision)
        RVec3 joltSpawnPos = new RVec3((float) spawnPosMc.x, (float) spawnPosMc.y, (float) spawnPosMc.z);

        // create the velocity vector directly from the look vector.
        // This ensures the ragdoll travels in the exact direction the camera is facing (including pitch).
        Vec3 joltVelocity = new Vec3(
                (float) lookVec.x,
                (float) lookVec.y,
                (float) lookVec.z
        );
        joltVelocity.scaleInPlace(speed);

        // Launch the ragdoll with the calculated position and velocity
        ragdollManager.launchHumanoidRagdoll(player, joltSpawnPos, joltVelocity);
    }
}