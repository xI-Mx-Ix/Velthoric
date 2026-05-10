/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * A Java wrapper for the native C++ {@code Velthoric::TerrainContactHandler}.
 * <p>
 * This handler is responsible for specialized terrain collision processing, 
 * including physical material overrides (friction/restitution) and 
 * interaction event generation (destruction/particles).
 * </p>
 * <p>
 * It maintains an internal, sharded flat-cache to ensure O(1) performance 
 * during high-frequency contact persistence across thousands of bodies.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class TerrainContactHandler extends NativeObject {

    /**
     * Constructs a new TerrainContactHandler and allocates its native C++ counterpart.
     *
     * @param physicsSystemPtr The native virtual address of the {@code JPH::PhysicsSystem}.
     * @param world            The Java {@code VxPhysicsWorld} object reference used for callbacks.
     */
    public TerrainContactHandler(long physicsSystemPtr, Object world) {
        super(nCreateHandler(physicsSystemPtr, world));
    }

    /**
     * Safely destroys the native handler. Called automatically by {@link #close()}.
     * 
     * @param address The virtual address of the native object.
     */
    @Override
    protected void nClose(long address) {
        nDestroyHandler(address);
    }

    // Native JNI Bridge

    /**
     * Allocates a new native TerrainContactHandler on the heap.
     * 
     * @param physicsSystemPtr Address of the native Jolt PhysicsSystem.
     * @param world            JNI weak/global reference to the Java world.
     * @return Virtual address of the new instance.
     */
    private static native long nCreateHandler(long physicsSystemPtr, Object world);

    /**
     * Securely deletes the native handler.
     * @param address Virtual address of the instance.
     */
    private static native void nDestroyHandler(long address);
}