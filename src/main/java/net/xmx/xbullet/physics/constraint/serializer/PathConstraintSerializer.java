package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.PathConstraint;
import com.github.stephengold.joltjni.PathConstraintPath;
import com.github.stephengold.joltjni.PathConstraintSettings;
import com.github.stephengold.joltjni.PathResult;
import com.github.stephengold.joltjni.StreamInWrapper;
import com.github.stephengold.joltjni.StreamOutWrapper;
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
    public void serialize(PathConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);

        if (builder.path != null && builder.path.hasAssignedNativeObject()) {
            try (StringStream stringStream = new StringStream();
                 StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
                builder.path.saveBinaryState(streamOut);
                buf.writeByteArray(stringStream.str().getBytes(StandardCharsets.ISO_8859_1));
            }
        } else {
            buf.writeByteArray(new byte[0]);
        }

        BufferUtil.putVec3(buf, builder.pathPosition);
        BufferUtil.putQuat(buf, builder.pathRotation);
        buf.writeFloat(builder.pathFraction);
        buf.writeEnum(builder.rotationConstraintType);
        buf.writeFloat(builder.maxFrictionForce);
        BufferUtil.putMotorSettings(buf, builder.positionMotorSettings);

        // Live state placeholders
        buf.writeFloat(0f); // Target Path Fraction
        buf.writeFloat(0f); // Target Velocity
        buf.writeEnum(EMotorState.Off); // Motor State
    }

    @Override
    public PathConstraintSettings createSettings(FriendlyByteBuf buf) {
        PathConstraintSettings s = new PathConstraintSettings();

        byte[] pathData = buf.readByteArray();
        if (pathData.length > 0) {
            String pathString = new String(pathData, StandardCharsets.ISO_8859_1);
            try (StringStream stringStream = new StringStream(pathString);
                 StreamInWrapper streamIn = new StreamInWrapper(stringStream);
                 PathResult result = PathConstraintPath.sRestoreFromBinaryState(streamIn)) {
                if (result.isValid()) {
                    try(PathConstraintPath path = result.get().getPtr()) {
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
        BufferUtil.loadMotorSettings(buf, s.getPositionMotorSettings());

        return s;
    }

    @Override
    public void applyLiveState(PathConstraint constraint, FriendlyByteBuf buf) {
        constraint.setTargetPathFraction(buf.readFloat());
        constraint.setTargetVelocity(buf.readFloat());
        constraint.setPositionMotorState(buf.readEnum(EMotorState.class));
    }
}