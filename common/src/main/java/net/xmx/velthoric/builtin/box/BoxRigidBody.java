/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
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
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(this.halfExtents);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setRestitution(0.01f);
            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writeSyncData(VxByteBuf buf) {
        buf.writeFloat(halfExtents.getX());
        buf.writeFloat(halfExtents.getY());
        buf.writeFloat(halfExtents.getZ());
        buf.writeInt(color.ordinal());
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        writeSyncData(buf);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        this.halfExtents = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.color = BoxColor.values()[buf.readInt()];
    }
}