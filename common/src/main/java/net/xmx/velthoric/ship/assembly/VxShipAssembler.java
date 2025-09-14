/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.plot.VxPlotManager;

import java.util.UUID;

public class VxShipAssembler {

    public static boolean assembleShip(ServerLevel level, BlockPos pos1, BlockPos pos2) {
        VxMainClass.LOGGER.info("[ASSEMBLER] Starting ship assembly...");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            VxMainClass.LOGGER.error("[ASSEMBLER] Abort: Physics world not found for dimension {}.", level.dimension().location());
            return false;
        }
        VxMainClass.LOGGER.info("[ASSEMBLER] Physics world found.");

        VxPlotManager plotManager = physicsWorld.getPlotManager();
        if (plotManager == null) {
            VxMainClass.LOGGER.error("[ASSEMBLER] Abort: PlotManager not found for the physics world.");
            return false;
        }
        VxMainClass.LOGGER.info("[ASSEMBLER] PlotManager found.");

        AABB assemblyBox = new AABB(pos1, pos2);
        BlockPos minCorner = new BlockPos((int)assemblyBox.minX, (int)assemblyBox.minY, (int)assemblyBox.minZ);
        BlockPos maxCorner = new BlockPos((int)assemblyBox.maxX, (int)assemblyBox.maxY, (int)assemblyBox.maxZ);
        Vec3 center = assemblyBox.getCenter();
        VxMainClass.LOGGER.info("[ASSEMBLER] Assembly Box from {} to {}. Center: {}", minCorner, maxCorner, center);

        int sizeX = maxCorner.getX() - minCorner.getX();
        int sizeZ = maxCorner.getZ() - minCorner.getZ();
        int maxDimBlocks = Math.max(sizeX, sizeZ);

        final int plotRadius = (int) Math.ceil((double) maxDimBlocks / 2.0 / 16.0) + 1;
        VxMainClass.LOGGER.info("[ASSEMBLER] Calculated plot radius: {}", plotRadius);

        final UUID plotId = plotManager.createNewPlot(plotRadius);
        final ChunkPos plotCenter = plotManager.getPlotCenter(plotId);
        final BlockPos plotOrigin = plotCenter.getWorldPosition();
        VxMainClass.LOGGER.info("[ASSEMBLER] Allocated plot {} for new ship at chunk {} (World: {})", plotId, plotCenter, plotOrigin);

        int blockCount = 0;
        for (BlockPos currentWorldPos : BlockPos.betweenClosed(minCorner, maxCorner)) {
            BlockState state = level.getBlockState(currentWorldPos);
            if (state.isAir()) {
                continue;
            }
            blockCount++;
            int plotOffsetX = currentWorldPos.getX() - minCorner.getX();
            int plotOffsetY = currentWorldPos.getY() - minCorner.getY();
            int plotOffsetZ = currentWorldPos.getZ() - minCorner.getZ();
            BlockPos plotTargetPos = plotOrigin.offset(plotOffsetX, plotOffsetY, plotOffsetZ);

            moveBlockWithEntity(level, currentWorldPos.immutable(), plotTargetPos);
        }
        VxMainClass.LOGGER.info("[ASSEMBLER] Finished moving {} blocks to plot area for plot {}.", blockCount, plotId);

        VxTransform initialTransform = new VxTransform();
        initialTransform.getTranslation().set(center.x, center.y, center.z);
        VxMainClass.LOGGER.info("[ASSEMBLER] Creating rigid body with initial transform at {}", center);

        physicsWorld.getObjectManager().createRigidBody(
                VxRegisteredObjects.SHIP,
                initialTransform,
                ship -> {
                    ship.setPlotId(plotId);
                    VxMainClass.LOGGER.info("[ASSEMBLER] Successfully created and configured rigid body for ship {} linked to plot {}", ship.getPhysicsId(), plotId);
                }
        );

        VxMainClass.LOGGER.info("[ASSEMBLER] Ship assembly initiated from {} to {}. Plot assigned at {}.", minCorner, maxCorner, plotCenter);
        return true;
    }

    private static void moveBlockWithEntity(ServerLevel level, BlockPos from, BlockPos to) {
        BlockState state = level.getBlockState(from);
        BlockEntity blockEntity = level.getBlockEntity(from);
        CompoundTag beTag = null;
        if (blockEntity != null) {
            beTag = blockEntity.saveWithFullMetadata();
            level.removeBlockEntity(from);
        }

        level.setBlock(from, Blocks.AIR.defaultBlockState(), 3 | 16);
        level.setBlock(to, state, 3);

        if (beTag != null) {
            BlockEntity newBlockEntity = level.getBlockEntity(to);
            if (newBlockEntity != null) {
                newBlockEntity.load(beTag);
            } else {
                VxMainClass.LOGGER.error("[ASSEMBLER] Failed to restore BlockEntity at target position {} after moving from {}", to, from);
            }
        }
    }
}