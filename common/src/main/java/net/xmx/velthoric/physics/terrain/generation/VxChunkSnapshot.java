/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.xmx.velthoric.physics.terrain.VxSectionPos;

import java.util.Arrays;

/**
 * An immutable, allocation-free snapshot of the collidable blocks within a single chunk section.
 * This is used to pass chunk data to worker threads for shape generation in a thread-safe manner.
 *
 * <p>
 * <b>Optimization Note:</b> To minimize GC pressure and memory overhead ("JVM stuttering"),
 * this class uses a Structure-of-Arrays (SoA) layout instead of a List of objects.
 * Block positions are packed into a primitive {@code short} array, and BlockStates are stored
 * in a parallel array. This ensures zero per-block object allocations during snapshot creation.
 * </p>
 *
 * @param packedPositions An array of packed local coordinates. Format: (x << 8) | (y << 4) | z.
 * @param states The block states corresponding to the packed positions by index.
 * @param count The actual number of collidable blocks stored in the arrays.
 * @param pos The position of the chunk section this snapshot represents.
 *
 * @author xI-Mx-Ix
 */
public record VxChunkSnapshot(short[] packedPositions, BlockState[] states, int count, VxSectionPos pos) {

    private static final short[] EMPTY_POSITIONS = new short[0];
    private static final BlockState[] EMPTY_STATES = new BlockState[0];

    /**
     * Creates a ChunkSnapshot from a live LevelChunk using a two-pass strategy.
     * <p>
     * Pass 1 counts the valid collision blocks to allocate exact-sized arrays.
     * Pass 2 fills the arrays. This avoids the overhead of resizing dynamic lists.
     * </p>
     *
     * @param level The level the chunk belongs to.
     * @param chunk The chunk to snapshot.
     * @param pos The specific section of the chunk to snapshot.
     * @return A new ChunkSnapshot instance.
     */
    public static VxChunkSnapshot snapshotFromChunk(Level level, LevelChunk chunk, VxSectionPos pos) {
        int sectionIndex = level.getSectionIndexFromSectionY(pos.y());

        // Validate section index to avoid out-of-bounds access
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            return new VxChunkSnapshot(EMPTY_POSITIONS, EMPTY_STATES, 0, pos);
        }

        LevelChunkSection section = chunk.getSections()[sectionIndex];
        // Fast exit for empty sections
        if (section == null || section.hasOnlyAir()) {
            return new VxChunkSnapshot(EMPTY_POSITIONS, EMPTY_STATES, 0, pos);
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos origin = pos.getOrigin();
        int blockCount = 0;

        // --- PASS 1: Count collidable blocks ---
        for (int x = 0; x < 16; ++x) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    BlockState blockState = section.getBlockState(x, y, z);
                    if (!blockState.isAir()) {
                        mutablePos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        // Only include blocks that actually have a collision shape
                        if (!blockState.getCollisionShape(level, mutablePos).isEmpty()) {
                            blockCount++;
                        }
                    }
                }
            }
        }

        if (blockCount == 0) {
            return new VxChunkSnapshot(EMPTY_POSITIONS, EMPTY_STATES, 0, pos);
        }

        // Allocate primitive arrays exactly to the required size
        short[] packedPositions = new short[blockCount];
        BlockState[] states = new BlockState[blockCount];
        int index = 0;

        // --- PASS 2: Fill arrays ---
        for (int x = 0; x < 16; ++x) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    BlockState blockState = section.getBlockState(x, y, z);
                    if (!blockState.isAir()) {
                        mutablePos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!blockState.getCollisionShape(level, mutablePos).isEmpty()) {
                            // Pack x (0-15), y (0-15), z (0-15) into a short.
                            // 4 bits are sufficient for 0-15.
                            // Layout: 0000 XXXX YYYY ZZZZ
                            short packed = (short) ((x << 8) | (y << 4) | z);
                            packedPositions[index] = packed;
                            states[index] = blockState;
                            index++;
                        }
                    }
                }
            }
        }

        return new VxChunkSnapshot(packedPositions, states, blockCount, pos);
    }

    /**
     * Checks equality based on content and position.
     * Uses optimized array comparison.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VxChunkSnapshot that)) return false;
        // Check count and position first as a cheap filter
        if (count != that.count || !pos.equals(that.pos)) return false;
        // Deep compare arrays
        return Arrays.equals(packedPositions, that.packedPositions) && Arrays.equals(states, that.states);
    }

    /**
     * Generates a hash code for the snapshot.
     */
    @Override
    public int hashCode() {
        int result = pos.hashCode();
        result = 31 * result + count;
        result = 31 * result + Arrays.hashCode(packedPositions);
        result = 31 * result + Arrays.hashCode(states);
        return result;
    }
}