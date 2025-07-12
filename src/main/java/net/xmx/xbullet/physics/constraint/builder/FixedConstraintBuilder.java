package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.FixedConstraint;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class FixedConstraintBuilder extends ConstraintBuilder<FixedConstraintBuilder, FixedConstraint> {

    public EConstraintSpace space;
    public boolean autoDetectPoint;
    public RVec3 point1;
    public RVec3 point2;
    public Vec3 axisX1;
    public Vec3 axisY1;
    public Vec3 axisX2;
    public Vec3 axisY2;

    public FixedConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:fixed";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;
        this.autoDetectPoint = false;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);
        if (this.axisX1 == null) this.axisX1 = new Vec3(1, 0, 0); else this.axisX1.set(1, 0, 0);
        if (this.axisY1 == null) this.axisY1 = new Vec3(0, 1, 0); else this.axisY1.set(0, 1, 0);
        if (this.axisX2 == null) this.axisX2 = new Vec3(1, 0, 0); else this.axisX2.set(1, 0, 0);
        if (this.axisY2 == null) this.axisY2 = new Vec3(0, 1, 0); else this.axisY2.set(0, 1, 0);
    }

    public FixedConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public FixedConstraintBuilder withAutoDetectPoint(boolean autoDetect) {
        this.autoDetectPoint = autoDetect;
        return this;
    }

    public FixedConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }

    public FixedConstraintBuilder withAxes1(Vec3 x1, Vec3 y1) {
        this.axisX1.set(x1);
        this.axisY1.set(y1);
        return this;
    }

    public FixedConstraintBuilder withAxes2(Vec3 x2, Vec3 y2) {
        this.axisX2.set(x2);
        this.axisY2.set(y2);
        return this;
    }
}