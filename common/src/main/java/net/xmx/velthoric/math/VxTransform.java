/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
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

public class VxTransform {
    private final RVec3 translation = new RVec3();
    private final Quat rotation = new Quat();

    public VxTransform() {
        this.rotation.loadIdentity();
    }

    public VxTransform(RVec3Arg translation, QuatArg rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
    }

    public RVec3 getTranslation() {
        return translation;
    }

    public Quat getRotation() {
        return rotation;
    }

    public RMat44 toRMat44() {
        return RMat44.sRotationTranslation(this.rotation, this.translation);
    }

    public void set(VxTransform other) {
        this.translation.set(other.translation);
        this.rotation.set(other.rotation);
    }

    public void set(RVec3Arg translation, QuatArg rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
    }

    public VxTransform copy() {
        return new VxTransform(this.translation, this.rotation);
    }

    public void loadIdentity() {
        this.translation.loadZero();
        this.rotation.loadIdentity();
    }

    public void toBuffer(FriendlyByteBuf buf) {
        buf.writeDouble(translation.xx());
        buf.writeDouble(translation.yy());
        buf.writeDouble(translation.zz());

        buf.writeFloat(rotation.getX());
        buf.writeFloat(rotation.getY());
        buf.writeFloat(rotation.getZ());
        buf.writeFloat(rotation.getW());
    }

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
        this.rotation.set(this.rotation.normalized());
    }

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

    public Vector3f getTranslation(Vector3f dest) {
        return dest.set((float) this.translation.xx(), (float) this.translation.yy(), (float) this.translation.zz());
    }

    public Quaternionf getRotation(Quaternionf dest) {
        return dest.set(this.rotation.getX(), this.rotation.getY(), this.rotation.getZ(), this.rotation.getW());
    }
}