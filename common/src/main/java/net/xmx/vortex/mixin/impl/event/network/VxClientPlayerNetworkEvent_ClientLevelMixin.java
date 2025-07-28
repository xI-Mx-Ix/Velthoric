package net.xmx.vortex.mixin.impl.event.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.xmx.vortex.event.api.VxClientPlayerNetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class VxClientPlayerNetworkEvent_ClientLevelMixin {

    @Inject(method = "disconnect()V", at = @At("HEAD"))
    private void vortex_fireClientLoggingOutEvent(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();

        MultiPlayerGameMode gameMode = minecraft.gameMode;
        LocalPlayer player = minecraft.player;

        Connection connection = minecraft.getConnection() != null ? minecraft.getConnection().getConnection() : null;

        VxClientPlayerNetworkEvent.LoggingOut.EVENT.invoker().onClientLoggingOut(
                new VxClientPlayerNetworkEvent.LoggingOut(gameMode, player, connection)
        );
    }
}