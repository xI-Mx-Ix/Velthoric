/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.ship.VxShipUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow public ServerPlayer player;

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 velthoric$correctHitValidationCoordinates(Vec3 hitLocation, Vec3 blockCenter, Operation<Vec3> original) {
        Vec3 worldBlockCenter = VxShipUtil.getTruePosition(this.player.level(), blockCenter.x(), blockCenter.y(), blockCenter.z());
        return original.call(hitLocation, worldBlockCenter);
    }

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            )
    )
    private double velthoric$bypassInteractionDistanceCheck(Vec3 playerEyePos, Vec3 blockCenter, Operation<Double> original) {
        return 0.0;
    }
}