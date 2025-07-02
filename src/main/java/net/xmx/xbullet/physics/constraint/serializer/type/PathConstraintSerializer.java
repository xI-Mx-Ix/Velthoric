package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        UUID[] bodyIds = loadBodyIds(tag);
        if (bodyIds == null) return CompletableFuture.completedFuture(null);

        CompletableFuture<IPhysicsObject> future1 = objectManager.getOrLoadObject(bodyIds[0]);
        CompletableFuture<IPhysicsObject> future2 = objectManager.getOrLoadObject(bodyIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();

        return CompletableFuture.allOf(future1, future2).thenApplyAsync(v -> {
            IPhysicsObject obj1 = future1.join();
            IPhysicsObject obj2 = future2.join();
            if (obj1 == null || obj2 == null || physicsWorld == null) return null;

            int bodyId1 = obj1.getBodyId();
            int bodyId2 = obj2.getBodyId();

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) return null;

            Body b1 = new Body(bodyId1);
            Body b2 = (bodyInterface.getMotionType(bodyId2) == EMotionType.Static) ? Body.sFixedToWorld() : new Body(bodyId2);

            try (PathConstraintSettings settings = new PathConstraintSettings()) {
                if (tag.contains("pathData")) {
                    byte[] pathData = tag.getByteArray("pathData");
                    String pathString = new String(pathData, StandardCharsets.ISO_8859_1);
                    try (StringStream stringStream = new StringStream(pathString);
                         StreamInWrapper streamIn = new StreamInWrapper(stringStream);
                         PathResult result = PathConstraintPath.sRestoreFromBinaryState(streamIn)) {
                        if (result.isValid()) {
                            settings.setPath(result.get().getPtr());
                        }
                    }
                }

                settings.setPathPosition(NbtUtil.getVec3(tag, "pathPosition"));
                settings.setPathRotation(NbtUtil.getQuat(tag, "pathRotation"));
                settings.setPathFraction(tag.getFloat("pathFraction"));
                settings.setRotationConstraintType(EPathRotationConstraintType.valueOf(tag.getString("rotationConstraintType")));
                settings.setMaxFrictionForce(tag.getFloat("maxFrictionForce"));
                NbtUtil.loadMotorSettings(tag, "positionMotorSettings", settings.getPositionMotorSettings());

                PathConstraint constraint = (PathConstraint) settings.create(b1, b2);

                if (constraint != null) {
                    constraint.setTargetPathFraction(tag.getFloat("targetPathFraction"));
                    constraint.setTargetVelocity(tag.getFloat("targetVelocity"));
                    constraint.setPositionMotorState(EMotorState.valueOf(tag.getString("positionMotorState")));
                }
                return constraint;
            }
        });
    }
}