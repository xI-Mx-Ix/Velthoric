package net.xmx.vortex.physics.constraint;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IConstraint {
    UUID getId();
    @Nullable UUID getBody1Id();
    @Nullable UUID getBody2Id();
    String getConstraintType();

    /**
     * Retrieves the dependency constraints' UUIDs.
     * @param index 0 or 1 for the first or second dependency.
     * @return The UUID of the dependency, or null if not applicable or not present.
     */
    @Nullable UUID getDependency(int index);

    @Nullable TwoBodyConstraint getJoltConstraint();
    void save(FriendlyByteBuf buf);
    void release();
}