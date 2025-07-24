package net.xmx.vortex.natives;

import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class NativeFailureHandler {

    private static final Component NATIVES_UNSUPPORTED_MESSAGE = Component.literal(
            "§l§6[Vortex Physics]§r §cPhysics are disabled on this server because the required native libraries are not supported on the host's system."
    );

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().sendSystemMessage(NATIVES_UNSUPPORTED_MESSAGE);
    }
}