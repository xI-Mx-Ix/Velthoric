/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.type;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.VxBodyDataStore;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.network.synchronization.accessor.VxClientAccessor;
import net.xmx.velthoric.core.network.synchronization.accessor.VxDataAccessor;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Acts as a primary handle and management delegate for a physics object.
 * <p>
 * This class provides a high-level API for interacting with the underlying Structure of Arrays (SoA)
 * data storage. It coordinates synchronized data, lifecycle events, and serves as the bridge
 * between the game world and the physics simulation.
 *
 * @author xI-Mx-Ix
 */
public class VxBody {

    /**
     * The unique persistent identifier for this body instance.
     */
    protected final UUID physicsId;

    /**
     * Container for all data synchronized between server and client.
     */
    protected final VxSynchronizedData synchronizedData;

    /**
     * The immutable type definition that describes the properties of this body.
     */
    protected final VxBodyType type;

    /**
     * The native Jolt Body ID assigned to this instance. Only valid on the server.
     */
    private int bodyId = 0;

    /**
     * The current index of this body within the Structure of Arrays (SoA) data store.
     */
    private int dataStoreIndex = -1;

    /**
     * A session-unique integer ID used for optimized network packets.
     */
    private int networkId = -1;

    /**
     * The data store instance that manages the physical state of this body.
     */
    protected VxBodyDataStore dataStore;

    /**
     * The physics world this body is currently simulated in.
     */
    protected final VxPhysicsWorld physicsWorld;

    /**
     * Constructs a new server-side body instance.
     *
     * @param type         The type definition for this body.
     * @param physicsWorld The physics world it belongs to.
     * @param id           The unique identifier for this instance.
     */
    public VxBody(VxBodyType type, VxPhysicsWorld physicsWorld, UUID id) {
        this.type = type;
        this.physicsWorld = physicsWorld;
        this.physicsId = id;
        VxSynchronizedData.Builder builder = new VxSynchronizedData.Builder();
        this.defineSyncData(builder);
        this.synchronizedData = builder.build();
    }

    /**
     * Constructs a new client-side body instance.
     *
     * @param type The type definition for this body.
     * @param id   The unique identifier for this instance.
     */
    @Environment(EnvType.CLIENT)
    public VxBody(VxBodyType type, UUID id) {
        this.type = type;
        this.physicsId = id;
        VxSynchronizedData.Builder builder = new VxSynchronizedData.Builder();
        this.defineSyncData(builder);
        this.synchronizedData = builder.build();
        this.physicsWorld = null;
    }

    /**
     * Invoked when the body is successfully integrated into the native physics simulation.
     *
     * @param world The physics world instance.
     */
    public void onBodyAdded(VxPhysicsWorld world) {
    }

    /**
     * Invoked when the body is being removed from the physics world.
     *
     * @param world  The physics world instance.
     * @param reason The context behind the removal.
     */
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
    }

    /**
     * Logic executed before the physics simulation step on the physics thread.
     *
     * @param world The physics world instance.
     */
    public void onPrePhysicsTick(VxPhysicsWorld world) {
    }

    /**
     * Logic executed after the physics simulation step on the physics thread.
     *
     * @param world The physics world instance.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
    }

    /**
     * Logic executed once per game tick on the main server thread.
     *
     * @param level The server level instance.
     */
    public void onServerTick(ServerLevel level) {
    }

    /**
     * Invoked when the body is registered in a client-side level.
     *
     * @param level The client level instance.
     */
    @Environment(EnvType.CLIENT)
    public void onBodyAdded(ClientLevel level) {
    }

    /**
     * Invoked when the body is removed from a client-side level.
     *
     * @param level The client level instance.
     */
    @Environment(EnvType.CLIENT)
    public void onBodyRemoved(ClientLevel level) {
    }

    /**
     * Logic executed once per client tick.
     */
    @Environment(EnvType.CLIENT)
    public void onClientTick() {
    }

    /**
     * Defines the synchronized data fields for this body.
     *
     * @param builder The builder used to define data accessors.
     */
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        type.defineSyncData(builder);
    }

    /**
     * Retrieves the value of a synchronized data field.
     *
     * @param accessor The accessor key.
     * @param <T>      The type of data.
     * @return The current value.
     */
    public <T> T get(VxDataAccessor<T> accessor) {
        return this.synchronizedData.get(accessor);
    }

    /**
     * Sets the value of a server-authoritative data field and marks it for synchronization.
     *
     * @param accessor The accessor key.
     * @param value    The new value.
     * @param <T>      The type of data.
     */
    public <T> void setServerData(VxServerAccessor<T> accessor, T value) {
        if (this.physicsWorld == null) {
            throw new IllegalStateException("Cannot set SERVER-authoritative data on the Client. Data: " + accessor.getId());
        }
        this.synchronizedData.set(accessor, value);
        if (this.synchronizedData.isDirty()) {
            this.physicsWorld.getBodyManager().markCustomDataDirty(this);
        }
    }

    /**
     * Sets the value of a client-authoritative data field and marks it for synchronization.
     *
     * @param accessor The accessor key.
     * @param value    The new value.
     * @param <T>      The type of data.
     */
    public <T> void setClientData(VxClientAccessor<T> accessor, T value) {
        if (this.physicsWorld != null) {
            throw new IllegalStateException("Cannot set CLIENT-authoritative data on the Server. Data: " + accessor.getId());
        }
        this.synchronizedData.set(accessor, value);
        if (this.synchronizedData.isDirty()) {
            VxClientBodyManager.getInstance().markBodyDirty(this);
        }
    }

    /**
     * Callback for when a server-authoritative field is updated.
     */
    public void onSyncedDataUpdated(VxServerAccessor<?> accessor) {
    }

    /**
     * Callback for when a client-authoritative field is updated.
     */
    public void onSyncedDataUpdated(VxClientAccessor<?> accessor) {
    }

    /**
     * Writes all synchronized data to a buffer for initial spawning.
     *
     * @param buf The buffer to write into.
     */
    public void writeInitialSyncData(VxByteBuf buf) {
        List<VxSynchronizedData.Entry<?>> allEntries = this.synchronizedData.getAllEntries();
        VxSynchronizedData.writeEntries(buf, allEntries);
    }

    /**
     * Writes only modified synchronized data to a buffer.
     *
     * @param buf The buffer to write into.
     * @return True if data was written, false if no fields were dirty.
     */
    public boolean writeDirtySyncData(VxByteBuf buf) {
        List<VxSynchronizedData.Entry<?>> dirtyEntries = this.synchronizedData.getDirtyEntries();
        if (dirtyEntries == null) return false;
        VxSynchronizedData.writeEntries(buf, dirtyEntries);
        this.synchronizedData.clearDirty();
        return true;
    }

    /**
     * Serializes the internal state of the body for persistent storage.
     *
     * @param buf The buffer to write into.
     */
    public void writeInternalPersistenceData(VxByteBuf buf) {
        if (this.physicsWorld == null || this.dataStoreIndex == -1) return;
        VxServerBodyDataStore store = this.physicsWorld.getBodyManager().getDataStore();
        int idx = this.dataStoreIndex;

        buf.writeDouble(store.posX[idx]);
        buf.writeDouble(store.posY[idx]);
        buf.writeDouble(store.posZ[idx]);
        buf.writeFloat(store.rotX[idx]);
        buf.writeFloat(store.rotY[idx]);
        buf.writeFloat(store.rotZ[idx]);
        buf.writeFloat(store.rotW[idx]);
        buf.writeFloat(store.velX[idx]);
        buf.writeFloat(store.velY[idx]);
        buf.writeFloat(store.velZ[idx]);
        buf.writeFloat(store.angVelX[idx]);
        buf.writeFloat(store.angVelY[idx]);
        buf.writeFloat(store.angVelZ[idx]);

        EMotionType motionType = store.motionType[idx];
        buf.writeByte(motionType != null ? motionType.ordinal() : EMotionType.Static.ordinal());

        if (VxBehaviors.SOFT_PHYSICS.isSet(store.behaviorBits[idx])) {
            float[] vertices = this.physicsWorld.getBodyManager().retrieveSoftBodyVertices(this);
            if (vertices != null) {
                buf.writeInt(vertices.length);
                for (float val : vertices) buf.writeFloat(val);
            } else {
                buf.writeInt(0);
            }
        }

        this.type.getPersistenceHandler().write(this, buf);
    }

    /**
     * Deserializes and restores the internal state of the body from storage.
     *
     * @param buf The buffer to read from.
     */
    public void readInternalPersistenceData(VxByteBuf buf) {
        double px = buf.readDouble(), py = buf.readDouble(), pz = buf.readDouble();
        float rx = buf.readFloat(), ry = buf.readFloat(), rz = buf.readFloat(), rw = buf.readFloat();
        float vx = buf.readFloat(), vy = buf.readFloat(), vz = buf.readFloat();
        float avx = buf.readFloat(), avy = buf.readFloat(), avz = buf.readFloat();
        int motionOrdinal = buf.readByte();

        if (this.physicsWorld != null && this.dataStoreIndex != -1) {
            VxServerBodyDataStore store = this.physicsWorld.getBodyManager().getDataStore();
            int idx = this.dataStoreIndex;
            store.posX[idx] = px;
            store.posY[idx] = py;
            store.posZ[idx] = pz;
            store.rotX[idx] = rx;
            store.rotY[idx] = ry;
            store.rotZ[idx] = rz;
            store.rotW[idx] = rw;
            store.velX[idx] = vx;
            store.velY[idx] = vy;
            store.velZ[idx] = vz;
            store.angVelX[idx] = avx;
            store.angVelY[idx] = avy;
            store.angVelZ[idx] = avz;
            store.motionType[idx] = EMotionType.values()[Math.max(0, Math.min(motionOrdinal, EMotionType.values().length - 1))];
        }

        if (this.type.isSoft()) {
            int vertexCount = buf.readInt();
            if (vertexCount > 0) {
                float[] vertices = new float[vertexCount];
                for (int i = 0; i < vertexCount; i++) vertices[i] = buf.readFloat();
                if (this.physicsWorld != null) {
                    this.physicsWorld.getBodyManager().updateSoftBodyVertices(this, vertices);
                }
            }
        }

        this.type.getPersistenceHandler().read(this, buf);
    }

    /**
     * Populates the provided transform object with current data.
     *
     * @param outTransform The transform object to populate.
     */
    public void getTransform(VxTransform outTransform) {
        if (this.dataStore != null && this.dataStoreIndex != -1) {
            if (this.dataStoreIndex < this.dataStore.getCapacity()) {
                outTransform.getTranslation().set(
                        this.dataStore.posX[this.dataStoreIndex],
                        this.dataStore.posY[this.dataStoreIndex],
                        this.dataStore.posZ[this.dataStoreIndex]
                );
                outTransform.getRotation().set(
                        this.dataStore.rotX[this.dataStoreIndex],
                        this.dataStore.rotY[this.dataStoreIndex],
                        this.dataStore.rotZ[this.dataStoreIndex],
                        this.dataStore.rotW[this.dataStoreIndex]
                );
            }
        }
    }

    /**
     * @return A new transform instance representing the current state.
     */
    public VxTransform getTransform() {
        VxTransform transform = new VxTransform();
        getTransform(transform);
        return transform;
    }

    /**
     * Calculates the interpolated state for rendering.
     *
     * @param partialTicks The frame interpolation factor.
     * @param outState     The state object to populate.
     * @param tempPos      Temporary vector for calculation.
     * @param tempRot      Temporary quaternion for calculation.
     */
    @Environment(EnvType.CLIENT)
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        VxClientBodyManager manager = VxClientBodyManager.getInstance();
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.dataStoreIndex, partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);

        if (this.type.isSoft()) {
            outState.vertexData = manager.getInterpolator().getInterpolatedVertexData(manager.getStore(), this.dataStoreIndex, partialTicks);
        } else {
            outState.vertexData = null;
        }
    }

    /**
     * @return An Axis-Aligned Bounding Box used for frustum culling.
     */
    @Environment(EnvType.CLIENT)
    public AABB getCullingAABB(float inflation) {
        RVec3 lastPos = VxClientBodyManager.getInstance().getStore().lastKnownPosition[dataStoreIndex];
        return new AABB(
                lastPos.xx() - inflation, lastPos.yy() - inflation, lastPos.zz() - inflation,
                lastPos.xx() + inflation, lastPos.yy() + inflation, lastPos.zz() + inflation
        );
    }

    /**
     * @return True if the body has been fully initialized for rendering.
     */
    @Environment(EnvType.CLIENT)
    public boolean isInitialized() {
        return VxClientBodyManager.getInstance().getStore().render_isInitialized[dataStoreIndex];
    }

    /**
     * @return The persistent UUID of this body.
     */
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    /**
     * @return The type definition of this body.
     */
    public VxBodyType getType() {
        return type;
    }

    /**
     * @return The ResourceLocation ID of this body's type.
     */
    public ResourceLocation getTypeId() {
        return type.getTypeId();
    }

    /**
     * @return The physics world this body belongs to. Null on client.
     */
    @Nullable
    public VxPhysicsWorld getPhysicsWorld() {
        return this.physicsWorld;
    }

    /**
     * @return The internal synchronized data container.
     */
    public VxSynchronizedData getSynchronizedData() {
        return synchronizedData;
    }

    /**
     * @return The native Jolt ID.
     */
    public int getBodyId() {
        return this.bodyId;
    }

    /**
     * Sets the native Jolt ID.
     */
    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    /**
     * @return The index in the data store.
     */
    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    /**
     * Sets the data store and index for this body.
     */
    public void setDataStoreIndex(VxBodyDataStore dataStore, int dataStoreIndex) {
        this.dataStore = dataStore;
        this.dataStoreIndex = dataStoreIndex;
    }

    /**
     * @return The session-unique network ID.
     */
    public int getNetworkId() {
        return networkId;
    }

    /**
     * Sets the session-unique network ID.
     */
    public void setNetworkId(int networkId) {
        this.networkId = networkId;
    }
}