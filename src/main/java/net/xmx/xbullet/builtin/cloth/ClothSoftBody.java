package net.xmx.xbullet.builtin.cloth;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.builder.SoftPhysicsObjectBuilder;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.BiFunction;

public class ClothSoftBody extends SoftPhysicsObject {

    public static final String TYPE_IDENTIFIER = "xbullet:cloth";

    private int widthSegments;
    private int heightSegments;
    private float clothWidth;
    private float clothHeight;
    private float mass;
    private float compliance;

    public ClothSoftBody(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);
    }

    @Override
    protected void configureAdditionalSoftBodyCreationSettings(SoftBodyCreationSettings settings) {
        settings.setVertexRadius(0.02f);
    }

    @Override
    protected SoftBodySharedSettings buildSharedSettings() {
        int numVerticesX = this.widthSegments + 1;
        int numVerticesY = this.heightSegments + 1;
        int totalVertices = numVerticesX * numVerticesY;

        SoftBodySharedSettings settings = new SoftBodySharedSettings();

        float segmentWidth = clothWidth / this.widthSegments;
        float segmentHeight = clothHeight / this.heightSegments;

        float invMassPerVertex = (this.mass > 0) ? totalVertices / this.mass : 0f;

        Vec3[] vertexPositions = new Vec3[totalVertices];

        for (int y = 0; y < numVerticesY; ++y) {
            for (int x = 0; x < numVerticesX; ++x) {
                float localX = (x * segmentWidth) - (clothWidth / 2.0f);
                float localZ = (y * segmentHeight) - (clothHeight / 2.0f);
                Vec3 localPos = new Vec3(localX, 0, localZ);

                int index = y * numVerticesX + x;
                vertexPositions[index] = localPos;

                Vertex v = new Vertex();
                v.setPosition(localPos);
                v.setInvMass(invMassPerVertex);
                settings.addVertex(v);
            }
        }

        BiFunction<Integer, Integer, Integer> getIndex = (x, y) -> y * numVerticesX + x;

        for (int y = 0; y < numVerticesY; ++y) {
            for (int x = 0; x < numVerticesX; ++x) {

                if (x < this.widthSegments) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 1, y), compliance, vertexPositions);
                if (y < this.heightSegments) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x, y + 1), compliance, vertexPositions);

                if (x < this.widthSegments && y < this.heightSegments) {
                    addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 1, y + 1), compliance, vertexPositions);
                    addEdge(settings, getIndex.apply(x + 1, y), getIndex.apply(x, y + 1), compliance, vertexPositions);
                }

                float bendCompliance = compliance * 10f;
                if (x < this.widthSegments - 1) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 2, y), bendCompliance, vertexPositions);
                if (y < this.heightSegments - 1) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x, y + 2), bendCompliance, vertexPositions);
            }
        }

        settings.optimize();
        return settings;
    }

    private void addEdge(SoftBodySharedSettings settings, int v1Index, int v2Index, float edgeCompliance, Vec3[] vertexPositions) {
        Edge edge = new Edge();
        edge.setVertex(0, v1Index);
        edge.setVertex(1, v2Index);
        edge.setCompliance(edgeCompliance);

        Vec3Arg pos1 = vertexPositions[v1Index];
        Vec3Arg pos2 = vertexPositions[v2Index];
        Vec3Arg offset = Op.minus(pos2, pos1);
        float restLength = offset.length();
        edge.setRestLength(restLength);

        settings.addEdgeConstraint(edge);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.widthSegments = tag.contains("widthSegments") ? tag.getInt("widthSegments") : 15;
        this.heightSegments = tag.contains("heightSegments") ? tag.getInt("heightSegments") : 15;
        this.clothWidth = tag.contains("clothWidth") ? tag.getFloat("clothWidth") : 2.0f;
        this.clothHeight = tag.contains("clothHeight") ? tag.getFloat("clothHeight") : 2.0f;
        this.mass = tag.contains("mass") ? tag.getFloat("mass") : 2.0f;
        this.compliance = tag.contains("compliance") ? tag.getFloat("compliance") : 0.001f;
    }
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("widthSegments", this.widthSegments);
        tag.putInt("heightSegments", this.heightSegments);
        tag.putFloat("clothWidth", this.clothWidth);
        tag.putFloat("clothHeight", this.clothHeight);
        tag.putFloat("mass", this.mass);
        tag.putFloat("compliance", this.compliance);
    }
    public static Builder builder() { return new Builder(); }
    public static class Builder extends SoftPhysicsObjectBuilder {
        public Builder() {
            super();
            this.type(TYPE_IDENTIFIER);
            this.initialNbt.putFloat("mass", 2.0f);
            this.initialNbt.putFloat("compliance", 0.001f);
            segments(15, 15);
            size(2.0f, 2.0f);
        }
        public Builder segments(int width, int height) { this.initialNbt.putInt("widthSegments", width); this.initialNbt.putInt("heightSegments", height); return this; }
        public Builder size(float width, float height) { this.initialNbt.putFloat("clothWidth", width); this.initialNbt.putFloat("clothHeight", height); return this; }
        public Builder mass(float mass) { this.initialNbt.putFloat("mass", mass); return this; }
        public Builder compliance(float compliance) { this.initialNbt.putFloat("compliance", compliance); return this; }
    }
}