package net.xmx.vortex.physics.object.physicsobject.type.rigid.properties;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;

import javax.annotation.concurrent.Immutable;

@Immutable
public class RigidPhysicsObjectProperties implements IPhysicsObjectProperties {

    private final float mass;
    private final float friction;
    private final float restitution;
    private final float linearDamping;
    private final float angularDamping;
    private final float gravityFactor;
    private final float buoyancyFactor;
    private final EMotionType motionType;

    public RigidPhysicsObjectProperties(
            float mass,
            float friction,
            float restitution,
            float linearDamping,
            float angularDamping,
            float gravityFactor,
            float buoyancyFactor,
            EMotionType motionType
    ) {
        this.mass = mass;
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.gravityFactor = gravityFactor;
        this.buoyancyFactor = buoyancyFactor;
        this.motionType = motionType;
    }

    public float getMass() { return mass; }
    public float getFriction() { return friction; }
    public float getRestitution() { return restitution; }
    public float getLinearDamping() { return linearDamping; }
    public float getAngularDamping() { return angularDamping; }
    public float getGravityFactor() { return gravityFactor; }
    public float getBuoyancyFactor() { return buoyancyFactor; }
    public EMotionType getMotionType() { return motionType; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float mass = 1.0f;
        private float friction = 0.5f;
        private float restitution = 0.2f;
        private float linearDamping = 0.05f;
        private float angularDamping = 0.05f;
        private float gravityFactor = 1.0f;
        private float buoyancyFactor = 0.0f;
        private EMotionType motionType = EMotionType.Dynamic;

        public Builder mass(float mass) { this.mass = mass; return this; }
        public Builder friction(float friction) { this.friction = friction; return this; }
        public Builder restitution(float restitution) { this.restitution = restitution; return this; }
        public Builder linearDamping(float linearDamping) { this.linearDamping = linearDamping; return this; }
        public Builder angularDamping(float angularDamping) { this.angularDamping = angularDamping; return this; }
        public Builder gravityFactor(float gravityFactor) { this.gravityFactor = gravityFactor; return this; }
        public Builder buoyancyFactor(float buoyancyFactor) { this.buoyancyFactor = buoyancyFactor; return this; }
        public Builder motionType(EMotionType motionType) { this.motionType = motionType; return this; }

        public RigidPhysicsObjectProperties build() {
            return new RigidPhysicsObjectProperties(
                    mass, friction, restitution,
                    linearDamping, angularDamping,
                    gravityFactor, buoyancyFactor,
                    motionType
            );
        }
    }
}