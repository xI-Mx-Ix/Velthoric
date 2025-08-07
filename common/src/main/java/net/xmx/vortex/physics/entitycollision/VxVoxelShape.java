package net.xmx.vortex.physics.entitycollision;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public final class VxVoxelShape extends ArrayVoxelShape {

    private final int targetBodyId;
    private final VxPhysicsWorld physicsWorld;
    private static final AABB EMPTY_AABB = new AABB(0, 0, 0, 0, 0, 0);

    private static final ThreadLocal<CollisionCache> COLLISION_CACHE = ThreadLocal.withInitial(CollisionCache::new);

    public VxVoxelShape(Level level, int targetBodyId) {
        super(createDiscreteShape(), new double[]{0, 1}, new double[]{0, 1}, new double[]{0, 1});
        this.targetBodyId = targetBodyId;
        this.physicsWorld = VxPhysicsWorld.get(level.dimension());
    }

    private static BitSetDiscreteVoxelShape createDiscreteShape() {
        BitSetDiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);
        return shape;
    }

    @Override
    public AABB bounds() {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getBodyLockInterface() == null) {
            return EMPTY_AABB;
        }

        try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), targetBodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                Body body = lock.getBody();
                ConstAaBox joltAabb = body.getWorldSpaceBounds();
                Vec3 min = joltAabb.getMin();
                Vec3 max = joltAabb.getMax();
                return new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("PhysicsVoxelShape: Error getting bounds for body {}: {}", targetBodyId, e.getMessage(), e);
        }
        return EMPTY_AABB;
    }

    @Override
    public double collide(Direction.Axis movementAxis, AABB entityAABB, double desiredOffset) {
        if (Math.abs(desiredOffset) < 1.0E-7) {
            return 0.0;
        }
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return desiredOffset;
        }

        CollisionCache cache = COLLISION_CACHE.get();
        cache.collector.reset();
        cache.bodyFilter.setBodyIdToCollide(this.targetBodyId);

        float xSize = (float) entityAABB.getXsize() / 2f;
        float ySize = (float) entityAABB.getYsize() / 2f;
        float zSize = (float) entityAABB.getZsize() / 2f;

        switch (movementAxis) {
            case X -> cache.motionVec.set((float) desiredOffset, 0f, 0f);
            case Y -> cache.motionVec.set(0f, (float) desiredOffset, 0f);
            case Z -> cache.motionVec.set(0f, 0f, (float) desiredOffset);
        }

        cache.startPosVec.set(entityAABB.getCenter().x, entityAABB.getCenter().y, entityAABB.getCenter().z);
        cache.startTransform.set(RMat44.sRotationTranslation(cache.identityQuat, cache.startPosVec));

        try (BoxShape entityShape = new BoxShape(xSize, ySize, zSize);
             RShapeCast shapeCast = RShapeCast.sFromWorldTransform(entityShape, cache.entityShapeScale, cache.startTransform, cache.motionVec)) {

            NarrowPhaseQuery narrowPhaseQuery = physicsWorld.getPhysicsSystem().getNarrowPhaseQuery();
            narrowPhaseQuery.castShape(shapeCast, cache.shapeCastSettings, cache.startPosVec, cache.collector,
                    cache.bplFilter, cache.olFilter, cache.bodyFilter, cache.shapeFilter);

            if (cache.collector.hadHit()) {
                ShapeCastResult result = cache.collector.getHit();
                double collisionOffset = desiredOffset * result.getFraction();
                double buffer = 1.0E-6;

                return desiredOffset > 0 ?
                        Math.min(desiredOffset, collisionOffset - buffer) :
                        Math.max(desiredOffset, collisionOffset + buffer);
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("PhysicsVoxelShape: Sweep-Test error for body {}: {}", targetBodyId, e.getMessage(), e);
        }
        return desiredOffset;
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        AABB bounds = this.bounds();
        return switch (axis) {
            case X -> DoubleArrayList.of(bounds.minX, bounds.maxX);
            case Y -> DoubleArrayList.of(bounds.minY, bounds.maxY);
            case Z -> DoubleArrayList.of(bounds.minZ, bounds.maxZ);
        };
    }
}