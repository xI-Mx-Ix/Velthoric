/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.chunk;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.xmx.velthoric.mixin.util.ship.IVxClientPacketListener;
import net.xmx.velthoric.ship.chunk.VxClientChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener implements IVxClientPacketListener {

    @Unique
    private VxClientChunkManager velthoric$seamlessManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Minecraft minecraft, Screen screen, Connection connection, ServerData serverData, GameProfile gameProfile, WorldSessionTelemetryManager worldSessionTelemetryManager, CallbackInfo ci) {
        this.velthoric$seamlessManager = new VxClientChunkManager((ClientPacketListener) (Object) this);
    }

    @Override
    public VxClientChunkManager velthoric$getSeamlessManager() {
        return velthoric$seamlessManager;
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"), cancellable = true)
    private void velthoric$queueChunkPacket(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (this.velthoric$getSeamlessManager().queueIfNecessary(packet.getX(), packet.getZ(), packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void velthoric$queueBlockUpdatePacket(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (this.velthoric$getSeamlessManager().queueIfNecessary(pos.getX() >> 4, pos.getZ() >> 4, packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), cancellable = true)
    private void velthoric$queueSectionUpdatePacket(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        SectionPos sectionPos = packet.sectionPos;
        if (this.velthoric$getSeamlessManager().queueIfNecessary(sectionPos.x(), sectionPos.z(), packet)) {
            ci.cancel();
        }
    }
}