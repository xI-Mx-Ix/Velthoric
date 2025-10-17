/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockMultiWrite;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.enumerate.EShapeType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyAABBFloater;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyCompoundFloater;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyConvexFloater;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyFloater;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * This class delegates the detailed Jolt C++ buoyancy and drag calculations
 * to specialized {@link VxBuoyancyFloater} implementations based on the shape type
 * of the body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private final VxPhysicsWorld physicsWorld;
    private final VxBuoyancyAABBFloater aabbFloater;
    private final VxBuoyancyConvexFloater convexFloater;
    private final VxBuoyancyCompoundFloater compoundFloater;

    /**
     * Constructs a new narrow-phase handler.
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyNarrowPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.aabbFloater = new VxBuoyancyAABBFloater(physicsWorld);
        this.convexFloater = new VxBuoyancyConvexFloater(physicsWorld);
        this.compoundFloater = new VxBuoyancyCompoundFloater(physicsWorld, this.convexFloater);
    }

    /**
     * Iterates through the locked bodies and applies buoyancy forces to each.
     *
     * @param lock      The multi-body write lock from Jolt.
     * @param deltaTime The simulation time step.
     * @param dataStore The data store containing all information about buoyant bodies.
     */
    public void applyForces(BodyLockMultiWrite lock, float deltaTime, VxBuoyancyDataStore dataStore) {
        for (int i = 0; i < dataStore.getCount(); ++i) {
            Body body = lock.getBody(i);
            if (body != null) {
                processBuoyancyForBody(body, deltaTime, i, dataStore);
            }
        }
    }

    /**
     * Determines the correct buoyancy calculation method based on the body's shape type
     * and delegates the force application to the corresponding floater.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid properties.
     */
    private void processBuoyancyForBody(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        if (!body.isActive()) {
            physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(body.getId());
        }

        MotionProperties motionProperties = body.getMotionProperties();
        if (motionProperties == null || motionProperties.getInverseMass() < 1e-6f) {
            return;
        }

        ConstShape shape = body.getShape();
        if (shape == null) return;

        // Select the appropriate floater strategy based on the shape type.
        VxBuoyancyFloater floater = getFloaterForShape(shape.getType());
        floater.applyForces(body, deltaTime, index, dataStore);
    }

    /**
     * Selects the appropriate floater strategy for a given shape type.
     *
     * @param shapeType The EShapeType of the body's shape.
     * @return The corresponding VxBuoyancyFloater instance.
     */
    private VxBuoyancyFloater getFloaterForShape(EShapeType shapeType) {
        return switch (shapeType) {
            case Convex -> convexFloater;
            case Compound -> compoundFloater;
            default ->
                // Use the less accurate approximation for all other shapes as a fallback.
                    aabbFloater;
        };
    }
}