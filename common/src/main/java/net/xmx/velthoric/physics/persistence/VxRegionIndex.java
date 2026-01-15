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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * A high-performance in-memory index mapping unique identifiers (UUIDs) to {@link RegionPos}.
 * <p>
 * <b>Performance Note:</b>
 * Access to this index ({@link #put}, {@link #get}, {@link #remove}) uses a
 * {@link ConcurrentHashMap}, ensuring O(1) performance with no blocking.
 * This is critical for bulk operations like mass entity removal.
 * <p>
 * <b>Persistence Strategy:</b>
 * Changes mark the index as {@code dirty}. The {@link #save()} method must be called explicitly
 * (usually during a world auto-save or shutdown) to serialize the index and write it to disk.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionIndex {

    private final Path indexPath;
    private final VxIOProcessor ioProcessor;

    /**
     * Primary lookup map. Using ConcurrentHashMap allows for thread-safe access without global locks.
     */
    private final ConcurrentHashMap<UUID, RegionPos> index = new ConcurrentHashMap<>();

    /**
     * Tracks if there are unsaved changes in the index.
     * Used to skip unnecessary disk I/O if the index hasn't changed since the last save.
     */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /**
     * Constructs a new region index.
     *
     * @param storagePath The directory containing the index file.
     * @param indexName   The logical name (e.g., "body", "constraint").
     * @param ioProcessor The dedicated I/O processor to use for saving.
     */
    public VxRegionIndex(Path storagePath, String indexName, VxIOProcessor ioProcessor) {
        this.indexPath = storagePath.resolve(indexName + ".vxidx");
        this.ioProcessor = ioProcessor;
    }

    /**
     * Loads the index from disk synchronously.
     * <p>
     * This is typically called only once during server startup. It includes checksum validation
     * to ensure the index file is not corrupted. If corruption is detected, the index starts empty,
     * relying on region files to potentially rebuild data (though full recovery depends on logic elsewhere).
     */
    public void load() {
        if (!Files.exists(indexPath)) return;

        try {
            byte[] fileBytes = Files.readAllBytes(indexPath);
            if (fileBytes.length < 8) {
                VxMainClass.LOGGER.warn("Region index file {} is too small to be valid.", indexPath);
                return;
            }

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            long storedChecksum = buffer.readLong();

            byte[] dataBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(dataBytes);

            // Verify integrity
            CRC32 crc = new CRC32();
            crc.update(dataBytes);

            if (storedChecksum == crc.getValue()) {
                FriendlyByteBuf dataBuffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(dataBytes));
                while (dataBuffer.isReadable()) {
                    try {
                        UUID id = dataBuffer.readUUID();
                        int x = dataBuffer.readInt();
                        int z = dataBuffer.readInt();
                        index.put(id, new RegionPos(x, z));
                    } catch (IndexOutOfBoundsException e) {
                        break; // End of file
                    }
                }
            } else {
                VxMainClass.LOGGER.error("Region index checksum mismatch for {}. Index file ignored to prevent crashes.", indexPath);
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to load region index from {}", indexPath, e);
        }
    }

    /**
     * Serializes the current index state and queues it for writing if it is marked dirty.
     * <p>
     * The serialization happens on the calling thread (creating a memory snapshot),
     * ensuring that subsequent modifications do not cause {@link java.util.ConcurrentModificationException}.
     * The actual disk write is offloaded to the {@link VxIOProcessor}.
     *
     * @return A {@link CompletableFuture} that completes when the index is written, or immediately if not dirty.
     */
    public CompletableFuture<Void> save() {
        // Atomic check-and-set: If dirty is true, set to false and proceed. If false, return.
        if (!dirty.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        // Estimate size: 16 bytes (UUID) + 8 bytes (2 ints) = 24 bytes per entry. + 128 bytes buffer.
        ByteBuf buffer = Unpooled.buffer(index.size() * 24 + 128);
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);

        try {
            index.forEach((id, pos) -> {
                friendlyBuf.writeUUID(id);
                friendlyBuf.writeInt(pos.x());
                friendlyBuf.writeInt(pos.z());
            });

            byte[] data = new byte[buffer.readableBytes()];
            buffer.getBytes(0, data);

            // Calculate Checksum
            CRC32 crc = new CRC32();
            crc.update(data);
            long checksum = crc.getValue();

            // Create Final Payload: [Checksum (long)] + [Data]
            ByteBuf finalBuf = Unpooled.buffer(data.length + 8);
            finalBuf.writeLong(checksum);
            finalBuf.writeBytes(data);

            byte[] fileContent = new byte[finalBuf.readableBytes()];
            finalBuf.readBytes(fileContent);
            finalBuf.release();

            // Submit to IO Worker
            return ioProcessor.submitWrite(indexPath, fileContent);
        } finally {
            buffer.release();
        }
    }

    /**
     * Associates a UUID with a Region Position.
     * Marks the index as dirty if the value changed.
     *
     * @param id  The unique identifier.
     * @param pos The region position.
     */
    public void put(UUID id, RegionPos pos) {
        RegionPos old = index.put(id, pos);
        if (!pos.equals(old)) {
            dirty.set(true);
        }
    }

    /**
     * Removes a UUID from the index.
     * Marks the index as dirty if the key existed.
     *
     * @param id The unique identifier to remove.
     */
    public void remove(UUID id) {
        if (index.remove(id) != null) {
            dirty.set(true);
        }
    }

    /**
     * Retrieves the Region Position for a given UUID.
     *
     * @param id The unique identifier.
     * @return The {@link RegionPos}, or {@code null} if not found.
     */
    public RegionPos get(UUID id) {
        return index.get(id);
    }
}