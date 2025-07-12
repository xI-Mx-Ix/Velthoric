package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.PulleyConstraint;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class PulleyConstraintBuilder extends ConstraintBuilder<PulleyConstraintBuilder, PulleyConstraint> {

    public EConstraintSpace space;
    public RVec3 bodyPoint1;
    public RVec3 bodyPoint2;
    public RVec3 fixedPoint1;
    public RVec3 fixedPoint2;
    public float ratio;
    public float minLength;
    public float maxLength;

    public PulleyConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:pulley";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.WorldSpace;

        if (this.bodyPoint1 == null) this.bodyPoint1 = new RVec3(); else this.bodyPoint1.set(0, 0, 0);
        if (this.bodyPoint2 == null) this.bodyPoint2 = new RVec3(); else this.bodyPoint2.set(0, 0, 0);
        if (this.fixedPoint1 == null) this.fixedPoint1 = new RVec3(); else this.fixedPoint1.set(0, 0, 0);
        if (this.fixedPoint2 == null) this.fixedPoint2 = new RVec3(); else this.fixedPoint2.set(0, 0, 0);

        this.ratio = 1.0f;
        this.minLength = 0.0f;
        this.maxLength = -1.0f;
    }

    public PulleyConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public PulleyConstraintBuilder withBodyPoints(RVec3 p1, RVec3 p2) {
        this.bodyPoint1.set(p1);
        this.bodyPoint2.set(p2);
        return this;
    }

    public PulleyConstraintBuilder withFixedPoints(RVec3 p1, RVec3 p2) {
        this.fixedPoint1.set(p1);
        this.fixedPoint2.set(p2);
        return this;
    }

    public PulleyConstraintBuilder withRatio(float ratio) {
        this.ratio = ratio;
        return this;
    }

    public PulleyConstraintBuilder withLengthRange(float min, float max) {
        this.minLength = min;
        this.maxLength = max;
        return this;
    }
}