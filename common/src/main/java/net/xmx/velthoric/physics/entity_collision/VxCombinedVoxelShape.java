package net.xmx.velthoric.physics.entity_collision;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VxCombinedVoxelShape extends ArrayVoxelShape {

    private final List<UUID> bodyUuids;
    private final VxPhysicsWorld physicsWorld;
    private final Level level;
    private final @Nullable Entity entity;
    private static final AABB EMPTY_AABB = new AABB(0, 0, 0, 0, 0, 0);

    public VxCombinedVoxelShape(Level level, List<UUID> bodyUuids, @Nullable Entity entity) {
        super(createDiscreteShape(), new double[]{0, 1}, new double[]{0, 1}, new double[]{0, 1});
        this.bodyUuids = bodyUuids;
        this.physicsWorld = VxPhysicsWorld.get(level.dimension());
        this.level = level;
        this.entity = entity;
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
        AABB combinedBounds = null;
        for (UUID bodyUuid : bodyUuids) {
            Optional<VxAbstractBody> bodyOpt = physicsWorld.getObjectManager().getObject(bodyUuid);
            if (bodyOpt.isEmpty()) continue;
            int bodyId = bodyOpt.get().getBodyId();
            if (bodyId == 0) continue;

            try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    ConstAaBox joltAabb = lock.getBody().getWorldSpaceBounds();
                    Vec3 min = joltAabb.getMin();
                    Vec3 max = joltAabb.getMax();
                    AABB bodyBounds = new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
                    if (combinedBounds == null) {
                        combinedBounds = bodyBounds;
                    } else {
                        combinedBounds = combinedBounds.minmax(bodyBounds);
                    }
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error getting bounds for body {}: {}", bodyId, e.getMessage());
            }
        }
        return combinedBounds != null ? combinedBounds : EMPTY_AABB;
    }

    @Override
    public double collide(Direction.Axis movementAxis, AABB entityAABB, double desiredOffset) {
        if (Math.abs(desiredOffset) < 1.0E-7) {
            return 0.0;
        }
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return desiredOffset;
        }
        double finalOffset = desiredOffset;
        Integer hitBodyId = null;

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
                 BodyFilter multiBodyFilter = new BodyFilter() {
                     @Override
                     public boolean shouldCollide(int bodyId) {
                         return physicsWorld.getObjectManager().getObjectContainer().getByBodyId(bodyId)
                                 .map(VxAbstractBody::getPhysicsId)
                                 .map(bodyUuids::contains)
                                 .orElse(false);
                     }
                 }) {
                RVec3Arg startPos = new Vec3(entityAABB.getCenter().x, entityAABB.getCenter().y, entityAABB.getCenter().z).toRVec3();
                narrowPhaseQuery.castShape(shapeCast, shapeCastSettings, startPos, collector, bplFilter, olFilter, multiBodyFilter, shapeFilter);

                if (collector.hadHit()) {
                    ShapeCastResult result = collector.getHit();
                    double fraction = result.getFraction();
                    double collisionOffset = desiredOffset * fraction;
                    double buffer = 1.0E-6;
                    hitBodyId = result.getBodyId2();
                    if (desiredOffset > 0) {
                        finalOffset = Math.min(desiredOffset, collisionOffset - buffer);
                    } else if (desiredOffset < 0) {
                        finalOffset = Math.max(desiredOffset, collisionOffset + buffer);
                    }
                }
            }

            if (hitBodyId != null && entity != null && movementAxis == Direction.Axis.Y && Math.abs(finalOffset) < Math.abs(desiredOffset)) {
                final int finalHitBodyId = hitBodyId;
                physicsWorld.getObjectManager().getObjectContainer().getByBodyId(finalHitBodyId)
                        .map(VxAbstractBody::getPhysicsId)
                        .ifPresent(uuid -> updateAttachmentState(uuid, entity));
            }
            return finalOffset;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("CombinedVoxelShape collision error: {}", e.getMessage());
            return desiredOffset;
        }
    }

    private void updateAttachmentState(UUID bodyUuid, Entity entity) {
        IEntityAttachmentData attachmentProvider = (IEntityAttachmentData) entity;
        EntityAttachmentData data = attachmentProvider.getAttachmentData();
        data.ticksSinceGrounded = 0;

        if (!bodyUuid.equals(data.attachedBodyUuid)) {
            data.attachedBodyUuid = bodyUuid;
            Optional<VxAbstractBody> bodyOpt = physicsWorld.getObjectManager().getObject(bodyUuid);
            if(bodyOpt.isEmpty()) return;
            int bodyId = bodyOpt.get().getBodyId();
            if(bodyId == 0) return;

            try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    data.lastBodyTransform = lock.getBody().getWorldTransform();
                }
            }
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