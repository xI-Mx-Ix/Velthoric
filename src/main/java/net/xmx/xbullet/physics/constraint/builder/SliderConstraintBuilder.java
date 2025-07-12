package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class SliderConstraintBuilder extends ConstraintBuilder<SliderConstraintBuilder, SliderConstraint> {

    public EConstraintSpace space;
    public RVec3 point1;
    public RVec3 point2;
    public Vec3 sliderAxis1;
    public Vec3 sliderAxis2;
    public Vec3 normalAxis1;
    public Vec3 normalAxis2;
    public float limitsMin;
    public float limitsMax;
    public float maxFrictionForce;
    public MotorSettings motorSettings;
    public SpringSettings limitsSpringSettings;

    public SliderConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:slider";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);
        if (this.sliderAxis1 == null) this.sliderAxis1 = new Vec3(1, 0, 0); else this.sliderAxis1.set(1, 0, 0);
        if (this.sliderAxis2 == null) this.sliderAxis2 = new Vec3(1, 0, 0); else this.sliderAxis2.set(1, 0, 0);
        if (this.normalAxis1 == null) this.normalAxis1 = new Vec3(0, 1, 0); else this.normalAxis1.set(0, 1, 0);
        if (this.normalAxis2 == null) this.normalAxis2 = new Vec3(0, 1, 0); else this.normalAxis2.set(0, 1, 0);

        this.limitsMin = 1.0f;
        this.limitsMax = 0.0f;
        this.maxFrictionForce = 0.0f;

        if (this.motorSettings == null) this.motorSettings = new MotorSettings();
        if (this.limitsSpringSettings == null) this.limitsSpringSettings = new SpringSettings();
    }

    public SliderConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public SliderConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }

    public SliderConstraintBuilder withSliderAxes(Vec3 axis1, Vec3 axis2) {
        this.sliderAxis1.set(axis1);
        this.sliderAxis2.set(axis2);
        return this;
    }

    public SliderConstraintBuilder withNormalAxes(Vec3 axis1, Vec3 axis2) {
        this.normalAxis1.set(axis1);
        this.normalAxis2.set(axis2);
        return this;
    }

    public SliderConstraintBuilder withLimits(float min, float max) {
        this.limitsMin = min;
        this.limitsMax = max;
        return this;
    }

    public SliderConstraintBuilder withMaxFrictionForce(float force) {
        this.maxFrictionForce = force;
        return this;
    }

    public SliderConstraintBuilder withMotorSettings(MotorSettings settings) {
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

    public SliderConstraintBuilder withLimitsSpringSettings(SpringSettings settings) {
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