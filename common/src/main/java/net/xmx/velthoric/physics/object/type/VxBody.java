/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.fabricmc.api.EnvType;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The abstract base class for all physics objects in Velthoric on the server side.
 * This class acts as a lightweight handle for a physics body, with most of its state
 * stored in a data-oriented {@link net.xmx.velthoric.physics.object.manager.VxObjectDataStore}
 * for efficient processing. It contains all server-side game logic for the object.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBody {

    // Fields
    protected final UUID physicsId;
    protected final VxObjectType<? extends VxBody> type;
    protected final VxPhysicsWorld world;
    protected final VxSynchronizedData synchronizedData;
    protected int bodyId = 0;
    protected int dataStoreIndex = -1;

    // Constructor
    protected VxBody(VxObjectType<? extends VxBody> type, VxPhysicsWorld world, UUID id) {
        this.type = type;
        this.world = world;
        this.physicsId = id;
        this.synchronizedData = new VxSynchronizedData(EnvType.SERVER);
        this.defineSyncData();
    }

    // Lifecycle and Ticking
    public void onBodyAdded(VxPhysicsWorld world) {}
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {}
    public void physicsTick(VxPhysicsWorld world) {}
    public void gameTick(ServerLevel level) {}


    // Synchronized Data Management

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
     * @param accessor The accessor for the data.
     * @param value The new value.
     */
    public <T> void setSyncData(VxDataAccessor<T> accessor, T value) {
        this.synchronizedData.set(accessor, value);
        if (this.synchronizedData.isDirty()) {
            this.world.getObjectManager().markCustomDataDirty(this);
        }
    }

    /**
     * Writes all defined synchronized data to the buffer. Used for spawning the object on the client.
     *
     * @param buf The buffer to write to.
     */
    public void writeInitialSyncData(VxByteBuf buf) {
        List<VxSynchronizedData.Entry<?>> allEntries = this.synchronizedData.getAllEntries();
        VxSynchronizedData.writeEntries(buf, allEntries);
    }

    /**
     * Writes only the dirty synchronized data entries to the buffer for updates.
     *
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


    // Persistence
    /**
     * Writes data needed to save this object to disk.
     * This is used for persistence.
     *
     * @param buf The buffer to write to.
     */
    public void writePersistenceData(VxByteBuf buf) {}

    /**
     * Reads the data written by {@link #writePersistenceData(VxByteBuf)} when loading from disk.
     *
     * @param buf The buffer to read from.
     */
    public void readPersistenceData(VxByteBuf buf) {}


    // Physics State & Body Access
    public void getTransform(VxTransform outTransform) {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().getTransform(this.dataStoreIndex, outTransform);
        }
    }

    public VxTransform getTransform() {
        if (this.dataStoreIndex != -1) {
            VxTransform transform = new VxTransform();
            this.world.getObjectManager().getTransform(this.dataStoreIndex, transform);
            return transform;
        }
        // Return a default or throw an exception if the object is not yet fully initialized
        return new VxTransform();
    }

    @Nullable
    public Body getBody() {
        if (bodyId == 0) {
            return null;
        }
        VxBody found = world.getObjectManager().getByBodyId(bodyId);
        if (found == this) {
            try (BodyLockWrite lock = new BodyLockWrite(world.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                }
            }
        }
        return null;
    }

    @Nullable
    public ConstBody getConstBody() {
        if (bodyId == 0) {
            return null;
        }
        VxBody found = world.getObjectManager().getByBodyId(bodyId);
        if (found == this) {
            try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                }
            }
        }
        return null;
    }


    // Getters and Setters
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    public VxObjectType<? extends VxBody> getType() {
        return type;
    }

    public VxPhysicsWorld getWorld() {
        return this.world;
    }

    public int getBodyId() {
        return this.bodyId;
    }

    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    public void setDataStoreIndex(int dataStoreIndex) {
        this.dataStoreIndex = dataStoreIndex;
    }

    public VxSynchronizedData getSynchronizedData() {
        return synchronizedData;
    }
}