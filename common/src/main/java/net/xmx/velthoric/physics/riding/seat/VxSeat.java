/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.seat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.math.VxTransform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents a single seat on a rideable physics object.
 * A seat defines a location where a player can sit, including its collision box,
 * rider offset, and whether it's a driver seat.
 *
 * @author xI-Mx-Ix
 */
public class VxSeat {
    private final String seatName;
    private final AABB localAABB;
    private final Vector3f riderOffset;
    private final boolean isDriverSeat;

    public VxSeat(String seatName, AABB localAABB, Vector3f riderOffset, boolean isDriverSeat) {
        this.seatName = seatName;
        this.localAABB = localAABB;
        this.riderOffset = riderOffset;
        this.isDriverSeat = isDriverSeat;
    }

    public VxSeat(FriendlyByteBuf buf) {
        this.seatName = buf.readUtf();
        double minX = buf.readDouble();
        double minY = buf.readDouble();
        double minZ = buf.readDouble();
        double maxX = buf.readDouble();
        double maxY = buf.readDouble();
        double maxZ = buf.readDouble();
        this.localAABB = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        this.riderOffset = new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.isDriverSeat = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.seatName);
        buf.writeDouble(this.localAABB.minX);
        buf.writeDouble(this.localAABB.minY);
        buf.writeDouble(this.localAABB.minZ);
        buf.writeDouble(this.localAABB.maxX);
        buf.writeDouble(this.localAABB.maxY);
        buf.writeDouble(this.localAABB.maxZ);
        buf.writeFloat(this.riderOffset.x());
        buf.writeFloat(this.riderOffset.y());
        buf.writeFloat(this.riderOffset.z());
        buf.writeBoolean(this.isDriverSeat);
    }

    /**
     * Transforms the seat's local-space AABB into a world-space AABB using the object's transform.
     *
     * @param objectTransform The current world transform of the physics object.
     * @return A new AABB representing the seat's bounds in world space.
     */
    public AABB getGlobalAABB(VxTransform objectTransform) {
        Vector3f[] corners = new Vector3f[8];
        corners[0] = new Vector3f((float) localAABB.minX, (float) localAABB.minY, (float) localAABB.minZ);
        corners[1] = new Vector3f((float) localAABB.maxX, (float) localAABB.minY, (float) localAABB.minZ);
        corners[2] = new Vector3f((float) localAABB.maxX, (float) localAABB.maxY, (float) localAABB.minZ);
        corners[3] = new Vector3f((float) localAABB.minX, (float) localAABB.maxY, (float) localAABB.minZ);
        corners[4] = new Vector3f((float) localAABB.minX, (float) localAABB.minY, (float) localAABB.maxZ);
        corners[5] = new Vector3f((float) localAABB.maxX, (float) localAABB.minY, (float) localAABB.maxZ);
        corners[6] = new Vector3f((float) localAABB.maxX, (float) localAABB.maxY, (float) localAABB.maxZ);
        corners[7] = new Vector3f((float) localAABB.minX, (float) localAABB.maxY, (float) localAABB.maxZ);

        Quaternionf rotation = new Quaternionf(objectTransform.getRotation().getX(), objectTransform.getRotation().getY(), objectTransform.getRotation().getZ(), objectTransform.getRotation().getW());
        Vector3f translation = new Vector3f(objectTransform.getTranslation().x(), objectTransform.getTranslation().y(), objectTransform.getTranslation().z());

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (Vector3f corner : corners) {
            // Apply rotation, then translation
            rotation.transform(corner);
            corner.add(translation);

            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public String getName() {
        return this.seatName;
    }

    public AABB getLocalAABB() {
        return this.localAABB;
    }

    public Vector3f getRiderOffset() {
        return this.riderOffset;
    }

    public boolean isDriverSeat() {
        return this.isDriverSeat;
    }
}