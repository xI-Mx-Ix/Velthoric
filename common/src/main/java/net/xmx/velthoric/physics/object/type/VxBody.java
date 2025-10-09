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
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.object.type.internal.VxInternalBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The abstract base class for all physics objects in Velthoric on the server side.
 * This class is the high-level representation, handling persistence, network synchronization,
 * and game logic. It holds a lightweight {@link VxInternalBody} handle for direct
 * interaction with the physics simulation. Users should inherit from this class's children,
 * {@link VxRigidBody} or {@link VxSoftBody}.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBody {

    // Fields
    protected final UUID physicsId;
    protected final VxObjectType<? extends VxBody> type;
    protected final VxPhysicsWorld physicsWorld;
    protected final VxSynchronizedData synchronizedData;
    protected final VxInternalBody internalBody;

    // Constructor
    protected VxBody(VxObjectType<? extends VxBody> type, VxPhysicsWorld physicsWorld, UUID id) {
        this.type = type;
        this.physicsWorld = physicsWorld;
        this.physicsId = id;
        this.internalBody = new VxInternalBody();
        this.synchronizedData = new VxSynchronizedData(EnvType.SERVER);
        this.defineSyncData();
    }

    // Lifecycle and Ticking
    /**
     * Called when the body is successfully added to the Jolt physics world.
     *
     * @param world The physics world the body was added to.
     */
    public void onBodyAdded(VxPhysicsWorld world) {}

    /**
     * Called when the body is removed from the world.
     *
     * @param world The physics world.
     * @param reason The reason for removal.
     */
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {}

    /**
     * Called on every physics thread tick for this body.
     *
     * @param world The physics world instance.
     */
    public void physicsTick(VxPhysicsWorld world) {}

    /**
     * Called on every game thread tick for this body.
     *
     * @param level The server level instance.
     */
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
            this.physicsWorld.getObjectManager().markCustomDataDirty(this);
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
        if (this.internalBody.getDataStoreIndex() != -1) {
            this.physicsWorld.getObjectManager().getTransform(this.internalBody.getDataStoreIndex(), outTransform);
        }
    }

    public VxTransform getTransform() {
        if (this.internalBody.getDataStoreIndex() != -1) {
            VxTransform transform = new VxTransform();
            this.physicsWorld.getObjectManager().getTransform(this.internalBody.getDataStoreIndex(), transform);
            return transform;
        }
        // Return a default or throw an exception if the object is not yet fully initialized
        return new VxTransform();
    }

    @Nullable
    public Body getBody() {
        int bodyId = internalBody.getBodyId();
        if (bodyId == 0) {
            return null;
        }
        VxBody found = physicsWorld.getObjectManager().getByBodyId(bodyId);
        if (found == this) {
            try (BodyLockWrite lock = new BodyLockWrite(physicsWorld.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                }
            }
        }
        return null;
    }

    @Nullable
    public ConstBody getConstBody() {
        int bodyId = internalBody.getBodyId();
        if (bodyId == 0) {
            return null;
        }
        VxBody found = physicsWorld.getObjectManager().getByBodyId(bodyId);
        if (found == this) {
            try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
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

    public VxPhysicsWorld getPhysicsWorld() {
        return this.physicsWorld;
    }

    /**
     * Gets the internal lightweight handle for this body.
     * This should primarily be used by the physics engine internals.
     *
     * @return The internal body handle.
     */
    public VxInternalBody getInternalBody() {
        return internalBody;
    }

    public VxSynchronizedData getSynchronizedData() {
        return synchronizedData;
    }
}