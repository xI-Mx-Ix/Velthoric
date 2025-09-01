package net.xmx.velthoric.mixin.impl.ship.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.mixin.impl.ship.accessor.ViewAreaAccessor;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.client.interpolation.InterpolationFrame;
import net.xmx.velthoric.physics.object.client.interpolation.RenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Nullable private ViewArea viewArea;

    @Unique
    private final Map<UUID, ObjectArrayList<ChunkRenderDispatcher.RenderChunk>> velthoric$shipRenderChunks = new ConcurrentHashMap<>();
    @Unique
    private final Map<UUID, BoundingBox> velthoric$plotBoxCache = new ConcurrentHashMap<>();

    @Inject(method = "setupRender", at = @At("RETURN"))
    private void velthoric_gatherShipChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean isSpectator, CallbackInfo ci) {
        velthoric$shipRenderChunks.clear();
        if (viewArea == null) return;

        ClientObjectDataManager manager = ClientObjectDataManager.getInstance();
        ViewAreaAccessor viewAreaAccessor = (ViewAreaAccessor) viewArea;

        for (UUID id : manager.getAllObjectIds()) {
            BoundingBox plotBox = velthoric$getPlotBox(id);
            if (plotBox == null) continue;

            InterpolationFrame frame = manager.getInterpolationFrame(id);
            if (frame == null || !frame.isInitialized) continue;

            ObjectArrayList<ChunkRenderDispatcher.RenderChunk> chunks = velthoric$shipRenderChunks.computeIfAbsent(id, k -> new ObjectArrayList<>());
            RenderState renderState = frame.getInterpolatedState(minecraft.getFrameTime());

            AABB shipRenderAABB = new AABB(plotBox.minX(), plotBox.minY(), plotBox.minZ(), plotBox.maxX() + 1, plotBox.maxY() + 1, plotBox.maxZ() + 1)
                    .move(-plotBox.getCenter().getX() - 0.5, -plotBox.getCenter().getY() - 0.5, -plotBox.getCenter().getZ() - 0.5)
                    .move(renderState.transform.getTranslation().x(), renderState.transform.getTranslation().y(), renderState.transform.getTranslation().z());

            if (!frustum.isVisible(shipRenderAABB)) {
                continue;
            }

            for (int chunkX = plotBox.minX() >> 4; chunkX <= plotBox.maxX() >> 4; chunkX++) {
                for (int chunkZ = plotBox.minZ() >> 4; chunkZ <= plotBox.maxZ() >> 4; chunkZ++) {
                    for (int sectionY = minecraft.level.getMinSection(); sectionY < minecraft.level.getMaxSection(); sectionY++) {
                        BlockPos chunkOrigin = new BlockPos(chunkX * 16, sectionY * 16, chunkZ * 16);
                        ChunkRenderDispatcher.RenderChunk renderChunk = viewAreaAccessor.callGetRenderChunkAt(chunkOrigin);

                        if (renderChunk != null && !renderChunk.getCompiledChunk().hasNoRenderableLayers()) {
                            chunks.add(renderChunk);
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderWorldBorder(Lnet/minecraft/client/Camera;)V", shift = At.Shift.AFTER))
    private void velthoric_renderShips(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        ClientObjectDataManager manager = ClientObjectDataManager.getInstance();

        for (Map.Entry<UUID, ObjectArrayList<ChunkRenderDispatcher.RenderChunk>> entry : velthoric$shipRenderChunks.entrySet()) {
            UUID id = entry.getKey();
            ObjectArrayList<ChunkRenderDispatcher.RenderChunk> chunksToRender = entry.getValue();
            if (chunksToRender.isEmpty()) continue;

            InterpolationFrame frame = manager.getInterpolationFrame(id);
            if (frame == null || !frame.isInitialized) continue;

            BoundingBox plotBox = velthoric$getPlotBox(id);
            if (plotBox == null) continue;

            RenderState renderState = frame.getInterpolatedState(partialTick);

            poseStack.pushPose();

            BlockPos plotCenter = plotBox.getCenter();
            double pivotX = plotCenter.getX() + 0.5;
            double pivotY = plotCenter.getY() + 0.5;
            double pivotZ = plotCenter.getZ() + 0.5;

            poseStack.translate(
                    renderState.transform.getTranslation().x() - camera.getPosition().x,
                    renderState.transform.getTranslation().y() - camera.getPosition().y,
                    renderState.transform.getTranslation().z() - camera.getPosition().z
            );
            poseStack.mulPose(new Quaternionf(
                    renderState.transform.getRotation().getX(),
                    renderState.transform.getRotation().getY(),
                    renderState.transform.getRotation().getZ(),
                    renderState.transform.getRotation().getW()
            ));
            poseStack.translate(-pivotX, -pivotY, -pivotZ);

            for (RenderType renderType : RenderType.chunkBufferLayers()) {
                velthoric_renderShipChunkLayer(renderType, poseStack, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, projectionMatrix, chunksToRender);
            }

            poseStack.popPose();
        }
    }

    @Unique
    private void velthoric_renderShipChunkLayer(RenderType renderType, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, ObjectList<ChunkRenderDispatcher.RenderChunk> chunksToRender) {
        renderType.setupRenderState();

        if (renderType == RenderType.translucent()) {
            chunksToRender.sort(Comparator.comparingDouble(chunk -> {
                BlockPos origin = ((ChunkRenderDispatcher.RenderChunk) chunk).getOrigin();
                return new BlockPos((int)camX, (int)camY, (int)camZ).distSqr(origin);
            }).reversed());
        }

        ShaderInstance shader = RenderSystem.getShader();
        shader.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
        shader.PROJECTION_MATRIX.set(projectionMatrix);
        RenderSystem.setupShaderLights(shader);
        shader.apply();

        for (ChunkRenderDispatcher.RenderChunk renderChunk : chunksToRender) {
            if (!renderChunk.getCompiledChunk().isEmpty(renderType)) {
                VertexBuffer buffer = renderChunk.getBuffer(renderType);
                BlockPos origin = renderChunk.getOrigin();
                shader.CHUNK_OFFSET.set((float)origin.getX(), (float)origin.getY(), (float)origin.getZ());
                shader.CHUNK_OFFSET.upload();
                buffer.bind();
                buffer.draw();
            }
        }

        shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    @Unique
    private BoundingBox velthoric$getPlotBox(UUID id) {
        return velthoric$plotBoxCache.computeIfAbsent(id, key -> {
            ClientObjectDataManager manager = ClientObjectDataManager.getInstance();
            if (!manager.hasObject(key)) {
                velthoric$plotBoxCache.remove(key);
                return null;
            }

            ByteBuffer data = manager.getCustomData(key);
            if (data == null) return null;

            data.rewind();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            if (buf.readableBytes() > 0 && buf.readBoolean()) {
                if (buf.readableBytes() >= 24) {
                    return new BoundingBox(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
                }
            }
            return null;
        });
    }
}