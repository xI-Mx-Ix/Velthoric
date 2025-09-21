/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.cloth;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.object.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;
import java.util.function.BiFunction;

public class ClothSoftBody extends VxSoftBody {

    private int widthSegments;
    private int heightSegments;
    private float clothWidth;
    private float clothHeight;
    private float mass;
    private float compliance;

    public ClothSoftBody(VxObjectType<ClothSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.widthSegments = 15;
        this.heightSegments = 15;
        this.clothWidth = 2.0f;
        this.clothHeight = 2.0f;
        this.mass = 2.0f;
        this.compliance = 0.02f;
    }

    public void setConfiguration(int widthSegments, int heightSegments, float clothWidth, float clothHeight, float mass, float compliance) {
        this.widthSegments = widthSegments;
        this.heightSegments = heightSegments;
        this.clothWidth = clothWidth;
        this.clothHeight = clothHeight;
        this.mass = mass;
        this.compliance = compliance;
    }

    @Override
    public int createJoltBody(VxSoftBodyFactory factory) {
        int numVerticesX = this.widthSegments + 1;
        int numVerticesY = this.heightSegments + 1;
        int totalVertices = numVerticesX * numVerticesY;

        try (
                SoftBodySharedSettings sharedSettings = new SoftBodySharedSettings();
                SoftBodyCreationSettings creationSettings = new SoftBodyCreationSettings()
        ) {
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
                    sharedSettings.addVertex(v);
                }
            }

            BiFunction<Integer, Integer, Integer> getIndex = (x, y) -> y * numVerticesX + x;

            for (int y = 0; y < numVerticesY; ++y) {
                for (int x = 0; x < numVerticesX; ++x) {
                    if (x < this.widthSegments) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 1, y), compliance, vertexPositions);
                    }
                    if (y < this.heightSegments) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x, y + 1), compliance, vertexPositions);
                    }
                    if (x < this.widthSegments && y < this.heightSegments) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 1, y + 1), compliance, vertexPositions);
                        addEdge(sharedSettings, getIndex.apply(x + 1, y), getIndex.apply(x, y + 1), compliance, vertexPositions);
                    }
                    float bendCompliance = compliance * 10f;
                    if (x < this.widthSegments - 1) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 2, y), bendCompliance, vertexPositions);
                    }
                    if (y < this.heightSegments - 1) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x, y + 2), bendCompliance, vertexPositions);
                    }
                }
            }

            sharedSettings.optimize();

            creationSettings.setSettings(sharedSettings);
            creationSettings.setObjectLayer(VxLayers.DYNAMIC);
            creationSettings.setVertexRadius(0.02f);

            return factory.create(sharedSettings, creationSettings);
        }
    }

    private void addEdge(SoftBodySharedSettings settings, int v1Index, int v2Index, float edgeCompliance, Vec3[] vertexPositions) {
        Edge edge = new Edge();
        edge.setVertex(0, v1Index);
        edge.setVertex(1, v2Index);
        edge.setCompliance(edgeCompliance);
        Vec3Arg pos1 = vertexPositions[v1Index];
        Vec3Arg pos2 = vertexPositions[v2Index];
        Vec3Arg offset = Op.minus(pos2, pos1);
        edge.setRestLength(offset.length());
        settings.addEdgeConstraint(edge);
    }

    @Override
    public void writeSyncData(VxByteBuf buf) {
        buf.writeInt(this.widthSegments);
        buf.writeInt(this.heightSegments);
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeInt(this.widthSegments);
        buf.writeInt(this.heightSegments);
        buf.writeFloat(this.clothWidth);
        buf.writeFloat(this.clothHeight);
        buf.writeFloat(this.mass);
        buf.writeFloat(this.compliance);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        this.widthSegments = buf.readInt();
        this.heightSegments = buf.readInt();
        this.clothWidth = buf.readFloat();
        this.clothHeight = buf.readFloat();
        this.mass = buf.readFloat();
        this.compliance = buf.readFloat();
    }
}