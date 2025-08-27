package net.xmx.velthoric.physics.riding;

import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public interface Rideable {

    UUID getPhysicsId();

    VxPhysicsWorld getWorld();

    VxTransform getGameTransform();

    void onStartRiding(ServerPlayer player, Seat seat);

    void onStopRiding(ServerPlayer player);
}