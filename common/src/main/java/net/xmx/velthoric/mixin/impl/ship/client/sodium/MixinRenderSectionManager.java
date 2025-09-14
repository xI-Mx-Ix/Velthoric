/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.client.sodium;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.xmx.velthoric.mixin.util.ship.render.sodium.IRenderSectionManager;
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

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager implements IRenderSectionManager {

    @Unique
    private final Map<UUID, SortedRenderLists> velthoric$shipRenderLists = new ConcurrentHashMap<>();
    @Unique
    private final RVec3 velthoric$interpolatedPosition = new RVec3();
    @Unique
    private final Quat velthoric$interpolatedRotation = new Quat();

    @Shadow @Final private ClientLevel world;
    @Shadow protected abstract RenderSection getRenderSection(int x, int y, int z);
    @Shadow private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;
    @Shadow @Final private ChunkRenderer chunkRenderer;

    @Inject(method = "createTerrainRenderList", at = @At("TAIL"))
    private void velthoric$collectShipChunks(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        float partialTick = Minecraft.getInstance().getFrameTime();
        this.velthoric$shipRenderLists.clear();

        for (UUID shipId : plotManager.getAllShipIds()) {
            ShipPlotInfo plotInfo = plotManager.getShipInfoForShip(shipId);
            if (plotInfo == null) continue;

            var index = objectManager.getStore().getIndexForId(shipId);
            if (index == null) continue;

            objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, velthoric$interpolatedPosition, velthoric$interpolatedRotation);

            int radius = plotManager.getPlotRadius(shipId);
            double halfWidth = (radius + 0.5) * 16.0;
            double halfHeight = (double) (world.getMaxSection() - world.getMinSection()) * 16.0 / 2.0;

            int centerX = (int) velthoric$interpolatedPosition.x();
            int centerY = (int) velthoric$interpolatedPosition.y();
            int centerZ = (int) velthoric$interpolatedPosition.z();
            float sizeX = (float) halfWidth;
            float sizeY = (float) halfHeight;
            float sizeZ = (float) halfWidth;

            if (!viewport.isBoxVisible(centerX, centerY, centerZ, sizeX, sizeY, sizeZ)) {
                continue;
            }

            VisibleChunkCollector collector = new VisibleChunkCollector(frame);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = this.world.getMinSection(); y < this.world.getMaxSection(); y++) {
                        RenderSection section = getRenderSection(plotInfo.plotCenter().x + x, y, plotInfo.plotCenter().z + z);
                        if (section != null) {
                            collector.visit(section, true);
                        }
                    }
                }
            }
            this.velthoric$shipRenderLists.put(shipId, collector.createRenderLists());

            for (final var entry : collector.getRebuildLists().entrySet()) {
                this.rebuildLists.get(entry.getKey()).addAll(entry.getValue());
            }
        }
    }

    @Inject(method = "resetRenderLists", at = @At("TAIL"))
    private void velthoric$resetShipLists(CallbackInfo ci) {
        this.velthoric$shipRenderLists.clear();
    }

    @Override
    public Map<UUID, SortedRenderLists> velthoric$getShipRenderLists() {
        return this.velthoric$shipRenderLists;
    }

    @Inject(method = "renderLayer", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;flush()V"))
    private void velthoric$renderShipsInLayer(
            ChunkRenderMatrices matrices, TerrainRenderPass pass,
            double camX, double camY, double camZ, CallbackInfo ci,
            @Local CommandList commandList
    ) {
        if (this.velthoric$shipRenderLists.isEmpty()) {
            return;
        }

        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
        float partialTick = Minecraft.getInstance().getFrameTime();

        for (Map.Entry<UUID, SortedRenderLists> entry : this.velthoric$shipRenderLists.entrySet()) {
            UUID shipId = entry.getKey();
            SortedRenderLists shipLists = entry.getValue();
            if (!shipLists.iterator(false).hasNext()) continue;

            var index = objectManager.getStore().getIndexForId(shipId);
            if (index == null) continue;

            objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, velthoric$interpolatedPosition, velthoric$interpolatedRotation);
            ShipPlotInfo plotInfo = plotManager.getShipInfoForShip(shipId);
            if (plotInfo == null) continue;

            BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();

            Matrix4f finalModelViewMatrix = new Matrix4f(matrices.modelView());

            finalModelViewMatrix.m30(0);
            finalModelViewMatrix.m31(0);
            finalModelViewMatrix.m32(0);

            finalModelViewMatrix.translate(
                    (float) (velthoric$interpolatedPosition.x() - camX),
                    (float) (velthoric$interpolatedPosition.y() - camY),
                    (float) (velthoric$interpolatedPosition.z() - camZ)
            );

            finalModelViewMatrix.rotate(new Quaternionf(
                    velthoric$interpolatedRotation.getX(), velthoric$interpolatedRotation.getY(),
                    velthoric$interpolatedRotation.getZ(), velthoric$interpolatedRotation.getW()
            ));

            finalModelViewMatrix.translate(
                    (float) -plotOrigin.getX(),
                    (float) -plotOrigin.getY(),
                    (float) -plotOrigin.getZ()
            );

            ChunkRenderMatrices shipMatrices = new ChunkRenderMatrices(matrices.projection(), finalModelViewMatrix);

            CameraTransform shipCamera = new CameraTransform(0, 0, 0);

            this.chunkRenderer.render(shipMatrices, commandList, shipLists, pass, shipCamera);
        }
    }
}