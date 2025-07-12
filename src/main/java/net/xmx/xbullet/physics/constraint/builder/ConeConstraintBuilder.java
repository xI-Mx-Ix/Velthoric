package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.ConeConstraint;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class ConeConstraintBuilder extends ConstraintBuilder<ConeConstraintBuilder, ConeConstraint> {

    public EConstraintSpace space;
    public RVec3 point1;
    public RVec3 point2;
    public Vec3 twistAxis1;
    public Vec3 twistAxis2;
    public float halfConeAngle;

    public ConeConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:cone";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);
        if (this.twistAxis1 == null) this.twistAxis1 = new Vec3(1, 0, 0); else this.twistAxis1.set(1, 0, 0);
        if (this.twistAxis2 == null) this.twistAxis2 = new Vec3(1, 0, 0); else this.twistAxis2.set(1, 0, 0);

        this.halfConeAngle = 0f;
    }

    public ConeConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public ConeConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }

    public ConeConstraintBuilder withTwistAxes(Vec3 axis1, Vec3 axis2) {
        this.twistAxis1.set(axis1);
        this.twistAxis2.set(axis2);
        return this;
    }

    public ConeConstraintBuilder withHalfConeAngle(float angle) {
        this.halfConeAngle = angle;
        return this;
    }
}