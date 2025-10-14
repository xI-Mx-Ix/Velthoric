/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * Serialized snapshot of a physics body used for persistence or syncing.
 *
 * @param typeId          body type identifier
 * @param id              unique instance ID
 * @param transform       position and rotation
 * @param linearVelocity  current linear velocity
 * @param angularVelocity current angular velocity
 * @param motionType      The motion type of the body.
 * @param persistenceData extra serialized data
 *
 * @author xI-Mx-Ix
 */
public record VxSerializedBodyData(
        ResourceLocation typeId,
        UUID id,
        VxTransform transform,
        Vec3 linearVelocity,
        Vec3 angularVelocity,
        EMotionType motionType,
        VxByteBuf persistenceData
) {}