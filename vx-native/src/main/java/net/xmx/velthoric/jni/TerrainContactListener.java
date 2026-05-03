/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * A Java wrapper representing the native C++ {@code Velthoric::ContactListener}.
 * <p>
 * This class provides a safe, object-oriented way to attach and detach the custom Jolt Physics
 * contact listener from the Java side without holding raw, unsafe C-pointers in common logic.
 * It strictly contains no Jolt logic of its own, delegating all instantiation and memory
 * management directly to the native C++ layer via JNI.
 * </p>
 * <p>
 * Because it extends {@link NativeObject}, the listener's native memory is automatically
 * guaranteed to be freed when {@link #close()} is called, preventing use-after-free or double-free errors.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class TerrainContactListener extends NativeObject {

    private final long physicsSystemPtr;

    /**
     * Constructs a new native ContactListener and attaches it to the specified Jolt PhysicsSystem.
     *
     * @param physicsSystemPtr The native virtual address of the {@code JPH::PhysicsSystem}.
     *                         Must be a valid, initialized pointer.
     * @param world            The Java {@code VxPhysicsWorld} instance this listener belongs to.
     */
    public TerrainContactListener(long physicsSystemPtr, Object world) {
        super(nAttachContactListener(physicsSystemPtr, world));
        this.physicsSystemPtr = physicsSystemPtr;
    }

    /**
     * Called internally by {@link #close()} to detach the listener from the PhysicsSystem
     * and delete the native C++ object.
     *
     * @param address The native virtual address of the ContactListener to destroy.
     */
    @Override
    protected void nClose(long address) {
        nDetachContactListener(physicsSystemPtr, address);
    }

    /**
     * Natively allocates a new C++ {@code ContactListener} and attaches it to the given PhysicsSystem.
     *
     * @param physicsSystemPtr The native virtual address of the Jolt PhysicsSystem.
     * @param world            The Java VxPhysicsWorld object.
     * @return The native virtual address of the newly allocated ContactListener.
     */
    private static native long nAttachContactListener(long physicsSystemPtr, Object world);

    /**
     * Natively detaches the ContactListener from the PhysicsSystem and securely deletes its memory.
     *
     * @param physicsSystemPtr The native virtual address of the Jolt PhysicsSystem.
     * @param listenerPtr      The native virtual address of the ContactListener to destroy.
     */
    private static native void nDetachContactListener(long physicsSystemPtr, long listenerPtr);
}