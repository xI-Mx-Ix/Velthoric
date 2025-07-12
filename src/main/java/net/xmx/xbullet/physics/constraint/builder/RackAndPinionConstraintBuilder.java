package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

import java.util.UUID;

public class RackAndPinionConstraintBuilder extends ConstraintBuilder<RackAndPinionConstraintBuilder, RackAndPinionConstraint> {

    public EConstraintSpace space;
    public Vec3 hingeAxis;
    public Vec3 sliderAxis;
    public float ratio;

    public RackAndPinionConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:rack_and_pinion";
    }

    @Override
    public void reset() {
        this.dependencyConstraintId1 = null;
        this.dependencyConstraintId2 = null;
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.WorldSpace;

        if (this.hingeAxis == null) this.hingeAxis = new Vec3(1, 0, 0); else this.hingeAxis.set(1, 0, 0);
        if (this.sliderAxis == null) this.sliderAxis = new Vec3(1, 0, 0); else this.sliderAxis.set(1, 0, 0);

        this.ratio = 1.0f;
    }

    public RackAndPinionConstraintBuilder connecting(UUID hingeConstraintId, UUID sliderConstraintId) {
        this.dependencyConstraintId1 = hingeConstraintId;
        this.dependencyConstraintId2 = sliderConstraintId;
        return this;
    }

    public RackAndPinionConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public RackAndPinionConstraintBuilder withHingeAxis(Vec3 axis) {
        this.hingeAxis.set(axis);
        return this;
    }

    public RackAndPinionConstraintBuilder withSliderAxis(Vec3 axis) {
        this.sliderAxis.set(axis);
        return this;
    }

    public RackAndPinionConstraintBuilder withRatio(int rackTeeth, float rackLength, int pinionTeeth) {
        if (rackLength <= 0f || pinionTeeth <= 0) {
            this.ratio = 1.0f;
        } else {
            this.ratio = (2.0f * (float)Math.PI * rackTeeth) / (rackLength * pinionTeeth);
        }
        return this;
    }
}