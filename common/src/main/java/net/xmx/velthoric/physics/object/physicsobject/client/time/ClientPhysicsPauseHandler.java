package net.xmx.velthoric.physics.object.physicsobject.client.time;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public class ClientPhysicsPauseHandler {

    private static boolean wasPaused = false;

    public static void registerEvents() {
        ClientTickEvent.CLIENT_POST.register(ClientPhysicsPauseHandler::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (minecraft.level == null) {
            if (wasPaused) {
                VxClientClock.getInstance().resume();
                wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = minecraft.isPaused();

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

    public static void onClientDisconnect() {
        VxClientClock.getInstance().reset();
        wasPaused = false;
    }
}