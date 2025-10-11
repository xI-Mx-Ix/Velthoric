/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntityState;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
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
 * Applies several modifications to the GameRenderer for entities mounted on physics objects.
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

    /**
     * A reusable RVec3 to store interpolated position data, avoiding re-allocation each frame.
     */
    @Unique
    private final RVec3 velthoric_interpolatedPosition = new RVec3();

    /**
     * A reusable Quat to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private final Quat velthoric_interpolatedRotation = new Quat();

    /**
     * A map to cache the original state (position, etc.) of entities before they are modified for rendering.
     * The entity's integer ID serves as the key.
     */
    @Unique
    private final Map<Integer, VxMountingEntityState> velthoric_originalStates = new HashMap<>();

    /**
     * Injects before the world is rendered to override the positions of any mounted entities
     * to match their smooth, interpolated physics state for the current frame.
     *
     * @param tickDelta The fraction of a tick for interpolation.
     * @param ci Callback info provided by Mixin.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.BEFORE))
    private void velthoric_preRenderLevel(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ClientLevel clientWorld = minecraft.level;
        if (clientWorld == null) return;

        // Clear previous state to avoid memory leaks if entities are removed without postRender being called.
        velthoric_originalStates.clear();

        for (Entity entity : clientWorld.entitiesForRendering()) {
            if (entity instanceof VxMountingEntity proxy) {
                // Adjust both the mounting proxy and its passenger (the player).
                if (!proxy.getPassengers().isEmpty()) {
                    Entity passenger = proxy.getFirstPassenger();
                    if (passenger != null) {
                        velthoric_adjustEntityForRender(proxy, tickDelta);
                        velthoric_adjustEntityForRender(passenger, tickDelta);
                    }
                }
            }
        }
    }

    /**
     * Adjusts a single entity's position and rotation state for the current render frame based on
     * its physics vehicle's interpolated transform.
     *
     * @param entity The entity to adjust.
     * @param tickDelta The current partial tick for interpolation.
     */
    @Unique
    private void velthoric_adjustEntityForRender(Entity entity, float tickDelta) {
        VxMountingEntity proxy;
        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof VxMountingEntity vehicleProxy) {
            proxy = vehicleProxy;
        } else if (entity instanceof VxMountingEntity selfProxy) {
            // This case handles the mount proxy itself, which has no vehicle.
            proxy = selfProxy;
        } else {
            return;
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            Integer index = store.getIndexForId(id);
            if (index == null || !store.render_isInitialized[index]) return;

            // Cache the original state before modification.
            velthoric_originalStates.computeIfAbsent(entity.getId(), k -> new VxMountingEntityState()).setFrom(entity);

            // Calculate the absolute target position from the interpolated physics state.
            manager.getInterpolator().interpolateFrame(store, index, tickDelta, velthoric_interpolatedPosition, velthoric_interpolatedRotation);

            Quaternionf physRotation = new Quaternionf(
                    velthoric_interpolatedRotation.getX(),
                    velthoric_interpolatedRotation.getY(),
                    velthoric_interpolatedRotation.getZ(),
                    velthoric_interpolatedRotation.getW()
            );

            // Apply the local passenger offset.
            Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
            physRotation.transform(rideOffset);

            double targetX = velthoric_interpolatedPosition.xx() + rideOffset.x;
            double targetY = velthoric_interpolatedPosition.yy() + rideOffset.y;
            double targetZ = velthoric_interpolatedPosition.zz() + rideOffset.z;

            // Override the entity's position for this frame only.
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
     *
     * @param ci Callback info provided by Mixin.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER))
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
     * Wraps the call to `LevelRenderer.prepareCullFrustum` to modify the camera setup when
     * the player is mounted on a physics object. It applies the inverse of the vehicle's
     * rotation to the PoseStack and updates the `inverseViewRotationMatrix`, which is essential
     * for rendering the world correctly from a rotated viewpoint.
     *
     * @param instance The LevelRenderer instance.
     * @param poseStack The PoseStack for rendering transformations.
     * @param cameraPos The camera's position vector.
     * @param projectionMatrix The projection matrix.
     * @param original A handle to the original method call.
     * @param partialTicks The fraction of a tick for interpolation.
     */
    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
            )
    )
    private void velthoric_setupCameraWithPhysicsObject(
            LevelRenderer instance,
            PoseStack poseStack,
            Vec3 cameraPos,
            Matrix4f projectionMatrix,
            Operation<Void> original,
            float partialTicks,
            long finishTimeNano,
            PoseStack matrixStack // This is the actual parameter name from the method signature
    ) {
        Entity player = this.minecraft.player;
        if (player == null || !(player.getVehicle() instanceof VxMountingEntity proxy)) {
            original.call(instance, matrixStack, cameraPos, projectionMatrix);
            return;
        }

        proxy.getPhysicsObjectId().ifPresentOrElse(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            Integer index = store.getIndexForId(id);

            if (index == null || !store.render_isInitialized[index]) {
                original.call(instance, matrixStack, cameraPos, projectionMatrix);
                return;
            }

            manager.getInterpolator().interpolateRotation(store, index, partialTicks, velthoric_interpolatedRotation);
            Quaternionf physRotation = new Quaternionf(
                    velthoric_interpolatedRotation.getX(),
                    velthoric_interpolatedRotation.getY(),
                    velthoric_interpolatedRotation.getZ(),
                    velthoric_interpolatedRotation.getW()
            );

            // Apply the inverse of the physics rotation to the view matrix.
            Quaternionf invPhysRotation = new Quaternionf(new Quaterniond(physRotation).conjugate());
            matrixStack.mulPose(invPhysRotation);

            Matrix3f matrix3f = new Matrix3f(matrixStack.last().normal());
            matrix3f.invert();
            RenderSystem.setInverseViewRotationMatrix(matrix3f);

            double fov = this.getFov(this.mainCamera, partialTicks, true);
            Matrix4f newProjectionMatrix = this.getProjectionMatrix(Math.max(fov, this.minecraft.options.fov().get()));

            original.call(instance, matrixStack, this.mainCamera.getPosition(), newProjectionMatrix);

        }, () -> {
            original.call(instance, matrixStack, cameraPos, projectionMatrix);
        });
    }

    /**
     * Wraps the entity picking logic to substitute AABB checks with OBB checks for entities mounted
     * on physics objects. This ensures that raycasts for interaction or projectiles correctly
     * target the entity's visible, rotated bounding box.
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
        Predicate<Entity> vanillaFilter = filter.and(entity -> !(entity.getVehicle() instanceof VxMountingEntity));

        // 2. Perform the vanilla raycast on all standard entities.
        EntityHitResult vanillaResult = original.call(shooter, start, end, searchBox, vanillaFilter, maxDistanceSq);

        // 3. Initialize state for the custom check, using the vanilla result as the current best.
        double closestHitDistSq = vanillaResult != null ? start.distanceToSqr(vanillaResult.getLocation()) : maxDistanceSq;
        EntityHitResult bestOverallResult = vanillaResult;

        // 4. Iterate through all potential targets to find those excluded earlier and perform an OBB check.
        List<Entity> potentialTargets = this.minecraft.level.getEntities(shooter, searchBox, filter);
        float partialTicks = this.minecraft.getFrameTime();

        for (Entity potentialTarget : potentialTargets) {
            if (potentialTarget.getVehicle() instanceof VxMountingEntity proxy) {

                Optional<VxBody> physObjectOpt = proxy.getPhysicsObjectId()
                        .flatMap(id -> Optional.ofNullable(VxClientObjectManager.getInstance().getObject(id)));

                if (physObjectOpt.isPresent() && physObjectOpt.get().isInitialized()) {
                    VxBody physObject = physObjectOpt.get();

                    // Get the interpolated transform for the physics body.
                    VxTransform physTransform = velthoric_getPhysicsObjectTransform(physObject, partialTicks);

                    // Apply the passenger's local offset to the transform.
                    Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
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
    private VxTransform velthoric_getPhysicsObjectTransform(VxBody clientBody, float partialTicks) {
        VxTransform transform = new VxTransform();
        VxClientObjectManager.getInstance().getInterpolator().interpolateFrame(
                VxClientObjectManager.getInstance().getStore(),
                clientBody.getDataStoreIndex(),
                partialTicks,
                velthoric_interpolatedPosition,
                velthoric_interpolatedRotation
        );
        transform.getTranslation().set(velthoric_interpolatedPosition);
        transform.getRotation().set(velthoric_interpolatedRotation);
        return transform;
    }
}