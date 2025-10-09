/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxUpdateContext;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Proactively manages terrain by preparing an area around tracked physics objects.
 * It uses a "keep-alive" system by refreshing the life counter of required chunks,
 * ensuring that terrain is loaded before it's needed and unloaded when it's not.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;

    private static final int PRELOAD_RADIUS_CHUNKS = 4; // Increased radius for smoother experience
    private static final float PREDICTION_SECONDS = 0.75f;

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
    }

    /**
     * Main update method for the tracker. Prepares the area around all physics objects.
     */
    public void update() {
        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        if (currentObjects.isEmpty()) {
            return;
        }

        VxUpdateContext ctx = updateContext.get();
        Set<Long> requiredChunks = ctx.requiredChunksSet;
        requiredChunks.clear();

        // Step 1: Calculate the total set of required chunks for all objects.
        for (VxBody obj : currentObjects) {
            ConstBody body = obj.getConstBody();
            if (body != null && body.isRigidBody()) { // Soft bodies might not need terrain tracking
                ConstAaBox bounds = body.getWorldSpaceBounds();
                calculateRequiredChunks(bounds.getMin().getX(), bounds.getMin().getY(), bounds.getMin().getZ(),
                        bounds.getMax().getX(), bounds.getMax().getY(), bounds.getMax().getZ(),
                        body.getLinearVelocity().getX(), body.getLinearVelocity().getY(), body.getLinearVelocity().getZ(),
                        PRELOAD_RADIUS_CHUNKS, requiredChunks);
            }
        }

        // Step 2: Refresh the life counter for each required chunk.
        // This acts as a "keep-alive" signal.
        for (long packedPos : requiredChunks) {
            terrainManager.requestChunk(packedPos);
        }
    }

    private void calculateRequiredChunks(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                         float velX, float velY, float velZ, int radius, Set<Long> outChunks) {
        double predMinX = minX + velX * PREDICTION_SECONDS;
        double predMinY = minY + velY * PREDICTION_SECONDS;
        double predMinZ = minZ + velZ * PREDICTION_SECONDS;
        double predMaxX = maxX + velX * PREDICTION_SECONDS;
        double predMaxY = maxY + velY * PREDICTION_SECONDS;
        double predMaxZ = maxZ + velZ * PREDICTION_SECONDS;

        double combinedMinX = Math.min(minX, predMinX);
        double combinedMinY = Math.min(minY, predMinY);
        double combinedMinZ = Math.min(minZ, predMinZ);
        double combinedMaxX = Math.max(maxX, predMaxX);
        double combinedMaxY = Math.max(maxY, predMaxY);
        double combinedMaxZ = Math.max(maxZ, predMaxZ);

        addChunksForBounds(combinedMinX, combinedMinY, combinedMinZ, combinedMaxX, combinedMaxY, combinedMaxZ, radius, outChunks);
    }

    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<Long> outChunks) {
        int minSectionX = ((int) Math.floor(minX) >> 4) - radiusInChunks;
        int minSectionY = ((int) Math.floor(minY) >> 4) - radiusInChunks;
        int minSectionZ = ((int) Math.floor(minZ) >> 4) - radiusInChunks;
        int maxSectionX = ((int) Math.floor(maxX) >> 4) + radiusInChunks;
        int maxSectionY = ((int) Math.floor(maxY) >> 4) + radiusInChunks;
        int maxSectionZ = ((int) Math.floor(maxZ) >> 4) + radiusInChunks;

        final int worldMinY = physicsWorld.getLevel().getMinBuildHeight() >> 4;
        final int worldMaxY = physicsWorld.getLevel().getMaxBuildHeight() >> 4;

        for (int y = minSectionY; y <= maxSectionY; ++y) {
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSectionZ; z <= maxSectionZ; ++z) {
                for (int x = minSectionX; x <= maxSectionX; ++x) {
                    outChunks.add(VxSectionPos.pack(x, y, z));
                }
            }
        }
    }
}