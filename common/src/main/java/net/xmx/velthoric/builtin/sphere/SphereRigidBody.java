/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
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
    public ShapeSettings createShapeSettings() {
        return new SphereShapeSettings(this.radius);
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        var settings = new BodyCreationSettings();
        settings.setShape(shapeRef);
        settings.setMotionType(EMotionType.Dynamic);
        settings.setObjectLayer(VxLayers.DYNAMIC);
        // The VxObjectManager will set the final position and rotation from the data store.
        return settings;
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