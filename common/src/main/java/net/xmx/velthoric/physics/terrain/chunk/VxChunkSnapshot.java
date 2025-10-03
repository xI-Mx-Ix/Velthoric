/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of the block data within a single chunk section (16x16x16 blocks).
 * It contains only the information relevant for physics shape generation, such as block states
 * and their local positions.
 *
 * @param shapes A list of shapes within this chunk section.
 * @param pos    The position of this chunk section.
 *
 * @author xI-Mx-Ix
 */
public record VxChunkSnapshot(List<ShapeInfo> shapes, VxSectionPos pos) {

    /**
     * Holds information about a single block's shape.
     *
     * @param state    The block state.
     * @param localPos The position of the block relative to the chunk section's origin (0-15).
     */
    public record ShapeInfo(BlockState state, BlockPos localPos) {}

    /**
     * Creates a snapshot from a live {@link LevelChunk}.
     *
     * @param level The level the chunk belongs to.
     * @param chunk The chunk to snapshot.
     * @param pos   The specific section position within the chunk.
     * @return A new {@link VxChunkSnapshot} instance.
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
                    BlockState blockState = section.getBlockState(x, y, z);

                    // Include only blocks that have a collision shape
                    if (!blockState.isAir() && !blockState.getCollisionShape(level, origin.offset(x, y, z)).isEmpty()) {
                        shapeInfos.add(new ShapeInfo(blockState, new BlockPos(x, y, z)));
                    }
                }
            }
        }
        return new VxChunkSnapshot(shapeInfos, pos);
    }
}