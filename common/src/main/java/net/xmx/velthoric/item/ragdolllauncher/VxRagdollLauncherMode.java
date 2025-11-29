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
 * in the direction the player is looking.
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
        // Only launch on primary fire
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
            // Create a manager instance for this batch
            VxRagdollManager ragdollManager = new VxRagdollManager(physicsWorld);

            for (int i = 0; i < count; i++) {
                spawnAndLaunchRagdoll(player, ragdollManager, speed);
            }
        });
    }

    /**
     * Spawns a single ragdoll and applies velocity.
     *
     * @param player         The player launching the ragdoll.
     * @param ragdollManager The manager handling ragdoll creation.
     * @param speed          The speed to launch at.
     */
    private void spawnAndLaunchRagdoll(ServerPlayer player, VxRagdollManager ragdollManager, float speed) {
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();

        // Calculate spawn position
        net.minecraft.world.phys.Vec3 spawnPosMc = eyePos.add(lookVec.scale(2.0f));
        RVec3 joltSpawnPos = new RVec3((float) spawnPosMc.x, (float) spawnPosMc.y, (float) spawnPosMc.z);

        // Calculate velocity vector
        Vec3 joltVelocity = new Vec3(
                (float) lookVec.x,
                (float) lookVec.y,
                (float) lookVec.z
        );
        joltVelocity.scaleInPlace(speed);

        // Delegate the actual creation to the ragdoll manager
        ragdollManager.launchHumanoidRagdoll(player, joltSpawnPos, joltVelocity);
    }
}