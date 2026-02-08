/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.part.definition;

import org.joml.Vector3f;

/**
 * An immutable definition of a specific type of seat.
 * <p>
 * This class defines the physical interaction bounds (hit-box) of a seat.
 * It is decoupled from the vehicle chassis, allowing standard seats
 * to be reused across different vehicles.
 * <p>
 * Visual rendering is handled by the {@code VxSeatRenderer} using the ID.
 *
 * @param id   The unique ID of the seat type.
 * @param size The dimensions of the interaction bounding box (Width, Height, Depth).
 * @author xI-Mx-Ix
 */
public record VxSeatDefinition(
    String id,
    Vector3f size
) {

    /**
     * Creates a fallback definition for error handling.
     *
     * @return A default seat definition.
     */
    public static VxSeatDefinition missing() {
        return new VxSeatDefinition(
            "missing",
            new Vector3f(0.5f, 0.5f, 0.5f)
        );
    }
}