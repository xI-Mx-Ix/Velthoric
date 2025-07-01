package net.xmx.xbullet.physics.physicsworld;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;

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
                wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != wasPaused) {
            if (isNowPaused) {

                XBullet.LOGGER.debug("Client game is pausing. Sending pause command to all physics worlds...");
                PhysicsWorldRegistry.getInstance().getAllPhysicsWorlds().values().forEach(PhysicsWorld::pause);
            } else {

                XBullet.LOGGER.debug("Client game is resuming. Sending resume command to all physics worlds...");
                PhysicsWorldRegistry.getInstance().getAllPhysicsWorlds().values().forEach(PhysicsWorld::resume);
            }

            wasPaused = isNowPaused;
        }
    }
}