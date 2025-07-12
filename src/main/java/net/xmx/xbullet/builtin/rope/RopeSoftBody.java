package net.xmx.xbullet.builtin.rope;

import com.github.stephengold.joltjni.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.SoftPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class RopeSoftBody extends SoftPhysicsObject {

    public static final String TYPE_IDENTIFIER = "xbullet:rope";

    private float ropeLength;
    private int numSegments;
    private float ropeRadius;
    private float mass;
    private float compliance;

    public RopeSoftBody(UUID physicsId, Level level, PhysicsTransform initialTransform, IPhysicsObjectProperties properties,
                        float ropeLength, int numSegments, float ropeRadius, float mass, float compliance) {
        super(physicsId, level, TYPE_IDENTIFIER, initialTransform, properties);
        this.ropeLength = ropeLength;
        this.numSegments = numSegments;
        this.ropeRadius = ropeRadius;
        this.mass = mass;
        this.compliance = compliance;
    }

    public RopeSoftBody(UUID physicsId, Level level, String typeId, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable FriendlyByteBuf initialData) {
        super(physicsId, level, typeId, initialTransform, properties);
        this.ropeLength = 10.0f;
        this.numSegments = 20;
        this.ropeRadius = 0.1f;
        this.mass = 5.0f;
        this.compliance = 0.001f;
    }

    @Override
    protected void addAdditionalData(FriendlyByteBuf buf) {
        super.addAdditionalData(buf);
        buf.writeFloat(this.ropeLength);
        buf.writeInt(this.numSegments);
        buf.writeFloat(this.ropeRadius);
        buf.writeFloat(this.mass);
        buf.writeFloat(this.compliance);
    }

    @Override
    protected void readAdditionalData(FriendlyByteBuf buf) {
        super.readAdditionalData(buf);
        this.ropeLength = buf.readFloat();
        this.numSegments = buf.readInt();
        this.ropeRadius = buf.readFloat();
        this.mass = buf.readFloat();
        this.compliance = buf.readFloat();
    }

    @Override
    protected void configureAdditionalSoftBodyCreationSettings(SoftBodyCreationSettings settings) {
        settings.setVertexRadius(this.ropeRadius);
    }

    @Override
    protected SoftBodySharedSettings buildSharedSettings() {
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
            try (Edge edge = new Edge()) {
                edge.setVertex(0, i);
                edge.setVertex(1, i + 1);
                edge.setCompliance(this.compliance);
                edge.setRestLength(segmentLength);
                settings.addEdgeConstraint(edge);
            }
        }

        settings.optimize();
        return settings;
    }
}