package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class DistanceConstraintBuilder extends ConstraintBuilder<DistanceConstraintBuilder, DistanceConstraint> {

    private DistanceConstraintSettings settings;

    public DistanceConstraintBuilder() {
        this.reset();
    }

    public DistanceConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:distance";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new DistanceConstraintSettings();
        try (SpringSettings limitsSpring = this.settings.getLimitsSpringSettings()) {
            limitsSpring.setMode(ESpringMode.FrequencyAndDamping);
            limitsSpring.setFrequency(20.0f);
            limitsSpring.setDamping(1.0f);
        }
    }

    public DistanceConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public DistanceConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }

    public DistanceConstraintBuilder withDistance(float distance) {
        this.settings.setMinDistance(distance);
        this.settings.setMaxDistance(distance);
        return this;
    }

    public DistanceConstraintBuilder withDistanceRange(float min, float max) {
        this.settings.setMinDistance(min);
        this.settings.setMaxDistance(max);
        return this;
    }

    public DistanceConstraintBuilder withLimitsSpringSettings(SpringSettings sourceSettings) {
        try (SpringSettings targetSettings = this.settings.getLimitsSpringSettings()) {
            targetSettings.setMode(sourceSettings.getMode());
            targetSettings.setDamping(sourceSettings.getDamping());
            targetSettings.setFrequency(sourceSettings.getFrequency());
            targetSettings.setStiffness(sourceSettings.getStiffness());
        }
        return this;
    }
}