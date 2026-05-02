/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An abstract base class representing a native C++ object.
 * It ensures safe resource cleanup by holding the virtual address atomically
 * and guaranteeing that the native close method is only called once.
 *
 * @author xI-Mx-Ix
 */
public abstract class NativeObject implements AutoCloseable {

    private final AtomicLong virtualAddress = new AtomicLong(0L);

    protected NativeObject(long address) {
        if (address == 0L) {
            throw new IllegalArgumentException("NativeObject address cannot be zero.");
        }
        this.virtualAddress.set(address);
    }

    /**
     * Returns the virtual address of the assigned native object.
     *
     * @return the virtual address (not zero)
     * @throws IllegalStateException if the object has already been freed.
     */
    public final long va() {
        long result = virtualAddress.get();
        if (result == 0L) {
            throw new IllegalStateException("Attempted to use an object that has already been freed: " + this);
        }
        return result;
    }

    /**
     * Checks whether a native object is currently assigned and not freed.
     *
     * @return true if an address is assigned, false otherwise.
     */
    public final boolean hasAssignedNativeObject() {
        return virtualAddress.get() != 0L;
    }

    /**
     * Implementing classes must override this method to perform the actual
     * native JNI destruction logic.
     *
     * @param address The native virtual address to be freed.
     */
    protected abstract void nClose(long address);

    /**
     * Frees the native object if it hasn't been freed yet.
     */
    @Override
    public final void close() {
        long address = virtualAddress.getAndSet(0L);
        if (address != 0L) {
            nClose(address);
        }
    }
}