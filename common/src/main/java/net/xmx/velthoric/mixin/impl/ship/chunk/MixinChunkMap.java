/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.chunk;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.ship.VxShipUtil;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow @Final
    ServerLevel level;

    @Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void velthoric$correctEuclideanDistanceCheck(ChunkPos chunkPos, Entity entity, CallbackInfoReturnable<Double> cir) {

        double chunkCenterX = chunkPos.getMiddleBlockX();
        double chunkCenterZ = chunkPos.getMiddleBlockZ();

        double shipAwareDistanceSq = VxShipUtil.sqrdShips(entity, chunkCenterX, entity.getY(), chunkCenterZ);

        cir.setReturnValue(shipAwareDistanceSq);
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("RETURN"), cancellable = true)
    private void velthoric$enableSpawningOnShipChunks(ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {

        if (cir.getReturnValue()) {
            return;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
        if (physicsWorld != null) {
            VxPlotManager plotManager = physicsWorld.getPlotManager();
            if (plotManager != null && plotManager.getShipManaging(chunkPos) != null) {

                cir.setReturnValue(true);
            }
        }
    }
}