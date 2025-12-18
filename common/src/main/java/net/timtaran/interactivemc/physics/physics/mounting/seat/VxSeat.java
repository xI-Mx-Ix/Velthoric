/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.mounting.seat;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.math.VxOBB;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.mounting.VxMountable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single seat on a rideable physics body.
 * A seat defines a location where a player can sit, identified by a unique UUID.
 * It also includes its collision box, rider offset, and whether it's a driver seat.
 * The UUID is deterministically generated from the parent body's UUID and the seat's name.
 *
 * @author xI-Mx-Ix
 */
public class VxSeat {
    private final UUID seatId; // The unique, primary identifier for this seat.
    private final String seatName;
    private final AABB localAABB;
    private final Vector3f riderOffset;
    private final boolean isDriverSeat;

    /**
     * Constructs a new seat.
     * The seat's UUID is generated deterministically to ensure it is the same on the server and client.
     *
     * @param physicsId   The unique ID of the physics body this seat belongs to.
     * @param seatName    A unique name for the seat within the context of the body (e.g., "driver_seat").
     * @param localAABB   The local-space bounding box for interaction checks.
     * @param riderOffset The local-space offset from the seat's origin for the rider.
     * @param isDriverSeat True if this seat allows control of the vehicle.
     */
    public VxSeat(UUID physicsId, String seatName, AABB localAABB, Vector3f riderOffset, boolean isDriverSeat) {
        this.seatId = UUID.nameUUIDFromBytes(
                (physicsId.toString() + seatName).getBytes(StandardCharsets.UTF_8)
        );
        this.seatName = seatName;
        this.localAABB = localAABB;
        this.riderOffset = riderOffset;
        this.isDriverSeat = isDriverSeat;
    }

    /**
     * Transforms the seat's local-space AABB into a world-space AABB using the body's transform.
     * This creates a bounding box that fully encloses the rotated seat, suitable for broad-phase checks.
     *
     * @param objectTransform The current world transform of the physics body.
     * @return A new AABB representing the seat's coarse bounds in world space.
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

        Quaternionf rotation = new Quaternionf();
        objectTransform.getRotation(rotation);
        Vector3f translation = new Vector3f();
        objectTransform.getTranslation(translation);

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (Vector3f corner : corners) {
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

    /**
     * Creates a world-space Oriented Bounding Box (OBB) for this seat based on the parent body's transform.
     * This is highly accurate for raycasting and intersection tests, serving as the narrow-phase check.
     *
     * @param objectTransform The current world transform of the physics body.
     * @return A new VxOBB representing the seat's precise bounds and orientation in world space.
     */
    public VxOBB getGlobalOBB(VxTransform objectTransform) {
        Vec3 localCenter = this.localAABB.getCenter();
        RVec3 localCenterRVec = new RVec3(localCenter.x, localCenter.y, localCenter.z);

        localCenterRVec.rotateInPlace(objectTransform.getRotation());
        RVec3 worldSeatCenter = Op.plus(objectTransform.getTranslation(), localCenterRVec);
        VxTransform seatTransform = new VxTransform(worldSeatCenter, objectTransform.getRotation());

        double xSize = this.localAABB.getXsize();
        double ySize = this.localAABB.getYsize();
        double zSize = this.localAABB.getZsize();
        AABB centeredAABB = new AABB(-xSize / 2.0, -ySize / 2.0, -zSize / 2.0, xSize / 2.0, ySize / 2.0, zSize / 2.0);

        return new VxOBB(seatTransform, centeredAABB);
    }

    /**
     * Gets the unique identifier for this seat.
     * This is the primary key used for all seat-related operations.
     *
     * @return The seat's UUID.
     */
    public UUID getId() {
        return this.seatId;
    }

    /**
     * Gets the user-friendly, descriptive name of this seat.
     *
     * @return The seat's name.
     */
    public String getName() {
        return this.seatName;
    }

    /**
     * Gets the un-transformed, local-space bounding box of the seat.
     *
     * @return The local AABB.
     */
    public AABB getLocalAABB() {
        return this.localAABB;
    }

    /**
     * Gets the local-space offset from the seat's origin where a rider should be positioned.
     *
     * @return The rider offset vector.
     */
    public Vector3f getRiderOffset() {
        return this.riderOffset;
    }

    /**
     * Checks if this seat is designated as a driver's seat.
     *
     * @return True if it is a driver's seat, false otherwise.
     */
    public boolean isDriverSeat() {
        return this.isDriverSeat;
    }

    /**
     * A builder class for defining and collecting a list of {@link VxSeat} objects.
     * This is used to streamline the seat definition process for a {@link VxMountable}.
     */
    public static class Builder {
        private final List<VxSeat> seats = new ArrayList<>();

        /**
         * Adds a new seat to the collection.
         *
         * @param seat The {@link VxSeat} to add.
         * @return This builder instance for method chaining.
         */
        public Builder addSeat(VxSeat seat) {
            this.seats.add(seat);
            return this;
        }

        /**
         * Builds and returns the final list of defined seats.
         *
         * @return A new {@link List} containing all the added seats.
         */
        public List<VxSeat> build() {
            return new ArrayList<>(this.seats);
        }
    }
}