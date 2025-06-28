package net.xmx.xbullet.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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

    public void toNbt(CompoundTag tag) {
        ListTag posList = new ListTag();
        posList.add(DoubleTag.valueOf(translation.xx()));
        posList.add(DoubleTag.valueOf(translation.yy()));
        posList.add(DoubleTag.valueOf(translation.zz()));
        tag.put("pos", posList);

        ListTag rotList = new ListTag();
        rotList.add(FloatTag.valueOf(rotation.getX()));
        rotList.add(FloatTag.valueOf(rotation.getY()));
        rotList.add(FloatTag.valueOf(rotation.getZ()));
        rotList.add(FloatTag.valueOf(rotation.getW()));
        tag.put("rot", rotList);
    }

    public void fromNbt(CompoundTag tag) {
        if (tag.contains("pos", Tag.TAG_LIST)) {
            ListTag posList = tag.getList("pos", Tag.TAG_DOUBLE);
            if (posList.size() == 3) {
                this.translation.set(posList.getDouble(0), posList.getDouble(1), posList.getDouble(2));
            }
        }
        if (tag.contains("rot", Tag.TAG_LIST)) {
            ListTag rotList = tag.getList("rot", Tag.TAG_FLOAT);
            if (rotList.size() == 4) {
                this.rotation.set(rotList.getFloat(0), rotList.getFloat(1), rotList.getFloat(2), rotList.getFloat(3));
                this.rotation.set(this.rotation.normalized());
            }
        }
    }

    public static PhysicsTransform createFromNbt(CompoundTag tag) {
        PhysicsTransform t = new PhysicsTransform();
        t.fromNbt(tag);
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