/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.timtaran.interactivemc.physics.init.VxMainClass;
import net.timtaran.interactivemc.physics.physics.persistence.VxAbstractRegionStorage.RegionPos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * Manages an index file that maps a unique key (UUID) to a region position.
 * This implementation is hardened against data corruption by using atomic write operations
 * (write-to-temp-and-rename) and checksum validation.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionIndex {

    private final Path indexPath;
    private final ConcurrentHashMap<UUID, RegionPos> index = new ConcurrentHashMap<>();
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
     * It performs a checksum validation to ensure data integrity. If the checksum
     * does not match, the file is considered corrupt and is ignored.
     */
    public void load() {
        Path tempPath = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
        try {
            // Clean up any temporary files from a previous, possibly crashed, session.
            Files.deleteIfExists(tempPath);
        } catch (IOException e) {
            VxMainClass.LOGGER.warn("Failed to delete stale temporary index file: {}", tempPath, e);
        }

        if (!Files.exists(indexPath)) return;

        try {
            byte[] fileBytes = Files.readAllBytes(indexPath);
            if (fileBytes.length < 8) { // A valid file must contain at least an 8-byte checksum.
                VxMainClass.LOGGER.warn("Region index file {} is too small to be valid. Ignoring.", indexPath);
                return;
            }

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            long storedChecksum = buffer.readLong();

            // The rest of the buffer contains the actual data.
            byte[] dataBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(dataBytes);

            // Verify the data against the stored checksum.
            CRC32 crc = new CRC32();
            crc.update(dataBytes);
            if (storedChecksum != crc.getValue()) {
                VxMainClass.LOGGER.error("Checksum mismatch for region index file {}. The file may be corrupt. Ignoring.", indexPath);
                return;
            }

            // If checksum is valid, proceed with deserialization.
            FriendlyByteBuf dataBuffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(dataBytes));
            while (dataBuffer.isReadable()) {
                UUID id = dataBuffer.readUUID();
                int x = dataBuffer.readInt();
                int z = dataBuffer.readInt();
                index.put(id, new RegionPos(x, z));
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to load region index from {}", indexPath, e);
        }
    }

    /**
     * Saves the in-memory index to its file on disk if it has changed.
     * This method schedules the save operation to be performed asynchronously.
     *
     * @return a {@link CompletableFuture} that completes when the save operation is finished.
     */
    public CompletableFuture<Void> save() {
        if (!dirty.get()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            if (dirty.compareAndSet(true, false)) {
                flushToDisk();
            }
        }, VxPersistenceManager.getExecutor()).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to asynchronously save region index to {}", indexPath, ex);
            dirty.set(true); // If saving failed, mark as dirty again to retry on the next save cycle.
            return null;
        });
    }

    /**
     * Performs the synchronous, blocking file write operation for the index.
     * This method uses an atomic write-and-rename approach to prevent corruption.
     * It writes the data to a temporary file first, and only upon successful completion,
     * it renames the temporary file to the final destination file.
     */
    private void flushToDisk() {
        if (index.isEmpty()) {
            try {
                Files.deleteIfExists(indexPath);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty region index file {}", indexPath, e);
            }
            return;
        }

        Path tempPath = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
        ByteBuf dataBuf = Unpooled.buffer();
        try {
            // Serialize the index data into a buffer.
            FriendlyByteBuf friendlyDataBuf = new FriendlyByteBuf(dataBuf);
            index.forEach((id, pos) -> {
                friendlyDataBuf.writeUUID(id);
                friendlyDataBuf.writeInt(pos.x());
                friendlyDataBuf.writeInt(pos.z());
            });

            byte[] dataBytes = new byte[dataBuf.readableBytes()];
            dataBuf.readBytes(dataBytes);

            // Calculate a checksum of the serialized data.
            CRC32 crc = new CRC32();
            crc.update(dataBytes);
            long checksum = crc.getValue();

            // Prepare a final buffer with the checksum followed by the data.
            ByteBuffer finalBuffer = ByteBuffer.allocate(8 + dataBytes.length);
            finalBuffer.putLong(checksum);
            finalBuffer.put(dataBytes);
            finalBuffer.flip();

            // Write the final buffer to a temporary file.
            Files.createDirectories(indexPath.getParent());
            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(finalBuffer);
            }

            // Atomically move the temporary file to the final destination, replacing the old one.
            Files.move(tempPath, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            // Let the CompletableFuture handle the exception.
            throw new RuntimeException("Failed to save region index to " + indexPath, e);
        } finally {
            if (dataBuf.refCnt() > 0) {
                dataBuf.release();
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