/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.data.component;

import org.joml.Vector3f;

/**
 * An immutable definition of a specific type of seat.
 * <p>
 * This class defines the visual model and the physical interaction bounds (hit-box)
 * of a seat. It is decoupled from the vehicle chassis, allowing standard seats
 * (e.g., "Racing Seat", "Leather Bench") to be reused across different vehicles.
 *
 * @param id              The unique ID of the seat type.
 * @param size            The dimensions of the interaction bounding box (Width, Height, Depth).
 * @param modelPath       The resource path to the 3D model file.
 * @param visualGroupName The name of the group within the 3D model file to render.
 *
 * @author xI-Mx-Ix
 */
public record VxSeatDefinition(
    String id,
    Vector3f size,
    String modelPath,
    String visualGroupName
) {

    /**
     * Creates a fallback definition for error handling.
     *
     * @return A default seat definition.
     */
    public static VxSeatDefinition missing() {
        return new VxSeatDefinition(
            "missing",
            new Vector3f(0.5f, 0.5f, 0.5f),
            "missing_model",
            "default"
        );
    }
}