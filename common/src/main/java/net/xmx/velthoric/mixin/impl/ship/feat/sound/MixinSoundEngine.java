/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.sound;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.xmx.velthoric.mixin.util.ship.sound.IVxHasOpenALVelocity;
import net.xmx.velthoric.mixin.util.ship.sound.VxSoundOnShip;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Shadow @Final private Listener listener;

    @Inject(method = "updateSource", at = @At("HEAD"))
    private void velthoric$updateListenerVelocity(Camera renderInfo, CallbackInfo ci) {
        if (!renderInfo.isInitialized()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        Vector3d velocity = new Vector3d(0.0);

        if (player != null) {
            ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForChunk(player.chunkPosition());
            if (plotInfo != null) {
                VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
                UUID shipId = plotInfo.shipId();
                var index = objectManager.getStore().getIndexForId(shipId);
                if (index != null) {
                    velocity.x = objectManager.getStore().state1_velX[index];
                    velocity.y = objectManager.getStore().state1_velY[index];
                    velocity.z = objectManager.getStore().state1_velZ[index];
                }
            }
        }

        ((IVxHasOpenALVelocity) this.listener).velthoric$setVelocity(velocity);
    }

    @WrapOperation(
            method = "tickNonPaused",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object velthoric$injectSourceVelocity(Map<SoundInstance, ChannelAccess.ChannelHandle> instance, Object key, Operation<Object> original) {
        Object result = original.call(instance, key);

        if (key instanceof VxSoundOnShip shipSound && result instanceof ChannelAccess.ChannelHandle handle) {
            handle.execute(channel -> {
                ((IVxHasOpenALVelocity) channel).velthoric$setVelocity(shipSound.getVelocity());
            });
        }

        return result;
    }
}