/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting;

import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An interface for physics objects that players can mount.
 * This defines the contract for handling player interaction, input, and lifecycle events
 * related to mounting.
 *
 * @author xI-Mx-Ix
 */
public interface VxMountable {

    /**
     * Gets the unique UUID of the physics body.
     *
     * @return The physics object's UUID.
     */
    UUID getPhysicsId();

    /**
     * Gets the physics world this object belongs to.
     *
     * @return The physics world.
     */
    VxPhysicsWorld getPhysicsWorld();

    /**
     * Gets the current transformation (position and rotation) of the object.
     *
     * @return The object's transform.
     */
    VxTransform getTransform();

    /**
     * Called when a player successfully starts mounting this object.
     *
     * @param player The player who started mounting.
     * @param seat   The seat the player is occupying.
     */
    default void onStartMounting(ServerPlayer player, VxSeat seat) {}

    /**
     * Called when a player stops mounting this object.
     *
     * @param player The player who stopped mounting.
     */
    default void onStopMounting(ServerPlayer player) {}

    /**
     * Handles movement and action input from a player in a driver's seat.
     *
     * @param driver The player driving the object.
     * @param input  The current input state from the player.
     */
    default void handleDriverInput(ServerPlayer driver, VxMountInput input) {}
}