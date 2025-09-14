/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.particle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow private @Nullable ClientLevel level;
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    @Nullable
    protected abstract Particle addParticleInternal(ParticleOptions options, boolean alwaysShow, boolean minimal, double x, double y, double z, double xd, double yd, double zd);

    private Vec3 velthoric$shipToWorld(Vec3 localVec, RVec3 shipPos, Quat shipRot, BlockPos plotOrigin) {
        Vector3d worldVec = new Vector3d(localVec.x - plotOrigin.getX(), localVec.y - plotOrigin.getY(), localVec.z - plotOrigin.getZ());
        new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldVec);
        worldVec.add(shipPos.x(), shipPos.y(), shipPos.z());
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }

    @Inject(
        method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void velthoric$transformShipParticle(
        ParticleOptions options, boolean alwaysShow, boolean minimal,
        double x, double y, double z, double xd, double yd, double zd,
        CallbackInfoReturnable<Particle> cir) {

        if (this.level == null) return;

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
                Vec3 worldPos = velthoric$shipToWorld(new Vec3(x, y, z), shipPos, shipRot, plotOrigin);

                Vector3d worldVel = new Vector3d(xd, yd, zd);
                new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldVel);

                float shipVelX = objectManager.getStore().state1_velX[index];
                float shipVelY = objectManager.getStore().state1_velY[index];
                float shipVelZ = objectManager.getStore().state1_velZ[index];
                worldVel.add(shipVelX * 0.05, shipVelY * 0.05, shipVelZ * 0.05);

                cir.setReturnValue(this.addParticleInternal(options, alwaysShow, minimal, worldPos.x, worldPos.y, worldPos.z, worldVel.x, worldVel.y, worldVel.z));
            }
        }
    }
}