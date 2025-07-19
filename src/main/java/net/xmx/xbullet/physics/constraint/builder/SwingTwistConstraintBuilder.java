package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class SwingTwistConstraintBuilder extends ConstraintBuilder<SwingTwistConstraintBuilder, SwingTwistConstraint> {

    private SwingTwistConstraintSettings settings;

    public SwingTwistConstraintBuilder() {
        this.reset();
    }

    public SwingTwistConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "xbullet:swing_twist";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new SwingTwistConstraintSettings();

        try (MotorSettings motor = this.settings.getSwingMotorSettings()) {
            try (SpringSettings spring = motor.getSpringSettings()) {
                spring.setMode(ESpringMode.FrequencyAndDamping);
                spring.setFrequency(20.0f);
                spring.setDamping(1.0f);
            }
        }

        try (MotorSettings motor = this.settings.getTwistMotorSettings()) {
            try (SpringSettings spring = motor.getSpringSettings()) {
                spring.setMode(ESpringMode.FrequencyAndDamping);
                spring.setFrequency(20.0f);
                spring.setDamping(1.0f);
            }
        }
    }

    public SwingTwistConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public SwingTwistConstraintBuilder withSwingType(ESwingType type) {
        this.settings.setSwingType(type);
        return this;
    }

    public SwingTwistConstraintBuilder atPositions(RVec3 p1, RVec3 p2) {
        this.settings.setPosition1(p1);
        this.settings.setPosition2(p2);
        return this;
    }

    public SwingTwistConstraintBuilder withTwistAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setTwistAxis1(axis1);
        this.settings.setTwistAxis2(axis2);
        return this;
    }

    public SwingTwistConstraintBuilder withPlaneAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setPlaneAxis1(axis1);
        this.settings.setPlaneAxis2(axis2);
        return this;
    }

    public SwingTwistConstraintBuilder withSwingLimits(float normalAngle, float planeAngle) {
        this.settings.setNormalHalfConeAngle(normalAngle);
        this.settings.setPlaneHalfConeAngle(planeAngle);
        return this;
    }

    public SwingTwistConstraintBuilder withTwistLimits(float min, float max) {
        this.settings.setTwistMinAngle(min);
        this.settings.setTwistMaxAngle(max);
        return this;
    }

    public SwingTwistConstraintBuilder withMaxFrictionTorque(float torque) {
        this.settings.setMaxFrictionTorque(torque);
        return this;
    }

    public SwingTwistConstraintBuilder withSwingMotorSettings(MotorSettings sourceSettings) {
        this.settings.setSwingMotorSettings(sourceSettings);
        return this;
    }

    public SwingTwistConstraintBuilder withTwistMotorSettings(MotorSettings sourceSettings) {
        this.settings.setTwistMotorSettings(sourceSettings);
        return this;
    }
}