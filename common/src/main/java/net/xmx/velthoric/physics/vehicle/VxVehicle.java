/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.vehicle.controller.VxWheeledVehicleController;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all vehicle physics objects.
 * This class extends VxRigidBody and manages the Jolt VehicleConstraint, controller,
 * and wheels associated with the vehicle's chassis.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody {

    protected List<VxWheel> wheels = Collections.emptyList();
    protected VehicleConstraintSettings constraintSettings;
    protected VehicleCollisionTester collisionTester;

    private VehicleConstraint constraint;
    private VxWheeledVehicleController controller;
    private boolean wheelsDirty = false;

    protected VxVehicle(VxObjectType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Called by the ObjectManager after the chassis body has been created and added.
     * This is where the vehicle-specific components are created and added to the physics world.
     */
    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        // This assumes the Body has been created and has a valid ID.
        Body chassisBody = getBody();
        if (chassisBody == null || constraintSettings == null) {
            throw new IllegalStateException("Vehicle cannot be initialized without a chassis body or constraint settings.");
        }

        // 1. Instantiate the constraint
        this.constraint = new VehicleConstraint(chassisBody, constraintSettings);

        // 2. Create and add a collision tester
        if (this.collisionTester == null) {
            // Default to a ray tester if none is provided
            this.collisionTester = new VehicleCollisionTesterCastCylinder(chassisBody.getObjectLayer());
        }
        this.constraint.setVehicleCollisionTester(collisionTester);

        // 3. Add the constraint and its step listener to the physics system
        world.getPhysicsSystem().addConstraint(constraint);
        world.getPhysicsSystem().addStepListener(constraint.getStepListener());

        // 4. Wrap the Jolt controller
        if (constraint.getController() instanceof WheeledVehicleController joltController) {
            this.controller = new VxWheeledVehicleController(joltController);
        }
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        if (constraint != null) {
            world.getPhysicsSystem().removeStepListener(constraint.getStepListener());
            world.getPhysicsSystem().removeConstraint(constraint);
            constraint.close();
            constraint = null;
        }
        if (collisionTester != null) {
            collisionTester.close();
            collisionTester = null;
        }
    }

    @Override
    public void physicsTick(VxPhysicsWorld world) {
        super.physicsTick(world);
        if (constraint != null && !wheels.isEmpty()) {
            updateWheelStates();
        }
    }

    /**
     * Updates the state of all wheels from the Jolt constraint and marks them dirty for networking.
     */
    private void updateWheelStates() {
        boolean changed = false;
        for (int i = 0; i < wheels.size(); i++) {
            VxWheel vxWheel = wheels.get(i);
            Wheel joltWheel = constraint.getWheel(i);

            // Update internal state
            vxWheel.setRotationAngle(joltWheel.getRotationAngle());
            vxWheel.setSteerAngle(joltWheel.getSteerAngle());
            vxWheel.setSuspensionLength(joltWheel.getSuspensionLength());
            vxWheel.setHasContact(joltWheel.hasContact());
            changed = true;
        }
        if (changed) {
            markWheelsDirty();
        }
    }

    /**
     * Notifies the network dispatcher that this vehicle's wheel data needs to be synchronized.
     */
    public void markWheelsDirty() {
        this.wheelsDirty = true;
    }

    public boolean areWheelsDirty() {
        return wheelsDirty;
    }

    public void clearWheelsDirty() {
        this.wheelsDirty = false;
    }

    public List<VxWheel> getWheels() {
        return wheels;
    }

    public VxWheeledVehicleController getController() {
        return controller;
    }
}