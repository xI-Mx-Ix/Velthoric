package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.ConstraintSettings;
import io.netty.buffer.ByteBuf;

public abstract class VxConstraintSerializer {

    protected void saveBase(ConstraintSettings settings, ByteBuf buf) {
        buf.writeBoolean(settings.getEnabled());
        buf.writeInt(settings.getNumPositionStepsOverride());
        buf.writeInt(settings.getNumVelocityStepsOverride());
        buf.writeInt(settings.getConstraintPriority());
        buf.writeFloat(settings.getDrawConstraintSize());
    }

    protected void loadBase(ConstraintSettings settings, ByteBuf buf) {
        settings.setEnabled(buf.readBoolean());
        settings.setNumPositionStepsOverride(buf.readInt());
        settings.setNumVelocityStepsOverride(buf.readInt());
        settings.setConstraintPriority(buf.readInt());
        settings.setDrawConstraintSize(buf.readFloat());
    }
}