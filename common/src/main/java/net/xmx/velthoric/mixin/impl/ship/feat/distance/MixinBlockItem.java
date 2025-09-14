/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.ship.VxShipUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @WrapOperation(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState velthoric$transformPlayerForPlacementState(BlockItem instance, BlockPlaceContext context, Operation<BlockState> original) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            return VxShipUtil.transformPlayerTemporarily(
                serverPlayer, context.getLevel(), context.getClickedPos(),
                () -> original.call(instance, context)
            );
        }
        return original.call(instance, context);
    }
}