/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence.region;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;

/**
 * Manages a single region file containing physics data for a 32x32 chunk area.
 * <p>
 * <b>Extended File Format Specification:</b>
 * Unlike the standard Minecraft Anvil format (which uses a 4KB header with packed 4-byte entries),
 * this implementation uses an <b>8KB header</b> to support larger data blobs per chunk.
 * <ul>
 *     <li><b>Header:</b> The first 8192 bytes (2 sectors) contain the location table.</li>
 *     <li><b>Capacity:</b> 1024 entries (32x32 chunks).</li>
 *     <li><b>Entry Format (8 bytes):</b>
 *         <ul>
 *             <li>Bytes 0-3: <b>Sector Offset</b> (Integer) - The index of the start sector.</li>
 *             <li>Bytes 4-7: <b>Sector Count</b> (Integer) - The number of 4KB sectors reserved.</li>
 *         </ul>
 *     </li>
 *     <li><b>Sectors:</b> Data is stored in 4KB aligned blocks. Sector 0 and 1 are reserved for the header.</li>
 * </ul>
 * <p>
 * <b>Features:</b>
 * <ul>
 *     <li><b>No Size Limit:</b> Supports chunks significantly larger than 1MB (up to Terabytes theoretically).</li>
 *     <li><b>Auto-Pruning:</b> If all data is deleted from the file, the file is automatically closed and deleted from the disk.</li>
 *     <li><b>Space Management:</b> Uses a BitSet to track used sectors and fill gaps (fragmentation handling).</li>
 * </ul>
 * <p>
 * This class is thread-safe for reading and writing.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionFile implements AutoCloseable {

    /**
     * The size of a single allocation unit (sector) in bytes.
     */
    private static final int SECTOR_SIZE = 4096;

    /**
     * The size of the header table in bytes.
     * 1024 chunks * 8 bytes per entry = 8192 bytes.
     */
    private static final int HEADER_SIZE = 8192;

    /**
     * The number of sectors occupied by the header.
     * 8192 / 4096 = 2 sectors.
     */
    private static final int HEADER_SECTOR_COUNT = 2;

    private final Path path;
    private FileChannel fileChannel;

    /**
     * In-memory cache of chunk sector offsets.
     * Index = (x & 31) + (z & 31) * 32.
     */
    private final int[] chunkOffsets = new int[1024];

    /**
     * In-memory cache of chunk sector counts.
     */
    private final int[] chunkSectorCounts = new int[1024];

    /**
     * Tracks which sectors in the file are currently occupied.
     * Used to find free space for new writes.
     */
    private final BitSet usedSectors = new BitSet();

    /**
     * Constructs a new region file handler.
     * <p>
     * If the file exists, the header is read and parsed.
     * If the file does not exist, a new file with a blank header is created.
     *
     * @param path The path to the physical file.
     * @throws IOException If the file cannot be opened or created.
     */
    public VxRegionFile(Path path) throws IOException {
        this.path = path;
        boolean exists = Files.exists(path);

        // Ensure parent directory exists
        if (!exists) {
            Files.createDirectories(path.getParent());
        }

        this.fileChannel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

        if (exists) {
            if (fileChannel.size() < HEADER_SIZE) {
                // File exists but is corrupt/too small -> Reset header
                writeHeader();
            } else {
                readHeader();
            }
        } else {
            writeHeader();
        }
    }

    /**
     * Checks if the underlying file channel is open.
     *
     * @return true if the channel is open, false otherwise.
     */
    public boolean isOpen() {
        return fileChannel != null && fileChannel.isOpen();
    }

    /**
     * Reads the header table from the beginning of the file.
     * Populates the internal offset/count arrays and the used sectors bitmap.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void readHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        fileChannel.read(header, 0);
        header.flip();

        for (int i = 0; i < 1024; i++) {
            int offset = header.getInt();
            int count = header.getInt();

            chunkOffsets[i] = offset;
            chunkSectorCounts[i] = count;

            if (offset != 0 && count != 0) {
                for (int s = 0; s < count; s++) {
                    usedSectors.set(offset + s);
                }
            }
        }
    }

    /**
     * Writes a blank header to the file.
     * Used when initializing a new file or recovering a corrupt one.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        // Fill with zeros (1024 chunks * 2 ints per chunk = 2048 ints)
        for (int i = 0; i < 2048; i++) {
            header.putInt(0);
        }
        header.flip();
        fileChannel.write(header, 0);

        // Mark the header sectors (0 and 1) as used so data isn't written there.
        for (int i = 0; i < HEADER_SECTOR_COUNT; i++) {
            usedSectors.set(i);
        }
    }

    /**
     * Reads a data chunk from the file.
     *
     * @param pos The chunk position (relative to the region).
     * @return A Netty ByteBuf containing the data, or null if the chunk does not exist.
     * The caller is responsible for releasing the buffer.
     */
    public synchronized ByteBuf read(ChunkPos pos) {
        if (!isOpen()) return null;

        int index = getIndex(pos);
        int sectorOffset = chunkOffsets[index];
        int sectorCount = chunkSectorCounts[index];

        // If offset or count is 0, the chunk is empty/not present.
        if (sectorOffset == 0 || sectorCount == 0) return null;

        try {
            // 1. Read the length prefix (first 4 bytes of the sector)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            fileChannel.read(lengthBuffer, (long) sectorOffset * SECTOR_SIZE);
            lengthBuffer.flip();
            int length = lengthBuffer.getInt();

            // 2. Validate length
            // It must be > 0 and fit within the allocated sector count.
            if (length <= 0 || length > sectorCount * SECTOR_SIZE) {
                VxMainClass.LOGGER.warn("Invalid chunk data length {} at {}. Sector Count: {}", length, pos, sectorCount);
                return null;
            }

            // 3. Read the payload
            ByteBuffer data = ByteBuffer.allocate(length);
            // Offset + 4 bytes to skip the length prefix
            fileChannel.read(data, (long) sectorOffset * SECTOR_SIZE + 4);
            data.flip();

            return Unpooled.wrappedBuffer(data);
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to read chunk {}", pos, e);
            return null;
        }
    }

    /**
     * Writes a data chunk to the file.
     * <p>
     * If the provided buffer is empty (readable bytes == 0), this method acts as a delete operation.
     *
     * @param pos  The chunk position.
     * @param data The buffer containing the data to write.
     */
    public synchronized void write(ChunkPos pos, ByteBuf data) {
        if (!isOpen()) return;

        try {
            int index = getIndex(pos);
            int oldSectorOffset = chunkOffsets[index];
            int oldSectorCount = chunkSectorCounts[index];

            int dataSize = data.readableBytes();

            // ==========================================
            // CASE 1: Deletion (Empty Data)
            // ==========================================
            if (dataSize == 0) {
                if (oldSectorOffset != 0) {
                    // 1. Mark previously used sectors as free
                    for (int i = 0; i < oldSectorCount; i++) {
                        usedSectors.clear(oldSectorOffset + i);
                    }

                    // 2. Clear memory cache
                    chunkOffsets[index] = 0;
                    chunkSectorCounts[index] = 0;

                    // 3. Clear header entry on disk (write 0 for offset and 0 for count)
                    ByteBuffer headerEntry = ByteBuffer.allocate(8);
                    headerEntry.putInt(0); // Offset
                    headerEntry.putInt(0); // Count
                    headerEntry.flip();
                    fileChannel.write(headerEntry, index * 8L);

                    // 4. Check if the file is now completely empty
                    checkAndPruneFile();
                }
                return;
            }

            // ==========================================
            // CASE 2: Writing Data
            // ==========================================

            // Calculate required sectors.
            // Payload size + 4 bytes for length prefix.
            int totalSize = dataSize + 4;
            int sectorsNeeded = (totalSize + SECTOR_SIZE - 1) / SECTOR_SIZE;

            int newSectorOffset;

            // Strategy: Reuse existing sectors if the count matches exactly.
            if (oldSectorOffset != 0 && oldSectorCount == sectorsNeeded) {
                newSectorOffset = oldSectorOffset;
            } else {
                // Otherwise, free the old sectors and allocate a new block.
                if (oldSectorOffset != 0) {
                    for (int i = 0; i < oldSectorCount; i++) {
                        usedSectors.clear(oldSectorOffset + i);
                    }
                }
                newSectorOffset = allocateSectors(sectorsNeeded);
            }

            // Prepare the file buffer: [Length (4 bytes)] + [Data] + [Padding]
            ByteBuffer fileBuffer = ByteBuffer.allocate(sectorsNeeded * SECTOR_SIZE);
            fileBuffer.putInt(dataSize);

            // Transfer data from Netty ByteBuf to NIO ByteBuffer
            int headerPos = fileBuffer.position();
            fileBuffer.limit(headerPos + dataSize);
            data.getBytes(data.readerIndex(), fileBuffer);

            // Restore limit to write full sectors (padding included)
            fileBuffer.limit(fileBuffer.capacity());
            fileBuffer.position(0);

            // Write to disk
            fileChannel.write(fileBuffer, (long) newSectorOffset * SECTOR_SIZE);

            // Update Memory
            chunkOffsets[index] = newSectorOffset;
            chunkSectorCounts[index] = sectorsNeeded;

            // Update Header on disk (8 bytes)
            ByteBuffer headerEntry = ByteBuffer.allocate(8);
            headerEntry.putInt(newSectorOffset);
            headerEntry.putInt(sectorsNeeded);
            headerEntry.flip();
            fileChannel.write(headerEntry, index * 8L);

        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to write chunk {}", pos, e);
        }
    }

    /**
     * Scans the used sectors bitmap to find a continuous range of free sectors.
     * Uses a "First Fit" algorithm.
     *
     * @param count The number of contiguous sectors required.
     * @return The start sector index.
     * @throws IOException If the file size calculation fails.
     */
    private int allocateSectors(int count) throws IOException {
        int fileSectorCount = (int) (fileChannel.size() / SECTOR_SIZE);

        // Start search after the header.
        // Sectors 0 and 1 are reserved.
        int searchStart = HEADER_SECTOR_COUNT;

        // 1. Try to fill gaps within the existing file
        for (int i = searchStart; i < fileSectorCount; i++) {
            if (!usedSectors.get(i)) {
                int run = 0;
                for (int j = 0; j < count; j++) {
                    if (i + j < fileSectorCount && !usedSectors.get(i + j)) {
                        run++;
                    } else {
                        break;
                    }
                }
                if (run == count) {
                    for (int k = 0; k < count; k++) usedSectors.set(i + k);
                    return i;
                }
            }
        }

        // 2. Append to end of file
        int newOffset = fileSectorCount;

        // Safety: Ensure we never overwrite the header if the file was truncated/corrupt
        if (newOffset < HEADER_SECTOR_COUNT) {
            newOffset = HEADER_SECTOR_COUNT;
        }

        for (int k = 0; k < count; k++) usedSectors.set(newOffset + k);
        return newOffset;
    }

    /**
     * Checks if the file contains any data. If all chunks are empty (offsets are 0),
     * the file is closed and deleted from the file system to save space.
     *
     * @throws IOException If file operations fail.
     */
    private void checkAndPruneFile() throws IOException {
        // Iterate through memory cache to see if any chunk is active
        for (int offset : chunkOffsets) {
            if (offset != 0) {
                return; // File still has data, do nothing.
            }
        }

        // File is completely empty.
        // Close the channel first to release file locks (essential for Windows).
        close();

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            VxMainClass.LOGGER.warn("Failed to delete empty region file: {}", path, e);
        }
    }

    /**
     * Helper to calculate the flat index (0-1023) for a chunk within the region.
     */
    private int getIndex(ChunkPos pos) {
        return (pos.x & 31) + (pos.z & 31) * 32;
    }

    /**
     * Closes the underlying file channel and forces any pending updates to disk.
     */
    @Override
    public synchronized void close() throws IOException {
        if (fileChannel != null && fileChannel.isOpen()) {
            fileChannel.force(true);
            fileChannel.close();
        }
    }
}