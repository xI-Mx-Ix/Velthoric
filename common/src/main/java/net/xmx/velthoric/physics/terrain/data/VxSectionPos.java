/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * A utility class for handling 16x16x16 chunk section coordinates.
 * <p>
 * It provides high-performance, allocation-free methods to convert between
 * 3D integer coordinates and a single primitive {@code long}. This is crucial
 * for a data-oriented design, as it allows positions to be used as keys in

 * maps and sets without the overhead of object allocation and garbage collection.
 * <p>
 * The bit layout for the packed long is as follows:
 * <ul>
 *   <li>Bits 0-25: Z coordinate (26 bits, signed)</li>
 *   <li>Bits 26-37: Y coordinate (12 bits, signed)</li>
 *   <li>Bits 38-63: X coordinate (26 bits, signed)</li>
 * </ul>
 * This layout provides a vast range for X and Z coordinates ([-33554432, 33554431])
 * and a sufficient range for Y coordinates ([-2048, 2047]), covering all possible
 * Minecraft world heights.
 *
 * @author xI-Mx-Ix
 */
public final class VxSectionPos {

    private static final int X_BITS = 26;
    private static final int Y_BITS = 12;
    private static final int Z_BITS = 26;

    private static final int Y_SHIFT = Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + Y_BITS;

    private static final long X_MASK = (1L << X_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final long Z_MASK = (1L << Z_BITS) - 1;

    /**
     * Private constructor to prevent instantiation.
     */
    private VxSectionPos() {}

    /**
     * Packs 3D section coordinates into a single primitive long.
     *
     * @param x The X coordinate of the section.
     * @param y The Y coordinate of the section.
     * @param z The Z coordinate of the section.
     * @return A packed long representing the position.
     */
    public static long pack(int x, int y, int z) {
        return ((long)x & X_MASK) << X_SHIFT |
                ((long)y & Y_MASK) << Y_SHIFT |
                ((long)z & Z_MASK);
    }

    /**
     * Packs a BlockPos into a single primitive long representing its section.
     *
     * @param pos The block position.
     * @return A packed long representing the section containing the block.
     */
    public static long fromBlockPos(BlockPos pos) {
        return pack(
                pos.getX() >> 4,
                pos.getY() >> 4,
                pos.getZ() >> 4
        );
    }

    /**
     * Unpacks the X coordinate from a packed long.
     *
     * @param packed The packed position long.
     * @return The integer X coordinate.
     */
    public static int unpackX(long packed) {
        return (int) ((packed >> X_SHIFT));
    }

    /**
     * Unpacks the Y coordinate from a packed long.
     *
     * @param packed The packed position long.
     * @return The integer Y coordinate.
     */
    public static int unpackY(long packed) {
        // Sign-extend the 12-bit value
        return (int) (((packed >> Y_SHIFT) & Y_MASK) << (32 - Y_BITS)) >> (32 - Y_BITS);
    }

    /**
     * Unpacks the Z coordinate from a packed long.
     *
     * @param packed The packed position long.
     * @return The integer Z coordinate.
     */
    public static int unpackZ(long packed) {
        // Sign-extend the 26-bit value
        return (int) ((packed & Z_MASK) << (32 - Z_BITS)) >> (32 - Z_BITS);
    }

    /**
     * Unpacks the world-space origin (minimum corner) of the chunk section.
     *
     * @param packed The packed position long.
     * @return The origin as a {@link BlockPos}.
     */
    public static BlockPos unpackToOrigin(long packed) {
        return new BlockPos(unpackX(packed) << 4, unpackY(packed) << 4, unpackZ(packed) << 4);
    }

    /**
     * Unpacks the packed long into a Minecraft SectionPos object.
     * Note: This allocates a new object and should be avoided in hot paths.
     *
     * @param packed The packed position long.
     * @return A new {@link SectionPos} instance.
     */
    public static SectionPos unpackToSectionPos(long packed) {
        return SectionPos.of(unpackX(packed), unpackY(packed), unpackZ(packed));
    }
}