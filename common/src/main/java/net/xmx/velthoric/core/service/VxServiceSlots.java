/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global registry for service slot allocation.
 * <p>
 * This class assigns a unique, immutable integer ID (slot) to every service type
 * encountered during the application lifecycle. These IDs are then used by the
 * {@link VxServiceManager} to store and retrieve services in a fixed-index array,
 * achieving performance comparable to direct field access.
 *
 * @author xI-Mx-Ix
 */
public final class VxServiceSlots {

    private static final AtomicInteger nextSlot = new AtomicInteger(0);

    /**
     * Map of service classes to their unique slot IDs.
     * Uses Java's highly-optimized {@link ClassValue} for O(1) type-to-ID resolution.
     */
    private static final ClassValue<Integer> slots = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            return nextSlot.getAndIncrement();
        }
    };

    /**
     * Retrieves the unique slot ID for a given service class.
     * If no ID has been assigned yet, a new one is allocated atomically.
     *
     * @param clazz The service class to resolve.
     * @return The unique slot ID.
     */
    public static int get(Class<? extends IVxPhysicsService> clazz) {
        return slots.get(clazz);
    }

    /**
     * @return The total number of slots allocated across the entire system.
     */
    public static int getTotalSlots() {
        return nextSlot.get();
    }

    private VxServiceSlots() {
        // Prevent instantiation
    }
}