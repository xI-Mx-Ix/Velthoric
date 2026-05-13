/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.ignore;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.persistence.VxChunkBasedStorage;
import net.xmx.velthoric.network.VxByteBuf;

/**
 * Chunk-based storage for body pair collision ignores.
 *
 * @author xI-Mx-Ix
 */
public class VxIgnoreStorage extends VxChunkBasedStorage<VxBodyPairIgnore, VxBodyPairIgnore> {

    /**
     * Initializes the storage handler for collision ignores.
     * <p>
     * Ignores are stored in a dedicated sub-directory within the world folder,
     * using the ".vxi" file extension.
     *
     * @param level The server-side Minecraft level to bind storage to.
     */
    public VxIgnoreStorage(ServerLevel level) {
        super(level, "ignores", "vxi");
    }

    /**
     * Internal: Writes a single ignore pair to the byte buffer.
     *
     * @param ignore The ignore pair instance to serialize.
     * @param buffer The buffer to write into.
     */
    @Override
    protected void writeSingle(VxBodyPairIgnore ignore, VxByteBuf buffer) {
        VxIgnoreCodec.serialize(ignore, buffer);
    }

    /**
     * Internal: Reads a single ignore pair from the byte buffer.
     *
     * @param buffer The buffer to read from.
     * @return A reconstituted ignore pair.
     */
    @Override
    protected VxBodyPairIgnore readSingle(VxByteBuf buffer) {
        return VxIgnoreCodec.deserialize(buffer);
    }
}