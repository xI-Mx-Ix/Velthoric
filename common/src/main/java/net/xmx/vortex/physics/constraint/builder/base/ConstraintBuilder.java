package net.xmx.vortex.physics.constraint.builder.base;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;

import java.util.UUID;

public abstract class ConstraintBuilder<B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> {

    protected VxConstraintManager manager;

    protected UUID body1Id;
    protected UUID body2Id;
    protected UUID dependencyConstraintId1;
    protected UUID dependencyConstraintId2;

    public void setManager(VxConstraintManager manager) {
        this.manager = manager;
    }

    public abstract void reset();

    @SuppressWarnings("unchecked")
    public B between(VxAbstractBody body1, VxAbstractBody body2) {
        this.body1Id = (body1 != null) ? body1.getPhysicsId() : null;
        this.body2Id = (body2 != null) ? body2.getPhysicsId() : null;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B between(UUID bodyId1, UUID bodyId2) {
        this.body1Id = bodyId1;
        this.body2Id = bodyId2;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public void build() {
        if (manager == null) {
            throw new IllegalStateException("ConstraintBuilder has no manager. It was likely created with 'new' instead of through the ConstraintManager.");
        }
        manager.queueCreation((B) this);
    }

    public UUID getBody1Id() { return body1Id; }
    public UUID getBody2Id() { return body2Id; }

    public UUID getDependencyConstraintId1() { return dependencyConstraintId1; }
    public UUID getDependencyConstraintId2() { return dependencyConstraintId2; }
    public abstract String getTypeId();
}