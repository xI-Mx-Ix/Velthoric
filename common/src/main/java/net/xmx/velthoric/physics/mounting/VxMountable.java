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
 * An interface for physics bodies that players can mount.
 * This defines the contract for handling player interaction, input, and lifecycle events
 * related to mounting.
 *
 * @author xI-Mx-Ix
 */
public interface VxMountable {

    /**
     * Gets the unique UUID of the physics body.
     *
     * @return The physics body's UUID.
     */
    UUID getPhysicsId();

    /**
     * Gets the physics world this body belongs to.
     *
     * @return The physics world.
     */
    VxPhysicsWorld getPhysicsWorld();

    /**
     * Gets the current transformation (position and rotation) of the body.
     *
     * @return The body's transform.
     */
    VxTransform getTransform();

    /**
     * Defines all seats for this mountable body by adding them to the provided builder.
     * This method is called by the {@link net.xmx.velthoric.physics.mounting.manager.VxMountingManager}
     * when the body is created to automatically register its seats.
     * <p>
     * Example implementation:
     * <pre>{@code
     * @Override
     * public void defineSeats(VxSeat.Builder builder) {
     *     builder.addSeat(new VxSeat(
     *         UUID.randomUUID(),
     *         "driver_seat",
     *         new AABB(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5),
     *         new Vector3f(0.0f, 0.5f, 0.0f),
     *         true
     *     ));
     * }
     * }</pre>
     *
     * @param builder The {@link VxSeat.Builder} to which seats should be added.
     */
    void defineSeats(VxSeat.Builder builder);

    /**
     * Called when a player successfully starts mounting this body.
     *
     * @param player The player who started mounting.
     * @param seat   The seat the player is occupying.
     */
    default void onStartMounting(ServerPlayer player, VxSeat seat) {}

    /**
     * Called when a player stops mounting this body.
     *
     * @param player The player who stopped mounting.
     */
    default void onStopMounting(ServerPlayer player) {}

    /**
     * Handles movement and action input from a player in a driver's seat.
     *
     * @param driver The player driving the body.
     * @param input  The current input state from the player.
     */
    default void handleDriverInput(ServerPlayer driver, VxMountInput input) {}
}