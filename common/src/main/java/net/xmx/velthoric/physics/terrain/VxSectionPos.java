/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Represents the unique identifier for a 16x16x16 chunk section in the world.
 * This is a value-based record used throughout the terrain system for addressing.
 *
 * @param x The x-coordinate of the section.
 * @param y The y-coordinate of the section.
 * @param z The z-coordinate of the section.
 *
 * @author xI-Mx-Ix
 */
public record VxSectionPos(int x, int y, int z) {

    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_SIZE_SHIFT = 4;

    /**
     * Converts this 3D section position to a 2D chunk position.
     * @return The corresponding ChunkPos.
     */
    public ChunkPos toChunkPos2D() {
        return new ChunkPos(x, z);
    }

    /**
     * Creates a VxSectionPos from a Minecraft BlockPos.
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
     * Creates a VxSectionPos from a Jolt RVec3.
     * @param pos The Jolt vector position.
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
     * Creates a VxSectionPos from raw world-space coordinates.
     * @param x The world x-coordinate.
     * @param y The world y-coordinate.
     * @param z The world z-coordinate.
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
     * @return A BlockPos representing the origin.
     */
    public BlockPos getOrigin() {
        return new BlockPos(x << CHUNK_SIZE_SHIFT, y << CHUNK_SIZE_SHIFT, z << CHUNK_SIZE_SHIFT);
    }

    /**
     * Gets the bounding box (AABB) that encloses this chunk section.
     * @return The AABB for this section.
     */
    public AABB getAABB() {
        BlockPos origin = getOrigin();
        return new AABB(origin, origin.offset(CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE));
    }

    /**
     * Checks if this section is within the buildable height of the given level.
     * @param level The level to check against.
     * @return True if the section is within the world's height limits.
     */
    public boolean isWithinWorldHeight(Level level) {
        int minBlockY = y << CHUNK_SIZE_SHIFT;
        int maxBlockY = minBlockY + (CHUNK_SIZE - 1);
        return maxBlockY >= level.getMinBuildHeight() && minBlockY < level.getMaxBuildHeight();
    }
}