package net.xmx.velthoric.physics.object.physicsobject;

import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import java.util.UUID;

public final class PhysicsObjectType<T extends VxAbstractBody> {

    private final String typeId;
    private final Factory<T> factory;

    private PhysicsObjectType(String typeId, Factory<T> factory) {
        this.typeId = typeId;
        this.factory = factory;
    }

    public T create(VxPhysicsWorld world, UUID id) {
        return this.factory.create(this, world, id);
    }

    public String getTypeId() {
        return typeId;
    }

    @FunctionalInterface
    public interface Factory<T extends VxAbstractBody> {
        T create(PhysicsObjectType<T> type, VxPhysicsWorld world, UUID id);
    }

    public static class Builder<T extends VxAbstractBody> {
        private final Factory<T> factory;

        private Builder(Factory<T> factory) {
            this.factory = factory;
        }

        public static <T extends VxAbstractBody> Builder<T> create(Factory<T> factory) {
            return new Builder<>(factory);
        }

        public PhysicsObjectType<T> build(String typeId) {
            return new PhysicsObjectType<>(typeId, factory);
        }
    }
}