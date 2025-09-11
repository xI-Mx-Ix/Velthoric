/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.event.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin_VxClientPlayerNetworkEvent {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Connection connection;

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("TAIL"))
    private void velthoric_fireClientLoggingInEvent(ClientboundLoginPacket packet, CallbackInfo ci) {

        VxClientPlayerNetworkEvent.LoggingIn.EVENT.invoker().onClientLoggingIn(
                new VxClientPlayerNetworkEvent.LoggingIn(this.minecraft.gameMode, this.minecraft.player, this.connection)
        );
    }

    private LocalPlayer capturedOldPlayerForClone;

    @Inject(
            method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("HEAD")
    )
    private void velthoric_captureOldPlayerForClone(ClientboundRespawnPacket packet, CallbackInfo ci) {
        this.capturedOldPlayerForClone = this.minecraft.player;
    }

    @Inject(
            method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("TAIL")
    )
    private void velthoric_fireClientPlayerCloneEvent(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LocalPlayer newPlayer = this.minecraft.player;

        if (this.capturedOldPlayerForClone != null && newPlayer != null && this.capturedOldPlayerForClone != newPlayer) {

            VxClientPlayerNetworkEvent.Clone.EVENT.invoker().onClientPlayerClone(
                    new VxClientPlayerNetworkEvent.Clone(this.minecraft.gameMode, this.capturedOldPlayerForClone, newPlayer, this.connection)
            );
        }

        this.capturedOldPlayerForClone = null;
    }
}