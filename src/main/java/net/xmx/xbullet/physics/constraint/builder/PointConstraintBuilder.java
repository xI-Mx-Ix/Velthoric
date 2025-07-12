package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.PointConstraint;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class PointConstraintBuilder extends ConstraintBuilder<PointConstraintBuilder, PointConstraint> {

    public EConstraintSpace space;
    public RVec3 point1;
    public RVec3 point2;

    public PointConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:point";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);
    }

    public PointConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public PointConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }
}