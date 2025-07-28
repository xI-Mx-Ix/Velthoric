package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class PathConstraintBuilder extends ConstraintBuilder<PathConstraintBuilder, PathConstraint> {

    private PathConstraintSettings settings;

    public PathConstraintBuilder() {
        this.reset();
    }

    public PathConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:path";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new PathConstraintSettings();
        try (MotorSettings motor = this.settings.getPositionMotorSettings()) {
            try (SpringSettings spring = motor.getSpringSettings()) {
                spring.setMode(ESpringMode.FrequencyAndDamping);
                spring.setFrequency(20.0f);
                spring.setDamping(1.0f);
            }
        }
    }

    public PathConstraintBuilder withPath(PathConstraintPath path) {
        this.settings.setPath(path);
        return this;
    }

    public PathConstraintBuilder withPathTransform(Vec3 position, Quat rotation) {
        this.settings.setPathPosition(position);
        this.settings.setPathRotation(rotation);
        return this;
    }

    public PathConstraintBuilder withInitialPathFraction(float fraction) {
        this.settings.setPathFraction(fraction);
        return this;
    }

    public PathConstraintBuilder withRotationConstraintType(EPathRotationConstraintType type) {
        this.settings.setRotationConstraintType(type);
        return this;
    }

    public PathConstraintBuilder withMaxFrictionForce(float force) {
        this.settings.setMaxFrictionForce(force);
        return this;
    }

    public PathConstraintBuilder withPositionMotorSettings(MotorSettings sourceSettings) {
        this.settings.setPositionMotorSettings(sourceSettings);
        return this;
    }
}