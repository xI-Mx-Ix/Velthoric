/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.mixin.util.ship.IVxMinecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow @Final Minecraft minecraft;

    @WrapOperation(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;")
    )
    public HitResult velthoric$storeCorrectCrosshairTarget(Entity player, double range, float partialTicks, boolean includeFluids, Operation<HitResult> original) {

        Vec3 from = player.getEyePosition(partialTicks);
        Vec3 lookVector = player.getViewVector(partialTicks);
        Vec3 to = from.add(lookVector.x * range, lookVector.y * range, lookVector.z * range);

        ClipContext context = new ClipContext(
                from,
                to,
                ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                player
        );
        HitResult correctHitResult = player.level().clip(context);
        ((IVxMinecraft) this.minecraft).velthoric$setOriginalCrosshairTarget(correctHitResult);
        return original.call(player, range, partialTicks, includeFluids);
    }
}