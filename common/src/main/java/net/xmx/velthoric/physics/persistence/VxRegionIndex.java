/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage.RegionPos;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages an index file that maps a unique key (UUID) to a region position.
 * This allows for quick lookups to find which region file contains the data for a specific
 * object without having to scan all region files.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionIndex {

    /** The path to the index file on disk. */
    private final Path indexPath;
    /** The in-memory representation of the index. */
    private final ConcurrentHashMap<UUID, RegionPos> index = new ConcurrentHashMap<>();
    /** A flag indicating if the in-memory index has changes that need to be saved to disk. */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /**
     * Constructs a new region index.
     *
     * @param storagePath The base path of the storage system.
     * @param indexName   The name for the index file (e.g., "body").
     */
    public VxRegionIndex(Path storagePath, String indexName) {
        this.indexPath = storagePath.resolve(indexName + ".vxidx");
    }

    /**
     * Loads the index from its file on disk into memory.
     */
    public void load() {
        if (!Files.exists(indexPath)) return;

        try {
            byte[] fileBytes = Files.readAllBytes(indexPath);
            if (fileBytes.length == 0) return;

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            // Read key-value pairs until the buffer is empty.
            while (buffer.isReadable()) {
                UUID id = buffer.readUUID();
                int x = buffer.readInt();
                int z = buffer.readInt();
                index.put(id, new RegionPos(x, z));
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load region index from {}", indexPath, e);
        }
    }

    /**
     * Saves the in-memory index to its file on disk, if it is dirty.
     */
    public void save() {
        // Only save if there are changes. compareAndSet atomically checks and resets the flag.
        if (!dirty.getAndSet(false)) return;

        // If the index is now empty, delete the file.
        if (index.isEmpty()) {
            try {
                Files.deleteIfExists(indexPath);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty region index file {}", indexPath, e);
            }
            return;
        }

        ByteBuf masterBuf = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyMasterBuf = new FriendlyByteBuf(masterBuf);
            // Write all entries to the buffer.
            index.forEach((id, pos) -> {
                friendlyMasterBuf.writeUUID(id);
                friendlyMasterBuf.writeInt(pos.x());
                friendlyMasterBuf.writeInt(pos.z());
            });

            Files.createDirectories(indexPath.getParent());
            // Write the buffer to the file, overwriting any existing content.
            try (FileChannel channel = FileChannel.open(indexPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(masterBuf.nioBuffer());
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save region index to {}", indexPath, e);
        } finally {
            if (masterBuf.refCnt() > 0) {
                masterBuf.release();
            }
        }
    }

    /**
     * Adds or updates an entry in the index.
     *
     * @param id  The UUID key.
     * @param pos The RegionPos value.
     */
    public void put(UUID id, RegionPos pos) {
        RegionPos oldPos = index.put(id, pos);
        // Mark as dirty only if the value actually changed.
        if (!pos.equals(oldPos)) {
            dirty.set(true);
        }
    }

    /**
     * Retrieves the region position for a given UUID.
     *
     * @param id The UUID key to look up.
     * @return The corresponding {@link RegionPos}, or null if not found.
     */
    public RegionPos get(UUID id) {
        return index.get(id);
    }

    /**
     * Removes an entry from the index.
     *
     * @param id The UUID key of the entry to remove.
     */
    public void remove(UUID id) {
        if (index.remove(id) != null) {
            dirty.set(true);
        }
    }
}