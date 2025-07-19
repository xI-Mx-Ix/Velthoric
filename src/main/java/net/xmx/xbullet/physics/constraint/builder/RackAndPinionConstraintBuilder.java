package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;
import java.util.UUID;

public class RackAndPinionConstraintBuilder extends ConstraintBuilder<RackAndPinionConstraintBuilder, RackAndPinionConstraint> {

    private RackAndPinionConstraintSettings settings;

    public RackAndPinionConstraintBuilder() {
        this.reset();
    }

    public RackAndPinionConstraintSettings getSettings() {
        return settings;
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
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new RackAndPinionConstraintSettings();
    }

    public RackAndPinionConstraintBuilder connecting(UUID hingeConstraintId, UUID sliderConstraintId) {
        this.dependencyConstraintId1 = hingeConstraintId;
        this.dependencyConstraintId2 = sliderConstraintId;
        return this;
    }

    public RackAndPinionConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public RackAndPinionConstraintBuilder withHingeAxis(Vec3 axis) {
        this.settings.setHingeAxis(axis);
        return this;
    }

    public RackAndPinionConstraintBuilder withSliderAxis(Vec3 axis) {
        this.settings.setSliderAxis(axis);
        return this;
    }

    public RackAndPinionConstraintBuilder withRatio(int rackTeeth, float rackLength, int pinionTeeth) {
        this.settings.setRatio(rackTeeth, rackLength, pinionTeeth);
        return this;
    }
}