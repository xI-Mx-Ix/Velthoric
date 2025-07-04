package net.xmx.xbullet.physics.object.riding.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.physics.object.riding.RidingManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class ServerRidingEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PhysicsWorld.getAll().forEach(RidingManager::serverTick);
        }
    }

    @SubscribeEvent
    public static void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        RidingManager.onPlayerDisconnect(event.getEntity());
    }
}