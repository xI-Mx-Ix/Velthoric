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
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.VxBodyDataStore;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.network.synchronization.accessor.VxClientAccessor;
import net.xmx.velthoric.core.network.synchronization.accessor.VxDataAccessor;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The abstract base class for all physics bodies in Velthoric.
 * This unified class handles both server-side logic (physics simulation, persistence)
 * and client-side logic (rendering state calculation).
 * Users should inherit from this class's children, {@link VxRigidBody} or {@link VxSoftBody}.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBody {

    // --- Common Fields ---
    protected final UUID physicsId;
    protected final VxSynchronizedData synchronizedData;
    protected final VxBodyType<? extends VxBody> type;

    /**
     * Flag indicating whether this body should be serialized to disk.
     * <p>
     * If true, the body is saved when the chunk is unloaded or saved.
     * If false, the body is discarded upon unload (useful for temporary debris or effects).
     * Defaults to true.
     */
    private boolean persistent = true;
    /**
     * The ID of the body in the Jolt physics simulation. 0 if not yet added. Server-side only.
     */
    private int bodyId = 0;
    /**
     * The index of this body's data in the data store. -1 if not yet added.
     */
    private int dataStoreIndex = -1;
    /**
     * The session-specific network ID for this body. -1 if not assigned. Server-side only.
     */
    private int networkId = -1;

    /**
     * The data store backing this body.
     * On the Server, this is the {@link VxServerBodyDataStore} (Simulation state).
     * On the Client, this is the {@link VxClientBodyDataStore} (Interpolated render state).
     */
    protected VxBodyDataStore dataStore;

    // --- Server-Only Fields ---
    protected final VxPhysicsWorld physicsWorld;

    /**
     * Server-side constructor.
     * @param type         The body type definition.
     * @param physicsWorld The physics world this body belongs to.
     * @param id           The unique UUID for this body.
     */
    protected VxBody(VxBodyType<? extends VxBody> type, VxPhysicsWorld physicsWorld, UUID id) {
        this.type = type;
        this.physicsWorld = physicsWorld;
        this.physicsId = id;
        this.persistent = type.isPersistent();
        // Use the builder to construct the synchronized data
        VxSynchronizedData.Builder builder = new VxSynchronizedData.Builder();
        this.defineSyncData(builder);
        this.synchronizedData = builder.build();
    }

    /**
     * Client-side constructor.
     * @param type The body type definition.
     * @param id The unique UUID for this body.
     */
    @Environment(EnvType.CLIENT)
    protected VxBody(VxBodyType<? extends VxBody> type, UUID id) {
        this.type = type;
        this.physicsId = id;
        this.persistent = type.isPersistent();
        // Use the builder to construct the synchronized data
        VxSynchronizedData.Builder builder = new VxSynchronizedData.Builder();
        this.defineSyncData(builder);
        this.synchronizedData = builder.build();
        // Server-only fields are left null on the client.
        this.physicsWorld = null;
    }

    // --- Server-Side Lifecycle and Ticking ---

    /**
     * Called when the body is successfully added to the Jolt physics world.
     * @param world The physics world the body was added to.
     */
    public void onBodyAdded(VxPhysicsWorld world) {}

    /**
     * Called when the body is removed from the world.
     * @param world The physics world.
     * @param reason The reason for removal.
     */
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {}

    /**
     * Called on each physics-step tick for this body.
     * <p>
     * Runs exclusively during the physics thread at a fixed rate
     * (typically 60 times per second). Sleeping bodies do not receive
     * physics ticks.
     *
     * @param world The physics world instance.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {}

    /**
     * Called on each game-thread tick for this body.
     * <p>
     * Executed only on the server thread. This method is not invoked
     * for sleeping bodies unless they are explicitly woken up.
     *
     * @param level The server level instance.
     */
    public void onServerTick(ServerLevel level) {}


    // --- Client-Side Lifecycle and Ticking ---

    /**
     * Called when the body is successfully added to the client world.
     * This can be used to initialize client-side state or effects related to the body.
     *
     * @param level The client level the body was added to.
     */
    public void onBodyAdded(ClientLevel level) {}

    /**
     * Called when the body is removed from the client world.
     * Use this to clean up any client-side state or effects associated with the body.
     *
     * @param level The client level the body was removed from.
     */
    public void onBodyRemoved(ClientLevel level) {}


    /**
     * Called on every client tick for this body (Client Only).
     * This occurs before interpolation and rendering updates.
     */
    @Environment(EnvType.CLIENT)
    public void onClientTick() {}


    // --- Common Synchronized Data Management ---

    /**
     * Called in the constructor to define all synchronized data fields for this body type.
     * Implementations should call {@code builder.define(ACCESSOR, defaultValue)}.
     * @param builder The builder to define data on.
     */
    protected abstract void defineSyncData(VxSynchronizedData.Builder builder);

    /**
     * Gets the value of a synchronized data field. Reading is allowed on both sides.
     *
     * @param accessor The accessor for the data (can be Client or Server type).
     * @return The current value.
     */
    public <T> T get(VxDataAccessor<T> accessor) {
        return this.synchronizedData.get(accessor);
    }

    /**
     * Sets SERVER-Authoritative data.
     * This method is only for use on the Logical Server.
     *
     * @param accessor A strongly-typed {@link VxServerAccessor}.
     * @param value    The new value.
     * @throws IllegalStateException If called on the client (where physicsWorld is null).
     */
    public <T> void setServerData(VxServerAccessor<T> accessor, T value) {
        if (this.physicsWorld == null) {
            throw new IllegalStateException("Cannot set SERVER-authoritative data on the Client. Data: " + accessor.getId());
        }

        // Apply update to internal storage
        this.synchronizedData.set(accessor, value);

        // Mark dirty for S2C replication via the server manager
        if (this.synchronizedData.isDirty()) {
            this.physicsWorld.getBodyManager().markCustomDataDirty(this);
        }
    }

    /**
     * Sets CLIENT-Authoritative data.
     * This method is only for use on the Logical Client.
     *
     * @param accessor A strongly-typed {@link VxClientAccessor}.
     * @param value    The new value.
     * @throws IllegalStateException If called on the server (where physicsWorld is not null).
     */
    public <T> void setClientData(VxClientAccessor<T> accessor, T value) {
        if (this.physicsWorld != null) {
            throw new IllegalStateException("Cannot set CLIENT-authoritative data on the Server. Data: " + accessor.getId());
        }

        // Apply update to internal storage
        this.synchronizedData.set(accessor, value);

        // Mark dirty for C2S replication via the client manager
        if (this.synchronizedData.isDirty()) {
            VxClientBodyManager.getInstance().markBodyDirty(this);
        }
    }

    /**
     * Called when SERVER-authoritative synchronized data has been updated.
     * <p>
     * - On Client: Triggered when an S2C packet updates this data.
     *
     * @param accessor The specific server data accessor that was just updated.
     */
    public void onSyncedDataUpdated(VxServerAccessor<?> accessor) {
        // This is a hook for subclasses to implement.
    }

    /**
     * Called when CLIENT-authoritative synchronized data has been updated.
     * <p>
     * - On Server: Triggered when a C2S packet updates this data.
     * - On Client: Triggered when other clients update this data (via Server relay).
     *
     * @param accessor The specific client data accessor that was just updated.
     */
    public void onSyncedDataUpdated(VxClientAccessor<?> accessor) {
        // This is a hook for subclasses to implement.
    }

    /**
     * Writes all defined synchronized data to the buffer. Used for spawning the body on the client.
     * @param buf The buffer to write to.
     */
    public void writeInitialSyncData(VxByteBuf buf) {
        List<VxSynchronizedData.Entry<?>> allEntries = this.synchronizedData.getAllEntries();
        VxSynchronizedData.writeEntries(buf, allEntries);
    }

    /**
     * Writes only the dirty synchronized data entries to the buffer for updates.
     * @param buf The buffer to write to.
     * @return True if any data was written, false otherwise.
     */
    public boolean writeDirtySyncData(VxByteBuf buf) {
        List<VxSynchronizedData.Entry<?>> dirtyEntries = this.synchronizedData.getDirtyEntries();
        if (dirtyEntries == null) {
            return false;
        }
        VxSynchronizedData.writeEntries(buf, dirtyEntries);
        this.synchronizedData.clearDirty();
        return true;
    }


    // --- Server-Side Persistence ---

    /**
     * Writes the comprehensive internal state of this body to the buffer for persistence.
     * This includes standard physics data (position, rotation, velocity) and delegates
     * to {@link #writePersistenceData(VxByteBuf)} for custom user data.
     * <p>
     * Subclasses (like {@link VxSoftBody}) should override this to save additional
     * internal state (e.g., vertex data) but must call super or handle the standard data manually.
     *
     * @param buf The buffer to write the state to.
     */
    public void writeInternalPersistenceData(VxByteBuf buf) {
        if (this.physicsWorld == null || this.dataStoreIndex == -1) {
            return;
        }
        VxServerBodyDataStore store = this.physicsWorld.getBodyManager().getDataStore();
        int idx = this.dataStoreIndex;

        // Write Position (as double for precision storage)
        buf.writeDouble(store.posX[idx]);
        buf.writeDouble(store.posY[idx]);
        buf.writeDouble(store.posZ[idx]);

        // Write Rotation
        buf.writeFloat(store.rotX[idx]);
        buf.writeFloat(store.rotY[idx]);
        buf.writeFloat(store.rotZ[idx]);
        buf.writeFloat(store.rotW[idx]);

        // Write Velocities
        buf.writeFloat(store.velX[idx]);
        buf.writeFloat(store.velY[idx]);
        buf.writeFloat(store.velZ[idx]);
        buf.writeFloat(store.angVelX[idx]);
        buf.writeFloat(store.angVelY[idx]);
        buf.writeFloat(store.angVelZ[idx]);

        // Write Motion Type
        EMotionType motionType = store.motionType[idx];
        buf.writeByte(motionType != null ? motionType.ordinal() : EMotionType.Static.ordinal());

        // Write User Persistence Data
        this.writePersistenceData(buf);
    }

    /**
     * Reads the internal state written by {@link #writeInternalPersistenceData(VxByteBuf)}.
     * This reconstructs the physics state and custom user data.
     *
     * @param buf The buffer containing the body state.
     */
    public void readInternalPersistenceData(VxByteBuf buf) {
        // Read Position (read as double)
        double px = buf.readDouble();
        double py = buf.readDouble();
        double pz = buf.readDouble();

        // Read Rotation
        float rx = buf.readFloat();
        float ry = buf.readFloat();
        float rz = buf.readFloat();
        float rw = buf.readFloat();

        // Read Velocities
        float vx = buf.readFloat();
        float vy = buf.readFloat();
        float vz = buf.readFloat();
        float avx = buf.readFloat();
        float avy = buf.readFloat();
        float avz = buf.readFloat();

        // Read Motion Type
        int motionOrdinal = buf.readByte();

        // Apply read values to the current data store if connected, or hold them for initialization
        if (this.physicsWorld != null && this.dataStoreIndex != -1) {
            VxServerBodyDataStore store = this.physicsWorld.getBodyManager().getDataStore();
            int idx = this.dataStoreIndex;

            // Store directly as doubles to preserve precision from persistence
            store.posX[idx] = px;
            store.posY[idx] = py;
            store.posZ[idx] = pz;

            store.rotX[idx] = rx; store.rotY[idx] = ry; store.rotZ[idx] = rz; store.rotW[idx] = rw;
            store.velX[idx] = vx; store.velY[idx] = vy; store.velZ[idx] = vz;
            store.angVelX[idx] = avx; store.angVelY[idx] = avy; store.angVelZ[idx] = avz;
            store.motionType[idx] = EMotionType.values()[Math.max(0, Math.min(motionOrdinal, EMotionType.values().length - 1))];
        }

        // Read User Persistence Data
        this.readPersistenceData(buf);
    }

    /**
     * Writes data needed to save this body to disk.
     * This is used for persistence.
     * @param buf The buffer to write to.
     */
    public void writePersistenceData(VxByteBuf buf) {}

    /**
     * Reads the data written by {@link #writePersistenceData(VxByteBuf)} when loading from disk.
     * @param buf The buffer to read from.
     */
    public void readPersistenceData(VxByteBuf buf) {}


    /**
     * Populates the given transform with this body's current state from the data store.
     * <p>
     * This method is agnostic to the execution side (Client/Server).
     *
     * @param outTransform The transform object to populate.
     */
    public void getTransform(VxTransform outTransform) {
        if (this.dataStore != null && this.dataStoreIndex != -1) {
            // Ensure the index is within bounds before access
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
     * Returns a new transform representing this body's current state.
     *
     * @return A new VxTransform containing the position and rotation.
     */
    public VxTransform getTransform() {
        VxTransform transform = new VxTransform();
        getTransform(transform);
        return transform;
    }

    // --- Client-Side Rendering Logic ---

    /**
     * Calculates the final, interpolated render state for the body for the current frame.
     * This method populates the provided output bodies with the interpolated transform.
     * Client-side only.
     *
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outState     The {@link VxRenderState} object to populate with the final state.
     * @param tempPos      A reusable {@link RVec3} to store the intermediate interpolated position.
     * @param tempRot      A reusable {@link Quat} to store the intermediate interpolated rotation.
     */
    @Environment(EnvType.CLIENT)
    public abstract void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot);

    /**
     * Gets the Axis-Aligned Bounding Box used for frustum culling.
     * Client-side only.
     *
     * @param inflation The amount to inflate the box by, to prevent culling at screen edges.
     * @return A new AABB for culling.
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
     * Checks if the client-side representation of this body has been fully initialized.
     * Client-side only.
     *
     * @return True if initialized, false otherwise.
     */
    @Environment(EnvType.CLIENT)
    public boolean isInitialized() {
        return VxClientBodyManager.getInstance().getStore().render_isInitialized[dataStoreIndex];
    }


    // --- Getters and Setters ---

    /**
     * @return The unique ID of this physics body instance.
     */
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    /**
     * @return The type definition of this body.
     */
    public VxBodyType<? extends VxBody> getType() {
        return type;
    }

    /**
     * Gets the ResourceLocation that identifies this body's type.
     *
     * @return The type identifier.
     */
    public ResourceLocation getTypeId() {
        return type.getTypeId();
    }

    /**
     * Sets the persistence state of this body.
     *
     * @param persistent true to enable saving to disk, false to make the body temporary.
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Checks if this body is marked for persistence.
     *
     * @return true if the body should be saved to disk, false otherwise.
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * @return The physics world this body belongs to. Null on the client.
     */
    @Nullable
    public VxPhysicsWorld getPhysicsWorld() {
        return this.physicsWorld;
    }

    /**
     * @return The body's synchronized data container.
     */
    public VxSynchronizedData getSynchronizedData() {
        return synchronizedData;
    }

    /**
     * Gets the Jolt body ID.
     * @return The body ID. Server-side only.
     */
    public int getBodyId() {
        return this.bodyId;
    }

    /**
     * Sets the Jolt body ID. This is called by the VxServerBodyManager after creation.
     * @param bodyId The new body ID. Server-side only.
     */
    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    /**
     * Gets the index in the data store.
     * @return The data store index.
     */
    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    /**
     * Sets the index in the data store. This is called by the VxServerBodyManager on addition.
     * @param dataStoreIndex The new data store index.
     */
    public void setDataStoreIndex(VxBodyDataStore dataStore, int dataStoreIndex) {
        this.dataStore = dataStore;
        this.dataStoreIndex = dataStoreIndex;
    }

    /**
     * Gets the session-specific network ID.
     * @return The network ID. Server-side only.
     */
    public int getNetworkId() {
        return networkId;
    }

    /**
     * Sets the session-specific network ID. Called by the VxServerBodyManager on addition.
     * @param networkId The new network ID. Server-side only.
     */
    public void setNetworkId(int networkId) {
        this.networkId = networkId;
    }
}