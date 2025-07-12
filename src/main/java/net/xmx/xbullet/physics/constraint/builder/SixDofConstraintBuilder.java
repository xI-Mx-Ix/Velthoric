package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

import java.util.EnumMap;

public class SixDofConstraintBuilder extends ConstraintBuilder<SixDofConstraintBuilder, SixDofConstraint> {

    public enum AxisState { FREE, LIMITED, FIXED }

    public EConstraintSpace space;
    public RVec3 position1;
    public RVec3 position2;
    public Vec3 axisX1;
    public Vec3 axisY1;
    public Vec3 axisX2;
    public Vec3 axisY2;
    public ESwingType swingType;
    public final EnumMap<EAxis, AxisState> axisStates = new EnumMap<>(EAxis.class);
    public final EnumMap<EAxis, Float> limitsMin = new EnumMap<>(EAxis.class);
    public final EnumMap<EAxis, Float> limitsMax = new EnumMap<>(EAxis.class);
    public final EnumMap<EAxis, Float> maxFriction = new EnumMap<>(EAxis.class);
    public final EnumMap<EAxis, MotorSettings> motorSettings = new EnumMap<>(EAxis.class);
    public final EnumMap<EAxis, SpringSettings> limitsSpringSettings = new EnumMap<>(EAxis.class);

    public SixDofConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:six_dof";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;
        this.swingType = ESwingType.Cone;

        if (this.position1 == null) this.position1 = new RVec3(); else this.position1.set(0, 0, 0);
        if (this.position2 == null) this.position2 = new RVec3(); else this.position2.set(0, 0, 0);
        if (this.axisX1 == null) this.axisX1 = new Vec3(1, 0, 0); else this.axisX1.set(1, 0, 0);
        if (this.axisY1 == null) this.axisY1 = new Vec3(0, 1, 0); else this.axisY1.set(0, 1, 0);
        if (this.axisX2 == null) this.axisX2 = new Vec3(1, 0, 0); else this.axisX2.set(1, 0, 0);
        if (this.axisY2 == null) this.axisY2 = new Vec3(0, 1, 0); else this.axisY2.set(0, 1, 0);

        for (EAxis axis : EAxis.values()) {
            this.axisStates.put(axis, AxisState.FREE);
            this.limitsMin.put(axis, 1.0f);
            this.limitsMax.put(axis, 0.0f);
            this.maxFriction.put(axis, 0.0f);
            if (this.motorSettings.get(axis) == null) this.motorSettings.put(axis, new MotorSettings());
            if (this.limitsSpringSettings.get(axis) == null) this.limitsSpringSettings.put(axis, new SpringSettings());
        }
    }

    public SixDofConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public SixDofConstraintBuilder atPositions(RVec3 p1, RVec3 p2) {
        this.position1.set(p1);
        this.position2.set(p2);
        return this;
    }

    public SixDofConstraintBuilder withAxes1(Vec3 x, Vec3 y) {
        this.axisX1.set(x);
        this.axisY1.set(y);
        return this;
    }

    public SixDofConstraintBuilder withAxes2(Vec3 x, Vec3 y) {
        this.axisX2.set(x);
        this.axisY2.set(y);
        return this;
    }

    public SixDofConstraintBuilder withSwingType(ESwingType type) {
        this.swingType = type;
        return this;
    }

    public SixDofConstraintBuilder setAxisAsFree(EAxis axis) {
        this.axisStates.put(axis, AxisState.FREE);
        return this;
    }

    public SixDofConstraintBuilder setAxisAsFixed(EAxis axis) {
        this.axisStates.put(axis, AxisState.FIXED);
        return this;
    }

    public SixDofConstraintBuilder setAxisAsLimited(EAxis axis, float min, float max) {
        this.axisStates.put(axis, AxisState.LIMITED);
        this.limitsMin.put(axis, min);
        this.limitsMax.put(axis, max);
        return this;
    }

    public SixDofConstraintBuilder withFriction(EAxis axis, float friction) {
        this.maxFriction.put(axis, friction);
        return this;
    }

    public SixDofConstraintBuilder withMotor(EAxis axis, MotorSettings settings) {
        MotorSettings target = this.motorSettings.computeIfAbsent(axis, k -> new MotorSettings());
        target.setForceLimits(settings.getMinForceLimit(), settings.getMaxForceLimit());
        target.setTorqueLimits(settings.getMinTorqueLimit(), settings.getMaxTorqueLimit());
        try (SpringSettings sourceSpring = settings.getSpringSettings(); SpringSettings targetSpring = target.getSpringSettings()) {
            targetSpring.setMode(sourceSpring.getMode());
            targetSpring.setDamping(sourceSpring.getDamping());
            targetSpring.setFrequency(sourceSpring.getFrequency());
            targetSpring.setStiffness(sourceSpring.getStiffness());
        }
        return this;
    }

    public SixDofConstraintBuilder withLimitsSpring(EAxis axis, SpringSettings settings) {
        SpringSettings target = this.limitsSpringSettings.computeIfAbsent(axis, k -> new SpringSettings());
        target.setMode(settings.getMode());
        target.setDamping(settings.getDamping());
        target.setFrequency(settings.getFrequency());
        target.setStiffness(settings.getStiffness());
        return this;
    }
}