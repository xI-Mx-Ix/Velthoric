/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object;

import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The abstract base class for all physics objects in Velthoric.
 * It defines the core properties and behaviors common to all physics bodies,
 * such as their ID, type, and lifecycle callbacks.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractBody {

    // The unique, persistent identifier for this physics object instance.
    protected final UUID physicsId;
    // The type definition for this object, containing its factory and type ID.
    protected final VxObjectType<? extends VxAbstractBody> type;
    // The physics world this object belongs to.
    protected final VxPhysicsWorld world;
    // The ID of the body in the Jolt physics system. 0 if not added to the simulation.
    protected int bodyId = 0;
    // The object's transform in the game world, used as the source of truth for spawning and saving.
    protected final VxTransform gameTransform = new VxTransform();
    // A flag indicating that this object's custom data needs to be synchronized to clients.
    protected boolean isDataDirty = false;
    // Caches the long-form key of the chunk this object is in, to efficiently detect chunk movements.
    private long lastKnownChunkKey = Long.MAX_VALUE;
    // The index of this object within the server-side data store for efficient state updates.
    protected int dataStoreIndex = -1;

    /**
     * Constructs a new abstract body.
     *
     * @param type  The object type definition.
     * @param world The physics world this body will belong to.
     * @param id    The unique UUID for this instance.
     */
    protected VxAbstractBody(VxObjectType<? extends VxAbstractBody> type, VxPhysicsWorld world, UUID id) {
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
     * and when {@link #markDataDirty()} is called.
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
     * Safely gets the current transform of the body from the physics simulation.
     *
     * @param world The physics world.
     * @return A new {@link VxTransform} with the body's current state, or null if the body is not in the simulation.
     */
    @Nullable
    public VxTransform getTransform(VxPhysicsWorld world) {
        if (bodyId == 0) return null;

        try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), this.bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                VxTransform transform = new VxTransform();
                lock.getBody().getPositionAndRotation(transform.getTranslation(), transform.getRotation());
                return transform;
            }
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
        VxAbstractBody found = world.getObjectManager().getByBodyId(bodyId);
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
     * Sets the Jolt body ID for this object. This is typically called by the {@code VxObjectManager}.
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
    public VxObjectType<? extends VxAbstractBody> getType() {
        return type;
    }

    /**
     * @return The game-side transform of this object.
     */
    public VxTransform getGameTransform() {
        return this.gameTransform;
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
    public void markDataDirty() {
        this.isDataDirty = true;
    }

    /**
     * @return True if the object's custom data is dirty.
     */
    public boolean isDataDirty() {
        return this.isDataDirty;
    }

    /**
     * Clears the dirty flag for custom data.
     */
    public void clearDataDirty() {
        this.isDataDirty = false;
    }

    /**
     * @return The cached chunk key for this object.
     */
    public long getLastKnownChunkKey() {
        return this.lastKnownChunkKey;
    }

    /**
     * Updates the cached chunk key.
     *
     * @param key The new chunk key.
     */
    public void setLastKnownChunkKey(long key) {
        this.lastKnownChunkKey = key;
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

    // A marker interface for client-side renderer classes.
    public interface Renderer {
    }
}