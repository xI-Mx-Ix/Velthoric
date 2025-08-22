package net.xmx.velthoric.physics.constraint.serializer;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import com.github.stephengold.joltjni.std.StringStream;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public final class ConstraintSerializerUtils {

    private ConstraintSerializerUtils() {}

    public static void saveVec3(Vec3Arg vec, ByteBuf buf) {
        buf.writeFloat(vec.getX());
        buf.writeFloat(vec.getY());
        buf.writeFloat(vec.getZ());
    }

    public static Vec3 loadVec3(ByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void saveRVec3(RVec3Arg vec, ByteBuf buf) {
        buf.writeDouble(vec.xx());
        buf.writeDouble(vec.yy());
        buf.writeDouble(vec.zz());
    }

    public static RVec3 loadRVec3(ByteBuf buf) {
        return new RVec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void saveSpring(SpringSettings spring, ByteBuf buf) {
        buf.writeFloat(spring.getFrequency());
        buf.writeFloat(spring.getDamping());
    }

    public static void loadSpring(SpringSettings spring, ByteBuf buf) {
        spring.setFrequency(buf.readFloat());
        spring.setDamping(buf.readFloat());
    }

    public static void saveMotor(MotorSettings motor, ByteBuf buf) {
        buf.writeBoolean(motor.isValid());
        if (motor.isValid()) {
            buf.writeFloat(motor.getMinForceLimit());
            buf.writeFloat(motor.getMaxForceLimit());
            buf.writeFloat(motor.getMinTorqueLimit());
            buf.writeFloat(motor.getMaxTorqueLimit());
            try (SpringSettings ss = motor.getSpringSettings()) {
                saveSpring(ss, buf);
            }
        }
    }

    public static void loadMotor(MotorSettings motor, ByteBuf buf) {
        if (buf.readBoolean()) {
            motor.setForceLimits(buf.readFloat(), buf.readFloat());
            motor.setTorqueLimits(buf.readFloat(), buf.readFloat());
            try (SpringSettings ss = motor.getSpringSettings()) {
                loadSpring(ss, buf);
            }
        }
    }

    public static void savePath(PathConstraintPath path, ByteBuf buf) {

        try (StringStream stringStream = new StringStream();
             StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {

            path.saveBinaryState(streamOut);

            byte[] pathData = stringStream.str().getBytes(StandardCharsets.ISO_8859_1);
            buf.writeInt(pathData.length);
            buf.writeBytes(pathData);
        }
    }

    public static PathConstraintPath loadPath(ByteBuf buf) {
        int length = buf.readInt();
        if (length == 0) {
            return new PathConstraintPathHermite();
        }
        byte[] pathData = new byte[length];
        buf.readBytes(pathData);

        String pathString = new String(pathData, StandardCharsets.ISO_8859_1);

        try (StringStream stringStream = new StringStream(pathString);
             StreamInWrapper streamIn = new StreamInWrapper(stringStream)) {

            try (PathResult result = PathConstraintPath.sRestoreFromBinaryState(streamIn)) {
                if (result.hasError()) {

                    System.err.println("Fehler beim Wiederherstellen des Pfades aus dem Binärzustand: " + result.getError());
                    return new PathConstraintPathHermite();
                }

                try (PathConstraintPathRef pathRef = result.get()) {

                    return pathRef.getPtr();
                }
            }
        }
    }
}