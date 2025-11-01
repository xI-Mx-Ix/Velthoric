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
import net.xmx.velthoric.physics.world.VxLayers;
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
 * A simple, spherical rigid body with high density.
 *
 * @author xI-Mx-Ix
 */
public class MarbleRigidBody extends VxRigidBody {

    public static final VxDataAccessor<Float> DATA_RADIUS = VxDataAccessor.create(MarbleRigidBody.class, VxDataSerializers.FLOAT);
    private static final float DENSITY = 6700f;

    /**
     * Server-side constructor.
     */
    public MarbleRigidBody(VxBodyType<MarbleRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public MarbleRigidBody(VxBodyType<MarbleRigidBody> type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_RADIUS, 0.15f);
    }

    public void setRadius(float radius) {
        this.setSyncData(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return getSyncData(DATA_RADIUS);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        float radius = getRadius();
        try (
                ShapeSettings shapeSettings = new SphereShapeSettings(radius);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
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

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeFloat(getRadius());
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        setRadius(buf.readFloat());
    }
}