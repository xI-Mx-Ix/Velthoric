package net.xmx.xbullet.builtin.rope;

import com.github.stephengold.joltjni.Edge;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.Vertex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.builder.SoftPhysicsObjectBuilder;

import javax.annotation.Nullable;
import java.util.UUID;

public class RopeSoftBody extends SoftPhysicsObject {

    public static final String TYPE_IDENTIFIER = "xbullet:rope";

    private float ropeLength;
    private int numSegments;
    private float ropeRadius;
    private float mass;
    private float compliance;

    public RopeSoftBody(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);
    }

    @Override
    protected SoftBodySharedSettings buildSharedSettings() {
        int numNodes = this.numSegments + 1;

        SoftBodySharedSettings settings = new SoftBodySharedSettings();
        settings.setVertexRadius(this.ropeRadius);

        float segmentLength = ropeLength / (float)this.numSegments;
        float invMassPerNode = (this.mass > 0) ? numNodes / this.mass : 0f;

        for (int i = 0; i < numNodes; i++) {

            Vec3 localPos = new Vec3(0, -i * segmentLength, 0);

            Vertex v = new Vertex();
            v.setPosition(localPos);
            v.setInvMass(invMassPerNode);
            settings.addVertex(v);
        }

        for (int i = 0; i < numNodes - 1; i++) {
            Edge edge = new Edge();
            edge.setVertex(0, i);
            edge.setVertex(1, i + 1);
            edge.setCompliance(this.compliance);

            edge.setRestLength(segmentLength);

            settings.addEdgeConstraint(edge);
        }

        settings.getVertex(0).setInvMass(0f);

        settings.optimize();
        return settings;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ropeLength = tag.contains("ropeLength") ? tag.getFloat("ropeLength") : 10.0f;
        this.numSegments = tag.contains("numSegments") ? tag.getInt("numSegments") : 20;
        this.ropeRadius = tag.contains("ropeRadius") ? tag.getFloat("ropeRadius") : 0.1f;

        this.mass = tag.contains("mass") ? tag.getFloat("mass") : 5.0f;
        this.compliance = tag.contains("compliance") ? tag.getFloat("compliance") : 0.001f;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ropeLength", this.ropeLength);
        tag.putInt("numSegments", this.numSegments);
        tag.putFloat("ropeRadius", this.ropeRadius);
        tag.putFloat("mass", this.mass);
        tag.putFloat("compliance", this.compliance);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends SoftPhysicsObjectBuilder {
        public Builder() {
            super();
            this.type(TYPE_IDENTIFIER);

            this.initialNbt.putFloat("mass", 5.0f);
            this.initialNbt.putFloat("compliance", 0.001f);
            ropeLength(10.0f);
            numSegments(20);
            ropeRadius(0.1f);
        }

        public Builder ropeLength(float length) {
            this.initialNbt.putFloat("ropeLength", length);
            return this;
        }

        public Builder numSegments(int segments) {
            this.initialNbt.putInt("numSegments", segments);
            return this;
        }

        public Builder ropeRadius(float radius) {
            this.initialNbt.putFloat("ropeRadius", radius);
            return this;
        }

        public Builder mass(float mass) {
            this.initialNbt.putFloat("mass", mass);
            return this;
        }

        public Builder compliance(float compliance) {
            this.initialNbt.putFloat("compliance", compliance);
            return this;
        }
    }
}