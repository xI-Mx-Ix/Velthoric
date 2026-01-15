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
 * Manages a single region file containing data for 32x32 chunks.
 * <p>
 * The file format mirrors the Anvil Region File format but is optimized for raw binary data:
 * <ul>
 *     <li><b>Header:</b> The first 4096 bytes contain the location table (1024 entries).</li>
 *     <li><b>Location Entry:</b> 4 bytes. The first 3 bytes are the sector offset (4KB units), the last byte is the sector count.</li>
 *     <li><b>Sectors:</b> Data is stored in 4KB aligned sectors.</li>
 * </ul>
 * <p>
 * This class is thread-safe for reading, but writing requires external synchronization or
 * single-threaded access via the {@link net.xmx.velthoric.physics.persistence.VxIOProcessor}.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionFile implements AutoCloseable {

    private static final int SECTOR_SIZE = 4096;
    private static final int HEADER_SIZE = 4096;

    private final Path path;
    private FileChannel fileChannel;
    private final int[] offsets = new int[1024];
    private final BitSet usedSectors = new BitSet();

    /**
     * Constructs a new region file handler.
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
                // Corrupt or empty file, reset header
                writeHeader();
            } else {
                readHeader();
            }
        } else {
            writeHeader();
        }
    }

    /**
     * Checks if the file channel is currently open.
     * Used by the cache to determine if a file was auto-deleted/closed.
     *
     * @return true if open, false otherwise.
     */
    public boolean isOpen() {
        return fileChannel != null && fileChannel.isOpen();
    }

    /**
     * Reads the header table into memory and populates the used sector bitmap.
     */
    private void readHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        fileChannel.read(header, 0);
        header.flip();

        for (int i = 0; i < 1024; i++) {
            int entry = header.getInt();
            offsets[i] = entry;

            int offset = (entry >> 8) & 0xFFFFFF;
            int sectors = entry & 0xFF;

            if (offset != 0 && sectors != 0) {
                for (int s = 0; s < sectors; s++) {
                    usedSectors.set(offset + s);
                }
            }
        }
    }

    /**
     * initializes a blank header on disk.
     */
    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        for (int i = 0; i < 1024; i++) {
            header.putInt(0);
        }
        header.flip();
        fileChannel.write(header, 0);
        // Mark header sectors as used (Sector 0 is header)
        usedSectors.set(0); 
    }

    /**
     * Reads a data chunk from the file.
     *
     * @param pos The chunk position (relative to the region).
     * @return A Netty ByteBuf containing the data, or null if the chunk does not exist.
     *         The caller is responsible for releasing the buffer.
     */
    public synchronized ByteBuf read(ChunkPos pos) {
        if (fileChannel == null || !fileChannel.isOpen()) return null;

        int index = getIndex(pos);
        int entry = offsets[index];

        if (entry == 0) return null;

        int sectorOffset = (entry >> 8) & 0xFFFFFF;
        int sectorCount = entry & 0xFF;

        if (sectorOffset == 0 || sectorCount == 0) return null;

        try {
            // Read raw data length (first 4 bytes of the sector)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            fileChannel.read(lengthBuffer, (long) sectorOffset * SECTOR_SIZE);
            lengthBuffer.flip();
            int length = lengthBuffer.getInt();

            if (length <= 0 || length > sectorCount * SECTOR_SIZE) {
                VxMainClass.LOGGER.warn("Invalid chunk data length {} at {}", length, pos);
                return null;
            }

            // Read the payload
            ByteBuffer data = ByteBuffer.allocate(length);
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
     * If {@code data} is empty, the chunk is removed.
     * <p>
     * <b>Garbage Collection:</b> If a deletion causes the entire region file to become
     * empty (all 1024 chunks are null), this method automatically closes the file
     * and deletes it from the disk to save space.
     *
     * @param pos  The chunk position.
     * @param data The buffer containing the data to write.
     */
    public synchronized void write(ChunkPos pos, ByteBuf data) {
        if (fileChannel == null || !fileChannel.isOpen()) return;

        try {
            int index = getIndex(pos);
            int oldEntry = offsets[index];
            int oldSectorOffset = (oldEntry >> 8) & 0xFFFFFF;
            int oldSectorCount = oldEntry & 0xFF;

            int dataSize = data.readableBytes();

            // --- Case 1: Deletion (Empty Data) ---
            if (dataSize == 0) {
                if (oldEntry != 0) {
                    // 1. Mark previously used sectors as free in the bitmap
                    if (oldSectorOffset != 0) {
                        for (int i = 0; i < oldSectorCount; i++) {
                            usedSectors.clear(oldSectorOffset + i);
                        }
                    }

                    // 2. Clear the header entry in memory
                    offsets[index] = 0;

                    // 3. Clear the header entry on disk
                    ByteBuffer headerEntry = ByteBuffer.allocate(4);
                    headerEntry.putInt(0);
                    headerEntry.flip();
                    fileChannel.write(headerEntry, index * 4L);

                    // 4. Check if file is completely empty now
                    checkAndPruneFile();
                }
                return;
            }

            // --- Case 2: Writing Data (Standard Logic) ---
            // Payload size + 4 bytes for length prefix
            int totalSize = dataSize + 4;
            int sectorsNeeded = (totalSize + SECTOR_SIZE - 1) / SECTOR_SIZE;

            if (sectorsNeeded > 255) {
                VxMainClass.LOGGER.error("Chunk data too large ({}) for region file format at {}", totalSize, pos);
                return;
            }

            int newSectorOffset;

            // Reuse existing sectors if size matches
            if (oldSectorOffset != 0 && oldSectorCount == sectorsNeeded) {
                newSectorOffset = oldSectorOffset;
            } else {
                // Free old sectors
                if (oldSectorOffset != 0) {
                    for (int i = 0; i < oldSectorCount; i++) {
                        usedSectors.clear(oldSectorOffset + i);
                    }
                }
                // Allocate new sectors
                newSectorOffset = allocateSectors(sectorsNeeded);
            }

            // Prepare the file buffer: [Length (4 bytes)] + [Data] + [Padding]
            ByteBuffer fileBuffer = ByteBuffer.allocate(sectorsNeeded * SECTOR_SIZE);
            fileBuffer.putInt(dataSize);

            // Correctly transfer bytes from Netty ByteBuf to NIO ByteBuffer.
            // We must limit the fileBuffer to the exact size of data to prevent
            // Netty trying to read more than available if the sector has padding space.
            int headerPos = fileBuffer.position(); // Should be 4
            fileBuffer.limit(headerPos + dataSize);
            data.getBytes(data.readerIndex(), fileBuffer);

            // Restore limit to capacity so the entire (padded) buffer is written to disk
            fileBuffer.limit(fileBuffer.capacity());
            fileBuffer.position(0);

            // Write data to the specific sector offset
            fileChannel.write(fileBuffer, (long) newSectorOffset * SECTOR_SIZE);

            // Update Header in memory
            int newEntry = (newSectorOffset << 8) | sectorsNeeded;
            offsets[index] = newEntry;

            // Update Header on disk
            ByteBuffer headerEntry = ByteBuffer.allocate(4);
            headerEntry.putInt(newEntry);
            headerEntry.flip();
            fileChannel.write(headerEntry, index * 4L);

        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to write chunk {}", pos, e);
        }
    }

    /**
     * Scans the bitmap to find a continuous range of free sectors.
     */
    private int allocateSectors(int count) throws IOException {
        int fileSectorCount = (int) (fileChannel.size() / SECTOR_SIZE);
        // Start search after header (sector 0 is used)
        int searchStart = 1; 

        // 1. Try to fill gaps in the file
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
        // Ensure file is physically grown if needed (optional, OS usually handles sparse)
        if (newOffset == 0) newOffset = 1; // Safety for corrupt files

        for (int k = 0; k < count; k++) usedSectors.set(newOffset + k);
        return newOffset;
    }

    /**
     * Scans the location table. If all entries are 0 (empty),
     * the file is closed and deleted from the file system.
     */
    private void checkAndPruneFile() throws IOException {
        for (int offset : offsets) {
            if (offset != 0) {
                return; // File still has data
            }
        }

        // File is completely empty
        // Close the channel first to release file locks (crucial on Windows)
        close();

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            VxMainClass.LOGGER.warn("Failed to delete empty region file: {}", path, e);
        }
    }

    private int getIndex(ChunkPos pos) {
        return (pos.x & 31) + (pos.z & 31) * 32;
    }

    @Override
    public synchronized void close() throws IOException {
        if (fileChannel != null && fileChannel.isOpen()) {
            fileChannel.force(true);
            fileChannel.close();
        }
    }
}