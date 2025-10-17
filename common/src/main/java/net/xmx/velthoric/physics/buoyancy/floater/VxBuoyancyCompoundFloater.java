/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.floater;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.CompoundShape;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EShapeType;
import com.github.stephengold.joltjni.readonly.ConstConvexShape;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.ConstSubShape;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Processes a compound shape by iterating through its convex sub-shapes and applying buoyancy to each.
 * This class relies on a {@link VxBuoyancyConvexFloater} to handle the detailed calculations
 * for each individual convex part.
 *
 * @author xI-Mx-Ix
 */
public class VxBuoyancyCompoundFloater extends VxBuoyancyFloater {

    private final VxBuoyancyConvexFloater convexFloater;

    // Thread-local temporary objects
    private final ThreadLocal<RVec3> tempRVec3_1 = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<RMat44> tempRMat44 = ThreadLocal.withInitial(RMat44::new);
    private final ThreadLocal<Vec3> tempScale = ThreadLocal.withInitial(() -> new Vec3(1, 1, 1));


    public VxBuoyancyCompoundFloater(VxPhysicsWorld physicsWorld, VxBuoyancyConvexFloater convexFloater) {
        super(physicsWorld);
        this.convexFloater = convexFloater;
    }

    @Override
    public void applyForces(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore) {
        CompoundShape compoundShape = (CompoundShape) body.getShape();
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
                convexFloater.applyBuoyancyToConvexPart(body, convexSubShape, subShapeWorldTransform, bodyCom, deltaTime, index, dataStore);
            }
        }
    }
}