package net.xmx.vortex.physics.object.riding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;

public class PlayerRidingSystem {

    public static void startRiding(ServerPlayer player, IPhysicsObject physicsObject) {
        if (player.isPassenger() || player.level().isClientSide()) {
            return;
        }

        RidingProxyEntity oldProxy = physicsObject.getRidingProxy();
        if (oldProxy != null && !oldProxy.isRemoved()) {
            oldProxy.remove(Entity.RemovalReason.DISCARDED);
        }

        RidingProxyEntity proxy = new RidingProxyEntity(player.level(), physicsObject);
        proxy.setIntendedPassenger(player);
        physicsObject.setRidingProxy(proxy);

        if (player.level().addFreshEntity(proxy)) {
            player.startRiding(proxy, true);

        } else {

            physicsObject.setRidingProxy(null);
        }
    }

    public static void stopRiding(ServerPlayer player) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
    }
}