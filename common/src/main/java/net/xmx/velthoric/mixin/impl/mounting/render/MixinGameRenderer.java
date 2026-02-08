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
import net.xmx.velthoric.core.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.core.mounting.entity.VxMountingEntityState;
import net.xmx.velthoric.core.mounting.util.VxMountingRenderUtils;
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
 * <p>
 * This mixin is responsible for:
 * <ul>
 *     <li>Synchronizing entity rendering positions with physics interpolation.</li>
 *     <li>Ensuring the camera system respects physics-based vehicle rotations.</li>
 *     <li>Replacing standard AABB raycasting with OBB raycasting for mounted entities.</li>
 * </ul>
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
     * Intercepts the main render loop immediately before the level is rendered.
     * <p>
     * This method iterates through entities mounted on physics-enabled vehicles and updates
     * their rendering positions to match the smooth, interpolated physics transform.
     * By applying this change in the main {@code render} method, the interpolated coordinates
     * remain active during GUI rendering, ensuring the F3 debug menu displays the correct
     * visual position relative to the frame's partial tick.
     *
     * @param deltaTracker The tracker providing partial tick time for interpolation.
     * @param runTasks     Whether render tasks should be run.
     * @param ci           The callback info.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void velthoric_preRender(DeltaTracker deltaTracker, boolean runTasks, CallbackInfo ci) {
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

    /**
     * Calculates and applies the interpolated physics position and rotation to an entity.
     * <p>
     * This stores the original entity state to be restored later and updates the entity's
     * position fields to match the visual physics transform.
     *
     * @param entity    The entity to adjust.
     * @param tickDelta The partial tick time for interpolation.
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
     * Intercepts the end of the main render loop to revert entity modifications.
     * <p>
     * This restores the entities to their actual game-logic positions after the World and GUI
     * have finished rendering. This ensures that physics interpolation is purely visual and
     * does not interfere with server-side position logic or client-side tick calculations
     * occurring in the next tick.
     *
     * @param deltaTracker The tracker providing partial tick time.
     * @param runTasks     Whether render tasks were run.
     * @param ci           The callback info.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void velthoric_postRender(DeltaTracker deltaTracker, boolean runTasks, CallbackInfo ci) {
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
     * Wraps the frustum preparation to adjust the camera projection when mounted.
     * <p>
     * Since the main camera object has already been rotated by preceding mixins, this method
     * focuses on ensuring the Projection Matrix (FOV) is calculated correctly for the current
     * physics state. It passes the original view matrix through to avoid applying rotations
     * twice (which would invert culling).
     *
     * @param instance          The LevelRenderer instance.
     * @param cameraPos         The current camera position.
     * @param viewMatrix        The view matrix (already rotated by Camera.setup).
     * @param projectionMatrix  The default projection matrix.
     * @param original          The original operation.
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

    /**
     * Wraps the entity picking (raycasting) method to support Oriented Bounding Boxes (OBB).
     * <p>
     * If the standard AABB pick fails, this method attempts to perform an OBB raycast
     * against mounted entities, which may be rotated relative to the world grid.
     *
     * @param instance     The GameRenderer instance.
     * @param entity       The entity performing the pick.
     * @param blockReach   The reach distance for blocks.
     * @param entityReach  The reach distance for entities.
     * @param partialTicks The partial tick time.
     * @param original     The original operation.
     * @return The result of the picking operation (block or entity).
     */
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

    /**
     * Helper method to perform OBB raycasting against potential targets within a search box.
     *
     * @param shooter       The entity shooting the ray.
     * @param start         The start position of the ray.
     * @param end           The end position of the ray.
     * @param searchBox     The AABB to search for entities within.
     * @param maxDistanceSq The maximum squared distance allowed for a hit.
     * @return The best EntityHitResult found, or null if none.
     */
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

    /**
     * Performs the actual geometric intersection test between the ray and the entity's OBB.
     *
     * @param target The target entity.
     * @param start  The ray start.
     * @param end    The ray end.
     * @return An Optional containing the hit result if an intersection occurs.
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
     * Constructs an Oriented Bounding Box (OBB) for the target entity based on the
     * interpolated physics transform of its vehicle.
     *
     * @param target The target entity.
     * @param proxy  The physics vehicle interface.
     * @return An Optional containing the calculated OBB.
     */
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