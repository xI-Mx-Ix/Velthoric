/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * @author xI-Mx-Ix
 */
public record VxSectionPos(int x, int y, int z) {

    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_SIZE_SHIFT = 4;

    public ChunkPos toChunkPos2D() {
        return new ChunkPos(x, z);
    }

    public static VxSectionPos fromBlockPos(BlockPos pos) {
        return new VxSectionPos(
                pos.getX() >> CHUNK_SIZE_SHIFT,
                pos.getY() >> CHUNK_SIZE_SHIFT,
                pos.getZ() >> CHUNK_SIZE_SHIFT
        );
    }

    public static VxSectionPos fromRVec3(RVec3 pos) {
        return new VxSectionPos(
                SectionPos.blockToSectionCoord(pos.x()),
                SectionPos.blockToSectionCoord(pos.y()),
                SectionPos.blockToSectionCoord(pos.z())
        );
    }

    public static VxSectionPos fromWorldSpace(double x, double y, double z) {
        return new VxSectionPos(
                (int) Math.floor(x) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(y) >> CHUNK_SIZE_SHIFT,
                (int) Math.floor(z) >> CHUNK_SIZE_SHIFT
        );
    }

    public BlockPos getOrigin() {
        return new BlockPos(x << CHUNK_SIZE_SHIFT, y << CHUNK_SIZE_SHIFT, z << CHUNK_SIZE_SHIFT);
    }

    public AABB getAABB() {
        BlockPos origin = getOrigin();
        return new AABB(origin, origin.offset(CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE));
    }

    public boolean isWithinWorldHeight(Level level) {
        int minBlockY = y << CHUNK_SIZE_SHIFT;
        int maxBlockY = minBlockY + (CHUNK_SIZE - 1);
        return maxBlockY >= level.getMinBuildHeight() && minBlockY < level.getMaxBuildHeight();
    }
}
