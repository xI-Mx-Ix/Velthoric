/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.readonly.ConstShape;

/**
 * Abstract base class for all collision shape wrappers.
 * <p>
 * This class provides a clean abstraction over Jolt's shape creation pipeline,
 * encapsulating the {@link ShapeSettings} → {@link ShapeResult} → {@link ShapeRefC}
 * lifecycle. Subclasses only need to implement {@link #createSettings()} to define
 * their specific shape configuration.
 * <p>
 * All shapes created through this wrapper are guaranteed to produce valid {@link ShapeRefC}
 * references, with proper error handling during the creation process.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxCollisionShape {

    /**
     * Creates the Jolt-specific shape settings for this collision shape.
     * <p>
     * Implementations should construct and return the appropriate {@link ShapeSettings}
     * subclass with all parameters configured. The caller is responsible for closing
     * the returned settings object.
     *
     * @return A new {@link ShapeSettings} instance describing this shape.
     */
    protected abstract ShapeSettings createSettings();

    /**
     * Creates a reference-counted shape from this collision shape's settings.
     * <p>
     * This method handles the full Jolt shape creation pipeline:
     * <ol>
     *     <li>Creates the {@link ShapeSettings} via {@link #createSettings()}</li>
     *     <li>Invokes {@link ShapeSettings#create()} to produce a {@link ShapeResult}</li>
     *     <li>Validates the result and extracts the {@link ShapeRefC}</li>
     * </ol>
     * The returned {@link ShapeRefC} must be closed by the caller when no longer needed.
     *
     * @return A valid {@link ShapeRefC} for use in body creation.
     * @throws IllegalStateException If the shape creation fails (e.g. invalid parameters).
     */
    public ShapeRefC createShapeRef() {
        try (ShapeSettings settings = createSettings();
             ShapeResult result = settings.create()) {
            if (result.hasError()) {
                throw new IllegalStateException("Shape creation failed: " + result.getError());
            }
            return result.get();
        }
    }

    /**
     * Creates the underlying {@link ConstShape} instance from this collision shape's settings.
     * <p>
     * This is a convenience method that extracts the {@link ConstShape} pointer from the
     * reference-counted wrapper. Note that the returned shape is only valid as long as
     * the {@link ShapeRefC} from {@link #createShapeRef()} is alive.
     *
     * @return The {@link ConstShape} instance.
     * @throws IllegalStateException If the shape creation fails.
     */
    public ConstShape createShape() {
        try (ShapeRefC ref = createShapeRef()) {
            return ref.getPtr();
        }
    }
}