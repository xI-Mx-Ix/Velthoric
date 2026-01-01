/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.floater;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Calculates and applies buoyancy using a simplified AABB approximation.
 * This method is used as a fallback for non-convex/non-compound shapes where precise
 * submerged volume calculation is not available.
 * <p>
 * This implementation includes mass-based clamping for drag forces to prevent
 * instability when interacting with light objects or high-viscosity fluids.
 *
 * @author xI-Mx-Ix
 */
public class VxBuoyancyAABBFloater extends VxBuoyancyFloater {

    // Thread-local temporary objects to prevent frequent allocations in the physics thread.
    private final ThreadLocal<Vec3> tempVec3_1 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_2 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_3 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<RVec3> tempRVec3_2 = ThreadLocal.withInitial(RVec3::new);

    public VxBuoyancyAABBFloater(VxPhysicsWorld physicsWorld) {
        super(physicsWorld);
    }

    /**
     * Applies buoyancy and drag forces using an AABB approximation.
     * The forces are scaled by the horizontal area fraction and applied
     * at the center of the detected fluid columns to simulate correct
     * torque and prevent sudden upward kicks.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid coverage and spatial properties.
     */
    @Override
    public void applyForces(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        MotionProperties motionProperties = body.getMotionProperties();
        float inverseMass = motionProperties.getInverseMass();
        if (inverseMass == 0.0f) {
            return;
        }
        float bodyMass = 1.0f / inverseMass;

        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];
        float areaFraction = dataStore.areaFractions[index];
        float waterX = dataStore.waterCenterX[index];
        float waterZ = dataStore.waterCenterZ[index];

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

        // Effective submersion is the vertical fraction multiplied by the horizontal area fraction.
        float effectiveSubmersion = submergedFraction * areaFraction;
        if (effectiveSubmersion <= 0.0f) return;

        // Apply force at the spatial center of the water contact instead of the body COM.
        RVec3 centerOfBuoyancyWorld = tempRVec3_2.get();
        centerOfBuoyancyWorld.set(waterX, minY + (submergedDepth * 0.5f), waterZ);

        // --- Buoyancy Impulse ---
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        Vec3 buoyancyImpulse = tempVec3_1.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-buoyancyMultiplier * bodyMass * effectiveSubmersion * deltaTime);
        body.addImpulse(buoyancyImpulse, centerOfBuoyancyWorld);

        // --- Drag and Damping ---
        // Forces are clamped against the body's momentum to prevent instability.

        Vec3 linearVelocity = body.getLinearVelocity();
        float linearSpeedSq = linearVelocity.lengthSq();
        if (linearSpeedSq > 1e-6f) {
            float linearSpeed = (float) Math.sqrt(linearSpeedSq);
            float dragImpulseMag = linearDragCoefficient * linearSpeed * effectiveSubmersion * deltaTime;

            // Limit drag impulse to the body's current momentum to prevent velocity reversal.
            float momentum = linearSpeed * bodyMass;
            if (dragImpulseMag > momentum) {
                dragImpulseMag = momentum;
            }

            Vec3 linearDragImpulse = tempVec3_2.get();
            linearDragImpulse.set(linearVelocity);
            linearDragImpulse.scaleInPlace(-dragImpulseMag / linearSpeed); // Normalize and scale
            body.addImpulse(linearDragImpulse, centerOfBuoyancyWorld);
        }

        float verticalVelocity = linearVelocity.getY();
        if (Math.abs(verticalVelocity) > 1e-6f) {
            float dampingImpulse = Math.abs(verticalVelocity * bodyMass * verticalDampingCoefficient * effectiveSubmersion * deltaTime);
            float momentumY = Math.abs(verticalVelocity * bodyMass);

            if (dampingImpulse > momentumY) {
                dampingImpulse = momentumY;
            }

            // Apply opposite to velocity
            float dir = -Math.signum(verticalVelocity);
            Vec3 impulse = tempVec3_3.get();
            impulse.set(0, dampingImpulse * dir, 0);
            body.addImpulse(impulse);
        }

        Vec3 angularVelocity = body.getAngularVelocity();
        float angularSpeedSq = angularVelocity.lengthSq();
        if (angularSpeedSq > 1e-6f) {
            float angularSpeed = (float) Math.sqrt(angularSpeedSq);
            float dragImpulseMag = angularDragCoefficient * angularSpeed * effectiveSubmersion * deltaTime;

            // Approximate angular momentum clamp
            float approxAngularMomentum = angularSpeed * bodyMass;
            if (dragImpulseMag > approxAngularMomentum) {
                dragImpulseMag = approxAngularMomentum;
            }

            Vec3 angularDragImpulse = tempVec3_1.get();
            angularDragImpulse.set(angularVelocity);
            angularDragImpulse.scaleInPlace(-dragImpulseMag / angularSpeed);
            body.addAngularImpulse(angularDragImpulse);
        }
    }
}