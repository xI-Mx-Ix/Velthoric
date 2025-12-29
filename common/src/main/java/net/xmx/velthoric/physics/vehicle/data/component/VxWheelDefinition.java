/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.data.component;

/**
 * An immutable definition of a specific type of wheel.
 * <p>
 * This class separates the "Item" (the physical dimensions and visual model of the wheel)
 * from the "Slot" (where the wheel is attached to the car). This allows the same
 * wheel definition to be reused across multiple vehicles or swapped at runtime.
 *
 * @param id              The unique ID of the wheel type.
 * @param radius          The physical radius of the wheel in meters.
 * @param width           The physical width of the wheel in meters.
 * @param friction        The friction coefficient of the tire.
 * @param modelPath       The resource path to the 3D model file (e.g., .obj or .gltf).
 * @param visualGroupName The name of the group within the 3D model file to render.
 *
 * @author xI-Mx-Ix
 */
public record VxWheelDefinition(
    String id,
    float radius,
    float width,
    float friction,
    String modelPath,
    String visualGroupName
) {

    /**
     * Creates a fallback definition for error handling.
     * Use this when a requested wheel ID cannot be found in the registry.
     *
     * @return A default "missing" wheel definition.
     */
    public static VxWheelDefinition missing() {
        return new VxWheelDefinition("missing", 0.3f, 0.2f, 1.0f, "missing_model", "default");
    }
}