/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A rigid body with a spherical shape.
 *
 * @author xI-Mx-Ix
 */
public class SphereRigidBody extends VxBody {

    public static final VxServerAccessor<Float> DATA_RADIUS = VxServerAccessor.create(SphereRigidBody.class, VxDataSerializers.FLOAT);

    public SphereRigidBody(VxBodyType type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public SphereRigidBody(VxBodyType type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_RADIUS, 0.5f);
    }

    public void setRadius(float radius) {
        this.setServerData(DATA_RADIUS, radius > 0 ? radius : 0.5f);
    }

    public float getRadius() {
        return get(DATA_RADIUS);
    }

    public static int createJoltBody(VxBody body, net.xmx.velthoric.core.body.type.factory.VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new SphereShapeSettings(body.get(DATA_RADIUS));
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxPhysicsLayers.MOVING);
            return factory.create(shapeSettings, bcs);
        }
    }

    public static void writePersistence(VxBody body, VxByteBuf buf) {
        buf.writeFloat(body.get(DATA_RADIUS));
    }

    public static void readPersistence(VxBody body, VxByteBuf buf) {
        body.setServerData(DATA_RADIUS, buf.readFloat());
    }
}