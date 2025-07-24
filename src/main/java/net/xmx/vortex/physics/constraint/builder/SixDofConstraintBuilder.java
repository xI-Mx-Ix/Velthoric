package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class SixDofConstraintBuilder extends ConstraintBuilder<SixDofConstraintBuilder, SixDofConstraint> {

    private SixDofConstraintSettings settings;

    public enum AxisState {
        FREE, LIMITED, FIXED
    }

    public SixDofConstraintBuilder() {
        this.reset();
    }

    public SixDofConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:six_dof";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new SixDofConstraintSettings();
        for (EAxis axis : EAxis.values()) {
            try (MotorSettings ms = settings.getMotorSettings(axis)) {
                try (SpringSettings ss = ms.getSpringSettings()) {
                    ss.setMode(ESpringMode.FrequencyAndDamping);
                    ss.setFrequency(20.0f);
                    ss.setDamping(1.0f);
                }
            }
            try (SpringSettings ss = settings.getLimitsSpringSettings(axis)) {
                ss.setMode(ESpringMode.FrequencyAndDamping);
                ss.setFrequency(20.0f);
                ss.setDamping(1.0f);
            }
        }
    }

    public SixDofConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public SixDofConstraintBuilder atPositions(RVec3 p1, RVec3 p2) {
        this.settings.setPosition1(p1);
        this.settings.setPosition2(p2);
        return this;
    }

    public SixDofConstraintBuilder withAxes1(Vec3 x, Vec3 y) {
        this.settings.setAxisX1(x);
        this.settings.setAxisY1(y);
        return this;
    }

    public SixDofConstraintBuilder withAxes2(Vec3 x, Vec3 y) {
        this.settings.setAxisX2(x);
        this.settings.setAxisY2(y);
        return this;
    }

    public SixDofConstraintBuilder withSwingType(ESwingType type) {
        this.settings.setSwingType(type);
        return this;
    }

    public SixDofConstraintBuilder setAxisAsFree(EAxis axis) {
        this.settings.makeFreeAxis(axis);
        return this;
    }

    public SixDofConstraintBuilder setAxisAsFixed(EAxis axis) {
        this.settings.makeFixedAxis(axis);
        return this;
    }

    public SixDofConstraintBuilder setAxisAsLimited(EAxis axis, float min, float max) {
        this.settings.setLimitedAxis(axis, min, max);
        return this;
    }

    public SixDofConstraintBuilder withFriction(EAxis axis, float friction) {
        this.settings.setMaxFriction(axis, friction);
        return this;
    }

    public SixDofConstraintBuilder withMotor(EAxis axis, MotorSettings sourceSettings) {
        try (MotorSettings targetSettings = this.settings.getMotorSettings(axis)) {
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

    public SixDofConstraintBuilder withLimitsSpring(EAxis axis, SpringSettings sourceSettings) {
        try (SpringSettings targetSettings = this.settings.getLimitsSpringSettings(axis)) {
            targetSettings.setMode(sourceSettings.getMode());
            targetSettings.setDamping(sourceSettings.getDamping());
            targetSettings.setFrequency(sourceSettings.getFrequency());
            targetSettings.setStiffness(sourceSettings.getStiffness());
        }
        return this;
    }
}