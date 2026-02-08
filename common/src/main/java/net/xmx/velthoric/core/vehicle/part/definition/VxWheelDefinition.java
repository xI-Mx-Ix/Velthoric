/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.part.definition;

/**
 * An immutable definition of a specific type of wheel.
 * <p>
 * This class defines the physical dimensions and friction properties of a wheel.
 * Visual rendering is handled by the {@code VxWheelRenderer} using the ID.
 *
 * @param id       The unique ID of the wheel type.
 * @param radius   The physical radius of the wheel in meters.
 * @param width    The physical width of the wheel in meters.
 * @param friction The friction coefficient of the tire (affects traction).
 * @author xI-Mx-Ix
 */
public record VxWheelDefinition(
    String id,
    float radius,
    float width,
    float friction
) {

    /**
     * Creates a fallback definition for error handling.
     *
     * @return A default "missing" wheel definition.
     */
    public static VxWheelDefinition missing() {
        return new VxWheelDefinition("missing", 0.3f, 0.2f, 1.0f);
    }
}