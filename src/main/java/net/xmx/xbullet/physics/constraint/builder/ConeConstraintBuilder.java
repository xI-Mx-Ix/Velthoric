package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.ConeConstraint;
import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class ConeConstraintBuilder extends ConstraintBuilder<ConeConstraintBuilder, ConeConstraint> {

    private ConeConstraintSettings settings;

    public ConeConstraintBuilder() {
        this.reset();
    }

    public ConeConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "xbullet:cone";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new ConeConstraintSettings();
    }

    public ConeConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public ConeConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.settings.setPoint1(p1);
        this.settings.setPoint2(p2);
        return this;
    }

    public ConeConstraintBuilder withTwistAxes(Vec3 axis1, Vec3 axis2) {
        this.settings.setTwistAxis1(axis1);
        this.settings.setTwistAxis2(axis2);
        return this;
    }

    public ConeConstraintBuilder withHalfConeAngle(float angle) {
        this.settings.setHalfConeAngle(angle);
        return this;
    }
}