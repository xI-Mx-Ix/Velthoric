/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.persistence;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.persistence.VxChunkBasedStorage;

import java.util.UUID;

/**
 * Storage implementation for physics constraints using the generic region system.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintStorage extends VxChunkBasedStorage<VxConstraint, VxConstraint> {

    public VxConstraintStorage(ServerLevel level) {
        super(level, "constraints", "vxc");
    }

    @Override
    protected void writeSingle(VxConstraint constraint, VxByteBuf buffer) {
        // Need to write ID manually as the Codec might expect it known or separate
        buffer.writeUUID(constraint.getConstraintId());
        VxConstraintCodec.serialize(constraint, buffer);
    }

    @Override
    protected VxConstraint readSingle(VxByteBuf buffer) {
        UUID id = buffer.readUUID();
        return VxConstraintCodec.deserialize(id, buffer);
    }
}