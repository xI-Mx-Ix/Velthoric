/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.car;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.WheeledVehicleController;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import net.xmx.velthoric.physics.riding.input.VxRideInput;
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

    protected VxCar(VxObjectType<? extends VxCar> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.constraintSettings = createConstraintSettings();
        this.collisionTester = createCollisionTester();
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
    protected void defineSyncData() {
        super.defineSyncData();
        this.synchronizedData.define(DATA_CHASSIS_HALF_EXTENTS, new Vec3(1.1f, 0.5f, 2.4f));
    }

    @Override
    public void onStopRiding(ServerPlayer player) {
        if (this.controller != null) {
            this.controller.setInput(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    @Override
    public void handleDriverInput(ServerPlayer player, VxRideInput input) {
        if (this.controller == null) {
            return;
        }

        float forward = input.isForward() ? 1.0f : (input.isBackward() ? -1.0f : 0.0f);
        float right = input.isRight() ? 1.0f : (input.isLeft() ? -1.0f : 0.0f);
        float brake = input.isBackward() ? 1.0f : 0.0f;
        float handBrake = input.isUp() ? 1.0f : 0.0f;

        this.controller.setInput(forward, right, brake, handBrake);
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        buf.writeVec3(this.getSyncData(DATA_CHASSIS_HALF_EXTENTS));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        this.setSyncData(DATA_CHASSIS_HALF_EXTENTS, buf.readVec3());
    }

    public VxWheeledVehicleController getController() {
        return controller;
    }
}