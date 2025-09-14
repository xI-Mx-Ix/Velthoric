/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.ship.VxShipUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Redirect(
            method = "pick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    public BlockHitResult addShipsToRaycast(final Level receiver, final ClipContext ctx) {
        return VxShipUtil.clipIncludeShips(receiver, ctx);
    }


    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void velthoric$replaceDistanceToSqrWithShipAwareVersion(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(VxShipUtil.sqrdShips((Entity) (Object) this, x, y, z));
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
    private void velthoric$replaceDistanceToSqrWithShipAwareVersion(Vec3 vec, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(VxShipUtil.sqrdShips((Entity) (Object) this, vec.x(), vec.y(), vec.z()));
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void velthoric$replaceDistanceToSqrWithShipAwareVersion(Entity other, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(VxShipUtil.sqrdShips((Entity) (Object) this, other));
    }
}