package net.xmx.vortex.mixin.impl.entitycollision;

import com.github.stephengold.joltjni.*;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.entitycollision.VxVoxelShape;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisions_MixinEntity {

    @Inject(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;addAll(Ljava/lang/Iterable;)Lcom/google/common/collect/ImmutableList$Builder;", ordinal = 1),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void injectPhysicsCollisions(
            @Nullable Entity pEntity,
            Vec3 pVec,
            AABB pCollisionBox,
            Level pLevel,
            List<VoxelShape> pPotentialHits,
            CallbackInfoReturnable<Vec3> cir,
            ImmutableList.Builder<VoxelShape> builder) {

        vortex$addPhysicsCollisions(pLevel, pCollisionBox.expandTowards(pVec), builder);
    }

    @Unique
    private static void vortex$addPhysicsCollisions(
            Level level,
            AABB expandedBox,
            ImmutableList.Builder<VoxelShape> builder) {

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        TerrainSystem terrainSystem = physicsWorld.getTerrainSystem();
        if (physicsSystem == null || terrainSystem == null) {
            return;
        }

        try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector()) {
            com.github.stephengold.joltjni.Vec3 min = new com.github.stephengold.joltjni.Vec3(
                    (float) expandedBox.minX, (float) expandedBox.minY, (float) expandedBox.minZ);
            com.github.stephengold.joltjni.Vec3 max = new com.github.stephengold.joltjni.Vec3(
                    (float) expandedBox.maxX, (float) expandedBox.maxY, (float) expandedBox.maxZ);

            try (AaBox joltExpandedBox = new AaBox(min, max)) {
                BroadPhaseQuery bpQuery = physicsSystem.getBroadPhaseQuery();
                bpQuery.collideAaBox(joltExpandedBox, collector);

                int[] hitBodyIds = collector.getHits();
                for (int bodyId : hitBodyIds) {
                    if (!terrainSystem.isTerrainBody(bodyId)) {
                        builder.add(new VxVoxelShape(level, bodyId));
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Mixin: Error performing broad-phase collision check: {}", e.getMessage(), e);
        }
    }
}