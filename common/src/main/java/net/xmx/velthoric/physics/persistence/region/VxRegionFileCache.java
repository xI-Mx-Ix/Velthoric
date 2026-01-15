/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence.region;

import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.persistence.RegionPos;

import java.io.IOException;
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
     * Retrieves or opens the region file corresponding to the given chunk position.
     *
     * @param chunkPos The position of the chunk.
     * @return The region file instance.
     * @throws IOException If the file cannot be opened.
     */
    public synchronized VxRegionFile getRegionFile(ChunkPos chunkPos) throws IOException {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
        return cache.computeIfAbsent(regionPos, k -> {
            String fileName = String.format("r.%d.%d.%s", k.x(), k.z(), extension);
            try {
                return new VxRegionFile(storageDirectory.resolve(fileName));
            } catch (IOException e) {
                throw new RuntimeException("Failed to open region file " + fileName, e);
            }
        });
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