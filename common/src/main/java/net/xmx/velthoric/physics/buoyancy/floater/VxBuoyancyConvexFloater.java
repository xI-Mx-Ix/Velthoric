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
 * <p>
 * It includes stability clamping to prevent energy injection when handling light bodies
 * in dense fluids and strict spatial coherence checks to prevent "ghost buoyancy"
 * on offset shapes.
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
        body.getCenterOfMassPosition(comPosition);

        // Pass the default scale (1,1,1) for non-recursive calls.
        Vec3 scale = tempScale.get();
        scale.set(1, 1, 1);

        applyBuoyancyToConvexPart(body, shape, worldTransform, comPosition, scale, deltaTime, index, dataStore);
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
     * @param scale              The accumulated scale of the shape hierarchy.
     * @param deltaTime          The simulation time step.
     * @param index              The index of the body in the data store.
     * @param dataStore          The data store containing fluid properties.
     */
    public void applyBuoyancyToConvexPart(Body body, ConstConvexShape convexPart, RMat44 partWorldTransform, RVec3 bodyCom, Vec3 scale, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        MotionProperties motionProperties = body.getMotionProperties();
        float inverseMass = motionProperties.getInverseMass();
        // If the body is static or kinematic (infinite mass), forces have no effect.
        if (inverseMass == 0.0f) {
            return;
        }
        float bodyMass = 1.0f / inverseMass;

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

        float[] submergedVolumeArr = tempFloatArray.get();
        Vec3 centerOfBuoyancyWorld = tempVec3_2.get();

        // Calculate the exact submerged volume and center of buoyancy assuming an infinite fluid plane at surfaceY.
        // We pass the accumulated scale to ensure correct volume calculation for scaled sub-shapes.
        convexPart.getSubmergedVolume(partWorldTransform.toMat44(), scale, waterPlane, submergedVolumeArr, submergedVolumeArr, centerOfBuoyancyWorld, bodyCom);

        float submergedVolume = Math.abs(submergedVolumeArr[0]);
        if (submergedVolume < 1e-6f) {
            return;
        }

        // Verify that the calculated Center of Buoyancy (CoB) is close to the actual fluid found in the world.
        // The infinite plane assumption of `getSubmergedVolume` can yield false positives if the shape
        // projects below the water level but is horizontally distant (e.g. in a dry cave below sea level).
        float dx = centerOfBuoyancyWorld.getX() - broadPhaseWaterX;
        float dz = centerOfBuoyancyWorld.getZ() - broadPhaseWaterZ;
        float distSq = dx * dx + dz * dz;

        // Apply strict attenuation based on horizontal distance.
        // If the CoB deviates too far from the broad-phase fluid center, we reduce the volume to 0.
        // This effectively culls "ghost buoyancy" for offset shapes or disjoint parts.
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
        Vec3 linearVelocity = motionProperties.getLinearVelocity();
        Vec3 angularVelocity = motionProperties.getAngularVelocity();

        RVec3 r = Op.minus(impulsePosition, bodyCom);
        Vec3 rotationalVelocityAtPoint = angularVelocity.cross(r.toVec3());
        Vec3 pointVelocity = Op.plus(linearVelocity, rotationalVelocityAtPoint);

        float linearSpeed = pointVelocity.length();
        if (linearSpeed > 1e-6f) {
            // Calculate the ideal drag impulse based on the standard drag equation.
            float dragMagnitude = linearDragCoefficient * fluidDensity * submergedVolume * linearSpeed * deltaTime;

            // CLAMPING: Ensure we never apply more impulse than the body's current momentum.
            // If the calculated drag is larger than the momentum, it would reverse the body's direction
            // in a single tick, causing instability (explosions).
            float momentum = linearSpeed * bodyMass;
            if (dragMagnitude > momentum) {
                dragMagnitude = momentum;
            }

            Vec3 linearDragImpulse = Op.star(-dragMagnitude, pointVelocity.normalized());
            body.addImpulse(linearDragImpulse, impulsePosition);
        }

        float angularSpeed = angularVelocity.length();
        if (angularSpeed > 1e-6f) {
            // We approximate the angular momentum clamp using the mass and a characteristic radius,
            // or simply clamp the impulse magnitude to prevent overshoot.
            // Using a simplified mass-based clamp for angular stability is usually sufficient.
            float dragMagnitude = angularDragCoefficient * fluidDensity * submergedVolume * angularSpeed * deltaTime;

            // Heuristic clamp for angular momentum to prevent spin explosions.
            // Assuming a unit radius for inertia approximation to avoid expensive tensor math here.
            float approxAngularMomentum = angularSpeed * bodyMass;
            if (dragMagnitude > approxAngularMomentum) {
                dragMagnitude = approxAngularMomentum;
            }

            Vec3 angularDragImpulse = Op.star(-dragMagnitude, angularVelocity.normalized());
            body.addAngularImpulse(angularDragImpulse);
        }
    }
}