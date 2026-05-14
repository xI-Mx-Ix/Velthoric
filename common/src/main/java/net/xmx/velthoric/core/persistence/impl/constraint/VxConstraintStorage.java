/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.impl.constraint;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.constraint.VxConstraint;
import net.xmx.velthoric.core.persistence.VxChunkBasedStorage;

import java.util.UUID;

/**
 * Storage implementation for physics constraints using the generic region system.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintStorage extends VxChunkBasedStorage<VxConstraint, VxSerializedConstraintData> {

    /**
     * Initializes the storage handler for physics constraints.
     * <p>
     * Constraints are stored in a dedicated sub-directory within the world folder,
     * using the ".vxc" file extension.
     *
     * @param level The server-side Minecraft level to bind storage to.
     */
    public VxConstraintStorage(ServerLevel level) {
        super(level, "constraints", "vxc");
    }

    /**
     * Internal: Serializes a single physics constraint to the byte buffer.
     * <p>
     * This method manually handles the persistence of the unique identifier
     * before delegating settings serialization to the codec.
     *
     * @param constraint The constraint instance to save.
     * @param buffer     The buffer to write into.
     */
    @Override
    protected void writeSingle(VxConstraint constraint, VxByteBuf buffer) {
        // Write the unique identifier as the header for each entry
        buffer.writeUUID(constraint.getConstraintId());
        VxConstraintCodec.serialize(constraint, buffer);
    }

    /**
     * Internal: Reconstitutes a physics constraint payload from the byte buffer.
     *
     * @param buffer The buffer to read from.
     * @return A {@link VxSerializedConstraintData} record for deferred loading.
     */
    @Override
    protected VxSerializedConstraintData readSingle(VxByteBuf buffer) {
        UUID id = buffer.readUUID();
        return VxConstraintCodec.deserialize(id, buffer);
    }
}