package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import io.netty.buffer.ByteBuf;

public interface IVxConstraintSerializer<T extends TwoBodyConstraintSettings> {
    void save(T settings, ByteBuf buf);
    T load(ByteBuf buf);
}