package net.xmx.xbullet.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.github.stephengold.joltjni.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
            ImmutableList.Builder<VoxelShape> builder) {

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

            for (int bodyId : broadPhaseCollector.getHits()) {

                if (!terrainSystem.isTerrainBody(bodyId)) {
                    builder.add(new PhysicsVoxelShape(level, bodyId));
                }
            }

        } catch (Exception e) {
            XBullet.LOGGER.error("Error during Jolt broad-phase query for entity collisions", e);
        }
    }
}