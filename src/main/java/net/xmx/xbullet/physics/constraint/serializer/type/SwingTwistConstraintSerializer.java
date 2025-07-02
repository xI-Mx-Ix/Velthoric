package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SwingTwistConstraintSerializer implements IConstraintSerializer<SwingTwistConstraint> {

    @Override
    public void save(SwingTwistConstraint constraint, CompoundTag tag) {
        try (SwingTwistConstraintSettings settings = (SwingTwistConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            tag.putString("swingType", settings.getSwingType().name());
            NbtUtil.putRVec3(tag, "position1", settings.getPosition1());
            NbtUtil.putRVec3(tag, "position2", settings.getPosition2());
            NbtUtil.putVec3(tag, "twistAxis1", settings.getTwistAxis1());
            NbtUtil.putVec3(tag, "twistAxis2", settings.getTwistAxis2());
            NbtUtil.putVec3(tag, "planeAxis1", settings.getPlaneAxis1());
            NbtUtil.putVec3(tag, "planeAxis2", settings.getPlaneAxis2());
            tag.putFloat("normalHalfConeAngle", settings.getNormalHalfConeAngle());
            tag.putFloat("planeHalfConeAngle", settings.getPlaneHalfConeAngle());
            tag.putFloat("twistMinAngle", settings.getTwistMinAngle());
            tag.putFloat("twistMaxAngle", settings.getTwistMaxAngle());
            tag.putFloat("maxFrictionTorque", settings.getMaxFrictionTorque());
            NbtUtil.putMotorSettings(tag, "swingMotor", settings.getSwingMotorSettings());
            NbtUtil.putMotorSettings(tag, "twistMotor", settings.getTwistMotorSettings());
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

            try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setSwingType(ESwingType.valueOf(tag.getString("swingType")));
                settings.setPosition1(NbtUtil.getRVec3(tag, "position1"));
                settings.setPosition2(NbtUtil.getRVec3(tag, "position2"));
                settings.setTwistAxis1(NbtUtil.getVec3(tag, "twistAxis1"));
                settings.setTwistAxis2(NbtUtil.getVec3(tag, "twistAxis2"));
                settings.setPlaneAxis1(NbtUtil.getVec3(tag, "planeAxis1"));
                settings.setPlaneAxis2(NbtUtil.getVec3(tag, "planeAxis2"));
                settings.setNormalHalfConeAngle(tag.getFloat("normalHalfConeAngle"));
                settings.setPlaneHalfConeAngle(tag.getFloat("planeHalfConeAngle"));
                settings.setTwistMinAngle(tag.getFloat("twistMinAngle"));
                settings.setTwistMaxAngle(tag.getFloat("twistMaxAngle"));
                settings.setMaxFrictionTorque(tag.getFloat("maxFrictionTorque"));
                NbtUtil.loadMotorSettings(tag, "swingMotor", settings.getSwingMotorSettings());
                NbtUtil.loadMotorSettings(tag, "twistMotor", settings.getTwistMotorSettings());

                return settings.create(b1, b2);
            }
        });
    }
}