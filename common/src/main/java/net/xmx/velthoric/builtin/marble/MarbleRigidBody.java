/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.marble;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class MarbleRigidBody extends VxRigidBody {

    private float radius;
    private static final float DENSITY = 6700f;

    public MarbleRigidBody(VxObjectType<MarbleRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.radius = 0.15f;
    }

    public void setRadius(float radius) {
        this.radius = radius;
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
            bcs.setRestitution(0.6f);
            bcs.setFriction(0.6f);

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
    public void writeSyncData(VxByteBuf buf) {
        buf.writeFloat(radius);
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        writeSyncData(buf);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        this.radius = buf.readFloat();
    }
}