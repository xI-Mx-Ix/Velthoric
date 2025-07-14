package net.xmx.xbullet.physics.object.riding;

import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;

public class PlayerRidingSystem {

    public static void startRiding(ServerPlayer player, IPhysicsObject physicsObject) {
        if (player.isPassenger()) {
            return;
        }
        RidingProxyEntity proxy = new RidingProxyEntity(player.level(), physicsObject);

        if (player.level().addFreshEntity(proxy)) {
            player.startRiding(proxy, true);
        }
    }

    public static void stopRiding(ServerPlayer player) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
    }
}