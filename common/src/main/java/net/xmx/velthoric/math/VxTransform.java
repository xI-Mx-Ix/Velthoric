/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents a physics transform consisting of a double-precision position
 * and a single-precision rotation quaternion.
 * <p>
 * This class is used to synchronize physics body states and convert between
 * Jolt physics types and Minecraft/JOML types.
 *
 * @author xI-Mx-Ix
 */
public class VxTransform {
    private final RVec3 translation = new RVec3();
    private final Quat rotation = new Quat();

    /**
     * Creates a new identity transform (Position 0,0,0; No rotation).
     */
    public VxTransform() {
        this.rotation.loadIdentity();
    }

    /**
     * Creates a transform with the specified position and rotation.
     *
     * @param translation The position vector.
     * @param rotation    The rotation quaternion.
     */
    public VxTransform(RVec3Arg translation, QuatArg rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
    }

    /**
     * Gets the position vector.
     *
     * @return The mutable RVec3 translation.
     */
    public RVec3 getTranslation() {
        return translation;
    }

    /**
     * Gets the rotation quaternion.
     *
     * @return The mutable Quat rotation.
     */
    public Quat getRotation() {
        return rotation;
    }

    /**
     * Converts this transform into a Jolt 4x4 Matrix.
     *
     * @return A new {@link RMat44} representing this transform.
     */
    public RMat44 toRMat44() {
        return RMat44.sRotationTranslation(this.rotation, this.translation);
    }

    /**
     * Copies the values from another transform into this one.
     *
     * @param other The transform to copy from.
     */
    public void set(VxTransform other) {
        this.translation.set(other.translation);
        this.rotation.set(other.rotation);
    }

    /**
     * Sets the position and rotation directly.
     *
     * @param translation The new position.
     * @param rotation    The new rotation.
     */
    public void set(RVec3Arg translation, QuatArg rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
    }

    /**
     * Creates a deep copy of this transform.
     *
     * @return A new VxTransform instance with the same values.
     */
    public VxTransform copy() {
        return new VxTransform(this.translation, this.rotation);
    }

    /**
     * Resets this transform to the identity state (Zero position, Identity rotation).
     */
    public void loadIdentity() {
        this.translation.loadZero();
        this.rotation.loadIdentity();
    }

    /**
     * Serializes this transform to a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void toBuffer(FriendlyByteBuf buf) {
        buf.writeDouble(translation.xx());
        buf.writeDouble(translation.yy());
        buf.writeDouble(translation.zz());

        buf.writeFloat(rotation.getX());
        buf.writeFloat(rotation.getY());
        buf.writeFloat(rotation.getZ());
        buf.writeFloat(rotation.getW());
    }

    /**
     * Deserializes transform data from a network buffer into this instance.
     *
     * @param buf The buffer to read from.
     */
    public void fromBuffer(FriendlyByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        this.translation.set(x, y, z);

        float qx = buf.readFloat();
        float qy = buf.readFloat();
        float qz = buf.readFloat();
        float qw = buf.readFloat();
        this.rotation.set(qx, qy, qz, qw);
        // Ensure the quaternion is normalized after network transmission
        this.rotation.set(this.rotation.normalized());
    }

    /**
     * Static helper to create a new transform from a buffer.
     *
     * @param buf The buffer to read from.
     * @return A new VxTransform instance.
     */
    public static VxTransform createFromBuffer(FriendlyByteBuf buf) {
        VxTransform t = new VxTransform();
        t.fromBuffer(buf);
        return t;
    }

    @Override
    public String toString() {
        return "VxTransform{" +
                "translation=" + translation +
                ", rotation=" + rotation +
                '}';
    }

    /**
     * Converts the translation to a JOML Vector3f.
     * Note: This loses precision (double -> float).
     *
     * @param dest The vector to store the result in.
     * @return The destination vector.
     */
    public Vector3f getTranslation(Vector3f dest) {
        return dest.set((float) this.translation.xx(), (float) this.translation.yy(), (float) this.translation.zz());
    }

    /**
     * Converts the rotation to a JOML Quaternionf.
     *
     * @param dest The quaternion to store the result in.
     * @return The destination quaternion.
     */
    public Quaternionf getRotation(Quaternionf dest) {
        return dest.set(this.rotation.getX(), this.rotation.getY(), this.rotation.getZ(), this.rotation.getW());
    }
}