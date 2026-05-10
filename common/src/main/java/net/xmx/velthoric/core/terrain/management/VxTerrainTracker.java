/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.management;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.VxTerrainSystem;
import net.xmx.velthoric.jni.TerrainSystem;
import net.xmx.velthoric.jni.TerrainTracker;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tracks physics bodies using a high-performance native approach.
 * <p>
 * This system serves as the Java-side orchestrator for the native C++ {@link TerrainTracker}.
 * It initializes the native tracking component, forwards the update loop calls, and processes
 * the results to schedule voxel data submissions to the physics engine.
 * By delegating the spatial clustering and bounds intersection to C++, this class ensures
 * zero garbage collection overhead and significantly reduces the CPU cycle cost of terrain tracking.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainTracker {

    /**
     * The physics world containing the bodies to track. 
     * This world provides access to the underlying Jolt physics system and the {@link VxTerrainSystem}.
     */
    private final VxPhysicsWorld physicsWorld;

    /**
     * The native object wrapper to the C++ TerrainTracker.
     * This instance holds the native pointer (address) to the C++ tracking structure.
     */
    private TerrainTracker nativeTracker;

    /**
     * Pre-allocated DirectByteBuffer for JNI to share memory natively with C++.
     * Can hold up to 40,000 chunk positions encoded as packed 64-bit longs (320 KB).
     * Using direct buffers eliminates JNI array copy overhead completely.
     */
    private final ByteBuffer initialChunksBuffer;

    /**
     * Pre-allocated DirectByteBuffer for JNI to share memory natively with C++.
     * Can hold up to 40,000 chunk positions encoded as packed 64-bit longs (320 KB).
     * Using direct buffers eliminates JNI array copy overhead completely.
     */
    private final ByteBuffer updateChunksBuffer;

    /**
     * Constructs a new VxTerrainTracker and initializes the native counterpart.
     *
     * @param physicsWorld The physics world containing the bodies to track. Must not be null.
     * @param level        The server level in which the tracking occurs. Must not be null.
     * @param nativeSystem The native C++ TerrainSystem for all chunk operations. Must be initialized and valid.
     */
    public VxTerrainTracker(VxPhysicsWorld physicsWorld, ServerLevel level, TerrainSystem nativeSystem) {
        this.physicsWorld = physicsWorld;

        long psVa = physicsWorld.getPhysicsSystem().va();
        long tsVa = nativeSystem.va();
        
        this.nativeTracker = new TerrainTracker(psVa, tsVa, VxPhysicsLayers.TERRAIN);
        
        this.initialChunksBuffer = ByteBuffer.allocateDirect(40000 * 8).order(ByteOrder.nativeOrder());
        this.updateChunksBuffer = ByteBuffer.allocateDirect(40000 * 8).order(ByteOrder.nativeOrder());
    }

    /**
     * Performs a single update tick, delegating the complex clustering algorithms to the native C++ tracker.
     * <p>
     * This method retrieves two sets of bit-packed long coordinates from the native layer:
     * 1. Initial chunks: Chunks that were just discovered and require full terrain generation data.
     * 2. Update chunks: Existing chunks that need priority data transmission or state updates.
     * It then schedules the data submission asynchronously via the {@link VxTerrainSystem}.
     * </p>
     */
    public void update() {
        if (this.nativeTracker == null) {
            return;
        }

        // The native call packs the counts of both arrays into a single 64-bit long
        // High 32 bits = initialCount, Low 32 bits = updateCount
        long counts = this.nativeTracker.update(this.initialChunksBuffer, this.updateChunksBuffer);
        
        int initialCount = (int) (counts >>> 32);
        int updateCount = (int) counts;

        VxTerrainSystem terrainSys = this.physicsWorld.getTerrainSystem();

        // Process newly requested chunks that require complete initial build data
        for (int i = 0; i < initialCount; i++) {
            long packedPos = this.initialChunksBuffer.getLong(i * 8);
            terrainSys.scheduleChunkDataSubmission(packedPos, true);
        }

        // Process chunks that need to be prioritized or updated
        for (int i = 0; i < updateCount; i++) {
            long packedPos = this.updateChunksBuffer.getLong(i * 8);
            terrainSys.scheduleChunkDataSubmission(packedPos, false);
        }
    }

    /**
     * Clears all tracking data and releases all held chunks natively.
     * This method should be called during system shutdown to ensure all native memory
     * is safely freed and no dangling physics bodies remain.
     */
    public void clear() {
        if (this.nativeTracker != null) {
            this.nativeTracker.clear();
            this.nativeTracker.close();
            this.nativeTracker = null;
        }
    }
}