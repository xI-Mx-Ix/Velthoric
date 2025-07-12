package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.PathConstraint;
import com.github.stephengold.joltjni.PathConstraintPath;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class PathConstraintBuilder extends ConstraintBuilder<PathConstraintBuilder, PathConstraint> {

    public PathConstraintPath path;
    public Vec3 pathPosition;
    public Quat pathRotation;
    public float pathFraction;
    public EPathRotationConstraintType rotationConstraintType;
    public float maxFrictionForce;
    public MotorSettings positionMotorSettings;

    public PathConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:path";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;

        this.path = null;

        if (this.pathPosition == null) {
            this.pathPosition = new Vec3();
        } else {
            this.pathPosition.loadZero();
        }

        if (this.pathRotation == null) {
            this.pathRotation = new Quat();
        } else {
            this.pathRotation.loadIdentity();
        }

        this.pathFraction = 0f;
        this.rotationConstraintType = EPathRotationConstraintType.Free;
        this.maxFrictionForce = 0f;

        if (this.positionMotorSettings == null) {
            this.positionMotorSettings = new MotorSettings();
        }
    }

    public PathConstraintBuilder withPath(PathConstraintPath path) {
        this.path = path;
        return this;
    }

    public PathConstraintBuilder withPathTransform(Vec3 position, Quat rotation) {
        this.pathPosition.set(position);
        this.pathRotation.set(rotation);
        return this;
    }

    public PathConstraintBuilder withInitialPathFraction(float fraction) {
        this.pathFraction = fraction;
        return this;
    }

    public PathConstraintBuilder withRotationConstraintType(EPathRotationConstraintType type) {
        this.rotationConstraintType = type;
        return this;
    }

    public PathConstraintBuilder withMaxFrictionForce(float force) {
        this.maxFrictionForce = force;
        return this;
    }

    public PathConstraintBuilder withPositionMotorSettings(MotorSettings settings) {
        if (this.positionMotorSettings == null) {
            this.positionMotorSettings = new MotorSettings();
        }
        this.positionMotorSettings.setForceLimits(settings.getMinForceLimit(), settings.getMaxForceLimit());
        this.positionMotorSettings.setTorqueLimits(settings.getMinTorqueLimit(), settings.getMaxTorqueLimit());
        try (SpringSettings sourceSpring = settings.getSpringSettings();
             SpringSettings targetSpring = this.positionMotorSettings.getSpringSettings()) {
            targetSpring.setMode(sourceSpring.getMode());
            targetSpring.setDamping(sourceSpring.getDamping());
            targetSpring.setFrequency(sourceSpring.getFrequency());
            targetSpring.setStiffness(sourceSpring.getStiffness());
        }
        return this;
    }
}