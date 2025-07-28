package net.xmx.vortex.physics.object.physicsobject.type.soft.properties;

import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;

public class SoftPhysicsObjectProperties implements IPhysicsObjectProperties {

    private final float friction;
    private final float restitution;
    private final float linearDamping;
    private final float gravityFactor;
    private final float buoyancyFactor;
    private final float pressure;
    private final int numIterations;

    public SoftPhysicsObjectProperties(float friction, float restitution, float linearDamping, float gravityFactor, float buoyancyFactor, float pressure, int numIterations) {
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.gravityFactor = gravityFactor;
        this.buoyancyFactor = buoyancyFactor;
        this.pressure = pressure;
        this.numIterations = numIterations;
    }

    public float getFriction() { return friction; }
    public float getRestitution() { return restitution; }
    public float getLinearDamping() { return linearDamping; }
    public float getGravityFactor() { return gravityFactor; }
    public float getBuoyancyFactor() { return buoyancyFactor; }
    public float getPressure() { return pressure; }
    public int getNumIterations() { return numIterations; }

    public static Builder builder() {
        return new Builder();
    }

    public void toBuffer(FriendlyByteBuf buf) {
        buf.writeFloat(this.friction);
        buf.writeFloat(this.restitution);
        buf.writeFloat(this.linearDamping);
        buf.writeFloat(this.gravityFactor);
        buf.writeFloat(this.buoyancyFactor);
        buf.writeFloat(this.pressure);
        buf.writeInt(this.numIterations);
    }

    public static SoftPhysicsObjectProperties fromBuffer(FriendlyByteBuf buf) {
        float friction = buf.readFloat();
        float restitution = buf.readFloat();
        float linearDamping = buf.readFloat();
        float gravityFactor = buf.readFloat();
        float buoyancyFactor = buf.readFloat();
        float pressure = buf.readFloat();
        int numIterations = buf.readInt();
        return new SoftPhysicsObjectProperties(friction, restitution, linearDamping, gravityFactor, buoyancyFactor, pressure, numIterations);
    }

    public static class Builder {
        private float friction = 0.5f;
        private float restitution = 0.0f;
        private float linearDamping = 0.05f;
        private float gravityFactor = 1.0f;
        private float buoyancyFactor = 1.2f;
        private float pressure = 0.0f;
        private int numIterations = 10;

        public Builder friction(float friction) { this.friction = friction; return this; }
        public Builder restitution(float restitution) { this.restitution = restitution; return this; }
        public Builder linearDamping(float linearDamping) { this.linearDamping = linearDamping; return this; }
        public Builder gravityFactor(float gravityFactor) { this.gravityFactor = gravityFactor; return this; }
        public Builder buoyancyFactor(float buoyancyFactor) { this.buoyancyFactor = buoyancyFactor; return this; }
        public Builder pressure(float pressure) { this.pressure = pressure; return this; }
        public Builder numIterations(int numIterations) { this.numIterations = numIterations; return this; }

        public SoftPhysicsObjectProperties build() {
            return new SoftPhysicsObjectProperties(friction, restitution, linearDamping, gravityFactor, buoyancyFactor, pressure, numIterations);
        }
    }
}