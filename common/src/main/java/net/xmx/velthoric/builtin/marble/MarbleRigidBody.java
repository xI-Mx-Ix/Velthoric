/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.marble;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
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
        this.markDataDirty();
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
        var settings = new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);

        settings.setRestitution(0.6f);
        settings.setFriction(0.6f);

        MassProperties massProperties = new MassProperties();
        float volume = (float) ( (4.0 / 3.0) * Math.PI * radius * radius * radius );
        massProperties.setMassAndInertiaOfSolidBox(shapeRef.getLocalBounds().getExtent(), DENSITY * volume);

        settings.setOverrideMassProperties(EOverrideMassProperties.MassAndInertiaProvided);
        settings.setMassPropertiesOverride(massProperties);

        return settings;
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeFloat(radius);
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.radius = buf.readFloat();
    }
}
