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
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * @author xI-Mx-Ix
 */
public class SphereRigidBody extends VxRigidBody {

    private static final VxDataAccessor<Float> DATA_RADIUS = createAccessor(VxDataSerializers.FLOAT);

    public SphereRigidBody(VxObjectType<SphereRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_RADIUS, 0.5f);
    }

    public void setRadius(float radius) {
        this.setSyncData(DATA_RADIUS, radius > 0 ? radius : 0.5f);
    }

    public float getRadius() {
        return getSyncData(DATA_RADIUS);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new SphereShapeSettings(this.getRadius());
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeFloat(getRadius());
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        setRadius(buf.readFloat());
    }
}