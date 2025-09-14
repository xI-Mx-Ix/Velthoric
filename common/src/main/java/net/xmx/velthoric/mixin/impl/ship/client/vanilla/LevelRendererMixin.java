/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.client.vanilla;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.mixin.impl.ship.client.vanilla.accessor.RenderChunkInfoAccessor;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This mixin modifies Minecraft's main rendering class (LevelRenderer) to support
 * the rendering of dynamic, transformed ship chunks. It carefully isolates ship
 * rendering from the vanilla rendering pipeline to prevent crashes and allow for
 * custom transformations (position, rotation).
 *
 * @author xI-Mx-Ix
 */
@Mixin(value = LevelRenderer.class, priority = 1001)
public abstract class LevelRendererMixin {

    // --- Shadow Fields & Methods ---
    // These are references to fields and methods in the original LevelRenderer class.

    @Shadow @Final private Minecraft minecraft;
    @Shadow private ViewArea viewArea;
    @Shadow @Final private ObjectArrayList<LevelRenderer.RenderChunkInfo> renderChunksInFrustum;

    // --- Unique Fields ---
    // These fields are added by this mixin and are unique to our implementation.

    @Unique private final RVec3 velthoric$interpolatedPosition = new RVec3();
    @Unique private final Quat velthoric$interpolatedRotation = new Quat();
    @Unique private final Map<UUID, ObjectArrayList<LevelRenderer.RenderChunkInfo>> velthoric$shipRenderChunks = new HashMap<>();

    /**
     * Injects at the end of the setupRender method to collect all visible ship chunks.
     * This method performs several key tasks:
     * 1. Removes any ship chunks that might have accidentally been added to the vanilla render list.
     * 2. Iterates through all known ships.
     * 3. Calculates their interpolated position and rotation for the current frame.
     * 4. Performs frustum culling to check if the ship is visible to the camera.
     * 5. If visible, it collects all associated RenderChunk objects for that ship.
     * 6. Stores these collected chunks in a map, ready for the rendering phase.
     */
    @Inject(method = "setupRender", at = @At("TAIL"))
    private void velthoric$collectShipChunks(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
        this.velthoric$shipRenderChunks.clear();

        // First, ensure no ship chunks are in the vanilla render list to prevent crashes.
        this.renderChunksInFrustum.removeIf(info -> {
            BlockPos origin = RenderChunkInfoAccessor.class.cast(info).getChunk().getOrigin();
            return VxClientPlotManager.getInstance().isShipChunk(origin.getX() >> 4, origin.getZ() >> 4);
        });

        VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
        if (plotManager.getAllShipIds().isEmpty()) {
            return;
        }

        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        float partialTick = this.minecraft.getFrameTime();

        for (UUID shipId : plotManager.getAllShipIds()) {
            var index = objectManager.getStore().getIndexForId(shipId);
            if (index == null) continue;

            // Get smooth, interpolated transform for the current frame.
            objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, velthoric$interpolatedPosition, velthoric$interpolatedRotation);

            ShipPlotInfo plotInfo = plotManager.getShipInfoForShip(shipId);
            if (plotInfo == null) continue;

            // Create a bounding box for the entire ship for frustum culling.
            int radius = plotManager.getPlotRadius(shipId);
            double renderSize = radius * 16.0 + 32.0;
            AABB shipBoundingBox = new AABB(
                    velthoric$interpolatedPosition.x() - renderSize, velthoric$interpolatedPosition.y() - renderSize, velthoric$interpolatedPosition.z() - renderSize,
                    velthoric$interpolatedPosition.x() + renderSize, velthoric$interpolatedPosition.y() + renderSize, velthoric$interpolatedPosition.z() + renderSize
            );

            // If the ship is in the camera's view, collect its chunks.
            if (frustum.isVisible(shipBoundingBox)) {
                ObjectArrayList<LevelRenderer.RenderChunkInfo> collectedChunks = new ObjectArrayList<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        for (int y = this.minecraft.level.getMinSection(); y < this.minecraft.level.getMaxSection(); y++) {
                            BlockPos chunkOrigin = new BlockPos(
                                    (plotInfo.plotCenter().x + x) << 4, y << 4, (plotInfo.plotCenter().z + z) << 4
                            );
                            ChunkRenderDispatcher.RenderChunk renderChunk = this.viewArea.getRenderChunkAt(chunkOrigin);
                            if (renderChunk != null && !renderChunk.getCompiledChunk().hasNoRenderableLayers()) {
                                collectedChunks.add(RenderChunkInfoAccessor.velthoric$new(renderChunk, null, 0));
                            }
                        }
                    }
                }
                if (!collectedChunks.isEmpty()) {
                    this.velthoric$shipRenderChunks.put(shipId, collectedChunks);
                }
            }
        }
    }

    /**
     * Injects at the end of the vanilla renderChunkLayer method.
     * After Minecraft has rendered its own chunks for a specific layer (e.g., SOLID, TRANSLUCENT),
     * this method iterates through our collected ship chunks and renders them.
     */
    @Inject(method = "renderChunkLayer", at = @At("TAIL"))
    private void velthoric$renderShips(RenderType renderType, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (this.velthoric$shipRenderChunks.isEmpty()) {
            return;
        }

        VxClientObjectManager manager = VxClientObjectManager.getInstance();
        float partialTick = this.minecraft.getFrameTime();

        for (Map.Entry<UUID, ObjectArrayList<LevelRenderer.RenderChunkInfo>> entry : this.velthoric$shipRenderChunks.entrySet()) {
            UUID shipId = entry.getKey();
            ObjectArrayList<LevelRenderer.RenderChunkInfo> chunksToRender = entry.getValue();

            var index = manager.getStore().getIndexForId(shipId);
            if (index == null) continue;

            // Get the same interpolated transform as used in the collection phase.
            manager.getInterpolator().interpolateFrame(manager.getStore(), index, partialTick, velthoric$interpolatedPosition, velthoric$interpolatedRotation);

            ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForShip(shipId);
            if (plotInfo == null) continue;

            BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();

            // --- Core Transformation Logic ---
            // This is where the magic happens. We manipulate the PoseStack to move and rotate
            // the entire coordinate system before drawing the chunks.
            poseStack.pushPose();
            // 1. Translate to the ship's world position.
            poseStack.translate(velthoric$interpolatedPosition.x() - camX, velthoric$interpolatedPosition.y() - camY, velthoric$interpolatedPosition.z() - camZ);
            // 2. Apply the ship's rotation.
            poseStack.mulPose(new Quaternionf(velthoric$interpolatedRotation.getX(), velthoric$interpolatedRotation.getY(), velthoric$interpolatedRotation.getZ(), velthoric$interpolatedRotation.getW()));
            // 3. Translate backwards by the plot's origin. This effectively moves the chunk from its
            //    storage location (e.g., x=5000) to the local origin (0,0,0) before transformation.
            poseStack.translate(-plotOrigin.getX(), -plotOrigin.getY(), -plotOrigin.getZ());

            // Now, render the chunks with the transformed PoseStack.
            velthoric$renderTransformedChunkLayer(renderType, poseStack, projectionMatrix, chunksToRender);

            // Restore the PoseStack to its original state for the next ship or vanilla rendering.
            poseStack.popPose();
        }
    }

    /**
     * A custom rendering method that draws a list of chunks using a given transformation.
     * This is a specialized version of the vanilla renderChunkLayer loop. It's necessary
     * because we must manually set up and tear down the shader state, as we are rendering
     * outside of the original method's main loop.
     */
    @Unique
    private void velthoric$renderTransformedChunkLayer(RenderType renderType, PoseStack poseStack, Matrix4f projectionMatrix, ObjectArrayList<LevelRenderer.RenderChunkInfo> chunksToRender) {
        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) return;

        // Set up standard shader samplers.
        for (int i = 0; i < 12; ++i) {
            int textureId = RenderSystem.getShaderTexture(i);
            shader.setSampler("Sampler" + i, textureId);
        }

        // Set up matrices for the shader.
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(projectionMatrix);
        }
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
        }

        // Activate the shader program. This is crucial as the vanilla method may have cleared it.
        RenderSystem.setupShaderLights(shader);
        shader.apply();

        Uniform uniform = shader.CHUNK_OFFSET;

        for (LevelRenderer.RenderChunkInfo chunkInfo : chunksToRender) {
            ChunkRenderDispatcher.RenderChunk renderChunk = RenderChunkInfoAccessor.class.cast(chunkInfo).getChunk();
            if (!renderChunk.getCompiledChunk().isEmpty(renderType)) {
                VertexBuffer vertexBuffer = renderChunk.getBuffer(renderType);
                BlockPos chunkOrigin = renderChunk.getOrigin();

                // The CHUNK_OFFSET uniform is used by the shader to correctly position blocks
                // within the chunk. We must set it to the chunk's absolute origin.
                if (uniform != null) {
                    uniform.set((float) (chunkOrigin.getX()), (float) (chunkOrigin.getY()), (float) (chunkOrigin.getZ()));
                    uniform.upload();
                }

                vertexBuffer.bind();
                vertexBuffer.draw();
            }
        }

        // Reset the chunk offset to zero.
        if (uniform != null) {
            uniform.set(0.0F, 0.0F, 0.0F);
        }

        // Deactivate the shader and clean up state.
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    /**
     * Prevents ship chunks from being added to the 'recently compiled' queue.
     * This is a critical fix. After a ship chunk is compiled, the ChunkRenderDispatcher
     * tries to notify the LevelRenderer. This inject cancels that notification for our
     * ship chunks, preventing them from entering the vanilla update logic which would
     * cause a crash due to their invalid ID (-1).
     */
    @Inject(method = "addRecentlyCompiledChunk", at = @At("HEAD"), cancellable = true)
    private void velthoric$preventShipChunkInQueue(ChunkRenderDispatcher.RenderChunk renderChunk, CallbackInfo ci) {
        BlockPos origin = renderChunk.getOrigin();
        if (VxClientPlotManager.getInstance().isShipChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
            ci.cancel();
        }
    }

    /**
     * Prevents vanilla chunks from "discovering" ship chunks as their neighbors.
     * The LevelRenderer builds its list of visible chunks by starting at the player and
     * traversing through neighbors. This inject intercepts that process. If a vanilla chunk
     * asks for a neighbor and the result is one of our ship chunks, we return null instead.
     * This completely isolates our ship chunks from the vanilla chunk traversal system,
     * preventing them from being processed incorrectly and causing a crash.
     */
    @Inject(method = "getRelativeFrom", at = @At("RETURN"), cancellable = true)
    private void velthoric$preventShipChunkDiscovery(BlockPos blockPos, ChunkRenderDispatcher.RenderChunk renderChunk, Direction direction, CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> cir) {
        ChunkRenderDispatcher.RenderChunk returnedChunk = cir.getReturnValue();
        if (returnedChunk != null) {
            BlockPos origin = returnedChunk.getOrigin();
            if (VxClientPlotManager.getInstance().isShipChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
                cir.setReturnValue(null);
            }
        }
    }
}