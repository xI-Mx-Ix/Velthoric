package net.xmx.vortex.physics.constraint;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.xmx.vortex.physics.constraint.serializer.ConstraintSerializerRegistry;
import net.xmx.vortex.physics.constraint.serializer.IVxConstraintSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VxConstraint {

    private final UUID constraintId;
    private final UUID body1Id;
    private final UUID body2Id;
    private byte[] settingsData;
    private final EConstraintSubType subType;

    @Nullable
    private TwoBodyConstraint joltConstraint;

    @SuppressWarnings("unchecked")
    public VxConstraint(UUID constraintId, UUID body1Id, UUID body2Id, TwoBodyConstraintSettings settings) {
        this.constraintId = constraintId;
        this.body1Id = body1Id;
        this.body2Id = body2Id;

        if (settings instanceof HingeConstraintSettings) {
            this.subType = EConstraintSubType.Hinge;
        } else if (settings instanceof SixDofConstraintSettings) {
            this.subType = EConstraintSubType.SixDof;
        } else if (settings instanceof ConeConstraintSettings) {
            this.subType = EConstraintSubType.Cone;
        } else if (settings instanceof DistanceConstraintSettings) {
            this.subType = EConstraintSubType.Distance;
        } else if (settings instanceof FixedConstraintSettings) {
            this.subType = EConstraintSubType.Fixed;
        } else if (settings instanceof GearConstraintSettings) {
            this.subType = EConstraintSubType.Gear;
        } else if (settings instanceof PathConstraintSettings) {
            this.subType = EConstraintSubType.Path;
        } else if (settings instanceof PointConstraintSettings) {
            this.subType = EConstraintSubType.Point;
        } else if (settings instanceof PulleyConstraintSettings) {
            this.subType = EConstraintSubType.Pulley;
        } else if (settings instanceof RackAndPinionConstraintSettings) {
            this.subType = EConstraintSubType.RackAndPinion;
        } else if (settings instanceof SliderConstraintSettings) {
            this.subType = EConstraintSubType.Slider;
        } else if (settings instanceof SwingTwistConstraintSettings) {
            this.subType = EConstraintSubType.SwingTwist;
        } else {
            throw new IllegalArgumentException("Unknown or unsupported constraint settings type: " + settings.getClass().getName());
        }

        ByteBuf buffer = Unpooled.buffer();
        try {
            ConstraintSerializerRegistry.get(this.subType).ifPresent(rawSerializer -> {
                IVxConstraintSerializer<TwoBodyConstraintSettings> serializer = (IVxConstraintSerializer<TwoBodyConstraintSettings>) rawSerializer;
                serializer.save(settings, buffer);
            });
            this.settingsData = new byte[buffer.readableBytes()];
            buffer.readBytes(this.settingsData);
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    public VxConstraint(UUID constraintId, UUID body1Id, UUID body2Id, byte[] settingsData, EConstraintSubType subType) {
        this.constraintId = constraintId;
        this.body1Id = body1Id;
        this.body2Id = body2Id;
        this.settingsData = settingsData;
        this.subType = subType;
    }

    public UUID getConstraintId() {
        return constraintId;
    }

    public UUID getBody1Id() {
        return body1Id;
    }

    public UUID getBody2Id() {
        return body2Id;
    }

    public byte[] getSettingsData() {
        return settingsData;
    }

    public EConstraintSubType getSubType() {
        return subType;
    }

    @Nullable
    public TwoBodyConstraint getJoltConstraint() {
        return joltConstraint;
    }

    public void setJoltConstraint(@Nullable TwoBodyConstraint joltConstraint) {
        this.joltConstraint = joltConstraint;
    }

    @SuppressWarnings("unchecked")
    public void updateSettingsData(TwoBodyConstraintSettings newSettings) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ConstraintSerializerRegistry.get(this.subType).ifPresent(rawSerializer -> {
                IVxConstraintSerializer<TwoBodyConstraintSettings> serializer = (IVxConstraintSerializer<TwoBodyConstraintSettings>) rawSerializer;
                serializer.save(newSettings, buffer);
            });
            this.settingsData = new byte[buffer.readableBytes()];
            buffer.readBytes(this.settingsData);
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }
}