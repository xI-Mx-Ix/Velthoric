/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.builtin.marble;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.timtaran.interactivemc.physics.physics.body.sync.accessor.VxServerAccessor;
import net.timtaran.interactivemc.physics.physics.world.VxLayers;
import net.timtaran.interactivemc.physics.network.VxByteBuf;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyType;
import net.timtaran.interactivemc.physics.physics.body.sync.VxDataSerializers;
import net.timtaran.interactivemc.physics.physics.body.sync.VxSynchronizedData;
import net.timtaran.interactivemc.physics.physics.body.type.VxRigidBody;
import net.timtaran.interactivemc.physics.physics.body.type.factory.VxRigidBodyFactory;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A simple, spherical rigid body with high density.
 *
 * @author xI-Mx-Ix
 */
public class MarbleRigidBody extends VxRigidBody {

    public static final VxServerAccessor<Float> DATA_RADIUS = VxServerAccessor.create(MarbleRigidBody.class, VxDataSerializers.FLOAT);
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
        this.setServerData(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return get(DATA_RADIUS);
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