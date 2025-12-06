/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.xmx.velthoric.renderer.mesh.VxRenderQueue;
import net.xmx.velthoric.renderer.mesh.arena.VxArenaBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into LevelRenderer to manage the entire lifecycle of the custom rendering system.
 * <p>
 * 1. HEAD: Resets the queue and prepares buffers.
 * 2. AFTER_ENTITIES: Flushes the queue to draw meshes into the G-Buffers/World.
 *
 * @author xI-Mx-Ix
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * Injects at the very start of the level rendering.
     * Clears the queue from the previous frame and prepares the Arena VBOs.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void velthoric_onRenderLevel_Head(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        // Prepare all arena-based models for rendering (bind shared VAO state if needed).
        VxArenaBuffer.getInstance().preRender();

        // Reset the batch queue for the new frame so it's empty before we start adding meshes.
        VxRenderQueue.getInstance().reset();
    }

    /**
     * Injects a call right after vanilla entities are rendered.
     * <p>
     * This is CRITICAL for Shaderpacks:
     * - It ensures meshes are drawn into the G-Buffers (Geometry Buffers).
     * - It ensures correct Depth Testing against the world.
     * - It ensures meshes are drawn before Translucent objects (water/glass).
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
                    // Targets the end of the entity rendering block (entitySmoothCutout is usually the last one)
                    args = "f=Lnet/minecraft/client/renderer/RenderType;args[0].staticValue=Lnet/minecraft/client/renderer/RenderType;entitySmoothCutout(Lnet/minecraft/resources/ResourceLocation;)",
                    shift = At.Shift.AFTER
            )
    )
    private void velthoric_onRenderLevel_AfterEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        // Execute all queued render calls within the world rendering context.
        VxRenderQueue.getInstance().flush();
    }
}