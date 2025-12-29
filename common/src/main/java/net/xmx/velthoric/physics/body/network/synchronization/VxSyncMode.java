/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.synchronization;

/**
 * Defines the authority level for a synchronized data entry.
 * This determines which side (client or server) is allowed to modify the data
 * and propagate changes to the other side.
 *
 * @author xI-Mx-Ix
 */
public enum VxSyncMode {
    /**
     * The server has authority over this data.
     * <p>
     * - Server: Can set values. Changes are sent to all clients.
     * - Client: Cannot set values directly. Must wait for updates from the server.
     * Attempting to set this on the client will result in an exception.
     * Attempting to bypass this via packets will result in a server warning.
     */
    SERVER,

    /**
     * The client has authority over this data.
     * <p>
     * - Client: Can set values. Changes are sent to the server.
     * - Server: Accepts updates from the client, validates authority, and replicates
     * the change to other clients.
     */
    CLIENT
}