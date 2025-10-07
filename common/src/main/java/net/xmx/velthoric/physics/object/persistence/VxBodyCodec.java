/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.persistence;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A utility class for serializing and deserializing {@link VxBody} objects.
 * This codec translates the state of a physics body (including transform, velocity, and
 * custom data) into a byte representation for storage, and vice versa. Deserialization
 * produces a {@link VxSerializedBodyData} record, which acts as an intermediary
 * data-transfer object for reconstructing the body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyCodec {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxBodyCodec() {}

    /**
     * Serializes a {@link VxBody} and its current physics state into a buffer.
     * It writes the body's unique ID, type, transform (position and rotation), velocities,
     * and any custom data provided by the body's implementation.
     *
     * @param object    The physics body to serialize.
     * @param index     The body's index in the {@link VxObjectDataStore}, used to access its live physics state.
     * @param dataStore The data store containing the live physics state arrays.
     * @param buf       The buffer to write the serialized data into.
     */
    public static void serialize(VxBody object, int index, VxObjectDataStore dataStore, VxByteBuf buf) {
        buf.writeUUID(object.getPhysicsId());
        buf.writeUtf(object.getType().getTypeId().toString());

        // Write transform data
        buf.writeDouble(dataStore.posX[index]);
        buf.writeDouble(dataStore.posY[index]);
        buf.writeDouble(dataStore.posZ[index]);
        buf.writeFloat(dataStore.rotX[index]);
        buf.writeFloat(dataStore.rotY[index]);
        buf.writeFloat(dataStore.rotZ[index]);
        buf.writeFloat(dataStore.rotW[index]);

        // Write velocity data
        buf.writeFloat(dataStore.velX[index]);
        buf.writeFloat(dataStore.velY[index]);
        buf.writeFloat(dataStore.velZ[index]);
        buf.writeFloat(dataStore.angVelX[index]);
        buf.writeFloat(dataStore.angVelY[index]);
        buf.writeFloat(dataStore.angVelZ[index]);

        // Write implementation-specific persistence data
        object.writePersistenceData(buf);
    }

    /**
     * Deserializes physics body data from a buffer into a {@link VxSerializedBodyData} record.
     * This record contains all necessary information to recreate the body later.
     * Includes sanity checks for velocity values to prevent physics instabilities from corrupt data.
     *
     * @param buf The buffer to read the serialized data from.
     * @return A {@link VxSerializedBodyData} record, or null if deserialization fails.
     */
    @Nullable
    public static VxSerializedBodyData deserialize(VxByteBuf buf) {
        try {
            UUID id = buf.readUUID();
            ResourceLocation typeId = new ResourceLocation(buf.readUtf());

            VxTransform transform = new VxTransform();
            transform.fromBuffer(buf);

            Vec3 linearVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            Vec3 angularVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());

            // Validate velocity data to prevent issues with non-finite numbers.
            if (!linearVelocity.isFinite() || linearVelocity.isNan() || !angularVelocity.isFinite() || angularVelocity.isNan()) {
                VxMainClass.LOGGER.warn("Deserialized invalid velocity for object {}. Resetting to zero.", id);
                linearVelocity.set(0f, 0f, 0f);
                angularVelocity.set(0f, 0f, 0f);
            }

            // The remaining bytes in the buffer are custom persistence data.
            VxByteBuf persistenceData = new VxByteBuf(buf.readBytes(buf.readableBytes()));

            return new VxSerializedBodyData(typeId, id, transform, linearVelocity, angularVelocity, persistenceData);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics object from data", e);
            return null;
        }
    }
}