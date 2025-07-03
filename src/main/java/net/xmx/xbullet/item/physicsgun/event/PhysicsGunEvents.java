package net.xmx.xbullet.item.physicsgun.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.physicsgun.PhysicsGunItem;
import net.xmx.xbullet.item.physicsgun.PhysicsGunManager;

public class PhysicsGunEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var server = event.getServer();
            var manager = PhysicsGunManager.getInstance();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                        || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

                if (isHoldingGun) {
                    manager.serverTick(player);
                } else {
                    manager.stopGrab(player);
                }
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            PhysicsGunManager.getInstance().stopGrab((ServerPlayer) player);
        }
    }

    private boolean isHoldingPhysicsGun(Player player) {
        return player.getMainHandItem().getItem() instanceof PhysicsGunItem || player.getOffhandItem().getItem() instanceof PhysicsGunItem;
    }
}