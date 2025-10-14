/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockMultiWrite;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * This class applies detailed Jolt C++ buoyancy and drag calculations by iterating
 * efficiently over a pre-filtered {@link VxBuoyancyDataStore}.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private final VxPhysicsWorld physicsWorld;

    // Thread-local temporary objects to prevent frequent allocations in the physics thread.
    private final ThreadLocal<Vec3> tempVec3_1 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_2 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_3 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<RVec3> tempRVec3_1 = ThreadLocal.withInitial(RVec3::new);

    /**
     * Constructs a new narrow-phase handler.
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyNarrowPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    /**
     * Iterates through the locked bodies and applies buoyancy forces to each.
     *
     * @param lock      The multi-body write lock from Jolt.
     * @param deltaTime The simulation time step.
     * @param dataStore The data store containing all information about buoyant bodies.
     */
    public void applyForces(BodyLockMultiWrite lock, float deltaTime, VxBuoyancyDataStore dataStore) {
        // We iterate using the index from the data store, which corresponds to the locked body.
        for (int i = 0; i < dataStore.getCount(); ++i) {
            Body body = lock.getBody(i);
            if (body != null) {
                // Pass the index 'i' to get the corresponding fluid data.
                processBuoyancyForBody(body, deltaTime, i, dataStore);
            }
        }
    }

    /**
     * Calculates and applies buoyancy, drag, and damping impulses to a single body.
     * This advanced implementation applies forces at the center of buoyancy to ensure
     * physically correct torque and rotation when moving in a fluid. Forces are applied
     * relative to the body's actual center of mass for accurate simulation.
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

        // --- Efficiently read data from the SoA store ---
        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];

        // --- Physics Properties ---
        final float buoyancyMultiplier;
        final float linearDragCoefficient;
        final float angularDragCoefficient;
        final float verticalDampingCoefficient;

        switch (fluidType) {
            case LAVA:
                buoyancyMultiplier = 2.5f;
                linearDragCoefficient = 10.0f;
                angularDragCoefficient = 5.0f;
                verticalDampingCoefficient = 8.0f;
                break;
            case WATER:
            default:
                buoyancyMultiplier = 1.72f;
                linearDragCoefficient = 3.0f;
                angularDragCoefficient = 2.0f;
                verticalDampingCoefficient = 5.0f;
                break;
        }

        // --- Submersion Calculation ---
        ConstAaBox worldBounds = body.getWorldSpaceBounds();
        float minY = worldBounds.getMin().getY();
        float maxY = worldBounds.getMax().getY();
        float height = maxY - minY;
        if (height < 1e-6f) return;

        float submergedDepth = surfaceY - minY;
        float submergedFraction = Math.max(0.0f, Math.min(1.0f, submergedDepth / height));
        if (submergedFraction <= 0.0f) return;

        // --- Center of Mass (crucial for all calculations) ---
        RVec3 comPosition = body.getCenterOfMassPosition();

        // --- Center of Buoyancy Calculation ---
        // The point of buoyancy is at the geometric center of the submerged volume,
        // but the impulse is applied relative to the center of mass.
        RVec3 centerOfBuoyancyWorld = tempRVec3_1.get();
        centerOfBuoyancyWorld.set(comPosition.xx(), minY + (submergedDepth * 0.5f), comPosition.zz());

        // --- Buoyancy Impulse ---
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        float bodyMass = 1.0f / motionProperties.getInverseMass();
        Vec3 buoyancyImpulse = tempVec3_2.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-buoyancyMultiplier * bodyMass * submergedFraction * deltaTime);

        // Apply buoyancy at the center of buoyancy to generate correct torque.
        body.addImpulse(buoyancyImpulse, centerOfBuoyancyWorld);

        // --- Drag and Damping Impulses ---
        Vec3 linearVelocity = body.getLinearVelocity();

        // --- 1. General Quadratic Drag (for all directions) ---
        float linearSpeedSq = linearVelocity.lengthSq();
        if (linearSpeedSq > 1e-6f) {
            Vec3 linearDragImpulse = tempVec3_3.get();
            linearDragImpulse.set(linearVelocity);
            linearDragImpulse.scaleInPlace(-linearDragCoefficient * (float) Math.sqrt(linearSpeedSq) * submergedFraction * deltaTime);

            // Apply drag at the center of buoyancy for realistic rotational drag.
            body.addImpulse(linearDragImpulse, centerOfBuoyancyWorld);
        }

        // --- 2. Specialized Vertical Damping (to prevent bobbing) ---
        // This force is applied at the center of mass (no position passed) as it
        // should only affect the vertical axis and avoid inducing rotation.
        float verticalVelocity = linearVelocity.getY();
        if (Math.abs(verticalVelocity) > 1e-6f) {
            float verticalDampingImpulse = -verticalVelocity * bodyMass * verticalDampingCoefficient * submergedFraction * deltaTime;
            body.addImpulse(new Vec3(0, verticalDampingImpulse, 0));
        }

        // --- 3. Angular Drag ---
        Vec3 angularVelocity = body.getAngularVelocity();
        float angularSpeedSq = angularVelocity.lengthSq();
        if (angularSpeedSq > 1e-6f) {
            Vec3 angularDragImpulse = tempVec3_1.get();
            angularDragImpulse.set(angularVelocity);
            angularDragImpulse.scaleInPlace(-angularDragCoefficient * (float) Math.sqrt(angularSpeedSq) * submergedFraction * deltaTime);
            body.addAngularImpulse(angularDragImpulse);
        }
    }
}