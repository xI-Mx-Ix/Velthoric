/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.builtin.rope;

import com.github.stephengold.joltjni.Edge;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.Vertex;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class RopeSoftBody extends VxSoftBody {

    private float ropeLength;
    private int numSegments;
    private float ropeRadius;
    private float mass;
    private float compliance;

    public RopeSoftBody(VxObjectType<RopeSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.ropeLength = 10.0f;
        this.numSegments = 20;
        this.ropeRadius = 0.1f;
        this.mass = 5.0f;
        this.compliance = 0.001f;
    }

    public void setConfiguration(float ropeLength, int numSegments, float ropeRadius, float mass, float compliance) {
        this.ropeLength = ropeLength;
        this.numSegments = numSegments;
        this.ropeRadius = ropeRadius;
        this.mass = mass;
        this.compliance = compliance;
        this.markDataDirty();
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeFloat(this.ropeLength);
        buf.writeInt(this.numSegments);
        buf.writeFloat(this.ropeRadius);
        buf.writeFloat(this.mass);
        buf.writeFloat(this.compliance);
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.ropeLength = buf.readFloat();
        this.numSegments = buf.readInt();
        this.ropeRadius = buf.readFloat();
        this.mass = buf.readFloat();
        this.compliance = buf.readFloat();
    }

    @Override
    public SoftBodyCreationSettings createSoftBodyCreationSettings(SoftBodySharedSettings sharedSettings) {
        SoftBodyCreationSettings settings = new SoftBodyCreationSettings();
        settings.setSettings(sharedSettings);
        settings.setObjectLayer(VxLayers.DYNAMIC);
        settings.setVertexRadius(this.ropeRadius);
        return settings;
    }

    @Override
    public SoftBodySharedSettings createSoftBodySharedSettings() {
        int safeNumSegments = Math.max(1, this.numSegments);
        float safeMass = Math.max(0.001f, this.mass);
        float safeRopeLength = Math.max(0.1f, this.ropeLength);
        int numNodes = safeNumSegments + 1;
        SoftBodySharedSettings settings = new SoftBodySharedSettings();
        float segmentLength = safeRopeLength / (float) safeNumSegments;
        float invMassPerNode = (safeMass > 0) ? numNodes / safeMass : 0f;

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

        settings.optimize();
        return settings;
    }
}