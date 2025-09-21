/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class SphereRigidBody extends VxRigidBody {

    private float radius;

    public SphereRigidBody(VxObjectType<SphereRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.radius = 0.5f;
    }

    public void setRadius(float radius) {
        this.radius = radius > 0 ? radius : 0.5f;
        this.markCustomDataDirty();
    }

    public float getRadius() {
        return radius;
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new SphereShapeSettings(this.radius);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);

            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeFloat(this.radius);
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.radius = buf.readFloat();
    }
}