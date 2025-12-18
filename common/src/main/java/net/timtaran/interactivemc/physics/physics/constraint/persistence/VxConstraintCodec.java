/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.constraint.persistence;

import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import net.minecraft.network.FriendlyByteBuf;
import net.timtaran.interactivemc.physics.physics.constraint.VxConstraint;

import java.util.UUID;

/**
 * A utility class for serializing and deserializing {@link VxConstraint} objects.
 * This codec handles the conversion between a constraint object and its byte representation,
 * focusing only on the data required to reconstruct the constraint, such as body IDs and settings.
 * It does not handle storage-specific metadata like chunk positions or primary keys.
 *
 * @author xI-Mx-Ix
 */
public final class VxConstraintCodec {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxConstraintCodec() {}

    /**
     * Serializes a {@link VxConstraint} into a buffer.
     * This method writes the essential properties of the constraint, including the IDs
     * of the two bodies it connects, its subtype, and its specific settings data.
     *
     * @param constraint The constraint object to serialize.
     * @param buf        The buffer to write the serialized data to.
     */
    public static void serialize(VxConstraint constraint, FriendlyByteBuf buf) {
        buf.writeUUID(constraint.getBody1Id());
        buf.writeUUID(constraint.getBody2Id());
        buf.writeInt(constraint.getSubType().ordinal());
        buf.writeByteArray(constraint.getSettingsData());
    }

    /**
     * Deserializes a {@link VxConstraint} from a buffer.
     * This method reads the constraint data from the buffer and uses it to construct
     * a new {@link VxConstraint} instance. The unique ID of the constraint must be
     * provided separately as it is not part of the serialized data payload.
     *
     * @param constraintId The unique identifier for the new constraint.
     * @param buf          The buffer to read the serialized data from.
     * @return A new {@link VxConstraint} instance populated with the deserialized data.
     */
    public static VxConstraint deserialize(UUID constraintId, FriendlyByteBuf buf) {
        UUID body1Id = buf.readUUID();
        UUID body2Id = buf.readUUID();
        EConstraintSubType subType = EConstraintSubType.values()[buf.readInt()];
        byte[] settingsData = buf.readByteArray();
        return new VxConstraint(constraintId, body1Id, body2Id, settingsData, subType);
    }
}