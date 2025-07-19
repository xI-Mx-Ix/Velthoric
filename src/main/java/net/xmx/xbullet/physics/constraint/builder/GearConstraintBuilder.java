package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.GearConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;
import java.util.UUID;

public class GearConstraintBuilder extends ConstraintBuilder<GearConstraintBuilder, GearConstraint> {

    private GearConstraintSettings settings;

    public GearConstraintBuilder() {
        this.reset();
    }

    public GearConstraintSettings getSettings() {
        return settings;
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
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new GearConstraintSettings();
    }

    public GearConstraintBuilder connectingHinges(UUID hingeConstraintId1, UUID hingeConstraintId2) {
        this.dependencyConstraintId1 = hingeConstraintId1;
        this.dependencyConstraintId2 = hingeConstraintId2;
        return this;
    }

    public GearConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public GearConstraintBuilder withHingeAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setHingeAxis1(axis1);
        this.settings.setHingeAxis2(axis2);
        return this;
    }

    public GearConstraintBuilder withRatio(float ratio) {
        this.settings.setRatio(ratio);
        return this;
    }

    public GearConstraintBuilder withRatio(int numTeeth1, int numTeeth2) {
        float ratio;
        if (numTeeth2 == 0) {
            ratio = Float.POSITIVE_INFINITY;
        } else {
            ratio = (float) numTeeth1 / (float) numTeeth2;
        }
        this.settings.setRatio(ratio);
        return this;
    }
}