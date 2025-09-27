/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.riding.VxOriginalState;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
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
import java.util.Map;

/**
 * @author xI-Mx-Ix
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_SmoothEntityPosition {

    @Shadow @Final Minecraft minecraft;

    @Unique
    private final RVec3 velthoric_interpolatedPosition = new RVec3();
    @Unique
    private final Quat velthoric_interpolatedRotation = new Quat();

    /**
     * A map to cache the original positions (xo, yo, zo) of entities before we modify them for rendering.
     * We use a custom class to store the values to avoid creating new objects every frame, and the entity's integer ID as the key.
     */
    @Unique
    private final Map<Integer, VxOriginalState> velthoric_originalStates = new HashMap<>();

    /**
     * Injected before the world is rendered. This is where we override the positions of the player
     * and their vehicle proxy to match the smooth, interpolated physics state.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.BEFORE))
    private void velthoric_preRenderLevel(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ClientLevel clientWorld = minecraft.level;
        if (clientWorld == null) return;

        // Clear previous state to avoid memory leaks if entities are removed without postRender being called.
        velthoric_originalStates.clear();

        for (Entity entity : clientWorld.entitiesForRendering()) {
            if (entity instanceof VxRidingProxyEntity proxy) {
                // If we find our proxy, we need to adjust it AND its passenger (the player).
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
     * Adjusts a single entity's position for this render frame.
     * @param entity The entity to adjust (either the proxy or the player).
     * @param tickDelta The current partial tick.
     */
    @Unique
    private void velthoric_adjustEntityForRender(Entity entity, float tickDelta) {
        VxRidingProxyEntity proxy;
        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof VxRidingProxyEntity vehicleProxy) {
            proxy = vehicleProxy;
        } else if (entity instanceof VxRidingProxyEntity selfProxy) {
            // This case handles the proxy itself, which has no vehicle.
            proxy = selfProxy;
        } else {
            return;
        }

        VxRidingProxyEntity finalProxy = proxy;
        finalProxy.getPhysicsObjectId().ifPresent(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            Integer index = store.getIndexForId(id);
            if (index == null || !store.render_isInitialized[index]) return;

            // Cache the original state before we modify it.
            velthoric_originalStates.computeIfAbsent(entity.getId(), k -> new VxOriginalState()).setFrom(entity);

            // Calculate the absolute target position and rotation for the physics object.
            manager.getInterpolator().interpolateFrame(store, index, tickDelta, velthoric_interpolatedPosition, velthoric_interpolatedRotation);

            Quaternionf physRotation = new Quaternionf(
                    velthoric_interpolatedRotation.getX(),
                    velthoric_interpolatedRotation.getY(),
                    velthoric_interpolatedRotation.getZ(),
                    velthoric_interpolatedRotation.getW()
            );

            Vector3f rideOffset = new Vector3f(finalProxy.getRidePositionOffset());
            physRotation.transform(rideOffset);

            double targetX = velthoric_interpolatedPosition.xx() + rideOffset.x;
            double targetY = velthoric_interpolatedPosition.yy() + rideOffset.y;
            double targetZ = velthoric_interpolatedPosition.zz() + rideOffset.z;

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
     * Injected after the world is rendered. We restore the original positions of all entities
     * we modified, so that the next game tick's logic is not affected.
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
}