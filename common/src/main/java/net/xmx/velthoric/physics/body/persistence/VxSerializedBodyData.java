/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * Serialized snapshot of a physics body used for persistence or syncing.
 * This record holds the identification information and a data buffer containing
 * the internal state of the body (transform, velocity, vertices, etc.).
 *
 * @param typeId    body type identifier
 * @param id        unique instance ID
 * @param bodyData  the buffer containing the body's internal persistence data
 *
 * @author xI-Mx-Ix
 */
public record VxSerializedBodyData(
        ResourceLocation typeId,
        UUID id,
        VxByteBuf bodyData
) {}