/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh.arena;

/**
 * Represents a contiguous segment of memory within the {@link VxArenaBuffer}.
 * <p>
 * This class is designed to be pooled to minimize object churn. It acts as a metadata
 * holder for the free-list allocator.
 *
 * @author xI-Mx-Ix
 */
public class VxMemorySegment implements Comparable<VxMemorySegment> {
    
    /**
     * The start offset of this segment in bytes.
     */
    public long offset;

    /**
     * The size of this segment in bytes.
     */
    public long size;

    /**
     * Constructs a new memory segment.
     *
     * @param offset The start offset in bytes.
     * @param size   The size in bytes.
     */
    public VxMemorySegment(long offset, long size) {
        this.offset = offset;
        this.size = size;
    }

    /**
     * Updates the segment's data. Used when recycling objects from the pool.
     *
     * @param offset The new offset.
     * @param size   The new size.
     * @return This instance for chaining.
     */
    public VxMemorySegment set(long offset, long size) {
        this.offset = offset;
        this.size = size;
        return this;
    }

    /**
     * Calculates the end offset of this segment (exclusive).
     *
     * @return offset + size.
     */
    public long getEnd() {
        return offset + size;
    }

    /**
     * Compares segments based on their offset.
     * Essential for maintaining a sorted free-list for efficient coalescing.
     */
    @Override
    public int compareTo(VxMemorySegment other) {
        return Long.compare(this.offset, other.offset);
    }

    @Override
    public String toString() {
        return "VxMemorySegment{offset=" + offset + ", size=" + size + '}';
    }
}