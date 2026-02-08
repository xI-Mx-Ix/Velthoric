/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.region;

import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.core.persistence.RegionPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU (Least Recently Used) cache for open {@link VxRegionFile} instances.
 * This prevents the application from exhausting file handles when many regions are accessed.
 *
 * @author xI-Mx-Ix
 */
public class VxRegionFileCache {

    private static final int MAX_OPEN_FILES = 64;
    private final Path storageDirectory;
    private final String extension;

    private final Map<RegionPos, VxRegionFile> cache = new LinkedHashMap<>(MAX_OPEN_FILES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RegionPos, VxRegionFile> eldest) {
            if (size() > MAX_OPEN_FILES) {
                try {
                    eldest.getValue().close();
                } catch (IOException e) {
                    VxMainClass.LOGGER.error("Failed to close region file", e);
                }
                return true;
            }
            return false;
        }
    };

    public VxRegionFileCache(Path storageDirectory, String extension) {
        this.storageDirectory = storageDirectory;
        this.extension = extension;
    }

    /**
     * Retrieves the region file for the given chunk position, with optional creation.
     *
     * @param chunkPos The position of the chunk.
     * @param create   If true, the file is created if it does not exist.
     *                 If false, returns null if the file does not exist.
     * @return The region file instance, or null if it doesn't exist and create is false.
     * @throws IOException If the file cannot be opened.
     */
    public synchronized VxRegionFile getRegionFile(ChunkPos chunkPos, boolean create) throws IOException {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        // Check if we have a cached instance
        VxRegionFile existing = cache.get(regionPos);

        // If the file closed itself (because it became empty and deleted itself),
        // we must remove the stale reference from the cache.
        if (existing != null && !existing.isOpen()) {
            cache.remove(regionPos);
            existing = null;
        }

        if (existing != null) {
            return existing;
        }

        String fileName = String.format("r.%d.%d.%s", regionPos.x(), regionPos.z(), extension);
        Path filePath = storageDirectory.resolve(fileName);

        // If we are not allowed to create the file and it doesn't exist on disk, return null.
        if (!create && !Files.exists(filePath)) {
            return null;
        }

        // Open/Create the file and add to cache
        VxRegionFile newFile = new VxRegionFile(filePath);
        cache.put(regionPos, newFile);
        return newFile;
    }

    public synchronized void closeAll() {
        for (VxRegionFile file : cache.values()) {
            try {
                file.close();
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Error closing region file", e);
            }
        }
        cache.clear();
    }
}