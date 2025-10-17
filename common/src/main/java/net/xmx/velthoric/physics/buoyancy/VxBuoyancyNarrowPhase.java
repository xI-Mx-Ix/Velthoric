/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockMultiWrite;
import com.github.stephengold.joltjni.CompoundShape;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EShapeType;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstConvexShape;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.ConstSubShape;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * This class applies detailed Jolt C++ buoyancy and drag calculations by iterating
 * efficiently over a pre-filtered {@link VxBuoyancyDataStore}. It uses a high-fidelity
 * approach for convex and compound shapes and a fallback approximation for other shape types.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private final VxPhysicsWorld physicsWorld;

    // Fluid physics constants
    private static final float WATER_DENSITY = 1000.0f; // kg/m^3
    private static final float LAVA_DENSITY = 3100.0f;  // kg/m^3 (approximate)

    // Thread-local temporary objects to prevent frequent allocations in the physics thread.
    private final ThreadLocal<Vec3> tempVec3_1 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_2 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_3 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<RVec3> tempRVec3_1 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<RVec3> tempRVec3_2 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Plane> tempPlane = ThreadLocal.withInitial(() -> new Plane(0f, 1f, 0f, 0f));
    private final ThreadLocal<RMat44> tempRMat44 = ThreadLocal.withInitial(RMat44::new);
    private final ThreadLocal<float[]> tempFloatArray = ThreadLocal.withInitial(() -> new float[1]);
    private final ThreadLocal<Vec3> tempScale = ThreadLocal.withInitial(() -> new Vec3(1, 1, 1));


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
        for (int i = 0; i < dataStore.getCount(); ++i) {
            Body body = lock.getBody(i);
            if (body != null) {
                processBuoyancyForBody(body, deltaTime, i, dataStore);
            }
        }
    }

    /**
     * Determines the correct buoyancy calculation method based on the body's shape type
     * and applies the corresponding forces.
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

        EShapeType shapeType = shape.getType();

        // Use the high-fidelity method for convex or compound shapes for physical accuracy.
        if (shapeType == EShapeType.Convex) {
            processBuoyancyForConvexBody(body, (ConstConvexShape) shape, deltaTime, index, dataStore);
        } else if (shapeType == EShapeType.Compound) {
            processBuoyancyForCompoundBody(body, (CompoundShape) shape, deltaTime, index, dataStore);
        } else {
            // Use the less accurate approximation for other shapes as a fallback.
            processBuoyancyForBodyApproximate(body, deltaTime, index, dataStore);
        }
    }

    /**
     * Processes a compound shape by iterating through its convex sub-shapes and applying buoyancy to each.
     *
     * @param body          The physics body to process.
     * @param compoundShape The compound shape of the body.
     * @param deltaTime     The simulation time step.
     * @param index         The index of the body in the data store.
     * @param dataStore     The data store containing fluid properties.
     */
    private void processBuoyancyForCompoundBody(Body body, CompoundShape compoundShape, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        RMat44 bodyTransform = body.getCenterOfMassTransform();
        RVec3 bodyCom = tempRVec3_1.get();
        body.getCenterOfMassPosition(bodyCom); // Populate the temp RVec3

        int numSubShapes = compoundShape.getNumSubShapes();

        for (int i = 0; i < numSubShapes; ++i) {
            ConstSubShape subShape = compoundShape.getSubShape(i);
            ConstShape innerShape = subShape.getShape();

            if (innerShape.getType() == EShapeType.Convex) {
                ConstConvexShape convexSubShape = (ConstConvexShape) innerShape;

                // Get local transform of the sub-shape and combine it with the parent body's world transform.
                Mat44 localTransform = subShape.getLocalTransformNoScale(tempScale.get());
                RMat44 subShapeWorldTransform = tempRMat44.get();
                subShapeWorldTransform.set(bodyTransform.multiply(localTransform));

                // Apply buoyancy and drag to this individual convex part, using the main body's CoM for torque calculations.
                applyBuoyancyToConvexPart(body, convexSubShape, subShapeWorldTransform, bodyCom, deltaTime, index, dataStore);
            }
        }
    }


    /**
     * Calculates and applies buoyancy using the precise submerged volume of a convex shape.
     * This method is a wrapper that prepares the necessary transforms for a single convex body.
     *
     * @param body      The physics body to process.
     * @param shape     The convex shape of the body.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid properties.
     */
    private void processBuoyancyForConvexBody(Body body, ConstConvexShape shape, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        RMat44 worldTransform = body.getCenterOfMassTransform();
        RVec3 comPosition = tempRVec3_1.get();
        body.getCenterOfMassPosition(comPosition); // Populate the temp RVec3
        applyBuoyancyToConvexPart(body, shape, worldTransform, comPosition, deltaTime, index, dataStore);
    }

    /**
     * Core logic to apply buoyancy forces to a single convex part (either a standalone body or a sub-shape).
     * This method implements Archimedes' principle for physically accurate buoyant forces
     * and applies them at the center of buoyancy to simulate correct torque.
     *
     * @param body               The parent physics body to which forces are applied.
     * @param convexPart         The convex shape part to process.
     * @param partWorldTransform The world transform of the convex part.
     * @param bodyCom            The world-space center of mass of the entire parent body.
     * @param deltaTime          The simulation time step.
     * @param index              The index of the body in the data store.
     * @param dataStore          The data store containing fluid properties.
     */
    private void applyBuoyancyToConvexPart(Body body, ConstConvexShape convexPart, RMat44 partWorldTransform, RVec3 bodyCom, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        // --- Get Fluid Properties ---
        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];
        float fluidDensity = (fluidType == VxFluidType.LAVA) ? LAVA_DENSITY : WATER_DENSITY;

        final float linearDragCoefficient;
        final float angularDragCoefficient;
        switch (fluidType) {
            case LAVA:
                linearDragCoefficient = 5.0f;
                angularDragCoefficient = 2.0f;
                break;
            case WATER:
            default:
                linearDragCoefficient = 1.0f;
                angularDragCoefficient = 0.5f;
                break;
        }

        // --- Jolt's Submerged Volume Calculation ---
        Plane waterPlane = tempPlane.get();
        waterPlane.set(0f, 1f, 0f, -surfaceY);

        Vec3 scale = tempScale.get();
        float[] submergedVolumeArr = tempFloatArray.get();
        Vec3 centerOfBuoyancyWorld = tempVec3_2.get();

        convexPart.getSubmergedVolume(partWorldTransform.toMat44(), scale, waterPlane, submergedVolumeArr, submergedVolumeArr, centerOfBuoyancyWorld, bodyCom);

        float submergedVolume = submergedVolumeArr[0];
        if (submergedVolume < 1e-6f) {
            return; // Not submerged.
        }

        // --- Buoyancy Impulse (Archimedes' Principle) ---
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        Vec3 buoyancyImpulse = tempVec3_3.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-1.0f * fluidDensity * submergedVolume * deltaTime);

        // Apply impulse at the center of buoyancy to generate correct torque.
        RVec3 impulsePosition = tempRVec3_2.get();
        impulsePosition.set(centerOfBuoyancyWorld);
        body.addImpulse(buoyancyImpulse, impulsePosition);

        // --- Drag Impulses ---
        MotionProperties motionProperties = body.getMotionProperties();
        Vec3 linearVelocity = motionProperties.getLinearVelocity();
        Vec3 angularVelocity = motionProperties.getAngularVelocity();

        // --- 1. Linear Drag ---
        // Calculate the velocity of the center of buoyancy point.
        RVec3 r = Op.minus(impulsePosition, bodyCom);
        Vec3 rotationalVelocityAtPoint = angularVelocity.cross(r.toVec3());
        Vec3 pointVelocity = Op.plus(linearVelocity, rotationalVelocityAtPoint);

        float linearSpeed = pointVelocity.length();
        if (linearSpeed > 1e-6f) {
            float dragMagnitude = linearDragCoefficient * fluidDensity * submergedVolume * linearSpeed;
            Vec3 linearDragImpulse = Op.star(-dragMagnitude * deltaTime, pointVelocity.normalized());
            body.addImpulse(linearDragImpulse, impulsePosition);
        }

        // --- 2. Angular Drag ---
        float angularSpeed = angularVelocity.length();
        if (angularSpeed > 1e-6f) {
            float angularDragMagnitude = angularDragCoefficient * fluidDensity * submergedVolume * angularSpeed;
            Vec3 angularDragImpulse = Op.star(-angularDragMagnitude * deltaTime, angularVelocity.normalized());
            body.addAngularImpulse(angularDragImpulse);
        }
    }


    /**
     * Calculates and applies buoyancy using a simplified AABB approximation.
     * This method is used as a fallback for non-convex/non-compound shapes where precise
     * submerged volume calculation is not available.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid properties.
     */
    private void processBuoyancyForBodyApproximate(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        MotionProperties motionProperties = body.getMotionProperties();

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

        RVec3 comPosition = tempRVec3_1.get();
        body.getCenterOfMassPosition(comPosition);
        RVec3 centerOfBuoyancyWorld = tempRVec3_2.get();
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
        float linearSpeedSq = linearVelocity.lengthSq();
        if (linearSpeedSq > 1e-6f) {
            Vec3 linearDragImpulse = tempVec3_2.get();
            linearDragImpulse.set(linearVelocity);
            linearDragImpulse.scaleInPlace(-linearDragCoefficient * (float) Math.sqrt(linearSpeedSq) * submergedFraction * deltaTime);
            body.addImpulse(linearDragImpulse, centerOfBuoyancyWorld);
        }

        float verticalVelocity = linearVelocity.getY();
        if (Math.abs(verticalVelocity) > 1e-6f) {
            float verticalDampingImpulse = -verticalVelocity * bodyMass * verticalDampingCoefficient * submergedFraction * deltaTime;
            Vec3 impulse = tempVec3_3.get();
            impulse.set(0, verticalDampingImpulse, 0);
            body.addImpulse(impulse);
        }

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