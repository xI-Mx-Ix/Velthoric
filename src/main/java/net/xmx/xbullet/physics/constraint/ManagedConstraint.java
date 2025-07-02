package net.xmx.xbullet.physics.constraint;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;

import java.util.UUID;

public class ManagedConstraint implements IConstraint {

    private final UUID jointId;
    private final UUID body1Id;
    private final UUID body2Id;
    private final TwoBodyConstraintRef constraintRef;
    private final TwoBodyConstraint joltConstraint;
    private final String constraintType;

    public ManagedConstraint(UUID jointId, UUID body1Id, UUID body2Id, TwoBodyConstraint joltConstraint, String constraintType) {
        this.jointId = jointId;
        this.body1Id = body1Id;
        this.body2Id = body2Id;
        this.joltConstraint = joltConstraint;
        this.constraintRef = joltConstraint.toRef();
        this.constraintType = constraintType;
    }

    @Override public UUID getJointId() { return jointId; }
    @Override public UUID getBody1Id() { return body1Id; }
    @Override public UUID getBody2Id() { return body2Id; }
    @Override public TwoBodyConstraint getJoltConstraint() { return joltConstraint; }

    @Override public TwoBodyConstraintRef getConstraintRef() { return this.constraintRef; }

    @SuppressWarnings("unchecked")
    @Override
    public void save(CompoundTag tag) {
        ConstraintSerializerRegistry.getSerializer(constraintType).ifPresent(serializer -> {
            ((IConstraintSerializer<TwoBodyConstraint>) serializer).save(joltConstraint, tag);
            serializer.saveBodyIds(body1Id, body2Id, tag);
        });
        tag.putString("constraintType", this.constraintType);
        tag.putUUID("jointDataId", this.jointId);
    }

    @Override
    public void release() {
        if (constraintRef != null && constraintRef.hasAssignedNativeObject()) {
            constraintRef.close();
        }
    }
}