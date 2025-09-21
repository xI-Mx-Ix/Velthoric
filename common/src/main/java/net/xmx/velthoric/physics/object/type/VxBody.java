/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

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

    protected final UUID physicsId;
    protected final VxObjectType<? extends VxBody> type;
    protected final VxPhysicsWorld world;
    protected int bodyId = 0;
    protected int dataStoreIndex = -1;

    protected VxBody(VxObjectType<? extends VxBody> type, VxPhysicsWorld world, UUID id) {
        this.type = type;
        this.world = world;
        this.physicsId = id;
    }

    public void onBodyAdded(VxPhysicsWorld world) {}
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {}
    public void physicsTick(VxPhysicsWorld world) {}
    public void gameTick(ServerLevel level) {}

    /**
     * Writes data needed by the client for spawning and for custom data updates.
     * This is sent over the network.
     *
     * @param buf The buffer to write to.
     */
    public void writeSyncData(VxByteBuf buf) {}

    /**
     * Reads the data written by {@link #writeSyncData(VxByteBuf)} on the client side.
     *
     * @param buf The buffer to read from.
     */
    public void readSyncData(VxByteBuf buf) {}

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
        return null;
    }

    @Nullable
    public ConstBody getBody() {
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
        VxMainClass.LOGGER.warn("Returned null ConstBody for bodyId {}", bodyId);
        return null;
    }

    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    public UUID getPhysicsId() {
        return this.physicsId;
    }

    public int getBodyId() {
        return this.bodyId;
    }

    public VxObjectType<? extends VxBody> getType() {
        return type;
    }

    public VxPhysicsWorld getWorld() {
        return this.world;
    }

    /**
     * Marks this object's custom data as dirty, queuing it for synchronization to clients in the next tick.
     * The data written in {@link #writeSyncData(VxByteBuf)} will be sent.
     */
    public void markCustomDataDirty() {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().markCustomDataDirty(this);
        }
    }

    public boolean isCustomDataDirty() {
        if (this.dataStoreIndex != -1) {
            return this.world.getObjectManager().getDataStore().isCustomDataDirty[this.dataStoreIndex];
        }
        return false;
    }

    public void setCustomDataDirty(boolean dirty) {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().getDataStore().isCustomDataDirty[this.dataStoreIndex] = dirty;
        }
    }

    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    public void setDataStoreIndex(int dataStoreIndex) {
        this.dataStoreIndex = dataStoreIndex;
    }
}