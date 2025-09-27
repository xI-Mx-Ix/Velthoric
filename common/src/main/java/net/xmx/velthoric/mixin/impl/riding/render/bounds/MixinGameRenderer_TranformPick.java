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
 * @author xI-Mx-Ix
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_TranformPick {

    @Shadow @Final Minecraft minecraft;

    @WrapOperation(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;")
    )
    private EntityHitResult velthoric_pickEntityWithOBB(
            Entity shooter, Vec3 start, Vec3 end, AABB searchBox, Predicate<Entity> filter, double maxDistanceSq,
            Operation<EntityHitResult> original) {

        EntityHitResult vanillaResult = original.call(shooter, start, end, searchBox, filter, maxDistanceSq);
        double closestHitDistSq = vanillaResult != null ? start.distanceToSqr(vanillaResult.getLocation()) : maxDistanceSq;
        EntityHitResult bestOverallResult = vanillaResult;

        List<Entity> potentialTargets = this.minecraft.level.getEntities(shooter, searchBox, filter);
        float partialTicks = this.minecraft.getFrameTime();

        for (Entity potentialTarget : potentialTargets) {
            if (potentialTarget.getVehicle() instanceof VxRidingProxyEntity proxy) {

                Optional<VxClientBody> physObjectOpt = proxy.getPhysicsObjectId()
                        .flatMap(id -> Optional.ofNullable(VxClientObjectManager.getInstance().getObject(id)));

                if (physObjectOpt.isPresent() && physObjectOpt.get().isInitialized()) {
                    VxClientBody physObject = physObjectOpt.get();

                    VxTransform physTransform = velthoric_getPhysicsObjectTransform(physObject, partialTicks);

                    Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                    physTransform.getRotation(new Quaternionf()).transform(rideOffset);
                    physTransform.getTranslation().addInPlace(rideOffset.x(), rideOffset.y(), rideOffset.z());

                    AABB targetAABB = potentialTarget.getBoundingBox().inflate(potentialTarget.getPickRadius());

                    AABB localEntityAABB = targetAABB.move(-potentialTarget.getX(), -potentialTarget.getY(), -potentialTarget.getZ());
                    VxOBB obb = new VxOBB(physTransform, localEntityAABB);

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