package net.xmx.vortex.physics.object.physicsobject;

import net.minecraft.world.level.Level;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;

import java.util.function.BiFunction;

public final class PhysicsObjectType<T extends IPhysicsObject> {

    private final String typeId;
    private final EObjectType objectTypeEnum;
    private final Factory<T> factory;
    private final IPhysicsObjectProperties defaultProperties;

    private PhysicsObjectType(String typeId, EObjectType objectTypeEnum, Factory<T> factory, IPhysicsObjectProperties properties) {
        this.typeId = typeId;
        this.objectTypeEnum = objectTypeEnum;
        this.factory = factory;
        this.defaultProperties = properties;
    }

    public T create(Level level) {
        return this.factory.create(this, level);
    }

    public String getTypeId() {
        return typeId;
    }

    public EObjectType getObjectTypeEnum() {
        return objectTypeEnum;
    }

    public IPhysicsObjectProperties getDefaultProperties() {
        return defaultProperties;
    }

    @FunctionalInterface
    public interface Factory<T extends IPhysicsObject> {
        T create(PhysicsObjectType<T> type, Level level);
    }

    public static class Builder<T extends IPhysicsObject> {
        private final Factory<T> factory;
        private final EObjectType objectTypeEnum;
        private IPhysicsObjectProperties properties;

        private Builder(Factory<T> factory, EObjectType objectTypeEnum) {
            this.factory = factory;
            this.objectTypeEnum = objectTypeEnum;
        }

        public static <T extends IPhysicsObject> Builder<T> create(Factory<T> factory, EObjectType objectTypeEnum) {
            return new Builder<>(factory, objectTypeEnum);
        }

        public Builder<T> properties(IPhysicsObjectProperties properties) {
            this.properties = properties;
            return this;
        }

        public PhysicsObjectType<T> build(String typeId) {
            if (properties == null) {
                throw new IllegalStateException("Properties must be set for PhysicsObjectType '" + typeId + "'");
            }
            return new PhysicsObjectType<>(typeId, objectTypeEnum, factory, properties);
        }
    }
}