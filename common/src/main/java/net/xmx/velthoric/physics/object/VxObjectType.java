/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import java.util.UUID;

/**
 * Represents the type of a physics object.
 * This class is an immutable container for the type's unique identifier, a factory
 * for creating instances of the object, and metadata such as whether it can be
 * summoned by a command.
 *
 * @param <T> The class of the {@link VxAbstractBody} this type represents.
 * @author xI-Mx-Ix
 */
public final class VxObjectType<T extends VxAbstractBody> {

    // The unique identifier for this object type (e.g., "my_mod:my_car").
    private final ResourceLocation typeId;
    // The factory function used to create new instances of this object type.
    private final Factory<T> factory;
    // Whether this object type can be summoned via commands.
    private final boolean summonable;

    // Private constructor, use the Builder to create instances.
    private VxObjectType(ResourceLocation typeId, Factory<T> factory, boolean summonable) {
        this.typeId = typeId;
        this.factory = factory;
        this.summonable = summonable;
    }

    /**
     * Creates a new instance of the physics object.
     *
     * @param world The physics world the object will belong to.
     * @param id    The unique UUID for the new instance.
     * @return A new object of type T.
     */
    public T create(VxPhysicsWorld world, UUID id) {
        return this.factory.create(this, world, id);
    }

    /**
     * @return The unique {@link ResourceLocation} of this type.
     */
    public ResourceLocation getTypeId() {
        return typeId;
    }

    /**
     * @return True if this object type can be summoned by commands, false otherwise.
     */
    public boolean isSummonable() {
        return summonable;
    }

    /**
     * A functional interface for a factory that creates physics objects.
     *
     * @param <T> The type of object to create.
     */
    @FunctionalInterface
    public interface Factory<T extends VxAbstractBody> {
        /**
         * Creates a new instance.
         *
         * @param type  The {@link VxObjectType} definition.
         * @param world The physics world.
         * @param id    The instance's UUID.
         * @return A new instance of type T.
         */
        T create(VxObjectType<T> type, VxPhysicsWorld world, UUID id);
    }

    /**
     * A builder for creating {@link VxObjectType} instances with a fluent API.
     *
     * @param <T> The class of the {@link VxAbstractBody}.
     */
    public static class Builder<T extends VxAbstractBody> {
        private final Factory<T> factory;
        private boolean summonable = true;

        private Builder(Factory<T> factory) {
            this.factory = factory;
        }

        /**
         * Creates a new builder.
         *
         * @param factory The factory function for this type.
         * @param <T>     The class of the {@link VxAbstractBody}.
         * @return A new Builder instance.
         */
        public static <T extends VxAbstractBody> Builder<T> create(Factory<T> factory) {
            return new Builder<>(factory);
        }

        /**
         * Marks this object type as not summonable by commands.
         *
         * @return This builder, for chaining.
         */
        public Builder<T> noSummon() {
            this.summonable = false;
            return this;
        }

        /**
         * Builds the final, immutable {@link VxObjectType}.
         *
         * @param typeId The unique {@link ResourceLocation} for this type.
         * @return A new {@link VxObjectType} instance.
         */
        public VxObjectType<T> build(ResourceLocation typeId) {
            return new VxObjectType<>(typeId, factory, summonable);
        }
    }
}