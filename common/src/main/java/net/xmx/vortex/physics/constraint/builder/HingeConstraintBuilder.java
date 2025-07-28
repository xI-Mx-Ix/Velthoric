package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class HingeConstraintBuilder extends ConstraintBuilder<HingeConstraintBuilder, HingeConstraint> {

    private HingeConstraintSettings settings;

    public HingeConstraintBuilder() {
        this.reset();
    }

    public HingeConstraintSettings getSettings() {
        return this.settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:hinge";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new HingeConstraintSettings();

        this.settings.setSpace(EConstraintSpace.LocalToBodyCOM);
        this.settings.setPoint1(new RVec3(0, 0, 0));
        this.settings.setPoint2(new RVec3(0, 0, 0));
        this.settings.setHingeAxis1(new Vec3(0, 1, 0));
        this.settings.setHingeAxis2(new Vec3(0, 1, 0));
        this.settings.setNormalAxis1(new Vec3(1, 0, 0));
        this.settings.setNormalAxis2(new Vec3(1, 0, 0));
        this.settings.setLimitsMin(-(float) Math.PI);
        this.settings.setLimitsMax((float) Math.PI);
        this.settings.setMaxFrictionTorque(0.0f);

        try (MotorSettings motor = this.settings.getMotorSettings()) {
            try (SpringSettings motorSpring = motor.getSpringSettings()) {
                motorSpring.setMode(ESpringMode.FrequencyAndDamping);
                motorSpring.setFrequency(20.0f);
                motorSpring.setDamping(1.0f);
            }
        }

        try (SpringSettings limitsSpring = this.settings.getLimitsSpringSettings()) {
            limitsSpring.setMode(ESpringMode.FrequencyAndDamping);
            limitsSpring.setFrequency(20.0f);
            limitsSpring.setDamping(1.0f);
        }
    }

    public HingeConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public HingeConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }

    public HingeConstraintBuilder withHingeAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setHingeAxis1(axis1);
        this.settings.setHingeAxis2(axis2);
        return this;
    }

    public HingeConstraintBuilder withNormalAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setNormalAxis1(axis1);
        this.settings.setNormalAxis2(axis2);
        return this;
    }

    public HingeConstraintBuilder withLimits(float min, float max) {
        this.settings.setLimitsMin(min);
        this.settings.setLimitsMax(max);
        return this;
    }

    public HingeConstraintBuilder withMaxFrictionTorque(float torque) {
        this.settings.setMaxFrictionTorque(torque);
        return this;
    }

    public HingeConstraintBuilder withMotorSettings(MotorSettings sourceSettings) {
        try (MotorSettings targetSettings = this.settings.getMotorSettings()) {
            targetSettings.setForceLimits(sourceSettings.getMinForceLimit(), sourceSettings.getMaxForceLimit());
            targetSettings.setTorqueLimits(sourceSettings.getMinTorqueLimit(), sourceSettings.getMaxTorqueLimit());
            try (SpringSettings sourceSpring = sourceSettings.getSpringSettings();
                 SpringSettings targetSpring = targetSettings.getSpringSettings()) {
                targetSpring.setMode(sourceSpring.getMode());
                targetSpring.setDamping(sourceSpring.getDamping());
                targetSpring.setFrequency(sourceSpring.getFrequency());
                targetSpring.setStiffness(sourceSpring.getStiffness());
            }
        }
        return this;
    }

    public HingeConstraintBuilder withLimitsSpringSettings(SpringSettings sourceSettings) {
        try (SpringSettings targetSettings = this.settings.getLimitsSpringSettings()) {
            targetSettings.setMode(sourceSettings.getMode());
            targetSettings.setDamping(sourceSettings.getDamping());
            targetSettings.setFrequency(sourceSettings.getFrequency());
            targetSettings.setStiffness(sourceSettings.getStiffness());
        }
        return this;
    }
}