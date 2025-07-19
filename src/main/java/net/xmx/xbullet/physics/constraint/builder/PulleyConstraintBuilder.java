package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.PulleyConstraint;
import com.github.stephengold.joltjni.PulleyConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class PulleyConstraintBuilder extends ConstraintBuilder<PulleyConstraintBuilder, PulleyConstraint> {

    private PulleyConstraintSettings settings;

    public PulleyConstraintBuilder() {
        this.reset();
    }

    public PulleyConstraintSettings getSettings() {
        return settings;
    }

    @Override
    public String getTypeId() {
        return "xbullet:pulley";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        if (this.settings != null && this.settings.hasAssignedNativeObject()) {
            this.settings.close();
        }
        this.settings = new PulleyConstraintSettings();
    }

    public PulleyConstraintBuilder inSpace(EConstraintSpace space) {
        this.settings.setSpace(space);
        return this;
    }

    public PulleyConstraintBuilder withBodyPoints(RVec3 p1, RVec3 p2) {
        this.settings.setBodyPoint1(p1);
        this.settings.setBodyPoint2(p2);
        return this;
    }

    public PulleyConstraintBuilder withFixedPoints(RVec3 p1, RVec3 p2) {
        this.settings.setFixedPoint1(p1);
        this.settings.setFixedPoint2(p2);
        return this;
    }

    public PulleyConstraintBuilder withRatio(float ratio) {
        this.settings.setRatio(ratio);
        return this;
    }

    public PulleyConstraintBuilder withLengthRange(float min, float max) {
        this.settings.setMinLength(min);
        this.settings.setMaxLength(max);
        return this;
    }
}