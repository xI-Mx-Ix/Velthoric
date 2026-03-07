/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.registry;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.behavior.VxPersistenceHandler;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.provider.VxJoltRigidProvider;
import net.xmx.velthoric.core.body.type.provider.VxJoltSoftProvider;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents an immutable definition of a physics body type.
 * <p>
 * This class describes how a body should be constructed, which behaviors it possesses by default,
 * and how its physical shape is provided to the Jolt engine.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyType {

    /**
     * Unique identifier for this body type.
     */
    private final ResourceLocation typeId;

    /**
     * Factory used to instantiate new body objects.
     */
    private final Factory factory;

    /**
     * Determines if this type can be summoned via commands.
     */
    private final boolean summonable;

    /**
     * Determines if this type is saved to disk by default.
     */
    private final boolean persistent;

    /**
     * Bitmask of behavior IDs attached to instances of this type at creation.
     */
    private final long defaultBehaviors;

    /**
     * Provider for rigid body shapes. Null if this is a soft body.
     */
    @Nullable
    private final VxJoltRigidProvider rigidProvider;

    /**
     * Provider for soft body meshes. Null if this is a rigid body.
     */
    @Nullable
    private final VxJoltSoftProvider softProvider;

    /**
     * Handler for custom persistence data serialization.
     */
    private final VxPersistenceHandler persistenceHandler;

    /**
     * Optional callback to define synchronized data entries.
     */
    @Nullable
    private final Consumer<VxSynchronizedData.Builder> syncDataDefiner;

    private VxBodyType(ResourceLocation typeId, Factory factory, boolean summonable, boolean persistent,
                       long defaultBehaviors, @Nullable VxJoltRigidProvider rigidProvider,
                       @Nullable VxJoltSoftProvider softProvider, VxPersistenceHandler persistenceHandler,
                       @Nullable Consumer<VxSynchronizedData.Builder> syncDataDefiner) {
        this.typeId = typeId;
        this.factory = factory;
        this.summonable = summonable;
        this.persistent = persistent;
        this.defaultBehaviors = defaultBehaviors;
        this.rigidProvider = rigidProvider;
        this.softProvider = softProvider;
        this.persistenceHandler = persistenceHandler;
        this.syncDataDefiner = syncDataDefiner;
    }

    /**
     * Creates a new instance of a body using this type definition.
     *
     * @param world The physics world.
     * @param id    The unique identifier.
     * @return A new body instance.
     */
    public VxBody create(VxPhysicsWorld world, UUID id) {
        return this.factory.create(this, world, id);
    }

    /**
     * Invokes the definition logic for synchronized data fields.
     *
     * @param builder The sync data builder.
     */
    public void defineSyncData(VxSynchronizedData.Builder builder) {
        if (syncDataDefiner != null) {
            syncDataDefiner.accept(builder);
        }
    }

    public ResourceLocation getTypeId() {
        return typeId;
    }

    public boolean isSummonable() {
        return summonable;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public long getDefaultBehaviors() {
        return defaultBehaviors;
    }

    @Nullable
    public VxJoltRigidProvider getRigidProvider() {
        return rigidProvider;
    }

    @Nullable
    public VxJoltSoftProvider getSoftProvider() {
        return softProvider;
    }

    public VxPersistenceHandler getPersistenceHandler() {
        return persistenceHandler;
    }

    public boolean isRigid() {
        return rigidProvider != null;
    }

    public boolean isSoft() {
        return softProvider != null;
    }

    /**
     * Functional interface for instantiating body handles.
     */
    @FunctionalInterface
    public interface Factory {
        VxBody create(VxBodyType type, VxPhysicsWorld world, UUID id);
    }

    /**
     * Fluent builder for creating type definitions.
     */
    public static class Builder {
        private final Factory factory;
        private boolean summonable = true;
        private boolean persistent = true;
        private long defaultBehaviors = 0;
        private VxJoltRigidProvider rigidProvider;
        private VxJoltSoftProvider softProvider;
        private VxPersistenceHandler persistenceHandler = VxPersistenceHandler.EMPTY;
        private Consumer<VxSynchronizedData.Builder> syncDataDefiner;

        private Builder(Factory factory) {
            this.factory = factory;
        }

        public static Builder create(Factory factory) {
            return new Builder(factory);
        }

        public Builder noSummon() {
            this.summonable = false;
            return this;
        }

        public Builder setPersistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        /**
         * Registers a rigid body shape provider and attaches physics synchronization behaviors.
         */
        public Builder rigidProvider(VxJoltRigidProvider provider) {
            this.rigidProvider = provider;
            this.defaultBehaviors |= VxBehaviors.RIGID_PHYSICS.getMask();
            this.defaultBehaviors |= VxBehaviors.PHYSICS_SYNC.getMask();
            return this;
        }

        /**
         * Registers a soft body mesh provider and attaches physics synchronization behaviors.
         */
        public Builder softProvider(VxJoltSoftProvider provider) {
            this.softProvider = provider;
            this.defaultBehaviors |= VxBehaviors.SOFT_PHYSICS.getMask();
            this.defaultBehaviors |= VxBehaviors.PHYSICS_SYNC.getMask();
            return this;
        }

        public Builder buoyant() {
            this.defaultBehaviors |= VxBehaviors.BUOYANCY.getMask();
            return this;
        }

        public Builder persistence(VxPersistenceHandler handler) {
            this.persistenceHandler = handler;
            return this;
        }

        public Builder persistence(VxPersistenceHandler.Writer writer, VxPersistenceHandler.Reader reader) {
            this.persistenceHandler = VxPersistenceHandler.of(writer, reader);
            return this;
        }

        public Builder syncData(Consumer<VxSynchronizedData.Builder> definer) {
            this.syncDataDefiner = definer;
            return this;
        }

        public Builder behavior(VxBehaviorId behaviorId) {
            this.defaultBehaviors |= behaviorId.getMask();
            return this;
        }

        /**
         * Builds the final immutable type.
         */
        public VxBodyType build(ResourceLocation typeId) {
            if (persistent) {
                defaultBehaviors |= VxBehaviors.PERSISTENCE.getMask();
            }
            return new VxBodyType(typeId, factory, summonable, persistent,
                    defaultBehaviors, rigidProvider, softProvider, persistenceHandler, syncDataDefiner);
        }
    }
}