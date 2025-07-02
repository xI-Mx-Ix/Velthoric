package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.SwingTwistConstraint;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

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
        return createFromLoadedBodies(tag, objectManager, (b1, b2, t) -> {
            try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setSwingType(ESwingType.valueOf(t.getString("swingType")));
                settings.setPosition1(NbtUtil.getRVec3(t, "position1"));
                settings.setPosition2(NbtUtil.getRVec3(t, "position2"));
                settings.setTwistAxis1(NbtUtil.getVec3(t, "twistAxis1"));
                settings.setTwistAxis2(NbtUtil.getVec3(t, "twistAxis2"));
                settings.setPlaneAxis1(NbtUtil.getVec3(t, "planeAxis1"));
                settings.setPlaneAxis2(NbtUtil.getVec3(t, "planeAxis2"));
                settings.setNormalHalfConeAngle(t.getFloat("normalHalfConeAngle"));
                settings.setPlaneHalfConeAngle(t.getFloat("planeHalfConeAngle"));
                settings.setTwistMinAngle(t.getFloat("twistMinAngle"));
                settings.setTwistMaxAngle(t.getFloat("twistMaxAngle"));
                settings.setMaxFrictionTorque(t.getFloat("maxFrictionTorque"));
                NbtUtil.loadMotorSettings(t, "swingMotor", settings.getSwingMotorSettings());
                NbtUtil.loadMotorSettings(t, "twistMotor", settings.getTwistMotorSettings());
                return (SwingTwistConstraint) settings.create(b1, b2);
            }
        });
    }
}