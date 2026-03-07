/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.type.provider;

import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.factory.VxSoftBodyFactory;

/**
 * A functional interface defining how a Jolt soft body is constructed for a specific body type.
 * <p>
 * This functional approach ensures physics creation logic is decoupled from
 * body identity and stored behaviors.
 *
 * @author xI-Mx-Ix
 */
@FunctionalInterface
public interface VxJoltSoftProvider {
    /**
     * Creates a Jolt soft body for the given VxBody using the provided factory.
     *
     * @param body    The body instance (use {@code body.get()} to read sync data for mesh configuration).
     * @param factory The factory that handles native Jolt soft body creation.
     * @return The Jolt body ID returned by the factory.
     */
    int createJoltBody(VxBody body, VxSoftBodyFactory factory);
}