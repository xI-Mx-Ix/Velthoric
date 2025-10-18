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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A simple physics body with a box shape.
 *
 * @author xI-Mx-Ix
 */
public class BoxRigidBody extends VxRigidBody {

    public static final VxDataAccessor<Vec3> DATA_HALF_EXTENTS = VxDataAccessor.create(BoxRigidBody.class, VxDataSerializers.VEC3);
    public static final VxDataAccessor<Integer> DATA_COLOR_ORDINAL = VxDataAccessor.create(BoxRigidBody.class, VxDataSerializers.INTEGER);

    /**
     * Server-side constructor.
     */
    public BoxRigidBody(VxBodyType<BoxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public BoxRigidBody(VxBodyType<BoxRigidBody> type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_HALF_EXTENTS, new Vec3(0.5f, 0.5f, 0.5f));
        builder.define(DATA_COLOR_ORDINAL, BoxColor.RED.ordinal());
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.setSyncData(DATA_HALF_EXTENTS, halfExtents);
    }

    public Vec3 getHalfExtents() {
        return getSyncData(DATA_HALF_EXTENTS);
    }

    public void setColor(BoxColor color) {
        this.setSyncData(DATA_COLOR_ORDINAL, color.ordinal());
    }

    public BoxColor getColor() {
        int ordinal = getSyncData(DATA_COLOR_ORDINAL);
        return (ordinal >= 0 && ordinal < BoxColor.values().length) ? BoxColor.values()[ordinal] : BoxColor.RED;
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(this.getHalfExtents());
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setRestitution(0.01f);
            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        Vec3 halfExtents = getSyncData(DATA_HALF_EXTENTS);
        buf.writeVec3(halfExtents);
        buf.writeInt(getSyncData(DATA_COLOR_ORDINAL));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        this.setSyncData(DATA_HALF_EXTENTS, buf.readVec3());
        this.setSyncData(DATA_COLOR_ORDINAL, buf.readInt());
    }
}