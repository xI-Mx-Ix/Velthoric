/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.buoyancy.phase;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import net.xmx.velthoric.core.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.core.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * <p>
 * This implementation delegates the heavy geometric lifting directly to the native
 * Jolt Physics engine. It calculates the exact submerged volume of complex shapes
 * (Convex Hulls, Meshes, Compounds) and applies the resulting buoyancy and drag forces.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    /**
     * Number of bodies processed in a single JNI batch operation.
     */
    private static final int BATCH_SIZE = 512;

    /**
     * The physics world instance containing the simulation context.
     */
    private final VxPhysicsWorld physicsWorld;

    /**
     * Reusable native array for collecting body IDs for batching.
     * This prevents per-batch allocations and reduces garbage collector pressure.
     */
    private final ThreadLocal<BodyIdArray> batchBodyIds = ThreadLocal.withInitial(() -> new BodyIdArray(BATCH_SIZE));

    /**
     * Reusable mutable vector used to store the fluid surface position for buoyancy calculations.
     * Thread-local to ensure safety during multi-threaded physics ticks.
     */
    private final ThreadLocal<RVec3> tempSurfacePos = ThreadLocal.withInitial(RVec3::new);

    /**
     * Reusable mutable vector used to store the fluid surface normal.
     * Standard fluid surfaces in Minecraft utilize an upward normal (0, 1, 0).
     */
    private final ThreadLocal<Vec3> tempSurfaceNormal = ThreadLocal.withInitial(() -> new Vec3(0, 1, 0));

    /**
     * Reusable mutable vector used to store the fluid flow velocity at a specific body position.
     */
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
     * Iterates through the bodies and applies buoyancy forces using batch write locks.
     * <p>
     * This method retrieves the environmental data populated by the broad-phase and uses the
     * native Jolt Physics engine to calculate exact submerged volumes and drag forces.
     *
     * @param lockInterface The locking interface used to acquire body locks.
     * @param deltaTime     The simulation time step.
     * @param dataStore     The data store containing all information about buoyant bodies.
     */
    public void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, VxBuoyancyDataStore dataStore) {
        final int totalCount = dataStore.getCount();
        if (totalCount == 0) return;

        final BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterfaceNoLock();
        final Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        // Retrieve ThreadLocal buffers once per frame to avoid allocation overhead in the loop.
        final RVec3 surfacePosition = tempSurfacePos.get();
        final Vec3 surfaceNormal = tempSurfaceNormal.get();
        final Vec3 fluidVelocity = tempFluidVelocity.get();
        final BodyIdArray localBatchIds = batchBodyIds.get();

        for (int batchStart = 0; batchStart < totalCount; batchStart += BATCH_SIZE) {
            int currentBatchCount = Math.min(BATCH_SIZE, totalCount - batchStart);

            BodyIdArray currentIds;
            if (currentBatchCount == BATCH_SIZE) {
                currentIds = localBatchIds;
            } else {
                // Create a temporary array for the tail batch to ensure the lock only covers valid IDs.
                currentIds = new BodyIdArray(currentBatchCount);
            }

            for (int b = 0; b < currentBatchCount; b++) {
                currentIds.set(b, dataStore.bodyIds[batchStart + b]);
            }

            // Acquire batch write locks to avoid per-body allocation of lock objects.
            try (BodyLockMultiWrite multiLock = new BodyLockMultiWrite(lockInterface, currentIds)) {
                Body[] lockedBodies = multiLock.getBodies();

                for (int b = 0; b < currentBatchCount; b++) {
                    Body body = lockedBodies[b];
                    int dataIndex = batchStart + b;

                    // Apply forces only if the body was successfully locked and is active.
                    if (body != null && bodyInterface.isAdded(body.getId()) && body.isActive()) {
                        applyNativeBuoyancy(
                                body,
                                deltaTime,
                                dataIndex,
                                dataStore,
                                gravity,
                                surfacePosition,
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