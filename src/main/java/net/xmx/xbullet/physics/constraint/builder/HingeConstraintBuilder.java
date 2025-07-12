package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class HingeConstraintBuilder extends ConstraintBuilder<HingeConstraintBuilder, HingeConstraint> {

    public EConstraintSpace space;
    public RVec3 point1;
    public RVec3 point2;
    public Vec3 hingeAxis1;
    public Vec3 hingeAxis2;
    public Vec3 normalAxis1;
    public Vec3 normalAxis2;
    public float limitsMin;
    public float limitsMax;
    public float maxFrictionTorque;
    public MotorSettings motorSettings;
    public SpringSettings limitsSpringSettings;

    public HingeConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:hinge";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);
        if (this.hingeAxis1 == null) this.hingeAxis1 = new Vec3(0, 1, 0); else this.hingeAxis1.set(0, 1, 0);
        if (this.hingeAxis2 == null) this.hingeAxis2 = new Vec3(0, 1, 0); else this.hingeAxis2.set(0, 1, 0);
        if (this.normalAxis1 == null) this.normalAxis1 = new Vec3(1, 0, 0); else this.normalAxis1.set(1, 0, 0);
        if (this.normalAxis2 == null) this.normalAxis2 = new Vec3(1, 0, 0); else this.normalAxis2.set(1, 0, 0);

        this.limitsMin = 1.0f;
        this.limitsMax = 0.0f;
        this.maxFrictionTorque = 0.0f;

        if (this.motorSettings == null) this.motorSettings = new MotorSettings();
        if (this.limitsSpringSettings == null) this.limitsSpringSettings = new SpringSettings();
    }

    public HingeConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public HingeConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }

    public HingeConstraintBuilder withHingeAxes(Vec3 axis1, Vec3 axis2) {
        this.hingeAxis1.set(axis1);
        this.hingeAxis2.set(axis2);
        return this;
    }

    public HingeConstraintBuilder withNormalAxes(Vec3 axis1, Vec3 axis2) {
        this.normalAxis1.set(axis1);
        this.normalAxis2.set(axis2);
        return this;
    }

    public HingeConstraintBuilder withLimits(float min, float max) {
        this.limitsMin = min;
        this.limitsMax = max;
        return this;
    }

    public HingeConstraintBuilder withMaxFrictionTorque(float torque) {
        this.maxFrictionTorque = torque;
        return this;
    }

    public HingeConstraintBuilder withMotorSettings(MotorSettings settings) {
        if (this.motorSettings == null) {
            this.motorSettings = new MotorSettings();
        }
        this.motorSettings.setForceLimits(settings.getMinForceLimit(), settings.getMaxForceLimit());
        this.motorSettings.setTorqueLimits(settings.getMinTorqueLimit(), settings.getMaxTorqueLimit());
        try (SpringSettings sourceSpring = settings.getSpringSettings();
             SpringSettings targetSpring = this.motorSettings.getSpringSettings()) {
            targetSpring.setMode(sourceSpring.getMode());
            targetSpring.setDamping(sourceSpring.getDamping());
            targetSpring.setFrequency(sourceSpring.getFrequency());
            targetSpring.setStiffness(sourceSpring.getStiffness());
        }
        return this;
    }

    public HingeConstraintBuilder withLimitsSpringSettings(SpringSettings settings) {
        if (this.limitsSpringSettings == null) {
            this.limitsSpringSettings = new SpringSettings();
        }
        this.limitsSpringSettings.setMode(settings.getMode());
        this.limitsSpringSettings.setDamping(settings.getDamping());
        this.limitsSpringSettings.setFrequency(settings.getFrequency());
        this.limitsSpringSettings.setStiffness(settings.getStiffness());
        return this;
    }
}