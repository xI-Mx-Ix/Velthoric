package net.xmx.xbullet.builtin.cloth;

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

        CompoundTag nbt = (initialNbt != null) ? initialNbt : new CompoundTag();
        this.mass = nbt.contains("mass") ? nbt.getFloat("mass") : 2.0f;
        this.compliance = nbt.contains("compliance") ? nbt.getFloat("compliance") : 0.001f;

        this.readAdditionalSaveData(nbt);
    }

    @Override
    protected SoftBodySharedSettings buildSharedSettings() {
        int numVerticesX = this.widthSegments + 1;
        int numVerticesY = this.heightSegments + 1;
        int totalVertices = numVerticesX * numVerticesY;

        SoftBodySharedSettings settings = new SoftBodySharedSettings();
        settings.setVertexRadius(0.02f);

        float segmentWidth = clothWidth / this.widthSegments;
        float segmentHeight = clothHeight / this.heightSegments;
        float invMassPerVertex = (this.mass > 0) ? totalVertices / this.mass : 0f;

        for (int y = 0; y < numVerticesY; ++y) {
            for (int x = 0; x < numVerticesX; ++x) {
                Vec3 pos = new Vec3(x * segmentWidth, 0, y * segmentHeight);
                Vec3 rotatedPos = Op.star(this.currentTransform.getRotation(), pos);
                RVec3 finalPos = Op.plus(this.currentTransform.getTranslation(), rotatedPos);

                Vertex v = new Vertex();
                v.setPosition(finalPos.toVec3());
                v.setInvMass(invMassPerVertex);
                settings.addVertex(v);
            }
        }

        java.util.function.BiFunction<Integer, Integer, Integer> getIndex = (x, y) -> y * numVerticesX + x;

        for (int y = 0; y < numVerticesY; ++y) {
            for (int x = 0; x < numVerticesX; ++x) {

                if (x < this.widthSegments) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 1, y), compliance);
                if (y < this.heightSegments) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x, y + 1), compliance);

                if (x < this.widthSegments && y < this.heightSegments) {
                    addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 1, y + 1), compliance);
                    addEdge(settings, getIndex.apply(x + 1, y), getIndex.apply(x, y + 1), compliance);
                }

                float bendCompliance = compliance * 100f;
                if (x < this.widthSegments - 1) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x + 2, y), bendCompliance);
                if (y < this.heightSegments - 1) addEdge(settings, getIndex.apply(x, y), getIndex.apply(x, y + 2), bendCompliance);
            }
        }

        for (int x = 0; x < numVerticesX; ++x) {
            settings.getVertex(getIndex.apply(x, 0)).setInvMass(0f);
        }

        settings.optimize();

        return settings;
    }

    private void addEdge(SoftBodySharedSettings settings, int v1, int v2, float compliance) {
        Edge edge = new Edge();
        edge.setVertex(0, v1);
        edge.setVertex(1, v2);
        edge.setCompliance(compliance);
        settings.addEdgeConstraint(edge);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("widthSegments", this.widthSegments);
        tag.putInt("heightSegments", this.heightSegments);
        tag.putFloat("clothWidth", this.clothWidth);
        tag.putFloat("clothHeight", this.clothHeight);
        tag.putFloat("mass", this.mass);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.widthSegments = tag.contains("widthSegments") ? tag.getInt("widthSegments") : 15;
        this.heightSegments = tag.contains("heightSegments") ? tag.getInt("heightSegments") : 15;
        this.clothWidth = tag.contains("clothWidth") ? tag.getFloat("clothWidth") : 2.0f;
        this.clothHeight = tag.contains("clothHeight") ? tag.getFloat("clothHeight") : 2.0f;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends SoftPhysicsObjectBuilder {
        public Builder() {
            super();
            this.type(TYPE_IDENTIFIER);
        }

        public Builder segments(int width, int height) {
            this.initialNbt.putInt("widthSegments", width);
            this.initialNbt.putInt("heightSegments", height);
            return this;
        }

        public Builder size(float width, float height) {
            this.initialNbt.putFloat("clothWidth", width);
            this.initialNbt.putFloat("clothHeight", height);
            return this;
        }

        public Builder mass(float mass) {
            this.initialNbt.putFloat("mass", mass);
            return this;
        }
    }
}