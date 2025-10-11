/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.rope;

import com.github.stephengold.joltjni.Edge;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.Vertex;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.object.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A soft body physics object that simulates a rope or cable.
 *
 * @author xI-Mx-Ix
 */
public class RopeSoftBody extends VxSoftBody {

    public static final VxDataAccessor<Float> DATA_ROPE_RADIUS = VxDataAccessor.create(RopeSoftBody.class, VxDataSerializers.FLOAT);

    private float ropeLength;
    private int numSegments;
    private float mass;
    private float compliance;

    /**
     * Server-side constructor.
     */
    public RopeSoftBody(VxObjectType<RopeSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.ropeLength = 10.0f;
        this.numSegments = 20;
        this.mass = 5.0f;
        this.compliance = 0.001f;
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public RopeSoftBody(UUID id, ResourceLocation typeId, EBodyType objectType) {
        super(id, typeId, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_ROPE_RADIUS, 0.1f);
    }

    public void setConfiguration(float ropeLength, int numSegments, float ropeRadius, float mass, float compliance) {
        this.ropeLength = ropeLength;
        this.numSegments = numSegments;
        this.setSyncData(DATA_ROPE_RADIUS, ropeRadius);
        this.mass = mass;
        this.compliance = compliance;
    }

    @Override
    public int createJoltBody(VxSoftBodyFactory factory) {
        try (
                SoftBodySharedSettings sharedSettings = new SoftBodySharedSettings();
                SoftBodyCreationSettings creationSettings = new SoftBodyCreationSettings()
        ) {
            int safeNumSegments = Math.max(1, this.numSegments);
            float safeMass = Math.max(0.001f, this.mass);
            int numNodes = safeNumSegments + 1;
            float segmentLength = this.ropeLength / (float) safeNumSegments;
            float invMassPerNode = (safeMass > 0) ? numNodes / safeMass : 0f;

            for (int i = 0; i < numNodes; i++) {
                sharedSettings.addVertex(new Vertex().setPosition(new Vec3(0, -i * segmentLength, 0)).setInvMass(invMassPerNode));
            }

            for (int i = 0; i < numNodes - 1; i++) {
                Edge edge = new Edge();
                edge.setVertex(0, i);
                edge.setVertex(1, i + 1);
                edge.setCompliance(this.compliance);
                edge.setRestLength(segmentLength);
                sharedSettings.addEdgeConstraint(edge);
            }

            sharedSettings.optimize();
            creationSettings.setSettings(sharedSettings);
            creationSettings.setObjectLayer(VxLayers.DYNAMIC);
            creationSettings.setVertexRadius(getSyncData(DATA_ROPE_RADIUS));
            return factory.create(sharedSettings, creationSettings);
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeFloat(this.ropeLength);
        buf.writeInt(this.numSegments);
        buf.writeFloat(getSyncData(DATA_ROPE_RADIUS));
        buf.writeFloat(this.mass);
        buf.writeFloat(this.compliance);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        this.ropeLength = buf.readFloat();
        this.numSegments = buf.readInt();
        this.setSyncData(DATA_ROPE_RADIUS, buf.readFloat());
        this.mass = buf.readFloat();
        this.compliance = buf.readFloat();
    }
}