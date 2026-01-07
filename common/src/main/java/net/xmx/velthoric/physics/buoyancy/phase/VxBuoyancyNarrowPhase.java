/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.phase;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * <p>
 * This implementation delegates the heavy geometric lifting directly to the native
 * Jolt Physics engine.
 * <p>
 * It uses {@link BodyLockMultiWrite} to process bodies in batches of 128. This drastically
 * reduces the JNI overhead associated with locking and unlocking bodies individually,
 * which is critical for maintaining high performance with 1000+ bodies.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private static final int BATCH_SIZE = 128;

    private final VxPhysicsWorld physicsWorld;

    // --- Thread-Local Temporaries to avoid allocation ---
    // These objects are reused every frame to prevent GC pressure.
    private final ThreadLocal<int[]> tempBatchIds = ThreadLocal.withInitial(() -> new int[BATCH_SIZE]);
    private final ThreadLocal<RVec3> tempSurfacePos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Vec3> tempSurfaceNormal = ThreadLocal.withInitial(() -> new Vec3(0, 1, 0));
    private final ThreadLocal<Vec3> tempFluidVelocity = ThreadLocal.withInitial(Vec3::new);

    /**
     * Constructs a new narrow-phase handler.
     *
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyNarrowPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    /**
     * Iterates through the locked bodies and applies buoyancy forces using the native Jolt implementation.
     *
     * @param lockInterface The no-lock interface used to retrieve body pointers.
     * @param deltaTime     The simulation time step.
     * @param dataStore     The data store containing all information about buoyant bodies.
     */
    public void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, VxBuoyancyDataStore dataStore) {
        int totalCount = dataStore.getCount();
        int[] batchIds = tempBatchIds.get();

        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        // Process bodies in chunks of BATCH_SIZE to reduce Locking overhead.
        // This is the primary optimization for high body counts.
        for (int baseIndex = 0; baseIndex < totalCount; baseIndex += BATCH_SIZE) {
            int currentBatchCount = Math.min(BATCH_SIZE, totalCount - baseIndex);

            // Copy IDs to the temporary batch array
            System.arraycopy(dataStore.bodyIds, baseIndex, batchIds, 0, currentBatchCount);

            // Create a single multi-lock for the entire batch.
            try (BodyLockMultiWrite lock = new BodyLockMultiWrite(lockInterface, batchIds)) {

                // Iterate through the results in the batch
                for (int i = 0; i < currentBatchCount; i++) {
                    Body body = lock.getBody(i);

                    // If the body pointer is null, the body ID was invalid or inactive.
                    if (body != null && body.isActive()) {
                        int globalIndex = baseIndex + i;
                        applyNativeBuoyancy(body, deltaTime, globalIndex, dataStore, gravity);
                    }
                }
            }
        }
    }

    /**
     * Configures the parameters and invokes the native C++ buoyancy calculation directly on the Body.
     *
     * @param body      The physics body to apply forces to.
     * @param deltaTime The time step.
     * @param index     The index in the data store.
     * @param dataStore The data source.
     * @param gravity   The world gravity vector.
     */
    private void applyNativeBuoyancy(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore, Vec3 gravity) {
        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];

        // Construct the water surface position for this specific body based on broadphase data.
        RVec3 surfacePosition = tempSurfacePos.get();
        surfacePosition.set(dataStore.waterCenterX[index], surfaceY, dataStore.waterCenterZ[index]);

        // Assuming a flat water surface with Normal UP (0, 1, 0).
        Vec3 surfaceNormal = tempSurfaceNormal.get();
        surfaceNormal.set(0f, 1f, 0f);

        // Fluid velocity (stationary for now).
        Vec3 fluidVelocity = tempFluidVelocity.get();
        fluidVelocity.set(0f, 0f, 0f);

        // Determine physical constants based on fluid type.
        // Buoyancy factor: >1.0 makes objects float higher, 1.0 is neutral.
        final float buoyancyFactor;
        final float linearDrag;
        final float angularDrag;

        if (fluidType == VxFluidType.LAVA) {
            buoyancyFactor = 2.5f;
            linearDrag = 5.0f;
            angularDrag = 2.0f;
        } else {
            buoyancyFactor = 1.1f;
            linearDrag = 0.5f;
            angularDrag = 0.05f;
        }

        // Call the native Jolt method directly.
        // This calculates the exact submerged volume of the shape (including compounds) in C++,
        // effectively eliminating Java-side math overhead and object allocations.
        body.applyBuoyancyImpulse(
                surfacePosition,
                surfaceNormal,
                buoyancyFactor,
                linearDrag,
                angularDrag,
                fluidVelocity,
                gravity,
                deltaTime
        );
    }
}