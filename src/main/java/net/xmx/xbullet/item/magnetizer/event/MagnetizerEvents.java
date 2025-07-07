package net.xmx.xbullet.item.magnetizer.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.magnetizer.MagnetizerManager;

public class MagnetizerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var server = event.getServer();
            var manager = MagnetizerManager.getInstance();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean isHoldingMagnetizer = player.getMainHandItem().is(ItemRegistry.MAGNETIZER.get())
                        || player.getOffhandItem().is(ItemRegistry.MAGNETIZER.get());

                if (isHoldingMagnetizer) {
                    if (manager.isActing(player)) {
                        manager.serverTick(player);
                    }
                } else {
                    manager.stop(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            MagnetizerManager.getInstance().stop((ServerPlayer) player);
        }
    }
}