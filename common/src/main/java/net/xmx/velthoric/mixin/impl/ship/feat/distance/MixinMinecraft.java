/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.velthoric.mixin.util.ship.IVxMinecraft;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IVxMinecraft {

    @Unique
    @Nullable
    private HitResult velthoric$originalCrosshairTarget;

    @Override
    public void velthoric$setOriginalCrosshairTarget(@Nullable HitResult hitResult) {
        this.velthoric$originalCrosshairTarget = hitResult;
    }

    @WrapOperation(
            method = "startUseItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;")
    )
    private InteractionResult velthoric$useCorrectCrosshairTarget(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {

        if (this.velthoric$originalCrosshairTarget instanceof BlockHitResult correctHitResult) {
            return original.call(gameMode, player, hand, correctHitResult);
        }

        return original.call(gameMode, player, hand, hitResult);
    }
}