/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.HashSet;
import java.util.Set;

/**
 * Proactively manages terrain by preparing an area around tracked physics objects and players.
 * It uses a "keep-alive" system by refreshing the life counter of required chunks,
 * ensuring that terrain is loaded before it's needed and unloaded when it's not.
 * This approach is crucial for a smooth experience, especially for players.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;

    // The radius in chunks to preload around tracked entities.
    private static final int PRELOAD_RADIUS_CHUNKS = 4;
    // The time in seconds to predict the future position of entities for preloading.
    private static final float PREDICTION_SECONDS = 0.75f;
    // Minecraft ticks per second, used for velocity conversion.
    private static final float TICKS_PER_SECOND = 20.0f;

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
    }

    /**
     * Main update method for the tracker. It calculates the required terrain area
     * around all dynamic physics objects and all active players.
     */
    public void update() {
        // Use a single set to collect all unique chunk sections required for this tick.
        // This prevents redundant processing for overlapping areas from multiple objects/players.
        Set<Long> requiredChunks = new HashSet<>(4096);

        // Step 1: Track all generic physics objects.
        var allObjects = physicsWorld.getObjectManager().getAllObjects();
        for (VxBody obj : allObjects) {
            ConstBody body = obj.getConstBody();
            if (body != null && body.isRigidBody()) {
                collectChunksForBody(body, requiredChunks);
            }
        }

        // Step 2: Explicitly track all players, as they are the most critical entities.
        var players = physicsWorld.getLevel().players();
        for (ServerPlayer player : players) {
            collectChunksForPlayer(player, requiredChunks);
        }

        // Step 3: Refresh the life counter for each required chunk section.
        // This acts as a "keep-alive" signal, preventing them from being unloaded.
        if (!requiredChunks.isEmpty()) {
            for (long packedPos : requiredChunks) {
                terrainManager.requestChunk(packedPos);
            }
        }
    }

    /**
     * Calculates required chunk sections for a Jolt physics body.
     *
     * @param body The physics body to track.
     * @param outChunks The set to which the packed chunk section positions will be added.
     */
    private void collectChunksForBody(ConstBody body, Set<Long> outChunks) {
        ConstAaBox currentBounds = body.getWorldSpaceBounds();
        float velX = body.getLinearVelocity().getX();
        float velY = body.getLinearVelocity().getY();
        float velZ = body.getLinearVelocity().getZ();

        calculateAndAddRequiredChunks(
                currentBounds.getMin().getX(), currentBounds.getMin().getY(), currentBounds.getMin().getZ(),
                currentBounds.getMax().getX(), currentBounds.getMax().getY(), currentBounds.getMax().getZ(),
                velX, velY, velZ, outChunks
        );
    }

    /**
     * Calculates required chunk sections for a Minecraft player.
     *
     * @param player The player to track.
     * @param outChunks The set to which the packed chunk section positions will be added.
     */
    private void collectChunksForPlayer(ServerPlayer player, Set<Long> outChunks) {
        AABB currentBounds = player.getBoundingBox();
        Vec3 velocity = player.getDeltaMovement(); // Velocity in meters per tick

        // Convert velocity from meters/tick to meters/second for prediction calculation.
        float velX = (float) velocity.x * TICKS_PER_SECOND;
        float velY = (float) velocity.y * TICKS_PER_SECOND;
        float velZ = (float) velocity.z * TICKS_PER_SECOND;

        calculateAndAddRequiredChunks(
                currentBounds.minX, currentBounds.minY, currentBounds.minZ,
                currentBounds.maxX, currentBounds.maxY, currentBounds.maxZ,
                velX, velY, velZ, outChunks
        );
    }

    /**
     * Generic method to calculate the required chunks based on an AABB and velocity.
     * It predicts the future position and creates a combined bounding box to ensure
     * a seamless area of loaded terrain around the entity.
     *
     * @param minX The minimum X coordinate of the current AABB.
     * @param minY The minimum Y coordinate of the current AABB.
     * @param minZ The minimum Z coordinate of the current AABB.
     * @param maxX The maximum X coordinate of the current AABB.
     * @param maxY The maximum Y coordinate of the current AABB.
     * @param maxZ The maximum Z coordinate of the current AABB.
     * @param velX The linear velocity on the X axis in meters/second.
     * @param velY The linear velocity on the Y axis in meters/second.
     * @param velZ The linear velocity on the Z axis in meters/second.
     * @param outChunks The set to which the packed chunk section positions will be added.
     */
    private void calculateAndAddRequiredChunks(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                               float velX, float velY, float velZ, Set<Long> outChunks) {
        // Predict future position to proactively load chunks in the direction of movement.
        double predMinX = minX + velX * PREDICTION_SECONDS;
        double predMinY = minY + velY * PREDICTION_SECONDS;
        double predMinZ = minZ + velZ * PREDICTION_SECONDS;
        double predMaxX = maxX + velX * PREDICTION_SECONDS;
        double predMaxY = maxY + velY * PREDICTION_SECONDS;
        double predMaxZ = maxZ + velZ * PREDICTION_SECONDS;

        // Create a combined bounding box that encompasses both current and predicted positions.
        double combinedMinX = Math.min(minX, predMinX);
        double combinedMinY = Math.min(minY, predMinY);
        double combinedMinZ = Math.min(minZ, predMinZ);
        double combinedMaxX = Math.max(maxX, predMaxX);
        double combinedMaxY = Math.max(maxY, predMaxY);
        double combinedMaxZ = Math.max(maxZ, predMaxZ);

        // Add all chunk sections that overlap with the combined and expanded area.
        addChunksForBounds(combinedMinX, combinedMinY, combinedMinZ, combinedMaxX, combinedMaxY, combinedMaxZ, outChunks);
    }


    /**
     * Iterates over a given world-space AABB, expands it by the preload radius,
     * and adds all overlapping chunk sections to the output set.
     *
     * @param minX The minimum X coordinate of the bounds.
     * @param minY The minimum Y coordinate of the bounds.
     * @param minZ The minimum Z coordinate of the bounds.
     * @param maxX The maximum X coordinate of the bounds.
     * @param maxY The maximum Y coordinate of the bounds.
     * @param maxZ The maximum Z coordinate of the bounds.
     * @param outChunks The set to which the packed chunk section positions will be added.
     */
    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Set<Long> outChunks) {
        int minSectionX = ((int) Math.floor(minX) >> 4) - PRELOAD_RADIUS_CHUNKS;
        int minSectionY = ((int) Math.floor(minY) >> 4) - PRELOAD_RADIUS_CHUNKS;
        int minSectionZ = ((int) Math.floor(minZ) >> 4) - PRELOAD_RADIUS_CHUNKS;
        int maxSectionX = ((int) Math.floor(maxX) >> 4) + PRELOAD_RADIUS_CHUNKS;
        int maxSectionY = ((int) Math.floor(maxY) >> 4) + PRELOAD_RADIUS_CHUNKS;
        int maxSectionZ = ((int) Math.floor(maxZ) >> 4) + PRELOAD_RADIUS_CHUNKS;

        final int worldMinY = physicsWorld.getLevel().getMinBuildHeight() >> 4;
        final int worldMaxY = physicsWorld.getLevel().getMaxBuildHeight() >> 4;

        for (int y = minSectionY; y <= maxSectionY; ++y) {
            // Clamp the vertical range to valid world build heights.
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSectionZ; z <= maxSectionZ; ++z) {
                for (int x = minSectionX; x <= maxSectionX; ++x) {
                    outChunks.add(VxSectionPos.pack(x, y, z));
                }
            }
        }
    }
}