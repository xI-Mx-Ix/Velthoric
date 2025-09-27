/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.bounds;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Mixin to GameRenderer to replace standard AABB-based entity picking (raycasting)
 * with OBB-based picking for entities riding on physics-driven objects. This ensures
 * that interaction logic correctly targets the rotated visual model.
 *
 * @author xI-Mx-Ix
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_TranformPick {

    @Shadow @Final private Minecraft minecraft;

    /**
     * Wraps the call to {@code ProjectileUtil.getEntityHitResult} to replace entity picking logic.
     * <p>
     * The strategy is to split entity picking into two parts:
     * 1. Vanilla AABB Picking: The original method is called with a modified filter that excludes
     *    any entity riding a {@link VxRidingProxyEntity}. This handles all standard entities correctly.
     * 2. Custom OBB Picking: A separate loop runs only over the excluded entities and performs a more
     *    accurate raycast against their Oriented Bounding Box (OBB).
     * <p>
     * The final result is the closest hit from either of these two checks, ensuring each entity
     * is tested exactly once with the appropriate method and preventing interaction with the old,
     * misaligned AABB of rotated entities.
     *
     * @param shooter The entity performing the pick.
     * @param start The start vector of the raycast.
     * @param end The end vector of the raycast.
     * @param searchBox The broad-phase search box.
     * @param filter The original predicate for filtering entities.
     * @param maxDistanceSq The maximum squared distance for a valid hit.
     * @param original A handle to the original method call.
     * @return The closest valid {@link EntityHitResult}, or null if no entity was hit.
     */
    @WrapOperation(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;")
    )
    private EntityHitResult velthoric_pickEntityWithOBB(
            Entity shooter, Vec3 start, Vec3 end, AABB searchBox, Predicate<Entity> filter, double maxDistanceSq,
            Operation<EntityHitResult> original) {

        // 1. Create a filter that excludes entities requiring OBB checks from the vanilla AABB raycast.
        Predicate<Entity> vanillaFilter = filter.and(entity -> !(entity.getVehicle() instanceof VxRidingProxyEntity));

        // 2. Perform the vanilla raycast on all "standard" entities.
        EntityHitResult vanillaResult = original.call(shooter, start, end, searchBox, vanillaFilter, maxDistanceSq);

        // 3. Initialize the state for our custom check, using the vanilla result as the current best.
        double closestHitDistSq = vanillaResult != null ? start.distanceToSqr(vanillaResult.getLocation()) : maxDistanceSq;
        EntityHitResult bestOverallResult = vanillaResult;

        // 4. Now, iterate through all potential targets to find only those we excluded and perform an OBB check.
        List<Entity> potentialTargets = this.minecraft.level.getEntities(shooter, searchBox, filter);
        float partialTicks = this.minecraft.getFrameTime();

        for (Entity potentialTarget : potentialTargets) {
            if (potentialTarget.getVehicle() instanceof VxRidingProxyEntity proxy) {

                Optional<VxClientBody> physObjectOpt = proxy.getPhysicsObjectId()
                        .flatMap(id -> Optional.ofNullable(VxClientObjectManager.getInstance().getObject(id)));

                if (physObjectOpt.isPresent() && physObjectOpt.get().isInitialized()) {
                    VxClientBody physObject = physObjectOpt.get();

                    // Get the interpolated transform for the physics body.
                    VxTransform physTransform = velthoric_getPhysicsObjectTransform(physObject, partialTicks);

                    // Apply the passenger's local offset to the transform.
                    Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                    physTransform.getRotation(new Quaternionf()).transform(rideOffset);
                    physTransform.getTranslation().addInPlace(rideOffset.x(), rideOffset.y(), rideOffset.z());

                    // Create the local AABB and the final OBB.
                    AABB targetAABB = potentialTarget.getBoundingBox().inflate(potentialTarget.getPickRadius());
                    AABB localEntityAABB = targetAABB.move(-potentialTarget.getX(), -potentialTarget.getY(), -potentialTarget.getZ());
                    VxOBB obb = new VxOBB(physTransform, localEntityAABB);

                    // Perform the raycast against the OBB.
                    Optional<Vec3> hitPos = obb.clip(start, end);

                    if (hitPos.isPresent()) {
                        double distSq = start.distanceToSqr(hitPos.get());
                        if (distSq < closestHitDistSq) {
                            closestHitDistSq = distSq;
                            bestOverallResult = new EntityHitResult(potentialTarget, hitPos.get());
                        }
                    }
                }
            }
        }

        return bestOverallResult;
    }

    /**
     * Retrieves the interpolated transformation of a physics body for a given partial tick.
     *
     * @param clientBody The client-side physics body.
     * @param partialTicks The fraction of a tick for interpolation.
     * @return An interpolated {@link VxTransform}.
     */
    @Unique
    private VxTransform velthoric_getPhysicsObjectTransform(VxClientBody clientBody, float partialTicks) {
        VxTransform transform = new VxTransform();
        com.github.stephengold.joltjni.RVec3 pos = new com.github.stephengold.joltjni.RVec3();
        com.github.stephengold.joltjni.Quat rot = new com.github.stephengold.joltjni.Quat();
        VxClientObjectManager.getInstance().getInterpolator().interpolateFrame(
                VxClientObjectManager.getInstance().getStore(),
                clientBody.getDataStoreIndex(),
                partialTicks,
                pos, rot
        );
        transform.getTranslation().set(pos);
        transform.getRotation().set(rot);
        return transform;
    }
}