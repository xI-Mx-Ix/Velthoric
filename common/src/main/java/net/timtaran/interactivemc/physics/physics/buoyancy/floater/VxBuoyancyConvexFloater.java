/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.buoyancy.floater;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstConvexShape;
import net.timtaran.interactivemc.physics.physics.buoyancy.VxBuoyancyDataStore;
import net.timtaran.interactivemc.physics.physics.buoyancy.VxFluidType;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

/**
 * Calculates and applies buoyancy using the precise submerged volume of a convex shape.
 * This class implements Archimedes' principle for physically accurate buoyant forces
 * and applies them at the center of buoyancy to simulate correct torque.
 *
 * @author xI-Mx-Ix
 */
public class VxBuoyancyConvexFloater extends VxBuoyancyFloater {

    // Fluid physics constants
    private static final float WATER_DENSITY = 1000.0f; // kg/m^3
    private static final float LAVA_DENSITY = 3100.0f;  // kg/m^3 (approximate)

    // Thread-local temporary objects
    private final ThreadLocal<Vec3> tempVec3_2 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempVec3_3 = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<RVec3> tempRVec3_1 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<RVec3> tempRVec3_2 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Plane> tempPlane = ThreadLocal.withInitial(() -> new Plane(0f, 1f, 0f, 0f));
    private final ThreadLocal<float[]> tempFloatArray = ThreadLocal.withInitial(() -> new float[1]);
    private final ThreadLocal<Vec3> tempScale = ThreadLocal.withInitial(() -> new Vec3(1, 1, 1));


    public VxBuoyancyConvexFloater(VxPhysicsWorld physicsWorld) {
        super(physicsWorld);
    }

    @Override
    public void applyForces(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        ConstConvexShape shape = (ConstConvexShape) body.getShape();
        RMat44 worldTransform = body.getCenterOfMassTransform();
        RVec3 comPosition = tempRVec3_1.get();
        body.getCenterOfMassPosition(comPosition); // Populate the temp RVec3
        applyBuoyancyToConvexPart(body, shape, worldTransform, comPosition, deltaTime, index, dataStore);
    }

    /**
     * Core logic to apply buoyancy forces to a single convex part.
     * The resulting force is scaled by the horizontal area fraction detected
     * in the broad-phase to account for partial contact with limited fluid volumes.
     *
     * @param body               The parent physics body to which forces are applied.
     * @param convexPart         The convex shape part to process.
     * @param partWorldTransform The world transform of the convex part.
     * @param bodyCom            The world-space center of mass of the entire parent body.
     * @param deltaTime          The simulation time step.
     * @param index              The index of the body in the data store.
     * @param dataStore          The data store containing fluid properties and area fraction.
     */
    public void applyBuoyancyToConvexPart(Body body, ConstConvexShape convexPart, RMat44 partWorldTransform, RVec3 bodyCom, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        // --- Get Fluid Properties ---
        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];
        float areaFraction = dataStore.areaFractions[index];

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

        // Scale the volume by the area fraction to account for partial fluid contact.
        float submergedVolume = Math.abs(submergedVolumeArr[0]) * areaFraction;
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
}