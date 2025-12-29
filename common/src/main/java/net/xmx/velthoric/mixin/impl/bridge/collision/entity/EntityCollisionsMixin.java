/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.bridge.collision.entity;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.AllHitCollideShapeBodyCollector;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.bridge.collision.entity.VxCombinedVoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.VxTerrainSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin for the Entity class to integrate Jolt physics collisions into Minecraft's collision detection.
 * <p>
 * In Minecraft 1.21, collision collection is delegated to the static helper method {@code collectColliders}.
 * This mixin intercepts the return value of that method. If Jolt physics bodies are detected in the
 * entity's path, a new list is built containing both the vanilla collisions and the Jolt physics shape.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class EntityCollisionsMixin {

    /**
     * Intercepts the end of the collision collection process.
     * <p>
     * Instead of trying to capture local variables inside the method (which can be unstable),
     * this injection happens at {@code RETURN}. It checks for physics bodies in the area.
     * If bodies are found, it creates a new ImmutableList that includes the vanilla results
     * plus the {@link VxCombinedVoxelShape}.
     *
     * @param entity      The entity performing the collision check (can be null).
     * @param level       The level the entity is in.
     * @param collisions  The list of potential voxel shapes (context from vanilla).
     * @param boundingBox The collision bounding box (already expanded by movement).
     * @param cir         The callback info returnable, used to override the return value.
     */
    @Inject(
            method = "collectColliders(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void injectJoltPhysicsCollisions(
            Entity entity,
            Level level,
            List<VoxelShape> collisions,
            AABB boundingBox,
            CallbackInfoReturnable<List<VoxelShape>> cir
    ) {
        // Query Jolt for physics bodies in the expanded bounding box
        IntList physicsBodyIds = jolt$getPhysicsBodyIdsInArea(level, boundingBox);

        if (!physicsBodyIds.isEmpty()) {
            // Vanilla returns an ImmutableList, so we cannot modify it directly.
            // We retrieve the vanilla result, create a new builder, add vanilla shapes,
            // add our physics shape, and then return the new list.
            List<VoxelShape> vanillaShapes = cir.getReturnValue();

            ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(vanillaShapes.size() + 1);
            builder.addAll(vanillaShapes);

            VxCombinedVoxelShape combinedShape = new VxCombinedVoxelShape(level, physicsBodyIds, entity);
            builder.add(combinedShape);

            cir.setReturnValue(builder.build());
        }
    }

    /**
     * Queries the Jolt physics system for body IDs located within the specified search area.
     *
     * @param level       The current level.
     * @param searchArea  The search bounding box (this is already expanded by the movement vector).
     * @return A list of integer IDs representing the physics bodies in the area, excluding terrain.
     */
    @Unique
    private static IntList jolt$getPhysicsBodyIdsInArea(Level level, AABB searchArea) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return new IntArrayList();
        }

        VxTerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(level.dimension());
        if (terrainSystem == null) {
            return new IntArrayList();
        }

        IntList physicsBodyIds = new IntArrayList();

        // Convert Minecraft AABB to Jolt vectors
        com.github.stephengold.joltjni.Vec3 minJoltVec = new com.github.stephengold.joltjni.Vec3((float) searchArea.minX, (float) searchArea.minY, (float) searchArea.minZ);
        com.github.stephengold.joltjni.Vec3 maxJoltVec = new com.github.stephengold.joltjni.Vec3((float) searchArea.maxX, (float) searchArea.maxY, (float) searchArea.maxZ);

        try (AaBox joltAabb = new AaBox(minJoltVec, maxJoltVec);
             AllHitCollideShapeBodyCollector broadPhaseCollector = new AllHitCollideShapeBodyCollector();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             ObjectLayerFilter olFilter = new ObjectLayerFilter()) {

            var bpQuery = physicsWorld.getPhysicsSystem().getBroadPhaseQuery();
            bpQuery.collideAaBox(joltAabb, broadPhaseCollector, bplFilter, olFilter);

            for (int bodyId : broadPhaseCollector.getHits()) {
                // Filter out static terrain bodies to avoid duplicate collisions with vanilla blocks
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