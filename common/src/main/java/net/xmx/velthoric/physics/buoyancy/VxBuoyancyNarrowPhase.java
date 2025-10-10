/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockMultiWrite;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
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
    private final ThreadLocal<Mat44> tempMat44_1 = ThreadLocal.withInitial(Mat44::new);

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
     * Calculates and applies buoyancy and drag forces to a single body.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param fluidSurfaceHeights A map from body ID to fluid surface Y-coordinate.
     * @param fluidTypes        A map from body ID to the type of fluid.
     */
    private void processBuoyancyForBody(Body body, float deltaTime, Map<Integer, Float> fluidSurfaceHeights, Map<Integer, VxFluidType> fluidTypes) {
        MotionProperties motionProperties = body.getMotionProperties();
        if (motionProperties == null || motionProperties.getInverseMass() < 1e-6f) {
            return; // Static or kinematic bodies are not affected by buoyancy forces.
        }

        int bodyId = body.getId();
        Float surfaceY = fluidSurfaceHeights.get(bodyId);
        VxFluidType fluidType = fluidTypes.get(bodyId);

        if (surfaceY == null || fluidType == null) {
            return; // No fluid data found for this body, cannot proceed.
        }

        // Set physics properties based on the fluid type.
        final float buoyancyFactor;
        final float linearDrag;
        final float angularDrag;

        switch (fluidType) {
            case LAVA:
                // Lava is much denser and more viscous than water.
                // These values are increased significantly to reflect that.
                buoyancyFactor = 3.0f; // Strong upward force.
                linearDrag = 10.0f;    // Very high resistance to movement.
                angularDrag = 5.0f;    // High resistance to rotation.
                break;
            case WATER:
            default:
                buoyancyFactor = 1.1f;
                linearDrag = 1.0f;
                angularDrag = 0.5f;
                break;
        }

        // --- The rest of the physics calculation remains the same ---

        // Calculate the submerged fraction of the body.
        ConstAaBox worldBounds = body.getWorldSpaceBounds();
        float minY = worldBounds.getMin().getY();
        float maxY = worldBounds.getMax().getY();
        float height = maxY - minY;
        if (height < 1e-6f) return;

        float submergedDepth = surfaceY - minY;
        if (submergedDepth <= 0.0f) return;

        float submergedFraction = Math.min(1.0f, submergedDepth / height);
        float totalVolume = worldBounds.getVolume();
        float submergedVolume = totalVolume * submergedFraction;
        if (submergedVolume <= 0.0f) return;

        // Calculate the center of buoyancy.
        RVec3 comPosition = body.getCenterOfMassPosition();
        RVec3 centerOfBuoyancyWorld = tempRVec3_1.get();
        centerOfBuoyancyWorld.set(comPosition.xx(), minY + (submergedDepth / 2.0f), comPosition.zz());
        Vec3 relativeCenterOfBuoyancy = Op.minus(centerOfBuoyancyWorld, comPosition).toVec3();

        // Calculate the buoyancy impulse (Archimedes' principle).
        float inverseMass = motionProperties.getInverseMass();
        float fluidDensity = buoyancyFactor / (totalVolume * inverseMass); // Effective density.
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();
        Vec3 buoyancyImpulse = tempVec3_1.get();
        buoyancyImpulse.set(gravity);
        buoyancyImpulse.scaleInPlace(-fluidDensity * submergedVolume * motionProperties.getGravityFactor() * deltaTime);

        // Calculate linear drag impulse.
        Vec3 linearVelocity = body.getLinearVelocity();
        Vec3 angularVelocity = body.getAngularVelocity();
        Vec3 centerOfBuoyancyVelocity = Op.star(angularVelocity, relativeCenterOfBuoyancy);
        centerOfBuoyancyVelocity.addInPlace(linearVelocity.getX(), linearVelocity.getY(), linearVelocity.getZ());
        Vec3 relativeCobVelocity = Op.minus(new Vec3(), centerOfBuoyancyVelocity);

        Vec3 dragImpulse = tempVec3_2.get();
        dragImpulse.loadZero();
        float relCobVelLenSq = relativeCobVelocity.lengthSq();
        if (relCobVelLenSq > 1.0e-12f) {
            Vec3 size = body.getShape().getLocalBounds().getSize();
            Quat rotation = body.getRotation();
            Vec3 localRelativeCobVelocity = Op.star(rotation.conjugated(), relativeCobVelocity);
            Vec3 faceAreas = tempVec3_3.get();
            faceAreas.set(size.getY() * size.getZ(), size.getZ() * size.getX(), size.getX() * size.getY());
            float area = Math.abs(localRelativeCobVelocity.dot(faceAreas)) / (float) Math.sqrt(relCobVelLenSq);

            dragImpulse.set(relativeCobVelocity);
            dragImpulse.scaleInPlace(0.5f * fluidDensity * linearDrag * area * deltaTime * (float) Math.sqrt(relCobVelLenSq));

            // Clamp drag impulse to not exceed the body's velocity.
            float linearVelocityLenSq = linearVelocity.lengthSq();
            if (linearVelocityLenSq > 0f) {
                float dragDeltaLinearVelocityLenSq = dragImpulse.lengthSq() * (inverseMass * inverseMass);
                if (dragDeltaLinearVelocityLenSq > linearVelocityLenSq) {
                    dragImpulse.scaleInPlace((float) Math.sqrt(linearVelocityLenSq / dragDeltaLinearVelocityLenSq));
                }
            }
        }

        // Calculate angular drag impulse.
        Vec3 size = body.getShape().getLocalBounds().getSize();
        float avgWidth = (size.getX() + size.getY() + size.getZ()) / 3.0f;
        Vec3 angularDragImpulse = tempVec3_3.get();
        angularDragImpulse.set(angularVelocity);
        angularDragImpulse.scaleInPlace(-angularDrag * submergedFraction * deltaTime * (avgWidth * avgWidth) / inverseMass);

        // Clamp angular drag impulse to not exceed the body's angular velocity.
        Mat44 worldInvInertia = getWorldSpaceInverseInertia(body, tempMat44_1.get());
        Vec3 dragDeltaAngularVelocity = worldInvInertia.multiply3x3(angularDragImpulse);
        float angularVelocityLenSq = angularVelocity.lengthSq();
        if (angularVelocityLenSq > 0f) {
            float dragDeltaAngularVelocityLenSq = dragDeltaAngularVelocity.lengthSq();
            if (dragDeltaAngularVelocityLenSq > angularVelocityLenSq) {
                angularDragImpulse.scaleInPlace((float) Math.sqrt(angularVelocityLenSq / dragDeltaAngularVelocityLenSq));
            }
        }

        // Apply the calculated impulses.
        Vec3 totalLinearImpulse = Op.plus(buoyancyImpulse, dragImpulse);
        body.addImpulse(totalLinearImpulse, centerOfBuoyancyWorld);
        body.addAngularImpulse(angularDragImpulse);
    }

    /**
     * Reconstructs the world-space inverse inertia tensor for a dynamic body.
     *
     * @param body  The body for which to calculate the tensor.
     * @param store A {@link Mat44} instance to store the result in, to avoid allocations.
     * @return The provided 'store' matrix, now containing the world-space inverse inertia tensor.
     */
    private Mat44 getWorldSpaceInverseInertia(Body body, Mat44 store) {
        MotionProperties mp = body.getMotionProperties();
        Vec3 invInertiaDiagonal = mp.getInverseInertiaDiagonal();
        Quat inertiaRotation = mp.getInertiaRotation();

        Mat44 scale = Mat44.sScale(invInertiaDiagonal);
        Mat44 rotation = Mat44.sRotation(inertiaRotation);
        Mat44 rotationT = Mat44.sRotation(inertiaRotation.conjugated());

        store.set(rotation);
        store.rightMultiplyInPlace(scale);
        store.rightMultiplyInPlace(rotationT);
        return store;
    }
}