/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding;

import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.riding.input.VxRideInput;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public interface VxRideable {

    UUID getPhysicsId();

    VxPhysicsWorld getWorld();

    VxTransform getTransform();

    void onStartRiding(ServerPlayer player, VxSeat seat);

    void onStopRiding(ServerPlayer player);

    void handleDriverInput(ServerPlayer driver, VxRideInput input);
}