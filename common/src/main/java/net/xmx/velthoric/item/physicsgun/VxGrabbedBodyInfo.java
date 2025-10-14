/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;

import java.util.UUID;

public record VxGrabbedBodyInfo(
        UUID physicsId,
        int bodyId,
        Vec3 grabPointLocal,
        float currentDistance,
        float originalAngularDamping,
        Quat initialBodyRotation,
        Quat initialPlayerRotation,
        boolean inRotationMode
) {}
