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
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private final VxPhysicsWorld physicsWorld;

    // --- Thread-Local Temporaries to avoid allocation ---
    // These objects are reused every frame to prevent GC pressure.
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
     * Iterates through the bodies and applies buoyancy forces using individual write locks.
     * <p>
     * This method retrieves the environmental data populated by the broad-phase and uses the
     * native Jolt Physics engine to calculate exact submerged volumes and drag forces.
     *
     * @param lockInterface The locking interface used to acquire body locks.
     * @param deltaTime     The simulation time step.
     * @param dataStore     The data store containing all information about buoyant bodies.
     */
    public void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, VxBuoyancyDataStore dataStore) {
        int totalCount = dataStore.getCount();
        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterfaceNoLock();

        // Retrieve ThreadLocal buffers once per frame to avoid allocation overhead in the loop.
        RVec3 surfacePosition = tempSurfacePos.get();
        Vec3 surfaceNormal = tempSurfaceNormal.get();
        Vec3 fluidVelocity = tempFluidVelocity.get();
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        // Iterate sequentially through all active buoyant bodies.
        for (int i = 0; i < totalCount; i++) {
            int bodyId = dataStore.bodyIds[i];

            if (bodyId == 0 || !bodyInterface.isAdded(bodyId)) {
                continue;
            }

            // Acquire an individual Write Lock for the body.
            // We use try-with-resources to ensure the lock is always released.
            try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
                Body body = lock.getBody();

                // Apply forces only if the body was successfully locked and is active.
                if (body != null && body.isActive()) {
                    applyNativeBuoyancy(
                            body,
                            deltaTime,
                            i,
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