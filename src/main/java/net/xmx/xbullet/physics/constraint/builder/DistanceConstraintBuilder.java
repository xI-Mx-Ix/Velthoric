package net.xmx.xbullet.physics.constraint.builder;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;

public class DistanceConstraintBuilder extends ConstraintBuilder<DistanceConstraintBuilder, DistanceConstraint> {

    public EConstraintSpace space;
    public RVec3 point1;
    public RVec3 point2;
    public float minDistance;
    public float maxDistance;
    public SpringSettings limitsSpringSettings;

    public DistanceConstraintBuilder() {
        this.reset();
    }

    @Override
    public String getTypeId() {
        return "xbullet:distance";
    }

    @Override
    public void reset() {
        this.body1Id = null;
        this.body2Id = null;
        this.space = EConstraintSpace.LocalToBodyCOM;

        if (this.point1 == null) this.point1 = new RVec3(); else this.point1.set(0, 0, 0);
        if (this.point2 == null) this.point2 = new RVec3(); else this.point2.set(0, 0, 0);

        this.minDistance = -1f;
        this.maxDistance = -1f;

        if (this.limitsSpringSettings == null) this.limitsSpringSettings = new SpringSettings();
    }

    public DistanceConstraintBuilder inSpace(EConstraintSpace space) {
        this.space = space;
        return this;
    }

    public DistanceConstraintBuilder atPoints(RVec3 p1, RVec3 p2) {
        this.point1.set(p1);
        this.point2.set(p2);
        return this;
    }

    public DistanceConstraintBuilder withDistance(float distance) {
        this.minDistance = distance;
        this.maxDistance = distance;
        return this;
    }

    public DistanceConstraintBuilder withDistanceRange(float min, float max) {
        this.minDistance = min;
        this.maxDistance = max;
        return this;
    }

    public DistanceConstraintBuilder withLimitsSpringSettings(SpringSettings settings) {
        if (this.limitsSpringSettings == null) {
            this.limitsSpringSettings = new SpringSettings();
        }
        this.limitsSpringSettings.setMode(settings.getMode());
        this.limitsSpringSettings.setDamping(settings.getDamping());
        this.limitsSpringSettings.setFrequency(settings.getFrequency());
        this.limitsSpringSettings.setStiffness(settings.getStiffness());
        return this;
    }
}