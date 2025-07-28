package net.xmx.vortex.physics.constraint.builder;

import com.github.stephengold.joltjni.FixedConstraint;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;

public class FixedConstraintBuilder extends ConstraintBuilder<FixedConstraintBuilder, FixedConstraint> {

    private FixedConstraintSettings settings;

    public FixedConstraintBuilder() {
        this.reset();
    }

    public FixedConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "vortex:fixed";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new FixedConstraintSettings();
    }

    public FixedConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public FixedConstraintBuilder withAutoDetectPoint(boolean autoDetect) {
        this.settings.setAutoDetectPoint(autoDetect);
        return this;
    }

    public FixedConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }

    public FixedConstraintBuilder withAxes1(Vec3 x1, Vec3 y1) {
        this.settings.setAxisX1(x1);
        this.settings.setAxisY1(y1);
        return this;
    }

    public FixedConstraintBuilder withAxes2(Vec3 x2, Vec3 y2) {
        this.settings.setAxisX2(x2);
        this.settings.setAxisY2(y2);
        return this;
    }
}