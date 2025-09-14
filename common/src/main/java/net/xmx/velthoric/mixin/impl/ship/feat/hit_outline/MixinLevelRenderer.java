/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.hit_outline;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow @Nullable private ClientLevel level;
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    private static void renderShape(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float g, float h, float i, float j) {
        throw new AssertionError();
    }

    @Inject(
            method = "renderHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"
            ),
            cancellable = true
    )
    private void velthoric$transformShipHitOutline(
            PoseStack poseStack, VertexConsumer vertexConsumer, Entity entity,
            double camX, double camY, double camZ, BlockPos pos, BlockState state, CallbackInfo ci) {

        if (this.level == null) return;

        ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForChunk(new ChunkPos(pos));
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

                poseStack.pushPose();

                poseStack.translate(shipPos.x() - camX, shipPos.y() - camY, shipPos.z() - camZ);

                poseStack.mulPose(new Quaternionf(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()));

                poseStack.translate(pos.getX() - plotOrigin.getX(), pos.getY() - plotOrigin.getY(), pos.getZ() - plotOrigin.getZ());

                VoxelShape shape = state.getShape(this.level, pos, CollisionContext.of(entity));
                renderShape(poseStack, vertexConsumer, shape, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 0.4F);

                poseStack.popPose();
                ci.cancel();
            }
        }
    }
}