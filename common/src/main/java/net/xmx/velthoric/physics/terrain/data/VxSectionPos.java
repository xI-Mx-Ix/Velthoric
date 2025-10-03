/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.data;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Represents the coordinates of a 16x16x16 chunk section.
 * This is a record, making it immutable and suitable for use as a map key.
 *
 * @param x The X coordinate of the section.
 * @param y The Y coordinate of the section.
 * @param z The Z coordinate of the section.
 *
 * @author xI-Mx-Ix
 */
public record VxSectionPos(int x, int y, int z) {

    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_SIZE_SHIFT = 4;

    /**
     * Converts this section position to a 2D chunk position.
     *
     * @return The corresponding {@link ChunkPos}.
     */
    public ChunkPos toChunkPos2D() {
        return new ChunkPos(x, z);
    }

    /**
     * Creates a {@link VxSectionPos} from a world block position.
     *
     * @param pos The block position.
     * @return The corresponding section position.
     */
    public static VxSectionPos fromBlockPos(BlockPos pos) {
        return new VxSectionPos(
                pos.getX() >> CHUNK_SIZE_SHIFT,
                pos.getY() >> CHUNK_SIZE_SHIFT,
                pos.getZ() >> CHUNK_SIZE_SHIFT
        );
    }

    /**
     * Creates a {@link VxSectionPos} from a high-precision Jolt vector.
     *
     * @param pos The RVec3 position.
     * @return The corresponding section position.
     */
    public static VxSectionPos fromRVec3(RVec3 pos) {
        return new VxSectionPos(
                SectionPos.blockToSectionCoord(pos.x()),
                SectionPos.blockToSectionCoord(pos.y()),
                SectionPos.blockToSectionCoord(pos.z())
        );
    }

    /**
     * Creates a {@link VxSectionPos} from world-space coordinates.
     *
     * @param x The world X coordinate.
     * @param y The world Y coordinate.
     * @param z The world Z coordinate.
     * @return The corresponding section position.
     */
    public static VxSectionPos fromWorldSpace(double x, double y, double z) {
        return new VxSectionPos(
                (int) Math.floor(x) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(y) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(z) >> CHUNK_SIZE_SHIFT
        );
    }

    /**
     * Gets the world-space origin (minimum corner) of this chunk section.
     *
     * @return The origin as a {@link BlockPos}.
     */
    public BlockPos getOrigin() {
        return new BlockPos(x << CHUNK_SIZE_SHIFT, y << CHUNK_SIZE_SHIFT, z << CHUNK_SIZE_SHIFT);
    }

    /**
     * Gets the world-space bounding box of this chunk section.
     *
     * @return The bounding box as an {@link AABB}.
     */
    public AABB getAABB() {
        BlockPos origin = getOrigin();
        return new AABB(origin, origin.offset(CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE));
    }

    /**
     * Checks if this section is within the buildable height of the given level.
     *
     * @param level The level to check against.
     * @return True if the section is within the world's height limits, false otherwise.
     */
    public boolean isWithinWorldHeight(Level level) {
        int minBlockY = y << CHUNK_SIZE_SHIFT;
        int maxBlockY = minBlockY + (CHUNK_SIZE - 1);
        return maxBlockY >= level.getMinBuildHeight() && minBlockY < level.getMaxBuildHeight();
    }
}