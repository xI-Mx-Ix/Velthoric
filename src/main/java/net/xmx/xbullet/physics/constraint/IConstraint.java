package net.xmx.xbullet.physics.constraint;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public interface IConstraint {
    UUID getJointId();
    UUID getBody1Id();
    UUID getBody2Id();

    TwoBodyConstraint getJoltConstraint();

    TwoBodyConstraintRef getConstraintRef();

    void save(CompoundTag tag);

    void release();
}