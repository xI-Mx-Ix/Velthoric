/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.motorcycle;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.WheeledVehicleController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.controller.VxWheeledVehicleController;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for motorcycle-like vehicles. It handles input specific
 * to motorcycles, such as leaning, and manages its own {@link VxWheeledVehicleController}
 * which wraps the underlying Jolt MotorcycleController.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxMotorcycle extends VxVehicle {

    public static final VxDataAccessor<Vec3> DATA_CHASSIS_HALF_EXTENTS = VxDataAccessor.create(VxMotorcycle.class, VxDataSerializers.VEC3);

    private VxWheeledVehicleController controller;

    /**
     * Server-side constructor.
     */
    protected VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.constraintSettings = createConstraintSettings();
        this.collisionTester = createCollisionTester();
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    protected VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, UUID id) {
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
            throw new IllegalStateException("VxMotorcycle requires a MotorcycleController, which is a subclass of WheeledVehicleController.");
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
        this.synchronizedData.define(DATA_CHASSIS_HALF_EXTENTS, new Vec3(0.4f, 0.6f, 1.1f));
    }

    @Override
    public void onStopMounting(ServerPlayer player) {
        if (controller != null) {
            controller.setInput(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        if (controller == null) {
            return;
        }

        float forward = input.isForward() ? 1.0f : (input.isBackward() ? -1.0f : 0.0f);
        float right = input.isRight() ? 1.0f : (input.isLeft() ? -1.0f : 0.0f);
        float brake = input.isBackward() ? 1.0f : 0.0f;
        float handBrake = input.isUp() ? 1.0f : 0.0f;

        physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(this.getBodyId());
        controller.setInput(forward, right, brake, handBrake);
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