package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class SliderConstraintBuilder extends ConstraintBuilder<SliderConstraintBuilder, SliderConstraint> {

    private SliderConstraintSettings settings;

    public SliderConstraintBuilder() {
        this.reset();
    }

    public SliderConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:slider";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new SliderConstraintSettings();

        try (MotorSettings motor = this.settings.getMotorSettings()) {
            try (SpringSettings spring = motor.getSpringSettings()) {
                spring.setMode(ESpringMode.FrequencyAndDamping);
                spring.setFrequency(20.0f);
                spring.setDamping(1.0f);
            }
        }

        try (SpringSettings spring = this.settings.getLimitsSpringSettings()) {
            spring.setMode(ESpringMode.FrequencyAndDamping);
            spring.setFrequency(20.0f);
            spring.setDamping(1.0f);
        }
    }

    public SliderConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public SliderConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }

    public SliderConstraintBuilder withSliderAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setSliderAxis1(axis1);
        this.settings.setSliderAxis2(axis2);
        return this;
    }

    public SliderConstraintBuilder withNormalAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setNormalAxis1(axis1);
        this.settings.setNormalAxis2(axis2);
        return this;
    }

    public SliderConstraintBuilder withLimits(float min, float max) {
        this.settings.setLimitsMin(min);
        this.settings.setLimitsMax(max);
        return this;
    }

    public SliderConstraintBuilder withMaxFrictionForce(float force) {
        this.settings.setMaxFrictionForce(force);
        return this;
    }

    public SliderConstraintBuilder withMotorSettings(MotorSettings sourceSettings) {
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

    public SliderConstraintBuilder withLimitsSpringSettings(SpringSettings sourceSettings) {
        try (SpringSettings targetSettings = this.settings.getLimitsSpringSettings()) {
            targetSettings.setMode(sourceSettings.getMode());
            targetSettings.setDamping(sourceSettings.getDamping());
            targetSettings.setFrequency(sourceSettings.getFrequency());
            targetSettings.setStiffness(sourceSettings.getStiffness());
        }
        return this;
    }
}