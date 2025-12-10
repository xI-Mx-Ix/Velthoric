/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.sync.C2SPartInteractPacket;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Represents a logical component of a vehicle (e.g., a door, wheel, or seat).
 * <p>
 * A part is attached to the main {@link VxVehicle} body and maintains a local transform relative to it.
 * Parts define their own hitboxes (OBB) for interaction and have their own rendering logic.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxPart {

    protected final VxVehicle vehicle;
    protected final UUID partId;
    protected final String partName;
    protected final Vector3f localPosition;
    protected final Quaternionf localRotation;

    /**
     * The local hit-box.
     * <p>
     * <strong>Note:</strong> This AABB must be centered at (0,0,0) relative to the part's pivot point.
     * The position in the world is determined entirely by {@link #localPosition}.
     * If this AABB contains offsets, they will be applied on top of the local position,
     * likely resulting in misaligned hitboxes.
     */
    protected final AABB localAABB;

    @Environment(EnvType.CLIENT)
    protected VxPartRenderer<VxPart> renderer;

    /**
     * Constructs a new vehicle part.
     *
     * @param vehicle       The parent vehicle.
     * @param partName      The unique name of this part.
     * @param localPosition The position relative to the vehicle center.
     * @param localAABB     The hit-box size centered at (0,0,0).
     */
    public VxPart(VxVehicle vehicle, String partName, Vector3f localPosition, AABB localAABB) {
        this.vehicle = vehicle;
        this.partName = partName;
        // Deterministic UUID generation ensures client and server IDs match
        this.partId = UUID.nameUUIDFromBytes((vehicle.getPhysicsId().toString() + partName).getBytes(StandardCharsets.UTF_8));
        this.localPosition = localPosition;
        this.localRotation = new Quaternionf();
        this.localAABB = localAABB;
    }

    @Environment(EnvType.CLIENT)
    @SuppressWarnings("unchecked")
    public void setRenderer(VxPartRenderer<? extends VxPart> renderer) {
        this.renderer = (VxPartRenderer<VxPart>) renderer;
    }

    @Environment(EnvType.CLIENT)
    public VxPartRenderer<VxPart> getRenderer() {
        return renderer;
    }

    @Environment(EnvType.CLIENT)
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight) {
        if (renderer != null) {
            renderer.render(this, poseStack, bufferSource, partialTicks, packedLight);
        }
    }

    /**
     * Handles interaction with this part.
     * On the client, this sends a packet to the server.
     *
     * @param player The player interacting.
     * @return True if interaction happened.
     */
    public boolean interact(Player player) {
        if (player.level().isClientSide) {
            VxPacketHandler.sendToServer(new C2SPartInteractPacket(vehicle.getPhysicsId(), this.partId));
            return true;
        }
        return false;
    }

    /**
     * Calculates the world-space Oriented Bounding Box (OBB) for this part.
     *
     * @param vehicleState The current render state of the parent vehicle.
     * @return The OBB in world space.
     */
    public VxOBB getGlobalOBB(VxRenderState vehicleState) {
        // 1. Get Vehicle Transform
        RVec3 vehiclePos = vehicleState.transform.getTranslation();
        Quat vehicleRot = vehicleState.transform.getRotation();

        // 2. Rotate local part position by vehicle rotation
        RVec3 localPosR = new RVec3(localPosition.x, localPosition.y, localPosition.z);
        localPosR.rotateInPlace(vehicleRot);

        // 3. Add rotated offset to vehicle position
        RVec3 worldPos = Op.plus(vehiclePos, localPosR);

        // 4. Combine rotations (Vehicle Rotation * Local Part Rotation)
        Quat localRotQ = new Quat(localRotation.x, localRotation.y, localRotation.z, localRotation.w);
        Quat worldRot = Op.star(vehicleRot, localRotQ);

        VxTransform partTransform = new VxTransform(worldPos, worldRot);

        // 5. Create OBB using the un-transformed local AABB
        return new VxOBB(partTransform, this.localAABB);
    }

    public UUID getId() {
        return partId;
    }

    public String getName() {
        return partName;
    }

    public Vector3f getLocalPosition() {
        return localPosition;
    }

    public Quaternionf getLocalRotation() {
        return localRotation;
    }
}