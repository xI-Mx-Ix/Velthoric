/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.car;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.WheeledVehicleController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.controller.VxWheeledVehicleController;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for car-like vehicles. It extends {@link VxVehicle}
 * by adding synchronized data for chassis properties, handling standard
 * four-wheel driver input, and managing its own {@link VxWheeledVehicleController}.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxCar extends VxVehicle {

    public static final VxDataAccessor<Vec3> DATA_CHASSIS_HALF_EXTENTS = VxDataAccessor.create(VxCar.class, VxDataSerializers.VEC3);

    private VxWheeledVehicleController controller;

    /**
     * Server-side constructor.
     */
    protected VxCar(VxBodyType<? extends VxCar> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.constraintSettings = createConstraintSettings();
        this.collisionTester = createCollisionTester();
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    protected VxCar(VxBodyType<? extends VxCar> type, UUID id) {
        super(type, id);
    }

    protected abstract VehicleConstraintSettings createConstraintSettings();
    protected abstract VehicleCollisionTester createCollisionTester();

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);
        if (constraint.getController() instanceof WheeledVehicleController joltController) {
            this.controller = new VxWheeledVehicleController(joltController);
        } else {
            throw new IllegalStateException("VxCar requires a WheeledVehicleController.");
        }
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        this.controller = null;
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        super.defineSyncData(builder);
        builder.define(DATA_CHASSIS_HALF_EXTENTS, new Vec3(1.1f, 0.5f, 2.4f));
    }

    @Override
    public void onStopMounting(ServerPlayer player) {
        if (this.controller != null) {
            this.controller.setInput(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    /**
     * Handles driver input for the car, translating player controls into vehicle actions.
     * This implementation provides a more intuitive driving model:
     * - Pressing 'back' while moving forward applies the brakes.
     * - Pressing 'back' while stationary or moving backward engages the reverse throttle.
     * - The handbrake is applied with the 'up' input (e.g., spacebar).
     *
     * @param player The player driving the vehicle.
     * @param input  The current state of the player's inputs.
     */
    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        if (this.controller == null) {
            return;
        }

        // Use a small speed threshold to determine if the vehicle is moving forward.
        boolean isMovingForward = getSpeedKmh() > 1.0f;

        float forwardInput = 0.0f;
        float brakeInput = 0.0f;

        if (input.isForward()) {
            // Apply forward throttle.
            forwardInput = 1.0f;
        } else if (input.isBackward()) {
            if (isMovingForward) {
                // If moving forward and pressing "back", apply the main brakes.
                brakeInput = 1.0f;
            } else {
                // If stationary or already moving backward, apply reverse throttle.
                forwardInput = -1.0f;
            }
        }

        float rightInput = 0.0f;
        if (input.isRight()) {
            rightInput = 1.0f;
        } else if (input.isLeft()) {
            rightInput = -1.0f;
        }

        // The 'up' input (e.g., spacebar) controls the handbrake.
        float handBrakeInput = input.isUp() ? 1.0f : 0.0f;

        // Ensure the vehicle body is active in the physics simulation before applying input.
        physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(this.getBodyId());
        this.controller.setInput(forwardInput, rightInput, brakeInput, handBrakeInput);
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        DATA_CHASSIS_HALF_EXTENTS.getSerializer().write(buf, this.getSyncData(DATA_CHASSIS_HALF_EXTENTS));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        this.setSyncData(DATA_CHASSIS_HALF_EXTENTS, DATA_CHASSIS_HALF_EXTENTS.getSerializer().read(buf));
    }

    public Vec3 getChassisHalfExtents() {
        return this.getSyncData(DATA_CHASSIS_HALF_EXTENTS);
    }

    public VxWheeledVehicleController getController() {
        return controller;
    }
}