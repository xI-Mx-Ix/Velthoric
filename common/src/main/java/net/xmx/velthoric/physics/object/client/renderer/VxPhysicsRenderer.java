/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * The main client-side renderer for all physics objects.
 * This class hooks into Minecraft's rendering pipeline to draw physics objects in the world.
 * It performs frustum culling, calculates the final interpolated state for the render frame,
 * and dispatches the rendering call to the appropriate object-specific renderer.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsRenderer {

    /** An inflation value for the culling bounding box to prevent objects from disappearing at the edge of the screen. */
    private static final float CULLING_BOUNDS_INFLATION = 2.0f;

    // Reusable objects to avoid allocations in the render loop.
    private static final VxRenderState finalRenderState = new VxRenderState();
    private static final RVec3 interpolatedPosition = new RVec3();
    private static final Quat interpolatedRotation = new Quat();

    /**
     * Registers the necessary render event listeners.
     */
    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(VxPhysicsRenderer::onRenderLevelStage);
    }

    /**
     * The main render event handler, called every frame.
     *
     * @param event The render event containing context like the pose stack and partial ticks.
     */
    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        // We render after entities to ensure correct transparency and depth testing.
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        VxClientObjectManager manager = VxClientObjectManager.getInstance();
        VxClientObjectDataStore store = manager.getStore();
        VxClientObjectInterpolator interpolator = manager.getInterpolator();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f projectionMatrix = event.getProjectionMatrix();

        // Set up the frustum for culling objects outside the camera's view.
        Frustum frustum = new Frustum(poseStack.last().pose(), projectionMatrix);
        frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);

        poseStack.pushPose();
        // Translate the world so rendering is relative to the camera position.
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (UUID id : store.getAllObjectIds()) {
            Integer index = store.getIndexForId(id);
            if (index == null) continue;

            try {
                RVec3 lastPos = store.lastKnownPosition[index];

                // Create an Axis-Aligned Bounding Box (AABB) for the object.
                AABB objectAABB = new AABB(
                        lastPos.xx() - CULLING_BOUNDS_INFLATION, lastPos.yy() - CULLING_BOUNDS_INFLATION, lastPos.zz() - CULLING_BOUNDS_INFLATION,
                        lastPos.xx() + CULLING_BOUNDS_INFLATION, lastPos.yy() + CULLING_BOUNDS_INFLATION, lastPos.zz() + CULLING_BOUNDS_INFLATION
                );

                // Cull the object if it's not visible or hasn't been fully initialized.
                if (!frustum.isVisible(objectAABB) || !store.render_isInitialized[index]) {
                    continue;
                }

                // Calculate the final interpolated state for this exact moment in the frame.
                interpolator.interpolateFrame(store, index, partialTicks, interpolatedPosition, interpolatedRotation);
                finalRenderState.transform.getTranslation().set(interpolatedPosition);
                finalRenderState.transform.getRotation().set(interpolatedRotation);

                // Dispatch to the appropriate render method based on body type.
                EBodyType objectType = store.objectType[index];
                if (objectType == EBodyType.RigidBody) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, id, index, store);
                } else if (objectType == EBodyType.SoftBody) {
                    finalRenderState.vertexData = interpolator.getInterpolatedVertexData(store, index, partialTicks);
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, id, index, store);
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", id, e);
            }
        }

        poseStack.popPose();
        // End the batch to draw everything.
        bufferSource.endBatch();
    }

    /**
     * Renders a single rigid body.
     */
    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, int index, VxClientObjectDataStore store) {
        VxRigidBody.Renderer renderer = (VxRigidBody.Renderer) store.renderer[index];
        if (renderer == null) return;

        RVec3 renderPosition = finalRenderState.transform.getTranslation();
        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.xx(), renderPosition.yy(), renderPosition.zz()));
        ByteBuffer customData = store.customData[index];

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);
    }

    /**
     * Renders a single soft body.
     */
    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, int index, VxClientObjectDataStore store) {
        VxSoftBody.Renderer renderer = (VxSoftBody.Renderer) store.renderer[index];
        if (renderer == null) return;

        // Can't render if there's no vertex data.
        if (finalRenderState.vertexData == null || finalRenderState.vertexData.length < 3) return;

        // Use the position of the first vertex to sample light levels.
        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));
        ByteBuffer customData = store.customData[index];

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);
    }
}