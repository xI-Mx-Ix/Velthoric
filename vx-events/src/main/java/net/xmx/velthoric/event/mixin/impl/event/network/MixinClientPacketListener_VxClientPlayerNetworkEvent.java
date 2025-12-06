/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.mixin.impl.event.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin implementation for triggering Velthoric client-side network events.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener_VxClientPlayerNetworkEvent {

    /**
     * Stores the player instance before a respawn packet is processed to detect entity changes.
     */
    @Unique
    private LocalPlayer capturedOldPlayerForClone;

    /**
     * Intercepts the completion of the login process to fire the {@link VxClientPlayerNetworkEvent.LoggingIn} event.
     *
     * @param packet The login packet received from the server.
     * @param ci     The callback information.
     */
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("TAIL"))
    private void velthoric_fireClientLoggingInEvent(ClientboundLoginPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // Use the Accessor interface to safely get the protected 'connection' field from the parent class
        Connection connection = ((ClientCommonPacketListenerImplAccessor) this).velthoric$getConnection();

        VxClientPlayerNetworkEvent.LoggingIn.EVENT.invoker().onClientLoggingIn(
                new VxClientPlayerNetworkEvent.LoggingIn(mc.gameMode, mc.player, connection)
        );
    }

    /**
     * Captures the current player entity instance immediately before the respawn packet is handled.
     */
    @Inject(
            method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("HEAD")
    )
    private void velthoric_captureOldPlayerForClone(ClientboundRespawnPacket packet, CallbackInfo ci) {
        this.capturedOldPlayerForClone = Minecraft.getInstance().player;
    }

    /**
     * Checks if the player entity has changed after the respawn packet is processed and fires the Clone event.
     */
    @Inject(
            method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("TAIL")
    )
    private void velthoric_fireClientPlayerCloneEvent(ClientboundRespawnPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer newPlayer = mc.player;

        // Retrieve connection via Accessor again
        Connection connection = ((ClientCommonPacketListenerImplAccessor) this).velthoric$getConnection();

        if (this.capturedOldPlayerForClone != null && newPlayer != null && this.capturedOldPlayerForClone != newPlayer) {
            VxClientPlayerNetworkEvent.Clone.EVENT.invoker().onClientPlayerClone(
                    new VxClientPlayerNetworkEvent.Clone(mc.gameMode, this.capturedOldPlayerForClone, newPlayer, connection)
            );
        }

        this.capturedOldPlayerForClone = null;
    }
}