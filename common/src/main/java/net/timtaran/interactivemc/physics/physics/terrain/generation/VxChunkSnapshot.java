/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.terrain.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.timtaran.interactivemc.physics.physics.terrain.VxSectionPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of the collidable blocks within a single chunk section.
 * This is used to pass chunk data to worker threads for shape generation in a thread-safe manner.
 *
 * @param shapes A list of all blocks with collision shapes in the section.
 * @param pos The position of the chunk section this snapshot represents.
 *
 * @author xI-Mx-Ix
 */
public record VxChunkSnapshot(List<ShapeInfo> shapes, VxSectionPos pos) {

    /**
     * Contains information about a single block with a collision shape.
     * @param state The block state.
     * @param localPos The position of the block relative to the section's origin (0-15).
     */
    public record ShapeInfo(BlockState state, BlockPos localPos) {}

    /**
     * Creates a ChunkSnapshot from a live LevelChunk.
     *
     * @param level The level the chunk belongs to.
     * @param chunk The chunk to snapshot.
     * @param pos The specific section of the chunk to snapshot.
     * @return A new ChunkSnapshot instance.
     */
    public static VxChunkSnapshot snapshotFromChunk(Level level, LevelChunk chunk, VxSectionPos pos) {
        int sectionIndex = level.getSectionIndexFromSectionY(pos.y());

        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            return new VxChunkSnapshot(Collections.emptyList(), pos);
        }

        LevelChunkSection section = chunk.getSections()[sectionIndex];
        if (section == null || section.hasOnlyAir()) {
            return new VxChunkSnapshot(Collections.emptyList(), pos);
        }

        List<ShapeInfo> shapeInfos = new ArrayList<>();
        BlockPos origin = pos.getOrigin();

        for (int x = 0; x < 16; ++x) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    BlockPos currentLocalPos = new BlockPos(x, y, z);
                    BlockPos currentWorldPos = origin.offset(currentLocalPos);
                    BlockState blockState = section.getBlockState(x, y, z);

                    if (!blockState.isAir() && !blockState.getCollisionShape(level, currentWorldPos).isEmpty()) {
                        shapeInfos.add(new ShapeInfo(blockState, currentLocalPos));
                    }
                }
            }
        }
        return new VxChunkSnapshot(shapeInfos, pos);
    }
}