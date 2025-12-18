/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.buoyancy.floater;

import com.github.stephengold.joltjni.Body;
import net.timtaran.interactivemc.physics.physics.buoyancy.VxBuoyancyDataStore;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

/**
 * Abstract base class for applying buoyancy forces to a physics body.
 * Each implementation defines a specific strategy for calculating submerged
 * volume and applying corresponding forces based on the shape type.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBuoyancyFloater {

    protected final VxPhysicsWorld physicsWorld;

    /**
     * Constructs a floater instance.
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyFloater(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    /**
     * Applies buoyancy and drag forces to the given body.
     *
     * @param body      The physics body to process.
     * @param deltaTime The simulation time step.
     * @param index     The index of the body in the data store.
     * @param dataStore The data store containing fluid properties for the body.
     */
    public abstract void applyForces(Body body, float deltaTime, int index, VxBuoyancyDataStore dataStore);
}