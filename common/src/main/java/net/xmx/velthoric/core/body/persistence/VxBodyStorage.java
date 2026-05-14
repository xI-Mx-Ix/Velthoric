/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.persistence;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.persistence.VxChunkBasedStorage;

/**
 * Storage implementation for physics bodies using the generic region system.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxChunkBasedStorage<VxBody, VxSerializedBodyData> {

    /**
     * Initializes the storage handler for physics bodies.
     * <p>
     * Bodies are stored in a dedicated sub-directory within the world folder,
     * using the ".vxb" file extension.
     *
     * @param level The server-side Minecraft level to bind storage to.
     */
    public VxBodyStorage(ServerLevel level) {
        super(level, "bodies", "vxb");
    }

    /**
     * Internal: Serializes a physics body to the byte buffer.
     *
     * @param body   The body instance to save.
     * @param buffer The buffer to write into.
     */
    @Override
    protected void writeSingle(VxBody body, VxByteBuf buffer) {
        VxBodyCodec.serialize(body, buffer);
    }

    /**
     * Internal: Reads serialized body data from the byte buffer.
     *
     * @param buffer The buffer to read from.
     * @return A data object containing the deserialized body state.
     */
    @Override
    protected VxSerializedBodyData readSingle(VxByteBuf buffer) {
        return VxBodyCodec.deserialize(buffer);
    }
}