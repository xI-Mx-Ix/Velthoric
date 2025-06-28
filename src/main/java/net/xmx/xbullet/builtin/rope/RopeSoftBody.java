package net.xmx.xbullet.builtin.rope;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
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

        CompoundTag nbt = (initialNbt != null) ? initialNbt : new CompoundTag();
        this.mass = nbt.contains("mass") ? nbt.getFloat("mass") : 5.0f;
        this.compliance = nbt.contains("compliance") ? nbt.getFloat("compliance") : 0.001f;

        this.readAdditionalSaveData(nbt);
    }

    @Override
    protected SoftBodySharedSettings buildSharedSettings() {
        int numNodes = (this.numSegments > 0) ? this.numSegments + 1 : 21;

        SoftBodySharedSettings settings = new SoftBodySharedSettings();
        settings.setVertexRadius(this.ropeRadius);

        RVec3 startPos = this.currentTransform.getTranslation();
        Quat rotation = this.currentTransform.getRotation();
        Vec3 direction = Op.star(rotation, new Vec3(0, -1, 0));
        float segmentLength = ropeLength / (numNodes - 1);

        for (int i = 0; i < numNodes; i++) {
            RVec3 nodePosR = Op.plus(startPos, Op.star(direction, i * segmentLength));
            Vertex v = new Vertex();
            v.setPosition(nodePosR.toVec3());
            v.setInvMass(1f / (this.mass / numNodes));
            settings.addVertex(v);
        }

        for (int i = 0; i < numNodes - 1; i++) {
            Edge edge = new Edge();
            edge.setVertex(0, i);
            edge.setVertex(1, i + 1);
            edge.setCompliance(this.compliance);
            settings.addEdgeConstraint(edge);
        }

        settings.getVertex(0).setInvMass(0f);
        return settings;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ropeLength", this.ropeLength);
        tag.putInt("numSegments", this.numSegments);
        tag.putFloat("ropeRadius", this.ropeRadius);
        tag.putFloat("mass", this.mass);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ropeLength = tag.contains("ropeLength") ? tag.getFloat("ropeLength") : 10.0f;
        this.numSegments = tag.contains("numSegments") ? tag.getInt("numSegments") : 20;
        this.ropeRadius = tag.contains("ropeRadius") ? tag.getFloat("ropeRadius") : 0.1f;
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder extends SoftPhysicsObjectBuilder {
        public Builder() {
            super();
            this.type(TYPE_IDENTIFIER);
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
    }
}