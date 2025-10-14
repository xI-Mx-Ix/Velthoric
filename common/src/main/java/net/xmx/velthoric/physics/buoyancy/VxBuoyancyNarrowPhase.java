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
     * This advanced implementation uses a quadratic drag model and a separate vertical
     * damping force to create smooth, stable floating behavior and prevent oscillation.
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
        final float verticalDampingCoefficient; // Coefficient for the specialized vertical damping force.

        switch (fluidType) {
            case LAVA:
                buoyancyMultiplier = 2.5f;
                linearDragCoefficient = 10.0f;
                angularDragCoefficient = 5.0f;
                verticalDampingCoefficient = 8.0f;
                break;
            case WATER:
            default:
                // A value slightly > 1.0 ensures the object will float.
                buoyancyMultiplier = 1.72f;
                // These coefficients determine how "thick" the water feels.
                linearDragCoefficient = 3.0f;
                angularDragCoefficient = 2.0f;
                // This is the key parameter to prevent bobbing. It strongly dampens up/down motion.
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

        // --- Buoyancy Impulse ---
        // This impulse counteracts gravity for the submerged portion of the body.
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        float bodyMass = 1.0f / motionProperties.getInverseMass();
        Vec3 buoyancyImpulse = tempVec3_1.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-buoyancyMultiplier * bodyMass * submergedFraction * deltaTime);

        // Apply at the center of buoyancy. For a box, this is the center of the submerged volume.
        RVec3 comPosition = body.getCenterOfMassPosition();
        RVec3 centerOfBuoyancyWorld = tempRVec3_1.get();
        centerOfBuoyancyWorld.set(comPosition.xx(), minY + (submergedDepth * 0.5f), comPosition.zz());
        body.addImpulse(buoyancyImpulse, centerOfBuoyancyWorld);

        // --- Drag and Damping Impulses ---
        Vec3 linearVelocity = body.getLinearVelocity();

        // --- 1. General Quadratic Drag (for all directions) ---
        // This simulates the resistance of moving through a fluid.
        float linearSpeedSq = linearVelocity.lengthSq();
        if (linearSpeedSq > 1e-6f) {
            Vec3 linearDragImpulse = tempVec3_2.get();
            linearDragImpulse.set(linearVelocity);
            // Impulse is proportional to velocity squared, applied opposite to the direction of motion.
            // F_drag = -c * |v| * v
            linearDragImpulse.scaleInPlace(-linearDragCoefficient * (float) Math.sqrt(linearSpeedSq) * submergedFraction * deltaTime);
            body.addImpulse(linearDragImpulse);
        }

        // --- 2. Specialized Vertical Damping (to prevent bobbing) ---
        // This is a strong, targeted force that only opposes vertical motion,
        // making the object settle smoothly at the water's surface.
        float verticalVelocity = linearVelocity.getY();
        if (Math.abs(verticalVelocity) > 1e-6f) {
            // This damping force is proportional to the vertical velocity and the body's mass.
            float verticalDampingImpulse = -verticalVelocity * bodyMass * verticalDampingCoefficient * submergedFraction * deltaTime;
            body.addImpulse(new Vec3(0, verticalDampingImpulse, 0));
        }

        // --- 3. Angular Drag ---
        // This simulates rotational resistance, making the object's rotation slow down in water.
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