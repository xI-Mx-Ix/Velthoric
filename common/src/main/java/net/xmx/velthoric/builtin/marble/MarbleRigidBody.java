/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.marble;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.body.factory.VxRigidBodyFactory;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.VxBodyType;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A simple, spherical rigid body with high density.
 *
 * @author xI-Mx-Ix
 */
public class MarbleRigidBody extends VxBody {

    public static final VxServerAccessor<Float> DATA_RADIUS = VxServerAccessor.create(MarbleRigidBody.class, VxDataSerializers.FLOAT);
    private static final float DENSITY = 6700f;

    public MarbleRigidBody(VxBodyType type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public MarbleRigidBody(VxBodyType type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_RADIUS, 0.15f);
    }

    public void setRadius(float radius) {
        this.setServerData(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return get(DATA_RADIUS);
    }

    public static int createJoltBody(VxBody body, VxRigidBodyFactory factory) {
        float radius = body.get(DATA_RADIUS);
        try (
                ShapeSettings shapeSettings = new SphereShapeSettings(radius);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxPhysicsLayers.MOVING);
            bcs.setRestitution(0.3f);
            bcs.setFriction(0.2f);

            try (var shapeResult = shapeSettings.create(); var shapeRef = shapeResult.get()) {
                try (MassProperties massProperties = new MassProperties()) {
                    float volume = (float) ((4.0 / 3.0) * Math.PI * radius * radius * radius);
                    massProperties.setMassAndInertiaOfSolidBox(shapeRef.getLocalBounds().getExtent(), DENSITY * volume);
                    bcs.setOverrideMassProperties(EOverrideMassProperties.MassAndInertiaProvided);
                    bcs.setMassPropertiesOverride(massProperties);
                }
            }
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