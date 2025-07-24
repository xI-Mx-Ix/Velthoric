package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.PointConstraint;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class PointConstraintBuilder extends ConstraintBuilder<PointConstraintBuilder, PointConstraint> {

    private PointConstraintSettings settings;

    public PointConstraintBuilder() {
        this.reset();
    }

    public PointConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:point";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new PointConstraintSettings();
    }

    public PointConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public PointConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }
}