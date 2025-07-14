package net.xmx.xbullet.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.network.FriendlyByteBuf;

public class PhysicsTransform {
    private final RVec3 translation = new RVec3();
    private final Quat rotation = new Quat();

    public PhysicsTransform() {
        this.rotation.loadIdentity();
    }

    public PhysicsTransform(RVec3Arg translation, QuatArg rotation) {
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

    public void set(PhysicsTransform other) {
        this.translation.set(other.translation);
        this.rotation.set(other.rotation);
    }

    public PhysicsTransform copy() {
        return new PhysicsTransform(this.translation, this.rotation);
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

    public static PhysicsTransform createFromBuffer(FriendlyByteBuf buf) {
        PhysicsTransform t = new PhysicsTransform();
        t.fromBuffer(buf);
        return t;
    }

    @Override
    public String toString() {
        return "PhysicsTransform{" +
                "translation=" + translation +
                ", rotation=" + rotation +
                '}';
    }
}