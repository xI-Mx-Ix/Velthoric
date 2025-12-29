/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.bridge.mounting.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxConversions;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.bridge.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.bridge.mounting.entity.VxMountingEntityState;
import net.xmx.velthoric.bridge.mounting.util.VxMountingRenderUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Applies several modifications to the GameRenderer for entities mounted on physics bodies.
 * This includes:
 * 1.  Temporarily moving mounted entities to their smoothly interpolated physics positions
 *     before rendering and restoring them after, ensuring fluid motion without affecting game logic.
 * 2.  Adjusting the camera's view matrix to account for the vehicle's rotation, allowing
 *     the world to be rendered correctly from a tilted perspective.
 * 3.  Replacing standard AABB-based entity picking with more precise OBB-based picking for mounted
 *     entities, ensuring accurate interaction with their rotated visual models.
 *
 * @author xI-Mx-Ix
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow @Final Minecraft minecraft;
    @Shadow @Final private Camera mainCamera;

    @Shadow protected abstract double getFov(Camera camera, float f, boolean bl);
    @Shadow public abstract Matrix4f getProjectionMatrix(double d);

    @Unique
    private final Map<Integer, VxMountingEntityState> velthoric_originalStates = new HashMap<>();

    /**
     * A reusable transform object to store interpolated physics state, avoiding re-allocation each frame.
     */
    @Unique
    private final VxTransform velthoric_interpolatedTransform = new VxTransform();

    /**
     * Injects before the world is rendered to override the positions of any mounted entities
     * to match their smooth, interpolated physics state for the current frame.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.BEFORE))
    private void velthoric_preRenderLevel(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ClientLevel clientWorld = minecraft.level;
        if (clientWorld == null) return;

        velthoric_originalStates.clear();

        for (Entity entity : clientWorld.entitiesForRendering()) {
            if (entity instanceof VxMountingEntity proxy && !proxy.getPassengers().isEmpty()) {
                Entity passenger = proxy.getFirstPassenger();
                if (passenger != null) {
                    velthoric_adjustEntityForRender(proxy, tickDelta);
                    velthoric_adjustEntityForRender(passenger, tickDelta);
                }
            }
        }
    }

    /**
     * Adjusts a single entity's position and rotation state for the current render frame based on
     * its physics vehicle's interpolated transform.
     */
    @Unique
    private void velthoric_adjustEntityForRender(Entity entity, float tickDelta) {
        VxMountingEntity proxy;
        if (entity.getVehicle() instanceof VxMountingEntity vehicleProxy) {
            proxy = vehicleProxy;
        } else if (entity instanceof VxMountingEntity selfProxy) {
            proxy = selfProxy;
        } else {
            return;
        }

        VxMountingRenderUtils.INSTANCE.getInterpolatedTransform(proxy, tickDelta, velthoric_interpolatedTransform).ifPresent(transform -> {
            velthoric_originalStates.computeIfAbsent(entity.getId(), k -> new VxMountingEntityState()).setFrom(entity);

            Quaternionf physRotation = transform.getRotation(new Quaternionf());
            Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
            physRotation.transform(rideOffset);

            double targetX = transform.getTranslation().xx() + rideOffset.x();
            double targetY = transform.getTranslation().yy() + rideOffset.y();
            double targetZ = transform.getTranslation().zz() + rideOffset.z();

            entity.setPos(targetX, targetY, targetZ);
            entity.xo = targetX;
            entity.yo = targetY;
            entity.zo = targetZ;
            entity.xOld = targetX;
            entity.yOld = targetY;
            entity.zOld = targetZ;
        });
    }

    /**
     * Injects after the world is rendered to restore the original positions of all modified entities.
     * This ensures that game logic for the next tick is not affected by the visual adjustments.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void velthoric_postRenderLevel(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ClientLevel clientWorld = minecraft.level;
        if (clientWorld == null) return;

        velthoric_originalStates.forEach((id, state) -> {
            Entity entity = clientWorld.getEntity(id);
            if (entity != null) {
                state.applyTo(entity);
            }
        });

        velthoric_originalStates.clear();
    }

    /**
     * Wraps the call to {@code LevelRenderer.prepareCullFrustum} to modify the camera setup when
     * the player is mounted on a physics body.
     */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"))
    private void velthoric_setupCameraWithPhysicsBody(LevelRenderer instance, PoseStack poseStack, Vec3 cameraPos, Matrix4f projectionMatrix, Operation<Void> original) {
        Entity player = this.minecraft.player;
        if (player != null) {
            final float partialTicks = this.minecraft.getFrameTime();
            VxMountingRenderUtils.INSTANCE.ifMountedOnBody(player, partialTicks, physQuat -> {
                Quaternionf physRotation = VxConversions.toJoml(physQuat, new Quaternionf());
                poseStack.mulPose(physRotation.conjugate());

                double fov = this.getFov(this.mainCamera, partialTicks, true);
                Matrix4f newProjectionMatrix = this.getProjectionMatrix(Math.max(fov, this.minecraft.options.fov().get()));
                original.call(instance, poseStack, this.mainCamera.getPosition(), newProjectionMatrix);
                return; // Exit lambda and method
            });
        }
        original.call(instance, poseStack, cameraPos, projectionMatrix);
    }

    /**
     * Wraps the entity picking logic to substitute AABB checks with OBB checks for entities mounted
     * on physics bodies.
     */
    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private EntityHitResult velthoric_pickEntityWithOBB(Entity shooter, Vec3 start, Vec3 end, AABB searchBox, Predicate<Entity> filter, double maxDistanceSq, Operation<EntityHitResult> original) {
        Predicate<Entity> vanillaFilter = filter.and(entity -> !(entity.getVehicle() instanceof VxMountingEntity));
        EntityHitResult vanillaResult = original.call(shooter, start, end, searchBox, vanillaFilter, maxDistanceSq);

        double closestHitDistSq = vanillaResult != null ? start.distanceToSqr(vanillaResult.getLocation()) : maxDistanceSq;
        EntityHitResult bestOverallResult = vanillaResult;

        List<Entity> potentialTargets = this.minecraft.level.getEntities(shooter, searchBox, filter);

        for (Entity target : potentialTargets) {
            if (target.getVehicle() instanceof VxMountingEntity) {
                Optional<EntityHitResult> obbHit = velthoric_performObbRaycast(target, start, end);
                if (obbHit.isPresent()) {
                    double distSq = start.distanceToSqr(obbHit.get().getLocation());
                    if (distSq < closestHitDistSq) {
                        closestHitDistSq = distSq;
                        bestOverallResult = obbHit.get();
                    }
                }
            }
        }
        return bestOverallResult;
    }

    /**
     * Performs a raycast against the Oriented Bounding Box (OBB) of a single target entity.
     */
    @Unique
    private Optional<EntityHitResult> velthoric_performObbRaycast(Entity target, Vec3 start, Vec3 end) {
        if (!(target.getVehicle() instanceof VxMountingEntity proxy)) {
            return Optional.empty();
        }

        return velthoric_createTargetOBB(target, proxy)
                .flatMap(obb -> obb.clip(start, end))
                .map(hitPos -> new EntityHitResult(target, hitPos));
    }

    /**
     * Creates an OBB for a target entity based on its physics vehicle's state.
     */
    @Unique
    private Optional<VxOBB> velthoric_createTargetOBB(Entity target, VxMountingEntity proxy) {
        float partialTicks = this.minecraft.getFrameTime();

        return VxMountingRenderUtils.INSTANCE.getInterpolatedTransform(proxy, partialTicks, velthoric_interpolatedTransform).map(transform -> {
            Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
            transform.getRotation(new Quaternionf()).transform(rideOffset);
            transform.getTranslation().addInPlace(rideOffset.x(), rideOffset.y(), rideOffset.z());

            AABB targetAABB = target.getBoundingBox().inflate(target.getPickRadius());
            AABB localEntityAABB = targetAABB.move(-target.getX(), -target.getY(), -target.getZ());

            return new VxOBB(transform, localEntityAABB);
        });
    }
}