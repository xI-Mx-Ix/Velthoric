package net.xmx.xbullet.physics.object.physicsobject.client.time;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class ClientPhysicsPauseHandler {

    private static boolean wasPaused = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            if (wasPaused) {

                ClientClock.getInstance().resume();
                wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != wasPaused) {
            if (isNowPaused) {
                XBullet.LOGGER.debug("Client game is pausing...");
                ClientClock.getInstance().pause();
                PhysicsWorld.getAll().forEach(PhysicsWorld::pause);
            } else {
                XBullet.LOGGER.debug("Client game is resuming...");
                ClientClock.getInstance().resume();
                PhysicsWorld.getAll().forEach(PhysicsWorld::resume);
            }
            wasPaused = isNowPaused;
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientClock.getInstance().reset();
        wasPaused = false;
    }
}