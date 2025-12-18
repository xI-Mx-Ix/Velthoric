/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.manager;

/**
 * Enum representing the reason for removing a physics body.
 * This is used to determine how the body should be handled when it is removed.
 *
 * @author xI-Mx-Ix
 */
public enum VxRemovalReason {
    /**
     *  The body is discarded and should not be saved.
     */
    DISCARD,

    /**
     * The body is being temporarily unloaded and should be saved to disk.
     */
    SAVE,

    /**
     * The body is being removed from memory because its chunk is unloading. Persistence is handled separately.
     */
    UNLOAD
}