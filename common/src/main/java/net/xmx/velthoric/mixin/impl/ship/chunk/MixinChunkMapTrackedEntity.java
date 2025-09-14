/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.VxShipUtil;
import net.xmx.velthoric.ship.body.VxShipBody;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.TrackedEntity.class)
public class MixinChunkMapTrackedEntity {

    @Shadow @Final Entity entity;
    @Unique private VxShipBody velthoric$cachedShipForFrame = null;

    @ModifyExpressionValue(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 velthoric$transformEntityPositionForTracking(Vec3 originalPosition) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.entity.level().dimension());
        if (physicsWorld != null && physicsWorld.getPlotManager() != null) {
            VxShipBody ship = physicsWorld.getPlotManager().getShipManaging(this.entity.chunkPosition());
            this.velthoric$cachedShipForFrame = ship;

            if (ship != null) {
                return VxShipUtil.shipToWorld(
                    originalPosition,
                    ship.getGameTransform().getTranslation(),
                    ship.getGameTransform().getRotation(),
                    ship.getPlotCenter().getWorldPosition()
                );
            }
        }
        this.velthoric$cachedShipForFrame = null;
        return originalPosition;
    }

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"))
    private boolean velthoric$forceBroadcastOnShip(Entity instance, ServerPlayer serverPlayer, Operation<Boolean> original) {
        if (this.velthoric$cachedShipForFrame != null) {
            return true;
        }
        return original.call(instance, serverPlayer);
    }
}