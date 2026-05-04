/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * A Java wrapper representing the native C++ {@code Velthoric::ContactListener} dispatcher.
 * <p>
 * This class serves as the primary entry point for all collision events reported by Jolt Physics. 
 * It does not process events directly but dispatches them to specialized handlers 
 * ({@link BodyPairIgnoreHandler} and {@link TerrainContactHandler}) that are injected 
 * upon construction.
 * </p>
 * <p>
 * Using a dispatcher allows Velthoric to maintain a modular architecture where collision 
 * filtering and terrain interactions are managed as separate, swappable components.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VelthoricContactListener extends NativeObject {

    private final long physicsSystemPtr;

    /**
     * Constructs a new native ContactListener and attaches it to the specified Jolt PhysicsSystem.
     *
     * @param physicsSystemPtr       The native virtual address of the {@code JPH::PhysicsSystem}.
     *                               Must be a valid, initialized pointer.
     * @param world                  The Java {@code VxPhysicsWorld} instance this listener belongs to.
     * @param ignoreHandler          The handler for ignored body pairs (collision filtering).
     * @param terrainHandler         The handler for terrain-specific logic and interactions.
     */
    public VelthoricContactListener(long physicsSystemPtr, Object world, BodyPairIgnoreHandler ignoreHandler, TerrainContactHandler terrainHandler) {
        super(nAttachVelthoricContactListener(physicsSystemPtr, world));
        this.physicsSystemPtr = physicsSystemPtr;

        // Inject the specialized handlers into the native listener dispatcher
        nSetBodyPairIgnoreHandler(va(), ignoreHandler.va());
        nSetTerrainContactHandler(va(), terrainHandler.va());
    }

    /**
     * Called internally by {@link #close()} to detach the listener from the PhysicsSystem
     * and securely delete its native memory.
     *
     * @param address The native virtual address of the ContactListener dispatcher.
     */
    @Override
    protected void nClose(long address) {
        nDetachVelthoricContactListener(physicsSystemPtr, address);
    }

    // Native JNI Bridge

    /**
     * Allocates a new native ContactListener and registers it with the Jolt PhysicsSystem.
     * 
     * @param physicsSystemPtr Address of the native Jolt PhysicsSystem.
     * @param world            JNI reference to the Java world.
     * @return Virtual address of the new dispatcher instance.
     */
    private static native long nAttachVelthoricContactListener(long physicsSystemPtr, Object world);

    /**
     * Detaches the listener from Jolt and securely deletes the instance.
     * 
     * @param physicsSystemPtr Address of the native Jolt PhysicsSystem.
     * @param listenerPtr       Address of the listener dispatcher to destroy.
     */
    private static native void nDetachVelthoricContactListener(long physicsSystemPtr, long listenerPtr);

    /**
     * Injects the native BodyPairIgnoreHandler into the dispatcher.
     * @param listenerPtr Address of the dispatcher.
     * @param handlerPtr  Address of the ignore handler.
     */
    private static native void nSetBodyPairIgnoreHandler(long listenerPtr, long handlerPtr);

    /**
     * Injects the native TerrainContactHandler into the dispatcher.
     * @param listenerPtr Address of the dispatcher.
     * @param handlerPtr  Address of the terrain handler.
     */
    private static native void nSetTerrainContactHandler(long listenerPtr, long handlerPtr);
}