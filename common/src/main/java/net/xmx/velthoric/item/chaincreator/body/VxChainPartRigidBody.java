/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
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
 * Represents a single segment of a physics-based chain.
 * This rigid body is shaped like an elongated box to simulate a chain link.
 * Its dimensions are set upon creation, synchronized to clients, and persisted to disk.
 *
 * @author xI-Mx-Ix
 */
public class VxChainPartRigidBody extends VxRigidBody {

    private static final VxDataAccessor<Float> DATA_LENGTH = VxDataAccessor.create(VxChainPartRigidBody.class, VxDataSerializers.FLOAT);
    private static final VxDataAccessor<Float> DATA_RADIUS = VxDataAccessor.create(VxChainPartRigidBody.class, VxDataSerializers.FLOAT);

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
        buf.writeFloat(getSyncData(DATA_LENGTH));
        buf.writeFloat(getSyncData(DATA_RADIUS));
    }

    /**
     * Reads the body's dynamic dimensions from a buffer for loading.
     * @param buf The buffer to read from.
     */
    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        this.setSyncData(DATA_LENGTH, buf.readFloat());
        this.setSyncData(DATA_RADIUS, buf.readFloat());
    }

    /**
     * Creates the Jolt physics body for this chain link using its instance-specific dimensions.
     * @param factory The factory to create the Jolt body.
     * @return The integer ID of the created Jolt body.
     */
    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        float currentRadius = getSyncData(DATA_RADIUS);
        float currentLength = getSyncData(DATA_LENGTH);

        Vec3 halfExtents = new Vec3(currentRadius, currentLength / 2.0f, currentRadius);
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(halfExtents);
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
        return this.getSyncData(DATA_LENGTH);
    }

    /**
     * @return The synchronized radius of this chain part.
     */
    public float getRadius() {
        return this.getSyncData(DATA_RADIUS);
    }

    /**
     * @return The data accessor for the length property.
     */
    public static VxDataAccessor<Float> getLengthAccessor() {
        return DATA_LENGTH;
    }

    /**
     * @return The data accessor for the radius property.
     */
    public static VxDataAccessor<Float> getRadiusAccessor() {
        return DATA_RADIUS;
    }
}