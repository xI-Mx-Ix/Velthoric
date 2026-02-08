/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.registry;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Represents the type of a physics body.
 * This class is an immutable container for the type's unique identifier, a factory
 * for creating instances of the body, and metadata such as whether it can be
 * summoned by a command and its default persistence behavior.
 *
 * @param <T> The class of the {@link VxBody} this type represents.
 * @author xI-Mx-Ix
 */
public final class VxBodyType<T extends VxBody> {

    /**
     * The unique identifier for this body type (e.g., "modid:my_physics_object").
     */
    private final ResourceLocation typeId;

    /**
     * The factory function used to create new instances of this body type.
     */
    private final Factory<T> factory;

    /**
     * Whether this body type can be summoned via commands.
     */
    private final boolean summonable;

    /**
     * The default persistence state for bodies created of this type.
     * If true, bodies are saved to disk by default; if false, they are temporary.
     */
    private final boolean persistent;

    /**
     * Private constructor for the body type, called by the Builder.
     *
     * @param typeId     The unique identifier.
     * @param factory    The creation factory.
     * @param summonable Whether the type is summonable.
     * @param persistent The default persistence state.
     */
    private VxBodyType(ResourceLocation typeId, Factory<T> factory, boolean summonable, boolean persistent) {
        this.typeId = typeId;
        this.factory = factory;
        this.summonable = summonable;
        this.persistent = persistent;
    }

    /**
     * Creates a new instance of the physics body using the registered factory.
     *
     * @param world The physics world the body will belong to.
     * @param id    The unique UUID for the new instance.
     * @return A new body instance of type T.
     */
    public T create(VxPhysicsWorld world, UUID id) {
        return this.factory.create(this, world, id);
    }

    /**
     * Gets the unique resource location associated with this type.
     *
     * @return The unique {@link ResourceLocation} of this type.
     */
    public ResourceLocation getTypeId() {
        return typeId;
    }

    /**
     * Checks if this body type is allowed to be summoned via command-line interfaces.
     *
     * @return True if this body type can be summoned by commands, false otherwise.
     */
    public boolean isSummonable() {
        return summonable;
    }

    /**
     * Gets the default persistence state for this type.
     *
     * @return True if bodies of this type should be saved to disk by default.
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * A functional interface for a factory that creates physics bodies.
     *
     * @param <T> The type of body to create.
     */
    @FunctionalInterface
    public interface Factory<T extends VxBody> {
        /**
         * Creates a new instance of the body.
         *
         * @param type  The {@link VxBodyType} definition.
         * @param world The physics world (logical server side).
         * @param id    The instance's unique UUID.
         * @return A new instance of type T.
         */
        T create(VxBodyType<T> type, VxPhysicsWorld world, UUID id);
    }

    /**
     * A builder for creating {@link VxBodyType} instances with a fluent API.
     *
     * @param <T> The class of the {@link VxBody}.
     */
    public static class Builder<T extends VxBody> {
        /**
         * The factory used for instantiation.
         */
        private final Factory<T> factory;

        /**
         * The summonable flag, defaults to true.
         */
        private boolean summonable = true;

        /**
         * The default persistence flag, defaults to true.
         */
        private boolean persistent = true;

        /**
         * Private builder constructor.
         *
         * @param factory The factory function.
         */
        private Builder(Factory<T> factory) {
            this.factory = factory;
        }

        /**
         * Creates a new builder for a specific body type.
         *
         * @param factory The factory function for this type.
         * @param <T>     The class of the {@link VxBody}.
         * @return A new Builder instance.
         */
        public static <T extends VxBody> Builder<T> create(Factory<T> factory) {
            return new Builder<>(factory);
        }

        /**
         * Marks this body type as not summonable by commands.
         *
         * @return This builder, for chaining.
         */
        public Builder<T> noSummon() {
            this.summonable = false;
            return this;
        }

        /**
         * Sets the default persistence state for this body type.
         *
         * @param persistent true if bodies should be saved to disk by default, false otherwise.
         * @return This builder, for chaining.
         */
        public Builder<T> setPersistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        /**
         * Builds the final, immutable {@link VxBodyType}.
         *
         * @param typeId The unique {@link ResourceLocation} for this type.
         * @return A new {@link VxBodyType} instance.
         */
        public VxBodyType<T> build(ResourceLocation typeId) {
            return new VxBodyType<>(typeId, factory, summonable, persistent);
        }
    }
}