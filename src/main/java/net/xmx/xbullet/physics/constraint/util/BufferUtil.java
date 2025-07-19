package net.xmx.xbullet.physics.constraint.util;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.minecraft.network.FriendlyByteBuf;

public final class BufferUtil {

    private BufferUtil() {
    }

    public static void putVec3(FriendlyByteBuf buf, Vec3 vec) {
        if (vec == null) {
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
        } else {
            buf.writeFloat(vec.getX());
            buf.writeFloat(vec.getY());
            buf.writeFloat(vec.getZ());
        }
    }

    public static Vec3 getVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void putRVec3(FriendlyByteBuf buf, RVec3 vec) {
        if (vec == null) {
            buf.writeDouble(0d);
            buf.writeDouble(0d);
            buf.writeDouble(0d);
        } else {
            buf.writeDouble(vec.xx());
            buf.writeDouble(vec.yy());
            buf.writeDouble(vec.zz());
        }
    }

    public static RVec3 getRVec3(FriendlyByteBuf buf) {
        return new RVec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void putQuat(FriendlyByteBuf buf, Quat quat) {
        if (quat == null) {
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeFloat(1f);
        } else {
            buf.writeFloat(quat.getX());
            buf.writeFloat(quat.getY());
            buf.writeFloat(quat.getZ());
            buf.writeFloat(quat.getW());
        }
    }

    public static Quat getQuat(FriendlyByteBuf buf) {
        return new Quat(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void putSpringSettings(FriendlyByteBuf buf, SpringSettings settings) {
        if (settings == null) {

            buf.writeEnum(ESpringMode.FrequencyAndDamping);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
        } else {

            buf.writeEnum(settings.getMode());
            buf.writeFloat(settings.getFrequency());
            buf.writeFloat(settings.getDamping());
            buf.writeFloat(settings.getStiffness());
        }
    }

    public static void loadSpringSettings(FriendlyByteBuf buf, SpringSettings settings) {
        ESpringMode mode = buf.readEnum(ESpringMode.class);
        float frequency = buf.readFloat();
        float damping = buf.readFloat();
        float stiffness = buf.readFloat();

        settings.setMode(mode);

        if (mode == ESpringMode.FrequencyAndDamping) {
            settings.setFrequency(frequency);
            settings.setDamping(damping);
        } else {
            settings.setStiffness(stiffness);
            settings.setDamping(damping);
        }
    }

    public static void putMotorSettings(FriendlyByteBuf buf, MotorSettings settings) {
        if (settings == null) {

            buf.writeFloat(Float.MAX_VALUE);
            buf.writeFloat(-Float.MAX_VALUE);
            buf.writeFloat(Float.MAX_VALUE);
            buf.writeFloat(-Float.MAX_VALUE);

            putSpringSettings(buf, null);
        } else {
            buf.writeFloat(settings.getMaxForceLimit());
            buf.writeFloat(settings.getMinForceLimit());
            buf.writeFloat(settings.getMaxTorqueLimit());
            buf.writeFloat(settings.getMinTorqueLimit());

            try (SpringSettings spring = settings.getSpringSettings()) {
                putSpringSettings(buf, spring);
            }
        }
    }

    public static void loadMotorSettings(FriendlyByteBuf buf, MotorSettings settings) {
        settings.setMaxForceLimit(buf.readFloat());
        settings.setMinForceLimit(buf.readFloat());
        settings.setMaxTorqueLimit(buf.readFloat());
        settings.setMinTorqueLimit(buf.readFloat());

        try (SpringSettings spring = settings.getSpringSettings()) {

            loadSpringSettings(buf, spring);
        }
    }
}