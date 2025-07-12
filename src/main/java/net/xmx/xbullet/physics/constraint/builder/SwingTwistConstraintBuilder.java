package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.SwingTwistConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class SwingTwistConstraintBuilder extends ConstraintBuilder<SwingTwistConstraintBuilder, SwingTwistConstraint> {

    public EConstraintSpace space;
    public ESwingType swingType;
    public RVec3 position1;
    public RVec3 position2;
    public Vec3 twistAxis1;
    public Vec3 twistAxis2;
    public Vec3 planeAxis1;
    public Vec3 planeAxis2;
    public float normalHalfConeAngle;
    public float planeHalfConeAngle;
    public float twistMinAngle;
    public float twistMaxAngle;
    public float maxFrictionTorque;
    public MotorSettings swingMotorSettings;
    public MotorSettings twistMotorSettings;

    public SwingTwistConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:swing_twist";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;
        this.swingType = ESwingType.Cone;

        if (this.position1 == null) this.position1 = new RVec3(); else this.position1.set(0, 0, 0);
        if (this.position2 == null) this.position2 = new RVec3(); else this.position2.set(0, 0, 0);
        if (this.twistAxis1 == null) this.twistAxis1 = new Vec3(1, 0, 0); else this.twistAxis1.set(1, 0, 0);
        if (this.twistAxis2 == null) this.twistAxis2 = new Vec3(1, 0, 0); else this.twistAxis2.set(1, 0, 0);
        if (this.planeAxis1 == null) this.planeAxis1 = new Vec3(0, 1, 0); else this.planeAxis1.set(0, 1, 0);
        if (this.planeAxis2 == null) this.planeAxis2 = new Vec3(0, 1, 0); else this.planeAxis2.set(0, 1, 0);

        this.normalHalfConeAngle = (float) Math.PI;
        this.planeHalfConeAngle = (float) Math.PI;
        this.twistMinAngle = -(float) Math.PI;
        this.twistMaxAngle = (float) Math.PI;
        this.maxFrictionTorque = 0.0f;

        if (this.swingMotorSettings == null) this.swingMotorSettings = new MotorSettings();
        if (this.twistMotorSettings == null) this.twistMotorSettings = new MotorSettings();
    }

    public SwingTwistConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public SwingTwistConstraintBuilder withSwingType(ESwingType type) {
        this.swingType = type;
        return this;
    }

    public SwingTwistConstraintBuilder atPositions(RVec3 p1, RVec3 p2) {
        this.position1.set(p1);
        this.position2.set(p2);
        return this;
    }

    public SwingTwistConstraintBuilder withTwistAxes(Vec3 axis1, Vec3 axis2) {
        this.twistAxis1.set(axis1);
        this.twistAxis2.set(axis2);
        return this;
    }

    public SwingTwistConstraintBuilder withPlaneAxes(Vec3 axis1, Vec3 axis2) {
        this.planeAxis1.set(axis1);
        this.planeAxis2.set(axis2);
        return this;
    }

    public SwingTwistConstraintBuilder withSwingLimits(float normalAngle, float planeAngle) {
        this.normalHalfConeAngle = normalAngle;
        this.planeHalfConeAngle = planeAngle;
        return this;
    }

    public SwingTwistConstraintBuilder withTwistLimits(float min, float max) {
        this.twistMinAngle = min;
        this.twistMaxAngle = max;
        return this;
    }

    public SwingTwistConstraintBuilder withMaxFrictionTorque(float torque) {
        this.maxFrictionTorque = torque;
        return this;
    }

    public SwingTwistConstraintBuilder withSwingMotorSettings(MotorSettings settings) {
        if (this.swingMotorSettings == null) {
            this.swingMotorSettings = new MotorSettings();
        }
        this.swingMotorSettings.setForceLimits(settings.getMinForceLimit(), settings.getMaxForceLimit());
        this.swingMotorSettings.setTorqueLimits(settings.getMinTorqueLimit(), settings.getMaxTorqueLimit());
        try (SpringSettings sourceSpring = settings.getSpringSettings();
             SpringSettings targetSpring = this.swingMotorSettings.getSpringSettings()) {
            targetSpring.setMode(sourceSpring.getMode());
            targetSpring.setDamping(sourceSpring.getDamping());
            targetSpring.setFrequency(sourceSpring.getFrequency());
            targetSpring.setStiffness(sourceSpring.getStiffness());
        }
        return this;
    }

    public SwingTwistConstraintBuilder withTwistMotorSettings(MotorSettings settings) {
        if (this.twistMotorSettings == null) {
            this.twistMotorSettings = new MotorSettings();
        }
        this.twistMotorSettings.setForceLimits(settings.getMinForceLimit(), settings.getMaxForceLimit());
        this.twistMotorSettings.setTorqueLimits(settings.getMinTorqueLimit(), settings.getMaxTorqueLimit());
        try (SpringSettings sourceSpring = settings.getSpringSettings();
             SpringSettings targetSpring = this.twistMotorSettings.getSpringSettings()) {
            targetSpring.setMode(sourceSpring.getMode());
            targetSpring.setDamping(sourceSpring.getDamping());
            targetSpring.setFrequency(sourceSpring.getFrequency());
            targetSpring.setStiffness(sourceSpring.getStiffness());
        }
        return this;
    }
}