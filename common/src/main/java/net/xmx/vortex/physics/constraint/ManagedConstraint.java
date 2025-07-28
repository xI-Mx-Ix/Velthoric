package net.xmx.vortex.physics.constraint;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ManagedConstraint implements IConstraint {
    private final UUID id;
    private final UUID body1Id;
    private final UUID body2Id;
    private final UUID dependencyId1;
    private final UUID dependencyId2;
    private final TwoBodyConstraintRef constraintRef;
    private final String constraintType;
    private final byte[] fullSerializedData;

    public ManagedConstraint(UUID id, @Nullable UUID b1, @Nullable UUID b2, @Nullable UUID d1, @Nullable UUID d2, TwoBodyConstraintRef ref, String type, byte[] fullSerializedData) {
        this.id = id;
        this.body1Id = b1;
        this.body2Id = b2;
        this.dependencyId1 = d1;
        this.dependencyId2 = d2;
        this.constraintRef = ref;
        this.constraintType = type;
        this.fullSerializedData = fullSerializedData;
    }

    @Override public UUID getId() { return id; }
    @Override public @Nullable UUID getBody1Id() { return body1Id; }
    @Override public @Nullable UUID getBody2Id() { return body2Id; }
    @Override public @Nullable UUID getDependency(int index) {
        if (index == 0) return dependencyId1;
        if (index == 1) return dependencyId2;
        return null;
    }
    @Override public String getConstraintType() { return constraintType; }

    @Override public @Nullable TwoBodyConstraint getJoltConstraint() {
        return (constraintRef != null && constraintRef.hasAssignedNativeObject()) ? constraintRef.getPtr() : null;
    }

    @Override
    public void save(FriendlyByteBuf buf) {
        buf.writeBytes(this.fullSerializedData);
    }

    @Override public void release() {
        if (constraintRef != null && constraintRef.hasAssignedNativeObject()) {
            constraintRef.close();
        }
    }
}