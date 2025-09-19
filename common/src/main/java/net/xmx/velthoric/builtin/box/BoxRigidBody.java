/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class BoxRigidBody extends VxRigidBody {

    private Vec3 halfExtents;
    private BoxColor color;

    public BoxRigidBody(VxObjectType<BoxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
        this.color = BoxColor.RED;
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.halfExtents = halfExtents;
        this.markCustomDataDirty();
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    public void setColor(BoxColor color) {
        this.color = color;
        this.markCustomDataDirty();
    }

    public BoxColor getColor() {
        return color;
    }

    @Override
    public ShapeSettings createShapeSettings() {
        return new BoxShapeSettings(this.halfExtents);
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        var settings = new BodyCreationSettings();
        settings.setShape(shapeRef);
        settings.setMotionType(EMotionType.Dynamic);
        settings.setObjectLayer(VxLayers.DYNAMIC);
        settings.setRestitution(0.4f);
        // The VxObjectManager will set the final position and rotation from the data store.
        return settings;
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeVec3(halfExtents);
        buf.writeInt(color.ordinal());
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.halfExtents = buf.readVec3();
        this.color = BoxColor.values()[buf.readInt()];
    }
}