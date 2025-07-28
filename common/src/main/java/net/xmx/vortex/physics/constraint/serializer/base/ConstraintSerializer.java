package net.xmx.vortex.physics.constraint.serializer.base;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;
import java.util.UUID;

public interface ConstraintSerializer<B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint, S extends TwoBodyConstraintSettings> {
    UUID WORLD_BODY_ID = new UUID(0L, 0L);
    String getTypeId();
    void serializeSettings(B builder, FriendlyByteBuf buf);
    S createSettings(FriendlyByteBuf buf);

    default void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
    }

    default void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
    }

    default void serializeBodies(B builder, FriendlyByteBuf buf) {
        UUID body1Id = builder.getBody1Id();
        UUID body2Id = builder.getBody2Id();
        buf.writeUUID(body1Id != null ? body1Id : WORLD_BODY_ID);
        buf.writeUUID(body2Id != null ? body2Id : WORLD_BODY_ID);
    }

    default UUID[] deserializeBodies(FriendlyByteBuf buf) {
        UUID id1 = buf.readUUID();
        UUID id2 = buf.readUUID();
        return new UUID[]{
                WORLD_BODY_ID.equals(id1) ? null : id1,
                WORLD_BODY_ID.equals(id2) ? null : id2
        };
    }
}