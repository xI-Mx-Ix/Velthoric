package net.xmx.xbullet.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.collision.entity.PhysicsVoxelShape;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionsMixin {

    @Inject(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;addAll(Ljava/lang/Iterable;)Lcom/google/common/collect/ImmutableList$Builder;", ordinal = 1),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void injectJoltPhysicsCollisions(
            @Nullable Entity pEntity,
            net.minecraft.world.phys.Vec3 pVec,
            AABB pCollisionBox,
            Level pLevel,
            List<VoxelShape> pPotentialHits,
            CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir,
            ImmutableList.Builder<VoxelShape> builder,
            WorldBorder worldborder,
            boolean flag) {

        jolt$addPhysicsCollisions(pLevel, pCollisionBox, pVec, builder);
    }

    @Unique
    private static void jolt$addPhysicsCollisions(
            Level level,
            AABB collisionBox,
            net.minecraft.world.phys.Vec3 movement,
            ImmutableList.Builder<VoxelShape> builder) {

        PhysicsWorld physicsWorld = PhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return;
        }

        TerrainSystem terrainSystem = PhysicsWorld.getTerrainSystem(level.dimension());
        if (terrainSystem == null) {
            return;
        }

        AABB expandedBox = collisionBox.expandTowards(movement);
        Vec3 minVec = new Vec3((float) expandedBox.minX, (float) expandedBox.minY, (float) expandedBox.minZ);
        Vec3 maxVec = new Vec3((float) expandedBox.maxX, (float) expandedBox.maxY, (float) expandedBox.maxZ);

        try (AaBox joltAabb = new AaBox(minVec, maxVec);
             AllHitCollideShapeBodyCollector broadPhaseCollector = new AllHitCollideShapeBodyCollector();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             ObjectLayerFilter olFilter = new ObjectLayerFilter()) {

            BroadPhaseQuery bpQuery = physicsWorld.getPhysicsSystem().getBroadPhaseQuery();
            bpQuery.collideAaBox(joltAabb, broadPhaseCollector, bplFilter, olFilter);

            if (broadPhaseCollector.countHits() == 0) {
                return;
            }

            List<Integer> candidateBodyIds = new ArrayList<>();
            for (int bodyId : broadPhaseCollector.getHits()) {
                if (!terrainSystem.isTerrainBody(bodyId)) {
                    candidateBodyIds.add(bodyId);
                }
            }

            if (candidateBodyIds.isEmpty()) {
                return;
            }

            NarrowPhaseQuery narrowPhaseQuery = physicsWorld.getPhysicsSystem().getNarrowPhaseQuery();

            float xSize = (float) collisionBox.getXsize() / 2f;
            float ySize = (float) collisionBox.getYsize() / 2f;
            float zSize = (float) collisionBox.getZsize() / 2f;
            net.minecraft.world.phys.Vec3 center = collisionBox.getCenter();

            try (BoxShape entityShape = new BoxShape(xSize, ySize, zSize);
                 ClosestHitCastShapeCollector narrowPhaseCollector = new ClosestHitCastShapeCollector();
                 ShapeCastSettings shapeCastSettings = new ShapeCastSettings();
                 ShapeFilter shapeFilter = new ShapeFilter();
                 BodyFilter candidateFilter = new BodyFilter() {
                     @Override
                     public boolean shouldCollide(int bodyId) {
                         return candidateBodyIds.contains(bodyId);
                     }
                 }) {

                RVec3Arg startPos = new Vec3(center.x, center.y, center.z).toRVec3();
                RMat44 startTransform = RMat44.sRotationTranslation(Quat.sIdentity(), startPos);
                Vec3 motion = new Vec3((float) movement.x, (float) movement.y, (float) movement.z);
                RShapeCast shapeCast = RShapeCast.sFromWorldTransform(entityShape, new Vec3(1, 1, 1), startTransform, motion);

                narrowPhaseQuery.castShape(shapeCast, shapeCastSettings, startPos, narrowPhaseCollector,
                        bplFilter, olFilter, candidateFilter, shapeFilter);

                if (narrowPhaseCollector.hadHit()) {
                    int closestHitBodyId = narrowPhaseCollector.getHit().getBodyId2();
                    XBullet.LOGGER.info("[DEBUG] EntityCollisionsMixin: Closest Jolt body is {}. Adding to potential colliders.", closestHitBodyId);
                    builder.add(new PhysicsVoxelShape(level, closestHitBodyId));
                }
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Error during Jolt broad-phase/narrow-phase query for entity collisions", e);
        }
    }
}