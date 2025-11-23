/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntityState;
import net.xmx.velthoric.physics.mounting.util.VxMountingRenderUtils;
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

/**
 * Enhances the GameRenderer to support physics-based entity mounting.
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

    @Unique
    private final VxTransform velthoric_interpolatedTransform = new VxTransform();

    /**
     * Prepares entities for rendering by temporarily moving them to their interpolated physics positions.
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void velthoric_preRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientLevel clientWorld = minecraft.level;
        if (clientWorld == null) return;

        velthoric_originalStates.clear();

        for (Entity entity : clientWorld.entitiesForRendering()) {
            if (entity instanceof VxMountingEntity proxy && !proxy.getPassengers().isEmpty()) {
                Entity passenger = proxy.getFirstPassenger();
                if (passenger != null) {
                    velthoric_adjustEntityForRender(proxy, deltaTracker.getGameTimeDeltaPartialTick(true));
                    velthoric_adjustEntityForRender(passenger, deltaTracker.getGameTimeDeltaPartialTick(true));
                }
            }
        }
    }

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

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void velthoric_postRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
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
     * Intercepts the frustum preparation.
     * <p>
     * <b>Crucial Fix:</b> We do NOT rotate the {@code viewMatrix} here. {@link MixinCamera} has already
     * rotated the Camera object itself. The {@code viewMatrix} passed to this method is derived from
     * that rotated Camera. Modifying it again here would double-apply the rotation or invert it,
     * causing the frustum to be misaligned (inverted culling).
     * <p>
     * We only intervene to ensure the Projection Matrix (FOV) is correct if the mod changes FOV logic.
     */
    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
            )
    )
    private void velthoric_setupCameraWithPhysicsBody(LevelRenderer instance, Vec3 cameraPos, Matrix4f viewMatrix, Matrix4f projectionMatrix, Operation<Void> original) {
        Entity player = this.minecraft.player;
        if (player != null) {
            final float partialTicks = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);

            boolean[] handled = {false};
            VxMountingRenderUtils.INSTANCE.ifMountedOnBody(player, partialTicks, physQuat -> {
                double fov = this.getFov(this.mainCamera, partialTicks, true);
                Matrix4f newProjectionMatrix = this.getProjectionMatrix(Math.max(fov, this.minecraft.options.fov().get()));

                // Pass the original viewMatrix. It is already correct because Camera.setup() ran first.
                original.call(instance, cameraPos, viewMatrix, newProjectionMatrix);
                handled[0] = true;
            });

            if (handled[0]) {
                return;
            }
        }

        original.call(instance, cameraPos, viewMatrix, projectionMatrix);
    }

    @WrapOperation(
            method = "pick(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"
            )
    )
    private HitResult velthoric_wrapPick(GameRenderer instance, Entity entity, double blockReach, double entityReach, float partialTicks, Operation<HitResult> original) {
        HitResult vanillaResult = original.call(instance, entity, blockReach, entityReach, partialTicks);

        if (!(vanillaResult instanceof EntityHitResult)) {
            Vec3 eyePos = entity.getEyePosition(partialTicks);
            Vec3 viewVector = entity.getViewVector(partialTicks);
            Vec3 reachVec = eyePos.add(viewVector.scale(entityReach));

            AABB searchBox = entity.getBoundingBox().expandTowards(viewVector.scale(entityReach)).inflate(1.0);

            EntityHitResult obbResult = velthoric_pickMountedEntities(entity, eyePos, reachVec, searchBox, entityReach * entityReach);
            if (obbResult != null) {
                return obbResult;
            }
        }

        return vanillaResult;
    }

    @Unique
    private EntityHitResult velthoric_pickMountedEntities(Entity shooter, Vec3 start, Vec3 end, AABB searchBox, double maxDistanceSq) {
        double closestDistSq = maxDistanceSq;
        EntityHitResult bestResult = null;

        List<Entity> potentialTargets = this.minecraft.level.getEntities(shooter, searchBox,
                e -> !e.isSpectator() && e.isPickable());

        for (Entity target : potentialTargets) {
            if (target.getVehicle() instanceof VxMountingEntity) {
                Optional<EntityHitResult> obbHit = velthoric_performObbRaycast(target, start, end);
                if (obbHit.isPresent()) {
                    double distSq = start.distanceToSqr(obbHit.get().getLocation());
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        bestResult = obbHit.get();
                    }
                }
            }
        }

        return bestResult;
    }

    @Unique
    private Optional<EntityHitResult> velthoric_performObbRaycast(Entity target, Vec3 start, Vec3 end) {
        if (!(target.getVehicle() instanceof VxMountingEntity proxy)) {
            return Optional.empty();
        }

        return velthoric_createTargetOBB(target, proxy)
                .flatMap(obb -> obb.clip(start, end))
                .map(hitPos -> new EntityHitResult(target, hitPos));
    }

    @Unique
    private Optional<VxOBB> velthoric_createTargetOBB(Entity target, VxMountingEntity proxy) {
        float partialTicks = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);

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