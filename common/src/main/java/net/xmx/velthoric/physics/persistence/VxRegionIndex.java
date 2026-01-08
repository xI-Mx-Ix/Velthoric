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
 * Manages an index file that maps a unique key (UUID) to a specific region position (x, z).
 * <p>
 * This index allows the system to locate the specific region file containing a body or constraint
 * in O(1) time without scanning all region files. It acts as a global lookup table for the
 * storage subsystem.
 * <p>
 * <b>Data Integrity Strategy:</b>
 * To prevent data corruption (e.g., during server crashes or power failures), this class implements
 * a strict <b>Atomic Write-and-Rename</b> strategy. Data is never written directly to the live index file.
 * Instead, it is written to a temporary file, validated with a Checksum (CRC32), and then atomically
 * moved to the final location by the Operating System.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionIndex {

    /**
     * The path to the actual index file on disk (e.g., "body.vxidx").
     */
    private final Path indexPath;

    /**
     * The thread-safe in-memory map of UUIDs to Region Positions.
     */
    private final ConcurrentHashMap<UUID, RegionPos> index = new ConcurrentHashMap<>();

    /**
     * A flag indicating if the in-memory index has changes that have not yet been written to disk.
     * This allows the system to skip unnecessary disk I/O if the index hasn't changed.
     */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /**
     * Constructs a new region index manager.
     *
     * @param storagePath The base directory where the index file should be stored.
     * @param indexName   The logical name of the index (e.g., "body", "constraint").
     */
    public VxRegionIndex(Path storagePath, String indexName) {
        this.indexPath = storagePath.resolve(indexName + ".vxidx");
    }

    /**
     * Loads the index data from the file system into memory.
     * <p>
     * This method performs several safety checks:
     * <ol>
     *     <li>Cleans up any stale temporary files left over from previous crashes.</li>
     *     <li>Checks if the file exists and is of valid size.</li>
     *     <li>Reads the file and verifies the CRC32 checksum against the stored data.</li>
     *     <li>If valid, deserializes the data into the concurrent map.</li>
     * </ol>
     */
    public void load() {
        Path tempPath = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
        try {
            // Clean up any temporary files from a previous, possibly crashed, session.
            // This ensures we have a clean slate for writing later.
            Files.deleteIfExists(tempPath);
        } catch (IOException e) {
            VxMainClass.LOGGER.warn("Failed to delete stale temporary index file: {}", tempPath, e);
        }

        // If the index file doesn't exist yet (new world), we simply start empty.
        if (!Files.exists(indexPath)) return;

        try {
            byte[] fileBytes = Files.readAllBytes(indexPath);

            // A valid file must contain at least an 8-byte checksum header.
            if (fileBytes.length < 8) {
                VxMainClass.LOGGER.warn("Region index file {} is too small to be valid. Ignoring.", indexPath);
                return;
            }

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            long storedChecksum = buffer.readLong();

            // The rest of the buffer contains the actual payload data.
            byte[] dataBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(dataBytes);

            // Calculate the actual checksum of the data we just read.
            CRC32 crc = new CRC32();
            crc.update(dataBytes);

            // Validation: Ensure the data hasn't been corrupted on disk.
            if (storedChecksum != crc.getValue()) {
                VxMainClass.LOGGER.error("Checksum mismatch for region index file {}. The file may be corrupt. Ignoring content to prevent crashes.", indexPath);
                return;
            }

            // If checksum is valid, proceed with deserialization.
            FriendlyByteBuf dataBuffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(dataBytes));
            while (dataBuffer.isReadable()) {
                try {
                    UUID id = dataBuffer.readUUID();
                    int x = dataBuffer.readInt();
                    int z = dataBuffer.readInt();
                    index.put(id, new RegionPos(x, z));
                } catch (IndexOutOfBoundsException e) {
                    VxMainClass.LOGGER.error("Unexpected end of stream while reading index file {}", indexPath);
                    break;
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to load region index from {}", indexPath, e);
        }
    }

    /**
     * Schedules an asynchronous save of the index to disk.
     * <p>
     * This method checks the {@link #dirty} flag. If the index hasn't changed, it returns immediately.
     * If changes exist, it schedules a background task to flush the data to disk.
     * <p>
     * The save operation uses {@link CompletableFuture#runAsync} which uses the common ForkJoinPool.
     * This is acceptable for the index as it is a single file and generally less throughput-intensive
     * than the bulk region data.
     *
     * @return A {@link CompletableFuture} that completes when the save operation is finished (or immediately if clean).
     */
    public CompletableFuture<Void> save() {
        // Optimization: Do not waste thread resources if there are no changes.
        if (!dirty.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            // Atomic check-and-set. This ensures that if multiple threads call save() simultaneously,
            // only one of them actually performs the I/O, preventing race conditions.
            if (dirty.compareAndSet(true, false)) {
                flushToDisk();
            }
        }).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to asynchronously save region index to {}", indexPath, ex);
            // If the save failed, mark it as dirty again so we retry on the next cycle.
            dirty.set(true);
            return null;
        });
    }

    /**
     * Performs the synchronous, blocking file write operation.
     * <p>
     * This method:
     * <ol>
     *     <li>Serializes the entire in-memory index.</li>
     *     <li>Calculates a CRC32 Checksum.</li>
     *     <li>Writes [Checksum + Data] to a temporary file (.tmp).</li>
     *     <li>Forces the OS to flush buffers to physical disk.</li>
     *     <li>Atomically moves (renames) the temp file to the final index name, replacing the old one.</li>
     * </ol>
     */
    private void flushToDisk() {
        // If the index is empty, we don't need a file. Clean up to save space.
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
            // 1. Serialize data to memory buffer
            FriendlyByteBuf friendlyDataBuf = new FriendlyByteBuf(dataBuf);
            index.forEach((id, pos) -> {
                friendlyDataBuf.writeUUID(id);
                friendlyDataBuf.writeInt(pos.x());
                friendlyDataBuf.writeInt(pos.z());
            });

            byte[] dataBytes = new byte[dataBuf.readableBytes()];
            dataBuf.readBytes(dataBytes);

            // 2. Compute Integrity Checksum
            CRC32 crc = new CRC32();
            crc.update(dataBytes);
            long checksum = crc.getValue();

            // 3. Prepare Final Output Buffer (Header + Payload)
            ByteBuffer finalBuffer = ByteBuffer.allocate(8 + dataBytes.length);
            finalBuffer.putLong(checksum);
            finalBuffer.put(dataBytes);
            finalBuffer.flip();

            // 4. Write to Temporary File
            Files.createDirectories(indexPath.getParent());
            try (FileChannel channel = FileChannel.open(tempPath,
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(finalBuffer);
                // Important: Force the OS to write metadata and content to disk to prevent data loss on power failure.
                channel.force(true);
            }

            // 5. Atomic Swap
            // This ensures that at any point in time, the 'indexPath' file is valid.
            // We never write partially to the live file.
            Files.move(tempPath, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            // We propagate the exception to the CompletableFuture to be handled by the caller or logging logic.
            throw new RuntimeException("Failed to save region index to " + indexPath, e);
        } finally {
            // Always release Netty buffers to prevent memory leaks in the native heap.
            if (dataBuf.refCnt() > 0) {
                dataBuf.release();
            }
        }
    }

    /**
     * Adds or updates an entry in the index map.
     * <p>
     * If the mapping for the given UUID changes, the dirty flag is set to true,
     * signaling that a save is required.
     *
     * @param id  The UUID key of the object (Body/Constraint).
     * @param pos The RegionPos where this object is stored.
     */
    public void put(UUID id, RegionPos pos) {
        RegionPos oldPos = index.put(id, pos);
        // Only mark dirty if the position actually changed or was new.
        if (!pos.equals(oldPos)) {
            dirty.set(true);
        }
    }

    /**
     * Retrieves the region position for a given UUID.
     *
     * @param id The UUID key to look up.
     * @return The corresponding {@link RegionPos}, or null if the ID is not in the index.
     */
    public RegionPos get(UUID id) {
        return index.get(id);
    }

    /**
     * Removes an entry from the index.
     * <p>
     * If an entry existed and was removed, the dirty flag is set to true.
     *
     * @param id The UUID key of the entry to remove.
     */
    public void remove(UUID id) {
        if (index.remove(id) != null) {
            dirty.set(true);
        }
    }
}