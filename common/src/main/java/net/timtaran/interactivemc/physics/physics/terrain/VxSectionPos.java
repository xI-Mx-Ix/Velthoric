/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.terrain;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Represents the position of a 16x16x16 chunk section in the world.
 * Used to uniquely identify sections in the terrain system.
 *
 * Each coordinate refers to a section index, not world-space coordinates.
 *
 * @author xI-Mx-Ix
 */
public final class VxSectionPos {

    /** Size of one chunk section edge, in blocks. */
    public static final int CHUNK_SIZE = 16;

    /** Bit shift value for converting between block and section coordinates (2^4 = 16). */
    public static final int CHUNK_SIZE_SHIFT = 4;

    private final int x;
    private final int y;
    private final int z;

    /**
     * Creates a new {@link VxSectionPos} with default coordinates (0, 0, 0).
     * <p>
     * This constructor is mainly intended for internal or temporary use,
     * for example with {@link ThreadLocal#withInitial(java.util.function.Supplier)}.
     * </p>
     */
    public VxSectionPos() {
        this(0, 0, 0);
    }

    /**
     * Creates a new section position.
     *
     * @param x the section's X coordinate
     * @param y the section's Y coordinate
     * @param z the section's Z coordinate
     */
    public VxSectionPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Returns the X coordinate of this section.
     *
     * @return the X coordinate
     */
    public int x() {
        return x;
    }

    /**
     * Returns the Y coordinate of this section.
     *
     * @return the Y coordinate
     */
    public int y() {
        return y;
    }

    /**
     * Returns the Z coordinate of this section.
     *
     * @return the Z coordinate
     */
    public int z() {
        return z;
    }

    /**
     * Converts this 3D section position to a 2D chunk position.
     *
     * @return the corresponding {@link ChunkPos}
     */
    public ChunkPos toChunkPos2D() {
        return new ChunkPos(x, z);
    }

    /**
     * Creates a section position from a block position.
     *
     * @param pos the block position
     * @return a new {@link VxSectionPos} representing the section containing the block
     */
    public static VxSectionPos fromBlockPos(BlockPos pos) {
        return new VxSectionPos(
                pos.getX() >> CHUNK_SIZE_SHIFT,
                pos.getY() >> CHUNK_SIZE_SHIFT,
                pos.getZ() >> CHUNK_SIZE_SHIFT
        );
    }

    /**
     * Creates a section position from a Jolt {@link RVec3} world position.
     *
     * @param pos the Jolt vector position
     * @return a new {@link VxSectionPos} representing the section containing the vector
     */
    public static VxSectionPos fromRVec3(RVec3 pos) {
        return new VxSectionPos(
                SectionPos.blockToSectionCoord(pos.x()),
                SectionPos.blockToSectionCoord(pos.y()),
                SectionPos.blockToSectionCoord(pos.z())
        );
    }

    /**
     * Creates a section position from raw world-space coordinates.
     *
     * @param x the world-space X coordinate
     * @param y the world-space Y coordinate
     * @param z the world-space Z coordinate
     * @return a new {@link VxSectionPos} representing the section containing that point
     */
    public static VxSectionPos fromWorldSpace(double x, double y, double z) {
        return new VxSectionPos(
                (int) Math.floor(x) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(y) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(z) >> CHUNK_SIZE_SHIFT
        );
    }

    /**
     * Returns the world-space origin (minimum corner) of this section.
     *
     * @return a {@link BlockPos} representing the section’s origin
     */
    public BlockPos getOrigin() {
        return new BlockPos(x << CHUNK_SIZE_SHIFT, y << CHUNK_SIZE_SHIFT, z << CHUNK_SIZE_SHIFT);
    }

    /**
     * Returns the axis-aligned bounding box of this section in world coordinates.
     *
     * @return an {@link AABB} representing the section’s bounds
     */
    public AABB getAABB() {
        BlockPos origin = getOrigin();
        Vec3 start = new Vec3(origin.getX(), origin.getY(), origin.getZ());
        Vec3 end = new Vec3(origin.getX() + CHUNK_SIZE, origin.getY() + CHUNK_SIZE, origin.getZ() + CHUNK_SIZE);
        return new AABB(start, end);
    }

    /**
     * Checks if this section is within the world’s buildable height range.
     *
     * @param level the world to check against
     * @return {@code true} if the section is within the world’s height limits, otherwise {@code false}
     */
    public boolean isWithinWorldHeight(Level level) {
        int minBlockY = y << CHUNK_SIZE_SHIFT;
        int maxBlockY = minBlockY + (CHUNK_SIZE - 1);
        return maxBlockY >= level.getMinBuildHeight() && minBlockY < level.getMaxBuildHeight();
    }

    /**
     * Checks if this section position equals another object.
     *
     * @param o the object to compare
     * @return {@code true} if both are section positions with the same coordinates
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VxSectionPos other)) return false;
        return x == other.x && y == other.y && z == other.z;
    }

    /**
     * Generates a hash code for this section position.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }

    /**
     * Returns a string representation of this section position.
     *
     * @return a string in the form {@code VxSectionPos[x, y, z]}
     */
    @Override
    public String toString() {
        return "VxSectionPos[" + x + ", " + y + ", " + z + "]";
    }
}