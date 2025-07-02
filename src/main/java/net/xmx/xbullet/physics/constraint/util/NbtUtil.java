package net.xmx.xbullet.physics.constraint.util;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.minecraft.nbt.*;
import net.xmx.xbullet.init.XBullet;

import java.nio.FloatBuffer;

public class NbtUtil {

    public static void putVec3(CompoundTag parent, String key, Vec3 vec) {
        if (vec == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putFloat("x", vec.getX());
        tag.putFloat("y", vec.getY());
        tag.putFloat("z", vec.getZ());
        parent.put(key, tag);
    }

    public static Vec3 getVec3(CompoundTag parent, String key) {
        if (!parent.contains(key, 10)) return new Vec3();
        CompoundTag tag = parent.getCompound(key);
        return new Vec3(tag.getFloat("x"), tag.getFloat("y"), tag.getFloat("z"));
    }

    public static void putRVec3(CompoundTag parent, String key, RVec3 vec) {
        if (vec == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vec.xx());
        tag.putDouble("y", vec.yy());
        tag.putDouble("z", vec.zz());
        parent.put(key, tag);
    }

    public static RVec3 getRVec3(CompoundTag parent, String key) {
        if (!parent.contains(key, 10)) return new RVec3();
        CompoundTag tag = parent.getCompound(key);
        return new RVec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    public static void putQuat(CompoundTag parent, String key, Quat quat) {
        if (quat == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putFloat("x", quat.getX());
        tag.putFloat("y", quat.getY());
        tag.putFloat("z", quat.getZ());
        tag.putFloat("w", quat.getW());
        parent.put(key, tag);
    }

    public static Quat getQuat(CompoundTag parent, String key) {
        if (!parent.contains(key, 10)) return new Quat();
        CompoundTag tag = parent.getCompound(key);
        return new Quat(tag.getFloat("x"), tag.getFloat("y"), tag.getFloat("z"), tag.getFloat("w"));
    }

    public static void putRMat44(CompoundTag parent, String key, RMat44 mat) {
        if (mat == null) {

            return;
        }

        ListTag listTag = new ListTag();

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 4; ++col) {

                listTag.add(DoubleTag.valueOf(mat.getElement(row, col)));
            }
        }

        parent.put(key, listTag);
    }

    public static RMat44 getRMat44(CompoundTag parent, String key) {

        if (!parent.contains(key, Tag.TAG_LIST)) {

            return new RMat44();
        }

        ListTag listTag = parent.getList(key, Tag.TAG_DOUBLE);

        if (listTag.size() != 16) {
            XBullet.LOGGER.warn("Konnte RMat44 mit Schlüssel '{}' nicht laden: NBT-Liste hat {} Elemente, 16 erwartet.", key, listTag.size());

            return new RMat44();
        }

        RMat44 result = new RMat44();

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 4; ++col) {

                double element = listTag.getDouble(row * 4 + col);
                result.setElement(row, col, element);
            }
        }

        return result;
    }

    public static void putSpringSettings(CompoundTag parent, String key, SpringSettings settings) {
        if (settings == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", settings.getMode().name());
        tag.putFloat("frequency", settings.getFrequency());
        tag.putFloat("damping", settings.getDamping());
        tag.putFloat("stiffness", settings.getStiffness());
        parent.put(key, tag);
    }

    public static void loadSpringSettings(CompoundTag parent, String key, SpringSettings settings) {
        if (!parent.contains(key, 10) || settings == null) return;
        CompoundTag tag = parent.getCompound(key);
        ESpringMode mode = ESpringMode.valueOf(tag.getString("mode"));
        settings.setMode(mode);
        if (mode == ESpringMode.FrequencyAndDamping) {
            settings.setFrequency(tag.getFloat("frequency"));
            settings.setDamping(tag.getFloat("damping"));
        } else {
            settings.setStiffness(tag.getFloat("stiffness"));
            settings.setDamping(tag.getFloat("damping"));
        }
    }

    public static void putMotorSettings(CompoundTag parent, String key, MotorSettings settings) {
        if (settings == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putFloat("maxForceLimit", settings.getMaxForceLimit());
        tag.putFloat("minForceLimit", settings.getMinForceLimit());
        tag.putFloat("maxTorqueLimit", settings.getMaxTorqueLimit());
        tag.putFloat("minTorqueLimit", settings.getMinTorqueLimit());

        SpringSettings spring = settings.getSpringSettings();
        putSpringSettings(tag, "springSettings", spring);
        spring.close();

        parent.put(key, tag);
    }

    public static void loadMotorSettings(CompoundTag parent, String key, MotorSettings settings) {
        if (!parent.contains(key, 10) || settings == null) return;
        CompoundTag tag = parent.getCompound(key);
        settings.setMaxForceLimit(tag.getFloat("maxForceLimit"));
        settings.setMinForceLimit(tag.getFloat("minForceLimit"));
        settings.setMaxTorqueLimit(tag.getFloat("maxTorqueLimit"));
        settings.setMinTorqueLimit(tag.getFloat("minTorqueLimit"));

        SpringSettings spring = settings.getSpringSettings();
        loadSpringSettings(tag, "springSettings", spring);
        spring.close();
    }
}