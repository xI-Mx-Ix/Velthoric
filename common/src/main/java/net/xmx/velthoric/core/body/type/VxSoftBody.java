/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.type;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for all soft body physics bodies.
 * A soft body is a deformable object composed of a collection of vertices or particles,
 * simulated using techniques like mass-spring systems. Users should inherit from this
 * class to create custom soft bodies.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxSoftBody extends VxBody {

    /**
     * Server-side constructor for a soft body.
     *
     * @param type  The body type definition.
     * @param world The physics world this body belongs to.
     * @param id    The unique UUID for this body.
     */
    protected VxSoftBody(VxBodyType<? extends VxSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor for a soft body.
     *
     * @param type The body type definition.
     * @param id The unique UUID for this body.
     */
    @Environment(EnvType.CLIENT)
    protected VxSoftBody(VxBodyType<? extends VxSoftBody> type, UUID id) {
        super(type, id);
    }

    /**
     * Defines and creates the Jolt soft body using the provided factory.
     * This method must be implemented by subclasses to define the properties
     * of the soft body.
     *
     * @param factory The factory provided by the VxServerBodyManager to create the body.
     * @return The body ID assigned by Jolt.
     */
    public abstract int createJoltBody(VxSoftBodyFactory factory);

    @Override
    public void writeInternalPersistenceData(VxByteBuf buf) {
        // Write standard physics state (transform, velocities, user data)
        super.writeInternalPersistenceData(buf);

        if (this.physicsWorld == null) return;

        // Retrieve current vertex data from the manager or Jolt interface
        // Assuming the manager exposes a way to get the live vertex positions for persistence
        float[] vertices = this.physicsWorld.getBodyManager().retrieveSoftBodyVertices(this);

        if (vertices != null) {
            buf.writeInt(vertices.length);
            for (float val : vertices) {
                buf.writeFloat(val);
            }
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void readInternalPersistenceData(VxByteBuf buf) {
        // Read standard physics state
        super.readInternalPersistenceData(buf);

        // Read soft body specific vertex data
        int vertexCount = buf.readInt();
        if (vertexCount > 0) {
            float[] vertices = new float[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                vertices[i] = buf.readFloat();
            }
            // Apply the vertices to the physics simulation if needed, or store for initialization
            if (this.physicsWorld != null) {
                this.physicsWorld.getBodyManager().updateSoftBodyVertices(this, vertices);
            }
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        VxClientBodyManager manager = VxClientBodyManager.getInstance();
        // Calculate the base interpolated transform (position and rotation).
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.getDataStoreIndex(), partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);

        // Also calculate the interpolated vertex data for the soft body mesh. This is generic.
        outState.vertexData = manager.getInterpolator().getInterpolatedVertexData(manager.getStore(), this.getDataStoreIndex(), partialTicks);
    }
}