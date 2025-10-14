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

import java.util.Map;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * This class applies detailed Jolt C++ buoyancy and drag calculations to a small,
 * pre-filtered list of bodies that are known to be in a fluid.
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
     * @param lock              The multi-body write lock from Jolt.
     * @param deltaTime         The simulation time step.
     * @param fluidSurfaceHeights A map from body ID to fluid surface Y-coordinate.
     * @param fluidTypes        A map from body ID to the type of fluid.
     */
    public void applyForces(BodyLockMultiWrite lock, float deltaTime, Map<Integer, Float> fluidSurfaceHeights, Map<Integer, VxFluidType> fluidTypes) {
        for (int i = 0; i < lock.getNumBodies(); ++i) {
            Body body = lock.getBody(i);
            if (body != null) {
                processBuoyancyForBody(body, deltaTime, fluidSurfaceHeights, fluidTypes);
            }
        }
    }

    /**
     * Calculates and applies buoyancy, drag, and damping impulses to a single body.
     * This advanced implementation applies forces at the center of buoyancy to ensure
     * physically correct torque and rotation when moving in a fluid.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param fluidSurfaceHeights A map from body ID to fluid surface Y-coordinate.
     * @param fluidTypes        A map from body ID to the type of fluid.
     */
    private void processBuoyancyForBody(Body body, float deltaTime, Map<Integer, Float> fluidSurfaceHeights, Map<Integer, VxFluidType> fluidTypes) {
        if (!body.isActive()) {
            physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(body.getId());
        }

        MotionProperties motionProperties = body.getMotionProperties();
        if (motionProperties == null || motionProperties.getInverseMass() < 1e-6f) {
            return;
        }

        int bodyId = body.getId();
        Float surfaceY = fluidSurfaceHeights.get(bodyId);
        VxFluidType fluidType = fluidTypes.get(bodyId);

        if (surfaceY == null || fluidType == null) {
            return;
        }

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

        // --- Center of Buoyancy Calculation ---
        // This is the point where all fluid forces (buoyancy and drag) will be applied.
        // For a simple box shape, it's the geometric center of the submerged volume.
        RVec3 comPosition = body.getCenterOfMassPosition();
        RVec3 centerOfBuoyancyWorld = tempRVec3_1.get();
        centerOfBuoyancyWorld.set(comPosition.xx(), minY + (submergedDepth * 0.5f), comPosition.zz());

        // --- Buoyancy Impulse ---
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        float bodyMass = 1.0f / motionProperties.getInverseMass();
        Vec3 buoyancyImpulse = tempVec3_1.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-buoyancyMultiplier * bodyMass * submergedFraction * deltaTime);
        body.addImpulse(buoyancyImpulse, centerOfBuoyancyWorld);

        // --- Drag and Damping Impulses ---
        Vec3 linearVelocity = body.getLinearVelocity();

        // --- 1. General Quadratic Drag (for all directions) ---
        float linearSpeedSq = linearVelocity.lengthSq();
        if (linearSpeedSq > 1e-6f) {
            Vec3 linearDragImpulse = tempVec3_2.get();
            linearDragImpulse.set(linearVelocity);
            linearDragImpulse.scaleInPlace(-linearDragCoefficient * (float) Math.sqrt(linearSpeedSq) * submergedFraction * deltaTime);

            // Apply the drag impulse at the center of buoyancy to create realistic rotational drag.
            body.addImpulse(linearDragImpulse, centerOfBuoyancyWorld);
        }

        // --- 2. Specialized Vertical Damping (to prevent bobbing) ---
        // This force is applied without a specific point (at the center of mass) as it only affects
        // the vertical axis and is meant to stabilize floating, not cause rotation.
        float verticalVelocity = linearVelocity.getY();
        if (Math.abs(verticalVelocity) > 1e-6f) {
            float verticalDampingImpulse = -verticalVelocity * bodyMass * verticalDampingCoefficient * submergedFraction * deltaTime;
            body.addImpulse(new Vec3(0, verticalDampingImpulse, 0));
        }

        // --- 3. Angular Drag ---
        Vec3 angularVelocity = body.getAngularVelocity();
        float angularSpeedSq = angularVelocity.lengthSq();
        if (angularSpeedSq > 1e-6f) {
            Vec3 angularDragImpulse = tempVec3_3.get();
            angularDragImpulse.set(angularVelocity);
            angularDragImpulse.scaleInPlace(-angularDragCoefficient * (float) Math.sqrt(angularSpeedSq) * submergedFraction * deltaTime);
            body.addAngularImpulse(angularDragImpulse);
        }
    }
}