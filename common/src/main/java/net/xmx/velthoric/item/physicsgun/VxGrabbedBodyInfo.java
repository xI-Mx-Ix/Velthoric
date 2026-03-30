/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun;

import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;

import java.util.UUID;

/**
 * Information about a grabbed physics body.
 *
 * @author xI-Mx-Ix
 */
public record VxGrabbedBodyInfo(
        UUID physicsId,
        int bodyId,
        Vec3Arg grabPointLocal,
        float currentDistance,
        float originalAngularDamping,
        QuatArg initialBodyRotation,
        QuatArg initialPlayerRotation,
        boolean inRotationMode
) {}
