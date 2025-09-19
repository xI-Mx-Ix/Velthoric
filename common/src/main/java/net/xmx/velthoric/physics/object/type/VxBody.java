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
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The abstract base class for all physics objects in Velthoric.
 * This class acts as a lightweight handle or wrapper for a physics body.
 * Most of its state is stored in a data-oriented {@link net.xmx.velthoric.physics.object.manager.VxObjectDataStore}
 * for efficient processing.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBody {

    /** The unique, persistent identifier for this physics object instance. */
    protected final UUID physicsId;
    /** The type definition for this object, containing its factory and type ID. */
    protected final VxObjectType<? extends VxBody> type;
    /** The physics world this object belongs to. */
    protected final VxPhysicsWorld world;
    /** The ID of the body in the Jolt physics system. 0 if not added to the simulation. */
    protected int bodyId = 0;
    /** The index of this object within the server-side data store for efficient state access. */
    protected int dataStoreIndex = -1;

    /**
     * Constructs a new abstract body.
     *
     * @param type  The object type definition.
     * @param world The physics world this body will belong to.
     * @param id    The unique UUID for this instance.
     */
    protected VxBody(VxObjectType<? extends VxBody> type, VxPhysicsWorld world, UUID id) {
        this.type = type;
        this.world = world;
        this.physicsId = id;
    }

    /**
     * Called when the body has been successfully added to the physics simulation.
     *
     * @param world The physics world it was added to.
     */
    public void onBodyAdded(VxPhysicsWorld world) {}

    /**
     * Called just before the body is removed from the physics simulation.
     *
     * @param world  The physics world it is being removed from.
     * @param reason The reason for the removal.
     */
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {}

    /**
     * Called once per physics simulation step.
     *
     * @param world The physics world.
     */
    public void physicsTick(VxPhysicsWorld world) {}

    /**
     * Called once per Minecraft game tick.
     *
     * @param level The server level.
     */
    public void gameTick(ServerLevel level) {}

    /**
     * Subclasses can override this to write custom data that is sent to the client on spawn
     * and when {@link #markCustomDataDirty()} is called.
     *
     * @param buf The buffer to write to.
     */
    public void writeCreationData(VxByteBuf buf) {}

    /**
     * Subclasses can override this to read the custom data written by {@link #writeCreationData(VxByteBuf)}.
     *
     * @param buf The buffer to read from.
     */
    public void readCreationData(VxByteBuf buf) {}

    /**
     * Retrieves the current transform of the body from the central data store.
     *
     * @param outTransform The {@link VxTransform} object to populate with the result.
     */
    public void getTransform(VxTransform outTransform) {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().getTransform(this.dataStoreIndex, outTransform);
        }
    }

    /**
     * Retrieves the current transform of the body from the central data store.
     * <p>
     * This method creates a new {@link VxTransform} instance, fills it with the
     * transform data from the {@code ObjectManager}, and returns it.
     *
     * @return a new {@link VxTransform} containing the current transform of this body,
     *         or {@code null} if the body does not have a valid data store index.
     */
    public VxTransform getTransform() {
        if (this.dataStoreIndex != -1) {
            VxTransform transform = new VxTransform();
            this.world.getObjectManager().getTransform(this.dataStoreIndex, transform);
            return transform;
        }
        return null;
    }

    /**
     * Safely gets a read-only reference to the underlying Jolt physics body.
     *
     * @return A {@link ConstBody} reference, or null if the body is not accessible.
     */
    @Nullable
    public ConstBody getBody() {
        if (bodyId == 0) {
            return null;
        }
        // Sanity check to ensure this object instance still corresponds to the bodyId.
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

    /**
     * Sets the Jolt body ID for this object. This is typically called by the {@link VxObjectManager}.
     *
     * @param bodyId The new body ID.
     */
    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    /**
     * @return The unique, persistent UUID of this object.
     */
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    /**
     * @return The Jolt body ID of this object.
     */
    public int getBodyId() {
        return this.bodyId;
    }

    /**
     * @return The {@link VxObjectType} definition for this object.
     */
    public VxObjectType<? extends VxBody> getType() {
        return type;
    }

    /**
     * @return The physics world this object belongs to.
     */
    public VxPhysicsWorld getWorld() {
        return this.world;
    }

    /**
     * Marks this object's custom data as dirty, queuing it for synchronization to clients.
     */
    public void markCustomDataDirty() {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().getDataStore().isCustomDataDirty[this.dataStoreIndex] = true;
        }
    }

    /**
     * Checks if the object's custom data is dirty.
     * @return True if the object's custom data is dirty.
     */
    public boolean isCustomDataDirty() {
        if (this.dataStoreIndex != -1) {
            return this.world.getObjectManager().getDataStore().isCustomDataDirty[this.dataStoreIndex];
        }
        return false;
    }

    /**
     * Clears the dirty flag for custom data.
     */
    public void clearCustomDataDirty() {
        if (this.dataStoreIndex != -1) {
            this.world.getObjectManager().getDataStore().isCustomDataDirty[this.dataStoreIndex] = false;
        }
    }

    /**
     * @return The index of this object in the server-side data store.
     */
    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    /**
     * Sets the data store index for this object.
     *
     * @param dataStoreIndex The new index.
     */
    public void setDataStoreIndex(int dataStoreIndex) {
        this.dataStoreIndex = dataStoreIndex;
    }

    /**
     * A marker interface for client-side renderer classes.
     */
    public interface Renderer {}
}