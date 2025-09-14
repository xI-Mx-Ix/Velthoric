/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.xmx.velthoric.ship.VxShipUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockPlaceContext.class)
public abstract class MixinBlockPlaceContext {

    @WrapOperation(
        method = "getNearestLookingDirections",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;orderedByNearest(Lnet/minecraft/world/entity/Entity;)[Lnet/minecraft/core/Direction;")
    )
    private Direction[] velthoric$transformPlayerForDirection(Entity entity, Operation<Direction[]> original) {
        BlockPlaceContext self = (BlockPlaceContext) (Object) this;
        if (entity instanceof ServerPlayer serverPlayer) {
            return VxShipUtil.transformPlayerTemporarily(
                serverPlayer, self.getLevel(), self.getClickedPos(),
                () -> original.call(entity)
            );
        }
        return original.call(entity);
    }
}