/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.sound;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.VxShipUtil;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Shadow @Final private Minecraft minecraft;

    @Redirect(

            method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/client/resources/sounds/SimpleSoundInstance"
            )
    )
    private SimpleSoundInstance velthoric$transformShipSoundPosition(

            SoundEvent soundEvent, SoundSource source, float volume, float pitch, RandomSource random, double x, double y, double z
    ) {
        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(x, y, z));
        ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForChunk(chunkPos);

        if (plotInfo != null) {
            VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
            UUID shipId = plotInfo.shipId();
            var index = objectManager.getStore().getIndexForId(shipId);

            if (index != null) {
                RVec3 shipPos = new RVec3();
                Quat shipRot = new Quat();
                float partialTick = this.minecraft.getFrameTime();
                objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

                BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();
                Vec3 worldPos = VxShipUtil.shipToWorld(new Vec3(x, y, z), shipPos, shipRot, plotOrigin);

                return new SimpleSoundInstance(soundEvent, source, volume, pitch, random, worldPos.x(), worldPos.y(), worldPos.z());
            }
        }

        return new SimpleSoundInstance(soundEvent, source, volume, pitch, random, x, y, z);
    }
}