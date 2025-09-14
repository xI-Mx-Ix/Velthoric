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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Particle.class)
public abstract class MixinParticle {

    @Shadow public abstract void setPos(double x, double y, double z);
    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;
    @Shadow protected double xo;
    @Shadow protected double yo;
    @Shadow protected double zo;

    private Vec3 velthoric$shipToWorld(Vec3 localVec, RVec3 shipPos, Quat shipRot, BlockPos plotOrigin) {
        Vector3d worldVec = new Vector3d(localVec.x - plotOrigin.getX(), localVec.y - plotOrigin.getY(), localVec.z - plotOrigin.getZ());
        new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldVec);
        worldVec.add(shipPos.x(), shipPos.y(), shipPos.z());
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", at = @At("TAIL"))
    private void velthoric$transformOnConstruct(
        ClientLevel level, double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {

        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(x, y, z));
        ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForChunk(chunkPos);

        if (plotInfo != null) {
            VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
            UUID shipId = plotInfo.shipId();
            var index = objectManager.getStore().getIndexForId(shipId);

            if (index != null) {
                RVec3 shipPos = new RVec3();
                Quat shipRot = new Quat();
                float partialTick = Minecraft.getInstance().getFrameTime();
                objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

                BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();
                Vec3 worldPos = velthoric$shipToWorld(new Vec3(x, y, z), shipPos, shipRot, plotOrigin);

                this.setPos(worldPos.x, worldPos.y, worldPos.z);
                this.xo = worldPos.x;
                this.yo = worldPos.y;
                this.zo = worldPos.z;

                Vector3d worldVel = new Vector3d(this.xd, this.yd, this.zd);
                new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldVel);

                float shipVelX = objectManager.getStore().state1_velX[index];
                float shipVelY = objectManager.getStore().state1_velY[index];
                float shipVelZ = objectManager.getStore().state1_velZ[index];
                worldVel.add(shipVelX * 0.05, shipVelY * 0.05, shipVelZ * 0.05);

                this.xd = worldVel.x;
                this.yd = worldVel.y;
                this.zd = worldVel.z;
            }
        }
    }
}