package net.xmx.vortex.physics.terrain.greedy;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.vortex.physics.terrain.model.VxGreedyBox;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GreedyShapeManager {

    public static final int VOXEL_RESOLUTION_PER_BLOCK = 16;
    public static final int VOXELS_PER_CHUNK_AXIS = 16 * VOXEL_RESOLUTION_PER_BLOCK;
    public static final float VOXEL_SIZE = 1.0f / VOXEL_RESOLUTION_PER_BLOCK;

    private final boolean[][][] voxels;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GreedyShapeManager() {
        this.voxels = new boolean[VOXELS_PER_CHUNK_AXIS][VOXELS_PER_CHUNK_AXIS][VOXELS_PER_CHUNK_AXIS];
    }

    public void addVoxelShape(VoxelShape shape, BlockPos localBlockPos) {
        if (shape.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            updateVoxelShape(shape, localBlockPos, true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeVoxelShape(VoxelShape shape, BlockPos localBlockPos) {
        if (shape.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            updateVoxelShape(shape, localBlockPos, false);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceVoxelShape(VoxelShape oldShape, VoxelShape newShape, BlockPos localBlockPos) {
        lock.writeLock().lock();
        try {
            if (!oldShape.isEmpty()) {
                updateVoxelShape(oldShape, localBlockPos, false);
            }
            if (!newShape.isEmpty()) {
                updateVoxelShape(newShape, localBlockPos, true);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void updateVoxelShape(VoxelShape shape, BlockPos localBlockPos, boolean value) {
        for (AABB aabb : shape.toAabbs()) {
            int minX = (int) ((localBlockPos.getX() + aabb.minX) * VOXEL_RESOLUTION_PER_BLOCK);
            int minY = (int) ((localBlockPos.getY() + aabb.minY) * VOXEL_RESOLUTION_PER_BLOCK);
            int minZ = (int) ((localBlockPos.getZ() + aabb.minZ) * VOXEL_RESOLUTION_PER_BLOCK);
            int maxX = (int) Math.ceil((localBlockPos.getX() + aabb.maxX) * VOXEL_RESOLUTION_PER_BLOCK);
            int maxY = (int) Math.ceil((localBlockPos.getY() + aabb.maxY) * VOXEL_RESOLUTION_PER_BLOCK);
            int maxZ = (int) Math.ceil((localBlockPos.getZ() + aabb.maxZ) * VOXEL_RESOLUTION_PER_BLOCK);

            minX = Math.max(0, minX);
            minY = Math.max(0, minY);
            minZ = Math.max(0, minZ);
            maxX = Math.min(VOXELS_PER_CHUNK_AXIS, maxX);
            maxY = Math.min(VOXELS_PER_CHUNK_AXIS, maxY);
            maxZ = Math.min(VOXELS_PER_CHUNK_AXIS, maxZ);

            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        voxels[x][y][z] = value;
                    }
                }
            }
        }
    }

    public ShapeSettings buildShape() {
        lock.readLock().lock();
        try {
            List<VxGreedyBox> greedyBoxes = GreedyMesher.mesh(voxels, VOXELS_PER_CHUNK_AXIS, VOXELS_PER_CHUNK_AXIS, VOXELS_PER_CHUNK_AXIS);

            if (greedyBoxes.isEmpty()) {
                return null;
            }

            final float minHalfExtent = Jolt.cDefaultConvexRadius;

            StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
            for (VxGreedyBox box : greedyBoxes) {
                float halfX = box.width() * VOXEL_SIZE / 2.0f;
                float halfY = box.height() * VOXEL_SIZE / 2.0f;
                float halfZ = box.depth() * VOXEL_SIZE / 2.0f;

                float centerX = (box.minX() + box.width() / 2.0f) * VOXEL_SIZE;
                float centerY = (box.minY() + box.height() / 2.0f) * VOXEL_SIZE;
                float centerZ = (box.minZ() + box.depth() / 2.0f) * VOXEL_SIZE;

                halfX = Math.max(halfX, minHalfExtent);
                halfY = Math.max(halfY, minHalfExtent);
                halfZ = Math.max(halfZ, minHalfExtent);

                try (BoxShapeSettings boxSettings = new BoxShapeSettings(halfX, halfY, halfZ)) {
                    boxSettings.setConvexRadius(0.0f);
                    settings.addShape(centerX, centerY, centerZ, boxSettings);
                }
            }
            return settings;
        } finally {
            lock.readLock().unlock();
        }
    }
}