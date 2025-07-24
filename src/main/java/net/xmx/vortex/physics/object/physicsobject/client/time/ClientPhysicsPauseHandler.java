package net.xmx.vortex.physics.object.physicsobject.client.time;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

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

                VxClientClock.getInstance().resume();
                wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != wasPaused) {
            if (isNowPaused) {
                VxMainClass.LOGGER.debug("Client game is pausing...");
                VxClientClock.getInstance().pause();
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::pause);
            } else {
                VxMainClass.LOGGER.debug("Client game is resuming...");
                VxClientClock.getInstance().resume();
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::resume);
            }
            wasPaused = isNowPaused;
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        VxClientClock.getInstance().reset();
        wasPaused = false;
    }
}