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
 * Jolt Physics engine. It calculates the exact submerged volume of complex shapes
 * (Convex Hulls, Meshes, Compounds) and applies the resulting buoyancy and drag forces.
 * <p>
 * <b>Optimization:</b> It uses {@link BodyLockMultiWrite} to process bodies in batches of 128.
 * Furthermore, it hoists ThreadLocal resource retrieval out of the inner loop to ensure
 * zero-allocation during the physics simulation step.
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
     * <p>
     * This method is called during the physics simulation step. It retrieves the data populated
     * by the broad-phase and injects it into the Jolt physics engine.
     *
     * @param lockInterface The no-lock interface used to retrieve body pointers.
     * @param deltaTime     The simulation time step.
     * @param dataStore     The data store containing all information about buoyant bodies.
     */
    public void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, VxBuoyancyDataStore dataStore) {
        int totalCount = dataStore.getCount();

        // Retrieve ThreadLocal buffers once per frame, NOT per body.
        // Calling .get() on a ThreadLocal inside a hot loop (thousands of iterations) adds
        // measurable overhead. By hoisting it here, we get raw object references.
        int[] batchIds = tempBatchIds.get();
        RVec3 surfacePosition = tempSurfacePos.get();
        Vec3 surfaceNormal = tempSurfaceNormal.get();
        Vec3 fluidVelocity = tempFluidVelocity.get();

        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        // Process bodies in chunks of BATCH_SIZE to reduce Locking overhead.
        // This is the primary optimization for high body counts (locking individually is slow).
        for (int baseIndex = 0; baseIndex < totalCount; baseIndex += BATCH_SIZE) {
            int currentBatchCount = Math.min(BATCH_SIZE, totalCount - baseIndex);

            // Copy IDs to the temporary batch array for the MultiLock.
            System.arraycopy(dataStore.bodyIds, baseIndex, batchIds, 0, currentBatchCount);

            // Create a single multi-lock for the entire batch.
            // Using try-with-resources ensures the lock is released even if an exception occurs.
            try (BodyLockMultiWrite lock = new BodyLockMultiWrite(lockInterface, batchIds)) {

                // Iterate through the results in the batch.
                for (int i = 0; i < currentBatchCount; i++) {
                    Body body = lock.getBody(i);

                    // If the body pointer is null, the body ID was invalid or the body was removed.
                    // If !isActive, the body is sleeping, and we shouldn't wake it up unnecessarily.
                    if (body != null && body.isActive()) {
                        int globalIndex = baseIndex + i;

                        applyNativeBuoyancy(
                                body,
                                deltaTime,
                                globalIndex,
                                dataStore,
                                gravity,
                                surfacePosition, // Pass reused objects
                                surfaceNormal,
                                fluidVelocity
                        );
                    }
                }
            }
        }
    }

    /**
     * Configures the parameters and invokes the native C++ buoyancy calculation directly on the Body.
     * <p>
     * This method populates the reused vector objects with data from the {@link VxBuoyancyDataStore}
     * and calls the JNI method.
     *
     * @param body            The physics body to apply forces to.
     * @param deltaTime       The time step.
     * @param index           The index in the data store (Structure of Arrays).
     * @param dataStore       The data source.
     * @param gravity         The world gravity vector.
     * @param surfacePosition Reused RVec3 container for surface position.
     * @param surfaceNormal   Reused Vec3 container for surface normal.
     * @param fluidVelocity   Reused Vec3 container for fluid velocity.
     */
    private void applyNativeBuoyancy(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore, Vec3 gravity,
                                     RVec3 surfacePosition, Vec3 surfaceNormal, Vec3 fluidVelocity) {

        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];

        // Update mutable vectors with data for this specific body.
        surfacePosition.set(dataStore.waterCenterX[index], surfaceY, dataStore.waterCenterZ[index]);

        // Assuming a flat water surface with Normal UP (0, 1, 0).
        // For standard Minecraft water, this is sufficient.
        surfaceNormal.set(0f, 1f, 0f);

        // Populate fluid velocity from the DataStore.
        // This vector represents the current/flow of the water affecting the body.
        fluidVelocity.set(dataStore.flowX[index], dataStore.flowY[index], dataStore.flowZ[index]);

        // Determine physical constants based on fluid type.
        // Buoyancy factor: >1.0 makes objects float higher, 1.0 is neutral.
        final float buoyancyFactor;
        final float linearDrag;
        final float angularDrag;

        if (fluidType == VxFluidType.LAVA) {
            // Lava is denser and more viscous than water.
            buoyancyFactor = 2.5f;
            linearDrag = 5.0f;
            angularDrag = 2.0f;
        } else {
            // Standard water properties.
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