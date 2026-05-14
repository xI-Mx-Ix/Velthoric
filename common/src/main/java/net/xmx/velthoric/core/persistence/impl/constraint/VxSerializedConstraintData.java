/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.impl.constraint;

import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * A record containing the minimally required data to instantiate a physics constraint
 * before its full state is applied on the main physics thread.
 * <p>
 * This class facilitates the "Deferred Loading" architecture, allowing async I/O threads
 * to parse and slice constraint properties without touching the main simulation logic.
 *
 * @param constraintId   The unique identifier of the constraint.
 * @param constraintData A sliced, zero-allocation Netty buffer containing the TLV schema payload.
 * @author xI-Mx-Ix
 */
public record VxSerializedConstraintData(UUID constraintId, VxByteBuf constraintData) {}