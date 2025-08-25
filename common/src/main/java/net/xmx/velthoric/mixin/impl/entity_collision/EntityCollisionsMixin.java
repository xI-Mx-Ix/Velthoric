package net.xmx.velthoric.mixin.impl.entity_collision;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.AllHitCollideShapeBodyCollector;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.BroadPhaseQuery;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.entity_collision.VxCombinedVoxelShape;
import net.xmx.velthoric.physics.terrain.TerrainSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionsMixin {

    @Inject(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void injectJoltPhysicsCollisions(
            @Nullable Entity entity,
            Vec3 vec,
            AABB collisionBox,
            Level level,
            List<VoxelShape> potentialHits,
            CallbackInfoReturnable<Vec3> cir,
            @Local ImmutableList.Builder<VoxelShape> builder
    ) {

        IntList physicsBodyIds = jolt$getPhysicsBodyIdsInArea(level, collisionBox, vec);
        if (!physicsBodyIds.isEmpty()) {

            VxCombinedVoxelShape combinedShape = new VxCombinedVoxelShape(level, physicsBodyIds, entity);
            builder.add(combinedShape);
        }
    }

    @Unique
    private static IntList jolt$getPhysicsBodyIdsInArea(Level level, AABB collisionBox, Vec3 movement) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return new IntArrayList();
        }

        TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(level.dimension());
        if (terrainSystem == null) {
            return new IntArrayList();
        }

        AABB expandedBox = collisionBox.expandTowards(movement);
        IntList physicsBodyIds = new IntArrayList();

        com.github.stephengold.joltjni.Vec3 minJoltVec = new com.github.stephengold.joltjni.Vec3((float) expandedBox.minX, (float) expandedBox.minY, (float) expandedBox.minZ);
        com.github.stephengold.joltjni.Vec3 maxJoltVec = new com.github.stephengold.joltjni.Vec3((float) expandedBox.maxX, (float) expandedBox.maxY, (float) expandedBox.maxZ);

        try (AaBox joltAabb = new AaBox(minJoltVec, maxJoltVec);
             AllHitCollideShapeBodyCollector broadPhaseCollector = new AllHitCollideShapeBodyCollector();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             ObjectLayerFilter olFilter = new ObjectLayerFilter()) {

            BroadPhaseQuery bpQuery = physicsWorld.getPhysicsSystem().getBroadPhaseQuery();
            bpQuery.collideAaBox(joltAabb, broadPhaseCollector, bplFilter, olFilter);

            for (int bodyId : broadPhaseCollector.getHits()) {
                if (!terrainSystem.isTerrainBody(bodyId)) {

                    physicsBodyIds.add(bodyId);
                }
            }

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during Jolt broad-phase query", e);
        }

        return physicsBodyIds;
    }
}