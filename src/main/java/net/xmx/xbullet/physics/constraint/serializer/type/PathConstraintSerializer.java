package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class PathConstraintSerializer implements IConstraintSerializer<PathConstraint> {

    @Override
    public void save(PathConstraint constraint, CompoundTag tag) {
        try (PathConstraintSettings settings = (PathConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            PathConstraintPath path = settings.getPath();
            if (path != null && path.hasAssignedNativeObject()) {
                try (StringStream stringStream = new StringStream();
                     StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
                    path.saveBinaryState(streamOut);
                    tag.putByteArray("pathData", stringStream.str().getBytes(StandardCharsets.ISO_8859_1));
                }
            }
            NbtUtil.putVec3(tag, "pathPosition", settings.getPathPosition());
            NbtUtil.putQuat(tag, "pathRotation", settings.getPathRotation());
            tag.putFloat("pathFraction", settings.getPathFraction());
            tag.putString("rotationConstraintType", settings.getRotationConstraintType().name());
            tag.putFloat("maxFrictionForce", settings.getMaxFrictionForce());
            NbtUtil.putMotorSettings(tag, "positionMotorSettings", settings.getPositionMotorSettings());
            tag.putFloat("targetPathFraction", constraint.getTargetPathFraction());
            tag.putFloat("targetVelocity", constraint.getTargetVelocity());
            tag.putString("positionMotorState", constraint.getPositionMotorState().name());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (bodyInterface, b1Id, b2Id, t) -> {
            PathConstraintSettings settings = new PathConstraintSettings();
            try {
                if (t.contains("pathData")) {
                    byte[] pathData = t.getByteArray("pathData");
                    String pathString = new String(pathData, StandardCharsets.ISO_8859_1);
                    try (StringStream stringStream = new StringStream(pathString);
                         StreamInWrapper streamIn = new StreamInWrapper(stringStream);
                         PathResult result = PathConstraintPath.sRestoreFromBinaryState(streamIn)) {
                        if (result.isValid()) {
                            settings.setPath(result.get().getPtr());
                        }
                    }
                }
                settings.setPathPosition(NbtUtil.getVec3(t, "pathPosition"));
                settings.setPathRotation(NbtUtil.getQuat(t, "pathRotation"));
                settings.setPathFraction(t.getFloat("pathFraction"));
                settings.setRotationConstraintType(EPathRotationConstraintType.valueOf(t.getString("rotationConstraintType")));
                settings.setMaxFrictionForce(t.getFloat("maxFrictionForce"));
                NbtUtil.loadMotorSettings(t, "positionMotorSettings", settings.getPositionMotorSettings());
                PathConstraint constraint = (PathConstraint) bodyInterface.createConstraint(settings, b1Id, b2Id);
                if (constraint != null) {
                    constraint.setTargetPathFraction(t.getFloat("targetPathFraction"));
                    constraint.setTargetVelocity(t.getFloat("targetVelocity"));
                    constraint.setPositionMotorState(EMotorState.valueOf(t.getString("positionMotorState")));
                }
                return constraint;
            } finally {
                settings.close();
            }
        });
    }
}