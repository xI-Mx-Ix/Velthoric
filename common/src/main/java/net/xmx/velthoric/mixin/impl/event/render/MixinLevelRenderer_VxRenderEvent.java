/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.event.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.xmx.velthoric.event.api.VxRenderEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author xI-Mx-Ix
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_VxRenderEvent {

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_fireRenderStageAfterSky(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_SKY, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_fireRenderStageAfterEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V",
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_fireRenderStageAfterBlockEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_fireRenderStageAfterParticles(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_PARTICLES, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_fireRenderStageAfterWeather(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_WEATHER, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("TAIL")
    )
    private void velthoric_fireRenderStageLevelLast(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.invoker().onRenderLevelStage(
                new VxRenderEvent.ClientRenderLevelStageEvent(VxRenderEvent.ClientRenderLevelStageEvent.Stage.LEVEL_LAST, (LevelRenderer)(Object)this, poseStack, partialTick, lightTexture, projectionMatrix)
        );
    }
}