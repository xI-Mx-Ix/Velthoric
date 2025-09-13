/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.serializer;

import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import io.netty.buffer.ByteBuf;

public interface IVxConstraintSerializer<T extends TwoBodyConstraintSettings> {
    void save(T settings, ByteBuf buf);
    T load(ByteBuf buf);
}
