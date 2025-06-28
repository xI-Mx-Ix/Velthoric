package net.xmx.xbullet.physics.core;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;

public class ClientPhysicsPauseHandler {

    private static boolean lastKnownPauseState = false;
    private static long pauseStartTimeNanos = 0L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            if (lastKnownPauseState) {
                lastKnownPauseState = false;
            }
            return;
        }

        boolean isGamePaused = mc.isPaused();

        if (isGamePaused != lastKnownPauseState) {
            lastKnownPauseState = isGamePaused;

            if (isGamePaused) {
                XBullet.LOGGER.debug("Client is pausing. Pausing all physics worlds...");

                pauseStartTimeNanos = System.nanoTime();
                PhysicsWorldRegistry.getInstance().getAllPhysicsWorlds().values().forEach(PhysicsWorld::pause);
            } else {
                XBullet.LOGGER.debug("Client is resuming. Resuming all physics worlds...");

                if (pauseStartTimeNanos > 0) {
                    pauseStartTimeNanos = 0L;
                }
                PhysicsWorldRegistry.getInstance().getAllPhysicsWorlds().values().forEach(PhysicsWorld::resume);
            }
        }
    }
}