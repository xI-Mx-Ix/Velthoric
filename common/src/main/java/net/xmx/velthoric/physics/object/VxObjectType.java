package net.xmx.velthoric.physics.object;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import java.util.UUID;

public final class VxObjectType<T extends VxAbstractBody> {

    private final ResourceLocation typeId;
    private final Factory<T> factory;
    private final boolean summonable;

    private VxObjectType(ResourceLocation typeId, Factory<T> factory, boolean summonable) {
        this.typeId = typeId;
        this.factory = factory;
        this.summonable = summonable;
    }

    public T create(VxPhysicsWorld world, UUID id) {
        return this.factory.create(this, world, id);
    }

    public ResourceLocation getTypeId() {
        return typeId;
    }

    public boolean isSummonable() {
        return summonable;
    }

    @FunctionalInterface
    public interface Factory<T extends VxAbstractBody> {
        T create(VxObjectType<T> type, VxPhysicsWorld world, UUID id);
    }

    public static class Builder<T extends VxAbstractBody> {
        private final Factory<T> factory;
        private boolean summonable = true;

        private Builder(Factory<T> factory) {
            this.factory = factory;
        }

        public static <T extends VxAbstractBody> Builder<T> create(Factory<T> factory) {
            return new Builder<>(factory);
        }

        public Builder<T> noSummon() {
            this.summonable = false;
            return this;
        }

        public VxObjectType<T> build(ResourceLocation typeId) {
            return new VxObjectType<>(typeId, factory, summonable);
        }
    }
}