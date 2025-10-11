/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.registry.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The abstract base class for all physics objects in Velthoric.
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
    protected final VxObjectType<? extends VxBody> type;
    /** The ID of the body in the Jolt physics simulation. 0 if not yet added. Server-side only. */
    private int bodyId = 0;
    /** The index of this body's data in the data store. -1 if not yet added. */
    private int dataStoreIndex = -1;

    // --- Server-Only Fields ---
    protected final VxPhysicsWorld physicsWorld;

    /**
     * Server-side constructor.
     * @param type         The object type definition.
     * @param physicsWorld The physics world this body belongs to.
     * @param id           The unique UUID for this body.
     */
    protected VxBody(VxObjectType<? extends VxBody> type, VxPhysicsWorld physicsWorld, UUID id) {
        this.type = type;
        this.physicsWorld = physicsWorld;
        this.physicsId = id;
        this.synchronizedData = new VxSynchronizedData(EnvType.SERVER);
        this.defineSyncData();
    }

    /**
     * Client-side constructor.
     * @param type The object type definition.
     * @param id The unique UUID for this body.
     */
    @Environment(EnvType.CLIENT)
    protected VxBody(VxObjectType<? extends VxBody> type, UUID id) {
        this.type = type;
        this.physicsId = id;
        this.synchronizedData = new VxSynchronizedData(EnvType.CLIENT);
        this.defineSyncData();
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
     * Called on every physics thread tick for this body.
     * @param world The physics world instance.
     */
    public void physicsTick(VxPhysicsWorld world) {}

    /**
     * Called on every game thread tick for this body.
     * @param level The server level instance.
     */
    public void gameTick(ServerLevel level) {}


    // --- Common Synchronized Data Management ---

    /**
     * Called in the constructor to define all synchronized data fields for this object type.
     * Implementations should call {@code synchronizedData.define(ACCESSOR, defaultValue)}.
     */
    protected abstract void defineSyncData();

    /**
     * Gets the value of a synchronized data field.
     * @param accessor The accessor for the data.
     * @return The current value.
     */
    public <T> T getSyncData(VxDataAccessor<T> accessor) {
        return this.synchronizedData.get(accessor);
    }

    /**
     * Sets the value of a synchronized data field. If the value changes,
     * the object will automatically be queued for a network update.
     * Server-side only.
     * @param accessor The accessor for the data.
     * @param value The new value.
     */
    public <T> void setSyncData(VxDataAccessor<T> accessor, T value) {
        if (this.physicsWorld == null) return; // Guard against client-side calls
        this.synchronizedData.set(accessor, value);
        if (this.synchronizedData.isDirty()) {
            this.physicsWorld.getObjectManager().markCustomDataDirty(this);
        }
    }

    /**
     * Writes all defined synchronized data to the buffer. Used for spawning the object on the client.
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
     * Writes data needed to save this object to disk.
     * This is used for persistence.
     * @param buf The buffer to write to.
     */
    public void writePersistenceData(VxByteBuf buf) {}

    /**
     * Reads the data written by {@link #writePersistenceData(VxByteBuf)} when loading from disk.
     * @param buf The buffer to read from.
     */
    public void readPersistenceData(VxByteBuf buf) {}


    // --- Server-Side Physics State & Body Access ---

    /**
     * Populates the given transform with this body's current state from the data store.
     * Server-side only.
     * @param outTransform The transform object to populate.
     */
    public void getTransform(VxTransform outTransform) {
        if (this.physicsWorld != null && this.dataStoreIndex != -1) {
            this.physicsWorld.getObjectManager().getTransform(this.dataStoreIndex, outTransform);
        }
    }

    /**
     * Returns a new transform object representing this body's current state.
     * Server-side only.
     * @return A new VxTransform, or a default one if the body is not initialized.
     */
    public VxTransform getTransform() {
        if (this.physicsWorld != null && this.dataStoreIndex != -1) {
            VxTransform transform = new VxTransform();
            this.physicsWorld.getObjectManager().getTransform(this.dataStoreIndex, transform);
            return transform;
        }
        return new VxTransform();
    }


    // --- Client-Side Rendering Logic ---

    /**
     * Calculates the final, interpolated render state for the object for the current frame.
     * This method populates the provided output objects with the interpolated transform.
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
        RVec3 lastPos = VxClientObjectManager.getInstance().getStore().lastKnownPosition[dataStoreIndex];
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
        return VxClientObjectManager.getInstance().getStore().render_isInitialized[dataStoreIndex];
    }


    // --- Getters and Setters ---

    /**
     * @return The unique ID of this physics object instance.
     */
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    /**
     * @return The type definition of this object.
     */
    public VxObjectType<? extends VxBody> getType() {
        return type;
    }

    /**
     * Gets the ResourceLocation that identifies this object's type.
     *
     * @return The type identifier.
     */
    public ResourceLocation getTypeId() {
        return type.getTypeId();
    }

    /**
     * @return The physics world this object belongs to. Null on the client.
     */
    @Nullable
    public VxPhysicsWorld getPhysicsWorld() {
        return this.physicsWorld;
    }

    /**
     * @return The object's synchronized data container.
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
     * Sets the Jolt body ID. This is called by the VxObjectManager after creation.
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
     * Sets the index in the data store. This is called by the ObjectManager on addition.
     * @param dataStoreIndex The new data store index.
     */
    public void setDataStoreIndex(int dataStoreIndex) {
        this.dataStoreIndex = dataStoreIndex;
    }
}