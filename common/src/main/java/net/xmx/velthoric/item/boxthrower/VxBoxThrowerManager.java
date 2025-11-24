/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxthrower;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the mechanics of the Box Thrower item.
 * <p>
 * This singleton class tracks players who are currently using the item and handles
 * the rapid-fire spawning and launching of physics-enabled box entities.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxThrowerManager {

    private static final VxBoxThrowerManager INSTANCE = new VxBoxThrowerManager();

    // Thread-safe map to track which players are currently holding down the use button.
    private final Map<UUID, Boolean> shootingPlayers = new ConcurrentHashMap<>();

    // Configuration constants for the throwing mechanic
    private static final int BOXES_PER_TICK = 5;    // Number of boxes spawned per server tick
    private static final float SHOOT_SPEED = 40f;   // Initial velocity magnitude
    private static final float SPAWN_OFFSET = 1.5f; // Distance from player eyes to spawn point
    private static final float MIN_BOX_SIZE = 0.4f; // Minimum random box dimension
    private static final float MAX_BOX_SIZE = 1.2f; // Maximum random box dimension

    private VxBoxThrowerManager() {}

    /**
     * Gets the singleton instance of the manager.
     *
     * @return The instance.
     */
    public static VxBoxThrowerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a player to start shooting boxes.
     * Called when the player starts using the item.
     *
     * @param player The player initiating the action.
     */
    public void startShooting(ServerPlayer player) {
        shootingPlayers.put(player.getUUID(), true);
    }

    /**
     * Stops a player from shooting boxes.
     * Called when the player stops using the item or disconnects.
     *
     * @param player The player stopping the action.
     */
    public void stopShooting(ServerPlayer player) {
        shootingPlayers.remove(player.getUUID());
    }

    /**
     * Checks if a specific player is currently in the shooting state.
     *
     * @param player The player to check.
     * @return True if the player is currently shooting.
     */
    public boolean isShooting(ServerPlayer player) {
        return shootingPlayers.getOrDefault(player.getUUID(), false);
    }

    /**
     * Processes the shooting logic for a specific player.
     * <p>
     * This should be called every server tick for players who are active in the {@code shootingPlayers} map.
     * It schedules the physics body creation tasks on the physics thread.
     *
     * @param player The player to process.
     */
    public void serverTick(ServerPlayer player) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.level().dimension());

        // Safety check: if physics aren't running, stop the action to prevent errors
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            stopShooting(player);
            return;
        }

        // Execute the spawning logic on the physics thread to ensure thread safety with Jolt
        physicsWorld.execute(() -> {
            for (int i = 0; i < BOXES_PER_TICK; i++) {
                spawnAndLaunchSingleBox(player, physicsWorld);
            }
        });
    }

    /**
     * Spawns a single box entity and launches it in the direction the player is looking.
     *
     * @param player       The player throwing the box.
     * @param physicsWorld The physics world instance.
     */
    private void spawnAndLaunchSingleBox(ServerPlayer player, VxPhysicsWorld physicsWorld) {
        // Calculate spawn position based on player's eye position and look vector
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        var random = ThreadLocalRandom.current();

        // Offset the spawn point so the box appears in front of the player, not inside their head
        net.minecraft.world.phys.Vec3 spawnPosMc = eyePos.add(lookVec.scale(SPAWN_OFFSET));

        // Create the initial transform (Position + Identity Rotation)
        VxTransform transform = new VxTransform(
                new RVec3((float)spawnPosMc.x, (float)spawnPosMc.y, (float)spawnPosMc.z),
                Quat.sIdentity()
        );

        // Generate random dimensions for the box
        float randomWidth = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);
        float randomHeight = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);
        float randomDepth = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);

        Vec3 halfExtents = new Vec3(randomWidth / 2.0f, randomHeight / 2.0f, randomDepth / 2.0f);

        // Convert Minecraft look vector to Jolt velocity vector
        Vec3 launchVelocity = new Vec3(
                (float) lookVec.x,
                (float) lookVec.y,
                (float) lookVec.z
        );
        launchVelocity.scaleInPlace(SHOOT_SPEED);

        VxBodyManager manager = physicsWorld.getBodyManager();

        // Create the rigid body via the manager
        BoxRigidBody spawnedBody = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform,
                box -> {
                    box.setHalfExtents(halfExtents);
                    box.setColor(BoxColor.getRandom());
                }
        );

        // If creation was successful, activate the body and apply the velocity
        if (spawnedBody != null) {
            var bodyId = spawnedBody.getBodyId();
            var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();

            bodyInterface.activateBody(bodyId);
            bodyInterface.setLinearVelocity(bodyId, launchVelocity);
        }
    }
}