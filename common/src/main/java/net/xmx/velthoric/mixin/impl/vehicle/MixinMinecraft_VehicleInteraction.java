/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.vehicle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Handles player interaction with vehicle parts (clicking seats, doors, etc.).
 * <p>
 * This mixin intercepts the use key press (usually right click) and performs
 * a raycast against Velthoric vehicle parts. If a part is hit, the interaction
 * is delegated to {@link VxPart#interact(net.minecraft.world.entity.player.Player)}.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_VehicleInteraction {

    @Shadow
    public LocalPlayer player;

    @Unique
    private final VxRenderState velthoric_renderState = new VxRenderState();
    @Unique
    private final RVec3 velthoric_tempPos = new RVec3();
    @Unique
    private final Quat velthoric_tempRot = new Quat();

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void velthoric_handleVehiclePartInteraction(CallbackInfo ci) {
        // Check for the click event immediately.
        if (Minecraft.getInstance().options.keyUse.consumeClick()) {
            // Perform the raycast. If successful, cancel vanilla processing.
            if (velthoric_performPartRaycastAndInteract()) {
                ci.cancel();
            }
        }
    }

    /**
     * Performs a raycast against all physics bodies to find a vehicle part the player
     * is looking at. If a part is hit, the interaction is triggered immediately.
     * <p>
     * This method iterates through every part of every vehicle, performing a broad-phase
     * AABB check on the part's bounds followed by a narrow-phase OBB check.
     *
     * @return True if an interaction occurred (packet sent), false otherwise.
     */
    @Unique
    private boolean velthoric_performPartRaycastAndInteract() {
        if (this.player == null || this.player.isPassenger()) {
            return false;
        }

        float partialTicks = Minecraft.getInstance().getFrameTime();
        double maxDist = this.player.isCreative() ? 5.0 : 4.5;
        Vec3 cameraPos = this.player.getEyePosition(partialTicks);
        Vec3 viewVec = this.player.getViewVector(partialTicks);
        Vec3 endVec = cameraPos.add(viewVec.x * maxDist, viewVec.y * maxDist, viewVec.z * maxDist);

        VxClientBodyManager bodyManager = VxClientBodyManager.getInstance();

        VxPart closestPart = null;
        double closestDistSq = Double.MAX_VALUE;

        // Iterate over all active bodies in the client world
        for (VxBody body : bodyManager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            // Only check bodies that are vehicles, as they have the modular part system
            if (body instanceof VxVehicle vehicle) {
                // Calculate the vehicle's interpolated render state for this frame
                vehicle.calculateRenderState(partialTicks, velthoric_renderState, velthoric_tempPos, velthoric_tempRot);

                // Iterate through all parts of the vehicle explicitly
                for (VxPart part : vehicle.getParts()) {
                    // Get the Oriented Bounding Box (OBB) of the part in world space
                    // This accounts for vehicle position, rotation, and part offset
                    VxOBB partOBB = part.getGlobalOBB(velthoric_renderState);

                    // 1. Broad Phase: Get the AABB enclosing the OBB and check for intersection.
                    // This replaces the vehicle-level culling check.
                    AABB broadPhaseBox = partOBB.getBounds();

                    if (broadPhaseBox.clip(cameraPos, endVec).isPresent()) {
                        // 2. Narrow Phase: Perform a precise check using the Oriented Bounding Box.
                        Optional<Vec3> exactHit = partOBB.clip(cameraPos, endVec);

                        if (exactHit.isPresent()) {
                            double distSq = cameraPos.distanceToSqr(exactHit.get());
                            if (distSq < closestDistSq) {
                                closestDistSq = distSq;
                                closestPart = part;
                            }
                        }
                    }
                }
            }
        }

        if (closestPart != null) {
            // Found a hit. Trigger the interaction (sends packet).
            return closestPart.interact(this.player);
        }

        return false;
    }
}