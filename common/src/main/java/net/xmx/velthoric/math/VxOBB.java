/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * An Oriented Bounding Box (OBB) defined by a local AABB and a VxTransform.
 * This provides a bounding box that can be freely rotated and translated in world space.
 * Methods are designed to be analogous to Minecraft's AABB class.
 *
 * @author xI-Mx-Ix
 */
public class VxOBB {
    private static final double EPSILON = 1.0E-7;

    public final VxTransform transform;
    public final AABB localAABB; // The un-transformed, axis-aligned box at the origin

    public VxOBB(VxTransform transform, AABB localAABB) {
        this.transform = transform.copy();
        this.localAABB = localAABB;
    }

    public VxOBB(VxTransform transform, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.transform = transform.copy();
        this.localAABB = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Creates a new OBB from a center point, size, and transform.
     * The translation part of the transform will be overwritten by the center.
     */
    public static VxOBB ofSize(VxTransform transform, Vec3 center, double xSize, double ySize, double zSize) {
        AABB local = new AABB(-xSize / 2.0, -ySize / 2.0, -zSize / 2.0, xSize / 2.0, ySize / 2.0, zSize / 2.0);
        VxTransform newTransform = transform.copy();
        newTransform.getTranslation().set(center.x, center.y, center.z);
        return new VxOBB(newTransform, local);
    }

    /**
     * Gets the center of the OBB in world coordinates.
     */
    public Vec3 getCenter() {
        RVec3 center = this.transform.getTranslation();
        return new Vec3(center.xx(), center.yy(), center.zz());
    }
    
    /**
     * Gets the half-extents (half-sizes) of the local bounding box.
     */
    public Vec3 getExtents() {
        return new Vec3(localAABB.getXsize() / 2.0, localAABB.getYsize() / 2.0, localAABB.getZsize() / 2.0);
    }

    /**
     * Returns a new OBB that is offset by the specified amount.
     */
    public VxOBB move(double x, double y, double z) {
        VxTransform newTransform = this.transform.copy();
        RVec3 translation = newTransform.getTranslation();
        translation.set(translation.xx() + x, translation.yy() + y, translation.zz() + z);
        return new VxOBB(newTransform, this.localAABB);
    }

    public VxOBB move(Vec3 vec) {
        return move(vec.x, vec.y, vec.z);
    }
    
    /**
     * Returns a new OBB that is rotated by the specified quaternion.
     * The rotation is applied on top of the existing rotation.
     */
    public VxOBB rotate(Quat rotation) {
        VxTransform newTransform = this.transform.copy();
        // Use Op.star for quaternion multiplication
        Quat resultRotation = Op.star(newTransform.getRotation(), rotation);
        newTransform.getRotation().set(resultRotation);
        return new VxOBB(newTransform, this.localAABB);
    }

    /**
     * Creates a new {@link VxOBB} that has been expanded by the given value in all directions in its local space.
     */
    public VxOBB inflate(double value) {
        return new VxOBB(this.transform, this.localAABB.inflate(value));
    }

    public VxOBB inflate(double x, double y, double z) {
        return new VxOBB(this.transform, this.localAABB.inflate(x, y, z));
    }

    /**
     * Creates a new {@link VxOBB} that has been contracted by the given value in all directions in its local space.
     */
    public VxOBB deflate(double value) {
        return new VxOBB(this.transform, this.localAABB.deflate(value));
    }
    
    /**
     * Checks if the supplied world-space Vec3 is completely inside the OBB.
     */
    public boolean contains(Vec3 point) {
        // To check for containment, transform the point into the OBB's local space
        // and perform a simple AABB contains check.
        Vec3 localPoint = transformToLocal(point);
        return this.localAABB.contains(localPoint);
    }
    
    /**
     * Checks if this OBB intersects with another OBB using the Separating Axis Theorem (SAT).
     */
    public boolean intersects(VxOBB other) {
        // Get data for this OBB
        Vec3 extentsA = this.getExtents();
        Quat rotA = this.transform.getRotation();
        Vec3[] axesA = new Vec3[] {
            toMcVec3(rotA.rotateAxisX()),
            toMcVec3(rotA.rotateAxisY()),
            toMcVec3(rotA.rotateAxisZ())
        };
        
        // Get data for other OBB
        Vec3 extentsB = other.getExtents();
        Quat rotB = other.transform.getRotation();
        Vec3[] axesB = new Vec3[] {
            toMcVec3(rotB.rotateAxisX()),
            toMcVec3(rotB.rotateAxisY()),
            toMcVec3(rotB.rotateAxisZ())
        };

        // Vector from center of A to center of B
        Vec3 t = other.getCenter().subtract(this.getCenter());
        
        // Precompute rotation matrix from B to A's space
        double[][] R = new double[3][3];
        double[][] absR = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                R[i][j] = axesA[i].dot(axesB[j]);
                absR[i][j] = Math.abs(R[i][j]) + EPSILON; // Add epsilon for floating point stability
            }
        }
        
        // Test axes L = A0, L = A1, L = A2
        for (int i = 0; i < 3; i++) {
            double ra = getComponent(extentsA, i);
            double rb = getComponent(extentsB, 0) * absR[i][0] + getComponent(extentsB, 1) * absR[i][1] + getComponent(extentsB, 2) * absR[i][2];
            if (Math.abs(axesA[i].dot(t)) > ra + rb) return false;
        }

        // Test axes L = B0, L = B1, L = B2
        for (int i = 0; i < 3; i++) {
            double ra = getComponent(extentsA, 0) * absR[0][i] + getComponent(extentsA, 1) * absR[1][i] + getComponent(extentsA, 2) * absR[2][i];
            double rb = getComponent(extentsB, i);
            double projection = axesB[i].dot(t);
            if (Math.abs(projection) > ra + rb) return false;
        }
        
        // Test cross product axes L = A_i x B_j
        double ra, rb;
        // A0 x B0, A0 x B1, A0 x B2
        ra = extentsA.y * absR[2][0] + extentsA.z * absR[1][0];
        rb = extentsB.y * absR[0][2] + extentsB.z * absR[0][1];
        if (Math.abs(t.z * R[1][0] - t.y * R[2][0]) > ra + rb) return false;
        
        ra = extentsA.y * absR[2][1] + extentsA.z * absR[1][1];
        rb = extentsB.x * absR[0][2] + extentsB.z * absR[0][0];
        if (Math.abs(t.z * R[1][1] - t.y * R[2][1]) > ra + rb) return false;

        ra = extentsA.y * absR[2][2] + extentsA.z * absR[1][2];
        rb = extentsB.x * absR[0][1] + extentsB.y * absR[0][0];
        if (Math.abs(t.z * R[1][2] - t.y * R[2][2]) > ra + rb) return false;
        
        // A1 x B0, A1 x B1, A1 x B2
        ra = extentsA.x * absR[2][0] + extentsA.z * absR[0][0];
        rb = extentsB.y * absR[1][2] + extentsB.z * absR[1][1];
        if (Math.abs(t.x * R[2][0] - t.z * R[0][0]) > ra + rb) return false;

        ra = extentsA.x * absR[2][1] + extentsA.z * absR[0][1];
        rb = extentsB.x * absR[1][2] + extentsB.z * absR[1][0];
        if (Math.abs(t.x * R[2][1] - t.z * R[0][1]) > ra + rb) return false;

        ra = extentsA.x * absR[2][2] + extentsA.z * absR[0][2];
        rb = extentsB.x * absR[1][1] + extentsB.y * absR[1][0];
        if (Math.abs(t.x * R[2][2] - t.z * R[0][2]) > ra + rb) return false;
        
        // A2 x B0, A2 x B1, A2 x B2
        ra = extentsA.x * absR[1][0] + extentsA.y * absR[0][0];
        rb = extentsB.y * absR[2][2] + extentsB.z * absR[2][1];
        if (Math.abs(t.y * R[0][0] - t.x * R[1][0]) > ra + rb) return false;

        ra = extentsA.x * absR[1][1] + extentsA.y * absR[0][1];
        rb = extentsB.x * absR[2][2] + extentsB.z * absR[2][0];
        if (Math.abs(t.y * R[0][1] - t.x * R[1][1]) > ra + rb) return false;

        ra = extentsA.x * absR[1][2] + extentsA.y * absR[0][2];
        rb = extentsB.x * absR[2][1] + extentsB.y * absR[2][0];
        return !(Math.abs(t.y * R[0][2] - t.x * R[1][2]) > ra + rb);

        // No separating axis found, the OBBs must be intersecting.
    }

    /**
     * Checks if this oriented bounding box (OBB) intersects with a given axis-aligned bounding box (AABB).
     *
     * @param aabb the axis-aligned bounding box to test for intersection
     * @return true if this OBB intersects with the given AABB, false otherwise
     */
    public boolean intersectsWith(AABB aabb) {
        Vec3 center = aabb.getCenter();
        double hx = aabb.getXsize() / 2.0;
        double hy = aabb.getYsize() / 2.0;
        double hz = aabb.getZsize() / 2.0;

        VxTransform aabbTransform = new VxTransform(new RVec3(center.x, center.y, center.z), new Quat());
        AABB localAABB = new AABB(-hx, -hy, -hz, hx, hy, hz);
        VxOBB other = new VxOBB(aabbTransform, localAABB);

        return this.intersects(other);
    }

    /**
     * Calculates the intersection of a ray with this OBB.
     * Analogous to AABB.clip, but does not return the hit side direction.
     */
    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        // Transform the ray into the OBB's local coordinate system
        Vec3 localFrom = transformToLocal(from);
        Vec3 localTo = transformToLocal(to);

        // Perform the clip against the now axis-aligned localAABB
        Optional<Vec3> localHit = this.localAABB.clip(localFrom, localTo);

        // If there was a hit, transform the hit point back to world coordinates
        return localHit.map(this::transformToWorld);
    }
    
    /**
     * Calculates the world-space axis-aligned bounding box that perfectly encloses this OBB.
     * This is useful for broad-phase collision detection.
     */
    public AABB getBounds() {
        Vec3[] corners = getCorners();
        Vec3 min = corners[0];
        Vec3 max = corners[0];

        for (int i = 1; i < 8; i++) {
            min = new Vec3(Math.min(min.x, corners[i].x), Math.min(min.y, corners[i].y), Math.min(min.z, corners[i].z));
            max = new Vec3(Math.max(max.x, corners[i].x), Math.max(max.y, corners[i].y), Math.max(max.z, corners[i].z));
        }
        return new AABB(min, max);
    }

    /**
     * Gets the 8 corners of the OBB in world coordinates.
     */
    public Vec3[] getCorners() {
        Vec3[] corners = new Vec3[8];
        corners[0] = transformToWorld(new Vec3(localAABB.minX, localAABB.minY, localAABB.minZ));
        corners[1] = transformToWorld(new Vec3(localAABB.maxX, localAABB.minY, localAABB.minZ));
        corners[2] = transformToWorld(new Vec3(localAABB.maxX, localAABB.maxY, localAABB.minZ));
        corners[3] = transformToWorld(new Vec3(localAABB.minX, localAABB.maxY, localAABB.minZ));
        corners[4] = transformToWorld(new Vec3(localAABB.minX, localAABB.minY, localAABB.maxZ));
        corners[5] = transformToWorld(new Vec3(localAABB.maxX, localAABB.minY, localAABB.maxZ));
        corners[6] = transformToWorld(new Vec3(localAABB.maxX, localAABB.maxY, localAABB.maxZ));
        corners[7] = transformToWorld(new Vec3(localAABB.minX, localAABB.maxY, localAABB.maxZ));
        return corners;
    }
    
    // --- Helper Methods ---
    
    /**
     * Transforms a point from world space to the OBB's local space.
     */
    private Vec3 transformToLocal(Vec3 worldPoint) {
        // 1. Translate the point relative to the OBB's center
        RVec3 center = this.transform.getTranslation();
        RVec3 p = new RVec3(worldPoint.x - center.xx(), worldPoint.y - center.yy(), worldPoint.z - center.zz());

        // 2. Rotate the point by the inverse (conjugate) of the OBB's rotation
        // Use conjugated() to get the inverse
        Quat inverseRotation = this.transform.getRotation().conjugated();

        // RVec3 needs to be rotated in place, so we create a copy
        RVec3 localPoint = new RVec3(p);
        localPoint.rotateInPlace(inverseRotation);

        return new Vec3(localPoint.xx(), localPoint.yy(), localPoint.zz());
    }

    /**
     * Transforms a point from the OBB's local space to world space.
     */
    private Vec3 transformToWorld(Vec3 localPoint) {
        // 1. Rotate the point by the OBB's rotation
        RVec3 p = new RVec3(localPoint.x, localPoint.y, localPoint.z);
        
        // RVec3 needs to be rotated in place, so we create a copy
        RVec3 rotatedPoint = new RVec3(p);
        rotatedPoint.rotateInPlace(this.transform.getRotation());

        // 2. Translate the point to the OBB's world position
        RVec3 center = this.transform.getTranslation();
        return new Vec3(rotatedPoint.xx() + center.xx(), rotatedPoint.yy() + center.yy(), rotatedPoint.zz() + center.zz());
    }
    
    /**
     * Converts a joltjni Vec3 to a Minecraft Vec3.
     */
    private static Vec3 toMcVec3(com.github.stephengold.joltjni.Vec3 joltVec) {
        return new Vec3(joltVec.getX(), joltVec.getY(), joltVec.getZ());
    }
    
    private static double getComponent(Vec3 v, int index) {
        return switch (index) {
            case 0 -> v.x;
            case 1 -> v.y;
            case 2 -> v.z;
            default -> 0;
        };
    }

    @Override
    public String toString() {
        return "VxOBB{" +
                "transform=" + transform +
                ", localAABB=" + localAABB +
                '}';
    }
}