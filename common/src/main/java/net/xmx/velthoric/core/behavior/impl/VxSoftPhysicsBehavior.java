/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;

/**
 * The behavior for soft body physics simulation.
 * <p>
 * Bodies with this behavior are simulated using Jolt's soft body dynamics engine,
 * which involves deformable meshes with per-vertex position data.
 *
 * @author xI-Mx-Ix
 */
public class VxSoftPhysicsBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.SOFT_PHYSICS;
    }
}