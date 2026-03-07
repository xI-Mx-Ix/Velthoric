/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

/**
 * A unique identifier for a {@link VxBehavior}, backed by a single bit position in a {@code long} bitmask.
 * <p>
 * Each behavior type in the engine is assigned a unique {@code VxBehaviorId} at startup.
 * The resulting {@link #mask} can be used for O(1) membership checks against a body's
 * {@code behaviorBits} field stored in the SoA DataStore.
 * <p>
 * A single {@code long} supports up to 64 unique behavior types, which is more than sufficient
 * for an engine of this scale. If more are ever needed, this can be extended to {@code long[2]}.
 *
 * @author xI-Mx-Ix
 */
public final class VxBehaviorId {

    /**
     * Global counter for assigning unique bit positions. Thread-safe by virtue of
     * being called only during static initialization (class loading).
     */
    private static int nextBit = 0;

    /**
     * The zero-indexed bit position of this behavior (0..63).
     */
    private final int bit;

    /**
     * The precomputed bitmask for this behavior ({@code 1L << bit}).
     * Used for fast bitwise AND checks in hot loops.
     */
    private final long mask;

    /**
     * A human-readable name for this behavior, used in logging and debugging.
     */
    private final String name;

    /**
     * Creates a new behavior ID with an automatically assigned bit position.
     *
     * @param name A human-readable name for debugging (e.g., "RigidPhysics", "Buoyancy").
     * @throws IllegalStateException If more than 64 behaviors are registered.
     */
    public VxBehaviorId(String name) {
        if (nextBit >= 64) {
            throw new IllegalStateException("Cannot register more than 64 behaviors. Exceeded limit with: " + name);
        }
        this.name = name;
        this.bit = nextBit++;
        this.mask = 1L << bit;
    }

    /**
     * @return The zero-indexed bit position of this behavior.
     */
    public int getBit() {
        return bit;
    }

    /**
     * @return The precomputed bitmask for fast membership checks.
     */
    public long getMask() {
        return mask;
    }

    /**
     * @return The human-readable name of this behavior.
     */
    public String getName() {
        return name;
    }

    /**
     * Checks if this behavior is present in the given bitmask.
     *
     * @param bits The behavior bitmask of a body (from {@code dataStore.behaviorBits[index]}).
     * @return True if this behavior is attached to the body.
     */
    public boolean isSet(long bits) {
        return (bits & mask) != 0;
    }

    /**
     * Returns the bitmask with this behavior added.
     *
     * @param bits The current behavior bitmask.
     * @return The updated bitmask with this behavior's bit set.
     */
    public long set(long bits) {
        return bits | mask;
    }

    /**
     * Returns the bitmask with this behavior removed.
     *
     * @param bits The current behavior bitmask.
     * @return The updated bitmask with this behavior's bit cleared.
     */
    public long clear(long bits) {
        return bits & ~mask;
    }

    @Override
    public String toString() {
        return "VxBehaviorId{" + name + ", bit=" + bit + "}";
    }
}