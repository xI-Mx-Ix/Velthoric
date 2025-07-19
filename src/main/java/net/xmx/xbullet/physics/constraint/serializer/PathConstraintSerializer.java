package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.PathConstraint;
import com.github.stephengold.joltjni.PathConstraintPath;
import com.github.stephengold.joltjni.PathConstraintSettings;
import com.github.stephengold.joltjni.PathResult;
import com.github.stephengold.joltjni.StreamInWrapper;
import com.github.stephengold.joltjni.StreamOutWrapper;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.PathConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;
import java.nio.charset.StandardCharsets;

public class PathConstraintSerializer implements ConstraintSerializer<PathConstraintBuilder, PathConstraint, PathConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:path";
    }

    @Override
    public void serializeSettings(PathConstraintBuilder builder, FriendlyByteBuf buf) {
        PathConstraintSettings settings = builder.getSettings();
        try (PathConstraintPath path = settings.getPath();
             StringStream stringStream = new StringStream();
             StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
            if (path != null && path.hasAssignedNativeObject()) {
                path.saveBinaryState(streamOut);
                buf.writeBoolean(true);
                byte[] data = stringStream.str().getBytes(StandardCharsets.ISO_8859_1);
                buf.writeByteArray(data);
            } else {
                buf.writeBoolean(false);
            }
        }
        BufferUtil.putVec3(buf, settings.getPathPosition());
        BufferUtil.putQuat(buf, settings.getPathRotation());
        buf.writeFloat(settings.getPathFraction());
        buf.writeEnum(settings.getRotationConstraintType());
        buf.writeFloat(settings.getMaxFrictionForce());
        try (MotorSettings motor = settings.getPositionMotorSettings()) {
            BufferUtil.putMotorSettings(buf, motor);
        }
    }

    @Override
    public PathConstraintSettings createSettings(FriendlyByteBuf buf) {
        PathConstraintSettings s = new PathConstraintSettings();
        if (buf.readBoolean()) {
            byte[] pathData = buf.readByteArray();
            String pathString = new String(pathData, StandardCharsets.ISO_8859_1);
            try (StringStream stringStream = new StringStream(pathString);
                 StreamInWrapper streamIn = new StreamInWrapper(stringStream);
                 PathResult result = PathConstraintPath.sRestoreFromBinaryState(streamIn)) {
                if (result.isValid()) {
                    try (PathConstraintPath path = result.get().getPtr()) {
                        s.setPath(path);
                    }
                }
            }
        }
        s.setPathPosition(BufferUtil.getVec3(buf));
        s.setPathRotation(BufferUtil.getQuat(buf));
        s.setPathFraction(buf.readFloat());
        s.setRotationConstraintType(buf.readEnum(EPathRotationConstraintType.class));
        s.setMaxFrictionForce(buf.readFloat());
        try (MotorSettings motor = s.getPositionMotorSettings()) {
            BufferUtil.loadMotorSettings(buf, motor);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof PathConstraint path) {
            buf.writeFloat(path.getTargetPathFraction());
            buf.writeFloat(path.getTargetVelocity());
            buf.writeEnum(path.getPositionMotorState());
            try (MotorSettings motor = path.getPositionMotorSettings()) {
                BufferUtil.putMotorSettings(buf, motor);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof PathConstraint path) {
            path.setTargetPathFraction(buf.readFloat());
            path.setTargetVelocity(buf.readFloat());
            path.setPositionMotorState(buf.readEnum(EMotorState.class));
            try (MotorSettings motor = path.getPositionMotorSettings()) {
                BufferUtil.loadMotorSettings(buf, motor);
            }
        }
    }
}