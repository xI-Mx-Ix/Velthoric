/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.phase;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EShapeType;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import com.github.stephengold.joltjni.readonly.ConstConvexShape;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.ConstSubShape;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyAABBFloater;
import net.xmx.velthoric.physics.buoyancy.floater.VxBuoyancyConvexFloater;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the narrow-phase of buoyancy physics on the dedicated physics thread.
 * This class recursively unwraps complex shapes to apply precise buoyancy forces
 * to individual convex parts, falling back to an AABB approximation for unsupported shapes.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyNarrowPhase {

    private final VxPhysicsWorld physicsWorld;
    private final VxBuoyancyAABBFloater aabbFloater;
    private final VxBuoyancyConvexFloater convexFloater;

    // --- Thread-Local Temporaries for Recursive Processing ---
    private final ThreadLocal<RMat44> tempRMat44 = ThreadLocal.withInitial(RMat44::new);
    private final ThreadLocal<RVec3> tempRVec3 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Mat44> tempMat44 = ThreadLocal.withInitial(Mat44::new);
    private final ThreadLocal<Vec3> tempScaleVec = ThreadLocal.withInitial(() -> new Vec3(1, 1, 1));

    /**
     * Constructs a new narrow-phase handler.
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyNarrowPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.aabbFloater = new VxBuoyancyAABBFloater(physicsWorld);
        this.convexFloater = new VxBuoyancyConvexFloater(physicsWorld);
    }

    /**
     * Iterates through the locked bodies and apply buoyancy forces to each.
     *
     * @param lockInterface The no-lock interface used to retrieve body pointers.
     * @param deltaTime     The simulation time step.
     * @param dataStore     The data store containing all information about buoyant bodies.
     */
    public void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, VxBuoyancyDataStore dataStore) {
        // Iterate over the data store indices to preserve the correct mapping between
        // fluid data (heights, types) and the associated physics bodies.
        for (int i = 0; i < dataStore.getCount(); ++i) {
            int bodyId = dataStore.bodyIds[i];

            // Use BodyLockWrite to retrieve the Body object from the ID.
            // Even though we use the "NoLock" interface, BodyLockWrite is the standard way
            // to resolve an ID to a Body object in Jolt JNI.
            try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
                if (lock.succeeded()) {
                    Body body = lock.getBody();
                    if (body != null) {
                        processBuoyancyForBody(body, deltaTime, i, dataStore);
                    }
                }
            }
        }
    }

    /**
     * Processes a single body, determining the correct buoyancy strategy.
     * It checks if the body's shape is fully supported for precise calculations;
     * if not, it uses an AABB approximation.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid properties.
     */
    private void processBuoyancyForBody(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        if (!body.isActive()) {
            return;
        }

        MotionProperties motionProperties = body.getMotionProperties();
        if (motionProperties == null || motionProperties.getInverseMass() < 1e-6f) {
            return;
        }

        ConstShape shape = body.getShape();
        if (shape == null) return;

        if (isShapeHierarchySupported(shape)) {
            RVec3 bodyCom = tempRVec3.get();
            body.getCenterOfMassPosition(bodyCom);

            // Start recursion with identity scale (1,1,1) as Jolt bodies generally
            // do not have a scale at the body level (scale is in the shape).
            Vec3 initialScale = new Vec3(1, 1, 1);

            processShapeRecursively(body, shape, body.getCenterOfMassTransform(), bodyCom, initialScale, deltaTime, index, dataStore);
        } else {
            // Fallback for unsupported shapes like ScaledShape or primitive shapes (MeshShape, HeightFieldShape).
            aabbFloater.applyForces(body, deltaTime, index, dataStore);
        }
    }

    /**
     * Recursively checks if a shape and all its sub-shapes are supported for precise
     * buoyancy calculations. Scaled shapes are not supported because the Jolt API's
     * `getSubmergedVolume` does not handle them.
     *
     * @param shape The shape to check.
     * @return True if the shape hierarchy is fully supported, false otherwise.
     */
    private boolean isShapeHierarchySupported(ConstShape shape) {
        if (shape instanceof ScaledShape) {
            return false; // Jolt's getSubmergedVolume does not support scaled shapes.
        }

        if (shape instanceof DecoratedShape decorated) {
            return isShapeHierarchySupported(decorated.getInnerShape());
        }

        if (shape.getType() == EShapeType.Compound) {
            CompoundShape compound = (CompoundShape) shape;
            for (int i = 0; i < compound.getNumSubShapes(); i++) {
                if (!isShapeHierarchySupported(compound.getSubShape(i).getShape())) {
                    return false;
                }
            }
        } else if (shape.getType() != EShapeType.Convex) {
            // If it's not a supported DecoratedShape, Compound, or Convex, it's not supported.
            return false;
        }

        return true;
    }

    /**
     * Recursively processes a supported shape hierarchy, unwrapping decorated shapes
     * and applying buoyancy forces to each convex part.
     *
     * @param body             The parent physics body.
     * @param shape            The current shape or sub-shape being processed.
     * @param currentTransform The world transform of the current shape.
     * @param bodyCom          The center of mass of the parent body.
     * @param accumulatedScale The accumulated scale factors from parent shapes.
     * @param deltaTime        The simulation time step.
     * @param index            The index of the body in the data store.
     * @param dataStore        The data store containing fluid properties.
     */
    private void processShapeRecursively(Body body, ConstShape shape, RMat44 currentTransform, RVec3 bodyCom, Vec3 accumulatedScale, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        // --- Unwrapping Decorated Shapes ---
        if (shape instanceof DecoratedShape decoratedShape) {
            ConstShape innerShape = decoratedShape.getInnerShape();
            RMat44 newTransform = tempRMat44.get();

            if (decoratedShape instanceof RotatedTranslatedShape rtShape) {
                Mat44 localTransform = tempMat44.get().sRotationTranslation(rtShape.getRotation(), rtShape.getPosition());
                newTransform.set(currentTransform.multiply(localTransform));
                processShapeRecursively(body, innerShape, newTransform, bodyCom, accumulatedScale, deltaTime, index, dataStore);
            } else if (decoratedShape instanceof OffsetCenterOfMassShape ocomShape) {
                Mat44 localTransform = tempMat44.get().sTranslation(ocomShape.getOffset());
                newTransform.set(currentTransform.multiply(localTransform));
                processShapeRecursively(body, innerShape, newTransform, bodyCom, accumulatedScale, deltaTime, index, dataStore);
            }
            return;
        }

        // --- Handling Compound Shapes ---
        if (shape.getType() == EShapeType.Compound) {
            CompoundShape compound = (CompoundShape) shape;
            for (int i = 0; i < compound.getNumSubShapes(); i++) {
                ConstSubShape subShape = compound.getSubShape(i);

                // Extract the scale for this specific sub-shape.
                Vec3 localScale = tempScaleVec.get();
                Mat44 localTransform = subShape.getLocalTransformNoScale(localScale);

                RMat44 subShapeWorldTransform = tempRMat44.get();
                subShapeWorldTransform.set(currentTransform.multiply(localTransform));

                // Combine the parent scale with the local scale for the recursive call.
                // We create a new Vec3 here to avoid stack corruption in the recursion.
                Vec3 combinedScale = new Vec3(
                        accumulatedScale.getX() * localScale.getX(),
                        accumulatedScale.getY() * localScale.getY(),
                        accumulatedScale.getZ() * localScale.getZ()
                );

                processShapeRecursively(body, subShape.getShape(), subShapeWorldTransform, bodyCom, combinedScale, deltaTime, index, dataStore);
            }
            return;
        }

        // --- Base Case: Applying forces to a Convex Shape ---
        if (shape.getType() == EShapeType.Convex) {
            convexFloater.applyBuoyancyToConvexPart(body, (ConstConvexShape) shape, currentTransform, bodyCom, accumulatedScale, deltaTime, index, dataStore);
        }
    }
}