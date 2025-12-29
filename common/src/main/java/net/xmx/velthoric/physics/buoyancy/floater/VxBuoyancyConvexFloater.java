/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.floater;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstConvexShape;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

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
     * <p>
     * Unlike the AABB approximation, this method does not simply scale the force by the horizontal
     * fluid area fraction. Instead, it relies on the precise submerged volume calculation against
     * the fluid plane. To prevent incorrect buoyancy when an object overhangs a fluid edge (e.g.,
     * resting half on land), it checks the spatial coherence between the calculated center of buoyancy
     * and the actual center of the fluid mass detected in the broad-phase.
     *
     * @param body               The parent physics body to which forces are applied.
     * @param convexPart         The convex shape part to process.
     * @param partWorldTransform The world transform of the convex part.
     * @param bodyCom            The world-space center of mass of the entire parent body.
     * @param deltaTime          The simulation time step.
     * @param index              The index of the body in the data store.
     * @param dataStore          The data store containing fluid properties.
     */
    public void applyBuoyancyToConvexPart(Body body, ConstConvexShape convexPart, RMat44 partWorldTransform, RVec3 bodyCom, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        float surfaceY = dataStore.surfaceHeights[index];
        VxFluidType fluidType = dataStore.fluidTypes[index];

        // Retrieve the average center position of the actual fluid blocks found in the broad-phase.
        float broadPhaseWaterX = dataStore.waterCenterX[index];
        float broadPhaseWaterZ = dataStore.waterCenterZ[index];

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

        Plane waterPlane = tempPlane.get();
        waterPlane.set(0f, 1f, 0f, -surfaceY);

        Vec3 scale = tempScale.get();
        float[] submergedVolumeArr = tempFloatArray.get();
        Vec3 centerOfBuoyancyWorld = tempVec3_2.get();

        // Calculate the exact submerged volume and center of buoyancy assuming an infinite fluid plane at surfaceY.
        convexPart.getSubmergedVolume(partWorldTransform.toMat44(), scale, waterPlane, submergedVolumeArr, submergedVolumeArr, centerOfBuoyancyWorld, bodyCom);

        float submergedVolume = Math.abs(submergedVolumeArr[0]);
        if (submergedVolume < 1e-6f) {
            return;
        }

        // Verify that the calculated Center of Buoyancy (CoB) is close to the actual fluid found in the world.
        // If the object is overhanging land, the infinite plane calculation places the CoB under the land,
        // far from the actual water. We reduce the force based on this distance to prevent "ghost buoyancy"
        // in areas where no water exists physically, despite the infinite plane assumption.
        float dx = centerOfBuoyancyWorld.getX() - broadPhaseWaterX;
        float dz = centerOfBuoyancyWorld.getZ() - broadPhaseWaterZ;
        float distSq = dx * dx + dz * dz;

        // Apply a falloff factor based on distance.
        // If the CoB aligns with the water (open water), factor is close to 1.0.
        // If they diverge significantly (edge case), factor approaches 0.0.
        float coherencyFactor = 1.0f / (1.0f + 0.5f * distSq);

        submergedVolume *= coherencyFactor;

        if (submergedVolume < 1e-6f) {
            return;
        }

        // --- Buoyancy Impulse ---
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        Vec3 buoyancyImpulse = tempVec3_3.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-1.0f * fluidDensity * submergedVolume * deltaTime);

        RVec3 impulsePosition = tempRVec3_2.get();
        impulsePosition.set(centerOfBuoyancyWorld);
        body.addImpulse(buoyancyImpulse, impulsePosition);

        // --- Drag Impulses ---
        MotionProperties motionProperties = body.getMotionProperties();
        Vec3 linearVelocity = motionProperties.getLinearVelocity();
        Vec3 angularVelocity = motionProperties.getAngularVelocity();

        RVec3 r = Op.minus(impulsePosition, bodyCom);
        Vec3 rotationalVelocityAtPoint = angularVelocity.cross(r.toVec3());
        Vec3 pointVelocity = Op.plus(linearVelocity, rotationalVelocityAtPoint);

        float linearSpeed = pointVelocity.length();
        if (linearSpeed > 1e-6f) {
            float dragMagnitude = linearDragCoefficient * fluidDensity * submergedVolume * linearSpeed;
            Vec3 linearDragImpulse = Op.star(-dragMagnitude * deltaTime, pointVelocity.normalized());
            body.addImpulse(linearDragImpulse, impulsePosition);
        }

        float angularSpeed = angularVelocity.length();
        if (angularSpeed > 1e-6f) {
            float angularDragMagnitude = angularDragCoefficient * fluidDensity * submergedVolume * angularSpeed;
            Vec3 angularDragImpulse = Op.star(-angularDragMagnitude * deltaTime, angularVelocity.normalized());
            body.addAngularImpulse(angularDragImpulse);
        }
    }
}