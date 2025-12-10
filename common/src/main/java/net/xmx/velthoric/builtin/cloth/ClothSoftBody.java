/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.cloth;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.physics.body.sync.accessor.VxServerAccessor;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.accessor.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxSoftBody;
import net.xmx.velthoric.physics.body.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;
import java.util.function.BiFunction;

/**
 * A soft body physics body that simulates a piece of cloth.
 *
 * @author xI-Mx-Ix
 */
public class ClothSoftBody extends VxSoftBody {

    public static final VxServerAccessor<Integer> DATA_WIDTH_SEGMENTS = VxServerAccessor.create(ClothSoftBody.class, VxDataSerializers.INTEGER);
    public static final VxServerAccessor<Integer> DATA_HEIGHT_SEGMENTS = VxServerAccessor.create(ClothSoftBody.class, VxDataSerializers.INTEGER);

    private float clothWidth;
    private float clothHeight;
    private float mass;
    private float compliance;

    /**
     * Server-side constructor.
     */
    public ClothSoftBody(VxBodyType<ClothSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.clothWidth = 2.0f;
        this.clothHeight = 2.0f;
        this.mass = 2.0f;
        this.compliance = 0.02f;
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public ClothSoftBody(VxBodyType<ClothSoftBody> type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_WIDTH_SEGMENTS, 15);
        builder.define(DATA_HEIGHT_SEGMENTS, 15);
    }

    public void setConfiguration(int widthSegments, int heightSegments, float clothWidth, float clothHeight, float mass, float compliance) {
        this.setServerData(DATA_WIDTH_SEGMENTS, widthSegments);
        this.setServerData(DATA_HEIGHT_SEGMENTS, heightSegments);
        this.clothWidth = clothWidth;
        this.clothHeight = clothHeight;
        this.mass = mass;
        this.compliance = compliance;
    }

    @Override
    public int createJoltBody(VxSoftBodyFactory factory) {
        int widthSegments = get(DATA_WIDTH_SEGMENTS);
        int heightSegments = get(DATA_HEIGHT_SEGMENTS);
        int numVerticesX = widthSegments + 1;
        int numVerticesY = heightSegments + 1;
        int totalVertices = numVerticesX * numVerticesY;

        try (
                SoftBodySharedSettings sharedSettings = new SoftBodySharedSettings();
                SoftBodyCreationSettings creationSettings = new SoftBodyCreationSettings()
        ) {
            float segmentWidth = clothWidth / widthSegments;
            float segmentHeight = clothHeight / heightSegments;
            float invMassPerVertex = (this.mass > 0) ? totalVertices / this.mass : 0f;

            Vec3[] vertexPositions = new Vec3[totalVertices];
            for (int y = 0; y < numVerticesY; ++y) {
                for (int x = 0; x < numVerticesX; ++x) {
                    int index = y * numVerticesX + x;
                    vertexPositions[index] = new Vec3((x * segmentWidth) - (clothWidth / 2.0f), 0, (y * segmentHeight) - (clothHeight / 2.0f));
                    Vertex v = new Vertex();
                    v.setPosition(vertexPositions[index]);
                    v.setInvMass(invMassPerVertex);
                    sharedSettings.addVertex(v);
                }
            }

            BiFunction<Integer, Integer, Integer> getIndex = (x, y) -> y * numVerticesX + x;
            for (int y = 0; y < numVerticesY; ++y) {
                for (int x = 0; x < numVerticesX; ++x) {
                    if (x < widthSegments) addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 1, y), compliance, vertexPositions);
                    if (y < heightSegments) addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x, y + 1), compliance, vertexPositions);
                    if (x < widthSegments && y < heightSegments) {
                        addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 1, y + 1), compliance, vertexPositions);
                        addEdge(sharedSettings, getIndex.apply(x + 1, y), getIndex.apply(x, y + 1), compliance, vertexPositions);
                    }
                    float bendCompliance = compliance * 10f;
                    if (x < widthSegments - 1) addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x + 2, y), bendCompliance, vertexPositions);
                    if (y < heightSegments - 1) addEdge(sharedSettings, getIndex.apply(x, y), getIndex.apply(x, y + 2), bendCompliance, vertexPositions);
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
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeInt(get(DATA_WIDTH_SEGMENTS));
        buf.writeInt(get(DATA_HEIGHT_SEGMENTS));
        buf.writeFloat(this.clothWidth);
        buf.writeFloat(this.clothHeight);
        buf.writeFloat(this.mass);
        buf.writeFloat(this.compliance);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        setServerData(DATA_WIDTH_SEGMENTS, buf.readInt());
        setServerData(DATA_HEIGHT_SEGMENTS, buf.readInt());
        this.clothWidth = buf.readFloat();
        this.clothHeight = buf.readFloat();
        this.mass = buf.readFloat();
        this.compliance = buf.readFloat();
    }
}