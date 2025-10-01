/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An immutable, data-oriented snapshot of a 16x16x16 chunk section.
 * <p>
 * This class captures the state of a chunk section at a specific moment in time,
 * converting block states into a compact integer array representation. It also
 * pre-calculates which block types are simple full cubes to optimize the subsequent
 * greedy meshing process.
 *
 * @param pos The world-space position of this chunk section.
 * @param blockData A flattened 3D array (size 4096) of block identifiers.
 * @param palette A map from block identifier to the original BlockState.
 * @param fullCubeIds A set of block identifiers that represent a standard 1x1x1 cube.
 *
 * @author xI-Mx-Ix
 */
public record VxChunkSnapshot(VxSectionPos pos, int[] blockData, Map<Integer, BlockState> palette, Set<Integer> fullCubeIds) {

    private static final int CHUNK_DIM = 16;
    private static final int CHUNK_VOLUME = CHUNK_DIM * CHUNK_DIM * CHUNK_DIM;
    private static final AABB FULL_CUBE_AABB = new AABB(0, 0, 0, 1, 1, 1);

    /**
     * Creates a snapshot from a live chunk in the world.
     *
     * @param level The level the chunk belongs to.
     * @param chunk The chunk to snapshot.
     * @param pos   The specific section position within the chunk to snapshot.
     * @return A new, immutable VxChunkSnapshot.
     */
    public static VxChunkSnapshot snapshotFromChunk(Level level, LevelChunk chunk, VxSectionPos pos) {
        int sectionIndex = level.getSectionIndexFromSectionY(pos.y());

        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            return new VxChunkSnapshot(pos, new int[CHUNK_VOLUME], new HashMap<>(), new HashSet<>());
        }

        LevelChunkSection section = chunk.getSections()[sectionIndex];
        if (section == null || section.hasOnlyAir()) {
            return new VxChunkSnapshot(pos, new int[CHUNK_VOLUME], new HashMap<>(), new HashSet<>());
        }

        int[] blockData = new int[CHUNK_VOLUME];
        Map<BlockState, Integer> stateToIdMap = new HashMap<>();
        Map<Integer, BlockState> palette = new HashMap<>();
        Set<Integer> fullCubeIds = new HashSet<>();
        AtomicInteger nextId = new AtomicInteger(1); // 0 is reserved for air
        BlockPos origin = pos.getOrigin();

        for (int y = 0; y < CHUNK_DIM; ++y) {
            for (int z = 0; z < CHUNK_DIM; ++z) {
                for (int x = 0; x < CHUNK_DIM; ++x) {
                    BlockState blockState = section.getBlockState(x, y, z);

                    if (!blockState.isAir()) {
                        VoxelShape collisionShape = blockState.getCollisionShape(level, origin.offset(x, y, z));
                        if (!collisionShape.isEmpty()) {
                            int id = stateToIdMap.computeIfAbsent(blockState, k -> {
                                int newId = nextId.getAndIncrement();
                                palette.put(newId, k);
                                // Check if this new block type is a full cube and cache the result.
                                List<AABB> aabbs = collisionShape.toAabbs();
                                if (aabbs.size() == 1 && aabbs.get(0).equals(FULL_CUBE_AABB)) {
                                    fullCubeIds.add(newId);
                                }
                                return newId;
                            });
                            blockData[getIndex(x, y, z)] = id;
                        }
                    }
                }
            }
        }

        return new VxChunkSnapshot(pos, blockData, palette, fullCubeIds);
    }

    /**
     * Checks if this snapshot contains any non-air blocks.
     *
     * @return True if the snapshot is empty, false otherwise.
     */
    public boolean isEmpty() {
        return palette.isEmpty();
    }

    /**
     * Converts 3D coordinates to a 1D array index.
     *
     * @param x The x-coordinate (0-15).
     * @param y The y-coordinate (0-15).
     * @param z The z-coordinate (0-15).
     * @return The corresponding index in the flattened 1D array.
     */
    private static int getIndex(int x, int y, int z) {
        return y * CHUNK_DIM * CHUNK_DIM + z * CHUNK_DIM + x;
    }
}