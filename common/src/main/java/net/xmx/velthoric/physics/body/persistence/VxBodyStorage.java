/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxChunkBasedStorage;

/**
 * Storage implementation for physics bodies using the generic region system.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxChunkBasedStorage<VxBody, VxSerializedBodyData> {

    public VxBodyStorage(ServerLevel level) {
        super(level, "bodies", "vxb");
    }

    @Override
    protected void writeSingle(VxBody body, VxByteBuf buffer) {
        VxBodyCodec.serialize(body, buffer);
    }

    @Override
    protected VxSerializedBodyData readSingle(VxByteBuf buffer) {
        return VxBodyCodec.deserialize(buffer);
    }
}