/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.chaincreator.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
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
 * Represents a single segment of a physics-based chain.
 * This rigid body is shaped like an elongated box to simulate a chain link.
 * Its dimensions are set upon creation, synchronized to clients, and persisted to disk.
 *
 * @author xI-Mx-Ix
 */
public class VxChainPartRigidBody extends VxRigidBody {

    private static final VxServerAccessor<Float> DATA_LENGTH = VxServerAccessor.create(VxChainPartRigidBody.class, VxDataSerializers.FLOAT);
    private static final VxServerAccessor<Float> DATA_RADIUS = VxServerAccessor.create(VxChainPartRigidBody.class, VxDataSerializers.FLOAT);

    /**
     * Server-side constructor.
     * @param type The body type definition.
     * @param world The physics world this body belongs to.
     * @param id The unique UUID for this body.
     */
    public VxChainPartRigidBody(VxBodyType<VxChainPartRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     * @param type The body type definition.
     * @param id The unique UUID for this body.
     */
    @Environment(EnvType.CLIENT)
    public VxChainPartRigidBody(VxBodyType<VxChainPartRigidBody> type, UUID id) {
        super(type, id);
    }

    /**
     * Defines synchronized data properties for this body.
     * The length and radius are synced from server to client.
     */
    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_LENGTH, 0.8f);
        builder.define(DATA_RADIUS, 0.1f);
    }

    /**
     * Writes the body's dynamic dimensions to a buffer for saving.
     * @param buf The buffer to write to.
     */
    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        buf.writeFloat(get(DATA_LENGTH));
        buf.writeFloat(get(DATA_RADIUS));
    }

    /**
     * Reads the body's dynamic dimensions from a buffer for loading.
     * @param buf The buffer to read from.
     */
    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        this.setServerData(DATA_LENGTH, buf.readFloat());
        this.setServerData(DATA_RADIUS, buf.readFloat());
    }

    /**
     * Creates the Jolt physics body for this chain link using its instance-specific dimensions.
     * The shape is calculated to ensure the total length matches the synchronized length data,
     * accounting for the hemispherical caps of the capsule shape.
     *
     * @param factory The factory to create the Jolt body.
     * @return The integer ID of the created Jolt body.
     */
    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        float currentRadius = get(DATA_RADIUS);
        float currentLength = get(DATA_LENGTH);

        // The TaperedCapsuleShape defines height as the half-height of the cylindrical part only.
        // Total Length = (2 * halfCylinderHeight) + (2 * radius).
        // Therefore, we must subtract the radius from the desired half-length to prevent overlap at the pivots.
        float halfCylinderHeight = (currentLength / 2.0f) - currentRadius;

        // Ensure the cylinder has a valid height if the length is very small relative to the radius.
        if (halfCylinderHeight < 0.001f) {
            halfCylinderHeight = 0.001f;
        }

        try (
                ShapeSettings shapeSettings = new TaperedCapsuleShapeSettings(halfCylinderHeight, currentRadius, currentRadius);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setFriction(0.5f);
            bcs.setMotionQuality(EMotionQuality.LinearCast);
            bcs.setRestitution(0.1f);
            return factory.create(shapeSettings, bcs);
        }
    }

    /**
     * @return The synchronized length of this chain part.
     */
    public float getLength() {
        return this.get(DATA_LENGTH);
    }

    /**
     * @return The synchronized radius of this chain part.
     */
    public float getRadius() {
        return this.get(DATA_RADIUS);
    }

    /**
     * @return The data accessor for the length property.
     */
    public static VxServerAccessor<Float> getLengthAccessor() {
        return DATA_LENGTH;
    }

    /**
     * @return The data accessor for the radius property.
     */
    public static VxServerAccessor<Float> getRadiusAccessor() {
        return DATA_RADIUS;
    }
}