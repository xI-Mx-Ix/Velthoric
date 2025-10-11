/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type.internal;

/**
 * A lightweight, final, server-only handle for a Jolt physics body.
 * This class is a simple data container for the Jolt body ID and the data store index.
 * It is not meant to be inherited from. It contains no game logic.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyHandle {

    /**
     * The ID of the body in the Jolt physics simulation. 0 if not yet added.
     */
    private int bodyId = 0;

    /**
     * The index of this body's data in the VxObjectDataStore. -1 if not yet added.
     */
    private int dataStoreIndex = -1;

    /**
     * Gets the Jolt body ID.
     *
     * @return The body ID.
     */
    public int getBodyId() {
        return this.bodyId;
    }

    /**
     * Sets the Jolt body ID. This is called by the VxObjectManager after creation.
     *
     * @param bodyId The new body ID.
     */
    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    /**
     * Gets the index in the data store.
     *
     * @return The data store index.
     */
    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    /**
     * Sets the index in the data store. This is called by the VxObjectManager on addition.
     *
     * @param dataStoreIndex The new data store index.
     */
    public void setDataStoreIndex(int dataStoreIndex) {
        this.dataStoreIndex = dataStoreIndex;
    }
}