package net.xmx.vortex.physics.collision.entity;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public class PhysicsVoxelShape extends ArrayVoxelShape {

    private final int targetBodyId;
    private final VxPhysicsWorld physicsWorld;
    private static final AABB EMPTY_AABB = new AABB(0, 0, 0, 0, 0, 0);

    public PhysicsVoxelShape(Level level, int targetBodyId) {
        super(createDiscreteShape(), new double[]{0, 1}, new double[]{0, 1}, new double[]{0, 1});
        this.targetBodyId = targetBodyId;
        this.physicsWorld = VxPhysicsWorld.get(level.dimension());
    }

    private static BitSetDiscreteVoxelShape createDiscreteShape() {
        BitSetDiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);
        return shape;
    }

    private AABB calculateCurrentBounds() {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getBodyLockInterface() == null) {
            return EMPTY_AABB;
        }

        TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(physicsWorld.getDimensionKey());
        if (terrainSystem != null && terrainSystem.isTerrainBody(this.targetBodyId)) {
            VxMainClass.LOGGER.warn("[DEBUG] PhysicsVoxelShape for a terrain body ({}) was created! Returning empty bounds.", this.targetBodyId);
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
            VxMainClass.LOGGER.error("JoltVoxelShape: Error getting bounds for body {}: {}", targetBodyId, e.getMessage(), e);
        }
        return EMPTY_AABB;
    }

    @Override
    public AABB bounds() {
        AABB bounds = this.calculateCurrentBounds();
        if (bounds.getSize() > 0) {
            VxMainClass.LOGGER.info("[DEBUG 1B] PhysicsVoxelShape.bounds() called for body {}. Bounds: {}", this.targetBodyId, bounds);
        }
        return bounds;
    }

    @Override
    public double collide(Direction.Axis movementAxis, AABB entityAABB, double desiredOffset) {
        if (Math.abs(desiredOffset) < 1.0E-7) {
            return 0.0;
        }

        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return desiredOffset;
        }

        try {
            NarrowPhaseQuery narrowPhaseQuery = physicsWorld.getPhysicsSystem().getNarrowPhaseQuery();

            float xSize = (float) entityAABB.getXsize() / 2f;
            float ySize = (float) entityAABB.getYsize() / 2f;
            float zSize = (float) entityAABB.getZsize() / 2f;

            try (BoxShape entityShape = new BoxShape(xSize, ySize, zSize);
                 RShapeCast shapeCast = createShapeCast(entityShape, entityAABB.getCenter(), movementAxis, desiredOffset);
                 ClosestHitCastShapeCollector collector = new ClosestHitCastShapeCollector();
                 ShapeCastSettings shapeCastSettings = new ShapeCastSettings();
                 BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
                 ObjectLayerFilter olFilter = new ObjectLayerFilter();
                 ShapeFilter shapeFilter = new ShapeFilter();
                 BodyFilter singleBodyFilter = new BodyFilter() {
                     @Override
                     public boolean shouldCollide(int bodyId) {
                         return bodyId == targetBodyId;
                     }
                 }) {

                RVec3Arg startPos = new Vec3(entityAABB.getCenter().x, entityAABB.getCenter().y, entityAABB.getCenter().z).toRVec3();

                narrowPhaseQuery.castShape(shapeCast, shapeCastSettings, startPos, collector,
                        bplFilter, olFilter, singleBodyFilter, shapeFilter);

                if (collector.hadHit()) {
                    ShapeCastResult result = collector.getHit();
                    double fraction = result.getFraction();
                    double collisionOffset = desiredOffset * fraction;
                    double buffer = 1.0E-6;

                    if (desiredOffset > 0) {
                        return Math.min(desiredOffset, collisionOffset - buffer);
                    } else if (desiredOffset < 0) {
                        return Math.max(desiredOffset, collisionOffset + buffer);
                    }
                }
            }
            return desiredOffset;

        } catch (Exception e) {
            VxMainClass.LOGGER.error("JoltVoxelShape: Sweep-Test error for body {}: {}", targetBodyId, e.getMessage(), e);
            return desiredOffset;
        }
    }

    private RShapeCast createShapeCast(BoxShape entityShape, net.minecraft.world.phys.Vec3 center, Direction.Axis movementAxis, double desiredOffset) {
        RVec3Arg startPosVec = new Vec3(center.x, center.y, center.z).toRVec3();
        RMat44 startTransform = RMat44.sRotationTranslation(Quat.sIdentity(), startPosVec);

        Vec3 motion = new Vec3();
        switch (movementAxis) {
            case X -> motion.setX((float) desiredOffset);
            case Y -> motion.setY((float) desiredOffset);
            case Z -> motion.setZ((float) desiredOffset);
        }

        return RShapeCast.sFromWorldTransform(entityShape, new Vec3(1, 1, 1), startTransform, motion);
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