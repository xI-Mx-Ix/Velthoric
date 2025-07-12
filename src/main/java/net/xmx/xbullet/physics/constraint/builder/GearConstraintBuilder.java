package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

import java.util.UUID;

public class GearConstraintBuilder extends ConstraintBuilder<GearConstraintBuilder, GearConstraint> {

    public EConstraintSpace space;
    public Vec3 hingeAxis1;
    public Vec3 hingeAxis2;
    public float ratio;

    public GearConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:gear";
    }

    @Override
    public void reset() {
        this.dependencyConstraintId1 = null;
        this.dependencyConstraintId2 = null;
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.WorldSpace;
        this.ratio = 1.0f;

        if (this.hingeAxis1 == null) this.hingeAxis1 = new Vec3(1, 0, 0); else this.hingeAxis1.set(1, 0, 0);
        if (this.hingeAxis2 == null) this.hingeAxis2 = new Vec3(1, 0, 0); else this.hingeAxis2.set(1, 0, 0);
    }

    public GearConstraintBuilder connectingHinges(UUID hingeConstraintId1, UUID hingeConstraintId2) {
        this.dependencyConstraintId1 = hingeConstraintId1;
        this.dependencyConstraintId2 = hingeConstraintId2;
        return this;
    }

    public GearConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public GearConstraintBuilder withHingeAxes(Vec3 axis1, Vec3 axis2) {
        this.hingeAxis1.set(axis1);
        this.hingeAxis2.set(axis2);
        return this;
    }

    public GearConstraintBuilder withRatio(float ratio) {
        this.ratio = ratio;
        return this;
    }

    public GearConstraintBuilder withRatio(int numTeeth1, int numTeeth2) {
        if (numTeeth2 == 0) {
            this.ratio = Float.POSITIVE_INFINITY;
        } else {
            this.ratio = (float) numTeeth1 / (float) numTeeth2;
        }
        return this;
    }
}